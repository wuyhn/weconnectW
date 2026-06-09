package com.weconnect.backend.service;

import com.weconnect.backend.controller.TagController;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    private static final Logger log = LoggerFactory.getLogger(GeminiService.class);
    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent";

    // Nguồn sự thật duy nhất: TagController.SYSTEM_TAGS — không duy trì bản sao ở đây nữa
    private static final List<String> SYSTEM_TAGS = TagController.SYSTEM_TAGS;

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final RestClient restClient = RestClient.create();

    // -----------------------------------------------------------------------
    // NV1-A: Gợi ý tag từ nội dung văn bản bài đăng
    // -----------------------------------------------------------------------

    /**
     * Phân tích ngữ nghĩa văn bản và chọn ĐÚNG 1 tag phù hợp nhất trong 60 tag chính thức.
     *
     * Quy tắc trả về:
     * - Tag trong danh sách: trả về CHÍNH XÁC tên tag (bao gồm emoji, vd: "⚽ Đá bóng sân cỏ")
     * - Xu hướng mới ngoài danh sách: "TREND:TênXuHướng" (Controller sẽ xử lý)
     * - Nội dung vô định không liên quan đến hoạt động: ""
     */
    public String getSuggestedTagFromText(String postText) {
        log.info("[TAG-DEBUG] ▶ getSuggestedTagFromText gọi với postText='{}'",
                postText == null ? "NULL" : postText.substring(0, Math.min(postText.length(), 80)));

        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            log.warn("[TAG-DEBUG] ✗ Gemini API key chưa được cấu hình.");
            return "";
        }
        log.info("[TAG-DEBUG] ✓ API key có độ dài={}", geminiApiKey.length());

        if (postText == null || postText.isBlank()) {
            log.warn("[TAG-DEBUG] ✗ postText null hoặc rỗng");
            return "";
        }

        // Liệt kê đầy đủ 60 tag cho prompt — Gemini phải sao chép y nguyên (kể cả emoji)
        String tagListStr = String.join(" | ", SYSTEM_TAGS);

        String prompt = """
                Bạn là AI phân loại nội dung cho mạng xã hội kết nối hoạt động WeConnect.

                DANH SÁCH 60 TAG CHÍNH THỨC (sao chép NGUYÊN VĂN tag bạn chọn, kể cả emoji):
                %s

                NHIỆM VỤ: Đọc văn bản bài đăng và chọn ĐÚNG 1 tag phù hợp nhất theo quy tắc:
                1. Trả về TAG CHÍNH XÁC từ danh sách (sao chép nguyên văn, kể cả emoji và dấu cách).
                2. Nếu bài đề cập nhiều hoạt động, chọn hoạt động CHÍNH được nhắc đến đầu tiên hoặc chiếm trọng tâm.
                3. Nếu nội dung đề cập xu hướng giải trí lành mạnh MỚI nằm NGOÀI 60 tag trên, trả về: TREND:TênXuHướng
                4. Nếu nội dung HOÀN TOÀN không liên quan hoạt động kết nối (tâm sự cảm xúc, thông báo chung chung), trả về chuỗi RỖNG.
                5. TUYỆT ĐỐI KHÔNG thêm giải thích, dấu ngoặc kép, dấu sao markdown, hay bất kỳ ký tự thừa nào.

                Văn bản cần phân tích: "%s"
                """.formatted(tagListStr, postText);

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        // Không dùng thinkingConfig — extractText() đã xử lý được cả thinking lẫn non-thinking.
        // thinkingBudget:0 trên gemini-2.5-flash có thể gây 400 Bad Request trên một số phiên bản API.
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        try {
            log.info("[TAG-DEBUG] → Gọi Gemini API (model={})...", GEMINI_API_URL);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(GEMINI_API_URL + "?key=" + geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            log.info("[TAG-DEBUG] ← Gemini response keys: {}",
                    response == null ? "NULL" : response.keySet());

            String rawExtracted = extractText(response);
            log.info("[TAG-DEBUG] extractText() trả về: '{}'", rawExtracted);

            String cleaned = rawExtracted.replace("\n", "");
            log.info("[TAG-DEBUG] cleaned sau replace \\n: '{}'", cleaned);

            String result = parseTagResult(cleaned);
            log.info("[TAG-DEBUG] ✓ parseTagResult() → '{}'", result);

            // Nếu Gemini trả về rỗng, thử keyword fallback
            if (result.isBlank()) {
                String fallback = keywordFallbackTag(postText);
                log.info("[TAG-DEBUG] Gemini rỗng → keyword fallback='{}'", fallback);
                return fallback;
            }
            return result;

        } catch (Exception e) {
            log.error("[TAG-DEBUG] ✗ Gemini call thất bại: {} — {}", e.getClass().getSimpleName(), e.getMessage());
            log.info("[TAG-DEBUG] → Fallback sang keyword matching...");
            String fallback = keywordFallbackTag(postText);
            log.info("[TAG-DEBUG] ← Keyword fallback kết quả: '{}'", fallback);
            return fallback;
        }
    }

    /**
     * So khớp chuỗi đã làm sạch với danh sách tag chính thức.
     *
     * Ưu tiên 3 tầng:
     *   1. Khớp chính xác (equalsIgnoreCase) — trường hợp lý tưởng
     *   2. Khớp chuẩn hóa qua normalize() — xử lý trường hợp Gemini bỏ emoji hoặc viết tắt nhẹ
     *   3. Không khớp → "" (không đoán bừa)
     *
     * Xu hướng mới (TREND:) trả về nguyên dạng để Controller xử lý riêng.
     */
    private String parseTagResult(String cleaned) {
        if (cleaned == null || cleaned.isBlank()) return "";

        // Xu hướng mới nằm ngoài 60 tag — giữ prefix TREND: để PostController ghi log và thay thế
        if (cleaned.toUpperCase().startsWith("TREND:")) {
            String trendName = cleaned.substring(cleaned.indexOf(':') + 1).trim();
            return trendName.isBlank() ? "" : "TREND:" + trendName;
        }

        // Tầng 1: So khớp chính xác (không phân biệt hoa/thường, bao gồm emoji)
        for (String tag : SYSTEM_TAGS) {
            if (tag.equalsIgnoreCase(cleaned)) return tag;
        }

        // Tầng 2: So khớp chuẩn hóa — loại bỏ emoji, ký tự đặc biệt, khoảng trắng, lowercase
        // Xử lý trường hợp Gemini trả về tag thiếu emoji hoặc dùng từ đồng nghĩa nhẹ
        String normCleaned = normalize(cleaned);
        if (!normCleaned.isBlank()) {
            for (String tag : SYSTEM_TAGS) {
                String normTag = normalize(tag);
                if (normTag.equals(normCleaned)
                        || normTag.contains(normCleaned)
                        || normCleaned.contains(normTag)) {
                    return tag; // Trả về tag CHÍNH XÁC từ SYSTEM_TAGS (đầy đủ emoji)
                }
            }
        }

        // Không khớp tag nào — không đoán bừa, để user tự chọn thủ công
        log.debug("Gemini trả về kết quả không khớp tag nào: \"{}\"", cleaned);
        return "";
    }

    /**
     * Chuẩn hóa chuỗi để so khớp tương đối:
     * Xóa emoji, ký hiệu đặc biệt, dấu gạch chéo, khoảng trắng;
     * Chỉ giữ chữ cái Unicode (bao gồm tiếng Việt có dấu) và số; lowercase.
     */
    private String normalize(String s) {
        if (s == null) return "";
        // \p{L} khớp mọi chữ cái Unicode (gồm tiếng Việt có dấu), \p{N} khớp số
        return s.replaceAll("[^\\p{L}\\p{N}]", "").toLowerCase().trim();
    }

    // -----------------------------------------------------------------------
    // NV1-B: AI Firewall — kiểm duyệt an toàn hình ảnh bằng Gemini Vision
    // -----------------------------------------------------------------------

    /**
     * Kiểm tra ảnh có an toàn không bằng Gemini Vision.
     * Đọc file ảnh từ /uploads/ trên server, encode base64, gửi lên Gemini để phân tích.
     *
     * @return true nếu ảnh an toàn, false nếu có nội dung vi phạm
     */
    public boolean isImageSafe(String imageUrl) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            // Fail open: không có API key thì không chặn (tránh làm hỏng tính năng upload)
            log.warn("Gemini API key chưa được cấu hình, bỏ qua kiểm duyệt ảnh.");
            return true;
        }
        if (imageUrl == null || imageUrl.isBlank()) return true;

        try {
            byte[] imageBytes = readImageBytes(imageUrl);
            if (imageBytes == null || imageBytes.length == 0) {
                // Fail open: không đọc được file thì không chặn
                log.warn("Không đọc được file ảnh tại: {}", imageUrl);
                return true;
            }

            String base64Image = Base64.getEncoder().encodeToString(imageBytes);
            String mimeType = detectMimeType(imageUrl);

            String promptText = """
                    Bạn là hệ thống kiểm duyệt nội dung nghiêm ngặt cho mạng xã hội lành mạnh.
                    Phân tích hình ảnh và kiểm tra xem nó có chứa bất kỳ nội dung vi phạm nào sau không:
                    - Nội dung người lớn, khỏa thân, tình dục
                    - Bạo lực, hình ảnh gây sốc hoặc kinh dị
                    - Hình ảnh kỳ thị, thù hận, phân biệt chủng tộc
                    - Nội dung có hại, phản cảm khác

                    Chỉ trả lời đúng một từ: SAFE nếu ảnh hoàn toàn an toàn, UNSAFE nếu vi phạm bất kỳ điều nào trên.
                    """;

            Map<String, Object> textPart = Map.of("text", promptText);
            Map<String, Object> imagePart = Map.of(
                    "inlineData", Map.of("mimeType", mimeType, "data", base64Image)
            );
            Map<String, Object> content = Map.of("parts", List.of(textPart, imagePart));
            Map<String, Object> requestBody = Map.of("contents", List.of(content));

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(GEMINI_API_URL + "?key=" + geminiApiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            String result = extractText(response).trim().toUpperCase();
            // Ảnh an toàn khi kết quả chứa "SAFE" mà KHÔNG chứa "UNSAFE"
            return result.contains("SAFE") && !result.contains("UNSAFE");

        } catch (Exception e) {
            // Fail open: lỗi kết nối Gemini thì không chặn người dùng
            log.error("Gemini Vision check thất bại: {}", e.getMessage());
            return true;
        }
    }

    // Đọc bytes ảnh từ server:
    //   - /uploads/xxx.jpg  → đọc trực tiếp từ disk (đường dẫn local)
    //   - http://...        → download qua HTTP (fallback cho external URL)
    private byte[] readImageBytes(String imageUrl) {
        try {
            if (imageUrl.startsWith("/uploads/")) {
                String filename = imageUrl.substring("/uploads/".length());
                Path filePath = Path.of("uploads", filename);
                if (Files.exists(filePath)) {
                    return Files.readAllBytes(filePath);
                }
                log.warn("File ảnh không tồn tại trên disk: {}", filePath);
                return null;
            }
            if (imageUrl.startsWith("http")) {
                return restClient.get().uri(imageUrl).retrieve().body(byte[].class);
            }
        } catch (Exception e) {
            log.error("Lỗi đọc ảnh từ {}: {}", imageUrl, e.getMessage());
        }
        return null;
    }

    // Xác định MIME type từ đuôi file để gửi đúng cho Gemini
    private String detectMimeType(String imageUrl) {
        String lower = imageUrl.toLowerCase();
        if (lower.endsWith(".png"))  return "image/png";
        if (lower.endsWith(".gif"))  return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg"; // mặc định
    }

    // -----------------------------------------------------------------------
    // Keyword-based fallback — chạy khi Gemini API không khả dụng (quota/lỗi)
    // -----------------------------------------------------------------------

    /**
     * Match tag bằng từ khóa tiếng Việt khi Gemini API không khả dụng.
     * Kiểm tra theo thứ tự từ cụ thể → chung để tránh match sai.
     */
    private String keywordFallbackTag(String postText) {
        if (postText == null || postText.isBlank()) return "";
        String t = postText.toLowerCase();

        // === Thể thao ===
        if (containsAny(t, "escape room", "giải mật phòng", "phòng thoát"))  return "🧩 Đi giải mật phòng (Escape Room)";
        if (containsAny(t, "pickleball"))                                      return "🎾 Đánh Pickleball";
        if (containsAny(t, "cầu lông", "badminton"))                           return "🏸 Đánh cầu lông";
        if (containsAny(t, "bóng rổ", "basketball"))                           return "🏀 Đánh bóng rổ";
        if (containsAny(t, "bóng chuyền", "volleyball"))                       return "🏐 Đánh bóng chuyền";
        if (containsAny(t, "đá bóng", "sân bóng", "sân cỏ", "bóng đá", "football", "soccer")) return "⚽ Đá bóng sân cỏ";
        if (containsAny(t, "bóng"))                                            return "⚽ Đá bóng sân cỏ";
        if (containsAny(t, "chạy bộ", "jogging", "marathon"))                 return "🏃 Chạy bộ công viên";
        if (containsAny(t, "đạp xe", "xe đạp", "cycling"))                    return "🚴 Đạp xe đường phố";
        if (containsAny(t, "bơi lội", "hồ bơi", "bơi"))                      return "🏊 Đi bơi hồ";
        if (containsAny(t, "trượt ván", "patin", "skateboard"))                return "🛹 Trượt ván / Patin";
        if (containsAny(t, "leo núi nhân tạo", "leo tường", "bouldering"))    return "🧗 Leo núi nhân tạo";
        if (containsAny(t, "yoga", "pilates", "thiền"))                        return "🧘 Tập Yoga / Pilates";
        if (containsAny(t, "gym", "calisthenics", "tập tạ", "fitness"))        return "🏋️ Tập Gym / Calisthenics";

        // === Giải trí ===
        if (containsAny(t, "karaoke"))                                          return "🎤 Đi hát Karaoke";
        if (containsAny(t, "xem phim", "rạp phim", "rạp chiếu", "cinema"))    return "🎬 Xem phim rạp";
        if (containsAny(t, "concert", "nhạc sống", "nghe nhạc"))               return "🎵 Đi nghe nhạc / Concert";
        if (containsAny(t, "liên quân", "liên minh", "pubg", "free fire", "mobile legend")) return "📱 Chơi Mobile Game / Liên Quân";
        if (containsAny(t, "board game", "boardgame", "ma soi", "cờ tỉ phú")) return "🎲 Chơi Board game / Ma soi";
        if (containsAny(t, "chơi game", "gaming", "console", "playstation", "xbox")) return "🎮 Chơi Game (PC/Console)";
        if (containsAny(t, "chụp ảnh", "check-in", "checkin", "nhiếp ảnh"))   return "📸 Đi chụp ảnh / Check-in";
        if (containsAny(t, "vẽ tranh", "vẽ vời", "painting"))                  return "🎨 Vẽ tranh thư giãn";
        if (containsAny(t, "vũ đạo", "nhảy múa", "dance", "nhảy"))            return "💃 Học nhảy / Vũ đạo";
        if (containsAny(t, "kịch", "stand-up", "hài kịch"))                    return "🎭 Xem kịch / Xem Stand-up Comedy";

        // === Học tập / Tech ===
        if (containsAny(t, "ôn thi", "giải đề", "thi cử"))                    return "📝 Ôn thi / Giải đề";
        if (containsAny(t, "đọc sách", "thư viện", "sách"))                   return "📖 Đọc sách tại thư viện";
        if (containsAny(t, "tiếng anh", "english", "luyện tiếng"))             return "🌍 Luyện nói tiếng Anh";
        if (containsAny(t, "tiếng nhật", "tiếng trung", "tiếng hàn", "japanese", "korean")) return "🎌 Học tiếng Nhật / Trung / Hàn";
        if (containsAny(t, "machine learning", "data science", "deep learning", "lập trình ai")) return "🤖 Lập trình AI / Học Data Science";
        if (containsAny(t, "hackathon", "lập trình", "coding", "code", "developer")) return "💻 Lập trình dự án / Hackathon";
        if (containsAny(t, "thiết kế", "design", "figma", "photoshop", "ui/ux")) return "📐 Thiết kế đồ họa / UI-UX";
        if (containsAny(t, "khởi nghiệp", "startup", "kinh doanh"))            return "💼 Thảo luận ý tưởng khởi nghiệp";
        if (containsAny(t, "nghiên cứu", "thí nghiệm", "research"))            return "🔬 Làm thí nghiệm / Nghiên cứu";
        if (containsAny(t, "học nhóm", "chạy deadline", "deadline"))           return "☕ Học nhóm / Chạy deadline";

        // === Đời sống / Travel ===
        if (containsAny(t, "cắm trại", "camping", "lều trại", "dã ngoại"))    return "🏕️ Đi cắm trại / Camping";
        if (containsAny(t, "leo núi", "trekking", "hiking"))                   return "🧗 Leo núi tự nhiên / Trekking";
        if (containsAny(t, "du lịch", "phượt", "travel", "đi xa"))            return "✈️ Đi du lịch xa / Phượt";
        if (containsAny(t, "picnic", "dạo công viên", "công viên"))            return "🌿 Đi dạo công viên / Picnic";
        if (containsAny(t, "thú cưng", "pet", "chó mèo"))                     return "🐕 Đi offline giao lưu thú cưng";
        if (containsAny(t, "tình nguyện", "từ thiện", "volunteer"))            return "🎪 Làm tình nguyện / Từ thiện";
        if (containsAny(t, "mua sắm", "shopping", "mall"))                     return "🛍️ Đi mua sắm / Shopping";
        if (containsAny(t, "câu cá", "fishing"))                               return "🎣 Đi câu cá thư giãn";

        // === Giao lưu / Hobby ===
        if (containsAny(t, "nhậu", "bia hơi", "nhậu nhẹt", "chill"))         return "🍻 Nhậu nhẹt / Chill cuối tuần";
        if (containsAny(t, "guitar", "piano", "violin", "nhạc cụ", "đàn"))    return "🎸 Tập chơi nhạc cụ (Guitar/Piano)";
        if (containsAny(t, "lego", "rubik", "xếp hình"))                       return "🧩 Xếp hình Lego / Giải Rubik";
        if (containsAny(t, "viết blog", "viết lách", "blog"))                  return "✍️ Viết lách / Viết Blog";
        if (containsAny(t, "tiktok", "quay video", "vlog"))                    return "🎬 Quay Video / Làm Tiktok";
        if (containsAny(t, "tarot", "chiêm tinh", "astrology"))                return "🔮 Xem bài Tarot / Chiêm tinh";
        if (containsAny(t, "nấu ăn", "làm bánh", "nấu nướng"))                return "🍳 Tụ tập nấu ăn / Làm bánh";
        if (containsAny(t, "trồng cây", "làm vườn", "garden"))                return "🪴 Trồng cây / Làm vườn";
        if (containsAny(t, "thêu thùa", "thủ công", "handmade", "craft"))     return "🪡 Thêu thùa / Làm đồ thủ công";

        // === Trí tuệ / Trend ===
        if (containsAny(t, "cờ vua", "cờ tướng", "chess"))                    return "♟️ Đánh cờ vua / Cờ tướng";
        if (containsAny(t, "debate", "hùng biện", "diễn thuyết"))              return "🎤 Tập nói trước đám đông / Debate";
        if (containsAny(t, "tài chính", "đầu tư", "chứng khoán"))              return "💸 Học quản lý tài chính cá nhân";
        if (containsAny(t, "tập lái", "lái xe", "bằng lái"))                  return "🚗 Tập lái xe / Trải nghiệm xe";
        if (containsAny(t, "bắn cung", "phi tiêu", "archery"))                 return "🎯 Chơi bắn cung / Phi tiêu";
        if (containsAny(t, "bowling"))                                          return "🎳 Chơi Bowling";
        if (containsAny(t, "lễ hội", "fandom", "idol", "festival"))            return "🎈 Tham gia lễ hội / Fandom";

        // === Thực phẩm / Cafe ===
        if (containsAny(t, "cafe", "cà phê", "coffee"))                        return "☕ Đi Cafe cà pháo";
        if (containsAny(t, "food tour"))                                        return "🍽️ Food Tour";
        if (containsAny(t, "foodtour", "ẩm thực", "quán ăn", "đồ ăn"))       return "🍜 Đi Foodtour / Ăn sập phố cổ";

        // === Giao lưu chung ===
        if (containsAny(t, "tâm sự", "tán gẫu", "hướng nội"))                 return "💬 Trò chuyện tâm sự / Hướng nội";

        return "";
    }

    private boolean containsAny(String text, String... keywords) {
        for (String kw : keywords) {
            if (text.contains(kw)) return true;
        }
        return false;
    }

    /**
     * Trích xuất text thực từ Gemini response, bỏ qua thinking parts.
     *
     * gemini-2.5-flash trả về response dạng:
     *   candidates[0].content.parts = [
     *     { "thought": true,  "text": "<suy luận dài>" },   ← BỎ QUA
     *     { "thought": false, "text": "<câu trả lời thực>" } ← LẤY CÁI NÀY
     *   ]
     * Khi thinkingBudget=0, parts chỉ có 1 phần tử không có "thought".
     * Hàm này xử lý an toàn cả 2 trường hợp.
     *
     * String cleaning được áp dụng ngay tại đây:
     *   - Xóa dấu nháy kép/đơn, dấu sao markdown, ký tự carriage-return
     *   - trim() trước khi trả về
     */
    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        try {
            // Guard tầng 1: response null
            if (response == null) {
                log.warn("Gemini trả về response null");
                return "";
            }

            // Guard tầng 2: candidates
            List<Map<String, Object>> candidates =
                    (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                log.warn("[TAG-DEBUG] extractText ✗ không có 'candidates'. Keys: {}", response.keySet());
                return "";
            }
            log.info("[TAG-DEBUG] extractText: candidates.size()={}", candidates.size());

            // Guard tầng 3: candidate[0]
            Map<String, Object> candidate = candidates.get(0);
            if (candidate == null) {
                log.warn("[TAG-DEBUG] extractText ✗ candidates[0] là null");
                return "";
            }
            log.info("[TAG-DEBUG] extractText: candidate keys={}", candidate.keySet());

            // Guard tầng 4: content
            Map<String, Object> contentObj = (Map<String, Object>) candidate.get("content");
            if (contentObj == null) {
                log.warn("[TAG-DEBUG] extractText ✗ không có 'content'. candidate keys: {}", candidate.keySet());
                return "";
            }
            log.info("[TAG-DEBUG] extractText: content keys={}", contentObj.keySet());

            // Guard tầng 5: parts
            List<Map<String, Object>> parts =
                    (List<Map<String, Object>>) contentObj.get("parts");
            if (parts == null || parts.isEmpty()) {
                log.warn("[TAG-DEBUG] extractText ✗ không có 'parts'");
                return "";
            }
            log.info("[TAG-DEBUG] extractText: parts.size()={}", parts.size());

            // Duyệt parts — bỏ qua thinking parts (thought=true)
            for (int i = 0; i < parts.size(); i++) {
                Map<String, Object> part = parts.get(i);
                if (part == null) continue;

                Object thought = part.get("thought");
                Object textObj = part.get("text");
                log.info("[TAG-DEBUG] extractText: parts[{}] thought={}, text length={}",
                        i, thought,
                        textObj instanceof String ? ((String) textObj).length() : "N/A");

                // Bỏ qua thinking text của gemini-2.5-flash
                if (Boolean.TRUE.equals(thought)) {
                    log.info("[TAG-DEBUG] extractText: bỏ qua parts[{}] (thinking text)", i);
                    continue;
                }

                if (!(textObj instanceof String)) continue;

                String text = (String) textObj;
                if (text.isBlank()) continue;

                String result = text.replaceAll("[\"'*\r]", "").trim();
                log.info("[TAG-DEBUG] extractText: ✓ lấy parts[{}], sau clean='{}'", i, result);
                return result;
            }

            log.warn("[TAG-DEBUG] extractText ✗ có {} parts nhưng không có text hợp lệ", parts.size());
            return "";

        } catch (Exception e) {
            log.error("Lỗi parse Gemini response: {}", e.getMessage());
            return "";
        }
    }
}
