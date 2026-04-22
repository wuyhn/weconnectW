package com.example.weconnect.adapters;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.models.UserReview;

import java.util.List;

public class UserReviewAdapter extends RecyclerView.Adapter<UserReviewAdapter.UserReviewViewHolder> {

    private final List<UserReview> reviewList;

    public UserReviewAdapter(List<UserReview> reviewList) {
        this.reviewList = reviewList;
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
        holder.tvReviewerName.setText(review.getReviewerName());
        holder.tvReviewerRating.setText(review.getReputationLabel());
        holder.tvReviewerComment.setText(review.getComment());
        holder.tvReviewActivity.setText("📌 " + review.getActivityName());
    }

    @Override
    public int getItemCount() {
        return reviewList.size();
    }

    static class UserReviewViewHolder extends RecyclerView.ViewHolder {
        TextView tvReviewerName, tvReviewerRating, tvReviewerComment, tvReviewActivity;

        public UserReviewViewHolder(@NonNull View itemView) {
            super(itemView);
            tvReviewerName = itemView.findViewById(R.id.tvReviewerName);
            tvReviewerRating = itemView.findViewById(R.id.tvReviewerRating);
            tvReviewerComment = itemView.findViewById(R.id.tvReviewerComment);
            tvReviewActivity = itemView.findViewById(R.id.tvReviewActivity);
        }
    }
}