package com.example.weconnect.fragments;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.widget.NestedScrollView;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.example.weconnect.R;
import com.example.weconnect.activities.ArchivePostsActivity;
import com.example.weconnect.activities.BlockedUsersActivity;
import com.example.weconnect.activities.ChangePasswordActivity;
import com.example.weconnect.activities.EditProfileActivity;
import com.example.weconnect.activities.FriendsListActivity;
import com.example.weconnect.activities.LegalActivity;
import com.example.weconnect.activities.LoginActivity;
import com.example.weconnect.activities.MainActivity;
import com.example.weconnect.adapters.PostAdapter;
import com.example.weconnect.adapters.UserReviewAdapter;
import com.example.weconnect.api.FriendApiService;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.api.ReviewApiService;
import com.example.weconnect.api.UserApiService;
import com.example.weconnect.data.FakePostRepository;
import com.example.weconnect.utils.InterestTextUtils;
import com.example.weconnect.data.FakeSocialRepository;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.Post;
import com.example.weconnect.models.PostResponse;
import com.example.weconnect.models.UserReview;
import com.example.weconnect.websocket.WebSocketManager;
import com.example.weconnect.api.AuthApiService;
import com.example.weconnect.models.LogoutRequest;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.tabs.TabLayout;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileFragment extends Fragment {

    private DrawerLayout drawerLayoutProfile;
    private ImageView ivBackUserProfile;
    private ImageView ivMenuProfile;
    private ImageView ivUserProfileAvatar;
    private ImageView ivCreatePostAvatar;
    private TextView tvUserProfileName;
    private TextView tvUserReputation;
    private TextView tvUserBio;
    private TextView tvUserBirthday;
    private TextView tvUserGender;
    private TextView tvUserProvince;
    private TextView tvFriendCount;
    private TextView tvInterestsTitle;
    private TextView tvReviewsTitle;
    private ChipGroup chipGroupUserInterests;
    private MaterialButton btnViewArchive;
    private LinearLayout layoutSocialButtons;
    private LinearLayout layoutRateReport;
    private LinearLayout containerMyPosts;
    private LinearLayout containerMyActivities;
    private View cardCreatePostProfile;
    private View footerNavigationProfile;
    private View tvNoActivePosts;
    private View tvNoMyActivities;
    private TextView tvRelatedPostsTitle;
    private TextView tvNoRelatedPosts;
    private RecyclerView rvActivePostsProfile;
    private RecyclerView rvMyActivities;
    private RecyclerView rvUserReviews;
    private RecyclerView rvRelatedPosts;
    private TabLayout tabLayoutProfile;

    private PostApiService postApiService;
    private UserApiService userApiService;
    private FriendApiService friendApiService;
    private ReviewApiService reviewApiService;
    private boolean hasLoadedOnce = false;
    private String currentAvatarUrl = "";
    private ActivityResultLauncher<Intent> avatarPickerLauncher;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        avatarPickerLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                result -> {
                    if (result.getResultCode() == android.app.Activity.RESULT_OK
                            && result.getData() != null) {
                        Uri uri = result.getData().getData();
                        if (uri != null) {
                            try {
                                requireContext().getContentResolver().takePersistableUriPermission(
                                        uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                            } catch (Exception ignored) {}
                            uploadAvatar(uri);
                        }
                    }
                });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_user_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        initApis();
        initViews(view);
        adaptOldActivityLayoutForFragment(view);
        setupSelfProfileUi();
        setupDrawerMenu(view);
        setupProfileTabs();
        setupClickListeners();
        bindCachedProfile();
        refreshProfileTab();
        hasLoadedOnce = true;
    }

    @Override
    public void onResume() {
        super.onResume();
        if (hasLoadedOnce && !isHidden()) {
            refreshProfileTab();
        }
    }

    // Fragment.hide()/show() không trigger onResume — cần override này để reload khi user
    // chuyển từ tab khác sang Profile trong khi Activity vẫn foreground.
    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden && hasLoadedOnce) {
            loadOwnProfile();
        }
    }

    // Gọi từ ngoài (MainActivity) khi nhận notification trừ điểm uy tín —
    // refresh ngay cả khi user đang ở tab Profile (không bị hide).
    public void refreshReputation() {
        if (hasLoadedOnce) {
            loadOwnProfile();
        }
    }

    private void initApis() {
        RetrofitClient.loadToken(requireContext());
        postApiService = RetrofitClient.getClient().create(PostApiService.class);
        userApiService = RetrofitClient.getClient().create(UserApiService.class);
        friendApiService = RetrofitClient.getClient().create(FriendApiService.class);
        reviewApiService = RetrofitClient.getClient().create(ReviewApiService.class);
    }

    private void initViews(View view) {
        drawerLayoutProfile = view.findViewById(R.id.drawerLayoutProfile);
        ivBackUserProfile = view.findViewById(R.id.ivBackUserProfile);
        ivMenuProfile = view.findViewById(R.id.ivMenuProfile);
        ivUserProfileAvatar = view.findViewById(R.id.ivUserProfileAvatar);
        ivCreatePostAvatar = view.findViewById(R.id.ivCreatePostAvatar);
        tvUserProfileName = view.findViewById(R.id.tvUserProfileName);
        tvUserReputation = view.findViewById(R.id.tvUserReputation);
        tvUserBio = view.findViewById(R.id.tvUserBio);
        tvUserBirthday = view.findViewById(R.id.tvUserBirthday);
        tvUserGender = view.findViewById(R.id.tvUserGender);
        tvUserProvince = view.findViewById(R.id.tvUserProvince);
        tvFriendCount = view.findViewById(R.id.tvFriendCount);
        tvInterestsTitle = view.findViewById(R.id.tvInterestsTitle);
        tvReviewsTitle = view.findViewById(R.id.tvReviewsTitle);
        chipGroupUserInterests = view.findViewById(R.id.chipGroupUserInterests);
        btnViewArchive = view.findViewById(R.id.btnViewArchive);
        layoutSocialButtons = view.findViewById(R.id.layoutSocialButtons);
        layoutRateReport = view.findViewById(R.id.layoutRateReport);
        containerMyPosts = view.findViewById(R.id.containerMyPosts);
        containerMyActivities = view.findViewById(R.id.containerMyActivities);
        cardCreatePostProfile = view.findViewById(R.id.cardCreatePostProfile);
        footerNavigationProfile = view.findViewById(R.id.footerNavigationProfile);
        tvNoActivePosts = view.findViewById(R.id.tvNoActivePosts);
        tvNoMyActivities = view.findViewById(R.id.tvNoMyActivities);
        tvRelatedPostsTitle = view.findViewById(R.id.tvRelatedPostsTitle);
        tvNoRelatedPosts = view.findViewById(R.id.tvNoRelatedPosts);
        rvActivePostsProfile = view.findViewById(R.id.rvActivePostsProfile);
        rvMyActivities = view.findViewById(R.id.rvMyActivities);
        rvUserReviews = view.findViewById(R.id.rvUserReviews);
        rvRelatedPosts = view.findViewById(R.id.rvRelatedPosts);
        tabLayoutProfile = view.findViewById(R.id.tabLayoutProfile);
    }

    private void adaptOldActivityLayoutForFragment(View root) {
        if (footerNavigationProfile != null) {
            footerNavigationProfile.setVisibility(View.GONE);
        }

        NestedScrollView scrollView = root.findViewById(R.id.nestedScrollProfile);
        if (scrollView != null && scrollView.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) scrollView.getLayoutParams();
            lp.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomMargin = dp(12);
            scrollView.setLayoutParams(lp);
        }
    }

    private void setupSelfProfileUi() {
        if (ivBackUserProfile != null) ivBackUserProfile.setVisibility(View.GONE);
        if (ivMenuProfile != null) {
            ivMenuProfile.setVisibility(View.VISIBLE);
            ivMenuProfile.setImageResource(R.drawable.ic_menu);
        }
        if (tvFriendCount != null) tvFriendCount.setVisibility(View.VISIBLE);
        if (layoutSocialButtons != null) layoutSocialButtons.setVisibility(View.GONE);
        if (layoutRateReport != null) layoutRateReport.setVisibility(View.GONE);
        if (btnViewArchive != null) btnViewArchive.setVisibility(View.VISIBLE);
        if (cardCreatePostProfile != null) cardCreatePostProfile.setVisibility(View.VISIBLE);
        if (tvInterestsTitle != null) tvInterestsTitle.setVisibility(View.VISIBLE);
        if (chipGroupUserInterests != null) chipGroupUserInterests.setVisibility(View.VISIBLE);
        hideRelatedPosts();
    }

    private void setupDrawerMenu(View root) {
        if (drawerLayoutProfile != null) {
            drawerLayoutProfile.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED);
        }

        if (ivMenuProfile != null) {
            ivMenuProfile.setOnClickListener(v -> {
                if (drawerLayoutProfile != null) drawerLayoutProfile.openDrawer(Gravity.END);
            });
        }

        View menuEditProfile = root.findViewById(R.id.menuEditProfile);
        View menuChangePassword = root.findViewById(R.id.menuChangePassword);
        View menuLogout = root.findViewById(R.id.menuLogout);
        View menuBlockedUsers = root.findViewById(R.id.menuBlockedUsers);
        View menuLegal = root.findViewById(R.id.menuLegal);
        View menuDeleteAccount = root.findViewById(R.id.menuDeleteAccount);

        if (menuEditProfile != null) {
            menuEditProfile.setOnClickListener(v -> {
                closeDrawer();
                startActivity(new Intent(requireContext(), EditProfileActivity.class));
            });
        }
        if (menuChangePassword != null) {
            menuChangePassword.setOnClickListener(v -> {
                closeDrawer();
                startActivity(new Intent(requireContext(), ChangePasswordActivity.class));
            });
        }
        if (menuLogout != null) {
            menuLogout.setOnClickListener(v -> {
                closeDrawer();
                confirmLogout();
            });
        }
        if (menuBlockedUsers != null) {
            menuBlockedUsers.setOnClickListener(v -> {
                closeDrawer();
                startActivity(new Intent(requireContext(), BlockedUsersActivity.class));
            });
        }
        if (menuLegal != null) {
            menuLegal.setOnClickListener(v -> {
                closeDrawer();
                startActivity(new Intent(requireContext(), LegalActivity.class));
            });
        }
        if (menuDeleteAccount != null) {
            menuDeleteAccount.setOnClickListener(v -> {
                closeDrawer();
                confirmDeleteAccount();
            });
        }
    }

    private void setupClickListeners() {
        if (btnViewArchive != null) {
            btnViewArchive.setOnClickListener(v -> {
                Intent intent = new Intent(requireContext(), ArchivePostsActivity.class);
                intent.putExtra("username", tvUserProfileName.getText().toString());
                intent.putExtra("user_id", RetrofitClient.getUserId(requireContext()));
                startActivity(intent);
            });
        }

        if (tvFriendCount != null) {
            tvFriendCount.setOnClickListener(v ->
                    startActivity(new Intent(requireContext(), FriendsListActivity.class)));
        }

        if (cardCreatePostProfile != null) {
            cardCreatePostProfile.setOnClickListener(v -> {
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).openCreatePostComposer();
                }
            });
        }

        if (ivUserProfileAvatar != null) {
            ivUserProfileAvatar.setOnClickListener(v -> showAvatarOptions());
        }
    }

    private void setupProfileTabs() {
        if (tabLayoutProfile == null) return;

        final int[] tabScrollY = {0, 0};
        final int[] currentTab = {0};
        NestedScrollView scrollView = getView() != null ? getView().findViewById(R.id.nestedScrollProfile) : null;

        tabLayoutProfile.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {
            @Override
            public void onTabSelected(TabLayout.Tab tab) {
                int newTab = tab.getPosition();
                if (newTab == currentTab[0]) return;

                if (scrollView != null) {
                    tabScrollY[currentTab[0]] = scrollView.getScrollY();
                    if (tabScrollY[newTab] == 0) tabScrollY[newTab] = scrollView.getScrollY();
                }
                currentTab[0] = newTab;

                if (newTab == 0) {
                    if (containerMyPosts != null) containerMyPosts.setVisibility(View.VISIBLE);
                    if (containerMyActivities != null) containerMyActivities.setVisibility(View.GONE);
                } else {
                    if (containerMyPosts != null) containerMyPosts.setVisibility(View.GONE);
                    if (containerMyActivities != null) containerMyActivities.setVisibility(View.VISIBLE);
                }

                if (scrollView != null) {
                    int targetY = tabScrollY[newTab];
                    scrollView.post(() -> scrollView.scrollTo(0, targetY));
                }
            }

            @Override public void onTabUnselected(TabLayout.Tab tab) {}
            @Override public void onTabReselected(TabLayout.Tab tab) {}
        });
    }

    private void bindCachedProfile() {
        String name = RetrofitClient.getUserName(requireContext());
        tvUserProfileName.setText(name == null || name.trim().isEmpty()
                ? "Người dùng WeConnect" : name);

        int reputation = (int) Math.round(RetrofitClient.getReputationScore(requireContext()));
        tvUserReputation.setText(String.valueOf(reputation));
        tvFriendCount.setText("👥 Bạn bè: 0");

        String avatarUrl = RetrofitClient.getAvatarUrl(requireContext());
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            currentAvatarUrl = avatarUrl;
            loadAvatar(avatarUrl);
        } else {
            currentAvatarUrl = "";
            ivUserProfileAvatar.setImageResource(R.drawable.ic_user_placeholder);
            if (ivCreatePostAvatar != null) ivCreatePostAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }
    }

    private void refreshProfileTab() {
        loadOwnProfile();
        loadFriendCount();
        loadOwnPosts();
        loadMyActivities();
        loadReviews();
    }

    private void loadOwnProfile() {
        userApiService.getMyProfile().enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    bindProfile(response.body().getResult());
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Không thể tải hồ sơ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void bindProfile(Map<String, Object> profile) {
        long userId = getLong(profile.get("id"), -1);
        if (userId > 0) RetrofitClient.saveUserId(requireContext(), userId);

        String fullName = getString(profile, "fullName");
        if (!fullName.isEmpty()) {
            tvUserProfileName.setText(fullName);
            RetrofitClient.saveUserName(requireContext(), fullName);
            FakeSocialRepository.getInstance().setCurrentUsername(fullName);
            FakePostRepository.getInstance().setCurrentUsername(fullName);
        }

        String bio = getString(profile, "bio");
        tvUserBio.setText(bio);
        tvUserBio.setVisibility(bio.isEmpty() ? View.GONE : View.VISIBLE);

        String birthday = getString(profile, "birthday");
        tvUserBirthday.setText(birthday.isEmpty() ? "" : "🎂 " + birthday);
        tvUserBirthday.setVisibility(birthday.isEmpty() ? View.GONE : View.VISIBLE);

        String gender = getString(profile, "gender");
        tvUserGender.setText(gender.isEmpty() ? "" : "👤 " + gender);
        tvUserGender.setVisibility(gender.isEmpty() ? View.GONE : View.VISIBLE);

        String provinceId = getString(profile, "provinceId");
        String provinceName = getString(profile, "provinceName");
        tvUserProvince.setText(provinceName.isEmpty() ? "" : "📍 " + provinceName);
        tvUserProvince.setVisibility(provinceName.isEmpty() ? View.GONE : View.VISIBLE);
        RetrofitClient.saveUserProvince(requireContext(), provinceId, provinceName);

        int reputation = (int) Math.round(getDouble(profile.get("reputationScore"), 60));
        tvUserReputation.setText(String.valueOf(reputation));
        RetrofitClient.saveReputationScore(requireContext(), reputation);

        String avatarUrl = getString(profile, "avatarUrl");
        if (!avatarUrl.isEmpty()) {
            currentAvatarUrl = normalizeImageUrl(avatarUrl);
            loadAvatar(avatarUrl);
            RetrofitClient.saveAvatarUrl(requireContext(), currentAvatarUrl);
        } else {
            currentAvatarUrl = "";
        }

        String interestTags = getString(profile, "interestTags");
        renderInterests(interestTags);
        if (!interestTags.isEmpty()) {
            requireContext().getSharedPreferences("weconnect_prefs", android.content.Context.MODE_PRIVATE)
                    .edit()
                    .putString("user_interests", interestTags)
                    .apply();
        }

        if (userId > 0) {
            loadOwnPosts();
            loadReviews();
        }
    }

    private void loadFriendCount() {
        friendApiService.getFriendCount().enqueue(new Callback<ApiResponse<Integer>>() {
            @Override
            public void onResponse(Call<ApiResponse<Integer>> call, Response<ApiResponse<Integer>> response) {
                if (!isAdded()) return;
                int count = 0;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    count = response.body().getResult();
                }
                tvFriendCount.setText("👥 Bạn bè: " + count);
            }

            @Override
            public void onFailure(Call<ApiResponse<Integer>> call, Throwable t) {
                if (!isAdded()) return;
                tvFriendCount.setText("👥 Bạn bè: 0");
            }
        });
    }

    public void onPostCreated() {
        loadOwnPosts();
    }

    private void loadOwnPosts() {
        long myId = RetrofitClient.getUserId(requireContext());
        if (myId <= 0) {
            showPosts(new ArrayList<>());
            return;
        }

        postApiService.getUserPosts(myId).enqueue(new Callback<ApiResponse<List<PostResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PostResponse>>> call,
                                   Response<ApiResponse<List<PostResponse>>> response) {
                if (!isAdded()) return;
                List<Post> posts = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    for (PostResponse postResponse : response.body().getResult()) {
                        posts.add(postResponse.toPost());
                    }
                }
                showPosts(posts);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PostResponse>>> call, Throwable t) {
                if (!isAdded()) return;
                showPosts(new ArrayList<>());
            }
        });
    }

    private void showPosts(List<Post> posts) {
        boolean empty = posts.isEmpty();
        if (tvNoActivePosts != null) tvNoActivePosts.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rvActivePostsProfile != null) {
            rvActivePostsProfile.setVisibility(empty ? View.GONE : View.VISIBLE);
            rvActivePostsProfile.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvActivePostsProfile.setAdapter(new PostAdapter(requireActivity(), posts));
        }
    }

    private void loadMyActivities() {
        postApiService.getMyActivities().enqueue(new Callback<ApiResponse<List<PostResponse>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<PostResponse>>> call,
                                   Response<ApiResponse<List<PostResponse>>> response) {
                if (!isAdded()) return;
                List<Post> activities = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    for (PostResponse postResponse : response.body().getResult()) {
                        activities.add(postResponse.toPost());
                    }
                }
                showActivities(activities);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<PostResponse>>> call, Throwable t) {
                if (!isAdded()) return;
                showActivities(new ArrayList<>());
            }
        });
    }

    private void showActivities(List<Post> activities) {
        boolean empty = activities.isEmpty();
        if (tvNoMyActivities != null) tvNoMyActivities.setVisibility(empty ? View.VISIBLE : View.GONE);
        if (rvMyActivities != null) {
            rvMyActivities.setVisibility(empty ? View.GONE : View.VISIBLE);
            rvMyActivities.setLayoutManager(new LinearLayoutManager(requireContext()));
            rvMyActivities.setAdapter(new PostAdapter(requireActivity(), activities));
        }
    }

    private void loadReviews() {
        long myId = RetrofitClient.getUserId(requireContext());
        if (myId <= 0) {
            setReviews(new ArrayList<>());
            return;
        }

        rvUserReviews.setLayoutManager(new LinearLayoutManager(requireContext()));
        reviewApiService.getReviews(myId).enqueue(new Callback<ApiResponse<List<Map<String, Object>>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<Map<String, Object>>>> call,
                                   Response<ApiResponse<List<Map<String, Object>>>> response) {
                if (!isAdded()) return;
                List<UserReview> reviews = new ArrayList<>();
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    for (Map<String, Object> map : response.body().getResult()) {
                        reviews.add(toUserReview(map));
                    }
                }
                setReviews(reviews);
            }

            @Override
            public void onFailure(Call<ApiResponse<List<Map<String, Object>>>> call, Throwable t) {
                if (!isAdded()) return;
                setReviews(new ArrayList<>());
            }
        });
    }

    private void setReviews(List<UserReview> reviews) {
        if (rvUserReviews == null) return;
        rvUserReviews.setAdapter(new UserReviewAdapter(
                reviews,
                RetrofitClient.getUserId(requireContext()),
                null));
        if (tvReviewsTitle != null) tvReviewsTitle.setVisibility(View.VISIBLE);
    }

    private UserReview toUserReview(Map<String, Object> map) {
        UserReview review = new UserReview();
        review.setId(getLong(map.get("id"), 0));
        review.setReviewerId(getLong(map.get("reviewerId"), 0));
        review.setReviewedUserId(getLong(map.get("reviewedUserId"), 0));
        review.setPostId(getLong(map.get("postId"), 0));
        if (map.get("rating") != null) review.setRating((int) getLong(map.get("rating"), 0));
        review.setReviewerName(getString(map, "reviewerName").isEmpty()
                ? "Ẩn danh" : getString(map, "reviewerName"));
        review.setReviewerAvatarUrl(getString(map, "reviewerAvatarUrl"));
        review.setReputationLabel(getString(map, "reputationLabel"));
        review.setComment(getString(map, "comment"));
        review.setActivityName(getString(map, "activityName"));
        review.setInterestTag(getString(map, "interestTag"));
        review.setActivityDateDisplay(getString(map, "activityDateDisplay"));
        review.setCreatedAt(getString(map, "createdAt"));
        review.setUpdatedAt(getString(map, "updatedAt"));
        review.setEdited(Boolean.TRUE.equals(map.get("isEdited"))
                || Boolean.TRUE.equals(map.get("edited")));
        return review;
    }

    private void renderInterests(String interestTags) {
        chipGroupUserInterests.removeAllViews();
        if (interestTags == null || interestTags.trim().isEmpty()) return;

        for (String tag : interestTags.split(",")) {
            String trimmed = InterestTextUtils.stripLeadingIcon(tag);
            if (trimmed.isEmpty()) continue;
            Chip chip = new Chip(requireContext());
            chip.setText(trimmed);
            chip.setClickable(false);
            chip.setCheckable(false);
            chip.setFocusable(false);
            chip.setTextAppearance(com.google.android.material.R.style.TextAppearance_MaterialComponents_Body2);
            chip.setChipBackgroundColorResource(R.color.chip_background_state);
            chip.setTextColor(getResources().getColorStateList(R.color.chip_text_state, requireContext().getTheme()));
            chip.setChipCornerRadius(getResources().getDimension(R.dimen.profile_interest_chip_radius));
            chip.setChipStrokeWidth(0f);
            chipGroupUserInterests.addView(chip);
        }
    }

    private void showAvatarOptions() {
        boolean hasAvatar = currentAvatarUrl != null && !currentAvatarUrl.isEmpty();
        String[] options = hasAvatar
                ? new String[]{"Xem ảnh đại diện", "Chọn ảnh từ thư viện", "Xóa ảnh đại diện"}
                : new String[]{"Chọn ảnh từ thư viện"};

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Ảnh đại diện")
                .setItems(options, (dialog, which) -> {
                    String selected = options[which];
                    if ("Xem ảnh đại diện".equals(selected)) {
                        viewCurrentAvatar();
                    } else if ("Chọn ảnh từ thư viện".equals(selected)) {
                        pickAvatar();
                    } else if ("Xóa ảnh đại diện".equals(selected)) {
                        confirmDeleteAvatar();
                    }
                })
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void viewCurrentAvatar() {
        if (currentAvatarUrl == null || currentAvatarUrl.isEmpty()) return;

        int sizePx = dp(260);
        ImageView bigAvatar = new ImageView(requireContext());
        bigAvatar.setLayoutParams(new LinearLayout.LayoutParams(sizePx, sizePx));
        bigAvatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        Glide.with(this)
                .load(currentAvatarUrl)
                .placeholder(R.drawable.ic_user_placeholder)
                .circleCrop()
                .into(bigAvatar);

        LinearLayout container = new LinearLayout(requireContext());
        container.setGravity(Gravity.CENTER);
        container.setPadding(dp(32), dp(32), dp(32), dp(20));
        container.addView(bigAvatar);

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setView(container)
                .setNegativeButton("Đóng", null)
                .show();
    }

    private void pickAvatar() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        avatarPickerLauncher.launch(intent);
    }

    private void uploadAvatar(Uri uri) {
        if (ivUserProfileAvatar != null) ivUserProfileAvatar.setAlpha(0.5f);
        Toast.makeText(requireContext(), "Đang tải ảnh lên...", Toast.LENGTH_SHORT).show();

        try {
            java.io.InputStream inputStream = requireContext().getContentResolver().openInputStream(uri);
            if (inputStream == null) {
                resetAvatarAlpha();
                Toast.makeText(requireContext(), "Không thể đọc ảnh", Toast.LENGTH_SHORT).show();
                return;
            }

            byte[] bytes = readAllBytes(inputStream);
            inputStream.close();

            String mimeType = requireContext().getContentResolver().getType(uri);
            if (mimeType == null) mimeType = "image/jpeg";
            if ("image/jpg".equalsIgnoreCase(mimeType)) mimeType = "image/jpeg";
            String ext = mimeType.contains("png") ? ".png" : mimeType.contains("webp") ? ".webp" : ".jpg";
            String fileName = "avatar_" + System.currentTimeMillis() + ext;

            okhttp3.RequestBody requestBody = okhttp3.RequestBody.create(
                    okhttp3.MediaType.parse(mimeType), bytes);
            okhttp3.MultipartBody.Part filePart =
                    okhttp3.MultipartBody.Part.createFormData("file", fileName, requestBody);

            postApiService.uploadImage(filePart).enqueue(new Callback<ApiResponse<String>>() {
                @Override
                public void onResponse(Call<ApiResponse<String>> call,
                                       Response<ApiResponse<String>> response) {
                    if (!isAdded()) return;
                    resetAvatarAlpha();
                    if (response.isSuccessful() && response.body() != null
                            && response.body().getResult() != null) {
                        String avatarUrl = normalizeImageUrl(response.body().getResult());
                        updateProfileAvatar(avatarUrl);
                    } else {
                        Toast.makeText(requireContext(), "Không thể tải ảnh lên", Toast.LENGTH_SHORT).show();
                    }
                }

                @Override
                public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                    if (!isAdded()) return;
                    resetAvatarAlpha();
                    Toast.makeText(requireContext(), "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
                }
            });
        } catch (Exception e) {
            resetAvatarAlpha();
            Toast.makeText(requireContext(), "Không thể xử lý ảnh", Toast.LENGTH_SHORT).show();
        }
    }

    private void updateProfileAvatar(String avatarUrl) {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("avatarUrl", avatarUrl);

        userApiService.updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    currentAvatarUrl = avatarUrl;
                    RetrofitClient.saveAvatarUrl(requireContext(), avatarUrl);
                    clearGlideCache();
                    loadAvatar(avatarUrl);
                    Toast.makeText(requireContext(), "Đã cập nhật ảnh đại diện!", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Không thể cập nhật hồ sơ với ảnh mới", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Lỗi kết nối khi cập nhật hồ sơ", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void confirmDeleteAvatar() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Xóa ảnh đại diện")
                .setMessage("Bạn có chắc muốn xóa ảnh đại diện hiện tại?")
                .setPositiveButton("Xóa", (dialog, which) -> deleteAvatar())
                .setNegativeButton("Hủy", null)
                .show();
    }

    private void deleteAvatar() {
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("avatarUrl", "");

        userApiService.updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    currentAvatarUrl = "";
                    RetrofitClient.saveAvatarUrl(requireContext(), "");
                    clearGlideCache();
                    ivUserProfileAvatar.setImageResource(R.drawable.ic_user_placeholder);
                    if (ivCreatePostAvatar != null) {
                        ivCreatePostAvatar.setImageResource(R.drawable.ic_user_placeholder);
                    }
                    Toast.makeText(requireContext(), "Đã xóa ảnh đại diện", Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(requireContext(), "Không thể xóa ảnh. Thử lại sau.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private byte[] readAllBytes(java.io.InputStream inputStream) throws java.io.IOException {
        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
        byte[] data = new byte[4096];
        int read;
        while ((read = inputStream.read(data, 0, data.length)) != -1) {
            buffer.write(data, 0, read);
        }
        return buffer.toByteArray();
    }

    private void clearGlideCache() {
        Glide.get(requireContext()).clearMemory();
        new Thread(() -> Glide.get(requireContext()).clearDiskCache()).start();
    }

    private void resetAvatarAlpha() {
        if (ivUserProfileAvatar != null) ivUserProfileAvatar.setAlpha(1.0f);
    }

    private void loadAvatar(String rawUrl) {
        String avatarUrl = normalizeImageUrl(rawUrl);
        Glide.with(this)
                .load(avatarUrl)
                .placeholder(R.drawable.ic_user_placeholder)
                .error(R.drawable.ic_user_placeholder)
                .skipMemoryCache(true)
                .diskCacheStrategy(DiskCacheStrategy.NONE)
                .circleCrop()
                .into(ivUserProfileAvatar);

        if (ivCreatePostAvatar != null) {
            Glide.with(this)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .skipMemoryCache(true)
                    .diskCacheStrategy(DiskCacheStrategy.NONE)
                    .circleCrop()
                    .into(ivCreatePostAvatar);
        }
    }

    private void hideRelatedPosts() {
        if (tvRelatedPostsTitle != null) tvRelatedPostsTitle.setVisibility(View.GONE);
        if (tvNoRelatedPosts != null) tvNoRelatedPosts.setVisibility(View.GONE);
        if (rvRelatedPosts != null) rvRelatedPosts.setVisibility(View.GONE);
    }

    private void closeDrawer() {
        if (drawerLayoutProfile != null && drawerLayoutProfile.isDrawerOpen(Gravity.END)) {
            drawerLayoutProfile.closeDrawer(Gravity.END);
        }
    }

    private void confirmLogout() {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                .setTitle("Đăng xuất")
                .setMessage("Bạn có chắc muốn đăng xuất?")
                .setPositiveButton("Đăng xuất", (dialog, which) -> logout())
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void confirmDeleteAccount() {
        new AlertDialog.Builder(requireContext())
                .setTitle("Xoá tài khoản")
                .setMessage("Bạn có chắc chắn muốn xoá tài khoản? Hành động này không thể hoàn tác.")
                .setPositiveButton("Xoá", (dialog, which) -> deleteAccount())
                .setNegativeButton("Huỷ", null)
                .show();
    }

    private void deleteAccount() {
        userApiService.deleteAccount().enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful()) {
                    Toast.makeText(requireContext(), "Đã xoá tài khoản thành công!", Toast.LENGTH_SHORT).show();
                    logout();
                } else {
                    Toast.makeText(requireContext(), "Không thể xoá tài khoản. Thử lại sau.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                if (!isAdded()) return;
                Toast.makeText(requireContext(), "Lỗi kết nối server", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void logout() {
        // Bước 1: Lấy FCM token hiện tại của thiết bị
        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            if (task.isSuccessful() && task.getResult() != null) {
                // Gọi API xóa liên kết fcmToken khỏi tài khoản trên server
                callLogoutApi(task.getResult());
            } else {
                // Không lấy được token → vẫn logout local bình thường
                performLocalLogout();
            }
        });
    }

    // Gửi yêu cầu xóa FCM token lên server trước khi dọn dẹp local
    private void callLogoutApi(String fcmToken) {
        AuthApiService authApiService = RetrofitClient.getClient().create(AuthApiService.class);
        authApiService.logout(new LogoutRequest(fcmToken)).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                // Dù server phản hồi thành công hay lỗi, vẫn tiến hành logout local
                performLocalLogout();
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                // Lỗi mạng → không để người dùng bị kẹt, vẫn logout local
                performLocalLogout();
            }
        });
    }

    // Bước 2 & 3: Xóa session local, reset FCM token, chuyển về màn hình đăng nhập
    private void performLocalLogout() {
        // Bước 2a: Ngắt kết nối WebSocket và xóa sạch dữ liệu session local
        WebSocketManager.getInstance().disconnect();
        RetrofitClient.clearSession(requireContext());
        FakeSocialRepository.resetInstance();
        FakePostRepository.resetInstance();

        // Bước 2b: Xóa FCM token khỏi thiết bị — Firebase sẽ cấp token mới
        // cho lần đăng nhập tiếp theo, đảm bảo không bị dính token cũ
        FirebaseMessaging.getInstance().deleteToken().addOnCompleteListener(deleteTask -> {
            if (!isAdded()) return;
            // Bước 3: Chuyển về LoginActivity và xóa sạch toàn bộ back stack
            Intent intent = new Intent(requireContext(), LoginActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(intent);
            requireActivity().finish();
        });
    }

    private String normalizeImageUrl(String url) {
        if (url != null && url.startsWith("/")) {
            return RetrofitClient.getBaseUrl() + url.substring(1);
        }
        return url;
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString().trim() : "";
    }

    private long getLong(Object value, long defaultValue) {
        if (value instanceof Number) return ((Number) value).longValue();
        try {
            return value != null ? Long.parseLong(value.toString()) : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private double getDouble(Object value, double defaultValue) {
        if (value instanceof Number) return ((Number) value).doubleValue();
        try {
            return value != null ? Double.parseDouble(value.toString()) : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
