# Admin Dashboard - Activity Connection App

A modern, professional, and fully functional admin dashboard for managing the Activity Connection mobile app. Built with React, TypeScript, Vite, and Ant Design.

## 🌟 Features

### ✅ Implemented
- **Authentication**: Email/password login with JWT token support
- **Dashboard**: Real-time statistics and recent activity overview
- **User Management**: Complete CRUD operations, blocking/unblocking users
- **Post Management**: View, archive, delete posts with filtering
- **Review Management**: Browse and filter user reviews
- **Protected Routes**: Secure authentication-based access control
- **Responsive Design**: Mobile, tablet, and desktop support
- **Modern UI**: Clean, professional design with Ant Design components

### 🛠️ Architecture
- **Mock Data Service**: Ready for backend API integration
- **Service Layer**: Easy switching from mock to real APIs
- **State Management**: Zustand for authentication state
- **API Client**: Axios with token interceptors
- **TypeScript**: Full type safety throughout

## 📁 Project Structure

```
src/
├── components/           # Reusable components
│   ├── AppSidebar.tsx       # Left navigation sidebar
│   ├── AppTopbar.tsx        # Top navigation bar
│   ├── DashboardStatCard.tsx # Statistics card
│   ├── StatusBadge.tsx      # Status indicator
│   ├── ConfirmActionModal.tsx # Confirmation dialog
│   ├── EmptyState.tsx       # Empty state UI
│   ├── LoadingState.tsx     # Loading indicator
│   └── MainLayout.tsx       # Main layout wrapper
├── pages/               # Page components
│   ├── LoginPage.tsx        # Authentication page
│   ├── DashboardPage.tsx    # Main dashboard
│   ├── UsersPage.tsx        # User management
│   ├── PostsPage.tsx        # Post management
│   ├── ReviewsPage.tsx      # Review management
│   └── ReportsPage.tsx      # Reports (coming soon)
├── services/            # API and business logic
│   ├── apiClient.ts         # Axios client with interceptors
│   ├── authService.ts       # Authentication service
│   ├── userAdminService.ts  # User admin operations
│   ├── postAdminService.ts  # Post admin operations
│   ├── reviewAdminService.ts # Review admin operations
│   ├── dashboardService.ts  # Dashboard statistics
│   └── reportAdminService.ts # Reports (placeholder)
├── stores/              # State management
│   └── authStore.ts         # Zustand auth store
├── types/               # TypeScript interfaces
│   └── index.ts             # All type definitions
├── mock/                # Mock data
│   └── mockData.ts          # Sample data for development
├── routes/              # Routing configuration
│   └── index.tsx            # Route definitions
├── App.tsx              # Main app component
└── main.tsx             # Entry point
```

## 🚀 Quick Start

### Prerequisites
- Node.js 16+ and npm/yarn
- Git

### Installation

1. **Clone or navigate to project**
```bash
cd d:\Web
```

2. **Install dependencies**
```bash
npm install
```

3. **Configure environment** (optional)
```bash
# Copy example env file
cp .env.example .env

# Edit .env if needed
# VITE_API_URL=http://localhost:8080/api
```

4. **Start development server**
```bash
npm run dev
```

The application will open at `http://localhost:5173`

### Build for Production
```bash
npm run build
```

### Preview Production Build
```bash
npm run preview
```

## 🔐 Authentication

### Demo Credentials
- **Email**: `admin@example.com`
- **Password**: `password123`

> **Note**: Click "Fill Demo Credentials" button on the login page for convenience

### How It Works
1. User submits login credentials
2. Frontend calls `POST /api/auth/login` on your backend
3. Backend returns JWT token
4. Token is stored in localStorage
5. All subsequent API calls include the token in Authorization header
6. Protected routes check for token presence

## 📊 Backend API Integration

### Current Status

#### ✅ Real APIs (Connected)
- `POST /api/auth/login` - User authentication

#### 🔄 Mock Data (Ready for Implementation)
- User Management - `GET /api/admin/users`, `POST /api/admin/users/:id/*`
- Post Management - `GET /api/admin/posts`, `POST /api/admin/posts/:id/*`  
- Review Management - `GET /api/admin/reviews`
- Dashboard - `GET /api/admin/dashboard/stats`

### How to Switch From Mock to Real APIs

1. **Open the service file** (e.g., `src/services/userAdminService.ts`)

2. **Replace mock implementation** with real API calls:

```typescript
// Before (Mock)
async getUsers(params, filter) {
  await new Promise((resolve) => setTimeout(resolve, 300))
  let users = [...mockUsers]
  // ... filtering logic
  return { data: users.slice(start, end), total, page, pageSize }
}

// After (Real API)
async getUsers(params, filter) {
  return apiClient.get<PaginatedResponse<User>>('/admin/users', {
    params: { page: params.page, pageSize: params.pageSize, ...filter }
  })
}
```

3. **Update the service** for all CRUD operations
4. **Test thoroughly** with your backend

### Service Layer Pattern

Each service follows this pattern:
```typescript
export const userAdminService = {
  async getUsers() { /* implementation */ },
  async getUser(id) { /* implementation */ },
  async updateUser(id, data) { /* implementation */ },
  async deleteUser(id) { /* implementation */ },
  // ... more operations
}
```

## 🎨 UI/UX Features

### Design System
- **Color Scheme**: Blue primary (#1890ff), neutral grays
- **Typography**: Clear hierarchy with Ant Design scales
- **Spacing**: 8px base unit for consistent padding/margins
- **Border Radius**: 8px for cards, 6px for inputs
- **Shadows**: Subtle elevation shadows for depth

### Components

#### DashboardStatCard
Displays key metrics with icons and trends
```jsx
<DashboardStatCard
  title="Total Users"
  value={100}
  icon={<UserOutlined />}
  color="blue"
  trendValue="+12% this month"
  trend="up"
/>
```

#### StatusBadge
Shows status with appropriate styling
```jsx
<StatusBadge 
  status="active" 
  text="Active" 
/>
```

#### MainLayout
Wraps pages with sidebar and topbar
```jsx
<MainLayout>
  <YourPageContent />
</MainLayout>
```

## 📱 Responsive Breakpoints
- **XS**: < 576px (Mobile)
- **SM**: 576-768px (Tablet)
- **MD**: 768-1200px (Laptop)
- **LG**: > 1200px (Desktop)

## 🛡️ Security

### Token Management
- JWT token stored in localStorage
- Automatically sent with every request via Axios interceptor
- Expired tokens (401 response) trigger redirect to login
- Logout clears token and user data

### Protected Routes
- All admin pages require authentication
- Unauthenticated users redirected to login
- Token validation on app initialization

## 🔧 API Client Configuration

### Axios Client (`src/services/apiClient.ts`)

```typescript
// Request interceptor adds token
config.headers.Authorization = `Bearer ${token}`

// Response interceptor handles 401
if (error.response?.status === 401) {
  localStorage.removeItem('token')
  window.location.href = '/login'
}
```

### Making API Calls

```typescript
import apiClient from '@/services/apiClient'

// Standard API response pattern
const result = await apiClient.post('/auth/login', credentials)
// Returns: result (from response.result field)

// Raw response (non-API pattern)
const raw = await apiClient.postRAW('/endpoint', data)
// Returns: raw response data
```

## 📦 Dependencies

### Core
- **react** - UI library
- **react-dom** - React DOM rendering
- **react-router-dom** - Routing
- **typescript** - Type safety

### UI & Styling
- **antd** - Component library
- **@ant-design/icons** - Icon set

### State & API
- **zustand** - State management
- **axios** - HTTP client
- **dayjs** - Date formatting

### Development
- **vite** - Build tool
- **@vitejs/plugin-react** - React plugin for Vite

## 🚨 Common Issues & Solutions

### Port Already in Use
```bash
# Kill process on port 5173
lsof -ti:5173 | xargs kill -9
# Or change port in vite.config.ts
```

### CORS Errors When Calling Backend
```typescript
// Backend must allow CORS headers
// Or proxy requests through vite.config.ts
server: {
  proxy: {
    '/api': {
      target: 'http://localhost:8080',
      changeOrigin: true,
    }
  }
}
```

### Module Not Found
```bash
# Clear node_modules and reinstall
rm -rf node_modules package-lock.json
npm install
```

### TypeScript Errors
```bash
# Rebuild TS
npx tsc --noEmit
```

## 📚 Usage Examples

### Login
```bash
# Use demo credentials
Email: admin@example.com
Password: password123

# Or integrate with your backend login endpoint
```

### View Dashboard
Navigate to `/dashboard` after login to see:
- Statistics cards (users, posts, reviews)
- Recent users table
- Recent posts table
- Recent reviews table

### Manage Users
1. Go to Users page
2. Search/filter users
3. Click "View" to see details
4. Click "Block" to block user
5. Click "Delete" to remove user

### Manage Posts
1. Go to Posts page
2. Search/filter posts
3. Click "View" for post details
4. Click "Archive" to hide from users
5. Click "Delete" to remove post

### Manage Reviews
1. Go to Reviews page
2. Search reviews by activity name
3. Filter by reputation label
4. Click "View" for full review details

## 🔄 Data Flow

```
User Login
    ↓
authService.login(credentials)
    ↓
POST /api/auth/login
    ↓
Store token + user in localStorage + Zustand
    ↓
Redirect to /dashboard
    ↓
Page loads, calls service layer
    ↓
Service (mock or real API)
    ↓
Display data in UI components
```

## 🎯 Next Steps for Production

1. **Set Backend URL**: Update `VITE_API_URL` in `.env`
2. **Implement Real APIs**: Replace mock services with actual API calls
3. **Error Handling**: Add comprehensive error handling
4. **Validation**: Implement form validation
5. **Testing**: Add unit and integration tests
6. **Performance**: Optimize bundle size and caching
7. **Security**: Implement refresh tokens, CSRF protection
8. **Monitoring**: Add error tracking and analytics

## 📧 Support & Documentation

### File Structure Help
- Components: `src/components/`
- Pages: `src/pages/`
- Services: `src/services/`
- Types: `src/types/`

### Adding New Features

1. **Create page component**
```bash
# src/pages/NewFeaturePage.tsx
```

2. **Create service**
```bash
# src/services/newFeatureService.ts
```

3. **Add route**
```typescript
// src/routes/index.tsx
<Route path="/new-feature" element={<NewFeaturePage />} />
```

4. **Add sidebar menu item**
```typescript
// src/components/AppSidebar.tsx
{
  key: '/new-feature',
  icon: <IconName />,
  label: 'New Feature',
}
```

## 📝 License

This project is part of the Activity Connection app system.

## 🎓 Notes

### Architecture Decisions
- **Zustand** over Redux: Simpler, less boilerplate
- **Mock Services**: Allows frontend development without backend
- **Service Layer**: Clean separation of concerns
- **Ant Design**: Rich component library, professional appearance
- **Vite**: Fast development and build times

### Performance Considerations
- Lazy loading routes (can be added)
- Memoized components (where needed)
- Pagination for large datasets
- Efficient table rendering

### Future Enhancements
- Report management module
- Advanced analytics and charts
- Batch operations for users/posts
- Activity audit logs
- Email notifications
- Real-time updates with WebSocket

---

**Happy coding! 🚀**

If you encounter any issues, check the troubleshooting section or refer to the Ant Design documentation: https://ant.design/
