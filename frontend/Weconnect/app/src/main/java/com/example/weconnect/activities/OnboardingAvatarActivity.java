package com.example.weconnect.activities;

import android.content.Intent;
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

        // Make avatar circle via Glide with placeholder
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
        if (token == null) { goToInterests(); return; }

        try {
            File file = uriToFile(uri);
            if (file == null) { goToInterests(); return; }

            RequestBody reqBody = RequestBody.create(MediaType.parse("image/*"), file);
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
                                final String avatarUrl = url;
                                RetrofitClient.saveAvatarUrl(OnboardingAvatarActivity.this, avatarUrl);
                                // Save to backend profile
                                Map<String, Object> body = new HashMap<>();
                                body.put("avatarUrl", avatarUrl);
                                RetrofitClient.getClient().create(UserApiService.class)
                                        .updateProfile(body).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
                                            @Override
                                            public void onResponse(Call<ApiResponse<Map<String, Object>>> c,
                                                                   Response<ApiResponse<Map<String, Object>>> r) {}
                                            @Override
                                            public void onFailure(Call<ApiResponse<Map<String, Object>>> c, Throwable t) {}
                                        });
                                // Clear Glide cache so all screens load the new avatar
                                Glide.get(OnboardingAvatarActivity.this).clearMemory();
                                new Thread(() -> Glide.get(OnboardingAvatarActivity.this).clearDiskCache()).start();
                            }
                            goToInterests();
                        }
                        @Override
                        public void onFailure(Call<ApiResponse<String>> call, Throwable t) {
                            Toast.makeText(OnboardingAvatarActivity.this,
                                    "Không thể tải ảnh lên. Bỏ qua.", Toast.LENGTH_SHORT).show();
                            goToInterests();
                        }
                    });
        } catch (Exception e) {
            goToInterests();
        }
    }

    private File uriToFile(Uri uri) {
        try {
            InputStream is = getContentResolver().openInputStream(uri);
            if (is == null) return null;
            File tmp = File.createTempFile("avatar_", ".jpg", getCacheDir());
            FileOutputStream out = new FileOutputStream(tmp);
            byte[] buf = new byte[4096];
            int len;
            while ((len = is.read(buf)) > 0) out.write(buf, 0, len);
            out.close();
            is.close();
            return tmp;
        } catch (Exception e) { return null; }
    }

    private void goToInterests() {
        Intent intent = new Intent(this, OnboardingActivity.class);
        startActivity(intent);
        finish();
    }
}
