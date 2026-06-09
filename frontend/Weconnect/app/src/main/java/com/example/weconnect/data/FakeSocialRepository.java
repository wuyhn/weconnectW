package com.example.weconnect.data;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FakeSocialRepository {

    public enum FriendStatus {
        NONE,           // Chưa kết bạn
        PENDING_SENT,   // Đã gửi lời mời
        PENDING_RECEIVED, // Nhận được lời mời
        FRIEND,         // Đã là bạn bè
        BLOCKED         // Đã chặn
    }

    public static class SocialState {
        private final boolean selfProfile;
        private FriendStatus friendStatus;

        public SocialState(boolean selfProfile, FriendStatus friendStatus) {
            this.selfProfile = selfProfile;
            this.friendStatus = friendStatus;
        }

        public boolean isSelfProfile() { return selfProfile; }

        public FriendStatus getFriendStatus() { return friendStatus; }
        public void setFriendStatus(FriendStatus status) { this.friendStatus = status; }

        // Convenience methods
        public boolean isFriend() { return friendStatus == FriendStatus.FRIEND; }
        public boolean isBlocked() { return friendStatus == FriendStatus.BLOCKED; }
        public boolean isPendingSent() { return friendStatus == FriendStatus.PENDING_SENT; }
        public boolean isPendingReceived() { return friendStatus == FriendStatus.PENDING_RECEIVED; }
    }

    private static FakeSocialRepository instance;
    private final Map<String, SocialState> stateMap = new HashMap<>();
    private String currentUsername = "Quỳnh Nguyễn";

    public void setCurrentUsername(String name) {
        this.currentUsername = name;
    }

    private FakeSocialRepository() {
        seed();
    }

    public static synchronized FakeSocialRepository getInstance() {
        if (instance == null) {
            instance = new FakeSocialRepository();
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        instance = null;
    }

    public String getCurrentUsername() {
        return currentUsername;
    }

    public SocialState getState(String username) {
        SocialState state = stateMap.get(username);
        if (state != null) return state;

        boolean self = currentUsername.equalsIgnoreCase(username);
        SocialState fallback = new SocialState(self, FriendStatus.NONE);
        stateMap.put(username, fallback);
        return fallback;
    }

    public void sendFriendRequest(String username) {
        SocialState state = getState(username);
        if (state.isSelfProfile() || state.isBlocked()) return;
        if (state.getFriendStatus() == FriendStatus.NONE) {
            state.setFriendStatus(FriendStatus.PENDING_SENT);
        }
    }

    public void acceptFriendRequest(String username) {
        SocialState state = getState(username);
        if (state.getFriendStatus() == FriendStatus.PENDING_RECEIVED) {
            state.setFriendStatus(FriendStatus.FRIEND);
        }
    }

    public void declineFriendRequest(String username) {
        SocialState state = getState(username);
        if (state.getFriendStatus() == FriendStatus.PENDING_RECEIVED) {
            state.setFriendStatus(FriendStatus.NONE);
        }
    }

    public void cancelFriendRequest(String username) {
        SocialState state = getState(username);
        if (state.getFriendStatus() == FriendStatus.PENDING_SENT) {
            state.setFriendStatus(FriendStatus.NONE);
        }
    }

    public void unfriend(String username) {
        SocialState state = getState(username);
        if (state.isFriend()) {
            state.setFriendStatus(FriendStatus.NONE);
        }
    }

    public void blockUser(String username) {
        SocialState state = getState(username);
        if (state.isSelfProfile()) return;
        state.setFriendStatus(FriendStatus.BLOCKED);
    }

    public void unblockUser(String username) {
        SocialState state = getState(username);
        if (state.isBlocked()) {
            state.setFriendStatus(FriendStatus.NONE);
        }
    }

    public int getFriendCount() {
        int count = 0;
        for (SocialState state : stateMap.values()) {
            if (!state.isSelfProfile() && state.isFriend()) {
                count++;
            }
        }
        return count;
    }

    public List<String> getFriendNames() {
        List<String> friends = new ArrayList<>();
        for (Map.Entry<String, SocialState> entry : stateMap.entrySet()) {
            if (!entry.getValue().isSelfProfile() && entry.getValue().isFriend()) {
                friends.add(entry.getKey());
            }
        }
        return friends;
    }

    public List<String> getBlockedUsers() {
        List<String> blocked = new ArrayList<>();
        for (Map.Entry<String, SocialState> entry : stateMap.entrySet()) {
            if (!entry.getValue().isSelfProfile() && entry.getValue().isBlocked()) {
                blocked.add(entry.getKey());
            }
        }
        return blocked;
    }

    private void seed() {
        stateMap.put(currentUsername, new SocialState(true, FriendStatus.NONE));
        // Không seed fake data - chỉ dùng dữ liệu thật từ backend
    }
}
