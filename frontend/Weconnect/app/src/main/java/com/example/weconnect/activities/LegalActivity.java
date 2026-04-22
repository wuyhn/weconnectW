package com.example.weconnect.activities;

import android.os.Bundle;
import android.widget.ImageView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.viewpager2.widget.ViewPager2;

import com.example.weconnect.R;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

public class LegalActivity extends AppCompatActivity {

    private static final String[] TAB_TITLES = {
            "Điều khoản dịch vụ",
            "Quyền riêng tư",
            "Tiêu chuẩn cộng đồng"
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Programmatic layout
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setBackgroundColor(getResources().getColor(R.color.soft_beige, null));
        root.setFitsSystemWindows(true);
        setContentView(root);

        // Header
        android.widget.LinearLayout header = new android.widget.LinearLayout(this);
        header.setOrientation(android.widget.LinearLayout.HORIZONTAL);
        header.setGravity(android.view.Gravity.CENTER_VERTICAL);
        header.setPadding(48, 48, 48, 24);

        ImageView ivBack = new ImageView(this);
        ivBack.setImageResource(R.drawable.ic_close);
        ivBack.setPadding(24, 24, 24, 24);
        ivBack.setOnClickListener(v -> finish());
        ivBack.setColorFilter(getResources().getColor(R.color.primary_pink, null));
        android.widget.LinearLayout.LayoutParams backLp = new android.widget.LinearLayout.LayoutParams(96, 96);
        ivBack.setLayoutParams(backLp);
        header.addView(ivBack);

        android.widget.TextView title = new android.widget.TextView(this);
        title.setText("Chính sách & Quy định");
        title.setTextSize(20);
        title.setTextColor(getResources().getColor(R.color.primary_pink, null));
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(24, 0, 0, 0);
        header.addView(title);

        root.addView(header);

        // TabLayout
        TabLayout tabLayout = new TabLayout(this);
        tabLayout.setTabMode(TabLayout.MODE_SCROLLABLE);
        tabLayout.setSelectedTabIndicatorColor(getResources().getColor(R.color.primary_pink, null));
        tabLayout.setTabTextColors(
                getResources().getColor(R.color.text_secondary, null),
                getResources().getColor(R.color.primary_pink, null));
        root.addView(tabLayout);

        // ViewPager2
        ViewPager2 viewPager = new ViewPager2(this);
        viewPager.setLayoutParams(new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, 0, 1));
        root.addView(viewPager);

        viewPager.setAdapter(new LegalPagerAdapter(this));

        new TabLayoutMediator(tabLayout, viewPager,
                (tab, position) -> tab.setText(TAB_TITLES[position])
        ).attach();
    }

    static class LegalPagerAdapter extends FragmentStateAdapter {
        public LegalPagerAdapter(@NonNull FragmentActivity fa) { super(fa); }

        @NonNull @Override
        public Fragment createFragment(int position) {
            return LegalFragment.newInstance(position);
        }

        @Override public int getItemCount() { return TAB_TITLES.length; }
    }

    public static class LegalFragment extends Fragment {
        private static final String ARG_POS = "pos";

        public static LegalFragment newInstance(int position) {
            LegalFragment f = new LegalFragment();
            Bundle args = new Bundle();
            args.putInt(ARG_POS, position);
            f.setArguments(args);
            return f;
        }

        @Override
        public android.view.View onCreateView(@NonNull android.view.LayoutInflater inflater,
                                               android.view.ViewGroup container, Bundle savedInstanceState) {
            androidx.core.widget.NestedScrollView scroll = new androidx.core.widget.NestedScrollView(requireContext());
            scroll.setPadding(64, 48, 64, 100);

            android.widget.TextView tv = new android.widget.TextView(requireContext());
            tv.setTextSize(14);
            tv.setTextColor(requireContext().getResources().getColor(R.color.text_primary, null));
            tv.setLineSpacing(8f, 1f);

            int pos = getArguments() != null ? getArguments().getInt(ARG_POS) : 0;
            tv.setText(getContent(pos));
            scroll.addView(tv);
            return scroll;
        }

        private String getContent(int pos) {
            switch (pos) {
                case 0:
                    return "ĐIỀU KHOẢN DỊCH VỤ WECONNECT\n\n" +
                            "Cập nhật lần cuối: 24/03/2026\n\n" +
                            "1. GIỚI THIỆU\n" +
                            "Chào mừng bạn đến với WeConnect. Bằng việc sử dụng ứng dụng, bạn đồng ý tuân thủ các điều khoản dịch vụ này.\n\n" +
                            "2. TÀI KHOẢN NGƯỜI DÙNG\n" +
                            "• Bạn phải cung cấp thông tin chính xác khi đăng ký.\n" +
                            "• Bạn chịu trách nhiệm bảo mật tài khoản của mình.\n" +
                            "• Mỗi người chỉ được sở hữu một tài khoản.\n\n" +
                            "3. HOẠT ĐỘNG VÀ NỘI DUNG\n" +
                            "• Bạn có thể tạo và tham gia các hoạt động cộng đồng.\n" +
                            "• Nội dung đăng tải phải phù hợp với tiêu chuẩn cộng đồng.\n" +
                            "• WeConnect có quyền xoá nội dung vi phạm.\n\n" +
                            "4. QUYỀN SỞ HỮU TRÍ TUỆ\n" +
                            "• Bạn giữ quyền sở hữu nội dung bạn tạo ra.\n" +
                            "• Bạn cấp cho WeConnect giấy phép sử dụng nội dung để vận hành dịch vụ.\n\n" +
                            "5. GIỚI HẠN TRÁCH NHIỆM\n" +
                            "• WeConnect không chịu trách nhiệm cho các sự cố xảy ra trong hoạt động ngoại tuyến.\n" +
                            "• Người dùng tự chịu trách nhiệm khi tham gia các hoạt động.\n\n" +
                            "6. CHẤM DỨT\n" +
                            "• Bạn có thể xoá tài khoản bất kỳ lúc nào.\n" +
                            "• WeConnect có quyền đình chỉ tài khoản vi phạm điều khoản.";
                case 1:
                    return "CHÍNH SÁCH QUYỀN RIÊNG TƯ WECONNECT\n\n" +
                            "Cập nhật lần cuối: 24/03/2026\n\n" +
                            "1. THÔNG TIN THU THẬP\n" +
                            "• Thông tin cá nhân: Họ tên, email, ngày sinh, giới tính.\n" +
                            "• Thông tin hoạt động: Bài viết, sở thích, lịch sử tham gia.\n" +
                            "• Thông tin kỹ thuật: Thiết bị, hệ điều hành, IP.\n\n" +
                            "2. MỤC ĐÍCH SỬ DỤNG\n" +
                            "• Cung cấp và cải thiện dịch vụ.\n" +
                            "• Kết nối người dùng có cùng sở thích.\n" +
                            "• Đảm bảo an toàn cộng đồng.\n\n" +
                            "3. CHIA SẺ THÔNG TIN\n" +
                            "• Hồ sơ công khai: Tên, avatar, sở thích.\n" +
                            "• Không bán thông tin cho bên thứ ba.\n" +
                            "• Có thể chia sẻ khi pháp luật yêu cầu.\n\n" +
                            "4. BẢO MẬT\n" +
                            "• Mã hoá dữ liệu nhạy cảm.\n" +
                            "• Xác thực hai yếu tố (sắp ra mắt).\n\n" +
                            "5. QUYỀN CỦA BẠN\n" +
                            "• Truy cập và chỉnh sửa thông tin.\n" +
                            "• Xoá tài khoản và dữ liệu.\n" +
                            "• Từ chối nhận thông báo marketing.";
                case 2:
                    return "TIÊU CHUẨN CỘNG ĐỒNG WECONNECT\n\n" +
                            "Cập nhật lần cuối: 24/03/2026\n\n" +
                            "WeConnect cam kết xây dựng một cộng đồng an toàn, tôn trọng và tích cực.\n\n" +
                            "1. TÔN TRỌNG LẪN NHAU\n" +
                            "• Không phân biệt đối xử về giới tính, chủng tộc, tôn giáo.\n" +
                            "• Sử dụng ngôn ngữ lịch sự, văn minh.\n" +
                            "• Tôn trọng ý kiến khác biệt.\n\n" +
                            "2. NỘI DUNG PHẢI PHÙ HỢP\n" +
                            "• Không đăng nội dung bạo lực, khiêu dâm.\n" +
                            "• Không spam hoặc quảng cáo trái phép.\n" +
                            "• Không lan truyền thông tin sai lệch.\n\n" +
                            "3. AN TOÀN HOẠT ĐỘNG\n" +
                            "• Tham gia hoạt động đúng giờ và có trách nhiệm.\n" +
                            "• Thông báo khi không thể tham gia.\n" +
                            "• Bảo đảm an toàn cho bản thân và người khác.\n\n" +
                            "4. UY TÍN CỘNG ĐỒNG\n" +
                            "• Điểm uy tín phản ánh mức độ tin cậy của bạn.\n" +
                            "• Nhận xét trung thực và công bằng.\n" +
                            "• Vi phạm nhiều lần sẽ bị giảm điểm uy tín.\n\n" +
                            "5. BÁO CÁO VI PHẠM\n" +
                            "• Sử dụng chức năng \"Báo cáo\" để thông báo vi phạm.\n" +
                            "• Đội ngũ sẽ xem xét trong vòng 24h.\n" +
                            "• Tài khoản vi phạm nghiêm trọng sẽ bị đình chỉ.";
                default:
                    return "";
            }
        }
    }
}
