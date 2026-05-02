import { useEffect, useState, useCallback } from 'react'
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
  Drawer,
  Descriptions,
  Tag,
  Empty,
  Modal,
  Radio,
  Divider,
  Avatar,
} from 'antd'
import {
  SearchOutlined,
  EyeOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  ToolOutlined,
  UserOutlined,
} from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import LoadingState from '../components/LoadingState'
import { reportAdminService } from '../services/reportAdminService'
import { Report, PaginationParams, ReportFilter, PostTargetInfo, UserTargetInfo } from '../types'
import { useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import './ReviewsPage.css'

// Định nghĩa action theo target type
const POST_ACTIONS = [
  { value: 'WARN', label: '⚠️ Cảnh cáo người đăng', color: 'orange' },
  { value: 'HIDE_POST', label: '👁️ Ẩn bài viết', color: 'blue' },
  { value: 'DELETE_POST', label: '🗑️ Xóa bài viết', color: 'red' },
  { value: 'NO_VIOLATION', label: '✅ Đánh dấu không vi phạm', color: 'green' },
]

const USER_ACTIONS = [
  { value: 'WARN', label: '⚠️ Cảnh cáo người dùng', color: 'orange' },
  { value: 'BLOCK_USER', label: '🔒 Khóa tài khoản', color: 'red' },
  { value: 'DELETE_USER', label: '🗑️ Xóa tài khoản', color: 'red' },
  { value: 'NO_VIOLATION', label: '✅ Đánh dấu không vi phạm', color: 'green' },
]

export default function ReportsPage() {
  const [reports, setReports] = useState<Report[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [targetTypeFilter, setTargetTypeFilter] = useState<'USER' | 'POST' | null>(null)
  const [statusFilter, setStatusFilter] = useState<'PENDING' | 'REVIEWED' | 'RESOLVED' | null>(null)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedReport, setSelectedReport] = useState<Report | null>(null)
  const [drawerVisible, setDrawerVisible] = useState(false)

  // Action modal state
  const [actionModalVisible, setActionModalVisible] = useState(false)
  const [actionReport, setActionReport] = useState<Report | null>(null)
  const [selectedAction, setSelectedAction] = useState<string>('')
  const [resolving, setResolving] = useState(false)

  const [searchParams, setSearchParams] = useSearchParams()

  useEffect(() => {
    loadReports(1)
  }, [])

  useEffect(() => {
    loadReports(1)
  }, [search, targetTypeFilter, statusFilter])

  // Auto-open drawer when reportId query param is present
  const openReportById = useCallback(async (reportId: number) => {
    try {
      const report = await reportAdminService.getReport(reportId)
      setSelectedReport(report)
      setDrawerVisible(true)
    } catch {
      message.error('Không tìm thấy report')
    }
  }, [])

  useEffect(() => {
    const reportIdParam = searchParams.get('reportId')
    if (reportIdParam) {
      const id = parseInt(reportIdParam, 10)
      if (!isNaN(id)) {
        openReportById(id)
      }
      // Clear the param so it doesn't re-trigger
      setSearchParams({}, { replace: true })
    }
  }, [searchParams, openReportById, setSearchParams])

  const loadReports = async (page: number) => {
    try {
      setLoading(true)
      const params: PaginationParams = { page, pageSize: 10 }
      const filter: ReportFilter = {
        search,
        targetType: targetTypeFilter,
        status: statusFilter,
      }
      const result = await reportAdminService.getReports(params, filter)
      setReports(result.data)
      setPagination({ current: page, pageSize: 10, total: result.total })
    } catch (error) {
      message.error('Failed to load reports')
    } finally {
      setLoading(false)
    }
  }

  const openActionModal = (report: Report) => {
    setActionReport(report)
    setSelectedAction('')
    setActionModalVisible(true)
  }

  const handleResolve = async () => {
    if (!actionReport || !selectedAction) {
      message.warning('Vui lòng chọn hành động xử lý')
      return
    }

    try {
      setResolving(true)
      await reportAdminService.resolveReport(actionReport.id, selectedAction)
      message.success('Đã xử lý report thành công!')
      setActionModalVisible(false)
      setActionReport(null)
      setSelectedAction('')
      loadReports(pagination.current)
      if (selectedReport && selectedReport.id === actionReport.id) {
        setSelectedReport({ ...selectedReport, status: 'RESOLVED' })
      }
    } catch (error: any) {
      message.error(error?.message || 'Không thể xử lý report')
    } finally {
      setResolving(false)
    }
  }

  const getStatusColor = (status: string) => {
    const map: Record<string, string> = { PENDING: 'orange', REVIEWED: 'blue', RESOLVED: 'green' }
    return map[status] || 'default'
  }

  const getStatusLabel = (status: string) => {
    const map: Record<string, string> = { PENDING: 'Chờ xử lý', REVIEWED: 'Đã xem', RESOLVED: 'Đã giải quyết' }
    return map[status] || status
  }

  const getTargetTypeColor = (type: string) => {
    return type === 'USER' ? 'purple' : 'cyan'
  }

  // Truncate text helper
  const truncate = (text: string, maxLen: number) => {
    if (!text) return ''
    return text.length > maxLen ? text.substring(0, maxLen) + '...' : text
  }

  // Render preview column based on target type
  const renderTargetPreview = (record: Report) => {
    try {
      const info = record.targetInfo
      if (!info) return <span style={{ color: '#bfbfbf' }}>—</span>

      if (record.targetType === 'POST') {
        const postInfo = info as PostTargetInfo
        return (
          <div>
            <Tag style={{ marginBottom: 4 }}>{postInfo.interestTag || 'N/A'}</Tag>
            <div style={{ fontSize: 12, color: '#595959', lineHeight: '1.4' }}>
              {truncate(postInfo.content || '', 50)}
            </div>
          </div>
        )
      } else {
        const userInfo = info as UserTargetInfo
        return (
          <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
            <Avatar size={24} src={userInfo.avatarUrl} icon={<UserOutlined />} />
            <span style={{ fontWeight: 500 }}>{userInfo.fullName || 'Unknown'}</span>
          </div>
        )
      }
    } catch {
      return <span style={{ color: '#bfbfbf' }}>—</span>
    }
  }

  // Render full target detail in drawer
  const renderTargetDetail = (report: Report) => {
    try {
      const info = report.targetInfo
      if (!info) return <Empty description="Không tìm thấy thông tin target" />

      if (report.targetType === 'POST') {
        const postInfo = info as PostTargetInfo
        return (
          <div style={{ marginTop: 20 }}>
            <Divider orientation="left" style={{ fontWeight: 600, fontSize: 15 }}>
              📝 Bài viết bị báo cáo
            </Divider>

            {/* Visual Post Card — giống app */}
            <div style={{
              border: '1px solid #f0f0f0',
              borderRadius: 16,
              overflow: 'hidden',
              background: '#fff',
              boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
            }}>
              {/* Post Header — Author */}
              <div style={{
                display: 'flex',
                alignItems: 'center',
                gap: 12,
                padding: '14px 16px',
                borderBottom: '1px solid #f5f5f5',
              }}>
                <Avatar size={40} src={postInfo.authorName ? undefined : undefined} icon={<UserOutlined />} style={{ backgroundColor: '#1890ff' }} />
                <div style={{ flex: 1 }}>
                  <div style={{ fontWeight: 600, fontSize: 14 }}>
                    {postInfo.authorName || 'Unknown'}
                  </div>
                  <div style={{ fontSize: 12, color: '#8c8c8c' }}>
                    {postInfo.createdAt ? dayjs(postInfo.createdAt).format('DD/MM/YYYY • HH:mm') : '—'}
                  </div>
                </div>
                <Tag color={postInfo.archived ? 'red' : 'green'} style={{ margin: 0 }}>
                  {postInfo.archived ? 'Đã ẩn' : 'Đang hiển thị'}
                </Tag>
              </div>

              {/* Post Content */}
              <div style={{ padding: '14px 16px' }}>
                <p style={{
                  margin: 0,
                  fontSize: 14,
                  lineHeight: 1.6,
                  whiteSpace: 'pre-wrap',
                  color: '#262626',
                }}>
                  {postInfo.content || '—'}
                </p>
              </div>

              {/* Post Image */}
              {postInfo.imageUrl && (
                <div style={{ padding: '0 16px 12px' }}>
                  <img
                    src={postInfo.imageUrl}
                    alt="Post"
                    style={{
                      width: '100%',
                      maxHeight: 300,
                      objectFit: 'cover',
                      borderRadius: 12,
                      border: '1px solid #f0f0f0',
                    }}
                  />
                </div>
              )}

              {/* Post Metadata Chips */}
              <div style={{
                padding: '10px 16px',
                display: 'flex',
                flexWrap: 'wrap',
                gap: 8,
                borderTop: '1px solid #f5f5f5',
              }}>
                {postInfo.interestTag && (
                  <Tag color="blue" style={{ borderRadius: 12, padding: '2px 10px' }}>
                    🏷️ {postInfo.interestTag}
                  </Tag>
                )}
                {postInfo.location && (
                  <Tag style={{ borderRadius: 12, padding: '2px 10px', background: '#f6ffed', borderColor: '#b7eb8f' }}>
                    📍 {postInfo.location}
                  </Tag>
                )}
                {postInfo.startTime && (
                  <Tag style={{ borderRadius: 12, padding: '2px 10px', background: '#fff7e6', borderColor: '#ffd591' }}>
                    📅 {dayjs(postInfo.startTime).format('DD/MM/YYYY HH:mm')}
                  </Tag>
                )}
                {postInfo.maxMembers && (
                  <Tag style={{ borderRadius: 12, padding: '2px 10px', background: '#f9f0ff', borderColor: '#d3adf7' }}>
                    👥 Tối đa {postInfo.maxMembers} người
                  </Tag>
                )}
              </div>

              {/* Post Footer — ID */}
              <div style={{
                padding: '8px 16px',
                background: '#fafafa',
                borderTop: '1px solid #f0f0f0',
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: 12,
                color: '#8c8c8c',
              }}>
                <span>Post ID: {report.targetId}</span>
                <span>Author ID: {postInfo.authorId}</span>
              </div>
            </div>
          </div>
        )
      } else {
        // USER target — Profile card
        const userInfo = info as UserTargetInfo
        let tags: string[] = []
        if (Array.isArray(userInfo.interestTags)) {
          tags = userInfo.interestTags
        } else if (typeof userInfo.interestTags === 'string' && userInfo.interestTags) {
          tags = userInfo.interestTags.split(',')
        }
        const rating = typeof userInfo.averageRating === 'number' ? userInfo.averageRating.toFixed(1) : '0.0'

        return (
          <div style={{ marginTop: 20 }}>
            <Divider orientation="left" style={{ fontWeight: 600, fontSize: 15 }}>
              👤 User bị báo cáo
            </Divider>

            {/* Visual User Profile Card */}
            <div style={{
              border: '1px solid #f0f0f0',
              borderRadius: 16,
              overflow: 'hidden',
              background: '#fff',
              boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
            }}>
              {/* Profile Header */}
              <div style={{
                background: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
                padding: '24px 16px',
                textAlign: 'center',
              }}>
                <Avatar
                  size={72}
                  src={userInfo.avatarUrl}
                  icon={<UserOutlined />}
                  style={{ border: '3px solid #fff', boxShadow: '0 2px 8px rgba(0,0,0,0.2)' }}
                />
                <div style={{ color: '#fff', fontWeight: 600, fontSize: 18, marginTop: 10 }}>
                  {userInfo.fullName || 'Unknown'}
                </div>
                <div style={{ color: 'rgba(255,255,255,0.8)', fontSize: 13, marginTop: 2 }}>
                  {userInfo.email || '—'}
                </div>
                <div style={{ marginTop: 8 }}>
                  <Tag color={userInfo.isBlocked ? 'red' : 'green'} style={{ borderRadius: 12 }}>
                    {userInfo.isBlocked ? '🔒 Đã khóa' : '✅ Hoạt động'}
                  </Tag>
                </div>
              </div>

              {/* Profile Info */}
              <div style={{ padding: '16px' }}>
                {/* Bio */}
                {userInfo.bio && (
                  <div style={{
                    padding: '10px 14px',
                    background: '#fafafa',
                    borderRadius: 10,
                    marginBottom: 12,
                    fontSize: 13,
                    color: '#595959',
                    fontStyle: 'italic',
                  }}>
                    "{userInfo.bio}"
                  </div>
                )}

                {/* Stats Row */}
                <div style={{
                  display: 'flex',
                  justifyContent: 'space-around',
                  padding: '12px 0',
                  borderBottom: '1px solid #f5f5f5',
                  marginBottom: 12,
                }}>
                  <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 18, fontWeight: 700, color: '#faad14' }}>
                      {rating} ⭐
                    </div>
                    <div style={{ fontSize: 11, color: '#8c8c8c' }}>Rating</div>
                  </div>
                  <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 18, fontWeight: 700, color: '#1890ff' }}>
                      {userInfo.reputationScore ?? 0}
                    </div>
                    <div style={{ fontSize: 11, color: '#8c8c8c' }}>Uy tín</div>
                  </div>
                  <div style={{ textAlign: 'center' }}>
                    <div style={{ fontSize: 14, fontWeight: 600, color: '#595959' }}>
                      {userInfo.gender || '—'}
                    </div>
                    <div style={{ fontSize: 11, color: '#8c8c8c' }}>Giới tính</div>
                  </div>
                </div>

                {/* Interest Tags */}
                {tags.length > 0 && (
                  <div style={{ marginBottom: 12 }}>
                    <div style={{ fontSize: 12, color: '#8c8c8c', marginBottom: 6 }}>Sở thích</div>
                    <Space wrap size={4}>
                      {tags.map((tag: string) => (
                        <Tag key={tag} style={{ borderRadius: 12 }}>{tag.trim()}</Tag>
                      ))}
                    </Space>
                  </div>
                )}
              </div>

              {/* Footer */}
              <div style={{
                padding: '8px 16px',
                background: '#fafafa',
                borderTop: '1px solid #f0f0f0',
                display: 'flex',
                justifyContent: 'space-between',
                fontSize: 12,
                color: '#8c8c8c',
              }}>
                <span>User ID: {report.targetId}</span>
                <span>Tham gia: {userInfo.createdAt ? dayjs(userInfo.createdAt).format('DD/MM/YYYY') : '—'}</span>
              </div>
            </div>
          </div>
        )
      }
    } catch (e) {
      console.error('Error rendering target detail:', e)
      return <Empty description="Lỗi hiển thị thông tin target" />
    }
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 60,
      sorter: (a: Report, b: Report) => a.id - b.id,
    },
    {
      title: 'Reporter',
      key: 'reporter',
      width: 120,
      render: (_: any, record: Report) => (
        <div>
          <div style={{ fontWeight: 500 }}>{record.reporterName || 'Unknown'}</div>
          <div style={{ fontSize: '12px', color: '#8c8c8c' }}>ID: {record.reporterId}</div>
        </div>
      ),
    },
    {
      title: 'Target',
      key: 'target',
      width: 80,
      render: (_: any, record: Report) => (
        <div>
          <Tag color={getTargetTypeColor(record.targetType)}>{record.targetType}</Tag>
          <div style={{ fontSize: '12px', color: '#8c8c8c', marginTop: 4 }}>ID: {record.targetId}</div>
        </div>
      ),
    },
    {
      title: 'Preview',
      key: 'preview',
      width: 180,
      render: (_: any, record: Report) => renderTargetPreview(record),
    },
    {
      title: 'Reason',
      dataIndex: 'reason',
      key: 'reason',
      width: 140,
      render: (text: string) => (
        <div
          style={{ maxWidth: '160px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
          title={text}
        >
          {text}
        </div>
      ),
    },
    {
      title: 'Status',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: string) => (
        <Tag color={getStatusColor(status)} style={{ fontWeight: 600 }}>
          {getStatusLabel(status)}
        </Tag>
      ),
      sorter: (a: Report, b: Report) => a.status.localeCompare(b.status),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 110,
      render: (date: string) => dayjs(date).format('DD/MM/YYYY'),
      sorter: (a: Report, b: Report) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 180,
      fixed: 'right' as const,
      render: (_: any, record: Report) => (
        <Space size="small">
          <Button
            type="primary"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => {
              setSelectedReport(record)
              setDrawerVisible(true)
            }}
          >
            Chi tiết
          </Button>
          {record.status !== 'RESOLVED' && (
            <Button
              size="small"
              type="primary"
              style={{ backgroundColor: '#fa8c16', borderColor: '#fa8c16' }}
              icon={<ToolOutlined />}
              onClick={() => openActionModal(record)}
            >
              Xử lý
            </Button>
          )}
          {record.status === 'RESOLVED' && (
            <Tag color="green" style={{ margin: 0 }}>
              <CheckCircleOutlined /> Đã xử lý
            </Tag>
          )}
        </Space>
      ),
    },
  ]

  if (loading && reports.length === 0) {
    return <LoadingState fullPage message="Loading reports..." />
  }

  const currentActions = actionReport?.targetType === 'POST' ? POST_ACTIONS : USER_ACTIONS

  return (
    <MainLayout>
      <div className="reviews-page">
        <Card className="reviews-card">
          {/* Header */}
          <Row justify="space-between" align="middle" style={{ marginBottom: '24px' }}>
            <Col>
              <h2 style={{ margin: 0 }}>Report Management</h2>
            </Col>
            <Col>
              <Button icon={<ReloadOutlined />} onClick={() => loadReports(1)} loading={loading}>
                Refresh
              </Button>
            </Col>
          </Row>

          {/* Filters */}
          <Row gutter={[16, 16]} style={{ marginBottom: '24px' }} className="filters-row">
            <Col xs={24} sm={24} md={10}>
              <Input
                placeholder="Search by reason, description, or reporter"
                prefix={<SearchOutlined />}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                allowClear
              />
            </Col>
            <Col xs={12} sm={12} md={7}>
              <Select
                placeholder="Target Type"
                value={targetTypeFilter}
                onChange={setTargetTypeFilter}
                style={{ width: '100%' }}
                allowClear
                options={[
                  { label: <Tag color="purple">USER</Tag>, value: 'USER' },
                  { label: <Tag color="cyan">POST</Tag>, value: 'POST' },
                ]}
              />
            </Col>
            <Col xs={12} sm={12} md={7}>
              <Select
                placeholder="Status"
                value={statusFilter}
                onChange={setStatusFilter}
                style={{ width: '100%' }}
                allowClear
                options={[
                  { label: <Tag color="orange">Chờ xử lý</Tag>, value: 'PENDING' },
                  { label: <Tag color="blue">Đã xem</Tag>, value: 'REVIEWED' },
                  { label: <Tag color="green">Đã giải quyết</Tag>, value: 'RESOLVED' },
                ]}
              />
            </Col>
          </Row>

          {/* Table */}
          <Table
            columns={columns}
            dataSource={reports.map((r) => ({ ...r, key: r.id }))}
            loading={loading}
            pagination={{
              current: pagination.current,
              pageSize: pagination.pageSize,
              total: pagination.total,
              showSizeChanger: false,
              showTotal: (total) => `Total ${total} reports`,
              onChange: (page) => loadReports(page),
            }}
            scroll={{ x: 1200 }}
            locale={{
              emptyText: <Empty description="No reports found" style={{ marginTop: '48px' }} />,
            }}
          />
        </Card>

        {/* Report Detail Drawer — full context */}
        <Drawer
          title="Chi tiết Report"
          placement="right"
          onClose={() => { setDrawerVisible(false); setSelectedReport(null) }}
          open={drawerVisible}
          width={580}
          extra={
            selectedReport && selectedReport.status !== 'RESOLVED' && (
              <Button
                type="primary"
                style={{ backgroundColor: '#fa8c16', borderColor: '#fa8c16' }}
                icon={<ToolOutlined />}
                onClick={() => { setDrawerVisible(false); openActionModal(selectedReport) }}
              >
                Xử lý
              </Button>
            )
          }
        >
          {selectedReport && (
            <div>
              {/* Report Info */}
              <Descriptions bordered size="small" layout="vertical" column={1}>
                <Descriptions.Item label="Report ID">{selectedReport.id}</Descriptions.Item>
                <Descriptions.Item label="Reporter">
                  {selectedReport.reporterName || 'Unknown'} (ID: {selectedReport.reporterId})
                </Descriptions.Item>
                <Descriptions.Item label="Lý do">{selectedReport.reason}</Descriptions.Item>
                <Descriptions.Item label="Mô tả">
                  <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
                    {selectedReport.description || 'Không có mô tả'}
                  </p>
                </Descriptions.Item>
                <Descriptions.Item label="Trạng thái">
                  <Tag color={getStatusColor(selectedReport.status)} style={{ fontWeight: 600 }}>
                    {getStatusLabel(selectedReport.status)}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="Ngày tạo">
                  {dayjs(selectedReport.createdAt).format('DD/MM/YYYY HH:mm')}
                </Descriptions.Item>
                {selectedReport.reviewedAt && (
                  <Descriptions.Item label="Ngày xử lý">
                    {dayjs(selectedReport.reviewedAt).format('DD/MM/YYYY HH:mm')}
                  </Descriptions.Item>
                )}
              </Descriptions>

              {/* Target full detail */}
              {renderTargetDetail(selectedReport)}
            </div>
          )}
        </Drawer>

        {/* Action Modal */}
        <Modal
          title={
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <ToolOutlined style={{ color: '#fa8c16' }} />
              <span>Xử lý Report #{actionReport?.id}</span>
            </div>
          }
          open={actionModalVisible}
          onCancel={() => { setActionModalVisible(false); setActionReport(null); setSelectedAction('') }}
          onOk={handleResolve}
          okText="Xác nhận xử lý"
          cancelText="Huỷ"
          confirmLoading={resolving}
          okButtonProps={{
            disabled: !selectedAction,
            danger: ['DELETE_POST', 'DELETE_USER', 'BLOCK_USER'].includes(selectedAction),
          }}
          width={500}
        >
          {actionReport && (
            <div>
              <div style={{ background: '#fafafa', padding: '12px 16px', borderRadius: 8, marginBottom: 16 }}>
                <div style={{ marginBottom: 4 }}>
                  <strong>Target:</strong>{' '}
                  <Tag color={getTargetTypeColor(actionReport.targetType)}>{actionReport.targetType}</Tag>
                  <span style={{ color: '#8c8c8c' }}>ID: {actionReport.targetId}</span>
                </div>
                <div style={{ marginBottom: 4 }}>
                  <strong>Lý do:</strong> {actionReport.reason}
                </div>
                {actionReport.description && (
                  <div style={{ color: '#595959' }}>
                    <strong>Mô tả:</strong> {actionReport.description}
                  </div>
                )}
              </div>

              <Divider style={{ margin: '12px 0' }} />
              <div style={{ marginBottom: 8, fontWeight: 600 }}>Chọn hành động xử lý:</div>

              <Radio.Group
                value={selectedAction}
                onChange={(e) => setSelectedAction(e.target.value)}
                style={{ display: 'flex', flexDirection: 'column', gap: 12 }}
              >
                {currentActions.map((act) => (
                  <Radio
                    key={act.value}
                    value={act.value}
                    style={{
                      padding: '10px 16px',
                      border: '1px solid',
                      borderColor: selectedAction === act.value ? '#1890ff' : '#d9d9d9',
                      borderRadius: 8,
                      background: selectedAction === act.value ? '#e6f7ff' : '#fff',
                      transition: 'all 0.2s',
                    }}
                  >
                    <span style={{ fontSize: 14 }}>{act.label}</span>
                  </Radio>
                ))}
              </Radio.Group>

              {['DELETE_POST', 'DELETE_USER', 'BLOCK_USER'].includes(selectedAction) && (
                <div style={{
                  marginTop: 16, padding: '8px 12px', background: '#fff2e8',
                  border: '1px solid #ffbb96', borderRadius: 6, color: '#d4380d', fontSize: 13,
                }}>
                  ⚠️ Hành động này không thể hoàn tác. Vui lòng xác nhận trước khi thực hiện.
                </div>
              )}
            </div>
          )}
        </Modal>
      </div>
    </MainLayout>
  )
}
