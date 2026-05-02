package com.example.weconnect.presentation.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weconnect.data.repository.FirestorePostRepository;
import com.example.weconnect.presentation.adapter.ParticipantAdapter; // Assuming ParticipantAdapter is moved or will be moved

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class PostViewModel extends AndroidViewModel {

    private final FirestorePostRepository postRepo;

    private final MutableLiveData<List<Map<String, Object>>> _pendingMembers = new MutableLiveData<>();
    public LiveData<List<Map<String, Object>>> pendingMembers = _pendingMembers;

    private final MutableLiveData<List<Map<String, Object>>> _approvedMembers = new MutableLiveData<>();
    public LiveData<List<Map<String, Object>>> approvedMembers = _approvedMembers;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private final MutableLiveData<String> _successMessage = new MutableLiveData<>();
    public LiveData<String> successMessage = _successMessage;

    public PostViewModel(@NonNull Application application) {
        super(application);
        postRepo = new FirestorePostRepository();
    }

    public void loadPendingMembers(String postId) {
        if (postId == null || postId.isEmpty()) {
            _pendingMembers.postValue(new ArrayList<>());
            return;
        }

        postRepo.getPendingMembers(postId, new FirestorePostRepository.MembersCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> members) {
                _pendingMembers.postValue(members);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
                _pendingMembers.postValue(new ArrayList<>());
            }
        });
    }

    public void loadApprovedMembers(String postId) {
        if (postId == null || postId.isEmpty()) {
            _approvedMembers.postValue(new ArrayList<>());
            return;
        }

        postRepo.getApprovedMembers(postId, new FirestorePostRepository.MembersCallback() {
            @Override
            public void onSuccess(List<Map<String, Object>> members) {
                _approvedMembers.postValue(members);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
                _approvedMembers.postValue(new ArrayList<>());
            }
        });
    }
}
