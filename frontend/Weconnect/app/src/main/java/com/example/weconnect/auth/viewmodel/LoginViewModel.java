package com.example.weconnect.auth.viewmodel;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import com.example.weconnect.core.data.ApiResponse;
import com.example.weconnect.core.data.RetrofitClient;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class LoginViewModel extends ViewModel {
    private AuthRepository repository;

    private MutableLiveData<Boolean> isLoading = new MutableLiveData<>(false);
    private MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private MutableLiveData<AuthResponse> loginSuccess = new MutableLiveData<>();

    public LoginViewModel() {
        AuthApiService apiService = RetrofitClient.getClient().create(AuthApiService.class);
        this.repository = new AuthRepository(apiService);
    }

    public LiveData<Boolean> getIsLoading() { return isLoading; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<AuthResponse> getLoginSuccess() { return loginSuccess; }

    public void login(String email, String password) {
        isLoading.setValue(true);
        errorMessage.setValue(null); // Clear previous errors

        repository.login(email, password, new Callback<ApiResponse<AuthResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<AuthResponse>> call, Response<ApiResponse<AuthResponse>> response) {
                isLoading.setValue(false);
                if (response.isSuccessful() && response.body() != null && response.body().isSuccess()) {
                    loginSuccess.setValue(response.body().getResult());
                } else {
                    String errorMsg = "Sai email hoặc mật khẩu";
                    if (response.body() != null && response.body().getMessage() != null) {
                        errorMsg = response.body().getMessage();
                    }
                    errorMessage.setValue(errorMsg);
                }
            }

            @Override
            public void onFailure(Call<ApiResponse<AuthResponse>> call, Throwable t) {
                isLoading.setValue(false);
                errorMessage.setValue("Không thể kết nối Server. Hãy kiểm tra Backend!");
            }
        });
    }
}
