package com.example.weconnect.presentation.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weconnect.data.repository.FirestoreChatRepository;
import com.example.weconnect.domain.model.ChatMessage;
import com.example.weconnect.domain.model.ChatRoom;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class ChatViewModel extends AndroidViewModel {

    private final FirestoreChatRepository chatRepo;

    private final MutableLiveData<List<ChatRoom>> _chatRooms = new MutableLiveData<>();
    public LiveData<List<ChatRoom>> chatRooms = _chatRooms;

    private final MutableLiveData<List<ChatMessage>> _messages = new MutableLiveData<>();
    public LiveData<List<ChatMessage>> messages = _messages;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    public ChatViewModel(@NonNull Application application) {
        super(application);
        chatRepo = new FirestoreChatRepository();
    }

    public void loadChatRooms(String userId) {
        if (userId == null) return;
        chatRepo.getUserRooms(userId, new FirestoreChatRepository.RoomsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> rooms) {
                List<ChatRoom> result = new ArrayList<>();
                for (Map<String, Object> map : rooms) {
                    ChatRoom room = new ChatRoom();
                    room.setId((String) map.get("id"));
                    room.setTitle((String) map.get("title"));
                    room.setType((String) map.get("type"));
                    room.setOwnerId((String) map.get("ownerId"));
                    room.setPostId((String) map.get("postId"));
                    room.setMemberIds((List<String>) map.get("memberIds"));
                    room.setActive(Boolean.TRUE.equals(map.get("active")));
                    result.add(room);
                }
                _chatRooms.postValue(result);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
                _chatRooms.postValue(new ArrayList<>());
            }
        });
    }

    private com.google.firebase.firestore.ListenerRegistration messageListener;

    public void listenToMessages(String roomId, String currentUserId) {
        if (roomId == null) return;
        
        if (messageListener != null) {
            messageListener.remove();
        }

        messageListener = chatRepo.listenToNewMessages(roomId, currentUserId, new FirestoreChatRepository.MessagesCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> newMsgs) {
                List<ChatMessage> msgs = new ArrayList<>();
                for (Map<String, Object> m : newMsgs) {
                    String id       = (String) m.get("id");
                    String sender   = (String) m.get("senderName");
                    String content  = (String) m.get("content");
                    boolean isMe    = Boolean.TRUE.equals(m.get("isMyMessage"));
                    com.google.firebase.Timestamp ts = (com.google.firebase.Timestamp) m.get("createdAt");
                    java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault());
                    String time = ts != null ? sdf.format(ts.toDate()) : "";
                    msgs.add(new ChatMessage(id, sender, content, time, isMe));
                }
                _messages.postValue(msgs);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
            }
        });
    }

    public void sendMessage(String roomId, String senderId, String senderName, String messageContent) {
        if (roomId == null || senderId == null) return;
        chatRepo.sendMessage(roomId, senderId, senderName, messageContent, new FirestoreChatRepository.MessageCallback() {
            @Override
            public void onSuccess(Map<String, Object> msg) {
                // Tin nhắn gửi thành công sẽ được lắng nghe thông qua listenToMessages
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
            }
        });
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        if (messageListener != null) {
            messageListener.remove();
            messageListener = null;
        }
    }
}
