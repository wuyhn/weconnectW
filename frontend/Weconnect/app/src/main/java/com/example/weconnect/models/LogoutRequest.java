package com.example.weconnect.models;

import com.google.gson.annotations.SerializedName;

public class LogoutRequest {

    @SerializedName("fcmToken")
    private String fcmToken;

    public LogoutRequest(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getFcmToken() {
        return fcmToken;
    }
}
