package com.example.weconnect.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.activities.UserProfileActivity;
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PendingRequestAdapter extends RecyclerView.Adapter<PendingRequestAdapter.ViewHolder> {

    public interface OnMemberActionListener {
        void onApproved(int position);
        void onRejected(int position);
    }

    private final Context context;
    private final List<Map<String, Object>> pendingMembers;
    private final long postId;
    private final OnMemberActionListener listener;
    private int memberCount = 0;
    private int maxMembers = 0;

    public PendingRequestAdapter(Context context, List<Map<String, Object>> pendingMembers,
                                 long postId, OnMemberActionListener listener) {
        this.context = context;
        this.pendingMembers = pendingMembers;
        this.postId = postId;
        this.listener = listener;
    }

    public void updateMemberCounts(int current, int max) {
        this.memberCount = current;
        this.maxMembers = max;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_pending_request, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Map<String, Object> member = pendingMembers.get(position);

        long userId = member.get("userId") != null
                ? ((Number) member.get("userId")).longValue() : 0;
        String userName = member.get("userName") != null
                ? member.get("userName").toString() : "Người dùng #" + userId;
        String status = member.get("status") != null
                ? member.get("status").toString() : "PENDING";

        holder.tvName.setText(userName);
        holder.tvInfo.setText("Đang chờ duyệt");

        // Load avatar from server URL with Glide
        String avatarUrl = member.get("avatarUrl") != null
                ? member.get("avatarUrl").toString() : null;
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (avatarUrl.startsWith("/")) {
                avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
            }
            com.bumptech.glide.Glide.with(context)
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }

        if ("PENDING".equals(status)) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.tvActioned.setVisibility(View.GONE);

            boolean isFull = maxMembers > 0 && memberCount >= maxMembers;
            holder.btnAccept.setEnabled(!isFull);
            holder.btnAccept.setAlpha(isFull ? 0.4f : 1.0f);

            holder.btnAccept.setOnClickListener(v -> approveMember(member, userId, holder));
            holder.btnReject.setOnClickListener(v -> rejectMember(userId, holder));
        } else {
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvActioned.setVisibility(View.VISIBLE);
            holder.tvActioned.setText("APPROVED".equals(status) ? "✅ Đã chấp nhận" : "❌ Đã từ chối");
        }

        // Click avatar/name to view profile
        holder.ivAvatar.setOnClickListener(v -> {
            Intent intent = new Intent(context, UserProfileActivity.class);
            intent.putExtra("username", userName);
            intent.putExtra("user_id", userId);
            intent.putExtra("view_other", true);
            context.startActivity(intent);
        });
        final String fUserName = userName;
        holder.tvName.setOnClickListener(v -> {
            Intent intent = new Intent(context, UserProfileActivity.class);
            intent.putExtra("username", fUserName);
            intent.putExtra("user_id", userId);
            intent.putExtra("view_other", true);
            context.startActivity(intent);
        });
    }

    private void approveMember(Map<String, Object> member, long userId, ViewHolder holder) {
        if (maxMembers > 0 && memberCount >= maxMembers) {
            Toast.makeText(context,
                    "Hoạt động đã đủ thành viên, không thể duyệt thêm người tham gia.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        boolean isLocked = Boolean.TRUE.equals(member.get("isActivityJoinLocked"));
        if (isLocked) {
            Toast.makeText(context,
                    "Người dùng này hiện không thể tham gia hoạt động.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        double reputationScore = member.get("reputationScore") != null
                ? ((Number) member.get("reputationScore")).doubleValue() : 100.0;
        float averageRating = member.get("averageRating") != null
                ? ((Number) member.get("averageRating")).floatValue() : 0f;
        int totalReviewCount = member.get("totalReviewCount") != null
                ? ((Number) member.get("totalReviewCount")).intValue() : 0;

        boolean hasEnoughReviews = totalReviewCount >= 3;
        boolean isHighRisk = reputationScore < 30
                || (hasEnoughReviews && averageRating < 2.0f);
        boolean isWarning = !isHighRisk && (reputationScore < 50
                || (hasEnoughReviews && averageRating < 3.0f));

        if (isHighRisk) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("Người này có mức uy tín rất thấp")
                    .setMessage("Người dùng này có mức uy tín rất thấp hoặc nhiều đánh giá không tốt từ các hoạt động trước đó. Bạn có chắc chắn muốn cho người này tham gia không?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Vẫn cho tham gia", (dialog, which) -> doApproveMember(userId, holder))
                    .show();
        } else if (isWarning) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("Người này có mức uy tín thấp")
                    .setMessage("Người dùng này có điểm uy tín hoặc trung bình đánh giá thấp. Bạn có chắc chắn muốn cho người này tham gia hoạt động không?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Vẫn cho tham gia", (dialog, which) -> doApproveMember(userId, holder))
                    .show();
        } else {
            doApproveMember(userId, holder);
        }
    }

    private void doApproveMember(long userId, ViewHolder holder) {
        RetrofitClient.loadToken(context);
        PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);

        postApi.approveMember(postId, userId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    int pos = holder.getAdapterPosition();
                    if (listener != null && pos != RecyclerView.NO_ID) {
                        listener.onApproved(pos);
                    }
                } else {
                    String errorMsg = "Lỗi khi duyệt";
                    try {
                        if (response.errorBody() != null) {
                            String body = response.errorBody().string();
                            org.json.JSONObject json = new org.json.JSONObject(body);
                            if (json.has("message")) errorMsg = json.getString("message");
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void rejectMember(long userId, ViewHolder holder) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Xác nhận từ chối")
                .setMessage("Bạn có chắc chắn muốn từ chối yêu cầu này?")
                .setPositiveButton("Từ chối", (dialog, which) -> {
                    RetrofitClient.loadToken(context);
                    PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);

                    postApi.rejectMember(postId, userId).enqueue(new Callback<ApiResponse<Void>>() {
                        @Override
                        public void onResponse(Call<ApiResponse<Void>> call,
                                               Response<ApiResponse<Void>> response) {
                            if (response.isSuccessful()) {
                                int pos = holder.getAdapterPosition();
                                if (listener != null && pos != RecyclerView.NO_ID) {
                                    listener.onRejected(pos);
                                }
                            } else {
                                Toast.makeText(context, "Lỗi khi từ chối", Toast.LENGTH_SHORT).show();
                            }
                        }

                        @Override
                        public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                            Toast.makeText(context, "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                        }
                    });
                })
                .setNegativeButton("Huỷ", null)
                .show();
    }

    @Override
    public int getItemCount() {
        return pendingMembers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvInfo, tvActioned;
        LinearLayout layoutActions;
        MaterialButton btnAccept, btnReject;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivPendingAvatar);
            tvName = itemView.findViewById(R.id.tvPendingName);
            tvInfo = itemView.findViewById(R.id.tvPendingInfo);
            tvActioned = itemView.findViewById(R.id.tvPendingActioned);
            layoutActions = itemView.findViewById(R.id.layoutPendingActions);
            btnAccept = itemView.findViewById(R.id.btnPendingAccept);
            btnReject = itemView.findViewById(R.id.btnPendingReject);
        }
    }
}
