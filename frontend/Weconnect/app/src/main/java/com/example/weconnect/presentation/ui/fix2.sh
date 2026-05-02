#!/bin/bash
sed -i '' 's/binding.footerNavigationProfile.findViewById(R.id.btnHomeProfile);/binding.btnHomeProfile;/g' UserProfileActivity.java
sed -i '' 's/binding.footerNavigationProfile.findViewById(R.id.btnMessagesProfile);/binding.btnMessagesProfile;/g' UserProfileActivity.java
sed -i '' 's/binding.footerNavigationProfile.findViewById(R.id.btnNotificationsProfile);/binding.btnNotificationsProfile;/g' UserProfileActivity.java
