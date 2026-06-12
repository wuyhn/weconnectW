import { useCallback, useEffect, useState } from 'react'
import { Avatar, Badge, Button, Divider, Dropdown, Empty, Layout, List, Popover, Tag, Tooltip, Typography } from 'antd'
import {
  BellOutlined,
  CheckOutlined,
  DownOutlined,
  FileTextOutlined,
  LockOutlined,
  LogoutOutlined,
  MenuFoldOutlined,
  MenuUnfoldOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import { useLocation, useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import { useAuthStore } from '../stores/authStore'
import ChangePasswordModal from './ChangePasswordModal'
import { notificationService, ReportNotification } from '../services/notificationService'
import './Topbar.css'

const { Header } = Layout
const { Text } = Typography

interface TopbarProps {
  collapsed?: boolean
  onToggleSidebar?: () => void
}

export const AppTopbar: React.FC<TopbarProps> = ({ collapsed = false, onToggleSidebar }) => {
  const navigate = useNavigate()
  const location = useLocation()
  const { logout } = useAuthStore()
  const [isChangePasswordModalOpen, setIsChangePasswordModalOpen] = useState(false)
  const [unreadCount, setUnreadCount] = useState(0)
  const [notifications, setNotifications] = useState<ReportNotification[]>([])
  const [notifOpen, setNotifOpen] = useState(false)
  const [notifLoading, setNotifLoading] = useState(false)

  const getPageTitle = () => {
    switch (location.pathname) {
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
      case '/chats':
        return 'Tin nhắn'
      default:
        return 'Bảng điều khiển'
    }
  }

  const handleLogout = () => {
    logout()
    navigate('/login')
  }

  const fetchUnreadCount = useCallback(async () => {
    try {
      const count = await notificationService.getUnreadCount()
      setUnreadCount(count)
    } catch {
      setUnreadCount(0)
    }
  }, [])

  const fetchNotifications = useCallback(async () => {
    setNotifLoading(true)
    try {
      const list = await notificationService.getReportNotifications()
      setNotifications(list)
    } catch {
      setNotifications([])
    } finally {
      setNotifLoading(false)
    }
  }, [])

  useEffect(() => {
    fetchUnreadCount()
    const interval = window.setInterval(fetchUnreadCount, 30000)
    // Refresh badge ngay khi một report được xử lý từ bất kỳ trang nào
    window.addEventListener('adminReportApproved', fetchUnreadCount)
    return () => {
      window.clearInterval(interval)
      window.removeEventListener('adminReportApproved', fetchUnreadCount)
    }
  }, [fetchUnreadCount])

  const handleNotifOpenChange = (open: boolean) => {
    setNotifOpen(open)
    if (open) {
      fetchNotifications()
    }
  }

  const handleMarkViewed = async (id: number) => {
    try {
      await notificationService.markAsViewed(id)
      setNotifications((prev) => prev.map((item) => (item.id === id ? { ...item, adminViewed: true } : item)))
      setUnreadCount((prev) => Math.max(0, prev - 1))
    } catch {
      return
    }
  }

  const handleMarkAllViewed = async () => {
    try {
      await notificationService.markAllAsViewed()
      setNotifications((prev) => prev.map((item) => ({ ...item, adminViewed: true })))
      setUnreadCount(0)
    } catch {
      return
    }
  }

  const handleNotifClick = (item: ReportNotification) => {
    if (!item.adminViewed) {
      handleMarkViewed(item.id)
    }
    setNotifOpen(false)
    navigate(`/admin/reports/${item.id}`)
  }

  const timeAgo = (isoStr: string) => {
    const date = dayjs(isoStr)
    if (!date.isValid()) return ''

    const minutes = dayjs().diff(date, 'minute')
    if (minutes < 1) return 'Vừa xong'
    if (minutes < 60) return `${minutes} phút trước`

    const hours = dayjs().diff(date, 'hour')
    if (hours < 24) return `${hours} giờ trước`

    const days = dayjs().diff(date, 'day')
    if (days < 30) return `${days} ngày trước`

    return date.format('DD/MM/YYYY')
  }

  const statusTag = (status: string) => {
    switch (status) {
      case 'PENDING':
        return <Tag color="orange">Chờ xử lý</Tag>
      case 'REVIEWED':
        return <Tag color="blue">Đã xem</Tag>
      case 'RESOLVED':
        return <Tag color="green">Đã xử lý</Tag>
      default:
        return null
    }
  }

  const notifContent = (
    <div className="notification-panel">
      <div className="notification-panel-head">
        <Text strong>
          <WarningOutlined />
          Báo cáo mới
        </Text>
        {unreadCount > 0 && (
          <Tooltip title="Đánh dấu tất cả đã xem">
            <Button type="link" size="small" icon={<CheckOutlined />} onClick={handleMarkAllViewed}>
              Xem tất cả
            </Button>
          </Tooltip>
        )}
      </div>
      <Divider className="notification-divider" />
      <div className="notification-list">
        {notifications.length === 0 && !notifLoading ? (
          <Empty description="Không có báo cáo" image={Empty.PRESENTED_IMAGE_SIMPLE} className="notification-empty" />
        ) : (
          <List
            loading={notifLoading}
            dataSource={notifications}
            renderItem={(item) => (
              <List.Item
                key={item.id}
                className={`notification-item ${item.adminViewed ? '' : 'notification-item-unread'}`}
                onClick={() => handleNotifClick(item)}
              >
                <List.Item.Meta
                  avatar={
                    <Avatar
                      size={34}
                      icon={item.targetType === 'USER' ? <UserOutlined /> : <FileTextOutlined />}
                      className="notification-avatar"
                    />
                  }
                  title={
                    <div className="notification-title-row">
                      <Text strong={!item.adminViewed} className="notification-title">
                        {item.reporterName} báo cáo {item.targetType === 'USER' ? 'người dùng' : 'bài viết'}
                        {item.targetName ? ` "${item.targetName}"` : ''}
                      </Text>
                      {statusTag(item.status)}
                    </div>
                  }
                  description={
                    <div className="notification-description">
                      <span>Lý do: {item.reason}</span>
                      <span>{timeAgo(item.createdAt)}</span>
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

  const displayName = 'Admin WeConnect'
  const avatarLetter = 'A'

  return (
    <Header className="app-header">
      <div className="topbar-left">
        <Button
          type="text"
          className="topbar-menu-button"
          icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={onToggleSidebar}
          aria-label="Thu gọn menu"
        />
        <h1 className="page-title">{getPageTitle()}</h1>
      </div>

      <div className="topbar-right">
        <Popover
          content={notifContent}
          trigger="click"
          open={notifOpen}
          onOpenChange={handleNotifOpenChange}
          placement="bottomRight"
          arrow={false}
          overlayInnerStyle={{ padding: 0, borderRadius: 16 }}
        >
          <Badge count={unreadCount} offset={[-3, 4]} size="small">
            <Button type="text" className="notification-button" icon={<BellOutlined />} />
          </Badge>
        </Popover>

        <Dropdown menu={{ items: userMenu }} trigger={['click']} placement="bottomRight">
          <button className="admin-profile" type="button">
            <Avatar size={42} className="admin-avatar">
              {avatarLetter}
            </Avatar>
            <span className="admin-name">{displayName}</span>
            <DownOutlined className="admin-dropdown-icon" />
          </button>
        </Dropdown>
      </div>

      <ChangePasswordModal isOpen={isChangePasswordModalOpen} onClose={() => setIsChangePasswordModalOpen(false)} />
    </Header>
  )
}

export default AppTopbar
