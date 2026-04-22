# 3.1.7. Xây dựng Web Admin Quản Trị Hệ Thống

## 3.1.7.1 Giới thiệu

Web admin quản trị hệ thống WeConnect được xây dựng để cung cấp một giao diện quản lý toàn diện cho các quản trị viên hệ thống. Dashboard này cho phép quản lý người dùng, bài viết, đánh giá, báo cáo vi phạm, và xem các thống kê hệ thống theo thời gian thực.

## 3.1.7.2 Công nghệ sử dụng

### Frontend
- **React 18**: Framework UI hiện đại
- **TypeScript**: Đảm bảo type safety toàn ứng dụng
- **Vite**: Build tool nhanh chóng
- **Ant Design**: Component library chuyên nghiệp
- **Axios**: HTTP client để giao tiếp REST API
- **Zustand**: State management nhẹ gọn
- **React Router**: Định tuyến và bảo vệ route

### Backend Integration
- **REST API**: Giao tiếp với Spring Boot backend
- **JWT Authentication**: Token-based security
- **Interceptors**: Tự động thêm token vào mỗi request

## 3.1.7.3 Kiến trúc hệ thống

### Tổng quan

```
┌─────────────────────────────────────────────────────┐
│          React Admin Dashboard (Frontend)           │
├─────────────────────────────────────────────────────┤
│                                                     │
│  ┌────────────────────────────────────────────┐   │
│  │  UI Layer (React Components + Ant Design)  │   │
│  │  - LoginPage                               │   │
│  │  - DashboardPage (Statistics)              │   │
│  │  - UsersPage (User Management)             │   │
│  │  - PostsPage (Post Management)             │   │
│  │  - ReviewsPage (Review Management)         │   │
│  │  - ReportsPage (Moderation & Reports)      │   │
│  └────────────────────────────────────────────┘   │
│                        ↓                            │
│  ┌────────────────────────────────────────────┐   │
│  │  Service Layer (API & Business Logic)      │   │
│  │  - authService (JWT, login/logout)         │   │
│  │  - userAdminService (user operations)      │   │
│  │  - postAdminService (post operations)      │   │
│  │  - reviewAdminService (reviews)            │   │
│  │  - reportAdminService (moderation)         │   │
│  │  - dashboardService (statistics)           │   │
│  └────────────────────────────────────────────┘   │
│                        ↓                            │
│  ┌────────────────────────────────────────────┐   │
│  │  Axios API Client                          │   │
│  │  - Request Interceptors (add JWT token)    │   │
│  │  - Response Interceptors (handle errors)   │   │
│  │  - Error handling & authentication check   │   │
│  └────────────────────────────────────────────┘   │
│                        ↓                            │
│  ┌────────────────────────────────────────────┐   │
│  │  State Management (Zustand)                │   │
│  │  - Auth State (user, token)                │   │
│  │  - Local Storage persistence               │   │
│  └────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────┘
                        ↓ HTTP/REST
┌─────────────────────────────────────────────────────┐
│        Spring Boot Backend API (Port 8081)          │
│                                                     │
│  POST   /api/auth/login                            │
│  GET    /api/admin/users                           │
│  GET    /api/admin/posts                           │
│  GET    /api/admin/reviews                         │
│  GET    /api/admin/reports                         │
│  POST   /api/admin/reports/{id}/resolve            │
│  GET    /api/admin/dashboard/stats                 │
└─────────────────────────────────────────────────────┘
```

## 3.1.7.4 Các tính năng chính

### 1. Xác thực và Bảo mật (Authentication & Security)

**JWT Authentication**
- Email + Password login qua REST API
- JWT token được lưu trữ trong localStorage
- Token tự động được gửi trong header "Authorization: Bearer {token}"
- Auto-logout khi token hết hạn (401 Unauthorized)
- Protected routes - chỉ admin role=1 mới có quyền truy cập

**Xử lý lỗi**
- Báo lỗi login rõ ràng (account không tồn tại, sai password, v.v.)
- Automatic redirect tới login page khi session hết hạn

### 2. Quản lý Người dùng (User Management)

**Chức năng**
- Xem danh sách tất cả người dùng với phân trang
- Lọc theo email, tên, trạng thái (active/blocked)
- Xem chi tiết hồ sơ người dùng
- Khóa/mở khóa tài khoản người dùng
- Xem thông tin uy tín (reputation score, average rating)

**Giao diện**
- Bảng danh sách responsive với sort & filter
- Modal xem chi tiết người dùng
- Nút hành động (View, Block, Unblock) trên mỗi hàng
- Phân trang linh hoạt

### 3. Quản lý Bài viết (Post Management)

**Chức năng**
- Xem danh sách bài viết/sự kiện
- Lọc theo tag quan tâm, trạng thái (active/archived)
- Xem chi tiết bài viết (content, location, members, v.v.)
- Lưu trữ (archive) bài viết
- Xóa bài viết do vi phạm

**Giao diện**
- Bảng bài viết với thông tin: tác giả, tag, location, ngày tạo
- Status badge (Active/Archived)
- Drawer xem chi tiết
- Confirm modal cho hành động xóa

### 4. Quản lý Đánh giá (Review Management)

**Chức năng**
- Xem tất cả đánh giá người dùng
- Lọc theo "reputation label" (Đáng tin cậy, Nhiệt tình, v.v.)
- Tìm kiếm theo tên người, comment, hoạt động
- Xem chi tiết đánh giá kèm phân số

**Giao diện**
- Bảng đánh giá với stars rating visual
- Color-coded reputation labels
- Drawer hiển thị full comment
- Real-time average rating calculation

### 5. Quản lý Báo cáo Vi phạm (Moderation & Reports)

**Chức năng**
- Xem danh sách báo cáo từ người dùng
- Lọc theo loại (USER/POST), trạng thái (PENDING/REVIEWED/RESOLVED)
- Xem lý do báo cáo và mô tả chi tiết
- Phê duyệt và thực hiện hành động:
  - WARN: Cảnh cáo người dùng
  - HIDE_POST: Ẩn bài viết
  - DELETE_POST: Xóa bài viết
  - BLOCK_USER: Khóa tài khoản người dùng
  - DELETE_USER: Xóa người dùng
  - NO_VIOLATION: Đánh dấu không vi phạm
- Thông báo tự động gửi cho người chịu tác động

**Giao diện**
- Bảng báo cáo với filter advanced
- Drawer chi tiết báo cáo + target info
- Modal chọn hành động xử lý
- Status update real-time

### 6. Dashboard Thống kê (Statistics Dashboard)

**Chức năng**
- Hiển thị thống kê tổng quan:
  - Tổng số người dùng
  - Tổng số bài viết
  - Tổng số đánh giá
  - Người dùng bị khóa
  - Bài viết được lưu trữ
- Biểu đồ xu hướng (trends) 7 ngày qua
- Thống kê activity gần đây
- Real-time updates

**Giao diện**
- Stat cards với số liệu và icon
- Line chart cho trends
- Summary section



## 3.1.7.5 Cấu trúc Project

```
Web/
├── src/
│   ├── components/              # React components tái sử dụng
│   │   ├── AppSidebar.tsx       # Navigation sidebar
│   │   ├── AppTopbar.tsx        # Top navigation bar
│   │   ├── MainLayout.tsx       # Layout chính
│   │   ├── DashboardStatCard.tsx # Card thống kê
│   │   ├── StatusBadge.tsx      # Badge trạng thái
│   │   ├── ConfirmActionModal.tsx # Modal xác nhận
│   │   ├── LoadingState.tsx     # Loading spinner
│   │   └── EmptyState.tsx       # Empty state UI
│   │
│   ├── pages/                   # Page components
│   │   ├── LoginPage.tsx        # Trang đăng nhập
│   │   ├── DashboardPage.tsx    # Trang dashboard
│   │   ├── UsersPage.tsx        # Trang quản lý người dùng
│   │   ├── PostsPage.tsx        # Trang quản lý bài viết
│   │   ├── ReviewsPage.tsx      # Trang quản lý đánh giá
│   │   └── ReportsPage.tsx      # Trang quản lý báo cáo
│   │
│   ├── services/                # API services
│   │   ├── apiClient.ts         # Axios client + interceptors
│   │   ├── authService.ts       # Authentication
│   │   ├── userAdminService.ts  # User operations
│   │   ├── postAdminService.ts  # Post operations
│   │   ├── reviewAdminService.ts # Review operations
│   │   ├── reportAdminService.ts # Report moderation
│   │   └── dashboardService.ts  # Dashboard stats
│   │
│   ├── stores/                  # State management
│   │   └── authStore.ts         # Zustand auth store
│   │
│   ├── types/                   # TypeScript định nghĩa
│   │   └── index.ts             # Tất cả interfaces
│   │
│   ├── mock/                    # Mock data
│   │   └── mockData.ts          # Sample data
│   │
│   ├── routes/                  # Router
│   │   └── index.tsx            # Route definitions
│   │
│   ├── App.tsx                  # Main app component
│   └── main.tsx                 # Entry point
│
├── vite.config.ts               # Vite configuration
├── tsconfig.json                # TypeScript config
├── package.json                 # Dependencies
└── index.html                   # HTML entry
```

## 3.1.7.6 Quy trình triển khai

### Phase 1: Thiết lập dự án (Setup)

1. **Cài đặt dependencies**
```bash
cd Web
npm install
```

2. **Cấu hình environment**
   - Tạo file `.env.local`
   - Cấu hình API URL: `VITE_API_URL=http://localhost:8081/api`

3. **Khởi động dev server**
```bash
npm run dev
```
   - Server chạy trên `http://localhost:5173`

### Phase 2: Phát triển giao diện (UI Development)

1. **Xây dựng layout chính**
   - Sidebar navigation
   - Top bar với user menu
   - Main content area

2. **Tạo các trang (Pages)**
   - LoginPage: Form đăng nhập
   - DashboardPage: Thống kê
   - UsersPage: Quản lý user
   - PostsPage: Quản lý post
   - ReviewsPage: Quản lý review
   - ReportsPage: Quản lý báo cáo

3. **Thêm components tái sử dụng**
   - StatusBadge, ConfirmActionModal, EmptyState, LoadingState

### Phase 3: Tích hợp API (API Integration)

1. **Cấu hình Axios Client**
   - Thiết lập baseURL
   - Request interceptor (add JWT token)
   - Response interceptor (check 401)

2. **Phát triển Services**
   - authService: Login, logout, token management
   - userAdminService: GET /admin/users, block/unblock user
   - postAdminService: GET /admin/posts, archive/delete post
   - reviewAdminService: GET /admin/reviews
   - reportAdminService: GET /admin/reports, POST resolve action
   - dashboardService: GET /admin/dashboard/stats

3. **Xử lý Authentication**
   - JWT token lưu trong localStorage
   - Token tự động gửi trong header
   - Handle 401 → auto logout

### Phase 4: State Management & Route Protection

1. **Zustand Store**
   - Lưu user info + token
   - Persist vào localStorage
   - Login/logout functions

2. **Protected Routes**
   - Kiểm tra token trước khi render route
   - Redirect tới login nếu không authenticated
   - Kiểm tra role (chỉ admin=1 được vào)

### Phase 5: Lọc & Phân trang (Filtering & Pagination)

1. **Client-side Filtering**
   - Lọc theo search text, status, category
   - Ứng dụng ngay mà không cần reload

2. **Pagination**
   - Phân chia dữ liệu theo page size
   - Navigate giữa các trang

### Phase 6: Modal & Drawer

1. **ConfirmActionModal**
   - Xác nhận trước khi thực hiện hành động
   - Block/delete/resolve

2. **Detail Drawer**
   - Xem chi tiết full record
   - Dạng side panel drawer

### Phase 7: Build & Deployment

1. **Development Build**
```bash
npm run dev
```

2. **Production Build**
```bash
npm run build
npm run preview
```

3. **Hosting**
   - Có thể deploy lên: Vercel, Netlify, AWS S3 + CloudFront, v.v.

## 3.1.7.7 Tích hợp với Backend Spring Boot

### REST API Endpoints

| Method | Endpoint | Mô tả |
|--------|----------|-------|
| POST | `/api/auth/login` | Đăng nhập |
| GET | `/api/admin/users` | Danh sách users |
| GET | `/api/admin/users/{id}` | Chi tiết user |
| PUT | `/api/admin/users/{id}/block` | Khóa user |
| GET | `/api/admin/posts` | Danh sách posts |
| GET | `/api/admin/posts/{id}` | Chi tiết post |
| PUT | `/api/admin/posts/{id}/archive` | Archive post |
| DELETE | `/api/admin/posts/{id}` | Xóa post |
| GET | `/api/admin/reviews` | Danh sách reviews |
| GET | `/api/admin/reports` | Danh sách reports |
| POST | `/api/admin/reports/{id}/resolve` | Xử lý report |
| GET | `/api/admin/dashboard/stats` | Thống kê |

### JWT Authentication Flow

```
Frontend                          Backend
   ↓                                ↓
User submit login form
   │                                │
   └──→ POST /api/auth/login ──────→
                                    ├─ Validate credentials
                                    ├─ Generate JWT token
                                    └─→ Return token + user info
   ←──────────────── Response ──────┘
   │
   ├─ Save token in localStorage
   ├─ Save user in Zustand store
   └─ Add token to axios default header

Every Request:
   │
   └──→ GET /api/admin/users ─────→
        (header: Authorization: Bearer {token})
                                    ├─ Verify token
                                    ├─ Check role
                                    └─→ Return data
   ←──────────── Response ──────────┘
```

## 3.1.7.8 Các tính năng ahead (Roadmap)

- [ ] Real-time notifications (WebSocket)
- [ ] Advanced analytics & reports
- [ ] Bulk actions (archive multiple posts)
- [ ] User activity timeline
- [ ] System logs & audit trail
- [ ] Custom dashboard widgets
- [ ] Dark mode support
- [ ] Multi-language support

## 3.1.7.9 Troubleshooting

### Connection Refused
- Kiểm tra backend Spring Boot có chạy trên port 8081
- Kiểm tra CORS configuration trên backend

### 401 Unauthorized
- Token hết hạn → auto logout
- Tài khoản không phải admin (role != 1)

### Blank Pages
- Kiểm tra browser console có lỗi gì
- Kiểm tra API response format

---

**Kết luận**: Web admin dashboard WeConnect cung cấp giao diện quản trị toàn diện, xây dựng trên React + TypeScript với tích hợp REST API từ backend Spring Boot. Hệ thống hỗ trợ JWT authentication, state management, responsive design, và sẵn sàng cho triển khai production.
