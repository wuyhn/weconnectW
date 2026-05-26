package com.weconnect.backend.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class JoinGroupResponse {
    private boolean success;
    private String message;
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

    @JsonProperty("isNewTagSuggested")
    public boolean isNewTagSuggested() {
        return newTagSuggested;
    }

    @JsonProperty("isNewTagSuggested")
    public void setNewTagSuggested(boolean newTagSuggested) {
        this.newTagSuggested = newTagSuggested;
    }
}
