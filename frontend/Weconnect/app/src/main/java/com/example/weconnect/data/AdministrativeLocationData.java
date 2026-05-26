package com.example.weconnect.data;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AdministrativeLocationData {
    public static final String HANOI_ID = "HN";
    public static final String HANOI_NAME = "Hà Nội";

    public enum WardType {
        PHUONG("Phường"),
        XA("Xã");

        private final String label;

        WardType(String label) {
            this.label = label;
        }
    }

    public static final class Province {
        public final String id;
        public final String name;

        public Province(String id, String name) {
            this.id = id;
            this.name = name;
        }
    }

    public static final class Ward {
        public final String id;
        public final String name;
        public final WardType type;
        public final String provinceId;
        public final String oldDistrictName;

        public Ward(String id, String name, WardType type, String provinceId, String oldDistrictName) {
            this.id = id;
            this.name = name;
            this.type = type;
            this.provinceId = provinceId;
            this.oldDistrictName = oldDistrictName;
        }

        public String displayName() {
            return type.label + " " + name;
        }
    }

    private AdministrativeLocationData() {}

    public static List<Province> provinces() {
        return Arrays.asList(
                new Province(HANOI_ID, HANOI_NAME),
                new Province("HCM", "TP.HCM"),
                new Province("DNG", "Đà Nẵng")
        );
    }

    public static List<Ward> hanoiWards() {
        return HANOI_WARDS;
    }

    public static List<String> hanoiOldDistrictFilters() {
        Set<String> filters = new LinkedHashSet<>();
        for (Ward ward : HANOI_WARDS) {
            if (ward.oldDistrictName != null && !ward.oldDistrictName.isBlank()) {
                filters.add(ward.oldDistrictName);
            }
        }
        return new ArrayList<>(filters);
    }

    public static List<String> hanoiWardDisplayNames(String oldDistrictName) {
        List<String> result = new ArrayList<>();
        for (Ward ward : HANOI_WARDS) {
            if (oldDistrictName == null || oldDistrictName.isBlank()
                    || oldDistrictName.equals(ward.oldDistrictName)) {
                result.add(ward.displayName());
            }
        }
        return result;
    }

    private static Ward phuong(int index, String name, String oldDistrictName) {
        return new Ward(String.format("HN%03d", index), name, WardType.PHUONG, HANOI_ID, oldDistrictName);
    }

    private static Ward xa(int index, String name, String oldDistrictName) {
        return new Ward(String.format("HN%03d", index), name, WardType.XA, HANOI_ID, oldDistrictName);
    }

    private static final List<Ward> HANOI_WARDS = Arrays.asList(
            phuong(1, "Hoàn Kiếm", "Hoàn Kiếm"),
            phuong(2, "Cửa Nam", "Hoàn Kiếm"),
            phuong(3, "Ba Đình", "Ba Đình"),
            phuong(4, "Ngọc Hà", "Ba Đình"),
            phuong(5, "Giảng Võ", "Ba Đình"),
            phuong(6, "Hai Bà Trưng", "Hai Bà Trưng"),
            phuong(7, "Vĩnh Tuy", "Hai Bà Trưng"),
            phuong(8, "Bạch Mai", "Hai Bà Trưng"),
            phuong(9, "Đống Đa", "Đống Đa"),
            phuong(10, "Kim Liên", "Đống Đa"),
            phuong(11, "Văn Miếu - Quốc Tử Giám", "Đống Đa"),
            phuong(12, "Láng", "Đống Đa"),
            phuong(13, "Ô Chợ Dừa", "Đống Đa"),
            phuong(14, "Hồng Hà", "Tây Hồ"),
            phuong(15, "Lĩnh Nam", "Hoàng Mai"),
            phuong(16, "Hoàng Mai", "Hoàng Mai"),
            phuong(17, "Vĩnh Hưng", "Hoàng Mai"),
            phuong(18, "Tương Mai", "Hoàng Mai"),
            phuong(19, "Định Công", "Hoàng Mai"),
            phuong(20, "Hoàng Liệt", "Hoàng Mai"),
            phuong(21, "Yên Sở", "Hoàng Mai"),
            phuong(22, "Thanh Xuân", "Thanh Xuân"),
            phuong(23, "Khương Đình", "Thanh Xuân"),
            phuong(24, "Phương Liệt", "Thanh Xuân"),
            phuong(25, "Cầu Giấy", "Cầu Giấy"),
            phuong(26, "Nghĩa Đô", "Cầu Giấy"),
            phuong(27, "Yên Hòa", "Cầu Giấy"),
            phuong(28, "Tây Hồ", "Tây Hồ"),
            phuong(29, "Phú Thượng", "Tây Hồ"),
            phuong(30, "Tây Tựu", "Bắc Từ Liêm"),
            phuong(31, "Phú Diễn", "Bắc Từ Liêm"),
            phuong(32, "Xuân Đỉnh", "Bắc Từ Liêm"),
            phuong(33, "Đông Ngạc", "Bắc Từ Liêm"),
            phuong(34, "Thượng Cát", "Bắc Từ Liêm"),
            phuong(35, "Từ Liêm", "Nam Từ Liêm"),
            phuong(36, "Xuân Phương", "Nam Từ Liêm"),
            phuong(37, "Tây Mỗ", "Nam Từ Liêm"),
            phuong(38, "Đại Mỗ", "Nam Từ Liêm"),
            phuong(39, "Long Biên", "Long Biên"),
            phuong(40, "Bồ Đề", "Long Biên"),
            phuong(41, "Việt Hưng", "Long Biên"),
            phuong(42, "Phúc Lợi", "Long Biên"),
            phuong(43, "Hà Đông", "Hà Đông"),
            phuong(44, "Dương Nội", "Hà Đông"),
            phuong(45, "Yên Nghĩa", "Hà Đông"),
            phuong(46, "Kiến Hưng", "Hà Đông"),
            phuong(47, "Phú Lương", "Hà Đông"),
            phuong(48, "Thanh Liệt", "Thanh Trì"),
            phuong(49, "Chương Mỹ", "Chương Mỹ"),
            phuong(50, "Sơn Tây", "Sơn Tây"),
            phuong(51, "Tùng Thiện", "Sơn Tây"),
            xa(52, "Thanh Trì", "Thanh Trì"),
            xa(53, "Đại Thanh", "Thanh Trì"),
            xa(54, "Nam Phù", "Thanh Trì"),
            xa(55, "Ngọc Hồi", "Thanh Trì"),
            xa(56, "Thượng Phúc", "Thường Tín"),
            xa(57, "Thường Tín", "Thường Tín"),
            xa(58, "Chương Dương", "Thường Tín"),
            xa(59, "Hồng Vân", "Thường Tín"),
            xa(60, "Phú Xuyên", "Phú Xuyên"),
            xa(61, "Phượng Dực", "Phú Xuyên"),
            xa(62, "Chuyên Mỹ", "Phú Xuyên"),
            xa(63, "Đại Xuyên", "Phú Xuyên"),
            xa(64, "Thanh Oai", "Thanh Oai"),
            xa(65, "Bình Minh", "Thanh Oai"),
            xa(66, "Tam Hưng", "Thanh Oai"),
            xa(67, "Dân Hòa", "Thanh Oai"),
            xa(68, "Vân Đình", "Ứng Hòa"),
            xa(69, "Ứng Thiên", "Ứng Hòa"),
            xa(70, "Hòa Xá", "Ứng Hòa"),
            xa(71, "Ứng Hòa", "Ứng Hòa"),
            xa(72, "Mỹ Đức", "Mỹ Đức"),
            xa(73, "Hồng Sơn", "Mỹ Đức"),
            xa(74, "Phúc Sơn", "Mỹ Đức"),
            xa(75, "Hương Sơn", "Mỹ Đức"),
            xa(76, "Phú Nghĩa", "Chương Mỹ"),
            xa(77, "Xuân Mai", "Chương Mỹ"),
            xa(78, "Trần Phú", "Chương Mỹ"),
            xa(79, "Hòa Phú", "Chương Mỹ"),
            xa(80, "Quảng Bị", "Chương Mỹ"),
            xa(81, "Minh Châu", "Ba Vì"),
            xa(82, "Quảng Oai", "Ba Vì"),
            xa(83, "Vật Lại", "Ba Vì"),
            xa(84, "Cổ Đô", "Ba Vì"),
            xa(85, "Bất Bạt", "Ba Vì"),
            xa(86, "Suối Hai", "Ba Vì"),
            xa(87, "Ba Vì", "Ba Vì"),
            xa(88, "Yên Bài", "Ba Vì"),
            xa(89, "Đoài Phương", "Sơn Tây"),
            xa(90, "Phúc Thọ", "Phúc Thọ"),
            xa(91, "Phúc Lộc", "Phúc Thọ"),
            xa(92, "Hát Môn", "Phúc Thọ"),
            xa(93, "Thạch Thất", "Thạch Thất"),
            xa(94, "Hạ Bằng", "Thạch Thất"),
            xa(95, "Tây Phương", "Thạch Thất"),
            xa(96, "Hòa Lạc", "Thạch Thất"),
            xa(97, "Yên Xuân", "Thạch Thất"),
            xa(98, "Quốc Oai", "Quốc Oai"),
            xa(99, "Hưng Đạo", "Quốc Oai"),
            xa(100, "Kiều Phú", "Quốc Oai"),
            xa(101, "Phú Cát", "Quốc Oai"),
            xa(102, "Hoài Đức", "Hoài Đức"),
            xa(103, "Dương Hòa", "Hoài Đức"),
            xa(104, "Sơn Đồng", "Hoài Đức"),
            xa(105, "An Khánh", "Hoài Đức"),
            xa(106, "Đan Phượng", "Đan Phượng"),
            xa(107, "Ô Diên", "Đan Phượng"),
            xa(108, "Liên Minh", "Đan Phượng"),
            xa(109, "Gia Lâm", "Gia Lâm"),
            xa(110, "Thuận An", "Gia Lâm"),
            xa(111, "Bát Tràng", "Gia Lâm"),
            xa(112, "Phù Đổng", "Gia Lâm"),
            xa(113, "Thư Lâm", "Đông Anh"),
            xa(114, "Đông Anh", "Đông Anh"),
            xa(115, "Phúc Thịnh", "Đông Anh"),
            xa(116, "Thiên Lộc", "Đông Anh"),
            xa(117, "Vĩnh Thanh", "Đông Anh"),
            xa(118, "Mê Linh", "Mê Linh"),
            xa(119, "Yên Lãng", "Mê Linh"),
            xa(120, "Tiến Thắng", "Mê Linh"),
            xa(121, "Quang Minh", "Mê Linh"),
            xa(122, "Sóc Sơn", "Sóc Sơn"),
            xa(123, "Đa Phúc", "Sóc Sơn"),
            xa(124, "Nội Bài", "Sóc Sơn"),
            xa(125, "Trung Giã", "Sóc Sơn"),
            xa(126, "Kim Anh", "Sóc Sơn")
    );
}
