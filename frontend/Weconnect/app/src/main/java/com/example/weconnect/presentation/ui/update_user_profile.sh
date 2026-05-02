#!/bin/bash
# Thay thế findViewById bằng binding
sed -i '' 's/setContentView(R.layout.activity_user_profile);/com.example.weconnect.databinding.ActivityUserProfileBinding binding;\nbinding = com.example.weconnect.databinding.ActivityUserProfileBinding.inflate(getLayoutInflater());\nsetContentView(binding.getRoot());/g' UserProfileActivity.java

# Thay thế các biến
sed -i '' 's/ivBackUserProfile/binding.ivBackUserProfile/g' UserProfileActivity.java
sed -i '' 's/ivMenuProfile/binding.ivMenuProfile/g' UserProfileActivity.java
sed -i '' 's/ivUserProfileAvatar/binding.ivUserProfileAvatar/g' UserProfileActivity.java
sed -i '' 's/tvUserProfileName/binding.tvUserProfileName/g' UserProfileActivity.java
sed -i '' 's/tvUserReputation/binding.tvUserReputation/g' UserProfileActivity.java
sed -i '' 's/tvUserBio/binding.tvUserBio/g' UserProfileActivity.java
sed -i '' 's/tvUserBirthday/binding.tvUserBirthday/g' UserProfileActivity.java
sed -i '' 's/tvUserGender/binding.tvUserGender/g' UserProfileActivity.java
sed -i '' 's/btnAddFriend/binding.btnAddFriend/g' UserProfileActivity.java
sed -i '' 's/btnMessage/binding.btnMessage/g' UserProfileActivity.java
sed -i '' 's/btnViewArchive/binding.btnViewArchive/g' UserProfileActivity.java
sed -i '' 's/btnRateUser/binding.btnRateUser/g' UserProfileActivity.java
sed -i '' 's/btnReportUser/binding.btnReportUser/g' UserProfileActivity.java
sed -i '' 's/layoutSocialButtons/binding.layoutSocialButtons/g' UserProfileActivity.java
sed -i '' 's/layoutRateReport/binding.layoutRateReport/g' UserProfileActivity.java
sed -i '' 's/tvFriendCount/binding.tvFriendCount/g' UserProfileActivity.java
sed -i '' 's/rvUserReviews/binding.rvUserReviews/g' UserProfileActivity.java
sed -i '' 's/chipGroupUserInterests/binding.chipGroupUserInterests/g' UserProfileActivity.java
sed -i '' 's/footerNavigationProfile/binding.footerNavigationProfile/g' UserProfileActivity.java
sed -i '' 's/drawerLayoutProfile/binding.drawerLayoutProfile/g' UserProfileActivity.java
sed -i '' 's/menuEditProfile/binding.menuEditProfile/g' UserProfileActivity.java
sed -i '' 's/menuChangePassword/binding.menuChangePassword/g' UserProfileActivity.java
sed -i '' 's/menuDeleteAccount/binding.menuDeleteAccount/g' UserProfileActivity.java
sed -i '' 's/rvActivePostsProfile/binding.rvActivePostsProfile/g' UserProfileActivity.java
sed -i '' 's/tvNoActivePosts/binding.tvNoActivePosts/g' UserProfileActivity.java
sed -i '' 's/tvInterestsTitle/binding.tvInterestsTitle/g' UserProfileActivity.java
sed -i '' 's/cardCreatePostProfile/binding.cardCreatePostProfile/g' UserProfileActivity.java
sed -i '' 's/tvCreatePostHint/binding.tvCreatePostHint/g' UserProfileActivity.java
sed -i '' 's/tvReviewsTitle/binding.tvReviewsTitle/g' UserProfileActivity.java
sed -i '' 's/tvRelatedPostsTitle/binding.tvRelatedPostsTitle/g' UserProfileActivity.java
sed -i '' 's/tvNoRelatedPosts/binding.tvNoRelatedPosts/g' UserProfileActivity.java
sed -i '' 's/rvRelatedPosts/binding.rvRelatedPosts/g' UserProfileActivity.java

# Xoá hàm initViews() và định nghĩa biến
sed -i '' 's/private ImageView binding.ivBackUserProfile;//g' UserProfileActivity.java
sed -i '' 's/private ImageView binding.ivMenuProfile;//g' UserProfileActivity.java
sed -i '' 's/private ImageView binding.ivUserProfileAvatar;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvUserProfileName;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvUserReputation;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvUserBio;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvUserBirthday;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvUserGender;//g' UserProfileActivity.java
sed -i '' 's/private MaterialButton binding.btnAddFriend;//g' UserProfileActivity.java
sed -i '' 's/private MaterialButton binding.btnMessage;//g' UserProfileActivity.java
sed -i '' 's/private MaterialButton binding.btnViewArchive;//g' UserProfileActivity.java
sed -i '' 's/private MaterialButton binding.btnRateUser;//g' UserProfileActivity.java
sed -i '' 's/private MaterialButton binding.btnReportUser;//g' UserProfileActivity.java
sed -i '' 's/private LinearLayout binding.layoutSocialButtons;//g' UserProfileActivity.java
sed -i '' 's/private LinearLayout binding.layoutRateReport;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvFriendCount;//g' UserProfileActivity.java
sed -i '' 's/private RecyclerView binding.rvUserReviews;//g' UserProfileActivity.java
sed -i '' 's/private ChipGroup binding.chipGroupUserInterests;//g' UserProfileActivity.java
sed -i '' 's/private View binding.footerNavigationProfile;//g' UserProfileActivity.java
sed -i '' 's/private DrawerLayout binding.drawerLayoutProfile;//g' UserProfileActivity.java
sed -i '' 's/private LinearLayout binding.menuEditProfile;//g' UserProfileActivity.java
sed -i '' 's/private LinearLayout binding.menuChangePassword;//g' UserProfileActivity.java
sed -i '' 's/private LinearLayout binding.menuDeleteAccount;//g' UserProfileActivity.java
sed -i '' 's/private RecyclerView binding.rvActivePostsProfile;//g' UserProfileActivity.java
sed -i '' 's/private View binding.tvNoActivePosts;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvInterestsTitle;//g' UserProfileActivity.java
sed -i '' 's/private View binding.cardCreatePostProfile;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvCreatePostHint;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvReviewsTitle;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvRelatedPostsTitle;//g' UserProfileActivity.java
sed -i '' 's/private TextView binding.tvNoRelatedPosts;//g' UserProfileActivity.java
sed -i '' 's/private RecyclerView binding.rvRelatedPosts;//g' UserProfileActivity.java

sed -i '' '/private void initViews() {/,/^    }/d' UserProfileActivity.java
sed -i '' 's/initViews();//g' UserProfileActivity.java

