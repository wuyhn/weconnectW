package com.example.weconnect.websocket;

import android.util.Log;

import com.example.weconnect.models.ChatMessageApiResponse;
import com.google.gson.Gson;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.reactivex.android.schedulers.AndroidSchedulers;
import io.reactivex.disposables.CompositeDisposable;
import io.reactivex.disposables.Disposable;
import io.reactivex.schedulers.Schedulers;
import ua.naiksoftware.stomp.Stomp;
import ua.naiksoftware.stomp.StompClient;
import ua.naiksoftware.stomp.dto.StompHeader;

public class WebSocketManager {

    private static final String TAG = "WebSocketManager";
    private static WebSocketManager instance;

    private StompClient stompClient;
    private CompositeDisposable compositeDisposable;
    private final Map<Long, Disposable> roomSubscriptions = new HashMap<>();
    private Disposable chatListSubscription;
    private Disposable feedSubscription;
    private Disposable avatarUpdateSubscription;
    private Consumer<String> chatListCallback;
    private Consumer<String> feedCallback;
    private Consumer<String> avatarUpdateCallback;
    private boolean connected = false;

    private WebSocketManager() {}

    public static synchronized WebSocketManager getInstance() {
        if (instance == null) {
            instance = new WebSocketManager();
        }
        return instance;
    }

    /**
     * Kết nối tới WebSocket server.
     * baseHttpUrl ví dụ: "http://10.0.2.2:8081/"
     */
    public void connect(String baseHttpUrl, String token) {
        if (connected && stompClient != null) return;

        // Dọn dẹp client cũ trước khi reconnect
        if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
            compositeDisposable.dispose();
            compositeDisposable = null;
        }
        if (stompClient != null) {
            try { stompClient.disconnect(); } catch (Exception ignored) {}
            stompClient = null;
        }
        roomSubscriptions.clear();

        // Chuyển http:// → ws://, bỏ dấu / cuối, thêm /ws
        String wsUrl = baseHttpUrl
                .replace("https://", "wss://")
                .replace("http://", "ws://")
                .replaceAll("/$", "") + "/ws";

        compositeDisposable = new CompositeDisposable();
        stompClient = Stomp.over(Stomp.ConnectionProvider.OKHTTP, wsUrl);

        List<StompHeader> connectHeaders = new ArrayList<>();
        connectHeaders.add(new StompHeader("Authorization", "Bearer " + token));

        Disposable lifecycleSub = stompClient.lifecycle()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(event -> {
                    switch (event.getType()) {
                        case OPENED:
                            connected = true;
                            Log.d(TAG, "WebSocket connected");
                            break;
                        case CLOSED:
                            connected = false;
                            Log.d(TAG, "WebSocket closed");
                            break;
                        case ERROR:
                            connected = false;
                            Log.e(TAG, "WebSocket error: " +
                                    (event.getException() != null ? event.getException().getMessage() : "unknown"));
                            break;
                        default:
                            break;
                    }
                }, error -> Log.e(TAG, "Lifecycle error: " + error.getMessage()));

        compositeDisposable.add(lifecycleSub);
        stompClient.connect(connectHeaders);
    }

    /**
     * Subscribe nhận tin nhắn mới trong phòng chat.
     */
    public void subscribeToRoom(long roomId, Consumer<ChatMessageApiResponse> onMessage) {
        if (stompClient == null) return;

        // Hủy subscription cũ của phòng này nếu có
        Disposable existing = roomSubscriptions.remove(roomId);
        if (existing != null && !existing.isDisposed()) {
            existing.dispose();
        }

        Gson gson = new Gson();
        Disposable sub = stompClient.topic("/topic/chat/" + roomId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(frame -> {
                    try {
                        ChatMessageApiResponse msg = gson.fromJson(
                                frame.getPayload(), ChatMessageApiResponse.class);
                        onMessage.accept(msg);
                    } catch (Exception e) {
                        Log.e(TAG, "Failed to parse message: " + e.getMessage());
                    }
                }, error -> Log.e(TAG, "Room subscription error: " + error.getMessage()));

        roomSubscriptions.put(roomId, sub);
        if (compositeDisposable != null) compositeDisposable.add(sub);
    }

    /**
     * Hủy subscribe phòng chat (khi thoát màn hình hội thoại).
     */
    public void unsubscribeFromRoom(long roomId) {
        Disposable sub = roomSubscriptions.remove(roomId);
        if (sub != null && !sub.isDisposed()) {
            sub.dispose();
        }
    }

    /**
     * Gửi tin nhắn qua WebSocket.
     */
    public void sendMessage(long roomId, String content) {
        if (stompClient == null || !connected) return;

        // Escape content để tránh JSON injection
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        String json = "{\"content\":\"" + escaped + "\"}";

        Disposable sendSub = stompClient.send("/app/chat/" + roomId + "/send", json)
                .subscribeOn(Schedulers.io())
                .subscribe(
                        () -> Log.d(TAG, "Message sent to room " + roomId),
                        error -> Log.e(TAG, "Send error: " + error.getMessage())
                );
        if (compositeDisposable != null) compositeDisposable.add(sendSub);
    }

    /**
     * Subscribe nhận thông báo khi có tin nhắn mới ở bất kỳ phòng nào.
     * Dùng ở ChatListActivity để refresh danh sách phòng.
     */
    public void subscribeToChatList(Consumer<String> onUpdate) {
        if (stompClient == null) return;
        chatListCallback = onUpdate;

        if (chatListSubscription != null && !chatListSubscription.isDisposed()) {
            chatListSubscription.dispose();
        }

        chatListSubscription = stompClient.topic("/user/queue/chat-list")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(frame -> {
                    if (chatListCallback != null) {
                        chatListCallback.accept(frame.getPayload());
                    }
                }, error -> Log.e(TAG, "ChatList subscription error: " + error.getMessage()));

        if (compositeDisposable != null) compositeDisposable.add(chatListSubscription);
    }

    /**
     * Hủy subscribe danh sách phòng (khi rời ChatListActivity).
     */
    public void unsubscribeFromChatList() {
        chatListCallback = null;
        if (chatListSubscription != null && !chatListSubscription.isDisposed()) {
            chatListSubscription.dispose();
            chatListSubscription = null;
        }
    }

    /**
     * Subscribe nhận bài đăng mới từ bất kỳ user nào (realtime feed).
     */
    public void subscribeToFeed(Consumer<String> onNewPost) {
        if (stompClient == null) return;
        feedCallback = onNewPost;

        if (feedSubscription != null && !feedSubscription.isDisposed()) {
            feedSubscription.dispose();
        }

        feedSubscription = stompClient.topic("/topic/feed")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(frame -> {
                    if (feedCallback != null) feedCallback.accept(frame.getPayload());
                }, error -> Log.e(TAG, "Feed subscription error: " + error.getMessage()));

        if (compositeDisposable != null) compositeDisposable.add(feedSubscription);
    }

    public void unsubscribeFromFeed() {
        feedCallback = null;
        if (feedSubscription != null && !feedSubscription.isDisposed()) {
            feedSubscription.dispose();
            feedSubscription = null;
        }
    }

    /**
     * Subscribe nhận cập nhật avatar của tất cả user (realtime avatar sync).
     */
    public void subscribeToAvatarUpdates(Consumer<String> onUpdate) {
        if (stompClient == null) return;
        avatarUpdateCallback = onUpdate;

        if (avatarUpdateSubscription != null && !avatarUpdateSubscription.isDisposed()) {
            avatarUpdateSubscription.dispose();
        }

        avatarUpdateSubscription = stompClient.topic("/topic/user-updates")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(frame -> {
                    if (avatarUpdateCallback != null) avatarUpdateCallback.accept(frame.getPayload());
                }, error -> Log.e(TAG, "Avatar update subscription error: " + error.getMessage()));

        if (compositeDisposable != null) compositeDisposable.add(avatarUpdateSubscription);
    }

    public void unsubscribeFromAvatarUpdates() {
        avatarUpdateCallback = null;
        if (avatarUpdateSubscription != null && !avatarUpdateSubscription.isDisposed()) {
            avatarUpdateSubscription.dispose();
            avatarUpdateSubscription = null;
        }
    }

    public boolean isConnected() {
        return connected && stompClient != null;
    }

    /**
     * Ngắt kết nối hoàn toàn (khi logout).
     */
    public void disconnect() {
        connected = false;
        chatListCallback = null;
        feedCallback = null;
        avatarUpdateCallback = null;
        roomSubscriptions.clear();
        if (compositeDisposable != null && !compositeDisposable.isDisposed()) {
            compositeDisposable.dispose();
            compositeDisposable = null;
        }
        if (stompClient != null) {
            stompClient.disconnect();
            stompClient = null;
        }
    }
}
