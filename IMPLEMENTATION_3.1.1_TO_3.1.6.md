# 3.1. Triển Khai Hệ Thống WeConnect

## 3.1.1 Cài Đặt Cơ Sở Dữ Liệu MySQL

### Giới thiệu

Cơ sở dữ liệu WeConnect được xây dựng trên MySQL với 11 bảng chính, lưu trữ toàn bộ dữ liệu về người dùng, bài viết, đánh giá, báo cáo, tin nhắn, và thông báo. Hệ thống sử dụng mô hình quan hệ tiêu chuẩn với các khóa ngoài để duy trì tính toàn vẹn dữ liệu.

### Cấu trúc Cơ Sở Dữ Liệu

#### 1. Bảng Users (Người Dùng)
```sql
CREATE TABLE users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  email VARCHAR(255) UNIQUE NOT NULL,
  password VARCHAR(255) NOT NULL,
  fullName VARCHAR(255),
  birthDay DATE,
  gender VARCHAR(20),
  avatarUrl TEXT,
  bio TEXT,
  interestTags JSON,
  averageRating DECIMAL(3,2) DEFAULT 0,
  reputationScore INT DEFAULT 0,
  role INT DEFAULT 0,
  isBlocked BOOLEAN DEFAULT FALSE,
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 2. Bảng Posts (Bài Viết & Hoạt Động)
```sql
CREATE TABLE posts (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  author_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  interestTag VARCHAR(255),
  location VARCHAR(255),
  maxMembers INT DEFAULT 10,
  status VARCHAR(50) DEFAULT 'ACTIVE',
  startTime DATETIME,
  endTime DATETIME,
  archived BOOLEAN DEFAULT FALSE,
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (author_id) REFERENCES users(id) ON DELETE CASCADE
);
```

#### 3. Bảng PostMembers (Thành Viên Bài Viết)
```sql
CREATE TABLE post_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  status VARCHAR(50) DEFAULT 'JOINED',
  FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  UNIQUE KEY unique_post_user (post_id, user_id)
);
```

#### 4. Bảng UserReviews (Đánh Giá Người Dùng)
```sql
CREATE TABLE user_reviews (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reviewer_id BIGINT NOT NULL,
  reviewed_user_id BIGINT NOT NULL,
  activityName VARCHAR(255),
  reputationLabel VARCHAR(100),
  comment TEXT,
  rating DECIMAL(3,2),
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (reviewer_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (reviewed_user_id) REFERENCES users(id) ON DELETE CASCADE
);
```

#### 5. Bảng Reports (Báo Cáo Vi Phạm)
```sql
CREATE TABLE reports (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  reporter_id BIGINT NOT NULL,
  target_type VARCHAR(50),
  target_id BIGINT,
  reason VARCHAR(255),
  description TEXT,
  status VARCHAR(50) DEFAULT 'PENDING',
  reviewed_by BIGINT,
  admin_action TEXT,
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  reviewedAt TIMESTAMP,
  FOREIGN KEY (reporter_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (reviewed_by) REFERENCES users(id)
);
```

#### 6. Bảng Friendships (Kết Bạn)
```sql
CREATE TABLE friendships (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  sender_id BIGINT NOT NULL,
  receiver_id BIGINT NOT NULL,
  status VARCHAR(50) DEFAULT 'PENDING',
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (receiver_id) REFERENCES users(id) ON DELETE CASCADE
);
```

#### 7. Bảng BlockedUsers (Chặn Người Dùng)
```sql
CREATE TABLE blocked_users (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  blocker_id BIGINT NOT NULL,
  blocked_id BIGINT NOT NULL,
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (blocker_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (blocked_id) REFERENCES users(id) ON DELETE CASCADE
);
```

#### 8. Bảng ChatRooms (Phòng Chat)
```sql
CREATE TABLE chat_rooms (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  post_id BIGINT,
  owner_id BIGINT NOT NULL,
  title VARCHAR(255),
  type VARCHAR(50),
  isActive BOOLEAN DEFAULT TRUE,
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (post_id) REFERENCES posts(id) ON DELETE SET NULL,
  FOREIGN KEY (owner_id) REFERENCES users(id) ON DELETE CASCADE
);
```

#### 9. Bảng ChatMessages (Tin Nhắn)
```sql
CREATE TABLE chat_messages (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  sender_id BIGINT NOT NULL,
  content TEXT NOT NULL,
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE,
  FOREIGN KEY (sender_id) REFERENCES users(id) ON DELETE CASCADE
);
```

#### 10. Bảng ChatRoomMembers (Thành Viên Phòng Chat)
```sql
CREATE TABLE chat_room_members (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  room_id BIGINT NOT NULL,
  user_id BIGINT NOT NULL,
  role VARCHAR(50) DEFAULT 'MEMBER',
  FOREIGN KEY (room_id) REFERENCES chat_rooms(id) ON DELETE CASCADE,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  UNIQUE KEY unique_room_user (room_id, user_id)
);
```

#### 11. Bảng Notifications (Thông Báo)
```sql
CREATE TABLE notifications (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  user_id BIGINT NOT NULL,
  related_post_id BIGINT,
  related_user_id BIGINT,
  type VARCHAR(100),
  message TEXT,
  relatedUsername VARCHAR(255),
  isRead BOOLEAN DEFAULT FALSE,
  isActioned BOOLEAN DEFAULT FALSE,
  createdAt TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
  FOREIGN KEY (related_post_id) REFERENCES posts(id),
  FOREIGN KEY (related_user_id) REFERENCES users(id)
);
```

### Cơ Chế Khóa Ngoài & Quan Hệ

**Mô Hình Dữ Liệu Trung Tâm (User-Centric):**
- Tất cả hoạt động (posts, friendships, messages) đều có liên kết tới bảng `users`
- Sử dụng cascade delete để đảm bảo tính toàn vẹn dữ liệu
- Các bảng trung gian (post_members, chat_room_members) kết nối N-to-N relationships

**Mối Quan Hệ Chính:**
- `Users` 1-to-Many `Posts` (qua author_id)
- `Users` 1-to-Many `PostMembers` (qua user_id)
- `Users` 1-to-Many `UserReviews` (qua reviewer_id và reviewed_user_id)
- `Users` 1-to-Many `Reports` (qua reporter_id)
- `Users` 1-to-Many `Friendships` (qua sender_id, receiver_id)
- `Users` 1-to-Many `ChatRooms` (qua owner_id)
- `Users` 1-to-Many `ChatMessages` (qua sender_id)
- `ChatRooms` 1-to-Many `ChatMessages` (qua room_id)

### Chỉ Số (Indexes)

```sql
-- Tối ưu hóa truy vấn
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_posts_author ON posts(author_id);
CREATE INDEX idx_posts_created ON posts(createdAt);
CREATE INDEX idx_post_members_post ON post_members(post_id);
CREATE INDEX idx_post_members_user ON post_members(user_id);
CREATE INDEX idx_reviews_reviewer ON user_reviews(reviewer_id);
CREATE INDEX idx_reviews_reviewed ON user_reviews(reviewed_user_id);
CREATE INDEX idx_reports_status ON reports(status);
CREATE INDEX idx_friendships_sender ON friendships(sender_id);
CREATE INDEX idx_friendships_receiver ON friendships(receiver_id);
CREATE INDEX idx_chat_messages_room ON chat_messages(room_id);
CREATE INDEX idx_chat_members_room ON chat_room_members(room_id);
CREATE INDEX idx_notifications_user ON notifications(user_id);
```

### Quy Trình Cài Đặt

**Bước 1:** Tạo Database
```bash
mysql -u root -p
CREATE DATABASE weconnect_db DEFAULT CHARACTER SET utf8mb4 DEFAULT COLLATE utf8mb4_unicode_ci;
USE weconnect_db;
```

**Bước 2:** Chạy Script Khởi Tạo Bảng (tự động qua Spring JPA)
```properties
spring.jpa.hibernate.ddl-auto=update
```

**Bước 3:** Tạo Tài Khoản Admin
```sql
INSERT INTO users (email, password, fullName, role, createdAt) 
VALUES ('admin@weconnect.com', 'hashed_password', 'Administrator', 1, NOW());
```

---

## 3.1.2 Phát Triển Ứng Dụng Android

### Giới Thiệu

Ứng dụng Android WeConnect được xây dựng bằng Java trên Android Studio, cung cấp giao diện di động cho người dùng tham gia các hoạt động xã hội, kết bạn, chat, và quản lý hồ sơ cá nhân.

### Công Nghệ & Thư Viện

**Build Tool & SDK:**
- Gradle (Kotlin DSL)
- Min SDK: 24, Target SDK: 34
- Java 8+ Features

**Thư Viện Chính:**
```gradle
dependencies {
  // HTTP & Networking
  implementation 'com.squareup.okhttp3:okhttp:4.11.0'
  implementation 'com.squareup.okhttp3:logging-interceptor:4.11.0'
  
  // JSON Parsing
  implementation 'com.google.code.gson:gson:2.10.1'
  
  // Image Loading
  implementation 'com.github.bumptech.glide:glide:4.15.1'
  
  // UI Components
  implementation 'androidx.cardview:cardview:1.0.0'
  implementation 'androidx.recyclerview:recyclerview:1.3.0'
  
  // JWT Decoding
  implementation 'com.auth0:java-jwt:4.4.0'
  
  // Local Storage
  implementation 'androidx.security:security-crypto:1.1.0-alpha06'
  
  // Lifecycle Components
  implementation 'androidx.lifecycle:lifecycle-viewmodel:2.6.1'
  implementation 'androidx.lifecycle:lifecycle-runtime:2.6.1'
}
```

### Cấu Trúc Dự Án

```
app/src/main/
├── java/com/example/weconnect/
│   ├── activities/
│   │   ├── LoginActivity.java
│   │   ├── RegisterActivity.java
│   │   ├── MainActivity.java (Tab-based)
│   │   ├── CreatePostActivity.java
│   │   ├── PostDetailActivity.java
│   │   ├── UserProfileActivity.java
│   │   ├── ChatListActivity.java
│   │   ├── ConversationActivity.java
│   │   ├── SearchActivity.java
│   │   └── SettingsActivity.java
│   ├── fragments/
│   │   ├── HomeFragment.java
│   │   ├── ExploreFragment.java
│   │   ├── ProfileFragment.java
│   │   └── NotificationsFragment.java
│   ├── adapters/
│   │   ├── PostAdapter.java
│   │   ├── ChatAdapter.java
│   │   ├── UserReviewAdapter.java
│   │   └── NotificationAdapter.java
│   ├── models/
│   │   ├── User.java
│   │   ├── Post.java
│   │   ├── Message.java
│   │   ├── ChatRoom.java
│   │   └── Notification.java
│   ├── services/
│   │   ├── ApiService.java (HTTP Client)
│   │   ├── AuthService.java (Login/Register)
│   │   ├── UserService.java
│   │   ├── PostService.java
│   │   ├── ChatService.java
│   │   └── NotificationService.java
│   ├── util/
│   │   ├── Constants.java
│   │   ├── SharedPreferenceManager.java
│   │   ├── TokenManager.java
│   │   └── DateUtils.java
│   └── App.java (Application class)
└── AndroidManifest.xml
```

### Các Activity Chính

#### 1. LoginActivity
Chức năng:
- Đăng nhập qua email/password
- Lưu JWT token vào SharedPreferences
- Kiểm tra xác thực trước khi truy cập
- Điều hướng tới MainActivity nếu đã login

```java
public class LoginActivity extends AppCompatActivity {
    private EditText emailInput, passwordInput;
    private Button loginButton;
    private AuthService authService;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        
        authService = new AuthService(this);
        loginButton.setOnClickListener(v -> performLogin());
    }
    
    private void performLogin() {
        String email = emailInput.getText().toString();
        String password = passwordInput.getText().toString();
        
        authService.login(email, password, new ApiCallback<LoginResponse>() {
            @Override
            public void onSuccess(LoginResponse response) {
                TokenManager.saveToken(response.getToken());
                startActivity(new Intent(LoginActivity.this, MainActivity.class));
                finish();
            }
            
            @Override
            public void onError(String error) {
                Toast.makeText(LoginActivity.this, error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
```

#### 2. MainActivity
- Activity chính với TabLayout + ViewPager2
- Chứa 4 Fragments chính (Home, Explore, Profile, Notifications)
- Bottom Navigation để chuyển đổi tabs

#### 3. CreatePostActivity
- Form tạo bài viết mới
- Chọn loại hoạt động (sở thích)
- Thiết lập thời gian, địa điểm
- Upload ảnh đại diện (nếu có)

#### 4. PostDetailActivity
- Xem chi tiết một bài viết
- Danh sách thành viên tham gia
- Nút tham gia/hủy tham gia
- Chat trong hoạt động

#### 5. ConversationActivity
- Chat real-time
- Polling messages mỗi 2 giây
- Hiển thị tin nhắn theo thời gian thực
- Ghi lại tin nhắn chưa gửi nếu offline

### Models (Data Classes)

```java
public class User {
    private Long id;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String bio;
    private List<String> interestTags;
    private Double averageRating;
    private Integer reputationScore;
    private Boolean isBlocked;
    // Getters & Setters
}

public class Post {
    private Long id;
    private User author;
    private String content;
    private String interestTag;
    private String location;
    private Integer maxMembers;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean archived;
    private List<User> members;
    private LocalDateTime createdAt;
    // Getters & Setters
}

public class ChatMessage {
    private Long id;
    private ChatRoom room;
    private User sender;
    private String content;
    private LocalDateTime createdAt;
    // Getters & Setters
}
```

### Permissions (AndroidManifest.xml)

```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.CAMERA" />
```

---

## 3.1.3 Xây Dựng Backend với Spring Boot

### Giới Thiệu

Backend WeConnect được xây dựng trên Spring Boot 3.x, cung cấp REST API đầy đủ cho cả ứng dụng Android và web admin. Hệ thống sử dụng Spring Security + JWT để xác thực và phân quyền.

### Kiến Trúc Backend

```
src/main/java/com/weconnect/backend/
├── entity/                    # JPA Entities
│   ├── User.java
│   ├── Post.java
│   ├── PostMember.java
│   ├── UserReview.java
│   ├── Report.java
│   ├── Friendship.java
│   ├── BlockedUser.java
│   ├── ChatRoom.java
│   ├── ChatMessage.java
│   ├── ChatRoomMember.java
│   └── Notification.java
├── repository/                # Spring Data JPA
│   ├── UserRepository.java
│   ├── PostRepository.java
│   ├── ChatMessageRepository.java
│   └── ... (10+ repositories)
├── service/                   # Business Logic
│   ├── AuthService.java
│   ├── UserService.java
│   ├── PostService.java
│   ├── ChatService.java
│   ├── ReportService.java
│   └── NotificationService.java
├── controller/                # REST Endpoints
│   ├── AuthController.java
│   ├── UserController.java
│   ├── PostController.java
│   ├── ChatController.java
│   ├── AdminUserController.java
│   ├── AdminPostController.java
│   ├── AdminReportController.java
│   └── AdminDashboardController.java
├── config/                    # Configuration
│   ├── SecurityConfig.java
│   ├── CorsConfig.java
│   ├── JwtConfig.java
│   └── WebConfig.java
├── dto/                       # Data Transfer Objects
│   ├── AuthRequest.java
│   ├── AuthResponse.java
│   ├── PostRequest.java
│   ├── PostResponse.java
│   └── ... (15+ DTOs)
├── security/                  # JWT & Security
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   ├── CustomUserDetails.java
│   └── CustomUserDetailsService.java
└── util/                      # Utilities
    ├── ApiResponse.java
    └── Constants.java
```

### Cấu Hình Spring Boot

**application.properties**
```properties
# Server
server.address=0.0.0.0
server.port=8081

# Database
spring.datasource.url=jdbc:mysql://localhost:3306/weconnect_db?createDatabaseIfNotExist=true
spring.datasource.username=root
spring.datasource.password=121930
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.MySQL8Dialect

# JWT
jwt.secret=WeConnectSecretKey2026SuperSecureRandomStringForJWTTokenGeneration123456
jwt.expiration=86400000

# Logging
logging.level.root=INFO
logging.level.com.weconnect.backend=DEBUG
```

### JWT Authentication Flow

```
┌─────────────────────────────────────────────────┐
│  1. Client gửi POST /api/auth/login             │
│     Body: {email, password}                     │
└─────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────┐
│  2. AuthController/AuthService xác minh         │
│     - Kiểm tra email có tồn tại                │
│     - Kiểm tra password (BCrypt)               │
└─────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────┐
│  3. JwtTokenProvider tạo JWT token             │
│     - Claims: userId, email, role              │
│     - Expiration: 24 hours                      │
└─────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────┐
│  4. Trả về AuthResponse                        │
│     {userId, email, token, role}               │
└─────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────┐
│  5. Client lưu token (localStorage/SharedPref) │
└─────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────┐
│  6. Mỗi request giao tiếp API                  │
│     Header: Authorization: Bearer {token}      │
└─────────────────────────────────────────────────┘
                        ↓
┌─────────────────────────────────────────────────┐
│  7. JwtAuthenticationFilter lọc request        │
│     - Trích xuất token từ header                │
│     - Verify signature & expiration             │
│     - Đặt SecurityContext với user info        │
└─────────────────────────────────────────────────┘
```

### REST Endpoints Chính

#### Authentication
| Method | Endpoint | Mô Tả |
|--------|----------|-------|
| POST | `/api/auth/register` | Đăng ký tài khoản |
| POST | `/api/auth/login` | Đăng nhập |
| POST | `/api/auth/logout` | Đăng xuất |

#### Users
| Method | Endpoint | Mô Tả |
|--------|----------|-------|
| GET | `/api/users/me` | Lấy profile hiện tại |
| GET | `/api/users/{id}` | Lấy profile user khác |
| PUT | `/api/users/me` | Cập nhật profile |
| PUT | `/api/users/me/interests` | Cập nhật sở thích |
| PUT | `/api/users/{id}/password` | Đổi mật khẩu |

#### Posts
| Method | Endpoint | Mô Tả |
|--------|----------|-------|
| GET | `/api/posts` | Danh sách bài viết active |
| GET | `/api/posts/{id}` | Chi tiết bài viết |
| POST | `/api/posts` | Tạo bài viết mới |
| PUT | `/api/posts/{id}` | Cập nhật bài viết |
| DELETE | `/api/posts/{id}` | Xóa bài viết |
| POST | `/api/posts/{id}/join` | Tham gia hoạt động |
| DELETE | `/api/posts/{id}/leave` | Rời hoạt động |

#### Chat
| Method | Endpoint | Mô Tả |
|--------|----------|-------|
| GET | `/api/chats/rooms` | Danh sách phòng chat |
| GET | `/api/chats/rooms/{roomId}` | Chi tiết phòng chat |
| GET | `/api/chats/messages/{roomId}` | Danh sách tin nhắn |
| POST | `/api/chats/messages` | Gửi tin nhắn |

#### Admin
| Method | Endpoint | Mô Tả |
|--------|----------|-------|
| GET | `/api/admin/users` | Danh sách tất cả users |
| GET | `/api/admin/users/{id}` | Chi tiết user (admin) |
| PUT | `/api/admin/users/{id}/block` | Block user |
| PUT | `/api/admin/users/{id}/unblock` | Unblock user |
| DELETE | `/api/admin/users/{id}` | Xóa user |
| GET | `/api/admin/posts` | Danh sách posts |
| GET | `/api/admin/reports` | Báo cáo vi phạm |
| GET | `/api/admin/dashboard/stats` | Thống kê dashboard |

### Service Layer Example

```java
@Service
@Transactional
public class PostService {
    
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final PostMemberRepository postMemberRepository;
    private final NotificationService notificationService;
    
    public PostResponse createPost(Long userId, PostRequest request) {
        User author = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
            
        Post post = Post.builder()
            .author(author)
            .content(request.getContent())
            .interestTag(request.getInterestTag())
            .location(request.getLocation())
            .startTime(request.getStartTime())
            .endTime(request.getEndTime())
            .maxMembers(request.getMaxMembers())
            .status("ACTIVE")
            .createdAt(LocalDateTime.now())
            .build();
            
        Post saved = postRepository.save(post);
        
        // Thêm tác giả vào thành viên
        PostMember member = new PostMember();
        member.setPost(saved);
        member.setUser(author);
        member.setStatus("JOINED");
        postMemberRepository.save(member);
        
        return toResponse(saved);
    }
    
    public String joinPost(Long postId, Long userId) {
        Post post = postRepository.findById(postId)
            .orElseThrow(() -> new RuntimeException("Post not found"));
            
        if (postMemberRepository.existsByPostAndUser(post, userId)) {
            return "Bạn đã tham gia hoạt động này";
        }
        
        if (post.getMembers().size() >= post.getMaxMembers()) {
            return "Hoạt động đã đủ thành viên";
        }
        
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new RuntimeException("User not found"));
            
        PostMember member = new PostMember();
        member.setPost(post);
        member.setUser(user);
        member.setStatus("JOINED");
        postMemberRepository.save(member);
        
        // Gửi thông báo tới tác giả
        notificationService.sendNotification(post.getAuthor().getId(), 
            "người dùng tham gia hoạt động");
        
        return "Tham gia hoạt động thành công";
    }
}
```

---

## 3.1.4 Thiết Kế & Xây Dựng Giao Diện Người Dùng (Web Admin)

### Giới Thiệu

Web admin dashboard được xây dựng trên React 18 + TypeScript + Vite, cung cấp giao diện quản trị toàn diện cho hệ thống WeConnect.

### Công Nghệ & Stack

**Frontend Technologies:**
- React 18: UI framework
- TypeScript: Type-safe development
- Vite: Fast build tool
- Ant Design: Professional UI components
- Zustand: State management
- Axios: HTTP client
- React Router: Navigation

**Development Tools:**
- ESLint: Code linting
- Prettier: Code formatting
- HMR: Hot module replacement

### Cấu Trúc Dự Án

```
src/
├── main.tsx                   # Entry point
├── App.tsx                    # Root component
├── App.css
├── components/
│   ├── AppSidebar.tsx        # Sidebar navigation
│   ├── AppTopbar.tsx         # Top header
│   ├── MainLayout.tsx        # Main layout wrapper
│   ├── ConfirmActionModal.tsx # Confirmation dialog
│   ├── StatusBadge.tsx       # Status indicator
│   ├── LoadingState.tsx      # Loading skeleton
│   ├── DashboardStatCard.tsx # Stat card component
│   └── EmptyState.tsx        # Empty state view
├── pages/
│   ├── LoginPage.tsx         # Admin login
│   ├── DashboardPage.tsx     # Statistics dashboard
│   ├── UsersPage.tsx         # User management
│   ├── PostsPage.tsx         # Post management
│   ├── ReviewsPage.tsx       # Review management
│   └── ReportsPage.tsx       # Report/Moderation
├── services/
│   ├── apiClient.ts          # Axios setup
│   ├── authService.ts        # Auth logic
│   ├── dashboardService.ts   # Dashboard APIs
│   ├── userAdminService.ts   # User APIs
│   ├── postAdminService.ts   # Post APIs
│   ├── reviewAdminService.ts # Review APIs
│   └── reportAdminService.ts # Report APIs
├── stores/
│   └── authStore.ts          # Zustand auth state
├── types/
│   └── index.ts              # TypeScript types
├── routes/
│   └── index.tsx             # Route definitions
└── vite.config.ts            # Vite configuration
```

### Key Components

#### 1. LoginPage Component

```tsx
export default function LoginPage() {
  const [loading, setLoading] = useState(false);
  const login = useAuthStore(state => state.login);
  const navigate = useNavigate();
  
  const onFinish = async (values: any) => {
    setLoading(true);
    try {
      const response = await authService.login(values.email, values.password);
      login(response.user, response.token);
      navigate('/dashboard');
    } catch (error) {
      message.error('Đăng nhập thất bại');
    } finally {
      setLoading(false);
    }
  };
  
  return (
    <div className="login-container">
      <Card title="Đăng Nhập Quản Trị">
        <Form onFinish={onFinish}>
          <Form.Item name="email" rules={[{required: true}]}>
            <Input placeholder="Email" />
          </Form.Item>
          <Form.Item name="password" rules={[{required: true}]}>
            <Input.Password placeholder="Mật khẩu" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" loading={loading}>
              Đăng Nhập
            </Button>
          </Form.Item>
        </Form>
      </Card>
    </div>
  );
}
```

#### 2. DashboardPage Component

```tsx
export default function DashboardPage() {
  const [stats, setStats] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  
  useEffect(() => {
    const fetchStats = async () => {
      try {
        const data = await dashboardService.getStats();
        setStats(data.result);
      } finally {
        setLoading(false);
      }
    };
    
    fetchStats();
  }, []);
  
  if (loading) return <LoadingState />;
  
  return (
    <div className="dashboard-page">
      <Row gutter={16}>
        <Col span={6}>
          <DashboardStatCard 
            title="Tổng Users" 
            value={stats?.totalUsers} 
            icon={<UserOutlined />}
          />
        </Col>
        <Col span={6}>
          <DashboardStatCard 
            title="Tổng Posts" 
            value={stats?.totalPosts}
            icon={<FileTextOutlined />}
          />
        </Col>
        <Col span={6}>
          <DashboardStatCard 
            title="Đánh Giá" 
            value={stats?.totalReviews}
            icon={<StarOutlined />}
          />
        </Col>
        <Col span={6}>
          <DashboardStatCard 
            title="Người Bị Block" 
            value={stats?.blockedUsers}
            icon={<LockOutlined />}
          />
        </Col>
      </Row>
    </div>
  );
}
```

#### 3. UsersPage Component

```tsx
export default function UsersPage() {
  const [users, setUsers] = useState<User[]>([]);
  const [loading, setLoading] = useState(true);
  
  useEffect(() => {
    loadUsers();
  }, []);
  
  const loadUsers = async () => {
    setLoading(true);
    try {
      const data = await userAdminService.getAllUsers();
      setUsers(data.result);
    } finally {
      setLoading(false);
    }
  };
  
  const columns = [
    { title: 'ID', dataIndex: 'id', key: 'id' },
    { title: 'Email', dataIndex: 'email', key: 'email' },
    { title: 'Tên', dataIndex: 'fullName', key: 'fullName' },
    { title: 'Danh Tiếng', dataIndex: 'reputationScore', key: 'reputation' },
    {
      title: 'Hành Động',
      key: 'action',
      render: (_, record) => (
        <Space>
          <Button type="link" onClick={() => handleViewUser(record.id)}>
            Xem
          </Button>
          <Button type="link" danger onClick={() => handleBlock(record.id)}>
            Block
          </Button>
        </Space>
      )
    }
  ];
  
  return (
    <div className="users-page">
      <Table 
        columns={columns} 
        dataSource={users}
        loading={loading}
        rowKey="id"
      />
    </div>
  );
}
```

### State Management (Zustand)

```typescript
interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  login: (user: User, token: string) => void;
  logout: () => void;
}

const useAuthStore = create<AuthState>((set) => ({
  user: JSON.parse(localStorage.getItem('user') || 'null'),
  token: localStorage.getItem('token'),
  isAuthenticated: !!localStorage.getItem('token'),
  
  login: (user, token) => {
    localStorage.setItem('user', JSON.stringify(user));
    localStorage.setItem('token', token);
    set({ user, token, isAuthenticated: true });
  },
  
  logout: () => {
    localStorage.removeItem('user');
    localStorage.removeItem('token');
    set({ user: null, token: null, isAuthenticated: false });
  }
}));

export default useAuthStore;
```

### API Integration

```typescript
// apiClient.ts
import axios from 'axios';
import useAuthStore from '../stores/authStore';

const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL || '/api'
});

// Request interceptor
apiClient.interceptors.request.use((config) => {
  const token = useAuthStore.getState().token;
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// Response interceptor
apiClient.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      useAuthStore.getState().logout();
      window.location.href = '/login';
    }
    return Promise.reject(error);
  }
);

export default apiClient;
```

---

## 3.1.5 Tích Hợp Ứng Dụng Android với Backend

### Quy Trình Tích Hợp

#### 1. Setup HTTP Client

**ApiService.java**
```java
public class ApiService {
    private static final String BASE_URL = "http://10.0.2.2:8081/api/";
    private Retrofit retrofit;
    private OkHttpClient httpClient;
    
    public ApiService(Context context) {
        TokenManager tokenManager = new TokenManager(context);
        
        // Setup interceptor để thêm token vào header
        httpClient = new OkHttpClient.Builder()
            .addInterceptor(chain -> {
                Request original = chain.request();
                String token = tokenManager.getToken();
                
                Request.Builder requestBuilder = original.newBuilder();
                if (token != null) {
                    requestBuilder.header("Authorization", "Bearer " + token);
                }
                
                Request request = requestBuilder.build();
                return chain.proceed(request);
            })
            .addInterceptor(new HttpLoggingInterceptor().setLevel(
                HttpLoggingInterceptor.Level.BODY))
            .build();
        
        retrofit = new Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(httpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build();
    }
    
    public <T> T createService(Class<T> serviceClass) {
        return retrofit.create(serviceClass);
    }
}
```

#### 2. Define API Interface

```java
public interface WeConnectApi {
    // Authentication
    @POST("auth/login")
    Call<ApiResponse<LoginResponse>> login(@Body AuthRequest request);
    
    @POST("auth/register")
    Call<ApiResponse<Void>> register(@Body RegisterRequest request);
    
    // Users
    @GET("users/me")
    Call<ApiResponse<User>> getCurrentUser();
    
    @PUT("users/me")
    Call<ApiResponse<User>> updateProfile(@Body UpdateProfileRequest request);
    
    // Posts
    @GET("posts")
    Call<ApiResponse<List<Post>>> getActivePosts();
    
    @POST("posts")
    Call<ApiResponse<Post>> createPost(@Body PostRequest request);
    
    @GET("posts/{id}")
    Call<ApiResponse<Post>> getPostDetail(@Path("id") Long postId);
    
    // Chat
    @GET("chats/rooms")
    Call<ApiResponse<List<ChatRoom>>> getChatRooms();
    
    @GET("chats/messages/{roomId}")
    Call<ApiResponse<List<ChatMessage>>> getMessages(
        @Path("roomId") Long roomId);
    
    @POST("chats/messages")
    Call<ApiResponse<ChatMessage>> sendMessage(@Body MessageRequest request);
}
```

#### 3. Service Layer

**AuthService.java**
```java
public class AuthService {
    private final WeConnectApi api;
    private final TokenManager tokenManager;
    
    public AuthService(Context context) {
        ApiService apiService = new ApiService(context);
        this.api = apiService.createService(WeConnectApi.class);
        this.tokenManager = new TokenManager(context);
    }
    
    public void login(String email, String password, 
                      ApiCallback<LoginResponse> callback) {
        AuthRequest request = new AuthRequest(email, password);
        
        api.login(request).enqueue(new Callback<ApiResponse<LoginResponse>>() {
            @Override
            public void onResponse(Call<ApiResponse<LoginResponse>> call,
                                 Response<ApiResponse<LoginResponse>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    ApiResponse<LoginResponse> apiResponse = response.body();
                    if (apiResponse.getCode() == 1000) {
                        LoginResponse loginResponse = apiResponse.getResult();
                        tokenManager.saveToken(loginResponse.getToken());
                        tokenManager.saveUserId(loginResponse.getUserId());
                        callback.onSuccess(loginResponse);
                    } else {
                        callback.onError(apiResponse.getMessage());
                    }
                }
            }
            
            @Override
            public void onFailure(Call<ApiResponse<LoginResponse>> call, 
                                Throwable t) {
                callback.onError(t.getMessage());
            }
        });
    }
}
```

#### 4. Data Models

```java
public class User {
    private Long id;
    private String email;
    private String fullName;
    private String avatarUrl;
    private String bio;
    private List<String> interestTags;
    private Double averageRating;
    private Integer reputationScore;
    private Boolean isBlocked;
    // Constructors, Getters, Setters
}

public class Post {
    private Long id;
    private User author;
    private String content;
    private String interestTag;
    private String location;
    private Integer maxMembers;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private List<User> members;
    // Constructors, Getters, Setters
}

public class Notification {
    private Long id;
    private String type;
    private String message;
    private String relatedUsername;
    private Boolean isRead;
    private LocalDateTime createdAt;
    // Constructors, Getters, Setters
}
```

#### 5. Activity Integration

```java
public class MainActivity extends AppCompatActivity {
    private WeConnectApi api;
    private UserService userService;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        ApiService apiService = new ApiService(this);
        api = apiService.createService(WeConnectApi.class);
        
        loadCurrentUser();
    }
    
    private void loadCurrentUser() {
        api.getCurrentUser().enqueue(new Callback<ApiResponse<User>>() {
            @Override
            public void onResponse(Call<ApiResponse<User>> call,
                                 Response<ApiResponse<User>> response) {
                if (response.isSuccessful()) {
                    User user = response.body().getResult();
                    // Update UI with user data
                    displayUserProfile(user);
                }
            }
            
            @Override
            public void onFailure(Call<ApiResponse<User>> call, Throwable t) {
                Toast.makeText(MainActivity.this, 
                    "Lỗi tải dữ liệu: " + t.getMessage(),
                    Toast.LENGTH_SHORT).show();
            }
        });
    }
    
    private void displayUserProfile(User user) {
        TextView nameView = findViewById(R.id.user_name);
        ImageView avatarView = findViewById(R.id.user_avatar);
        
        nameView.setText(user.getFullName());
        Glide.with(this).load(user.getAvatarUrl()).into(avatarView);
    }
}
```

#### 6. Error Handling & Retry Logic

```java
public class ApiCallback<T> {
    private static final int MAX_RETRIES = 3;
    private int retryCount = 0;
    
    public abstract void onSuccess(T result);
    public abstract void onError(String error);
    
    public Callback<T> getRetryableCallback() {
        return new Callback<T>() {
            @Override
            public void onResponse(Call<T> call, Response<T> response) {
                if (response.isSuccessful()) {
                    onSuccess(response.body());
                } else if (response.code() == 401) {
                    // Unauthorized - need to login again
                    onError("Phiên đăng nhập hết hạn");
                    logout();
                } else {
                    onError("Lỗi: " + response.code());
                }
            }
            
            @Override
            public void onFailure(Call<T> call, Throwable t) {
                if (retryCount < MAX_RETRIES) {
                    retryCount++;
                    call.clone().enqueue(this);
                } else {
                    onError("Kết nối thất bại: " + t.getMessage());
                }
            }
        };
    }
}
```

---

## 3.1.6 Triển Khai Chức Năng Chat

### Giới Thiệu

Chức năng chat được triển khai bằng cơ chế **polling** (request HTTP định kỳ) thay vì WebSocket, giúp đơn giản hóa backend và tương thích với các hạn chế mạng.

### Kiến Trúc Chat

```
┌──────────────────────┐
│   Chat Messages      │
└──────────────────────┘
           ↓
┌──────────────────────┐
│   Chat Rooms         │
└──────────────────────┘
           ↓
┌──────────────────────┐
│ Chat Room Members    │
└──────────────────────┘
           ↓
┌──────────────────────┐
│   Users (Members)    │
└──────────────────────┘
```

### Database Schema

**ChatRoom.java**
```java
@Entity
@Table(name = "chat_rooms")
public class ChatRoom {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "post_id")
    private Post post;
    
    @ManyToOne
    @JoinColumn(name = "owner_id")
    private User owner;
    
    private String title;
    
    @Enumerated(EnumType.STRING)
    private ChatType type; // DIRECT, GROUP, FRIEND_GROUP, ACTIVITY
    
    private Boolean isActive = true;
    
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private List<ChatMessage> messages;
    
    @OneToMany(mappedBy = "room", cascade = CascadeType.ALL)
    private List<ChatRoomMember> members;
    
    private LocalDateTime createdAt = LocalDateTime.now();
}

public enum ChatType {
    DIRECT,        // 1-to-1 chat
    GROUP,         // Nhóm đa người
    FRIEND_GROUP,  // Chat bạn bè
    ACTIVITY       // Chat hoạt động
}
```

**ChatMessage.java**
```java
@Entity
@Table(name = "chat_messages")
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne
    @JoinColumn(name = "room_id")
    private ChatRoom room;
    
    @ManyToOne
    @JoinColumn(name = "sender_id")
    private User sender;
    
    @Column(columnDefinition = "TEXT")
    private String content;
    
    private LocalDateTime createdAt = LocalDateTime.now();
}
```

### REST API Endpoints

| Method | Endpoint | Mô Tả |
|--------|----------|-------|
| GET | `/api/chats/rooms` | Danh sách phòng chat |
| POST | `/api/chats/rooms` | Tạo phòng chat mới |
| GET | `/api/chats/rooms/{roomId}` | Chi tiết phòng chat |
| GET | `/api/chats/messages/{roomId}` | Danh sách tin nhắn (polling) |
| POST | `/api/chats/messages` | Gửi tin nhắn |
| DELETE | `/api/chats/messages/{messageId}` | Xóa tin nhắn |
| PUT | `/api/chats/rooms/{roomId}/members/{userId}` | Thêm thành viên |
| DELETE | `/api/chats/rooms/{roomId}/members/{userId}` | Xóa thành viên |

### Backend Implementation

**ChatController.java**
```java
@RestController
@RequestMapping("/api/chats")
public class ChatController {
    
    private final ChatService chatService;
    
    // Lấy danh sách phòng chat của user
    @GetMapping("/rooms")
    public ResponseEntity<?> getChatRooms(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        List<ChatRoom> rooms = chatService.getUserChatRooms(user.getId());
        return ResponseEntity.ok(ApiResponse.builder()
            .code(1000)
            .message("Success")
            .result(rooms)
            .build());
    }
    
    // Lấy tin nhắn trong phòng (cho polling)
    @GetMapping("/messages/{roomId}")
    public ResponseEntity<?> getMessages(
            @PathVariable Long roomId,
            @RequestParam(required = false) Long afterId) {
        List<ChatMessage> messages = afterId != null
            ? chatService.getMessagesAfter(roomId, afterId)
            : chatService.getMessages(roomId);
            
        return ResponseEntity.ok(ApiResponse.builder()
            .code(1000)
            .message("Success")
            .result(messages)
            .build());
    }
    
    // Gửi tin nhắn mới
    @PostMapping("/messages")
    public ResponseEntity<?> sendMessage(
            Authentication authentication,
            @RequestBody SendMessageRequest request) {
        User sender = (User) authentication.getPrincipal();
        
        ChatMessage message = chatService.sendMessage(
            sender.getId(),
            request.getRoomId(),
            request.getContent());
            
        return ResponseEntity.ok(ApiResponse.builder()
            .code(1000)
            .message("Message sent")
            .result(message)
            .build());
    }
}
```

**ChatService.java**
```java
@Service
@Transactional
public class ChatService {
    
    private final ChatRoomRepository chatRoomRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ChatRoomMemberRepository memberRepository;
    private final UserRepository userRepository;
    
    public List<ChatMessage> getMessagesAfter(Long roomId, Long afterId) {
        ChatMessage lastMessage = chatMessageRepository.findById(afterId)
            .orElse(null);
            
        if (lastMessage == null) {
            return getMessages(roomId);
        }
        
        return chatMessageRepository.findAllByRoomIdAndIdGreaterThan(
            roomId, afterId);
    }
    
    public ChatMessage sendMessage(Long senderId, Long roomId, String content) {
        User sender = userRepository.findById(senderId)
            .orElseThrow(() -> new RuntimeException("Sender not found"));
            
        ChatRoom room = chatRoomRepository.findById(roomId)
            .orElseThrow(() -> new RuntimeException("Room not found"));
            
        ChatMessage message = ChatMessage.builder()
            .room(room)
            .sender(sender)
            .content(content)
            .createdAt(LocalDateTime.now())
            .build();
            
        return chatMessageRepository.save(message);
    }
}
```

### Android Implementation - Polling Mechanism

**ConversationActivity.java**
```java
public class ConversationActivity extends AppCompatActivity {
    
    private RecyclerView messagesRecycler;
    private EditText messageInput;
    private Button sendButton;
    private ChatAdapter adapter;
    private WeConnectApi api;
    private Long roomId;
    private Long lastMessageId = 0L;
    
    private Handler pollingHandler = new Handler(Looper.getMainLooper());
    private Runnable pollingRunnable;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_conversation);
        
        roomId = getIntent().getLongExtra("roomId", -1);
        ApiService apiService = new ApiService(this);
        api = apiService.createService(WeConnectApi.class);
        
        initUI();
        startPolling();
    }
    
    private void initUI() {
        messagesRecycler = findViewById(R.id.messages_recycler);
        messageInput = findViewById(R.id.message_input);
        sendButton = findViewById(R.id.send_button);
        
        adapter = new ChatAdapter();
        messagesRecycler.setAdapter(adapter);
        
        sendButton.setOnClickListener(v -> sendMessage());
    }
    
    private void startPolling() {
        pollingRunnable = new Runnable() {
            @Override
            public void run() {
                fetchNewMessages();
                // Poll mỗi 2 giây
                pollingHandler.postDelayed(this, 2000);
            }
        };
        
        pollingHandler.post(pollingRunnable);
    }
    
    private void fetchNewMessages() {
        api.getMessages(roomId, lastMessageId)
            .enqueue(new Callback<ApiResponse<List<ChatMessage>>>() {
                @Override
                public void onResponse(Call<ApiResponse<List<ChatMessage>>> call,
                                     Response<ApiResponse<List<ChatMessage>>> response) {
                    if (response.isSuccessful()) {
                        List<ChatMessage> newMessages = 
                            response.body().getResult();
                            
                        if (!newMessages.isEmpty()) {
                            adapter.addMessages(newMessages);
                            lastMessageId = newMessages.get(newMessages.size() - 1)
                                .getId();
                            
                            // Scroll to bottom
                            messagesRecycler.scrollToPosition(
                                adapter.getItemCount() - 1);
                        }
                    }
                }
                
                @Override
                public void onFailure(Call<ApiResponse<List<ChatMessage>>> call,
                                    Throwable t) {
                    Log.e("Chat", "Failed to fetch messages", t);
                }
            });
    }
    
    private void sendMessage() {
        String text = messageInput.getText().toString().trim();
        if (text.isEmpty()) return;
        
        SendMessageRequest request = new SendMessageRequest(roomId, text);
        
        api.sendMessage(request)
            .enqueue(new Callback<ApiResponse<ChatMessage>>() {
                @Override
                public void onResponse(Call<ApiResponse<ChatMessage>> call,
                                     Response<ApiResponse<ChatMessage>> response) {
                    if (response.isSuccessful()) {
                        ChatMessage message = response.body().getResult();
                        adapter.addMessage(message);
                        messageInput.setText("");
                        lastMessageId = message.getId();
                    }
                }
                
                @Override
                public void onFailure(Call<ApiResponse<ChatMessage>> call,
                                    Throwable t) {
                    Toast.makeText(ConversationActivity.this,
                        "Gửi tin nhắn thất bại",
                        Toast.LENGTH_SHORT).show();
                }
            });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Dừng polling khi activity bị destroy
        pollingHandler.removeCallbacks(pollingRunnable);
    }
}
```

### ChatAdapter.java

```java
public class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.ViewHolder> {
    
    private List<ChatMessage> messages = new ArrayList<>();
    private Long currentUserId;
    
    public void setCurrentUserId(Long userId) {
        this.currentUserId = userId;
    }
    
    public void addMessage(ChatMessage message) {
        messages.add(message);
        notifyItemInserted(messages.size() - 1);
    }
    
    public void addMessages(List<ChatMessage> newMessages) {
        int oldSize = messages.size();
        messages.addAll(newMessages);
        notifyItemRangeInserted(oldSize, newMessages.size());
    }
    
    @Override
    public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        int layoutId = viewType == 0 
            ? R.layout.item_chat_message_left
            : R.layout.item_chat_message_right;
            
        View view = LayoutInflater.from(parent.getContext())
            .inflate(layoutId, parent, false);
            
        return new ViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(ViewHolder holder, int position) {
        ChatMessage message = messages.get(position);
        
        holder.messageText.setText(message.getContent());
        holder.timestamp.setText(formatTime(message.getCreatedAt()));
    }
    
    @Override
    public int getItemViewType(int position) {
        ChatMessage message = messages.get(position);
        return message.getSender().getId().equals(currentUserId) ? 1 : 0;
    }
    
    @Override
    public int getItemCount() {
        return messages.size();
    }
    
    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView messageText, timestamp;
        
        ViewHolder(View view) {
            super(view);
            messageText = view.findViewById(R.id.message_text);
            timestamp = view.findViewById(R.id.message_time);
        }
    }
    
    private String formatTime(LocalDateTime dateTime) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm");
        return dateTime.format(formatter);
    }
}
```

### Polling vs WebSocket

| Tính Năng | Polling | WebSocket |
|----------|---------|-----------|
| Độ Phức Tạp | Thấp | Cao |
| Tiêu Thụ Bandwidth | Cao (request/response dư) | Thấp (bidirectional) |
| Độ Trễ | 1-5 giây | Milliseconds |
| Cấu Hình Server | Đơn giản (REST API) | Phức tạp (WebSocket server) |
| Tương Thích | Tất cả thiết bị | Cần hỗ trợ |
| **Phù Hợp Cho Dự Án Này** | ✅ | ❌ |

**Lý do chọn Polling cho WeConnect:**
1. Backend sử dụng REST API đơn giản
2. Dễ debug và kiểm tra (curl/Postman)
3. Chat không yêu cầu real-time ngay lập tức (2-3 giây chấp nhận được)
4. Android & Web Admin dễ triển khai
5. Giảm yêu cầu hạ tầng phục vụ
