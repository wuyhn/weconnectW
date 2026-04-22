package com.weconnect.backend.scheduler;

import com.weconnect.backend.service.PostService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler kiểm tra bài đăng hết hạn mỗi 1 phút.
 * - Auto-reject các pending requests
 * - Gửi thông báo POST_EXPIRED cho chủ bài viết
 */
@Component
public class PostExpirationScheduler {

    private static final Logger log = LoggerFactory.getLogger(PostExpirationScheduler.class);

    private final PostService postService;

    public PostExpirationScheduler(PostService postService) {
        this.postService = postService;
    }

    @Scheduled(fixedRate = 10000) // Mỗi 10 giây — near real-time
    public void checkExpiredPosts() {
        try {
            postService.checkAndNotifyExpiredPosts();
        } catch (Exception e) {
            log.error("Lỗi khi kiểm tra bài đăng hết hạn: {}", e.getMessage());
        }
    }
}
