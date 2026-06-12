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
    private Disposable roomEventsSubscription;
    private Disposable notificationSubscription;
    private Disposable accountStatusSubscription;
    private Disposable aiSummarySubscription;
    private Consumer<String> chatListCallback;
    private Consumer<String> feedCallback;
    private Consumer<String> avatarUpdateCallback;
    private Consumer<String> roomEventsCallback;
    private Consumer<String> notificationCallback;
    private Consumer<String> accountStatusCallback;
    private Consumer<ChatMessageApiResponse> aiSummaryCallback;
    private boolean connected = false;
    // Callback được gọi ngay khi STOMP connection OPENED thành công.
    // MainActivity đặt callback này để tự động đăng ký subscription dù connect() là async.
    private Runnable onConnectedCallback;

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
                            // Gọi callback ngay khi kết nối mở — đảm bảo các subscription
                            // (đặc biệt account-status) được đăng ký dù connect() là async.
                            if (onConnectedCallback != null) {
                                onConnectedCallback.run();
                            }
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
     * Subscribe nhận sự kiện phòng chat (ví dụ: KICKED) gửi riêng cho user hiện tại.
     * Dùng ở ConversationActivity để phát hiện bị kick realtime.
     */
    public void subscribeToRoomEvents(Consumer<String> onEvent) {
        if (stompClient == null) return;
        roomEventsCallback = onEvent;

        if (roomEventsSubscription != null && !roomEventsSubscription.isDisposed()) {
            roomEventsSubscription.dispose();
        }

        roomEventsSubscription = stompClient.topic("/user/queue/room-events")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(frame -> {
                    if (roomEventsCallback != null) {
                        roomEventsCallback.accept(frame.getPayload());
                    }
                }, error -> Log.e(TAG, "RoomEvents subscription error: " + error.getMessage()));

        if (compositeDisposable != null) compositeDisposable.add(roomEventsSubscription);
    }

    public void unsubscribeFromRoomEvents() {
        roomEventsCallback = null;
        if (roomEventsSubscription != null && !roomEventsSubscription.isDisposed()) {
            roomEventsSubscription.dispose();
            roomEventsSubscription = null;
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

    /**
     * Subscribe nhận notification realtime (khi server push qua STOMP).
     * Dùng ở NotificationsActivity và MainActivity để cập nhật badge.
     */
    public void subscribeToNotifications(Consumer<String> onNotification) {
        if (stompClient == null) return;
        notificationCallback = onNotification;

        if (notificationSubscription != null && !notificationSubscription.isDisposed()) {
            notificationSubscription.dispose();
        }

        notificationSubscription = stompClient.topic("/user/queue/notifications")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(frame -> {
                    if (notificationCallback != null) {
                        notificationCallback.accept(frame.getPayload());
                    }
                }, error -> Log.e(TAG, "Notification subscription error: " + error.getMessage()));

        if (compositeDisposable != null) compositeDisposable.add(notificationSubscription);
    }

    public void unsubscribeFromNotifications() {
        notificationCallback = null;
        if (notificationSubscription != null && !notificationSubscription.isDisposed()) {
            notificationSubscription.dispose();
            notificationSubscription = null;
        }
    }

    /**
     * Subscribe nhận sự kiện trạng thái tài khoản (ACCOUNT_LOCKED) từ backend real-time.
     * Backend dùng convertAndSendToUser(userId, "/queue/account-status", payload).
     * Payload JSON: {"action": "ACCOUNT_LOCKED", "message": "..."}
     *
     * Dùng ở MainActivity để phát hiện ngay khi tài khoản bị khóa trong lúc đang dùng app.
     */
    public void subscribeToAccountStatus(Consumer<String> onEvent) {
        if (stompClient == null) return;
        accountStatusCallback = onEvent;

        if (accountStatusSubscription != null && !accountStatusSubscription.isDisposed()) {
            accountStatusSubscription.dispose();
        }

        accountStatusSubscription = stompClient.topic("/user/queue/account-status")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(frame -> {
                    if (accountStatusCallback != null) {
                        accountStatusCallback.accept(frame.getPayload());
                    }
                }, error -> Log.e(TAG, "AccountStatus subscription error: " + error.getMessage()));

        if (compositeDisposable != null) compositeDisposable.add(accountStatusSubscription);
    }

    public void unsubscribeFromAccountStatus() {
        accountStatusCallback = null;
        if (accountStatusSubscription != null && !accountStatusSubscription.isDisposed()) {
            accountStatusSubscription.dispose();
            accountStatusSubscription = null;
        }
    }

    /**
     * Subscribe nhận bản tóm tắt AI riêng tư (chỉ gửi tới user đã yêu cầu).
     * Backend dùng convertAndSendToUser(userId, "/queue/ai-summary", payload).
     */
    public void subscribeToAiSummary(Consumer<ChatMessageApiResponse> onSummary) {
        if (stompClient == null) return;
        aiSummaryCallback = onSummary;

        if (aiSummarySubscription != null && !aiSummarySubscription.isDisposed()) {
            aiSummarySubscription.dispose();
        }

        Gson gson = new Gson();
        aiSummarySubscription = stompClient.topic("/user/queue/ai-summary")
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(frame -> {
                    if (aiSummaryCallback != null) {
                        try {
                            ChatMessageApiResponse msg = gson.fromJson(
                                    frame.getPayload(), ChatMessageApiResponse.class);
                            aiSummaryCallback.accept(msg);
                        } catch (Exception e) {
                            Log.e(TAG, "Failed to parse AI summary: " + e.getMessage());
                        }
                    }
                }, error -> Log.e(TAG, "AiSummary subscription error: " + error.getMessage()));

        if (compositeDisposable != null) compositeDisposable.add(aiSummarySubscription);
    }

    public void unsubscribeFromAiSummary() {
        aiSummaryCallback = null;
        if (aiSummarySubscription != null && !aiSummarySubscription.isDisposed()) {
            aiSummarySubscription.dispose();
            aiSummarySubscription = null;
        }
    }

    /**
     * Đặt callback được gọi ngay khi WebSocket OPENED thành công.
     * Dùng ở MainActivity để subscribe các topic ngay sau khi kết nối mở,
     * tránh race condition khi subscribeToRealtimeEvents() chạy trước OPENED event.
     */
    public void setOnConnectedCallback(Runnable callback) {
        this.onConnectedCallback = callback;
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
        roomEventsCallback = null;
        notificationCallback = null;
        accountStatusCallback = null;
        aiSummaryCallback = null;
        onConnectedCallback = null;
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
