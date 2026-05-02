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
import com.example.weconnect.api.FirestorePostRepository;
import com.google.android.material.button.MaterialButton;

import java.util.List;
import java.util.Map;

/**
 * PendingRequestAdapter — đã migrate sang Firebase.
 * Dùng FirestorePostRepository.approveMember() / rejectMember() thay PostApiService/RetrofitClient.
 * Trường userId trong Map giờ là String UID thay vì Long.
 */
public class PendingRequestAdapter extends RecyclerView.Adapter<PendingRequestAdapter.ViewHolder> {

    public interface OnMemberActionListener {
        void onApproved(int position);
        void onRejected(int position);
    }

    private final Context context;
    private final List<Map<String, Object>> pendingMembers;
    private final String postId; // Firestore post document ID (String)
    private final OnMemberActionListener listener;
    private final FirestorePostRepository postRepo;

    public PendingRequestAdapter(Context context, List<Map<String, Object>> pendingMembers,
                                  String postId, OnMemberActionListener listener) {
        this.context        = context;
        this.pendingMembers = pendingMembers;
        this.postId         = postId;
        this.listener       = listener;
        this.postRepo       = new FirestorePostRepository();
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

        // userId là String UID trong Firebase
        String userId = member.get("userId") != null ? member.get("userId").toString() : "";
        String userName = member.get("userName") != null
            ? member.get("userName").toString() : "Người dùng";
        String status = member.get("status") != null
            ? member.get("status").toString() : "PENDING";

        holder.tvName.setText(userName);
        holder.tvInfo.setText("Đang chờ duyệt");

        // Avatar — luôn fallback đến placeholder (Firestore không trữ URL ở đây)
        holder.ivAvatar.setImageResource(R.drawable.ic_user_placeholder);

        if ("PENDING".equals(status)) {
            holder.layoutActions.setVisibility(View.VISIBLE);
            holder.tvActioned.setVisibility(View.GONE);

            holder.btnAccept.setOnClickListener(v -> approveMember(userId, userName, holder, position));
            holder.btnReject.setOnClickListener(v -> rejectMember(userId, userName, holder, position));
        } else {
            holder.layoutActions.setVisibility(View.GONE);
            holder.tvActioned.setVisibility(View.VISIBLE);
            holder.tvActioned.setText("APPROVED".equals(status) ? "✅ Đã chấp nhận" : "❌ Đã từ chối");
        }

        // Click avatar/name → xem profile
        View.OnClickListener profileClick = v -> {
            Intent intent = new Intent(context, UserProfileActivity.class);
            intent.putExtra("username", userName);
            intent.putExtra("user_uid", userId);
            intent.putExtra("view_other", true);
            context.startActivity(intent);
        };
        holder.ivAvatar.setOnClickListener(profileClick);
        holder.tvName.setOnClickListener(profileClick);
    }

    private void approveMember(String userId, String userName, ViewHolder holder, int position) {
        String ownerId = com.example.weconnect.api.FirebaseManager.getCurrentUserId();
        postRepo.approveMember(postId, userId, ownerId, new FirestorePostRepository.ActionCallback() {
            @Override public void onSuccess(String id) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() -> {
                        holder.layoutActions.setVisibility(View.GONE);
                        holder.tvActioned.setVisibility(View.VISIBLE);
                        holder.tvActioned.setText("✅ Đã chấp nhận");

                        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
                            .setTitle("Đã duyệt!")
                            .setMessage("Bạn đã duyệt " + userName + " tham gia hoạt động.")
                            .setPositiveButton("OK", null)
                            .show();

                        if (listener != null) listener.onApproved(position);
                    });
                }
            }
            @Override public void onError(String err) {
                if (context instanceof android.app.Activity) {
                    ((android.app.Activity) context).runOnUiThread(() ->
                        Toast.makeText(context, "Lỗi khi duyệt: " + err, Toast.LENGTH_SHORT).show()
                    );
                }
            }
        });
    }

    private void rejectMember(String userId, String userName, ViewHolder holder, int position) {
        new com.google.android.material.dialog.MaterialAlertDialogBuilder(context)
            .setTitle("Xác nhận từ chối")
            .setMessage("Bạn có chắc chắn muốn từ chối yêu cầu này?")
            .setPositiveButton("Từ chối", (dialog, which) -> {
                String ownerId = com.example.weconnect.api.FirebaseManager.getCurrentUserId();
                postRepo.rejectMember(postId, userId, ownerId, new FirestorePostRepository.ActionCallback() {
                    @Override public void onSuccess(String id) {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() -> {
                                holder.layoutActions.setVisibility(View.GONE);
                                holder.tvActioned.setVisibility(View.VISIBLE);
                                holder.tvActioned.setText("❌ Đã từ chối");
                                Toast.makeText(context, "Đã từ chối yêu cầu", Toast.LENGTH_SHORT).show();
                                if (listener != null) listener.onRejected(position);
                            });
                        }
                    }
                    @Override public void onError(String err) {
                        if (context instanceof android.app.Activity) {
                            ((android.app.Activity) context).runOnUiThread(() ->
                                Toast.makeText(context, "Lỗi khi từ chối: " + err, Toast.LENGTH_SHORT).show()
                            );
                        }
                    }
                });
            })
            .setNegativeButton("Huỷ", null)
            .show();
    }

    @Override public int getItemCount() { return pendingMembers.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName, tvInfo, tvActioned;
        LinearLayout layoutActions;
        MaterialButton btnAccept, btnReject;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar      = itemView.findViewById(R.id.ivPendingAvatar);
            tvName        = itemView.findViewById(R.id.tvPendingName);
            tvInfo        = itemView.findViewById(R.id.tvPendingInfo);
            tvActioned    = itemView.findViewById(R.id.tvPendingActioned);
            layoutActions = itemView.findViewById(R.id.layoutPendingActions);
            btnAccept     = itemView.findViewById(R.id.btnPendingAccept);
            btnReject     = itemView.findViewById(R.id.btnPendingReject);
        }
    }
}
