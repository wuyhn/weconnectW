package com.example.weconnect.adapters;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.activities.UserProfileActivity;
import com.example.weconnect.api.RetrofitClient;

import java.util.List;

public class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.ViewHolder> {

    public static class Participant {
        public final String name;
        public final int avatarResId;
        public final long userId;
        public final String avatarUrl;

        public Participant(String name, int avatarResId) {
            this(name, avatarResId, -1, null);
        }

        public Participant(String name, int avatarResId, long userId) {
            this(name, avatarResId, userId, null);
        }

        public Participant(String name, int avatarResId, long userId, String avatarUrl) {
            this.name = name;
            this.avatarResId = avatarResId;
            this.userId = userId;
            this.avatarUrl = avatarUrl;
        }
    }

    private final Context context;
    private final List<Participant> participants;

    public ParticipantAdapter(Context context, List<Participant> participants) {
        this.context = context;
        this.participants = participants;
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_participant, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Participant p = participants.get(position);
        holder.tvName.setText(p.name);

        String url = resolveAvatarUrl(p);
        if (url != null) {
            com.bumptech.glide.Glide.with(context)
                    .load(url)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(p.avatarResId);
        }

        View.OnClickListener openProfile = v -> {
            // Lấy tên thật (bỏ hậu tố " (Người tổ chức)" nếu có)
            String cleanName = p.name.replace(" (Người tổ chức)", "").trim();

            // Kiểm tra xem có phải profile của mình không
            long myUserId = RetrofitClient.getUserId(context);
            boolean isOwnProfile = (p.userId > 0 && p.userId == myUserId);

            Intent intent = new Intent(context, UserProfileActivity.class);
            intent.putExtra("username", cleanName);
            if (p.userId > 0) {
                intent.putExtra("user_id", p.userId);
            }
            if (!isOwnProfile) {
                intent.putExtra("view_other", true);
            }
            context.startActivity(intent);
        };

        holder.itemView.setOnClickListener(openProfile);
    }

    @Override
    public int getItemCount() {
        return participants.size();
    }

    private String resolveAvatarUrl(Participant participant) {
        String url = participant.avatarUrl;
        if ((url == null || url.trim().isEmpty()) && participant.userId > 0) {
            url = RetrofitClient.getCachedAvatarForUser(participant.userId);
        }
        if (url == null) return null;

        url = url.trim().replace("\\", "/");
        if (url.isEmpty() || "null".equalsIgnoreCase(url)) return null;
        if (url.startsWith("http://") || url.startsWith("https://")
                || url.startsWith("content://") || url.startsWith("file://")) {
            return url;
        }
        if (url.startsWith("/")) {
            return RetrofitClient.getBaseUrl() + url.substring(1);
        }
        if (url.startsWith("uploads/")) {
            return RetrofitClient.getBaseUrl() + url;
        }
        return url;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivAvatar;
        TextView tvName;

        ViewHolder(@NonNull View itemView) {
            super(itemView);
            ivAvatar = itemView.findViewById(R.id.ivParticipantAvatar);
            tvName = itemView.findViewById(R.id.tvParticipantName);
        }
    }
}

