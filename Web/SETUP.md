# Setup & Installation Guide

## 📋 Prerequisites

Before starting, ensure you have:
- **Node.js**: Version 16.0.0 or higher
- **npm** or **yarn**: Node package manager (comes with Node.js)
- **Git**: For version control (optional)

## ✅ Step 1: Verify Node.js Installation

Open terminal/PowerShell and check:

```bash
node --version  # Should show v16.0.0 or higher
npm --version   # Should show 7.0.0 or higher
```

If not installed, download from: https://nodejs.org/

## 🚀 Step 2: Install Dependencies

Navigate to the project directory:

```bash
cd d:\Web
```

Install all required packages:

```bash
npm install
```

This will install:
- React, React Router, React DOM
- TypeScript
- Ant Design & Icons
- Axios
- Zustand
- Vite and plugins
- And all other dependencies

**Time:** ~2-5 minutes depending on internet speed

## 🔧 Step 3: Configure Environment (Optional)

The project includes a default `.env` file configured for local development:

```
VITE_API_URL=http://localhost:8080/api
```

If your backend runs on a different address:
1. Edit `.env` file
2. Update `VITE_API_URL` to your backend address
3. Save and restart dev server

## 💻 Step 4: Start Development Server

Run the development server:

```bash
npm run dev
```

You should see output like:
```
  VITE v5.0.0  ready in 123 ms

  ➜  Local:   http://localhost:5173/
  ➜  press h to show help
```

The app should automatically open in your default browser at `http://localhost:5173/`

## 🔐 Step 5: Login

### Using Demo Credentials (No Backend Required)

1. On the login page, enter:
   - **Email**: `admin@example.com`
   - **Password**: `password123`

2. Or click "Fill Demo Credentials" button

3. Click "Sign In"

### With Real Backend

If your backend is running:
1. Enter your admin's email and password
2. Click "Sign In"
3. System will call your backend's `POST /api/auth/login` endpoint

## 📊 Step 6: Explore the Dashboard

After login, you can:
- View dashboard with statistics
- Manage users (view, block, delete)
- Manage posts (view, archive, delete)
- Manage reviews (view, filter, sort)
- View reports placeholder (coming soon)

## 🛑 Stopping the Dev Server

Press `Ctrl+C` in the terminal where the server is running.

## 🏗️ Step 7: Build for Production

When ready to deploy:

```bash
npm run build
```

This creates an optimized `dist` folder. Then:

```bash
npm run preview
```

To preview the production build locally.

## 📂 Deployment

### Deploy to Vercel (Recommended)
```bash
npm install -g vercel
vercel
```

### Deploy to Netlify
```bash
npm install -g netlify-cli
netlify deploy
```

### Manual Deployment
1. Upload contents of `dist` folder to your server
2. Configure server to serve `index.html` for all routes
3. Update `VITE_API_URL` in build process

## 🤔 Troubleshooting

### Issue: "npm command not found"
**Solution**: Node.js not installed properly. Reinstall from https://nodejs.org/

### Issue: Port 5173 already in use
**Solution**: 
```bash
# Windows
netstat -ano | findstr :5173
taskkill /PID <PID> /F

# Mac/Linux
lsof -i :5173
kill -9 <PID>
```

Or change port in `vite.config.ts`:
```typescript
server: {
  port: 3000,
}
```

### Issue: CORS errors when calling backend
**Solution**: 
- Ensure backend allows CORS for frontend URL
- Check `VITE_API_URL` in `.env` is correct
- Backend should have CORS headers enabled

### Issue: Module not found errors
**Solution**: 
```bash
# Reinstall dependencies
rm -rf node_modules package-lock.json
npm install
```

### Issue: TypeScript errors
**Solution**: 
```bash
# Check type errors
npx tsc --noEmit

# Restart dev server
npm run dev
```

### Issue: White screen after login
**Solution**:
- Check browser console for errors (F12)
- Verify backend is running
- Check `VITE_API_URL` configuration
- Try clearing localStorage (F12 > Application > Storage)

## 🔌 Backend Integration Checklist

Before connecting to your Spring Boot backend:

- [ ] Backend is running and accessible
- [ ] CORS is enabled on backend
- [ ] `POST /api/auth/login` endpoint exists and returns required format
- [ ] API returns response with structure:
  ```json
  {
    "code": 1000,
    "message": "success",
    "result": {
      "id": number,
      "email": string,
      "fullName": string,
      "token": string
    }
  }
  ```
- [ ] Other admin endpoints are ready or will be mocked
- [ ] `.env` points to correct backend URL

## 📚 Project Structure Created

```
d:\Web/
├── .env                          # Environment variables
├── .env.example                  # Example env file
├── .gitignore                    # Git ignore rules
├── package.json                  # Dependencies & scripts
├── tsconfig.json                 # TypeScript config
├── tsconfig.node.json            # Node TypeScript config
├── vite.config.ts                # Vite configuration
├── index.html                    # HTML entry point
├── README.md                     # Main documentation
├── SETUP.md                      # This file
│
├── src/
│   ├── main.tsx                  # App entry point
│   ├── App.tsx                   # Main App component
│   ├── App.css                   # Global styles
│   │
│   ├── components/               # Reusable components
│   │   ├── AppSidebar.tsx
│   │   ├── AppTopbar.tsx
│   │   ├── MainLayout.tsx
│   │   ├── DashboardStatCard.tsx
│   │   ├── StatusBadge.tsx
│   │   ├── ConfirmActionModal.tsx
│   │   ├── EmptyState.tsx
│   │   ├── LoadingState.tsx
│   │   └── *.css                 # Component styles
│   │
│   ├── pages/                    # Page components
│   │   ├── LoginPage.tsx
│   │   ├── DashboardPage.tsx
│   │   ├── UsersPage.tsx
│   │   ├── PostsPage.tsx
│   │   ├── ReviewsPage.tsx
│   │   ├── ReportsPage.tsx
│   │   └── *.css                 # Page styles
│   │
│   ├── services/                 # Business logic
│   │   ├── apiClient.ts          # API client with interceptors
│   │   ├── authService.ts        # Authentication
│   │   ├── userAdminService.ts   # User CRUD (mock/API)
│   │   ├── postAdminService.ts   # Post CRUD (mock/API)
│   │   ├── reviewAdminService.ts # Review operations (mock/API)
│   │   ├── dashboardService.ts   # Dashboard stats (mock/API)
│   │   └── reportAdminService.ts # Reports (placeholder)
│   │
│   ├── stores/                   # State management
│   │   └── authStore.ts          # Zustand auth store
│   │
│   ├── types/                    # TypeScript types
│   │   └── index.ts              # All interfaces
│   │
│   ├── mock/                     # Mock data
│   │   └── mockData.ts           # Sample data
│   │
│   └── routes/                   # Routing
│       └── index.tsx             # Route definitions
```

## 🚀 Quick Commands Reference

```bash
# Install dependencies
npm install

# Start development server
npm run dev

# Build for production
npm run build

# Preview production build
npm run preview

# Stop dev server
# Press Ctrl+C
```

## ✨ Features Ready to Use

### Fully Functional
✅ Admin login with demo credentials  
✅ Protected routes with authentication  
✅ Dashboard with statistics  
✅ User management (CRUD + block/unblock)  
✅ Post management (CRUD + archive/restore)  
✅ Review management (search, filter, view)  
✅ Responsive design (mobile, tablet, desktop)  
✅ Modern UI with Ant Design  
✅ Mock data for testing  
✅ Service layer ready for API integration  

### Ready for Backend Integration
🔄 Authentication (replace mock with real)  
🔄 User operations (replace mock with real)  
🔄 Post operations (replace mock with real)  
🔄 Review operations (replace mock with real)  
🔄 Dashboard stats (replace mock with real)  

### Coming Soon (Placeholder)
⏳ Report management module  
⏳ Report analytics  
⏳ Report resolution workflow  

## 📖 Next Steps

1. **Verify Installation**: Run `npm run dev` and see the app
2. **Test Login**: Use demo credentials
3. **Explore UI**: Navigate all pages and features
4. **Review Code**: Read service layer to understand API integration
5. **Connect Backend**: Update service files to call real APIs
6. **Deploy**: Build and deploy to production server

## 🎓 Learning Resources

- **Ant Design**: https://ant.design/docs/react/introduce/
- **React Router**: https://reactrouter.com/
- **TypeScript**: https://www.typescriptlang.org/docs/
- **Vite**: https://vitejs.dev/guide/
- **Zustand**: https://github.com/pmndrs/zustand

## 💬 Support

If you encounter issues:
1. Check browser console for errors (F12)
2. Check terminal for error messages
3. Review the README.md file
4. Check this SETUP.md file
5. Verify Node.js version is 16+
6. Verify backend is running (if not using demo)

## 🎉 Ready to Go!

Your admin dashboard is ready. After running `npm run dev`, you can:
- Access the app at http://localhost:5173/
- Login with demo credentials
- Start managing your Activity Connection platform!

For questions about integrating with your Spring Boot backend, refer to the service layer documentation in README.md.

---

**Happy coding! 🚀**
