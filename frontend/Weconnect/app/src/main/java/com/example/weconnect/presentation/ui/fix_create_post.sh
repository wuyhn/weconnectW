#!/bin/bash
sed -i '' 's/setContentView(R.layout.activity_create_post);/com.example.weconnect.databinding.ActivityCreatePostBinding binding;\nbinding = com.example.weconnect.databinding.ActivityCreatePostBinding.inflate(getLayoutInflater());\nsetContentView(binding.getRoot());/g' CreatePostActivity.java

# Variables
sed -i '' 's/ivClose/binding.ivClose/g' CreatePostActivity.java
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

# Remove declarations and findViewById
sed -i '' 's/private EditText binding.etPostContent;//g' CreatePostActivity.java
sed -i '' 's/private TextView binding.tvUserName;//g' CreatePostActivity.java
sed -i '' 's/private ImageView binding.ivClose, binding.ivAddImage, binding.ivAddLocation, binding.ivTagInterest;//g' CreatePostActivity.java
sed -i '' 's/private MaterialButton binding.btnPost;//g' CreatePostActivity.java
sed -i '' 's/private MaterialCardView binding.cardSelectedTag;//g' CreatePostActivity.java
sed -i '' 's/private TextView binding.tvSelectedTag;//g' CreatePostActivity.java
sed -i '' 's/private ImageView binding.ivParticipants;//g' CreatePostActivity.java
sed -i '' 's/private MaterialCardView binding.cardParticipantLimit;//g' CreatePostActivity.java
sed -i '' 's/private TextView binding.tvParticipantLimit;//g' CreatePostActivity.java
sed -i '' 's/private MaterialCardView binding.cardSelectedLocation;//g' CreatePostActivity.java
sed -i '' 's/private TextView binding.tvSelectedLocation;//g' CreatePostActivity.java
sed -i '' 's/private ImageView binding.ivPostImagePreview;//g' CreatePostActivity.java
sed -i '' 's/private ImageView binding.ivDuration;//g' CreatePostActivity.java
sed -i '' 's/private MaterialCardView binding.cardSelectedDuration;//g' CreatePostActivity.java
sed -i '' 's/private TextView binding.tvSelectedDuration;//g' CreatePostActivity.java

sed -i '' 's/binding.ivClose = findViewById(R.id.ivClose);//g' CreatePostActivity.java
sed -i '' 's/binding.etPostContent = findViewById(R.id.etPostContent);//g' CreatePostActivity.java
sed -i '' 's/binding.tvUserName = findViewById(R.id.tvUserName);//g' CreatePostActivity.java
sed -i '' 's/binding.btnPost = findViewById(R.id.btnPost);//g' CreatePostActivity.java
sed -i '' 's/binding.ivAddImage = findViewById(R.id.ivAddImage);//g' CreatePostActivity.java
sed -i '' 's/binding.ivAddLocation = findViewById(R.id.ivAddLocation);//g' CreatePostActivity.java
sed -i '' 's/binding.ivTagInterest = findViewById(R.id.ivTagInterest);//g' CreatePostActivity.java
sed -i '' 's/binding.cardSelectedTag = findViewById(R.id.cardSelectedTag);//g' CreatePostActivity.java
sed -i '' 's/binding.tvSelectedTag = findViewById(R.id.tvSelectedTag);//g' CreatePostActivity.java
sed -i '' 's/binding.cardSelectedLocation = findViewById(R.id.cardSelectedLocation);//g' CreatePostActivity.java
sed -i '' 's/binding.tvSelectedLocation = findViewById(R.id.tvSelectedLocation);//g' CreatePostActivity.java
sed -i '' 's/binding.ivParticipants = findViewById(R.id.ivParticipants);//g' CreatePostActivity.java
sed -i '' 's/binding.cardParticipantLimit = findViewById(R.id.cardParticipantLimit);//g' CreatePostActivity.java
sed -i '' 's/binding.tvParticipantLimit = findViewById(R.id.tvParticipantLimit);//g' CreatePostActivity.java
sed -i '' 's/binding.ivDuration = findViewById(R.id.ivDuration);//g' CreatePostActivity.java
sed -i '' 's/binding.cardSelectedDuration = findViewById(R.id.cardSelectedDuration);//g' CreatePostActivity.java
sed -i '' 's/binding.tvSelectedDuration = findViewById(R.id.tvSelectedDuration);//g' CreatePostActivity.java
sed -i '' 's/binding.ivPostImagePreview = findViewById(R.id.ivPostImagePreview);//g' CreatePostActivity.java

