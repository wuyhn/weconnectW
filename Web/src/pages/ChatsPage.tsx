import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { Avatar, Badge, Button, Dropdown, Empty, List, Spin, Typography } from 'antd'
import { EyeOutlined, MoreOutlined, TeamOutlined, UserOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import MainLayout from '../components/MainLayout'
import { chatService, ChatRoom } from '../services/chatService'

const { Text } = Typography

const TYPE_LABELS: Record<string, string> = {
  DIRECT: 'Nhắn tin',
  FRIEND_GROUP: 'Nhóm bạn bè',
  ACTIVITY: 'Nhóm hoạt động',
}

function roomIcon(type: string) {
  if (type === 'DIRECT') return <UserOutlined />
  return <TeamOutlined />
}

function formatTime(iso: string) {
  if (!iso) return ''
  const d = dayjs(iso)
  if (!d.isValid()) return ''
  const diffMins = dayjs().diff(d, 'minute')
  if (diffMins < 1) return 'Vừa xong'
  if (diffMins < 60) return `${diffMins} phút trước`
  const diffHours = dayjs().diff(d, 'hour')
  if (diffHours < 24) return `${diffHours} giờ trước`
  return d.format('DD/MM/YYYY')
}

export default function ChatsPage() {
  const navigate = useNavigate()
  const [rooms, setRooms] = useState<ChatRoom[]>([])
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const load = async () => {
      setLoading(true)
      const data = await chatService.getRooms()
      setRooms(data)
      setLoading(false)
      // Notify sidebar to refresh badge
      window.dispatchEvent(new Event('chatMessageRead'))
    }
    load()
  }, [])

  return (
    <MainLayout>
      <div style={{ maxWidth: 680, margin: '0 auto', padding: '24px 0' }}>
        {loading ? (
          <div style={{ textAlign: 'center', padding: 48 }}>
            <Spin size="large" />
          </div>
        ) : rooms.length === 0 ? (
          <Empty description="Không có tin nhắn nào" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <List
            dataSource={rooms}
            renderItem={(room) => (
              <List.Item
                key={room.id}
                style={{
                  padding: '12px 16px',
                  borderRadius: 12,
                  marginBottom: 4,
                  cursor: 'default',
                  background: room.unreadCount > 0 ? '#fff8fb' : '#fff',
                  border: '1px solid',
                  borderColor: room.unreadCount > 0 ? '#ffd6e5' : '#f0f0f0',
                  alignItems: 'center',
                }}
              >
                <List.Item.Meta
                  avatar={
                    <Badge count={room.unreadCount} size="small" offset={[-4, 4]}>
                      <Avatar
                        size={44}
                        src={room.otherUserAvatarUrl || undefined}
                        icon={roomIcon(room.type)}
                        style={{ background: '#ffeef4', color: '#ee4778' }}
                      />
                    </Badge>
                  }
                  title={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Text strong={room.unreadCount > 0} style={{ flex: 1 }}>
                        {room.title}
                      </Text>
                      <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                        {formatTime(room.lastMessageTime)}
                      </Text>
                    </div>
                  }
                  description={
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <Text
                        type="secondary"
                        ellipsis
                        style={{ flex: 1, fontWeight: room.unreadCount > 0 ? 600 : 400, color: room.unreadCount > 0 ? '#303745' : undefined }}
                      >
                        {room.lastMessagePreview || 'Chưa có tin nhắn'}
                      </Text>
                      <Text type="secondary" style={{ fontSize: 12, whiteSpace: 'nowrap' }}>
                        {TYPE_LABELS[room.type] || room.type}
                      </Text>
                    </div>
                  }
                />
                {room.type === 'ACTIVITY' && room.postId && (
                  <Dropdown
                    trigger={['click']}
                    placement="bottomRight"
                    menu={{
                      items: [
                        {
                          key: 'viewPost',
                          icon: <EyeOutlined />,
                          label: 'Xem chi tiết bài viết',
                          onClick: () => navigate(`/admin/posts/${room.postId}`),
                        },
                      ],
                    }}
                  >
                    <Button
                      size="small"
                      type="text"
                      icon={<MoreOutlined />}
                      onClick={(e) => e.stopPropagation()}
                      style={{ marginLeft: 8, flexShrink: 0 }}
                    />
                  </Dropdown>
                )}
              </List.Item>
            )}
          />
        )}
      </div>
    </MainLayout>
  )
}
