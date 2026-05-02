# Implementation Notes - Admin Dashboard Architecture

## 🏗️ System Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│                     Frontend (React + TypeScript)               │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              UI Components (Ant Design)                  │  │
│  │ ┌─────────────────────────────────────────────────────┐ │  │
│  │ │ AppSidebar | AppTopbar | Pages | Modals | Drawers │ │  │
│  │ └─────────────────────────────────────────────────────┘ │  │
│  └──────────────────────────────────────────────────────────┘  │
│                            ↓                                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         Service Layer (API & Business Logic)            │  │
│  │ ┌─────────────────────────────────────────────────────┐ │  │
│  │ │ Auth | User | Post | Review | Dashboard | Reports │ │  │
│  │ └─────────────────────────────────────────────────────┘ │  │
│  │             ↓                          ↓                │  │
│  │        Mock Data              Real API (Future)         │  │
│  └──────────────────────────────────────────────────────────┘  │
│                            ↓                                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │              API Client (Axios)                         │  │
│  │  - Token Management                                    │  │
│  │  - Request/Response Interceptors                       │  │
│  │  - Error Handling                                      │  │
│  └──────────────────────────────────────────────────────────┘  │
│                            ↓                                    │
│  ┌──────────────────────────────────────────────────────────┐  │
│  │         State Management (Zustand)                      │  │
│  │  - Auth State (user, token)                            │  │
│  │  - Local Storage Persistence                           │  │
│  └──────────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
                            ↓ HTTP
┌─────────────────────────────────────────────────────────────────┐
│                Backend API (Spring Boot)                         │
│  POST /api/auth/login                 [Currently: Real]         │
│  GET  /api/admin/users                [Mock → Real Soon]        │
│  GET  /api/admin/posts                [Mock → Real Soon]        │
│  GET  /api/admin/reviews              [Mock → Real Soon]        │
│  GET  /api/admin/dashboard/stats      [Mock → Real Soon]        │
└─────────────────────────────────────────────────────────────────┘
```

## 🔄 Data Flow Examples

### Example 1: User Login Flow

```
User enters credentials
    ↓
LoginPage.onFinish() called
    ↓
authService.login(email, password)
    ↓
apiClient.postRAW('/auth/login', credentials)
    ↓
Backend returns: {
  code: 1000,
  message: "Đăng nhập thành công!",
  result: {
    id: 1,
    email: "...",
    fullName: "...",
    token: "eyJ..."
  }
}
    ↓
useAuthStore.login(user, token)
    ↓
Store in localStorage + Zustand state
    ↓
Navigate to /dashboard
    ↓
App checks auth → renders protected routes
```

### Example 2: Load Users List

```
UsersPage.useEffect() runs on mount
    ↓
loadUsers(page=1)
    ↓
userAdminService.getUsers(params, filters)
    ↓
Check: Real API or Mock?
    │
    ├─ Mock: Return filtered/paginated mockUsers
    │
    └─ Real API: apiClient.get('/admin/users', params)
    ↓
Return PaginatedResponse<User>
    ↓
setUsers(result.data)
    ↓
Table renders with data
    ↓
User clicks "Delete" → handleDeleteUser()
    ↓
userAdminService.deleteUser(userId)
    ↓
loadUsers(pagination.current) - refresh list
    ↓
Table updates with removed user
```

### Example 3: Switching from Mock to Real API

**Before (Mock):**
```typescript
async getUsers(params, filter) {
  // Simulate API delay
  await new Promise(resolve => setTimeout(resolve, 300))
  
  // Get mock data
  let users = [...mockUsers]
  
  // Apply filters
  if (filter?.search) {
    users = users.filter(u => 
      u.fullName.includes(filter.search) ||
      u.email.includes(filter.search)
    )
  }
  
  // Paginate
  const start = (params.page - 1) * params.pageSize
  const end = start + params.pageSize
  
  return {
    data: users.slice(start, end),
    total: users.length,
    page: params.page,
    pageSize: params.pageSize,
  }
}
```

**After (Real API):**
```typescript
async getUsers(params, filter) {
  const response = await apiClient.get<PaginatedResponse<User>>(
    '/admin/users',
    {
      params: {
        page: params.page,
        pageSize: params.pageSize,
        ...filter
      }
    }
  )
  return response
}
```

That's it! No other code changes needed because the interface is the same.

## 🔐 Authentication & Authorization

### Flow
1. **Login**: POST `/api/auth/login` with credentials
2. **Token Storage**: JWT stored in localStorage
3. **Auto-Attach**: Axios interceptor adds token to all requests
4. **Validation**: Routes check for token presence
5. **Expiration**: 401 response triggers redirect to login

### Token Interceptor
```typescript
// src/services/apiClient.ts
this.client.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})
```

### Protected Routes
```typescript
// src/routes/index.tsx
{isAuthenticated ? (
  <>
    <Route path="/dashboard" element={<DashboardPage />} />
    <Route path="/users" element={<UsersPage />} />
    {/* ... */}
  </>
) : (
  <Route path="*" element={<Navigate to="/login" />} />
)}
```

## 🎯 Service Layer Design

### Pattern
Each service module has:
- **Get List**: Paginated queries with filters
- **Get Single**: Fetch one record
- **Create/Update**: Modify data
- **Delete**: Remove data
- **Special Operations**: Archive, block, etc.

### Example: userAdminService

```typescript
export const userAdminService = {
  // List all users (paginated, filtered)
  async getUsers(params, filter) { }
  
  // Get single user
  async getUser(id) { }
  
  // Update user
  async updateUser(id, data) { }
  
  // Delete user
  async deleteUser(id) { }
  
  // Block user
  async blockUser(id) { }
  
  // Unblock user
  async unblockUser(id) { }
  
  // Get recent users
  async getRecentUsers(limit) { }
}
```

### Mock vs Real Strategy

**Development (Mock):**
- No backend needed
- Instant responses
- Work on UI independently
- Full control over test data

**Production (Real):**
- Connect to Spring Boot backend
- Replace mock with API calls
- Same interface, different implementation
- No component changes needed

## 📊 Component Hierarchy

```
App
├── Router (React Router)
│   ├── LoginPage
│   │   ├── Form Component
│   │   └── Input Fields
│   │
│   └── MainLayout (Protected Routes)
│       ├── AppSidebar
│       │   ├── Menu Items
│       │   └── Logout Button
│       │
│       ├── AppTopbar
│       │   ├── Page Title
│       │   ├── Search
│       │   └── User Dropdown
│       │
│       ├── DashboardPage
│       │   ├── DashboardStatCard (x5)
│       │   ├── Table (Recent Users)
│       │   ├── Table (Recent Posts)
│       │   └── Table (Recent Reviews)
│       │
│       ├── UsersPage
│       │   ├── Filter & Search Bar
│       │   ├── Table (Users)
│       │   ├── Drawer (User Details)
│       │   └── Modal (Confirm Action)
│       │
│       ├── PostsPage
│       │   ├── Filter & Search Bar
│       │   ├── Table (Posts)
│       │   ├── Drawer (Post Details)
│       │   └── Modal (Confirm Action)
│       │
│       ├── ReviewsPage
│       │   ├── Filter & Search Bar
│       │   ├── Table (Reviews)
│       │   └── Drawer (Review Details)
│       │
│       └── ReportsPage
│           └── Coming Soon Placeholder
```

## 🎨 Styling Strategy

### Color Scheme
```
Primary Blue: #1890ff (main actions, primary elements)
Success Green: #52c41a (positive actions, active states)
Warning Orange: #faad14 (warnings, alerts)
Error Red: #ff4d4f (destructive actions, errors)
Gray: #262626, #8c8c8c, #bfbfbf (text, borders, disabled)
Background: #f5f5f5, #fafafa (content areas)
```

### Component Styling Approach
- **Ant Design Components**: Use component props
- **Custom Styles**: CSS files co-located with components
- **Responsive**: Mobile-first media queries
- **Hover States**: Subtle transitions for interactivity
- **Shadows**: Subtle elevation for depth

## 🧪 Testing Scenarios

### Test Case 1: Login with Demo Credentials
1. Navigate to http://localhost:5173/login
2. Click "Fill Demo Credentials"
3. Click "Sign In"
4. Should redirect to /dashboard
5. Should show dashboard data

### Test Case 2: User Management
1. Navigate to Users page
2. Search for a user
3. Click "View" to see details
4. Click "Block" to block user
5. Confirm action
6. User status should change

### Test Case 3: Filter & Pagination
1. Go to Users page
2. Filter by role (User/Admin)
3. Filter by status (Active/Blocked)
4. Change page
5. Data should update accordingly

### Test Case 4: Backend Connection
1. Ensure backend is running
2. Update VITE_API_URL in .env
3. Use real login credentials
4. Should authenticate with backend
5. All operations should call backend APIs

## 📈 Performance Optimizations

### Current
- Lazy loading of pages (can be enhanced)
- Efficient table rendering
- Pagination for large datasets
- Debounced search input

### Future Improvements
- React.memo for component memoization
- useCallback for callback optimization
- Code splitting for route-based chunks
- Image lazy loading
- API response caching
- Virtualized tables for large lists

## 🔧 Configuration Points

### Environment Variables (`.env`)
```
VITE_API_URL=http://localhost:8080/api
```

### Theme Configuration (`src/main.tsx`)
```typescript
ConfigProvider theme={{
  token: {
    colorPrimary: '#1890ff',
    borderRadius: 8,
    fontSize: 14,
  },
}}
```

### API Client Configuration (`src/services/apiClient.ts`)
- Base URL
- Timeout (10s)
- Interceptors
- Error handling

## 📝 Type Safety

### All types defined in `src/types/index.ts`

```typescript
// User entity (matches backend)
interface User {
  id: number
  email: string
  fullName: string
  gender?: string
  // ... other fields
}

// API response format
interface ApiResponse<T> {
  code: number
  message: string
  result: T
}

// Paginated response
interface PaginatedResponse<T> {
  data: T[]
  total: number
  page: number
  pageSize: number
}
```

## 🚀 Deployment Checklist

- [ ] Backend URL updated in `.env`
- [ ] Real API endpoints implemented in services
- [ ] Error handling tested
- [ ] Forms validated
- [ ] Mobile responsiveness verified
- [ ] Performance optimized
- [ ] Security reviewed (CORS, tokens, etc.)
- [ ] Build runs without errors: `npm run build`
- [ ] Production build tested: `npm run preview`
- [ ] Deployed to hosting service

## 📚 File Dependencies

```
pages/*.tsx
    ↓
services/*.ts
    ↓
apiClient.ts
    ↓
localStorage / mock data

components/*.tsx
    ↓ (used by)
layouts/MainLayout.tsx
    ↓ (wraps)
pages/*.tsx

stores/authStore.ts
    ↓ (imported by)
App.tsx, LoginPage.tsx, services/*.ts

types/index.ts
    ↓ (imported by)
All *.tsx and *.ts files
```

## 🎓 Key Concepts

### Zustand Store Pattern
```typescript
// Create store
export const useAuthStore = create<AuthStore>((set) => ({
  user: null,
  token: null,
  login: (user, token) => set({ user, token }),
  logout: () => set({ user: null, token: null }),
}))

// Use in components
const { user, login } = useAuthStore()
```

### Service Layer Pattern
```typescript
export const userAdminService = {
  async getUsers() { /* ... */ },
  async deleteUser(id) { /* ... */ },
}

// Use in components
const data = await userAdminService.getUsers(params)
```

### Protected Routes Pattern
```typescript
<Route
  path="/dashboard"
  element={isAuthenticated ? <Dashboard /> : <Navigate to="/login" />}
/>
```

## 🔄 Development Workflow

1. **Design**: UI/UX mockups (done ✓)
2. **Component Development**: Build UI components (done ✓)
3. **Service Integration**: Connect services to components (done ✓)
4. **Mock Testing**: Test with mock data (ready ✓)
5. **Backend Integration**: Replace mocks with real APIs (next step →)
6. **Testing**: Unit & integration tests (optional)
7. **Deployment**: Build and deploy (final step)

---

**This architecture is designed for:**
- ✅ Easy backend integration
- ✅ Clear separation of concerns
- ✅ Type-safe development
- ✅ Professional appearance
- ✅ Scalable codebase
- ✅ Quick feature additions

Refer to README.md for API integration instructions.
