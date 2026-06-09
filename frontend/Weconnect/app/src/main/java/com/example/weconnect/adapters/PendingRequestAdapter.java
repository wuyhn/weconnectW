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
import com.example.weconnect.utils.JoinRequestDetailBottomSheet;
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
        boolean isFarLocation = Boolean.TRUE.equals(member.get("isFarLocation"));
        String joinReason = member.get("joinReason") != null
                ? member.get("joinReason").toString() : null;
        String requesterProvince = member.get("requesterProvince") != null
                ? member.get("requesterProvince").toString() : null;
        String activityProvince = member.get("activityProvince") != null
                ? member.get("activityProvince").toString() : null;

        holder.tvName.setText(userName);
        holder.tvInfo.setText("Đang chờ duyệt");

        // Avatar
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

        // Far location badge
        if (isFarLocation) {
            holder.layoutFarLocationBadge.setVisibility(View.VISIBLE);
            if (hasText(requesterProvince) && hasText(activityProvince)) {
                holder.tvLocationInfo.setText(
                        "Bạn: " + requesterProvince + "  ·  Hoạt động: " + activityProvince);
                holder.tvLocationInfo.setVisibility(View.VISIBLE);
            } else {
                holder.tvLocationInfo.setVisibility(View.GONE);
            }
        } else {
            holder.layoutFarLocationBadge.setVisibility(View.GONE);
        }

        // Join reason preview (tối đa 2 dòng)
        if (hasText(joinReason) && !"Cùng địa phương".equalsIgnoreCase(joinReason)) {
            holder.tvJoinReason.setVisibility(View.VISIBLE);
            holder.tvJoinReason.setText("Lý do: " + joinReason);
        } else {
            holder.tvJoinReason.setVisibility(View.GONE);
        }

        // Nút Chi tiết: chỉ hiện khi có dữ liệu đáng xem
        if (isFarLocation || hasText(joinReason)) {
            holder.tvDetail.setVisibility(View.VISIBLE);
            holder.tvDetail.setOnClickListener(v -> openDetailBottomSheet(member, userId, userName));
        } else {
            holder.tvDetail.setVisibility(View.GONE);
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

        // Click avatar/name → profile
        holder.ivAvatar.setOnClickListener(v -> openUserProfile(userId, userName));
        final String fUserName = userName;
        holder.tvName.setOnClickListener(v -> openUserProfile(userId, fUserName));
    }

    private void openUserProfile(long userId, String userName) {
        Intent intent = new Intent(context, UserProfileActivity.class);
        intent.putExtra("username", userName);
        intent.putExtra("user_id", userId);
        intent.putExtra("view_other", true);
        context.startActivity(intent);
    }

    private void openDetailBottomSheet(Map<String, Object> member, long userId, String userName) {
        JoinRequestDetailBottomSheet.show(context, member, postId, memberCount, maxMembers,
                new JoinRequestDetailBottomSheet.OnActionListener() {
                    @Override
                    public void onApprove() {
                        int pos = pendingMembers.indexOf(member);
                        if (pos >= 0) {
                            ViewHolder vh = getViewHolderAt(pos);
                            approveMember(member, userId, vh);
                        }
                    }

                    @Override
                    public void onReject() {
                        int pos = pendingMembers.indexOf(member);
                        if (pos >= 0) {
                            ViewHolder vh = getViewHolderAt(pos);
                            rejectMember(userId, vh);
                        }
                    }
                });
    }

    private ViewHolder getViewHolderAt(int pos) {
        RecyclerView rv = null;
        if (context instanceof androidx.fragment.app.FragmentActivity) {
            rv = ((androidx.fragment.app.FragmentActivity) context).findViewById(R.id.rvPendingRequests);
        }
        if (rv == null) return null;
        RecyclerView.ViewHolder vh = rv.findViewHolderForAdapterPosition(pos);
        return vh instanceof ViewHolder ? (ViewHolder) vh : null;
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

        boolean isFarLocation = Boolean.TRUE.equals(member.get("isFarLocation"));

        if (isHighRisk) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("Người này có mức uy tín rất thấp")
                    .setMessage("Người dùng này có mức uy tín rất thấp hoặc nhiều đánh giá không tốt từ các hoạt động trước đó. Bạn có chắc chắn muốn cho người này tham gia không?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Vẫn cho tham gia", (dialog, which) ->
                            checkFarLocationThenApprove(member, userId, holder, isFarLocation))
                    .show();
        } else if (isWarning) {
            new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                    .setTitle("Người này có mức uy tín thấp")
                    .setMessage("Người dùng này có điểm uy tín hoặc trung bình đánh giá thấp. Bạn có chắc chắn muốn cho người này tham gia hoạt động không?")
                    .setNegativeButton("Hủy", null)
                    .setPositiveButton("Vẫn cho tham gia", (dialog, which) ->
                            checkFarLocationThenApprove(member, userId, holder, isFarLocation))
                    .show();
        } else {
            checkFarLocationThenApprove(member, userId, holder, isFarLocation);
        }
    }

    private void checkFarLocationThenApprove(Map<String, Object> member, long userId,
                                              ViewHolder holder, boolean isFarLocation) {
        if (!isFarLocation) {
            doApproveMember(userId, holder);
            return;
        }

        String requesterProvince = member.get("requesterProvince") != null
                ? member.get("requesterProvince").toString() : "nơi khác";
        String activityProvince = member.get("activityProvince") != null
                ? member.get("activityProvince").toString() : "địa điểm tổ chức";
        String joinReason = member.get("joinReason") != null
                ? member.get("joinReason").toString() : "";

        String message = "Người dùng này ở " + requesterProvince
                + ", trong khi hoạt động tổ chức tại " + activityProvince
                + ". Bạn nên chắc chắn rằng người này có thể tham gia đúng địa điểm.";
        if (hasText(joinReason) && !"Cùng địa phương".equalsIgnoreCase(joinReason)) {
            message += "\n\nLý do: " + joinReason;
        }

        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                .setTitle("Cân nhắc trước khi duyệt")
                .setMessage(message)
                .setNegativeButton("Hủy", null)
                .setPositiveButton("Vẫn duyệt", (dialog, which) -> doApproveMember(userId, holder))
                .show();
    }

    private void doApproveMember(long userId, ViewHolder holder) {
        RetrofitClient.loadToken(context);
        PostApiService postApi = RetrofitClient.getClient().create(PostApiService.class);

        postApi.approveMember(postId, userId).enqueue(new Callback<ApiResponse<Void>>() {
            @Override
            public void onResponse(Call<ApiResponse<Void>> call, Response<ApiResponse<Void>> response) {
                if (response.isSuccessful()) {
                    int pos = holder != null ? holder.getAdapterPosition() : RecyclerView.NO_POSITION;
                    if (listener != null && pos != RecyclerView.NO_POSITION) {
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
                                int pos = holder != null ? holder.getAdapterPosition() : RecyclerView.NO_POSITION;
                                if (listener != null && pos != RecyclerView.NO_POSITION) {
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

    private boolean hasText(String s) {
        return s != null && !s.trim().isEmpty();
    }

    @Override
    public int getItemCount() {
        return pendingMembers.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvInfo, tvActioned, tvDetail, tvLocationInfo, tvJoinReason;
        LinearLayout layoutActions, layoutFarLocationBadge;
        MaterialButton btnAccept, btnReject;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivPendingAvatar);
            tvName = itemView.findViewById(R.id.tvPendingName);
            tvInfo = itemView.findViewById(R.id.tvPendingInfo);
            tvActioned = itemView.findViewById(R.id.tvPendingActioned);
            tvDetail = itemView.findViewById(R.id.tvPendingDetail);
            tvLocationInfo = itemView.findViewById(R.id.tvPendingLocationInfo);
            tvJoinReason = itemView.findViewById(R.id.tvPendingJoinReason);
            layoutActions = itemView.findViewById(R.id.layoutPendingActions);
            layoutFarLocationBadge = itemView.findViewById(R.id.layoutFarLocationBadge);
            btnAccept = itemView.findViewById(R.id.btnPendingAccept);
            btnReject = itemView.findViewById(R.id.btnPendingReject);
        }
    }
}
