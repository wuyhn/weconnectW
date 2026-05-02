# 🚀 QUICK START GUIDE

## ⚡ Get Running in 3 Steps

### Step 1️⃣: Install Dependencies
```bash
cd d:\Web
npm install
```
**Wait**: 2-5 minutes ⏳

### Step 2️⃣: Start Development Server
```bash
npm run dev
```
**Result**: Browser opens at `http://localhost:5173/`

### Step 3️⃣: Login with Demo Account
- **Email**: `admin@example.com`
- **Password**: `password123`
- (Or click "Fill Demo Credentials" button)

✅ **Done!** You now have a fully functional admin dashboard!

---

## 🎯 What You Can Do

### 📊 Dashboard
- View system statistics (users, posts, reviews)
- See recent activity
- Monitor system health

### 👥 Users
- Search and filter users
- View user details
- Block/unblock users
- Delete users

### 📝 Posts
- Search and filter posts
- View post details
- Archive/restore posts
- Delete posts

### ⭐ Reviews
- Search reviews
- Filter by reputation label
- View full review details

### 📋 Reports
- Placeholder for future development
- Development roadmap included

---

## 🔧 Troubleshooting

### "npm command not found"
→ Install Node.js from https://nodejs.org/

### "Port 5173 already in use"
→ Kill existing process or change port in vite.config.ts

### "Cannot find module"
→ Run `npm install` again

### CORS errors
→ Ensure backend allows CORS (if using real backend)

---

## 📚 Next Steps

1. ✅ **Explore UI**: Navigate all pages and features
2. 📖 **Read Documentation**: Check README.md
3. 🔌 **Connect Backend**: Update VITE_API_URL in .env
4. 🛠️ **Integrate APIs**: Replace mock services with real API calls
5. 🚀 **Deploy**: Build and deploy to production

---

## 📋 Available Scripts

```bash
npm run dev           # Start development server
npm run build         # Build for production
npm run preview       # Preview production build
npm run lint          # Check for ESLint errors
```

---

## 📁 Project Structure at a Glance

```
src/
├── pages/           # Login, Dashboard, Users, Posts, Reviews, Reports
├── components/      # Sidebar, Topbar, Cards, Tables, Modals
├── services/        # API & business logic (ready for backend)
├── stores/          # Auth state management
├── types/           # TypeScript interfaces
└── mock/            # Test data (10 users, 12 posts, 8 reviews)
```

---

## 🎨 UI Architecture

```
┌─────────────────────────────────────┐
│         AppTopbar                   │
│   (Title, Search, User Menu)        │
├──────────────┬──────────────────────┤
│              │                      │
│ AppSidebar   │   Page Content       │
│              │                      │
│ • Dashboard  │  Dashboard / Users / │
│ • Users      │  Posts / Reviews /   │
│ • Posts      │  Reports             │
│ • Reviews    │                      │
│ • Reports    │                      │
│ • Logout     │                      │
│              │                      │
└──────────────┴──────────────────────┘
```

---

## 🔒 Authentication

- **Demo Mode**: Use provided credentials, no backend needed
- **Real Mode**: Connect to your Spring Boot backend
- **Token Storage**: JWT stored in localStorage
- **Auto-Attach**: Token automatically added to all API requests

---

## 🎓 Key Features

✨ **Modern Design**
- Clean, professional UI
- Responsive (mobile/tablet/desktop)
- Ant Design components
- Smooth animations

🔄 **Service Layer**
- Mock data ready for testing
- Easy switch to real APIs
- Type-safe operations
- Proper error handling

🛡️ **Security**
- Token-based authentication
- Protected routes
- CORS support ready
- Secure logout

📱 **Responsive**
- Mobile: < 576px
- Tablet: 576-1200px
- Desktop: > 1200px

---

## 🌐 Backend Integration

### When Backend is Ready

1. Update `.env`:
```
VITE_API_URL=http://your-backend:8080/api
```

2. Update service files (e.g., `userAdminService.ts`):
```typescript
// Replace mock with real API
async getUsers(params, filter) {
  return apiClient.get('/admin/users', { params: {...} })
}
```

3. That's it! No component changes needed.

---

## 📦 Tech Stack

- **Frontend**: React 18 + TypeScript
- **Build**: Vite
- **Routing**: React Router v6
- **UI Components**: Ant Design 5
- **State**: Zustand
- **HTTP**: Axios
- **Styling**: CSS + Ant Design theme

---

## 💡 Important Notes

### Demo Credentials
Always available for testing without backend:
- Email: `admin@example.com`
- Password: `password123`

### Mock Data Includes
- 10 users (2 blocked)
- 12 posts (2 archived)
- 8 reviews
- Dashboard statistics

### Backend Expected Format
```json
{
  "code": 1000,
  "message": "success",
  "result": {
    "id": 1,
    "email": "admin@example.com",
    "fullName": "Admin User",
    "token": "eyJ..."
  }
}
```

---

## 🎯 Common Tasks

### Login
```
1. Go to http://localhost:5173/
2. Enter credentials
3. Click "Sign In"
→ Redirects to dashboard
```

### View Users
```
1. Click "Users" in sidebar
2. Scroll through paginated table
3. Filter by role or status
4. Click "View" for details
```

### Block a User
```
1. Go to Users page
2. Find user in table
3. Click "Block" button
4. Confirm action
→ User status changes to "Blocked"
```

### Archive a Post
```
1. Go to Posts page
2. Find post in table
3. Click "Archive" button
4. Confirm action
→ Post hidden from users
```

---

## 📞 Need Help?

1. Check the error in browser console (F12)
2. Read README.md for detailed documentation
3. Check SETUP.md for installation issues
4. Review IMPLEMENTATION_NOTES.md for architecture
5. Verify backend is running (if using real API)

---

## ✨ What's Working Now

✅ All UI pages (Login, Dashboard, Users, Posts, Reviews, Reports)  
✅ Complete user management (CRUD + block/unblock)  
✅ Complete post management (CRUD + archive)  
✅ Complete review management  
✅ Responsive design  
✅ Authentication & protected routes  
✅ Mock data for testing  
✅ Service layer for easy API integration  

## 🚀 What's Next

🔄 Connect to your Spring Boot backend  
🔄 Replace mock services with real API calls  
🔄 Add backend validation as needed  
🔄 Deploy to production  

---

## 🎉 You're All Set!

The admin dashboard is **ready to use** with demo data. Start exploring and integrating your backend APIs when ready.

**Happy coding!** 🚀

---

*For detailed documentation, see README.md | For setup help, see SETUP.md | For architecture details, see IMPLEMENTATION_NOTES.md*
