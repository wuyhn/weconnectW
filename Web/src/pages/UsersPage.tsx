import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Card,
  Table,
  Button,
  Space,
  Input,
  Select,
  Row,
  Col,
  message,
  Popconfirm,
  Drawer,
  Descriptions,
  Tag,
  Avatar,
  Empty,
} from 'antd'
import {
  SearchOutlined,
  DeleteOutlined,
  EyeOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import LoadingState from '../components/LoadingState'
import StatusBadge from '../components/StatusBadge'
import ConfirmActionModal from '../components/ConfirmActionModal'
import { userAdminService } from '../services/userAdminService'
import { postAdminService } from '../services/postAdminService'
import { User, PaginationParams } from '../types'
import dayjs from 'dayjs'
import './UsersPage.css'

export default function UsersPage() {
  const [searchParams] = useSearchParams()
  const [users, setUsers] = useState<User[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [roleFilter, setRoleFilter] = useState<number | null>(null)
  const [blockedFilter, setBlockedFilter] = useState<boolean | null>(null)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedUser, setSelectedUser] = useState<User | null>(null)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [modalConfig, setModalConfig] = useState<any>(null)
  const [actionLoading, setActionLoading] = useState(false)
  const [userPostCount, setUserPostCount] = useState<Record<number, number>>({})  // Map userId to postCount

  useEffect(() => {
    loadUsers(1)
    loadPostCount()
  }, [search, roleFilter, blockedFilter])

  useEffect(() => {
    // Handle query params from dashboard
    const fromDashboard = searchParams.get('from') === 'dashboard'
    const sortParam = searchParams.get('sort')
    if (fromDashboard) {
      // Show indication that we're coming from dashboard (optional)
      // Could set a banner or highlight
    }
    // Sort parameter can be used if needed: createdAt_desc
  }, [searchParams])

  const loadPostCount = async () => {
    try {
      const result = await postAdminService.getPosts({ page: 1, pageSize: 10000 })
      const counts: Record<number, number> = {}
      
      result.data.forEach((post) => {
        counts[post.authorId] = (counts[post.authorId] || 0) + 1
      })
      
      setUserPostCount(counts)
    } catch (error) {
      console.error('Failed to load post counts', error)
    }
  }

  const loadUsers = async (page: number) => {
    try {
      setLoading(true)
      const params: PaginationParams = { page, pageSize: 10 }
      const result = await userAdminService.getUsers(params, {
        search,
        role: roleFilter,
        isBlocked: blockedFilter,
      })
      setUsers(result.data)
      setPagination({ current: page, pageSize: 10, total: result.total })
    } catch (error) {
      message.error('Failed to load users')
    } finally {
      setLoading(false)
    }
  }

  const handleDeleteUser = async (userId: number) => {
    try {
      setActionLoading(true)
      await userAdminService.deleteUser(userId)
      message.success('User deleted successfully')
      loadUsers(pagination.current)
      setModalConfig(null)
    } catch (error: any) {
      message.error(error?.message || 'Failed to delete user')
    } finally {
      setActionLoading(false)
    }
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
      sorter: (a: User, b: User) => a.id - b.id,
    },
    {
      title: 'Posts',
      dataIndex: 'id',
      key: 'postCount',
      width: 80,
      render: (userId: number) => {
        const count = userPostCount[userId] || 0
        return <strong>{count}</strong>
      },
      sorter: (a: User, b: User) => {
        const countA = userPostCount[a.id] || 0
        const countB = userPostCount[b.id] || 0
        return countA - countB
      },
    },
    {
      title: 'Name',
      dataIndex: 'fullName',
      key: 'fullName',
      width: 180,
      render: (text: string) => <span className="user-name">{text}</span>,
    },
    {
      title: 'Email',
      dataIndex: 'email',
      key: 'email',
      width: 200,
    },
    {
      title: 'Gender',
      dataIndex: 'gender',
      key: 'gender',
      width: 80,
      render: (gender: string) => gender || '-',
    },
    {
      title: 'Rating',
      dataIndex: 'averageRating',
      key: 'averageRating',
      width: 100,
      render: (rating: number) => (
        <span>{rating?.toFixed(1) || 0} ⭐</span>
      ),
      sorter: (a: User, b: User) => (a.averageRating || 0) - (b.averageRating || 0),
    },
    {
      title: 'Reputation',
      dataIndex: 'reputationScore',
      key: 'reputationScore',
      width: 100,
      sorter: (a: User, b: User) => (a.reputationScore || 0) - (b.reputationScore || 0),
    },
    {
      title: 'Role',
      dataIndex: 'role',
      key: 'role',
      width: 80,
      render: (role: number) => (
        <Tag color={role === 1 ? 'blue' : 'default'}>
          {role === 1 ? 'Admin' : 'User'}
        </Tag>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'isBlocked',
      key: 'isBlocked',
      width: 100,
      render: (isBlocked: boolean) => (
        <StatusBadge
          status={isBlocked ? 'blocked' : 'active'}
          text={isBlocked ? 'Blocked' : 'Active'}
        />
      ),
    },
    {
      title: 'Joined',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 120,
      render: (date: string) => dayjs(date).format('MMM DD, YYYY'),
      sorter: (a: User, b: User) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 150,
      fixed: 'right' as const,
      render: (_: any, record: User) => (
        <Space size="small">
          <Button
            type="primary"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => {
              setSelectedUser(record)
              setDrawerVisible(true)
            }}
          >
            View
          </Button>
          <Popconfirm
            title="Delete User"
            description={`Are you sure you want to delete ${record.fullName}? This action cannot be undone.`}
            onConfirm={() => handleDeleteUser(record.id)}
            okText="Yes"
            cancelText="No"
            okButtonProps={{ danger: true }}
          >
            <Button danger size="small" icon={<DeleteOutlined />}>Delete</Button>
          </Popconfirm>
        </Space>
      ),
    },
  ]

  if (loading && users.length === 0) {
    return <LoadingState fullPage message="Loading users..." />
  }

  return (
    <MainLayout>
      <div className="users-page">
        <Card className="users-card">
          {/* Header with title and actions */}
          <Row justify="space-between" align="middle" style={{ marginBottom: '24px' }}>
            <Col>
              <h2 style={{ margin: 0 }}>User Management</h2>
            </Col>
            <Col>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => loadUsers(1)}
                loading={loading}
              >
                Refresh
              </Button>
            </Col>
          </Row>

          {/* Filters and Search */}
          <Row gutter={[16, 16]} style={{ marginBottom: '24px' }} className="filters-row">
            <Col xs={24} sm={24} md={8}>
              <Input
                placeholder="Search by name or email"
                prefix={<SearchOutlined />}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                allowClear
              />
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Select
                placeholder="Filter by role"
                value={roleFilter}
                onChange={setRoleFilter}
                style={{ width: '100%' }}
                allowClear
                options={[
                  { label: 'User', value: 0 },
                  { label: 'Admin', value: 1 },
                ]}
              />
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Select
                placeholder="Filter by status"
                value={blockedFilter}
                onChange={setBlockedFilter}
                style={{ width: '100%' }}
                allowClear
                options={[
                  { label: 'Active', value: false },
                  { label: 'Blocked', value: true },
                ]}
              />
            </Col>
          </Row>

          {/* Table */}
          <Table
            columns={columns}
            dataSource={users.map((u) => ({ ...u, key: u.id }))}
            loading={loading}
            pagination={{
              current: pagination.current,
              pageSize: pagination.pageSize,
              total: pagination.total,
              showSizeChanger: false,
              showTotal: (total) => `Total ${total} users`,
              onChange: (page) => loadUsers(page),
            }}
            scroll={{ x: 1200 }}
            locale={{
              emptyText: (
                <Empty description="No users found" style={{ marginTop: '48px' }} />
              ),
            }}
          />
        </Card>

        {/* User Detail Drawer */}
        <Drawer
          title="User Details"
          placement="right"
          onClose={() => {
            setDrawerVisible(false)
            setSelectedUser(null)
          }}
          open={drawerVisible}
          width={500}
        >
          {selectedUser && (
            <div className="user-detail">
              <div style={{ textAlign: 'center', marginBottom: '24px' }}>
                <Avatar size={80} src={selectedUser.avatarUrl} />
              </div>
              <Descriptions bordered size="small" layout="vertical">
                <Descriptions.Item label="ID">{selectedUser.id}</Descriptions.Item>
                <Descriptions.Item label="Full Name">
                  {selectedUser.fullName}
                </Descriptions.Item>
                <Descriptions.Item label="Email">{selectedUser.email}</Descriptions.Item>
                <Descriptions.Item label="Birthday">
                  {selectedUser.birthday ? dayjs(selectedUser.birthday).format('MMM DD, YYYY') : '-'}
                </Descriptions.Item>
                <Descriptions.Item label="Gender">{selectedUser.gender || '-'}</Descriptions.Item>
                <Descriptions.Item label="Bio">{selectedUser.bio || '-'}</Descriptions.Item>
                <Descriptions.Item label="Interest Tags">
                  {selectedUser.interestTags && selectedUser.interestTags.length > 0 ? (
                    <Space wrap>
                      {selectedUser.interestTags.map((tag) => (
                        <Tag key={tag}>{tag}</Tag>
                      ))}
                    </Space>
                  ) : (
                    '-'
                  )}
                </Descriptions.Item>
                <Descriptions.Item label="Average Rating">
                  {selectedUser.averageRating?.toFixed(2) || 0} ⭐
                </Descriptions.Item>
                <Descriptions.Item label="Reputation Score">
                  {selectedUser.reputationScore || 0}
                </Descriptions.Item>
                <Descriptions.Item label="Total Posts">
                  <strong>{userPostCount[selectedUser.id] || 0}</strong>
                </Descriptions.Item>
                <Descriptions.Item label="Role">
                  <Tag color={selectedUser.role === 1 ? 'blue' : 'default'}>
                    {selectedUser.role === 1 ? 'Admin' : 'User'}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="Status">
                  <StatusBadge
                    status={selectedUser.isBlocked ? 'blocked' : 'active'}
                    text={selectedUser.isBlocked ? 'Blocked' : 'Active'}
                  />
                </Descriptions.Item>
                <Descriptions.Item label="Joined">
                  {dayjs(selectedUser.createdAt).format('MMMM DD, YYYY')}
                </Descriptions.Item>
              </Descriptions>
            </div>
          )}
        </Drawer>

        {/* Confirm Action Modal */}
        {modalConfig && (
          <ConfirmActionModal
            title={modalConfig.title}
            message={modalConfig.message}
            onConfirm={modalConfig.onConfirm}
            onCancel={() => setModalConfig(null)}
            loading={actionLoading}
            type={modalConfig.title.includes('Delete') ? 'error' : 'warning'}
          />
        )}
      </div>
    </MainLayout>
  )
}
