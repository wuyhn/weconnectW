package com.example.weconnect.util;

import android.view.View;
import android.widget.TextView;

/**
 * Global singleton giữ trạng thái unread count cho notification và chat.
 * Dùng để badge hiển thị nhất quán trên tất cả các tab Activity.
 */
public class BadgeManager {

    private static int unreadCount = 0;
    private static int chatUnreadCount = 0;

    // ── Chat unread ──
    public static synchronized void setChatCount(int count) {
        chatUnreadCount = Math.max(0, count);
    }

    public static synchronized int getChatCount() {
        return chatUnreadCount;
    }

    public static synchronized void incrementChat() {
        chatUnreadCount++;
    }

    public static synchronized void resetChat() {
        chatUnreadCount = 0;
    }

    // ── Notification unread ──
    public static void setCount(int count) {
        unreadCount = Math.max(0, count);
    }

    public static int getCount() {
        return unreadCount;
    }

    public static void reset() {
        unreadCount = 0;
    }

    public static void increment() {
        unreadCount++;
    }

    public static void decrement() {
        if (unreadCount > 0) unreadCount--;
    }

    public static void decrement(int n) {
        unreadCount = Math.max(0, unreadCount - n);
    }

    /** Cập nhật visibility + text của một badge TextView theo count hiện tại. */
    public static void applyBadge(TextView badge) {
        if (badge == null) return;
        if (unreadCount > 0) {
            badge.setVisibility(View.VISIBLE);
            badge.setText(unreadCount > 99 ? "99+" : String.valueOf(unreadCount));
        } else {
            badge.setVisibility(View.GONE);
        }
    }
}
