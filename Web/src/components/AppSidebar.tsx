import { Layout, Menu, Button, Divider, Space, Avatar, Tooltip } from 'antd'
import {
  DashboardOutlined,
  UserOutlined,
  FileTextOutlined,
  StarOutlined,
  AlertOutlined,
  LogoutOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import './Sidebar.css'

const { Sider } = Layout

interface SidebarProps {
  collapsed?: boolean
  onCollapse?: (collapsed: boolean) => void
}

export const AppSidebar: React.FC<SidebarProps> = ({ collapsed, onCollapse }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const { logout, user } = useAuthStore()

  const menuItems = [
    {
      key: '/dashboard',
      icon: <DashboardOutlined />,
      label: 'Bảng điều khiển',
    },
    {
      key: '/users',
      icon: <UserOutlined />,
      label: 'Người dùng',
    },
    {
      key: '/posts',
      icon: <FileTextOutlined />,
      label: 'Bài viết',
    },
    {
      key: '/reviews',
      icon: <StarOutlined />,
      label: 'Đánh giá',
    },
    {
      key: '/reports',
      icon: <AlertOutlined />,
      label: 'Báo cáo',
    },
  ]

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <Sider
      collapsed={collapsed}
      width={250}
      className="app-sider"
      style={{
        position: 'fixed',
        left: 0,
        top: 0,
        bottom: 0,
        overflow: 'auto',
      }}
    >
      <div className="sidebar-header">
        <Avatar size={40} icon={<UserOutlined />} className="sidebar-avatar" />
        {!collapsed && (
          <div className="sidebar-user-info">
            <div className="sidebar-user-name">Bảng điều khiển</div>
            <div className="sidebar-user-role">{user?.fullName || 'Quản trị viên'}</div>
          </div>
        )}
      </div>

      <Divider style={{ margin: '12px 0' }} />

      <Menu
        theme="light"
        mode="inline"
        selectedKeys={[location.pathname]}
        items={menuItems}
        onClick={(e) => navigate(e.key)}
        style={{ border: 'none' }}
      />

      <Divider style={{ margin: '12px 0' }} />

      <div className="sidebar-footer">
        <Tooltip title="Đăng xuất" placement="right">
          <Button
            type="primary"
            icon={<LogoutOutlined />}
            onClick={handleLogout}
            block
            size="large"
            style={{ height: '40px' }}
          >
            {!collapsed && 'Đăng xuất'}
          </Button>
        </Tooltip>
      </div>
    </Sider>
  )
}

export default AppSidebar
