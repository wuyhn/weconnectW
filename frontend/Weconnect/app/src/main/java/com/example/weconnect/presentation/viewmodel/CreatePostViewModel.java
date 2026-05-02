package com.example.weconnect.presentation.viewmodel;

import android.app.Application;
import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.weconnect.data.repository.FirebaseManager;
import com.example.weconnect.data.repository.FirestorePostRepository;
import com.google.firebase.storage.FirebaseStorage;
import com.google.firebase.storage.StorageReference;

import java.util.HashMap;
import java.util.Map;

public class CreatePostViewModel extends AndroidViewModel {

    private final FirestorePostRepository postRepo;

    private final MutableLiveData<String> _error = new MutableLiveData<>();
    public LiveData<String> error = _error;

    private final MutableLiveData<String> _successMessage = new MutableLiveData<>();
    public LiveData<String> successMessage = _successMessage;

    private final MutableLiveData<Boolean> _actionStatus = new MutableLiveData<>();
    public LiveData<Boolean> actionStatus = _actionStatus;

    public CreatePostViewModel(@NonNull Application application) {
        super(application);
        postRepo = new FirestorePostRepository();
    }

    public void createPost(String content, String location, String tag, int maxMembers, 
                           long startTime, long endTime, Uri imageUri, String selectedLocationName) {
        
        String uid = FirebaseManager.getCurrentUserId();
        if (uid == null) {
            _error.postValue("Vui lòng đăng nhập lại");
            _actionStatus.postValue(false);
            return;
        }

        if (imageUri != null) {
            uploadImageAndCreatePost(uid, content, location, tag, maxMembers, startTime, endTime, imageUri, selectedLocationName);
        } else {
            savePostData(uid, content, location, tag, maxMembers, startTime, endTime, null, selectedLocationName);
        }
    }

    private void uploadImageAndCreatePost(String uid, String content, String location, String tag, 
                                          int maxMembers, long startTime, long endTime, Uri imageUri, String selectedLocationName) {
        StorageReference storageRef = FirebaseStorage.getInstance().getReference()
                .child("post_images/" + uid + "_" + System.currentTimeMillis() + ".jpg");

        storageRef.putFile(imageUri)
                .addOnSuccessListener(taskSnapshot -> storageRef.getDownloadUrl().addOnSuccessListener(uri -> {
                    savePostData(uid, content, location, tag, maxMembers, startTime, endTime, uri.toString(), selectedLocationName);
                }))
                .addOnFailureListener(e -> {
                    _error.postValue("Lỗi upload ảnh: " + e.getMessage());
                    _actionStatus.postValue(false);
                });
    }

    private void savePostData(String uid, String content, String location, String tag, 
                              int maxMembers, long startTime, long endTime, String imageUrl, String selectedLocationName) {
        Map<String, Object> postData = new HashMap<>();
        String authorName = FirebaseManager.getUserName(getApplication());
        postData.put("authorId", uid);
        postData.put("authorUid", uid);
        postData.put("authorName", authorName != null ? authorName : "");
        postData.put("content", content);
        postData.put("location", location);
        postData.put("interestTag", tag);
        postData.put("maxMembers", maxMembers);
        postData.put("startTime", new com.google.firebase.Timestamp(new java.util.Date(startTime)));
        postData.put("endTime", new com.google.firebase.Timestamp(new java.util.Date(endTime)));
        if (imageUrl != null) {
            postData.put("imageUrl", imageUrl);
        }
        if (selectedLocationName != null) {
            postData.put("locationName", selectedLocationName);
        }
        
        postData.put("archived", false);
        postData.put("expired", false);
        postData.put("memberCount", 1);
        postData.put("createdAt", com.google.firebase.firestore.FieldValue.serverTimestamp());

        postRepo.createPost(postData, new FirestorePostRepository.ActionCallback() {
            @Override
            public void onSuccess(String msg) {
                _successMessage.postValue(msg);
                _actionStatus.postValue(true);
            }

            @Override
            public void onError(String err) {
                _error.postValue(err);
                _actionStatus.postValue(false);
            }
        });
    }
}
