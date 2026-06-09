package com.weconnect.backend.enums;

import com.weconnect.backend.entity.Report;

/**
 * Ma trận Mã Vi Phạm (Violation Code Matrix).
 *
 * Mỗi mã xác định:
 *   - Loại báo cáo nhắm vào (USER hay POST)
 *   - Điểm phạt cố định tương ứng
 *
 * Quy tắc mã "Khác":
 *   - U_OTHER / P_OTHER có fixedPenaltyPoint = -1 (đánh dấu điểm phạt do Admin tự chọn)
 *   - Backend sẽ validate customPenalty trong khoảng [0, 50]
 *   - Admin phải kèm adminNote ≥ 10 ký tự giải trình
 */
public enum ViolationCode {

    // ==========================================================
    //  NHÓM BÁO CÁO NGƯỜI DÙNG (targetType = USER)
    // ==========================================================

    /** Spam / Làm phiền người khác — phạt 10 điểm */
    SPAM            (Report.TargetType.USER, 10),

    /** Nội dung không phù hợp / Tục tĩu cá nhân — phạt 15 điểm */
    INAPPROPRIATE   (Report.TargetType.USER, 15),

    /** Lừa đảo / Giả mạo danh tính — phạt 30 điểm */
    FRAUD           (Report.TargetType.USER, 30),

    /** Quấy rối / Xúc phạm người khác — phạt 30 điểm */
    HARASSMENT      (Report.TargetType.USER, 30),

    /** Lý do Khác (Admin tự chọn điểm phạt qua Wheel Picker, khoảng [0, 50]) */
    U_OTHER         (Report.TargetType.USER, -1),

    // ==========================================================
    //  NHÓM BÁO CÁO BÀI VIẾT (targetType = POST)
    // ==========================================================

    /** Spam / Quảng cáo rác trong bài viết — phạt 5 điểm */
    SPAM_POST       (Report.TargetType.POST, 5),

    /** Thông tin sai lệch / Tin giả — phạt 10 điểm */
    MISLEADING      (Report.TargetType.POST, 10),

    /** Nội dung thô tục / Phản cảm trong bài — phạt 10 điểm */
    VULGAR          (Report.TargetType.POST, 10),

    /** Vi phạm quy định cộng đồng — phạt 10 điểm */
    VIOLATION       (Report.TargetType.POST, 10),

    /** Quấy rối / Bắt nạt qua bài viết — phạt 20 điểm */
    BULLYING        (Report.TargetType.POST, 20),

    /** Lý do Khác (Admin tự chọn điểm phạt qua Wheel Picker, khoảng [0, 50]) */
    P_OTHER         (Report.TargetType.POST, -1);

    // ----------------------------------------------------------

    private final Report.TargetType targetType;
    private final int fixedPenaltyPoint;

    ViolationCode(Report.TargetType targetType, int fixedPenaltyPoint) {
        this.targetType = targetType;
        this.fixedPenaltyPoint = fixedPenaltyPoint;
    }

    /**
     * @return true nếu đây là mã "Khác" (U_OTHER / P_OTHER),
     *         điểm phạt không cố định mà do Admin tự nhập.
     */
    public boolean isCustomPenalty() {
        return fixedPenaltyPoint == -1;
    }

    /**
     * @return true nếu báo cáo nhắm vào người dùng (targetType = USER).
     */
    public boolean isUserReport() {
        return targetType == Report.TargetType.USER;
    }

    /**
     * @return true nếu báo cáo nhắm vào bài viết (targetType = POST).
     */
    public boolean isPostReport() {
        return targetType == Report.TargetType.POST;
    }

    /**
     * @return điểm phạt cố định theo Ma trận. Trả về -1 nếu là mã "Khác".
     */
    public int getFixedPenaltyPoint() {
        return fixedPenaltyPoint;
    }

    public Report.TargetType getTargetType() {
        return targetType;
    }
}
