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
import com.example.weconnect.api.PostApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.data.FakePostRepository;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.JoinGroupResponse;
import com.example.weconnect.models.Post;
import com.example.weconnect.models.SearchResultItem;
import com.example.weconnect.activities.PostDetailActivity;
import com.example.weconnect.utils.JoinRequestHelper;
import java.util.ArrayList;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SearchResultAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        private final Context context;
        private final List<SearchResultItem> items = new ArrayList<>();
        private final String currentUsername;

        public SearchResultAdapter(Context context) {
            this.context = context;
            this.currentUsername = FakePostRepository.getInstance().getCurrentUsername();
        }

        public void submitList(List<SearchResultItem> newItems) {
            items.clear();
            items.addAll(newItems);
            notifyDataSetChanged();
        }

        public void clearData() {
            items.clear();
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
        } else if (viewType == SearchResultItem.TYPE_EMPTY) {
            View view = layoutInflater.inflate(R.layout.item_search_empty, parent, false);
            return new EmptyViewHolder(view);
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
            // Ưu tiên global cache (realtime) → item avatarUrl từ API
            String avatarUrl = item.getUserId() > 0
                    ? RetrofitClient.getCachedAvatarForUser(item.getUserId()) : null;
            if (avatarUrl == null || avatarUrl.isEmpty()) avatarUrl = item.getAvatarUrl();
            if (avatarUrl != null && !avatarUrl.isEmpty()) {
                if (avatarUrl.startsWith("/")) {
                    avatarUrl = RetrofitClient.getBaseUrl() + avatarUrl.substring(1);
                }
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
                boolean isOwnProfile = currentUsername.equalsIgnoreCase(item.getTitle());
                if (!isOwnProfile) {
                    intent.putExtra("view_other", true);
                    if (item.getUserId() > 0) {
                        intent.putExtra("user_id", item.getUserId());
                    }
                    if (item.isBlockedBetweenUsers()) {
                        intent.putExtra("blocked_profile", true);
                        intent.putExtra("is_blocked_by_me", item.isBlockedByMe());
                        intent.putExtra("has_blocked_me", item.hasBlockedMe());
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
                long myId = RetrofitClient.getUserId(context);
                boolean isOwnPost = (myId > 0 && post.getAuthorId() == myId)
                        || currentUsername.equalsIgnoreCase(post.getUsername());
                if (isOwnPost) {
                    ph.btnJoin.setVisibility(View.GONE);
                } else if (post.isExpired() || post.isArchived()) {
                    ph.btnJoin.setVisibility(View.VISIBLE);
                    ph.btnJoin.setText("⏰ Đã hết hạn");
                    ph.btnJoin.setEnabled(false);
                    ph.btnJoin.setAlpha(0.4f);
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
                    ph.btnJoin.setOnClickListener(v ->
                            JoinRequestHelper.startJoinFlow(context, post, new JoinRequestHelper.JoinCallback() {
                                @Override
                                public void onSending() {
                                    ph.btnJoin.setEnabled(false);
                                    ph.btnJoin.setAlpha(0.6f);
                                    ph.btnJoin.setText("⏳ Đang gửi...");
                                }

                                @Override
                                public void onSuccess(JoinGroupResponse result) {
                                    post.setPendingApproval(true);
                                    ph.btnJoin.setText("⏳ Đang chờ duyệt");
                                    ph.btnJoin.setEnabled(false);
                                    ph.btnJoin.setAlpha(0.6f);
                                    ph.btnJoin.setOnClickListener(null);
                                    JoinRequestHelper.showJoinToast(context, result);
                                }

                                @Override
                                public void onError(String errorMessage) {
                                    if (errorMessage != null && errorMessage.contains("đủ thành viên")) {
                                        post.setMemberCount(post.getMaxMembers());
                                        ph.btnJoin.setText("Đã đủ thành viên");
                                        ph.btnJoin.setEnabled(false);
                                        ph.btnJoin.setAlpha(0.6f);
                                        ph.btnJoin.setOnClickListener(null);
                                    } else {
                                        ph.btnJoin.setEnabled(true);
                                        ph.btnJoin.setAlpha(1.0f);
                                        ph.btnJoin.setText("Tham gia");
                                    }
                                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show();
                                }
                            }));
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

    static class EmptyViewHolder extends RecyclerView.ViewHolder {
        public EmptyViewHolder(@NonNull View itemView) {
            super(itemView);
        }
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
