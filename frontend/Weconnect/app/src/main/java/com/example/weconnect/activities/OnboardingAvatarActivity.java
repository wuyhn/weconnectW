package com.example.weconnect.activities;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.example.weconnect.R;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.api.UserApiService;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.button.MaterialButton;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class OnboardingAvatarActivity extends AppCompatActivity {

    private ImageView ivAvatar;
    private View layoutConfirm;
    private Uri selectedUri = null;

    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    selectedUri = result.getData().getData();
                    if (selectedUri != null) {
                        try {
                            getContentResolver().takePersistableUriPermission(
                                    selectedUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
                        } catch (SecurityException ignored) {}
                        Glide.with(this).load(selectedUri).circleCrop().into(ivAvatar);
                        layoutConfirm.setVisibility(View.VISIBLE);
                    }
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_onboarding_avatar);

        ivAvatar = findViewById(R.id.ivAvatar);
        layoutConfirm = findViewById(R.id.layoutConfirm);
        MaterialButton btnChoose = findViewById(R.id.btnChoosePhoto);
        MaterialButton btnConfirm = findViewById(R.id.btnConfirm);
        View tvSkip = findViewById(R.id.tvSkip);

        Glide.with(this).load(R.drawable.ic_user_placeholder).circleCrop().into(ivAvatar);

        findViewById(R.id.ivBack).setOnClickListener(v -> finish());

        btnChoose.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.setType("image/*");
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
            picker.launch(intent);
        });

        btnConfirm.setOnClickListener(v -> {
            if (selectedUri != null) {
                uploadAndProceed(selectedUri, btnConfirm);
            } else {
                goToInterests();
            }
        });

        tvSkip.setOnClickListener(v -> goToInterests());
    }

    private void uploadAndProceed(Uri uri, MaterialButton btn) {
        btn.setEnabled(false);
        btn.setText("Đang lưu...");

        RetrofitClient.loadToken(this);
        String token = RetrofitClient.getAuthToken();
        if (token == null) {
            goToInterests();
            return;
        }

        try {
            File file = compressAvatarToFile(uri);
            if (file == null) {
                restoreConfirmButton(btn);
                Toast.makeText(this, "Không thể đọc ảnh đã chọn. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                return;
            }

            RequestBody reqBody = RequestBody.create(MediaType.parse("image/jpeg"), file);
            MultipartBody.Part part = MultipartBody.Part.createFormData("file", file.getName(), reqBody);

            RetrofitClient.getClient().create(PostApiService.class)
                    .uploadImage(part).enqueue(new Callback<ApiResponse<String>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<String>> call, Response<ApiResponse<String>> resp) {
                            if (resp.isSuccessful() && resp.body() != null && resp.body().getResult() != null) {
                                String url = resp.body().getResult();
                                if (url.startsWith("/")) {
                                    url = RetrofitClient.getBaseUrl() + url.substring(1);
                                }
                                saveAvatarToBackend(url, btn);
                            } else {
                                restoreConfirmButton(btn);
                                Toast.makeText(OnboardingAvatarActivity.this,
                                        "Không thể tải ảnh lên. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                            restoreConfirmButton(btn);
                            Toast.makeText(OnboardingAvatarActivity.this,
                                    "Không thể tải ảnh lên. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                        }
                    });
        } catch (Exception e) {
            restoreConfirmButton(btn);
            Toast.makeText(this, "Không thể đọc ảnh đã chọn. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
        }
    }

    private void saveAvatarToBackend(String avatarUrl, MaterialButton btn) {
        Map<String, Object> body = new HashMap<>();
        body.put("avatarUrl", avatarUrl);

        RetrofitClient.getClient().create(UserApiService.class)
                .updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                           Response<ApiResponse<Map<String, Object>>> response) {
                        if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                            String savedAvatarUrl = avatarUrl;
                            Map<String, Object> profile = response.body().getResult();
                            if (profile != null && profile.get("avatarUrl") != null
                                    && !profile.get("avatarUrl").toString().isEmpty()) {
                                savedAvatarUrl = profile.get("avatarUrl").toString();
                            }

                            RetrofitClient.saveAvatarUrl(OnboardingAvatarActivity.this, savedAvatarUrl);
                            long myId = RetrofitClient.getUserId(OnboardingAvatarActivity.this);
                            if (myId > 0) {
                                RetrofitClient.cacheAvatarForUser(myId, savedAvatarUrl);
                            }

                            Glide.get(OnboardingAvatarActivity.this).clearMemory();
                            new Thread(() -> Glide.get(OnboardingAvatarActivity.this).clearDiskCache()).start();
                            goToInterests();
                        } else {
                            restoreConfirmButton(btn);
                            Toast.makeText(OnboardingAvatarActivity.this,
                                    "Không thể lưu ảnh đại diện. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                        restoreConfirmButton(btn);
                        Toast.makeText(OnboardingAvatarActivity.this,
                                "Không thể lưu ảnh đại diện. Vui lòng thử lại.", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private void restoreConfirmButton(MaterialButton btn) {
        btn.setEnabled(true);
        btn.setText("Xác nhận");
    }

    private File compressAvatarToFile(Uri uri) {
        try {
            BitmapFactory.Options bounds = new BitmapFactory.Options();
            bounds.inJustDecodeBounds = true;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) return null;
                BitmapFactory.decodeStream(input, null, bounds);
            }

            int sampleSize = 1;
            int maxSide = 1080;
            while ((bounds.outWidth / sampleSize) > maxSide
                    || (bounds.outHeight / sampleSize) > maxSide) {
                sampleSize *= 2;
            }

            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inSampleSize = Math.max(1, sampleSize);

            Bitmap bitmap;
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) return null;
                bitmap = BitmapFactory.decodeStream(input, null, options);
            }
            if (bitmap == null) return null;

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            int quality = 88;
            bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer);
            while (buffer.size() > 4 * 1024 * 1024 && quality > 55) {
                buffer.reset();
                quality -= 10;
                bitmap.compress(Bitmap.CompressFormat.JPEG, quality, buffer);
            }
            bitmap.recycle();

            File tmp = File.createTempFile("avatar_", ".jpg", getCacheDir());
            try (FileOutputStream out = new FileOutputStream(tmp)) {
                out.write(buffer.toByteArray());
            }
            return tmp;
        } catch (Exception e) {
            return null;
        }
    }

    private void goToInterests() {
        Intent intent = new Intent(this, OnboardingActivity.class);
        startActivity(intent);
        finish();
    }
}
