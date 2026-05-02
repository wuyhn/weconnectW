import { useState, useEffect, useCallback } from 'react'
import { Layout, Avatar, Dropdown, Space, Button, Badge, Popover, List, Typography, Divider, Tooltip, Empty, Tag } from 'antd'
import {
  UserOutlined,
  LogoutOutlined,
  BellOutlined,
  LockOutlined,
  CheckOutlined,
  ExclamationCircleOutlined,
} from '@ant-design/icons'
import { useNavigate, useLocation } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import ChangePasswordModal from './ChangePasswordModal'
import { GlobalSearch } from './GlobalSearch'
import { notificationService, ReportNotification } from '../services/notificationService'
import './Topbar.css'

const { Header } = Layout
const { Text } = Typography

interface TopbarProps {
  collapsed?: boolean
}

export const AppTopbar: React.FC<TopbarProps> = ({ collapsed }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const { logout, user } = useAuthStore()
  const [isChangePasswordModalOpen, setIsChangePasswordModalOpen] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [notifications, setNotifications] = useState<ReportNotification[]>([])
  const [notifOpen, setNotifOpen] = useState(false)
  const [notifLoading, setNotifLoading] = useState(false)

  // Get page title from current route
  const getPageTitle = () => {
    const path = location.pathname
    switch (path) {
      case '/dashboard':
        return 'Bảng điều khiển'
      case '/users':
        return 'Quản lý người dùng'
      case '/posts':
        return 'Quản lý bài viết'
      case '/reviews':
        return 'Quản lý đánh giá'
      case '/reports':
        return 'Báo cáo'
      default:
        return 'Bảng điều khiển Admin'
    }
  }

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  // Fetch unread count
  const fetchUnreadCount = useCallback(async () => {
    try {
      const count = await notificationService.getUnreadCount()
      setUnreadCount(count)
    } catch {
      // Ignore errors
    }
  }, [])

  // Fetch report notifications
  const fetchNotifications = useCallback(async () => {
    setNotifLoading(true)
    try {
      const list = await notificationService.getReportNotifications()
      setNotifications(list)
    } catch {
      // Ignore errors
    } finally {
      setNotifLoading(false)
    }
  }, [])

  // Poll unread count every 30s
  useEffect(() => {
    fetchUnreadCount()
    const interval = setInterval(fetchUnreadCount, 30000)
    return () => clearInterval(interval)
  }, [fetchUnreadCount])

  // Load notifications when popover opens
  const handleNotifOpenChange = (open: boolean) => {
    setNotifOpen(open)
    if (open) {
      fetchNotifications()
    }
  }

  // Mark single report as viewed
  const handleMarkViewed = async (id: number) => {
    try {
      await notificationService.markAsViewed(id)
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, adminViewed: true } : n))
      )
      setUnreadCount((prev) => Math.max(0, prev - 1))
    } catch {
      // Ignore
    }
  }

  // Mark all as viewed
  const handleMarkAllViewed = async () => {
    try {
      await notificationService.markAllAsViewed()
      setNotifications((prev) => prev.map((n) => ({ ...n, adminViewed: true })))
      setUnreadCount(0)
    } catch {
      // Ignore
    }
  }

  // Click notification -> mark as viewed + navigate to report detail
  const handleNotifClick = (item: ReportNotification) => {
    if (!item.adminViewed) {
      handleMarkViewed(item.id)
    }
    setNotifOpen(false)
    navigate(`/reports?reportId=${item.id}`)
  }

  // Format time ago
  const timeAgo = (isoStr: string) => {
    try {
      const diff = Date.now() - new Date(isoStr).getTime()
      const mins = Math.floor(diff / 60000)
      if (mins < 1) return 'Vừa xong'
      if (mins < 60) return `${mins} phút trước`
      const hrs = Math.floor(mins / 60)
      if (hrs < 24) return `${hrs} giờ trước`
      const days = Math.floor(hrs / 24)
      if (days < 30) return `${days} ngày trước`
      return new Date(isoStr).toLocaleDateString('vi-VN')
    } catch {
      return ''
    }
  }

  // Status tag
  const statusTag = (status: string) => {
    switch (status) {
      case 'PENDING': return <Tag color="orange" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>Chờ xử lý</Tag>
      case 'REVIEWED': return <Tag color="blue" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>Đã xem</Tag>
      case 'RESOLVED': return <Tag color="green" style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px' }}>Đã xử lý</Tag>
      default: return null
    }
  }

  // Notification panel content
  const notifContent = (
    <div style={{ width: 400, maxHeight: 500, overflow: 'hidden', display: 'flex', flexDirection: 'column' }}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 16px 8px' }}>
        <Text strong style={{ fontSize: 16 }}>
          <ExclamationCircleOutlined style={{ marginRight: 8, color: '#ff4d4f' }} />
          Báo cáo mới
        </Text>
        {unreadCount > 0 && (
          <Tooltip title="Đánh dấu tất cả đã xem">
            <Button
              type="link"
              size="small"
              icon={<CheckOutlined />}
              onClick={handleMarkAllViewed}
            >
              Xem tất cả
            </Button>
          </Tooltip>
        )}
      </div>
      <Divider style={{ margin: '0 0 4px' }} />
      <div style={{ overflowY: 'auto', maxHeight: 430 }}>
        {notifications.length === 0 && !notifLoading ? (
          <Empty
            description="Không có báo cáo"
            image={Empty.PRESENTED_IMAGE_SIMPLE}
            style={{ padding: '40px 0' }}
          />
        ) : (
          <List
            loading={notifLoading}
            dataSource={notifications}
            renderItem={(item) => (
              <List.Item
                key={item.id}
                style={{
                  padding: '10px 16px',
                  cursor: 'pointer',
                  background: item.adminViewed ? 'transparent' : 'rgba(255, 77, 79, 0.04)',
                  borderLeft: item.adminViewed ? 'none' : '3px solid #ff4d4f',
                  transition: 'background 0.2s',
                }}
                onClick={() => handleNotifClick(item)}
              >
                <List.Item.Meta
                  avatar={
                    <span style={{ fontSize: 18 }}>
                      {item.targetType === 'USER' ? '👤' : '📝'}
                    </span>
                  }
                  title={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
                      <Text
                        strong={!item.adminViewed}
                        style={{ fontSize: 13, lineHeight: '1.4', flex: 1 }}
                      >
                        {item.reporterName} báo cáo{' '}
                        {item.targetType === 'USER' ? 'người dùng' : 'bài viết'}
                        {item.targetName ? ` "${item.targetName}"` : ''}
                      </Text>
                      {statusTag(item.status)}
                    </div>
                  }
                  description={
                    <div>
                      <Text type="secondary" style={{ fontSize: 12 }}>
                        Lý do: {item.reason}
                      </Text>
                      <br />
                      <Text type="secondary" style={{ fontSize: 11 }}>
                        {timeAgo(item.createdAt)}
                      </Text>
                    </div>
                  }
                />
              </List.Item>
            )}
          />
        )}
      </div>
    </div>
  )

  const userMenu = [
    {
      key: 'changePassword',
      icon: <LockOutlined />,
      label: 'Đổi mật khẩu',
      onClick: () => setIsChangePasswordModalOpen(true),
    },
    {
      type: 'divider' as const,
    },
    {
      key: 'logout',
      icon: <LogoutOutlined />,
      label: 'Đăng xuất',
      onClick: handleLogout,
      danger: true,
    },
  ]

  return (
    <Header
      className="app-header"
      style={{
        background: '#ffffff',
        boxShadow: '0 1px 4px rgba(0, 0, 0, 0.08)',
        padding: '0 24px',
        position: 'sticky',
        top: 0,
        zIndex: 999,
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        height: 64,
      }}
    >
      <div className="topbar-left">
        <h1 className="page-title">{getPageTitle()}</h1>
      </div>

      <div className="topbar-center">
        <GlobalSearch />
      </div>

      <div className="topbar-right">
        <Space size="large">
          <Popover
            content={notifContent}
            trigger="click"
            open={notifOpen}
            onOpenChange={handleNotifOpenChange}
            placement="bottomRight"
            arrow={false}
            overlayInnerStyle={{ padding: 0, borderRadius: 12 }}
          >
            <Badge count={unreadCount} offset={[-5, 5]}>
              <Button
                type="text"
                size="large"
                icon={<BellOutlined style={{ fontSize: '18px' }} />}
              />
            </Badge>
          </Popover>

          <Dropdown
            menu={{ items: userMenu }}
            trigger={['click']}
            placement="bottomRight"
          >
            <div className="user-avatar-section">
              <Avatar
                size={36}
                icon={<UserOutlined />}
                style={{
                  backgroundColor: '#1890ff',
                  cursor: 'pointer',
                }}
              />
              <span className="user-name">{user?.fullName || 'Admin'}</span>
            </div>
          </Dropdown>
        </Space>
      </div>

      <ChangePasswordModal
        isOpen={isChangePasswordModalOpen}
        onClose={() => setIsChangePasswordModalOpen(false)}
      />
    </Header>
  )
}

export default AppTopbar
