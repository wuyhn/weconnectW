package com.weconnect.backend.controller;

import com.weconnect.backend.dto.request.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Quản lý danh sách tag sở thích chính thức của hệ thống WeConnect.
 *
 * SYSTEM_TAGS là nguồn sự thật duy nhất (single source of truth) cho toàn bộ hệ thống:
 *   - TagController (đây) expose qua REST API
 *   - GeminiService tham chiếu TagController.SYSTEM_TAGS thay vì giữ bản sao riêng
 *   - Android client fetch từ /api/tags/all khi mở màn hình tạo bài
 *
 * Quy tắc bất biến: MỌI thay đổi về tag phải sửa đúng một chỗ duy nhất — list này.
 */
@RestController
@RequestMapping("/api/tags")
public class TagController {

    /**
     * 60 tag chính thức của WeConnect, chia 6 nhóm chủ đề.
     * Khai báo public static final để GeminiService và các service khác có thể tái sử dụng
     * mà không cần inject TagController vào Spring context.
     *
     * Quy tắc format: "[emoji] [Tên tag đầy đủ]" — có emoji dẫn đầu, cách nhau 1 space.
     * Client dùng InterestTextUtils.stripLeadingIcon() để hiển thị, lưu full string làm key.
     */
    public static final List<String> SYSTEM_TAGS = List.of(

            // ── Nhóm 1: Thể thao (12 tags) ────────────────────────────────────────────
            "⚽ Đá bóng sân cỏ",
            "🏸 Đánh cầu lông",
            "🏀 Đánh bóng rổ",
            "🏐 Đánh bóng chuyền",
            "🏃 Chạy bộ công viên",
            "🚴 Đạp xe đường phố",
            "🏊 Đi bơi hồ",
            "🎾 Đánh Pickleball",
            "🛹 Trượt ván / Patin",
            "🧗 Leo núi nhân tạo",
            "🧘 Tập Yoga / Pilates",
            "🏋️ Tập Gym / Calisthenics",

            // ── Nhóm 2: Giải trí (10 tags) ────────────────────────────────────────────
            "🎬 Xem phim rạp",
            "🎵 Đi nghe nhạc / Concert",
            "🎤 Đi hát Karaoke",
            "🎮 Chơi Game (PC/Console)",
            "📱 Chơi Mobile Game / Liên Quân",
            "🎲 Chơi Board game / Ma soi",
            "📸 Đi chụp ảnh / Check-in",
            "🎨 Vẽ tranh thư giãn",
            "💃 Học nhảy / Vũ đạo",
            "🎭 Xem kịch / Xem Stand-up Comedy",

            // ── Nhóm 3: Học tập / Công nghệ (10 tags) ────────────────────────────────
            "☕ Học nhóm / Chạy deadline",
            "📖 Đọc sách tại thư viện",
            "🌍 Luyện nói tiếng Anh",
            "🎌 Học tiếng Nhật / Trung / Hàn",
            "💻 Lập trình dự án / Hackathon",
            "📐 Thiết kế đồ họa / UI-UX",
            "📝 Ôn thi / Giải đề",
            "💼 Thảo luận ý tưởng khởi nghiệp",
            "🔬 Làm thí nghiệm / Nghiên cứu",
            "🤖 Lập trình AI / Học Data Science",

            // ── Nhóm 4: Đời sống / Du lịch (10 tags) ────────────────────────────────
            "☕ Đi Cafe cà pháo",
            "🍜 Đi Foodtour / Ăn sập phố cổ",
            "🍽️ Food Tour",
            "✈️ Đi du lịch xa / Phượt",
            "🏕️ Đi cắm trại / Camping",
            "🌿 Đi dạo công viên / Picnic",
            "🧗 Leo núi tự nhiên / Trekking",
            "🐕 Đi offline giao lưu thú cưng",
            "🎪 Làm tình nguyện / Từ thiện",
            "🛍️ Đi mua sắm / Shopping",
            "🎣 Đi câu cá thư giãn",

            // ── Nhóm 5: Giao lưu / Hobby (10 tags) ───────────────────────────────────
            "💬 Trò chuyện tâm sự / Hướng nội",
            "🍻 Nhậu nhẹt / Chill cuối tuần",
            "🎸 Tập chơi nhạc cụ (Guitar/Piano)",
            "🧩 Xếp hình Lego / Giải Rubik",
            "✍️ Viết lách / Viết Blog",
            "🎬 Quay Video / Làm Tiktok",
            "🔮 Xem bài Tarot / Chiêm tinh",
            "🍳 Tụ tập nấu ăn / Làm bánh",
            "🪴 Trồng cây / Làm vườn",
            "🪡 Thêu thùa / Làm đồ thủ công",

            // ── Nhóm 6: Xu hướng & Kỹ năng (8 tags) ─────────────────────────────────
            "♟️ Đánh cờ vua / Cờ tướng",
            "🎤 Tập nói trước đám đông / Debate",
            "💸 Học quản lý tài chính cá nhân",
            "🚗 Tập lái xe / Trải nghiệm xe",
            "🎯 Chơi bắn cung / Phi tiêu",
            "🎳 Chơi Bowling",
            "🎈 Tham gia lễ hội / Fandom",
            "🧩 Đi giải mật phòng (Escape Room)"
    );

    /**
     * Trả về toàn bộ 60 tag chính thức của hệ thống.
     *
     * Endpoint: GET /api/tags/all
     * Auth: Yêu cầu JWT token (chỉ user đã đăng nhập mới tạo bài được)
     *
     * Response trả về List<String> để client có thể render chip trực tiếp
     * mà không cần parse thêm. Mỗi phần tử là full string gồm emoji + tên tag.
     */
    @GetMapping("/all")
    public ResponseEntity<ApiResponse<List<String>>> getAllTags() {
        return ResponseEntity.ok(ApiResponse.<List<String>>builder()
                .code(1000)
                .message("Thành công")
                .result(SYSTEM_TAGS)
                .build());
    }
}
