package com.example.weconnect.adapters;

import android.content.Context;
import android.content.Intent;

import com.example.weconnect.activities.UserProfileActivity;
import com.example.weconnect.activities.ParticipantsActivity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.api.FirebaseManager;
import com.example.weconnect.models.Post;
import com.example.weconnect.models.SearchResultItem;
import com.example.weconnect.activities.PostDetailActivity;
import java.util.ArrayList;
import java.util.List;

public class SearchResultAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Context context;
        private final List<SearchResultItem> items = new ArrayList<>();

        public SearchResultAdapter(Context context) {
            this.context = context;
        }

        public void submitList(List<SearchResultItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

    @Override
    public int getItemViewType(int position) {
        return items.get(position).getViewType();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater layoutInflater = LayoutInflater.from(parent.getContext());

        if (viewType == SearchResultItem.TYPE_SECTION) {
            View view = layoutInflater.inflate(R.layout.item_search_section, parent, false);
            return new SectionViewHolder(view);
        } else if (viewType == SearchResultItem.TYPE_USER) {
            View view = layoutInflater.inflate(R.layout.item_search_user, parent, false);
            return new UserViewHolder(view);
        } else {
            View view = layoutInflater.inflate(R.layout.item_search_post, parent, false);
            return new PostViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        SearchResultItem item = items.get(position);

        if (holder instanceof SectionViewHolder) {
            ((SectionViewHolder) holder).tvSectionTitle.setText(item.getTitle());
        } else if (holder instanceof UserViewHolder) {
            ((UserViewHolder) holder).tvUserName.setText(item.getTitle());
            // Firebase Storage URL là HTTPS đầy đủ — dùng Glide trực tiếp
            String avatarUrl = item.getAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                com.bumptech.glide.Glide.with(context)
                        .load(avatarUrl)
                        .placeholder(R.drawable.ic_user_placeholder)
                        .error(R.drawable.ic_user_placeholder)
                        .circleCrop()
                        .into(((UserViewHolder) holder).ivUserAvatar);
            } else {
                ((UserViewHolder) holder).ivUserAvatar.setImageResource(item.getAvatarResId());
            }

            holder.itemView.setOnClickListener(v -> {
                Intent intent = new Intent(context, UserProfileActivity.class);
                intent.putExtra("username", item.getTitle());
                // Dùng userUid (Firebase UID) thay userId cũ
                String myUid = FirebaseManager.getCurrentUserId();
                String itemUid = item.getUserUid();
                boolean isOwn = myUid != null && myUid.equals(itemUid);
                if (!isOwn) {
                    intent.putExtra("view_other", true);
                    if (itemUid != null && !itemUid.isEmpty()) {
                        intent.putExtra("user_uid", itemUid);
                    }
                }
                context.startActivity(intent);
            });
        } else if (holder instanceof PostViewHolder) {
            PostViewHolder ph = (PostViewHolder) holder;
            Post post = item.getPost();

            ph.tvPostTitle.setText(item.getTitle());

            if (item.getSubtitle() != null && item.getSubtitle().length() > 0) {
                ph.tvPostSubtitle.setVisibility(View.VISIBLE);
                ph.tvPostSubtitle.setText(item.getSubtitle());
            } else {
                ph.tvPostSubtitle.setVisibility(View.GONE);
            }

            // Member count button
            if (post != null) {
                ph.btnMembers.setText("Thành viên: " + post.getMemberCount() + "/" + post.getMaxMembers());
                ph.btnMembers.setOnClickListener(v -> {
                    Intent intent = new Intent(context, ParticipantsActivity.class);
                    intent.putExtra("post_id", post.getId());
                    intent.putExtra("member_count", post.getMemberCount());
                    intent.putExtra("max_members", post.getMaxMembers());
                    context.startActivity(intent);
                });

                // Join status button
                String currentUid = com.example.weconnect.api.FirebaseManager.getCurrentUserId();
                boolean isOwnPost = currentUid != null && currentUid.equals(post.getAuthorUid());
                if (isOwnPost) {
                    ph.btnJoin.setVisibility(android.view.View.GONE);
                } else if (post.isJoined()) {
                    ph.btnJoin.setVisibility(View.VISIBLE);
                    ph.btnJoin.setText("✅ Đã tham gia");
                    ph.btnJoin.setEnabled(false);
                    ph.btnJoin.setAlpha(0.5f);
                } else if (post.isPendingApproval()) {
                    ph.btnJoin.setVisibility(View.VISIBLE);
                    ph.btnJoin.setText("⏳ Đang chờ duyệt");
                    ph.btnJoin.setEnabled(false);
                    ph.btnJoin.setAlpha(0.6f);
                } else {
                    ph.btnJoin.setVisibility(View.VISIBLE);
                    ph.btnJoin.setText("Tham gia");
                    ph.btnJoin.setEnabled(true);
                    ph.btnJoin.setAlpha(1.0f);
                    ph.btnJoin.setOnClickListener(v -> {
                        post.setPendingApproval(true);
                        Toast.makeText(context, "Đã gửi yêu cầu tham gia " + post.getUsername(), Toast.LENGTH_SHORT).show();
                        ph.btnJoin.setText("⏳ Đang chờ duyệt");
                        ph.btnJoin.setEnabled(false);
                        ph.btnJoin.setAlpha(0.6f);
                    });
                }
            }

            holder.itemView.setOnClickListener(v -> {
                if (post != null) {
                    Intent intent = new Intent(context, PostDetailActivity.class);
                    intent.putExtra("post", post);
                    context.startActivity(intent);
                }
            });
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class SectionViewHolder extends RecyclerView.ViewHolder {
        TextView tvSectionTitle;

        public SectionViewHolder(@NonNull View itemView) {
            super(itemView);
            tvSectionTitle = itemView.findViewById(R.id.tvSectionTitle);
        }
    }

    static class UserViewHolder extends RecyclerView.ViewHolder {
        ImageView ivUserAvatar;
        TextView tvUserName;

        public UserViewHolder(@NonNull View itemView) {
            super(itemView);
            ivUserAvatar = itemView.findViewById(R.id.ivSearchUserAvatar);
            tvUserName = itemView.findViewById(R.id.tvSearchUserName);
        }
    }

    static class PostViewHolder extends RecyclerView.ViewHolder {
        TextView tvPostTitle;
        TextView tvPostSubtitle;
        TextView btnJoin;
        TextView btnMembers;

        public PostViewHolder(@NonNull View itemView) {
            super(itemView);
            tvPostTitle = itemView.findViewById(R.id.tvSearchPostTitle);
            tvPostSubtitle = itemView.findViewById(R.id.tvSearchPostSubtitle);
            btnJoin = itemView.findViewById(R.id.btnSearchPostJoin);
            btnMembers = itemView.findViewById(R.id.btnSearchPostMembers);
        }
    }
}
