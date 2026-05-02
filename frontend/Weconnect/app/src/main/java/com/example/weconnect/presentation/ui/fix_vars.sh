#!/bin/bash
sed -i '' 's/etPostContent/binding.etPostContent/g' CreatePostActivity.java
sed -i '' 's/tvUserName/binding.tvUserName/g' CreatePostActivity.java
sed -i '' 's/btnPost/binding.btnPost/g' CreatePostActivity.java
sed -i '' 's/ivAddImage/binding.ivAddImage/g' CreatePostActivity.java
sed -i '' 's/ivAddLocation/binding.ivAddLocation/g' CreatePostActivity.java
sed -i '' 's/ivTagInterest/binding.ivTagInterest/g' CreatePostActivity.java
sed -i '' 's/cardSelectedTag/binding.cardSelectedTag/g' CreatePostActivity.java
sed -i '' 's/tvSelectedTag/binding.tvSelectedTag/g' CreatePostActivity.java
sed -i '' 's/cardSelectedLocation/binding.cardSelectedLocation/g' CreatePostActivity.java
sed -i '' 's/tvSelectedLocation/binding.tvSelectedLocation/g' CreatePostActivity.java
sed -i '' 's/ivParticipants/binding.ivParticipants/g' CreatePostActivity.java
sed -i '' 's/cardParticipantLimit/binding.cardParticipantLimit/g' CreatePostActivity.java
sed -i '' 's/tvParticipantLimit/binding.tvParticipantLimit/g' CreatePostActivity.java
sed -i '' 's/ivPostImagePreview/binding.ivPostImagePreview/g' CreatePostActivity.java
sed -i '' 's/ivDuration/binding.ivDuration/g' CreatePostActivity.java
sed -i '' 's/cardSelectedDuration/binding.cardSelectedDuration/g' CreatePostActivity.java
sed -i '' 's/tvSelectedDuration/binding.tvSelectedDuration/g' CreatePostActivity.java

sed -i '' 's/ivClose.setOnClickListener(v -> finish());//g' CreatePostActivity.java
