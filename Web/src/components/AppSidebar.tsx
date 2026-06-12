import { Button, Layout, Menu, Tooltip } from 'antd'
import {
  AlertOutlined,
  DashboardOutlined,
  FileTextOutlined,
  LogoutOutlined,
  StarOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import './Sidebar.css'

const { Sider } = Layout

interface SidebarProps {
  collapsed?: boolean
  onCollapse?: (collapsed: boolean) => void
}

export const AppSidebar: React.FC<SidebarProps> = ({ collapsed = false, onCollapse }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const { logout } = useAuthStore()

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

  const selectedKey =
    menuItems.find((item) => location.pathname === item.key || location.pathname.startsWith(`/admin${item.key}`))
      ?.key || '/dashboard'

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  return (
    <Sider
      collapsed={collapsed}
      collapsedWidth={88}
      width={250}
      trigger={null}
      collapsible
      onCollapse={onCollapse}
      className="app-sider"
    >
      <div className="sidebar-brand" onClick={() => navigate('/dashboard')} role="button" tabIndex={0}>
        <span className="sidebar-brand-icon">
          <TeamOutlined />
        </span>
        {!collapsed && <span className="sidebar-brand-text">WeConnect</span>}
      </div>

      <Menu
        theme="light"
        mode="inline"
        selectedKeys={[selectedKey]}
        items={menuItems}
        onClick={(event) => navigate(event.key)}
        className="sidebar-menu"
      />

      <div className="sidebar-footer">
        <Tooltip title={collapsed ? 'Đăng xuất' : ''} placement="right">
          <Button className="logout-button" icon={<LogoutOutlined />} onClick={handleLogout} block>
            {!collapsed && 'Đăng xuất'}
          </Button>
        </Tooltip>
      </div>
    </Sider>
  )
}

export default AppSidebar
