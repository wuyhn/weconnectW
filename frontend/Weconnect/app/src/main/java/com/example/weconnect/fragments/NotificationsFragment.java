package com.example.weconnect.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.weconnect.R;
import com.example.weconnect.activities.MainActivity;
import com.example.weconnect.adapters.NotificationAdapter;
import com.example.weconnect.api.NotificationApiService;
import com.example.weconnect.api.RetrofitClient;
import com.example.weconnect.models.ApiResponse;
import com.example.weconnect.models.NotificationItem;
import com.example.weconnect.util.BadgeManager;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class NotificationsFragment extends Fragment {

    private RecyclerView rvNotifications;
    private NotificationAdapter adapter;
    private TextView tvEmpty;
    private boolean hasLoadedOnce = false;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.activity_notifications, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        hideOldFooter(view);
        rvNotifications = view.findViewById(R.id.rvNotifications);
        tvEmpty = view.findViewById(R.id.tvNoNotifications);

        ImageView ivMarkAllRead = view.findViewById(R.id.ivMarkAllRead);
        ivMarkAllRead.setOnClickListener(v -> markAllAsRead());

        loadNotifications();
        hasLoadedOnce = true;
    }

    private void hideOldFooter(View root) {
        View footer = root.findViewById(R.id.footerNavNotif);
        if (footer != null) {
            footer.setVisibility(View.GONE);
        }

        adjustBottomToParent(root.findViewById(R.id.rvNotifications));
        adjustBottomToParent(root.findViewById(R.id.tvNoNotifications));
    }

    private void adjustBottomToParent(View view) {
        if (view != null && view.getLayoutParams() instanceof ConstraintLayout.LayoutParams) {
            ConstraintLayout.LayoutParams lp = (ConstraintLayout.LayoutParams) view.getLayoutParams();
            lp.bottomToTop = ConstraintLayout.LayoutParams.UNSET;
            lp.bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID;
            lp.bottomMargin = dp(12);
            view.setLayoutParams(lp);
        }
    }

    public void reloadNotifications() {
        loadNotifications();
    }

    @Override
    public void onHiddenChanged(boolean hidden) {
        super.onHiddenChanged(hidden);
        if (!hidden) {
            loadNotifications();
        }
    }

    private void loadNotifications() {
        RetrofitClient.loadToken(requireContext());
        String token = RetrofitClient.getAuthToken();

        if (token == null) {
            showEmptyNotifications();
            return;
        }

        NotificationApiService apiService = RetrofitClient.getClient()
                .create(NotificationApiService.class);

        apiService.getNotifications().enqueue(new Callback<ApiResponse<List<NotificationItem>>>() {
            @Override
            public void onResponse(Call<ApiResponse<List<NotificationItem>>> call,
                                   Response<ApiResponse<List<NotificationItem>>> response) {
                if (!isAdded()) return;
                if (response.isSuccessful() && response.body() != null
                        && response.body().getResult() != null) {
                    displayNotifications(response.body().getResult());
                } else {
                    showEmptyNotifications();
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<List<NotificationItem>>> call, Throwable t) {
                if (!isAdded()) return;
                showEmptyNotifications();
                Toast.makeText(requireContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayNotifications(List<NotificationItem> notifications) {
        int unreadCount = 0;
        for (NotificationItem item : notifications) {
            if (!item.isRead()) unreadCount++;
        }
        updateBadge(unreadCount);

        if (notifications.isEmpty()) {
            tvEmpty.setVisibility(View.VISIBLE);
            rvNotifications.setVisibility(View.GONE);
            return;
        }

        tvEmpty.setVisibility(View.GONE);
        rvNotifications.setVisibility(View.VISIBLE);

        List<Object> groupedItems = NotificationAdapter.groupByDate(notifications);
        adapter = new NotificationAdapter(requireContext(), groupedItems,
                () -> updateBadge(BadgeManager.getCount()));
        rvNotifications.setLayoutManager(new LinearLayoutManager(requireContext()));
        rvNotifications.setAdapter(adapter);
    }

    private void showEmptyNotifications() {
        tvEmpty.setVisibility(View.VISIBLE);
        rvNotifications.setVisibility(View.GONE);
        updateBadge(0);
    }

    private void markAllAsRead() {
        RetrofitClient.loadToken(requireContext());
        String token = RetrofitClient.getAuthToken();

        if (token != null) {
            NotificationApiService apiService = RetrofitClient.getClient()
                    .create(NotificationApiService.class);

            apiService.markAllAsRead().enqueue(new Callback<ApiResponse<Void>>() {
                @Override
                public void onResponse(Call<ApiResponse<Void>> call,
                                       Response<ApiResponse<Void>> response) {
                    if (!isAdded()) return;
                    updateBadge(0);
                    if (adapter != null) adapter.markAllRead();
                    Toast.makeText(requireContext(),
                            "Đã đánh dấu tất cả là đã đọc", Toast.LENGTH_SHORT).show();
                }

                @Override
                public void onFailure(Call<ApiResponse<Void>> call, Throwable t) {
                    if (!isAdded()) return;
                    Toast.makeText(requireContext(), "Lỗi kết nối", Toast.LENGTH_SHORT).show();
                }
            });
            return;
        }

        updateBadge(0);
        if (adapter != null) adapter.markAllRead();
        Toast.makeText(requireContext(),
                "Đã đánh dấu tất cả là đã đọc", Toast.LENGTH_SHORT).show();
    }

    private void updateBadge(int count) {
        BadgeManager.setCount(count);
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).setNotificationBadgeCount(count);
        }
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
