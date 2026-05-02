import { BrowserRouter as Router } from 'react-router-dom'
import { App as AntApp } from 'antd'
import { useAuthStore } from './stores/authStore'
import AppRoutes from './routes'

function App() {
  const { token, user } = useAuthStore()
  // Phải có token VÀ role = 1 (ADMIN) mới được truy cập
  const isAdmin = !!token && user?.role === 1

  return (
    <AntApp>
      <Router>
        <AppRoutes isAuthenticated={isAdmin} />
      </Router>
    </AntApp>
  )
}

export default App
