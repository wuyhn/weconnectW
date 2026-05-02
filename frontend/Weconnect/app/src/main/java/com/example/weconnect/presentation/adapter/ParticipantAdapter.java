package com.example.weconnect.presentation.adapter;

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
import com.example.weconnect.presentation.ui.UserProfileActivity;
import com.example.weconnect.data.repository.FirebaseManager;

import java.util.List;

public class ParticipantAdapter extends RecyclerView.Adapter<ParticipantAdapter.ViewHolder> {

    public static class Participant {
        public final String name;
        public final int avatarResId;
        public final long userId; // kept for backward compat
        public final String avatarUrl;
        public String userUid; // Firebase UID

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

        // Firebase Storage URL là HTTPS đầy đủ — dùng Glide trực tiếp
        if (p.avatarUrl != null && !p.avatarUrl.isEmpty()) {
            com.bumptech.glide.Glide.with(context)
                    .load(p.avatarUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(holder.ivAvatar);
        } else {
            holder.ivAvatar.setImageResource(p.avatarResId);
        }

        View.OnClickListener openProfile = v -> {
            String cleanName = p.name.replace(" (Người tổ chức)", "").trim();
            String myUid = FirebaseManager.getCurrentUserId();
            boolean isOwn = p.userUid != null && p.userUid.equals(myUid);
            Intent intent = new Intent(context, UserProfileActivity.class);
            intent.putExtra("username", cleanName);
            if (p.userUid != null && !p.userUid.isEmpty()) {
                intent.putExtra("user_uid", p.userUid);
            }
            if (!isOwn) intent.putExtra("view_other", true);
            context.startActivity(intent);
        };

        holder.itemView.setOnClickListener(openProfile);
    }

    @Override
    public int getItemCount() {
        return participants.size();
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

