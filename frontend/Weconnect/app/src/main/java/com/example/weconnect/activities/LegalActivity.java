package com.example.weconnect.activities;

import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.weconnect.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

public class LegalActivity extends AppCompatActivity {

    static final String[] TAB_TITLES = {"Điều khoản", "Riêng tư", "Cộng đồng"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.soft_beige, null));
        root.setFitsSystemWindows(true);
        setContentView(root);

        // ── Header ──
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        header.setPadding(dp(12), dp(14), dp(16), dp(14));
        header.setElevation(dp(4));

        ImageView ivClose = new ImageView(this);
        ivClose.setImageResource(R.drawable.ic_back);
        ivClose.setColorFilter(getResources().getColor(R.color.primary_pink, null));
        ivClose.setPadding(dp(8), dp(8), dp(8), dp(8));
        ivClose.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams closeLp = new LinearLayout.LayoutParams(dp(40), dp(40));
        closeLp.setMarginEnd(dp(8));
        ivClose.setLayoutParams(closeLp);
        header.addView(ivClose);

        ImageView ivIcon = new ImageView(this);
        ivIcon.setImageResource(R.drawable.ic_legal);
        ivIcon.setColorFilter(getResources().getColor(R.color.primary_pink, null));
        LinearLayout.LayoutParams iconLp = new LinearLayout.LayoutParams(dp(22), dp(22));
        iconLp.setMarginEnd(dp(8));
        ivIcon.setLayoutParams(iconLp);
        header.addView(ivIcon);

        TextView tvTitle = new TextView(this);
        tvTitle.setText("Chính sách & Quy định");
        tvTitle.setTextSize(17);
        tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
        tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(tvTitle);

        root.addView(header);

        // ── TabLayout ──
        TabLayout tabLayout = new TabLayout(this);
        tabLayout.setBackgroundColor(getResources().getColor(R.color.card_surface, null));
        tabLayout.setTabMode(TabLayout.MODE_FIXED);
        tabLayout.setSelectedTabIndicatorColor(getResources().getColor(R.color.primary_pink, null));
        tabLayout.setTabTextColors(
                getResources().getColor(R.color.text_secondary, null),
                getResources().getColor(R.color.primary_pink, null));

        LinearLayout.LayoutParams tabLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tabLayout.setLayoutParams(tabLp);
        root.addView(tabLayout);

        // Divider
        View divider = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 1);
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(0xFFEEEEEE);
        root.addView(divider);

        // ── ViewPager2 ──
        ViewPager2 viewPager = new ViewPager2(this);
        viewPager.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        viewPager.setAdapter(new LegalPagerAdapter(this));
        root.addView(viewPager);

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, pos) -> tab.setText(TAB_TITLES[pos])).attach();
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ── Adapter ──
    static class LegalPagerAdapter extends FragmentStateAdapter {
        LegalPagerAdapter(@NonNull FragmentActivity fa) { super(fa); }

        @NonNull @Override
        public Fragment createFragment(int position) {
            return LegalFragment.newInstance(position);
        }

        @Override public int getItemCount() { return TAB_TITLES.length; }
    }

    // ── Fragment ──
    public static class LegalFragment extends Fragment {
        private static final String ARG_POS = "pos";

        public static LegalFragment newInstance(int pos) {
            LegalFragment f = new LegalFragment();
            Bundle b = new Bundle();
            b.putInt(ARG_POS, pos);
            f.setArguments(b);
            return f;
        }

        @Override
        public View onCreateView(@NonNull android.view.LayoutInflater inflater,
                                 android.view.ViewGroup container, Bundle savedInstanceState) {
            int pos = getArguments() != null ? getArguments().getInt(ARG_POS) : 0;

            NestedScrollView scroll = new NestedScrollView(requireContext());
            scroll.setFillViewport(true);

            LinearLayout page = new LinearLayout(requireContext());
            page.setOrientation(LinearLayout.VERTICAL);
            page.setPadding(dp(16), dp(16), dp(16), dp(32));
            scroll.addView(page);

            // Date badge
            TextView dateBadge = new TextView(requireContext());
            dateBadge.setText("Cập nhật: 10/06/2026");
            dateBadge.setTextSize(11);
            dateBadge.setTextColor(getResources().getColor(R.color.primary_pink, null));
            dateBadge.setPadding(dp(12), dp(5), dp(12), dp(5));
            GradientDrawable badgeBg = new GradientDrawable();
            badgeBg.setColor(getResources().getColor(R.color.secondary_pink, null));
            badgeBg.setCornerRadius(dp(20));
            dateBadge.setBackground(badgeBg);
            LinearLayout.LayoutParams badgeLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            badgeLp.setMargins(0, 0, 0, dp(4));
            dateBadge.setLayoutParams(badgeLp);
            page.addView(dateBadge);

            // Main title
            TextView tvMain = new TextView(requireContext());
            tvMain.setText(getMainTitle(pos));
            tvMain.setTextSize(18);
            tvMain.setTextColor(getResources().getColor(R.color.primary_pink, null));
            tvMain.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams mainLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            mainLp.setMargins(0, dp(10), 0, dp(4));
            tvMain.setLayoutParams(mainLp);
            page.addView(tvMain);

            // Intro (nếu có)
            String intro = getIntro(pos);
            if (!intro.isEmpty()) {
                TextView tvIntro = new TextView(requireContext());
                tvIntro.setText(intro);
                tvIntro.setTextSize(13);
                tvIntro.setTextColor(getResources().getColor(R.color.text_secondary, null));
                tvIntro.setLineSpacing(dp(3), 1f);
                LinearLayout.LayoutParams introLp = new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                introLp.setMargins(0, 0, 0, dp(12));
                tvIntro.setLayoutParams(introLp);
                page.addView(tvIntro);
            }

            // Section cards
            String[][] sections = getSections(pos);
            for (int i = 0; i < sections.length; i++) {
                page.addView(buildSectionCard(sections[i][0], sections[i][1], i + 1));
            }

            return scroll;
        }

        private View buildSectionCard(String title, String content, int number) {
            // Card container
            LinearLayout card = new LinearLayout(requireContext());
            card.setOrientation(LinearLayout.VERTICAL);
            card.setPadding(dp(16), dp(14), dp(16), dp(16));
            GradientDrawable cardBg = new GradientDrawable();
            cardBg.setColor(getResources().getColor(R.color.card_surface, null));
            cardBg.setCornerRadius(dp(14));
            card.setBackground(cardBg);
            card.setElevation(dp(2));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardLp.setMargins(0, dp(10), 0, 0);
            card.setLayoutParams(cardLp);

            // Title row: badge + text
            LinearLayout titleRow = new LinearLayout(requireContext());
            titleRow.setOrientation(LinearLayout.HORIZONTAL);
            titleRow.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 0, 0, dp(10));
            titleRow.setLayoutParams(rowLp);

            // Number badge
            TextView numBadge = new TextView(requireContext());
            numBadge.setText(String.valueOf(number));
            numBadge.setTextSize(11);
            numBadge.setTextColor(Color.WHITE);
            numBadge.setTypeface(null, android.graphics.Typeface.BOLD);
            numBadge.setGravity(Gravity.CENTER);
            GradientDrawable circleBg = new GradientDrawable();
            circleBg.setShape(GradientDrawable.OVAL);
            circleBg.setColor(getResources().getColor(R.color.primary_pink, null));
            numBadge.setBackground(circleBg);
            int size = dp(24);
            LinearLayout.LayoutParams numLp = new LinearLayout.LayoutParams(size, size);
            numLp.setMarginEnd(dp(10));
            numBadge.setLayoutParams(numLp);
            titleRow.addView(numBadge);

            // Section title
            TextView tvTitle = new TextView(requireContext());
            tvTitle.setText(title);
            tvTitle.setTextSize(14);
            tvTitle.setTextColor(getResources().getColor(R.color.text_primary, null));
            tvTitle.setTypeface(null, android.graphics.Typeface.BOLD);
            tvTitle.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            titleRow.addView(tvTitle);
            card.addView(titleRow);

            // Divider
            View div = new View(requireContext());
            LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, 1);
            divLp.setMargins(0, 0, 0, dp(10));
            div.setLayoutParams(divLp);
            div.setBackgroundColor(0xFFF0F0F0);
            card.addView(div);

            // Content
            TextView tvContent = new TextView(requireContext());
            tvContent.setText(content);
            tvContent.setTextSize(13);
            tvContent.setTextColor(getResources().getColor(R.color.text_secondary, null));
            tvContent.setLineSpacing(dp(4), 1f);
            card.addView(tvContent);

            return card;
        }

        private int dp(int dp) {
            return Math.round(dp * requireContext().getResources().getDisplayMetrics().density);
        }

        private String getMainTitle(int pos) {
            switch (pos) {
                case 0: return "Điều khoản dịch vụ";
                case 1: return "Chính sách quyền riêng tư";
                case 2: return "Tiêu chuẩn cộng đồng";
                default: return "";
            }
        }

        private String getIntro(int pos) {
            switch (pos) {
                case 0: return "Bằng việc sử dụng WeConnect, bạn đồng ý tuân thủ các điều khoản dịch vụ dưới đây.";
                case 2: return "WeConnect cam kết xây dựng một cộng đồng an toàn, tôn trọng và tích cực.";
                default: return "";
            }
        }

        private String[][] getSections(int pos) {
            switch (pos) {
                case 0:
                    return new String[][]{
                            {"Tài khoản người dùng",
                                    "• Cung cấp thông tin chính xác khi đăng ký.\n" +
                                    "• Chịu trách nhiệm bảo mật tài khoản của mình.\n" +
                                    "• Mỗi người chỉ được sở hữu một tài khoản."},
                            {"Hoạt động và nội dung",
                                    "• Có thể tạo và tham gia các hoạt động cộng đồng.\n" +
                                    "• Nội dung đăng tải phải tuân thủ tiêu chuẩn cộng đồng.\n" +
                                    "• WeConnect có quyền xoá nội dung vi phạm mà không cần báo trước."},
                            {"Quyền sở hữu trí tuệ",
                                    "• Bạn giữ quyền sở hữu nội dung bạn tạo ra.\n" +
                                    "• Bạn cấp cho WeConnect giấy phép sử dụng nội dung để vận hành và cải thiện dịch vụ."},
                            {"Giới hạn trách nhiệm",
                                    "• WeConnect không chịu trách nhiệm cho các sự cố xảy ra trong hoạt động ngoại tuyến.\n" +
                                    "• Người dùng tự chịu trách nhiệm khi tham gia các hoạt động."},
                            {"Chấm dứt dịch vụ",
                                    "• Bạn có thể xoá tài khoản bất kỳ lúc nào trong phần Cài đặt.\n" +
                                    "• WeConnect có quyền đình chỉ tài khoản vi phạm điều khoản."},
                    };
                case 1:
                    return new String[][]{
                            {"Thông tin thu thập",
                                    "• Thông tin cá nhân: Họ tên, email, ngày sinh, giới tính.\n" +
                                    "• Thông tin hoạt động: Bài viết, sở thích, lịch sử tham gia.\n" +
                                    "• Thông tin kỹ thuật: Loại thiết bị, hệ điều hành, địa chỉ IP."},
                            {"Mục đích sử dụng",
                                    "• Cung cấp và liên tục cải thiện dịch vụ.\n" +
                                    "• Kết nối người dùng có cùng sở thích hoạt động.\n" +
                                    "• Đảm bảo an toàn và bảo mật cho cộng đồng."},
                            {"Chia sẻ thông tin",
                                    "• Hồ sơ công khai bao gồm: Tên, ảnh đại diện, sở thích.\n" +
                                    "• WeConnect không bán thông tin cá nhân cho bên thứ ba.\n" +
                                    "• Có thể cung cấp cho cơ quan có thẩm quyền khi pháp luật yêu cầu."},
                            {"Bảo mật dữ liệu",
                                    "• Dữ liệu nhạy cảm được mã hoá khi lưu trữ và truyền tải.\n" +
                                    "• Xác thực hai yếu tố sẽ ra mắt trong phiên bản tới."},
                            {"Quyền của bạn",
                                    "• Truy cập và chỉnh sửa thông tin cá nhân bất kỳ lúc nào.\n" +
                                    "• Xoá tài khoản và toàn bộ dữ liệu liên quan.\n" +
                                    "• Từ chối nhận thông báo marketing từ WeConnect."},
                    };
                case 2:
                    return new String[][]{
                            {"Tôn trọng lẫn nhau",
                                    "• Không phân biệt đối xử về giới tính, chủng tộc, tôn giáo hay lứa tuổi.\n" +
                                    "• Sử dụng ngôn ngữ lịch sự, văn minh trong mọi tình huống.\n" +
                                    "• Tôn trọng ý kiến và quan điểm khác biệt."},
                            {"Nội dung phù hợp",
                                    "• Không đăng nội dung bạo lực, khiêu dâm hoặc kích động thù địch.\n" +
                                    "• Không spam, quảng cáo trái phép hoặc lừa đảo.\n" +
                                    "• Không lan truyền thông tin sai lệch, tin giả."},
                            {"An toàn hoạt động",
                                    "• Tham gia hoạt động đúng giờ và thực hiện đúng cam kết.\n" +
                                    "• Thông báo sớm cho nhóm khi không thể tham gia.\n" +
                                    "• Luôn đảm bảo an toàn cho bản thân và những người xung quanh."},
                            {"Uy tín cộng đồng",
                                    "• Điểm uy tín phản ánh mức độ tin cậy và trách nhiệm của bạn.\n" +
                                    "• Đánh giá và nhận xét phải trung thực, khách quan.\n" +
                                    "• Vi phạm nhiều lần sẽ bị giảm điểm uy tín hoặc hạn chế tính năng."},
                            {"Báo cáo vi phạm",
                                    "• Dùng nút \"Báo cáo\" trên bài viết, bình luận hoặc hồ sơ để thông báo vi phạm.\n" +
                                    "• Đội ngũ kiểm duyệt sẽ xem xét trong vòng 24 giờ.\n" +
                                    "• Tài khoản vi phạm nghiêm trọng sẽ bị đình chỉ vĩnh viễn."},
                    };
                default:
                    return new String[][]{};
            }
        }
    }
}
