package com.weconnect.backend.dto;

public class LogoutRequest {

    private String fcmToken;

    public LogoutRequest() {}

    public LogoutRequest(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
