package com.example.weconnect.adapters;

import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.example.weconnect.R;
import com.example.weconnect.activities.UserProfileActivity;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.UserReview;

import java.util.List;

public class UserReviewAdapter extends RecyclerView.Adapter<UserReviewAdapter.UserReviewViewHolder> {

    public interface OnReviewClickListener {
        void onReviewClick(UserReview review);
    }

    private final List<UserReview> reviewList;
    private final long currentUserId;
    private final OnReviewClickListener clickListener;

    public UserReviewAdapter(List<UserReview> reviewList, long currentUserId,
                             OnReviewClickListener clickListener) {
        this.reviewList = reviewList;
        this.currentUserId = currentUserId;
        this.clickListener = clickListener;
    }

    @NonNull
    @Override
    public UserReviewViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_user_review, parent, false);
        return new UserReviewViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull UserReviewViewHolder holder, int position) {
        UserReview review = reviewList.get(position);

        // Avatar
        String avatarUrl = review.getReviewerAvatarUrl();
        if (avatarUrl != null && !avatarUrl.isEmpty()) {
            if (!avatarUrl.startsWith("http")) {
                String base = RetrofitClient.getBaseUrl();
                avatarUrl = avatarUrl.startsWith("/")
                        ? base + avatarUrl.substring(1)
                        : base + avatarUrl;
            }
            Glide.with(holder.itemView.getContext())
                    .load(avatarUrl)
                    .placeholder(R.drawable.ic_user_placeholder)
                    .error(R.drawable.ic_user_placeholder)
                    .circleCrop()
                    .into(holder.ivReviewerAvatar);
        } else {
            holder.ivReviewerAvatar.setImageResource(R.drawable.ic_user_placeholder);
        }

        // Name — "Đã chỉnh sửa" chỉ hiện khi là review của currentUser và đã chỉnh sửa
        String displayName = review.getReviewerName() != null ? review.getReviewerName() : "Ẩn danh";
        if (review.isEdited() && review.getReviewerId() == currentUserId) displayName += " · Đã chỉnh sửa";
        holder.tvReviewerName.setText(displayName);

        // Rating — ★★★★☆ với sao vàng / xám
        int starCount = (review.getRating() != null && review.getRating() > 0) ? review.getRating() : 0;
        if (starCount > 0) {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 5; i++) sb.append(i < starCount ? "★" : "☆");
            android.text.SpannableStringBuilder ssb = new android.text.SpannableStringBuilder(sb.toString());
            ssb.setSpan(new android.text.style.ForegroundColorSpan(0xFFFFC107),
                    0, starCount, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            if (starCount < 5) {
                ssb.setSpan(new android.text.style.ForegroundColorSpan(0xFFD1D1D6),
                        starCount, 5, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
            holder.tvReviewerRating.setVisibility(View.VISIBLE);
            holder.tvReviewerRating.setText(ssb);
        } else {
            holder.tvReviewerRating.setVisibility(View.GONE);
        }

        // Activity — no emoji prefix
        String actDisplay = review.getActivityDateDisplay();
        if (actDisplay == null || actDisplay.isEmpty()) actDisplay = review.getActivityName();
        if (actDisplay != null && !actDisplay.isEmpty()) {
            holder.tvReviewActivity.setVisibility(View.VISIBLE);
            holder.tvReviewActivity.setText(actDisplay);
        } else {
            holder.tvReviewActivity.setVisibility(View.GONE);
        }

        // Comment
        holder.tvReviewerComment.setText(review.getComment());

        // Avatar + name click → open reviewer profile
        View.OnClickListener profileClick = v -> {
            long reviewerId = review.getReviewerId();
            if (reviewerId > 0) {
                Intent intent = new Intent(holder.itemView.getContext(), UserProfileActivity.class);
                intent.putExtra("user_id", reviewerId);
                intent.putExtra("view_other", true);
                holder.itemView.getContext().startActivity(intent);
            }
        };
        holder.ivReviewerAvatar.setOnClickListener(profileClick);
        holder.tvReviewerName.setOnClickListener(profileClick);

        // Full item click → detail sheet
        if (clickListener != null) {
            holder.itemView.setOnClickListener(v -> clickListener.onReviewClick(review));
        }
    }

    @Override
    public int getItemCount() {
        return reviewList.size();
    }

    static class UserReviewViewHolder extends RecyclerView.ViewHolder {
        ImageView ivReviewerAvatar;
        TextView tvReviewerName, tvReviewerRating, tvReviewerComment, tvReviewActivity;

        public UserReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            ivReviewerAvatar = itemView.findViewById(R.id.ivReviewerAvatar);
            tvReviewerName = itemView.findViewById(R.id.tvReviewerName);
            tvReviewerRating = itemView.findViewById(R.id.tvReviewerRating);
            tvReviewerComment = itemView.findViewById(R.id.tvReviewerComment);
            tvReviewActivity = itemView.findViewById(R.id.tvReviewActivity);
        }
    }
}
