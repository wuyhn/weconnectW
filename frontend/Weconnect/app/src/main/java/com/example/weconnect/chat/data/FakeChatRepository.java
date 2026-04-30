package com.example.weconnect.chat.data;

import com.example.weconnect.R;
import com.example.weconnect.chat.data.ChatMessage;
import com.example.weconnect.chat.data.ChatRoom;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class FakeChatRepository {

    private static FakeChatRepository instance;

    private final List<ChatRoom> chatRooms = new ArrayList<>();

    private FakeChatRepository() {
        seedRooms();
    }

    public static synchronized FakeChatRepository getInstance() {
        if (instance == null) {
            instance = new FakeChatRepository();
        }
        return instance;
    }

    public static synchronized void resetInstance() {
        instance = null;
    }

    public List<ChatRoom> getChatRooms() {
        return new ArrayList<>(chatRooms);
    }

    public List<ChatRoom> searchChatRooms(String query) {
        return searchChatRoomsByType(null, query);
    }

    public List<ChatRoom> getGroupChatRooms() {
        return searchChatRoomsByType(ChatRoom.TYPE_GROUP, "");
    }

    public List<ChatRoom> getDirectChatRooms() {
        return searchChatRoomsByType(ChatRoom.TYPE_DIRECT, "");
    }

    public List<ChatRoom> searchChatRoomsByType(String type, String query) {
        List<ChatRoom> results = new ArrayList<>();
        String normalized = query == null ? "" : query.trim().toLowerCase();

        for (ChatRoom room : chatRooms) {
            boolean matchesType;
            if (type == null) {
                matchesType = true;
            } else if (ChatRoom.TYPE_DIRECT.equals(type)) {
                // "Liên hệ" tab shows both direct and friend_group
                matchesType = ChatRoom.TYPE_DIRECT.equals(room.getType())
                        || ChatRoom.TYPE_FRIEND_GROUP.equals(room.getType());
            } else {
                matchesType = type.equals(room.getType());
            }

            if (!matchesType) {
                continue;
            }

            if (normalized.isEmpty()) {
                results.add(room);
                continue;
            }

            String title = room.getTitle() == null ? "" : room.getTitle().toLowerCase();
            String preview = room.getLastMessagePreview() == null ? "" : room.getLastMessagePreview().toLowerCase();
            if (title.contains(normalized) || preview.contains(normalized)) {
                results.add(room);
            }
        }

        // Sắp xếp theo tin nhắn mới nhất lên đầu
        results.sort((a, b) -> Long.compare(b.getLastActivityTime(), a.getLastActivityTime()));

        return results;
    }

    public ChatRoom getRoomById(String roomId) {
        for (ChatRoom room : chatRooms) {
            if (room.getId().equals(roomId)) {
                return room;
            }
        }
        return null;
    }

    public void sendMessage(String roomId, String senderName, String content) {
        ChatRoom room = getRoomById(roomId);
        if (room == null) {
            return;
        }
        room.addMessage(new ChatMessage(
                String.valueOf(System.currentTimeMillis()),
                senderName,
                content,
                "Now",
                true
        ));
    }

    public ChatRoom findDirectRoom(String participantName) {
        for (ChatRoom room : chatRooms) {
            if (ChatRoom.TYPE_DIRECT.equals(room.getType()) &&
                    room.getTitle() != null &&
                    room.getTitle().equalsIgnoreCase(participantName)) {
                return room;
            }
        }
        return null;
    }

    public ChatRoom getOrCreateDirectRoom(String participantName) {
        ChatRoom existing = findDirectRoom(participantName);
        if (existing != null) return existing;

        String currentUser = FakePostRepository.getInstance().getCurrentUsername();
        ChatRoom newRoom = new ChatRoom(
                "room_direct_" + participantName.toLowerCase().replaceAll("\\s+", "_"),
                participantName,
                ChatRoom.TYPE_DIRECT,
                R.drawable.ic_user_placeholder,
                true,
                "",
                new ArrayList<>(),
                currentUser,
                new ArrayList<>(Arrays.asList(currentUser, participantName)),
                new ArrayList<>()
        );
        chatRooms.add(newRoom);
        return newRoom;
    }

    public ChatRoom createGroupChat(String title, List<String> members) {
        // Check for existing group chat with same title
        ChatRoom existing = findGroupRoomByTitle(title);
        if (existing != null) {
            return existing;
        }

        String currentUser = FakePostRepository.getInstance().getCurrentUsername();
        List<String> allMembers = new ArrayList<>();
        allMembers.add(currentUser);
        for (String member : members) {
            if (!member.equalsIgnoreCase(currentUser)) {
                allMembers.add(member);
            }
        }

        String roomId = "room_friend_group_" + System.currentTimeMillis();
        ChatRoom newRoom = new ChatRoom(
                roomId,
                title,
                ChatRoom.TYPE_FRIEND_GROUP,
                R.drawable.ic_user_placeholder,
                true,
                "",
                new ArrayList<>(),
                currentUser,
                allMembers,
                new ArrayList<>()
        );
        chatRooms.add(0, newRoom);
        return newRoom;
    }

    public ChatRoom findGroupRoomByTitle(String title) {
        for (ChatRoom room : chatRooms) {
            if ((ChatRoom.TYPE_GROUP.equals(room.getType()) || ChatRoom.TYPE_FRIEND_GROUP.equals(room.getType())) &&
                    room.getTitle() != null &&
                    room.getTitle().equalsIgnoreCase(title)) {
                return room;
            }
        }
        return null;
    }

    /**
     * Tạo hoặc lấy nhóm chat hoạt động (TYPE_GROUP) dựa trên postId.
     * Khi user được approve tham gia hoạt động, sẽ tạo room mới hoặc add member vào room cũ.
     */
    public ChatRoom getOrCreateActivityGroupChat(String postId, String postTitle, String ownerUsername) {
        String roomId = "room_activity_" + postId;
        ChatRoom existing = getRoomById(roomId);
        if (existing != null) return existing;

        // Also check by title for backwards compatibility
        for (ChatRoom room : chatRooms) {
            if (ChatRoom.TYPE_GROUP.equals(room.getType()) &&
                    room.getTitle() != null &&
                    room.getTitle().equalsIgnoreCase(postTitle)) {
                return room;
            }
        }

        String currentUser = FakePostRepository.getInstance().getCurrentUsername();
        List<String> members = new ArrayList<>();
        if (ownerUsername != null && !ownerUsername.isEmpty()) {
            members.add(ownerUsername);
        }
        if (currentUser != null && !members.contains(currentUser)) {
            members.add(currentUser);
        }

        ChatRoom newRoom = new ChatRoom(
                roomId,
                postTitle,
                ChatRoom.TYPE_GROUP,
                R.drawable.ic_user_placeholder,
                true,
                "",
                new ArrayList<>(),
                ownerUsername,
                members,
                new ArrayList<>()
        );
        chatRooms.add(0, newRoom);
        return newRoom;
    }

    /**
     * Add a member to an existing activity group chat.
     */
    public void addMemberToActivityChat(String postId, String username) {
        String roomId = "room_activity_" + postId;
        ChatRoom room = getRoomById(roomId);
        if (room != null) {
            room.addMember(username);
        }
    }

    private void seedRooms() {
        // Không seed fake data - chỉ dùng dữ liệu thật từ backend
    }
}
