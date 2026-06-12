package com.weconnect.backend.service;

import com.weconnect.backend.entity.ChatMessage;
import com.weconnect.backend.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class AISummaryService {

    private static final Logger log = LoggerFactory.getLogger(AISummaryService.class);
    private static final String GEMINI_API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent";

    @Value("${gemini.api.key:}")
    private String geminiApiKey;

    private final RestClient restClient = RestClient.create();
    private final UserRepository userRepository;

    public AISummaryService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public String summarize(List<ChatMessage> messages) {
        if (geminiApiKey == null || geminiApiKey.isBlank()) {
            throw new RuntimeException("Gemini API key chưa được cấu hình.");
        }
        if (messages == null || messages.isEmpty()) {
            throw new RuntimeException("Không có tin nhắn nào để tóm tắt.");
        }

        Map<Long, String> nameCache = new HashMap<>();
        StringBuilder sb = new StringBuilder();
        for (ChatMessage msg : messages) {
            if (msg.getSenderId() == 0L) continue; // bỏ tin hệ thống
            String sender = nameCache.computeIfAbsent(msg.getSenderId(), id ->
                    userRepository.findById(id)
                            .map(u -> u.getFullName() != null && !u.getFullName().isBlank()
                                    ? u.getFullName() : "Thành viên")
                            .orElse("Thành viên"));
            sb.append(sender).append(": ").append(msg.getContent()).append("\n");
        }
        String messagesText = sb.toString().trim();
        if (messagesText.isEmpty()) {
            throw new RuntimeException("Không có nội dung để tóm tắt.");
        }

        String prompt = """
                Bạn là AI core xử lý dữ liệu hội thoại của mạng xã hội WeConnect.
                Nhiệm vụ: Phân tích lịch sử chat nhóm và trích xuất thông tin lịch trình sang định dạng Text ngắn gọn cho giao diện Mobile.

                [BỘ LỌC CHÍNH TẢ & NGỮ NGHĨA]
                - Tự động sửa lỗi chính tả, gõ sai ký tự, thiếu dấu, viết tắt hoặc từ lóng dựa trên ngữ cảnh bóng đá (VD: "gôn" -> "thủ môn", "đá bống" -> "đá bóng", "hùng mjn giám" -> "Hoàng Minh Giám", "ok/okay/co/dc" -> "Đồng ý").
                - Tuyệt đối giữ nguyên Tên người dùng (Không thay bằng số hay "Thành viên X").

                [QUY TẮC PHÂN LOẠI CHẶT CHẼ]
                - 📅 Lịch trình sơ bộ: CHỈ ghi thông tin khi có sự xác nhận đồng thuận từ các thành viên liên quan bằng lời nói hoặc hành động (VD: Đã chốt sân, chốt giờ, chốt giá).
                - ❓ Các điểm chưa rõ: BẮT BUỘC đưa vào nếu thông tin chỉ từ 1 phía đề xuất, đang tranh luận giữa 2 con số/phương án, hoặc có thành viên gặp sự cố cá nhân chưa giải quyết xong.

                [CẤU TRÚC ĐẦU RA BẮT BUỘC - TỐI ĐA 80 TỪ]
                📅 Lịch trình sơ bộ
                - Thời gian: [Giờ, Ngày]
                - Địa điểm: [Tên sân, Địa chỉ]
                - Chi phí: [Mức giá/Quy định quỹ]

                ❓ Các điểm chưa rõ
                - [Tên người dùng] + [Vấn đề cụ thể cần làm rõ hoặc xử lý]

                [DỮ LIỆU ĐẦU VÀO]
                Dưới đây là %d tin nhắn gần nhất:

                %s

                [YÊU CẦU ĐẦU RA]
                Chỉ xuất đúng cấu trúc trên. Không thêm lời dẫn, lời chào. Khống chế dưới 80 từ."""
                .formatted(messages.size(), messagesText);

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> requestBody = Map.of("contents", List.of(content));

        int maxRetries = 3;
        int[] retryDelaysMs = {500, 1000};
        for (int attempt = 0; attempt <= maxRetries - 1; attempt++) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> response = restClient.post()
                        .uri(GEMINI_API_URL + "?key=" + geminiApiKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(requestBody)
                        .retrieve()
                        .body(Map.class);

                return extractText(response);
            } catch (org.springframework.web.client.HttpServerErrorException e) {
                log.warn("Gemini API server error attempt {}/{}: status={}", attempt + 1, maxRetries, e.getStatusCode());
                boolean isLastAttempt = (attempt == maxRetries - 1);
                if (isLastAttempt) {
                    throw new RuntimeException("Dich vu AI dang qua tai, vui long thu lai sau it phut.");
                }
                try { Thread.sleep(retryDelaysMs[attempt]); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); break; }
            } catch (org.springframework.web.client.HttpClientErrorException e) {
                log.error("Gemini API HTTP error: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
                if (e.getStatusCode().value() == 429) {
                    throw new RuntimeException("Gemini API da dat gioi han request. Vui long thu lai sau.");
                }
                if (e.getStatusCode().value() == 400) {
                    throw new RuntimeException("Gemini API tu choi yeu cau (co the API key khong hop le).");
                }
                throw new RuntimeException("Gemini API loi " + e.getStatusCode().value() + ". Vui long thu lai sau.");
            } catch (RuntimeException e) {
                log.error("Gemini summarize failed: {}", e.getMessage());
                throw e;
            } catch (Exception e) {
                log.error("Gemini API call failed: {}", e.getMessage());
                throw new RuntimeException("Khong the tom tat luc nay. Vui long thu lai sau.");
            }
        }
        throw new RuntimeException("Dich vu AI dang qua tai, vui long thu lai sau it phut.");
    }

    @SuppressWarnings("unchecked")
    private String extractText(Map<String, Object> response) {
        try {
            if (response == null) {
                throw new RuntimeException("Gemini API không trả về phản hồi.");
            }

            // Kiểm tra lỗi trả về trong body (một số trường hợp Gemini trả HTTP 200 nhưng có "error")
            if (response.containsKey("error")) {
                Map<String, Object> error = (Map<String, Object>) response.get("error");
                String errorMsg = error != null ? String.valueOf(error.get("message")) : "Lỗi không xác định";
                log.error("Gemini API returned error in body: {}", errorMsg);
                throw new RuntimeException("Gemini API lỗi: " + errorMsg);
            }

            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                // Kiểm tra promptFeedback xem có bị chặn không
                Map<String, Object> feedback = (Map<String, Object>) response.get("promptFeedback");
                if (feedback != null && feedback.get("blockReason") != null) {
                    log.error("Gemini blocked prompt, reason: {}", feedback.get("blockReason"));
                    throw new RuntimeException("Nội dung bị từ chối bởi AI (blockReason: " + feedback.get("blockReason") + ").");
                }
                log.error("Gemini response has no candidates. Full response keys: {}", response.keySet());
                throw new RuntimeException("Gemini AI không trả về kết quả. Vui lòng thử lại sau.");
            }

            Map<String, Object> candidate = candidates.get(0);
            String finishReason = (String) candidate.get("finishReason");

            Map<String, Object> contentObj = (Map<String, Object>) candidate.get("content");
            if (contentObj == null) {
                if ("SAFETY".equals(finishReason)) {
                    throw new RuntimeException("Nội dung bị chặn bởi bộ lọc an toàn của AI.");
                }
                log.error("Gemini candidate content is null, finishReason={}", finishReason);
                throw new RuntimeException("Gemini AI không trả về nội dung (finishReason: " + finishReason + ").");
            }

            List<Map<String, Object>> parts = (List<Map<String, Object>>) contentObj.get("parts");
            if (parts == null || parts.isEmpty()) {
                throw new RuntimeException("Gemini AI trả về nội dung rỗng.");
            }

            for (Map<String, Object> part : parts) {
                Boolean isThought = (Boolean) part.get("thought");
                if (isThought != null && isThought) continue; // bỏ qua thinking text
                String text = (String) part.get("text");
                if (text != null && !text.isBlank()) return text;
            }
            throw new RuntimeException("Lỗi phân tích phản hồi từ Gemini AI.");
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to extract Gemini response: {}", e.getMessage());
            throw new RuntimeException("Lỗi phân tích phản hồi từ Gemini AI.");
        }
    }
}
