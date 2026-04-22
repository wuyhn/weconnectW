# 3.1.5 Tích Hợp API Giao Tiếp và Spring Boot

## Giới Thiệu

Sau khi hoàn thiện ứng dụng Android và backend Spring Boot, bước tiếp theo là tích hợp hai thành phần này thông qua REST API. Trong mô hình này, ứng dụng Android đóng vai trò client, gửi request tới backend để lấy dữ liệu hoặc cập nhật dữ liệu, còn backend chịu trách nhiệm xử lý nghiệp vụ và trả kết quả về dưới dạng JSON.

Phía Android sử dụng **Retrofit** kết hợp với **OkHttp** để triển khai lớp giao tiếp mạng. Phía backend sử dụng **Spring Boot** với **Spring Security + JWT** để xác thực và phân quyền các request từ client. Mục này sẽ tìm hiểu chi tiết cách tích hợp hai phía, bao gồm các cơ chế như JWT authentication, error handling, offline queue, và background services.

## Kiến Trúc Tích Hợp

### Sơ Đồ Tổng Quan

```
┌──────────────────────────────────────────────────────────┐
│           Android Application (Client)                   │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │  UI Layer (Activities & Fragments)                 │ │
│  │  - LoginActivity, PostsActivity, ChatActivity, etc │ │
│  └────────────────────────────────────────────────────┘ │
│                        ↓                                 │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Service Layer (API & Business Logic)             │ │
│  │  - AuthService, PostService, ChatService, etc     │ │
│  └────────────────────────────────────────────────────┘ │
│                        ↓                                 │
│  ┌────────────────────────────────────────────────────┐ │
│  │  HTTP Client (Retrofit + OkHttp + Interceptors)   │ │
│  │  - JWT Token Interceptor                          │ │
│  │  - Logging Interceptor                            │ │
│  │  - Retry & Timeout Logic                          │ │
│  └────────────────────────────────────────────────────┘ │
│                        ↓                                 │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Local Storage (SharedPreferences, Room DB)       │ │
│  │  - Token, User Data, Offline Queue, Cache         │ │
│  └────────────────────────────────────────────────────┘ │
│                        ↓                                 │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Background Services (Polling, Sync)              │ │
│  │  - Chat Message Polling (every 2 seconds)         │ │
│  │  - Notification Listener                          │ │
│  │  - Offline Queue Synchronization                  │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
└──────────────────────────────────────────────────────────┘
        ↕ (REST API - HTTP/HTTPS)
        ↕ (Port 8081 - Development)
        ↕ (Port 443 - Production)
┌──────────────────────────────────────────────────────────┐
│        Spring Boot Backend (Server)                      │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │  REST API Controllers                              │ │
│  │  - AuthController               (/api/auth/*)     │ │
│  │  - UserController               (/api/users/*)    │ │
│  │  - PostController               (/api/posts/*)    │ │
│  │  - ChatController               (/api/chats/*)    │ │
│  │  - AdminControllers             (/api/admin/*) .  │ │
│  └────────────────────────────────────────────────────┘ │
│                        ↓                                 │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Security Layer (JWT Validation & CORS)            │ │
│  │  - JwtAuthenticationFilter                         │ │
│  │  - CorsConfig                                      │ │
│  │  - SecurityConfig                                 │ │
│  └────────────────────────────────────────────────────┘ │
│                        ↓                                 │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Service Layer (Business Logic)                    │ │
│  │  - AuthService, UserService, PostService, etc     │ │
│  └────────────────────────────────────────────────────┘ │
│                        ↓                                 │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Repository Layer (Data Access)                    │ │
│  │  - UserRepository, PostRepository, etc            │ │
│  └────────────────────────────────────────────────────┘ │
│                        ↓                                 │
│  ┌────────────────────────────────────────────────────┐ │
│  │  Exception Handling & Response Formatting          │ │
│  │  - Global Exception Handler                       │ │
│  │  - ApiResponse<T> Wrapper                         │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
└──────────────────────────────────────────────────────────┘
                        ↓
┌──────────────────────────────────────────────────────────┐
│          MySQL Database (Persistence)                   │
├──────────────────────────────────────────────────────────┤
│  Users, Posts, Friendships, Chat Rooms, Notifications  │
└──────────────────────────────────────────────────────────┘
```

## API Response Format (Định Nghĩa Cấu Trúc)

### Generic Response Wrapper

Tất cả API response đều theo cấu trúc chuẩn, giúp client dễ xử lý:

```json
{
  "code": 1000,
  "message": "Success",
  "result": {
    "userId": 1,
    "email": "user@weconnect.com",
    "fullName": "John Doe"
  },
  "timestamp": "2026-04-20T10:30:00Z"
}
```

### Status Codes

| Code | Message | Ý Nghĩa | HTTP Status |
|------|---------|---------|------------|
| **1000** | Success | Thành công | 200 OK |
| **1001** | Bad Request | Dữ liệu sai/thiếu | 400 Bad Request |
| **1002** | Unauthorized | Token hết hạn/sai | 401 Unauthorized |
| **1003** | Not Found | Resource không tồn tại | 404 Not Found |
| **1004** | Forbidden | Không có quyền truy cập | 403 Forbidden |
| **1005** | Conflict | Dữ liệu bị trùng (email đã tồn tại) | 409 Conflict |
| **1006** | Internal Server Error | Lỗi backend | 500 Server Error |

### Backend ApiResponse Class (Spring Boot)

```java
@Data
@Builder
public class ApiResponse<T> {
    private Integer code;
    private String message;
    private T result;
    private LocalDateTime timestamp;
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .code(1000)
            .message("Success")
            .result(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> ApiResponse<T> error(Integer code, String message) {
        return ApiResponse.<T>builder()
            .code(code)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

## JWT Authentication & Token Flow

### JWT (JSON Web Token) Giải Thích

JWT là một token dạng text mã hóa chứa thông tin user (claims). Cấu trúc: `header.payload.signature`

```
eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9               // Header
.
eyJzdWIiOiIxIiwiZW1haWwiOiJhZG1pbkB3ZWNvbm5lY3QiLCJyb2xlIjoxLCJpYXQiOjE2MTMzODEwMDcsImV4cCI6MTYxMzQ2NzQwN30  // Payload (claims)
.
SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c      // Signature
```

**Payload (decoded):**
```json
{
  "sub": "1",
  "email": "admin@weconnect.com",
  "role": 1,
  "iat": 1613381007,
  "exp": 1613467407
}
```

### Authentication Flow (Sơ Đồ Sequence)

```
┌──────────────┐                         ┌──────────────┐
│   Android    │                         │   Backend    │
└──────────────┘                         └──────────────┘
       │                                        │
       │ 1. POST /api/auth/login                │
       │    {email, password}                   │
       ├─────────────────────────────────────→ │
       │                                        │
       │                          2. Verify password
       │                              (BCrypt)  │
       │                                        │
       │                      3. Create JWT Token
       │                          (24h expiry)  │
       │                                        │
       │ 4. 200 OK                              │
       │    {token, userId, email}              │
       │ ← ──────────────────────────────────── │
       │                                        │
       │ 5. Save token to SharedPreferences    │
       │                                        │
       │ 6. GET /api/users/me                   │
       │    Header: Authorization: Bearer {JWT} │
       ├─────────────────────────────────────→ │
       │                                        │
       │                   7. Validate JWT Token
       │                      (signature, exp)  │
       │                                        │
       │                      8. Extract claims
       │                          (userId, role)│
       │                                        │
       │                 9. Return user profile │
       │ 10. 200 OK {user data}                 │
       │ ← ──────────────────────────────────── │
       │                                        │
       │  ... (subsequent requests with token)  │
       │                                        │
       │ 11. POST /api/posts                    │
       │     {content, location, etc}           │
       │     Header: Authorization: Bearer {JWT}│
       ├─────────────────────────────────────→ │
       │                                        │
       │         12. Validate token, verify owner
       │             Process request, save to DB │
       │                                        │
       │ 13. 200 OK {post data}                 │
       │ ← ──────────────────────────────────── │
```

### Backend JWT Implementation (Spring Boot)

**JwtTokenProvider.java**
```java
@Component
public class JwtTokenProvider {
    
    @Value("${jwt.secret}")
    private String jwtSecret;
    
    @Value("${jwt.expiration}")
    private long jwtExpiration;
    
    // Tạo JWT token
    public String generateToken(String email, Long userId, Integer role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userId);
        claims.put("role", role);
        
        return Jwts.builder()
            .setClaims(claims)
            .setSubject(email)
            .setIssuedAt(new Date())
            .setExpiration(new Date(System.currentTimeMillis() + jwtExpiration))
            .signWith(SignatureAlgorithm.HS512, jwtSecret)
            .compact();
    }
    
    // Trích xuất email từ token
    public String getEmailFromToken(String token) {
        return Jwts.parser()
            .setSigningKey(jwtSecret)
            .parseClaimsJws(token)
            .getBody()
            .getSubject();
    }
    
    // Trích xuất userId từ token
    public Long getUserIdFromToken(String token) {
        Claims claims = Jwts.parser()
            .setSigningKey(jwtSecret)
            .parseClaimsJws(token)
            .getBody();
        return claims.get("userId", Long.class);
    }
    
    // Kiểm tra token còn hiệu lực không
    public boolean validateToken(String token) {
        try {
            Jwts.parser()
                .setSigningKey(jwtSecret)
                .parseClaimsJws(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }
}
```

**JwtAuthenticationFilter.java**
```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    private final JwtTokenProvider tokenProvider;
    
    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String jwt = getJwtFromRequest(request);
            
            if (jwt != null && tokenProvider.validateToken(jwt)) {
                String email = tokenProvider.getEmailFromToken(jwt);
                Long userId = tokenProvider.getUserIdFromToken(jwt);
                
                // Đặt SecurityContext
                UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                        new CustomUserDetails(userId, email),
                        null,
                        new ArrayList<>());
                
                SecurityContextHolder.getContext()
                    .setAuthentication(authentication);
            }
        } catch (Exception ex) {
            logger.error("Could not set user authentication", ex);
        }
        
        filterChain.doFilter(request, response);
    }
    
    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (bearerToken != null && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
```

## Phía Android: Retrofit & OkHttp Configuration

### Base URL Setup

```java
public class ApiClient {
    // Development
    private static final String BASE_URL_DEV = "http://10.0.2.2:8081/api/";
    // Hoặc nếu dùng device: "http://192.168.x.x:8081/api/"
    
    // Production
    private static final String BASE_URL_PROD = "https://api.weconnect.com/api/";
    
    private static Retrofit retrofit;
    private static OkHttpClient httpClient;
    
    public static Retrofit getRetrofitInstance(boolean isDevelopment) {
        if (retrofit == null) {
            httpClient = createOkHttpClient();
            
            String baseUrl = isDevelopment ? BASE_URL_DEV : BASE_URL_PROD;
            
            retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(httpClient)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        }
        return retrofit;
    }
    
    private static OkHttpClient createOkHttpClient() {
        return new OkHttpClient.Builder()
            .addInterceptor(new TokenInterceptor())
            .addInterceptor(new HttpLoggingInterceptor()
                .setLevel(HttpLoggingInterceptor.Level.BODY))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }
}
```

### JWT Token Interceptor

```java
public class TokenInterceptor implements Interceptor {
    
    private TokenManager tokenManager;
    
    public TokenInterceptor(TokenManager tokenManager) {
        this.tokenManager = tokenManager;
    }
    
    @Override
    public Response intercept(Chain chain) throws IOException {
        Request originalRequest = chain.request();
        
        // Lấy token từ storage
        String token = tokenManager.getToken();
        
        // Nếu không có token, bỏ qua
        if (token == null) {
            return chain.proceed(originalRequest);
        }
        
        // Thêm token vào header Authorization
        Request requestWithToken = originalRequest.newBuilder()
            .header("Authorization", "Bearer " + token)
            .build();
        
        Response response = chain.proceed(requestWithToken);
        
        // Nếu 401 (token hết hạn), reset session
        if (response.code() == 401) {
            tokenManager.clearToken();
            // Redirect tới login screen
            // (xử lý ở MainActivity hoặc Fragment)
        }
        
        return response;
    }
}
```

### API Interface Definition (Retrofit)

```java
public interface WeConnectApi {
    
    // ========== Authentication ==========
    @POST("auth/login")
    Call<ApiResponse<LoginResponse>> login(@Body AuthRequest request);
    
    @POST("auth/register")
    Call<ApiResponse<Void>> register(@Body RegisterRequest request);
    
    // ========== Users ==========
    @GET("users/me")
    Call<ApiResponse<UserResponse>> getCurrentUser();
    
    @GET("users/{id}")
    Call<ApiResponse<UserResponse>> getUser(@Path("id") Long userId);
    
    @PUT("users/me")
    Call<ApiResponse<UserResponse>> updateProfile(
        @Body UpdateProfileRequest request);
    
    @PUT("users/me/interests")
    Call<ApiResponse<Void>> saveInterests(
        @Body Map<String, List<String>> body);
    
    // ========== Posts ==========
    @GET("posts")
    Call<ApiResponse<List<PostResponse>>> getActivePosts();
    
    @GET("posts/{id}")
    Call<ApiResponse<PostResponse>> getPostDetail(@Path("id") Long postId);
    
    @POST("posts")
    Call<ApiResponse<PostResponse>> createPost(@Body PostRequest request);
    
    @PUT("posts/{id}")
    Call<ApiResponse<PostResponse>> updatePost(
        @Path("id") Long postId,
        @Body PostRequest request);
    
    @DELETE("posts/{id}")
    Call<ApiResponse<Void>> deletePost(@Path("id") Long postId);
    
    @POST("posts/{id}/join")
    Call<ApiResponse<Void>> joinPost(@Path("id") Long postId);
    
    @POST("posts/{id}/leave")
    Call<ApiResponse<Void>> leavePost(@Path("id") Long postId);
    
    // ========== Chat ==========
    @GET("chats/rooms")
    Call<ApiResponse<List<ChatRoomResponse>>> getChatRooms();
    
    @GET("chats/messages/{roomId}")
    Call<ApiResponse<List<ChatMessageResponse>>> getMessages(
        @Path("roomId") Long roomId,
        @Query("afterId") Long afterId);
    
    @POST("chats/messages")
    Call<ApiResponse<ChatMessageResponse>> sendMessage(
        @Body SendMessageRequest request);
    
    // ========== Notifications ==========
    @GET("notifications")
    Call<ApiResponse<List<NotificationResponse>>> getNotifications();
    
    @GET("notifications/unread-count")
    Call<ApiResponse<Integer>> getUnreadNotificationCount();
}
```

### Request/Response Models (DTOs)

```java
// Request
@Data
public class AuthRequest {
    private String email;
    private String password;
}

@Data
public class PostRequest {
    private String content;
    
    @SerializedName("interest_tag")
    private String interestTag;
    
    private String location;
    
    @SerializedName("max_members")
    private Integer maxMembers;
    
    @SerializedName("start_time")
    private LocalDateTime startTime;
    
    @SerializedName("end_time")
    private LocalDateTime endTime;
}

// Response
@Data
public class LoginResponse {
    @SerializedName("user_id")
    private Long userId;
    
    private String email;
    
    @SerializedName("full_name")
    private String fullName;
    
    private String token;
    
    private Integer role;
}

@Data
public class UserResponse {
    private Long id;
    private String email;
    
    @SerializedName("full_name")
    private String fullName;
    
    @SerializedName("avatar_url")
    private String avatarUrl;
    
    private String bio;
    
    @SerializedName("interest_tags")
    private List<String> interestTags;
    
    @SerializedName("average_rating")
    private Double averageRating;
    
    @SerializedName("reputation_score")
    private Integer reputationScore;
}

@Data
public class ChatMessageResponse {
    private Long id;
    
    @SerializedName("room_id")
    private Long roomId;
    
    private UserResponse sender;
    
    private String content;
    
    @SerializedName("created_at")
    private LocalDateTime createdAt;
}
```

### Error Handling & Retry Logic

```java
public class ApiClient {
    
    private static final int MAX_RETRIES = 3;
    
    public <T> void executeWithRetry(Call<ApiResponse<T>> call,
                                      final ApiCallback<T> callback) {
        executeWithRetry(call, callback, 1);
    }
    
    private <T> void executeWithRetry(final Call<ApiResponse<T>> call,
                                       final ApiCallback<T> callback,
                                       final int attempt) {
        call.enqueue(new Callback<ApiResponse<T>>() {
            @Override
            public void onResponse(Call<ApiResponse<T>> callResponse,
                                 Response<ApiResponse<T>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<T> apiResponse = response.body();
                    
                    if (apiResponse.getCode() == 1000) {
                        callback.onSuccess(apiResponse.getResult());
                    } else if (apiResponse.getCode() == 1002) {
                        // 401 Unauthorized - Token expired
                        callback.onError("Session expired. Please login again.");
                    } else {
                        callback.onError(apiResponse.getMessage());
                    }
                } else {
                    handleHttpError(response.code(), callback);
                }
            }
            
            @Override
            public void onFailure(Call<ApiResponse<T>> callResponse, Throwable t) {
                if (t instanceof IOException && attempt < MAX_RETRIES) {
                    // Network error - retry
                    executeWithRetry(call.clone(), callback, attempt + 1);
                } else {
                    callback.onError("Network error: " + t.getMessage());
                }
            }
        });
    }
    
    private <T> void handleHttpError(int code, ApiCallback<T> callback) {
        switch (code) {
            case 400:
                callback.onError("Bad request. Please check your input.");
                break;
            case 401:
                callback.onError("Unauthorized. Please login.");
                break;
            case 403:
                callback.onError("Forbidden. You don't have permission.");
                break;
            case 404:
                callback.onError("Resource not found.");
                break;
            case 500:
                callback.onError("Server error. Please try again later.");
                break;
            default:
                callback.onError("Unknown error: " + code);
        }
    }
}

// Callback interface
public interface ApiCallback<T> {
    void onSuccess(T data);
    void onError(String error);
}
```

### Offline Queue Mechanism

```java
public class OfflineQueueManager {
    
    private SharedPreferences sharedPreferences;
    private Gson gson;
    
    public OfflineQueueManager(Context context) {
        this.sharedPreferences = context.getSharedPreferences(
            "offline_queue", Context.MODE_PRIVATE);
        this.gson = new Gson();
    }
    
    // Lưu request vào queue khi offline
    public void addRequestToQueue(String endpoint, String method,
                                  Object requestBody) {
        OfflineRequest request = new OfflineRequest(
            System.currentTimeMillis(),
            endpoint,
            method,
            gson.toJson(requestBody));
        
        List<OfflineRequest> queue = getQueue();
        queue.add(request);
        saveQueue(queue);
    }
    
    // Đồng bộ queue khi online
    public void syncQueue(WeConnectApi api, Callback<Void> callback) {
        List<OfflineRequest> queue = getQueue();
        
        for (OfflineRequest request : queue) {
            syncRequest(api, request, callback);
        }
    }
    
    private void syncRequest(WeConnectApi api, OfflineRequest request,
                            Callback<Void> callback) {
        // Tùy thuộc endpoint, gọi API tương ứng
        // Nếu thành công, xóa từ queue
        // Nếu fail, giữ lại để retry lần sau
    }
    
    private List<OfflineRequest> getQueue() {
        String queueJson = sharedPreferences.getString("queue", "[]");
        Type listType = new TypeToken<List<OfflineRequest>>(){}.getType();
        return gson.fromJson(queueJson, listType);
    }
    
    private void saveQueue(List<OfflineRequest> queue) {
        String queueJson = gson.toJson(queue);
        sharedPreferences.edit()
            .putString("queue", queueJson)
            .apply();
    }
}

@Data
class OfflineRequest {
    private long timestamp;
    private String endpoint;
    private String method;  // POST, PUT, DELETE
    private String requestBody;
}
```

### Background Chat Polling Service

```java
public class ChatPollingService extends Service {
    
    private static final long POLLING_INTERVAL = 2000; // 2 giây
    private Handler handler;
    private Runnable pollingRunnable;
    private WeConnectApi api;
    private Long currentRoomId;
    private Long lastMessageId = 0L;
    
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        currentRoomId = intent.getLongExtra("roomId", -1);
        
        if (currentRoomId == -1) {
            stopSelf();
            return START_NOT_STICKY;
        }
        
        api = ApiClient.getWeConnectApiService();
        handler = new Handler(Looper.getMainLooper());
        
        startPolling();
        
        return START_STICKY;
    }
    
    private void startPolling() {
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                pollMessages();
                handler.postDelayed(this, POLLING_INTERVAL);
            }
        };
        
        handler.post(pollingRunnable);
    }
    
    private void pollMessages() {
        api.getMessages(currentRoomId, lastMessageId)
            .enqueue(new Callback<ApiResponse<List<ChatMessageResponse>>>() {
                @Override
                public void onResponse(
                    Call<ApiResponse<List<ChatMessageResponse>>> call,
                    Response<ApiResponse<List<ChatMessageResponse>>> response) {
                    
                    if (response.isSuccessful() && response.body() != null) {
                        List<ChatMessageResponse> messages = 
                            response.body().getResult();
                        
                        if (messages != null && !messages.isEmpty()) {
                            // Broadcast new messages
                            broadcastMessages(messages);
                            
                            // Update lastMessageId
                            ChatMessageResponse lastMessage = 
                                messages.get(messages.size() - 1);
                            lastMessageId = lastMessage.getId();
                        }
                    }
                }
                
                @Override
                public void onFailure(
                    Call<ApiResponse<List<ChatMessageResponse>>> call,
                    Throwable t) {
                    // Log error, continue polling
                    Log.e("ChatPolling", "Polling failed", t);
                }
            });
    }
    
    private void broadcastMessages(List<ChatMessageResponse> messages) {
        Intent intent = new Intent("com.weconnect.NEW_MESSAGES");
        intent.putExtra("messages", new ArrayList<>(messages));
        LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
    }
    
    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacks(pollingRunnable);
    }
    
    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
```

### LocalBroadcastReceiver để Nhận Messages

```java
public class ChatMessageReceiver extends BroadcastReceiver {
    
    private ChatAdapter adapter;
    private RecyclerView recyclerView;
    
    public ChatMessageReceiver(ChatAdapter adapter, RecyclerView recyclerView) {
        this.adapter = adapter;
        this.recyclerView = recyclerView;
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        ArrayList<ChatMessageResponse> messages = 
            intent.getParcelableArrayListExtra("messages");
        
        if (messages != null) {
            for (ChatMessageResponse message : messages) {
                adapter.addMessage(message);
            }
            
            // Scroll to bottom
            recyclerView.scrollToPosition(adapter.getItemCount() - 1);
        }
    }
}
```

## Phía Backend: Controller & Exception Handling

### AuthController.java

```java
@RestController
@RequestMapping("/api/auth")
public class AuthController {
    
    private final AuthService authService;
    private final JwtTokenProvider tokenProvider;
    
    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody @Valid AuthRequest request) {
        try {
            String email = request.getEmail();
            String password = request.getPassword();
            
            // AuthService xác thực password
            User user = authService.authenticateUser(email, password);
            
            if (user == null) {
                return ResponseEntity.status(401).body(ApiResponse.error(
                    1002, "Invalid email or password"));
            }
            
            // Tạo JWT token
            String token = tokenProvider.generateToken(
                user.getEmail(), user.getId(), user.getRole());
            
            LoginResponse response = LoginResponse.builder()
                .userId(user.getId())
                .email(user.getEmail())
                .fullName(user.getFullName())
                .token(token)
                .role(user.getRole())
                .build();
            
            return ResponseEntity.ok(ApiResponse.success(response));
            
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(
                1006, "Login failed: " + e.getMessage()));
        }
    }
    
    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody @Valid RegisterRequest request) {
        try {
            if (authService.emailExists(request.getEmail())) {
                return ResponseEntity.status(409).body(ApiResponse.error(
                    1005, "Email already exists"));
            }
            
            User user = authService.registerUser(request);
            
            return ResponseEntity.ok(ApiResponse.<Void>builder()
                .code(1000)
                .message("Registration successful")
                .build());
                
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(
                1006, "Registration failed"));
        }
    }
}
```

### Global Exception Handler

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> handleValidationException(
        MethodArgumentNotValidException ex) {
        
        String message = ex.getBindingResult()
            .getAllErrors()
            .stream()
            .map(error -> error.getDefaultMessage())
            .collect(Collectors.joining(", "));
        
        return ResponseEntity.badRequest().body(ApiResponse.error(
            1001, "Validation error: " + message));
    }
    
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<?> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(404).body(ApiResponse.error(
            1003, ex.getMessage()));
    }
    
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(403).body(ApiResponse.error(
            1004, "Access denied"));
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> handleGenericException(Exception ex) {
        ex.printStackTrace();
        return ResponseEntity.status(500).body(ApiResponse.error(
            1006, "Internal server error"));
    }
}
```

### CORS Configuration

```java
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
            .allowedOriginPatterns("http://localhost:[*]",
                                    "http://10.0.2.2:[*]",
                                    "https://weconnect.com")
            .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
            .allowedHeaders("*")
            .exposedHeaders("Authorization")
            .allowCredentials(true)
            .maxAge(3600);
    }
}
```

## API Endpoints Reference Table

### Authentication (Public)

| Method | Endpoint | Auth | Mô Tả |
|--------|----------|------|-------|
| POST | `/api/auth/login` | ✗ | Đăng nhập (trả JWT token) |
| POST | `/api/auth/register` | ✗ | Đăng ký tài khoản mới |

### Users (Protected)

| Method | Endpoint | Auth | Mô Tả |
|--------|----------|------|-------|
| GET | `/api/users/me` | ✓ | Lấy thông tin user hiện tại |
| GET | `/api/users/{id}` | ✓ | Lấy info user khác |
| PUT | `/api/users/me` | ✓ | Cập nhật profile |
| PUT | `/api/users/me/interests` | ✓ | Lưu sở thích |
| PUT | `/api/users/{id}/password` | ✓ | Đổi mật khẩu |

### Posts (Protected)

| Method | Endpoint | Auth | Mô Tả |
|--------|----------|------|-------|
| GET | `/api/posts` | ✓ | Danh sách bài viết active |
| GET | `/api/posts/{id}` | ✓ | Chi tiết bài viết |
| POST | `/api/posts` | ✓ | Tạo bài viết mới |
| PUT | `/api/posts/{id}` | ✓ | Cập nhật bài viết |
| DELETE | `/api/posts/{id}` | ✓ | Xóa bài viết |
| POST | `/api/posts/{id}/join` | ✓ | Tham gia hoạt động |
| POST | `/api/posts/{id}/leave` | ✓ | Rời hoạt động |

### Chat (Protected)

| Method | Endpoint | Auth | Mô Tả |
|--------|----------|------|-------|
| GET | `/api/chats/rooms` | ✓ | Danh sách phòng chat |
| GET | `/api/chats/messages/{roomId}` | ✓ | Danh sách tin nhắn (polling) |
| POST | `/api/chats/messages` | ✓ | Gửi tin nhắn mới |
| DELETE | `/api/chats/messages/{msgId}` | ✓ | Xóa tin nhắn |

### Notifications (Protected)

| Method | Endpoint | Auth | Mô Tả |
|--------|----------|------|-------|
| GET | `/api/notifications` | ✓ | Danh sách thông báo |
| GET | `/api/notifications/unread-count` | ✓ | Số thông báo chưa đọc |
| PUT | `/api/notifications/{id}/read` | ✓ | Đánh dấu đã đọc |

### Admin (Protected - role=1)

| Method | Endpoint | Auth | Mô Tả |
|--------|----------|------|-------|
| GET | `/api/admin/users` | ✓ | Danh sách users |
| GET | `/api/admin/posts` | ✓ | Danh sách posts |
| GET | `/api/admin/reports` | ✓ | Báo cáo vi phạm |
| GET | `/api/admin/dashboard/stats` | ✓ | Thống kê dashboard |
| PUT | `/api/admin/users/{id}/block` | ✓ | Block user |
| POST | `/api/admin/reports/{id}/resolve` | ✓ | Xử lý báo cáo |

## Logging Strategy

### Development Environment

```java
HttpLoggingInterceptor loggingInterceptor = 
    new HttpLoggingInterceptor();
loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.BODY);

// Logs mỗi request/response đầy đủ:
// --> POST /api/auth/login
// Content-Type: application/json
// Content-Length: 50
// 
// {"email":"user@weconnect.com","password":"password"}
// <-- 200 OK
// Content-Type: application/json
// {"code":1000,"message":"Success","result":{"token":"..."}}
```

### Production Environment

```java
HttpLoggingInterceptor loggingInterceptor = 
    new HttpLoggingInterceptor();
loggingInterceptor.setLevel(HttpLoggingInterceptor.Level.NONE);
// Không log để bảo mật & tối ưu performance
```

## Testing Strategy

### Unit Test Example

```java
public class AuthServiceTest {
    
    @Mock
    private UserRepository userRepository;
    
    @InjectMocks
    private AuthService authService;
    
    @Test
    public void testLoginSuccess() {
        // Arrange
        User user = User.builder()
            .id(1L)
            .email("user@weconnect.com")
            .password(passwordEncoder.encode("password123"))
            .role(0)
            .build();
        
        when(userRepository.findByEmail("user@weconnect.com"))
            .thenReturn(Optional.of(user));
        
        // Act
        User result = authService.authenticateUser(
            "user@weconnect.com", "password123");
        
        // Assert
        assertNotNull(result);
        assertEquals("user@weconnect.com", result.getEmail());
    }
    
    @Test
    public void testLoginFailInvalidEmail() {
        // Arrange
        when(userRepository.findByEmail("nonexistent@weconnect.com"))
            .thenReturn(Optional.empty());
        
        // Act & Assert
        assertThrows(RuntimeException.class, () ->
            authService.authenticateUser("nonexistent@weconnect.com", "pass"));
    }
}
```

### Integration Test Example

```java
@SpringBootTest
public class AuthIntegrationTest {
    
    @Autowired
    private TestRestTemplate testRestTemplate;
    
    @Test
    public void testLoginEndpoint() {
        // Request
        AuthRequest request = new AuthRequest();
        request.setEmail("admin@weconnect.com");
        request.setPassword("password");
        
        // Act
        ResponseEntity<ApiResponse<LoginResponse>> response = 
            testRestTemplate.postForEntity(
                "/api/auth/login",
                request,
                new ParameterizedTypeReference<ApiResponse<LoginResponse>>(){});
        
        // Assert
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(1000, response.getBody().getCode());
        assertNotNull(response.getBody().getResult().getToken());
    }
}
```

## Troubleshooting & Common Issues

### Issue 1: Token Not Sent to Backend

**Triệu Chứng:** Always get 401 Unauthorized

**Nguyên Nhân:** TokenInterceptor không thêm token vào header

**Giải Pháp:**
```java
// Kiểm tra:
1. Token có lưu vào SharedPreferences không?
   sharedPrefs.getString("token", null)
2. Interceptor có được thêm vào OkHttpClient?
   addInterceptor(new TokenInterceptor())
3. Header format đúng không?
   "Authorization: Bearer {token}"
```

### Issue 2: CORS Error from Web Admin

**Triệu Chứng:** ` Access to XMLHttpRequest blocked by CORS policy`

**Nguyên Nhân:** CORS not configured đúng

**Giải Pháp:**
```java
// Trong CorsConfig, kiểm tra:
registry.addMapping("/api/**")
    .allowedOriginPatterns("http://localhost:[*]")  // ← Phải match
    .allowedMethods("GET", "POST", "PUT", "DELETE")
    .allowCredentials(true);
```

### Issue 3: Chat Polling Drains Battery

**Triệu Chứng:** App dùng pin nhanh vì polling liên tục

**Giải Pháp:**
```java
// Tăng polling interval từ 2s thành 5-10s
private static final long POLLING_INTERVAL = 5000;

// Hoặc pause polling khi app ở background
@Override
public void onPause() {
    handler.removeCallbacks(pollingRunnable);
    super.onPause();
}

@Override
public void onResume() {
    super.onResume();
    startPolling();
}
```

### Issue 4: Gson Deserialization Error

**Triệu Chứng:** `JsonSyntaxException` khi parse response

**Nguyên Nhân:** Field names không match (camelCase vs snake_case)

**Giải Pháp:**
```java
@Data
public class UserResponse {
    private Long id;
    
    @SerializedName("full_name")  // ← Bắt buộc nếu JSON là "full_name"
    private String fullName;
}
```

### Issue 5: Network Error on Physical Device

**Triệu Chứng:** Works on emulator, fails on real device

**Nguyên Nhân:** Base URL `10.0.2.2` chỉ hoạt động trên emulator

**Giải Pháp:**
```java
// Dùng IP address của backend server
String baseUrl = "http://192.168.1.100:8081/api/";  // IP máy dev

// Hoặc dùng domain name (nếu có)
String baseUrl = "http://api.weconnect.local:8081/api/";
```

## Kết Luận

Mục 3.1.5 này đã giới thiệu chi tiết cách tích hợp Android app với Spring Boot backend thông qua REST API, bao gồm:

✅ JWT authentication flow
✅ Retrofit + OkHttp configuration
✅ Error handling & retry logic
✅ Offline queue mechanism
✅ Background polling services
✅ API response format standardization
✅ CORS & security configuration
✅ Testing strategies
✅ Troubleshooting guide

Với các kiến thức này, team phát triển có thể xây dựng hệ thống tích hợp ổn định, an toàn và hiệu quả. 🚀
