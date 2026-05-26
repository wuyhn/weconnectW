package com.example.weconnect.models;

import com.google.gson.annotations.SerializedName;

public class JoinGroupResponse {
    private boolean success;
    private String message;

    @SerializedName(value = "isNewTagSuggested", alternate = {"newTagSuggested"})
    private boolean newTagSuggested;

    public JoinGroupResponse() {
    }

    public JoinGroupResponse(boolean success, String message, boolean newTagSuggested) {
        this.success = success;
        this.message = message;
        this.newTagSuggested = newTagSuggested;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public boolean isNewTagSuggested() {
        return newTagSuggested;
    }

    public void setNewTagSuggested(boolean newTagSuggested) {
        this.newTagSuggested = newTagSuggested;
    }
}
