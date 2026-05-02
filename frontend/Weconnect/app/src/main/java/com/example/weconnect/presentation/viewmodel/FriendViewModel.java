package com.example.weconnect.presentation.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weconnect.data.repository.FirestoreFriendRepository;
import com.example.weconnect.domain.model.User;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class FriendViewModel extends AndroidViewModel {

    private final FirestoreFriendRepository friendRepo;

    private final MutableLiveData<List<User>> _friends = new MutableLiveData<>();
    public LiveData<List<User>> friends = _friends;

    private final MutableLiveData<List<User>> _pendingRequests = new MutableLiveData<>();
    public LiveData<List<User>> pendingRequests = _pendingRequests;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private final MutableLiveData<String> _successMessage = new MutableLiveData<>();
    public LiveData<String> successMessage = _successMessage;

    public FriendViewModel(@NonNull Application application) {
        super(application);
        friendRepo = new FirestoreFriendRepository();
    }

    public void loadFriends(String userId) {
        if (userId == null) return;
        friendRepo.getFriends(userId, new FirestoreFriendRepository.FriendsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> friendsList) {
                List<User> result = new ArrayList<>();
                for (Map<String, Object> map : friendsList) {
                    User u = new User();
                    u.setId(map.get("id") != null ? map.get("id").toString() : "");
                    u.setFullName(map.get("fullName") != null ? map.get("fullName").toString() : "");
                    // mapping other fields if necessary
                    result.add(u);
                }
                _friends.postValue(result);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
                _friends.postValue(new ArrayList<>());
            }
        });
    }

    public void loadPendingRequests(String userId) {
        if (userId == null) return;
        friendRepo.getPendingRequests(userId, new FirestoreFriendRepository.FriendsCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> requestsList) {
                List<User> result = new ArrayList<>();
                for (Map<String, Object> map : requestsList) {
                    User u = new User();
                    u.setId(map.get("id") != null ? map.get("id").toString() : "");
                    u.setFullName(map.get("fullName") != null ? map.get("fullName").toString() : "");
                    result.add(u);
                }
                _pendingRequests.postValue(result);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
                _pendingRequests.postValue(new ArrayList<>());
            }
        });
    }

    public void acceptFriendRequest(String senderId, String receiverId) {
        if (senderId == null || receiverId == null) return;
        friendRepo.acceptRequestByUsers(senderId, receiverId, new FirestoreFriendRepository.ActionCallback() {
            @Override
            public void onSuccess(String msg) {
                _successMessage.postValue(msg);
                loadPendingRequests(receiverId); // reload list
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
            }
        });
    }

    public void declineFriendRequest(String senderId, String receiverId) {
        if (senderId == null || receiverId == null) return;
        friendRepo.declineRequestByUsers(senderId, receiverId, new FirestoreFriendRepository.ActionCallback() {
            @Override
            public void onSuccess(String msg) {
                _successMessage.postValue(msg);
                loadPendingRequests(receiverId); // reload list
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
            }
        });
    }
}
