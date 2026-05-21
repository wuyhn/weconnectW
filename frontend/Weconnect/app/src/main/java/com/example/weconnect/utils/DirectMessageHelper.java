package com.example.weconnect.utils;

import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import com.example.weconnect.activities.ConversationActivity;
import com.example.weconnect.api.ChatApiService;
import com.example.weconnect.api.FriendApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.ChatRoomApiResponse;

import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DirectMessageHelper {

    public static void openDirectMessage(Context context, long userId, String displayName) {
        if (userId <= 0) {
            Toast.makeText(context, "Không thể nhắn tin", Toast.LENGTH_SHORT).show();
            return;
        }

        RetrofitClient.loadToken(context);
        FriendApiService friendApi = RetrofitClient.getClient().create(FriendApiService.class);
        friendApi.getBlockStatus(userId).enqueue(new Callback<ApiResponse<Map<String, Object>>>() {
            @Override
            public void onResponse(Call<ApiResponse<Map<String, Object>>> call,
                                   Response<ApiResponse<Map<String, Object>>> response) {
                Map<String, Object> status = response.isSuccessful() && response.body() != null
                        ? response.body().getResult() : null;

                if (asBoolean(status, "otherUserBlockedCurrent")) {
                    Toast.makeText(context, "Bạn không thể nhắn tin cho người này.",
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                if (asBoolean(status, "currentUserBlockedOther")) {
                    new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                            .setTitle("Bạn đã chặn người này")
                            .setMessage("Bỏ chặn để nhắn tin.")
                            .setPositiveButton("Đã hiểu", null)
                            .show();
                    return;
                }

                openRoom(context, userId, displayName);
            }

            @Override
            public void onFailure(Call<ApiResponse<Map<String, Object>>> call, Throwable t) {
                Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private static void openRoom(Context context, long userId, String displayName) {
        RetrofitClient.loadToken(context);
        RetrofitClient.getClient().create(ChatApiService.class)
                .getDirectRoom(userId).enqueue(new Callback<ApiResponse<ChatRoomApiResponse>>() {
                    @Override
                    public void onResponse(Call<ApiResponse<ChatRoomApiResponse>> call,
                                           Response<ApiResponse<ChatRoomApiResponse>> response) {
                        if (response.isSuccessful() && response.body() != null
                                && response.body().getResult() != null) {
                            long roomId = response.body().getResult().getId();
                            Intent intent = new Intent(context, ConversationActivity.class);
                            intent.putExtra("room_id", roomId);
                            intent.putExtra("chat_name", displayName);
                            context.startActivity(intent);
                        } else {
                            String message = response.body() != null && response.body().getMessage() != null
                                    ? response.body().getMessage() : "Không thể mở đoạn chat";
                            Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ApiResponse<ChatRoomApiResponse>> call, Throwable t) {
                        Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                    }
                });
    }

    private static boolean asBoolean(Map<String, Object> map, String key) {
        if (map == null || !map.containsKey(key)) return false;
        Object value = map.get(key);
        return value instanceof Boolean && (Boolean) value;
    }
}
