#!/bin/bash
sed -i '' 's/com.google.android.material.tabs.TabLayout tabLayout = findViewById(R.id.tabLayoutProfile);/com.google.android.material.tabs.TabLayout tabLayout = binding.tabLayoutProfile;/g' UserProfileActivity.java
sed -i '' 's/LinearLayout containerMyPosts = findViewById(R.id.containerMyPosts);/LinearLayout containerMyPosts = binding.containerMyPosts;/g' UserProfileActivity.java
sed -i '' 's/LinearLayout containerMyActivities = findViewById(R.id.containerMyActivities);/LinearLayout containerMyActivities = binding.containerMyActivities;/g' UserProfileActivity.java
sed -i '' 's/View btnHome = findViewById(R.id.btnHomeProfile);/View btnHome = binding.footerNavigationProfile.findViewById(R.id.btnHomeProfile);/g' UserProfileActivity.java
sed -i '' 's/View binding.btnMessages = findViewById(R.id.binding.btnMessagesProfile);/View btnMessages = binding.footerNavigationProfile.findViewById(R.id.btnMessagesProfile);/g' UserProfileActivity.java
sed -i '' 's/View btnNotifications = findViewById(R.id.btnNotificationsProfile);/View btnNotifications = binding.footerNavigationProfile.findViewById(R.id.btnNotificationsProfile);/g' UserProfileActivity.java
sed -i '' 's/LinearLayout menuLogout = findViewById(R.id.menuLogout);/LinearLayout menuLogout = binding.menuLogout;/g' UserProfileActivity.java
sed -i '' 's/LinearLayout menuBlockedUsers = findViewById(R.id.menuBlockedUsers);/LinearLayout menuBlockedUsers = binding.menuBlockedUsers;/g' UserProfileActivity.java
sed -i '' 's/LinearLayout menuLegal = findViewById(R.id.menuLegal);/LinearLayout menuLegal = binding.menuLegal;/g' UserProfileActivity.java
sed -i '' 's/View tvEmpty = findViewById(R.id.tvNoMyActivities);/View tvEmpty = binding.tvNoMyActivities;/g' UserProfileActivity.java
sed -i '' 's/RecyclerView rv = findViewById(R.id.rvMyActivities);/RecyclerView rv = binding.rvMyActivities;/g' UserProfileActivity.java
sed -i '' 's/if (binding.btnMessages != null) binding.btnMessages.setOnClickListener/if (btnMessages != null) btnMessages.setOnClickListener/g' UserProfileActivity.java
