import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Avatar,
  Button,
  Drawer,
  Empty,
  Image,
  Input,
  Select,
  Skeleton,
  Table,
  Tag,
  Typography,
  message,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
  CloseOutlined,
  ExclamationCircleOutlined,
  EyeOutlined,
  FileImageOutlined,
  FileTextOutlined,
  PictureOutlined,
  ReloadOutlined,
  SafetyCertificateOutlined,
  SearchOutlined,
  TagsOutlined,
  ToolOutlined,
  UserOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import dayjs from 'dayjs'
import MainLayout from '../components/MainLayout'
import ConfirmActionModal from '../components/ConfirmActionModal'
import { useDebouncedValue } from '../hooks/useDebouncedValue'
import { reportAdminService } from '../services/reportAdminService'
import { PostTargetInfo, Report, ReportFilter, UserTargetInfo } from '../types'
import { resolveAvatarUrl } from '../utils/avatar'
import { cleanTagText } from '../utils/text'
import './ReportsPage.css'

const { Paragraph } = Typography

const PAGE_SIZE = 8

type TargetTypeFilter = 'all' | 'USER' | 'POST'
type StatusGroupFilter = 'all' | 'OPEN' | 'CLOSED'

const ACTION_LABEL: Record<string, string> = {
  NO_VIOLATION: 'Không vi phạm',
  WARN: 'Cảnh cáo',
  HIDE_POST: 'Ẩn bài viết',
  DELETE_POST: 'Xóa bài viết',
  BLOCK_USER: 'Khóa tài khoản',
  DELETE_USER: 'Xóa tài khoản',
}

const normalizeDate = (value: unknown): dayjs.Dayjs | null => {
  if (Array.isArray(value)) {
    const [year, month, day, hour = 0, minute = 0, second = 0] = value as number[]
    if (!year || !month || !day) return null
    const parsed = dayjs(new Date(year, month - 1, day, hour, minute, second))
    return parsed.isValid() ? parsed : null
  }
  if (!value) return null
  if (typeof value === 'string') {
    const trimmed = value.trim()
    if (!trimmed) return null
    const viDate = trimmed.match(/^(\d{1,2})\/(\d{1,2})\/(\d{4})(?:\s*[·,-]?\s*(\d{1,2}):(\d{2}))?/)
    if (viDate) {
      const [, d, mo, y, h = '0', mi = '0'] = viDate
      const parsed = dayjs(new Date(Number(y), Number(mo) - 1, Number(d), Number(h), Number(mi)))
      return parsed.isValid() ? parsed : null
    }
  }
  const parsed = dayjs(value as string | number | Date)
  return parsed.isValid() ? parsed : null
}

const formatDate = (value: unknown, withTime = false) => {
  const parsed = normalizeDate(value)
  if (!parsed) return '—'
  return parsed.format(withTime ? 'DD/MM/YYYY HH:mm' : 'DD/MM/YYYY')
}

const resolveMediaUrl = (url?: string | null) => {
  if (!url || typeof url !== 'string') return undefined
  const trimmed = url.trim()
  if (!trimmed || trimmed.startsWith('content://')) return undefined
  if (trimmed.startsWith('/uploads/')) return trimmed
  const m = trimmed.match(/(\/uploads\/[^?#\s]+)/)
  if (m) return m[1]
  if (trimmed.startsWith('http://') || trimmed.startsWith('https://')) return trimmed
  return trimmed
}

const getStatusMeta = (status: Report['status']) => {
  if (status === 'VALID')    return { key: 'resolved', label: 'Đã xác nhận vi phạm' }
  if (status === 'REJECTED') return { key: 'rejected', label: 'Đã từ chối' }
  return { key: 'pending', label: 'Chờ xử lý' }
}

const getTargetTypeLabel = (type: Report['targetType']) => (type === 'USER' ? 'Người dùng' : 'Bài viết')

const getTargetName = (report: Report) => {
  if (report.targetName) return report.targetType === 'POST' ? cleanTagText(report.targetName) : report.targetName
  if (report.targetType === 'USER') {
    const user = report.targetInfo as UserTargetInfo | undefined
    return user?.fullName || `Người dùng #${report.targetId}`
  }
  const post = report.targetInfo as PostTargetInfo | undefined
  return post?.interestTag ? cleanTagText(post.interestTag) : post?.content || `Bài viết #${report.targetId}`
}

// ─── Table cells ────────────────────────────────────────────────────────────

const ReporterCell = ({ report }: { report: Report }) => (
  <div className="reports-user-cell">
    <Avatar size={36} src={resolveAvatarUrl(report.reporterAvatarUrl)}
      icon={!report.reporterAvatarUrl ? <UserOutlined /> : undefined}
      className="reports-avatar" />
    <div className="reports-user-meta">
      <span className="reports-user-name" title={report.reporterName || 'Người dùng'}>{report.reporterName || 'Người dùng'}</span>
    </div>
  </div>
)

const TargetCell = ({ report }: { report: Report }) => {
  const targetName = getTargetName(report)
  if (report.targetType === 'USER') {
    const user = report.targetInfo as UserTargetInfo | undefined
    const avatarUrl = report.targetAvatarUrl || user?.avatarUrl
    return (
      <div className="reports-target-cell">
        <div className="reports-target-main">
          <Avatar size={36} src={resolveAvatarUrl(avatarUrl)}
            icon={!avatarUrl ? <UserOutlined /> : undefined} className="reports-avatar" />
          <span className="reports-user-name" title={targetName}>{targetName}</span>
        </div>
      </div>
    )
  }
  const post = report.targetInfo as PostTargetInfo | undefined
  const imageUrl = resolveMediaUrl(report.targetThumbnailUrl || post?.imageUrl)
  return (
    <div className="reports-target-cell">
      <div className="reports-target-main">
        <div className="reports-thumbnail">
          {imageUrl ? <img src={imageUrl} alt="Ảnh bài viết" /> : <FileImageOutlined />}
        </div>
        <span className="reports-user-name" title={targetName}>{targetName}</span>
      </div>
    </div>
  )
}

const ReportSkeletonRows = () => (
  <div className="reports-skeleton">
    {Array.from({ length: PAGE_SIZE }).map((_, i) => (
      <div className="reports-skeleton-row" key={i}>
        <Skeleton.Input active size="small" />
        <Skeleton.Avatar active size={36} shape="circle" />
        <Skeleton.Input active size="small" block />
        <Skeleton.Button active size="small" />
      </div>
    ))}
  </div>
)

// ─── Detail Drawer ───────────────────────────────────────────────────────────

interface DetailDrawerProps {
  report: Report | null
  loading: boolean
  onOpenResolve: (report: Report) => void
  onApproveWrongTag: (report: Report) => void
  approveWrongTagLoading: boolean
}

const DetailDrawer = ({ report, loading, onOpenResolve, onApproveWrongTag, approveWrongTagLoading }: DetailDrawerProps) => {
  if (!report && !loading) return null

  const postInfo = report?.targetType === 'POST' ? (report.targetInfo as PostTargetInfo) : null
  const userInfo = report?.targetType === 'USER' ? (report.targetInfo as UserTargetInfo) : null

  const renderSectionTitle = (icon: React.ReactNode, title: string) => (
    <div className="rdet-section-title">
      <span className="rdet-section-icon">{icon}</span>
      <span>{title}</span>
    </div>
  )

  return (
    <div className="rdet-body">
      {loading ? (
        <div className="rdet-skeleton">
          {[1, 2, 3].map((i) => (
            <div key={i} className="rdet-skeleton-block">
              <Skeleton active paragraph={{ rows: 3 }} />
            </div>
          ))}
        </div>
      ) : report ? (
        <>
          {/* Status banner */}
          {report.status === 'VALID' ? (
            <div className="rdet-resolved-banner">
              <CheckCircleOutlined />
              <div>
                <div className="rdet-resolved-title">Đã xác nhận vi phạm</div>
                {report.reviewedAt && (
                  <div className="rdet-resolved-sub">
                    {formatDate(report.reviewedAt, true)}
                    {report.penaltyPoint ? ` — Trừ ${report.penaltyPoint} điểm uy tín` : ''}
                  </div>
                )}
              </div>
            </div>
          ) : report.status === 'REJECTED' ? (
            <div className="rdet-resolved-banner rdet-resolved-banner--rejected">
              <CloseCircleOutlined />
              <div>
                <div className="rdet-resolved-title">Đã từ chối báo cáo</div>
                {report.reviewedAt && (
                  <div className="rdet-resolved-sub">{formatDate(report.reviewedAt, true)}</div>
                )}
              </div>
            </div>
          ) : (
            <div className="rdet-pending-banner">
              <WarningOutlined />
              <span>Báo cáo đang chờ xử lý</span>
            </div>
          )}

          {/* A. Thông tin báo cáo */}
          <section className="rdet-section">
            {renderSectionTitle(<FileTextOutlined />, 'Thông tin báo cáo')}
            <div className="rdet-info-grid">
              <div className="rdet-info-row">
                <span>Mã báo cáo</span>
                <strong className="rdet-report-id">#{report.id}</strong>
              </div>
              <div className="rdet-info-row">
                <span>Loại đối tượng</span>
                <span className={`reports-type-badge ${report.targetType === 'USER' ? 'type-user' : 'type-post'}`}>
                  {getTargetTypeLabel(report.targetType)}
                </span>
              </div>
              <div className="rdet-info-row">
                <span>Trạng thái</span>
                <span className={`reports-status-badge status-${getStatusMeta(report.status).key}`}>
                  {getStatusMeta(report.status).label}
                </span>
              </div>
              <div className="rdet-info-row">
                <span>Ngày tạo</span>
                <strong>{formatDate(report.createdAt, true)}</strong>
              </div>
              {report.reviewedAt && (
                <div className="rdet-info-row">
                  <span>Ngày xử lý</span>
                  <strong>{formatDate(report.reviewedAt, true)}</strong>
                </div>
              )}
              {report.adminAction && (
                <div className="rdet-info-row">
                  <span>Kết luận</span>
                  <Tag color="blue" style={{ margin: 0 }}>{ACTION_LABEL[report.adminAction] || report.adminAction}</Tag>
                </div>
              )}
            </div>
          </section>

          {/* B. Người báo cáo */}
          <section className="rdet-section">
            {renderSectionTitle(<UserOutlined />, 'Người báo cáo')}
            <div className="rdet-person-row">
              <Avatar size={44} src={resolveAvatarUrl(report.reporterAvatarUrl)}
                icon={!report.reporterAvatarUrl ? <UserOutlined /> : undefined}
                className="rdet-person-avatar rdet-avatar--reporter" />
              <div className="rdet-person-info">
                <div className="rdet-person-name">{report.reporterName || 'Người dùng'}</div>
              </div>
            </div>
          </section>

          {/* C. Đối tượng bị báo cáo */}
          <section className="rdet-section">
            {renderSectionTitle(
              <ExclamationCircleOutlined style={{ color: '#ff4d4f' }} />,
              report.targetType === 'USER' ? 'Người dùng bị báo cáo' : 'Bài viết bị báo cáo'
            )}

            {report.targetType === 'USER' && userInfo ? (
              <>
                <div className="rdet-person-row">
                  <Avatar size={44} src={resolveAvatarUrl(userInfo.avatarUrl)}
                    icon={!userInfo.avatarUrl ? <UserOutlined /> : undefined}
                    className="rdet-person-avatar rdet-avatar--target" />
                  <div className="rdet-person-info">
                    <div className="rdet-person-name">
                      {userInfo.fullName || getTargetName(report)}
                      {userInfo.isBlocked && <Tag color="red" style={{ marginLeft: 6, fontSize: 11 }}>Đã khóa</Tag>}
                    </div>
                    {userInfo.email && <div className="rdet-person-sub">{userInfo.email}</div>}
                    <div className="rdet-person-sub">ID #{report.targetId}</div>
                  </div>
                </div>
                <div className="rdet-user-stats">
                  <div className="rdet-user-stat">
                    <strong style={{ color: '#faad14' }}>
                      {typeof userInfo.averageRating === 'number' ? userInfo.averageRating.toFixed(1) : '—'}
                    </strong>
                    <span>Rating</span>
                  </div>
                  <div className="rdet-user-stat">
                    <strong style={{ color: '#1677ff' }}>{userInfo.reputationScore ?? '—'}</strong>
                    <span>Uy tín</span>
                  </div>
                  {userInfo.gender && (
                    <div className="rdet-user-stat">
                      <strong style={{ color: '#595959' }}>{userInfo.gender}</strong>
                      <span>Giới tính</span>
                    </div>
                  )}
                </div>
              </>
            ) : report.targetType === 'POST' && postInfo ? (
              <>
                <div className="rdet-post-author">
                  <Avatar size={34} icon={<UserOutlined />} className="rdet-avatar--post-author" />
                  <div>
                    <div className="rdet-person-name">{postInfo.authorName || 'Ẩn danh'}</div>
                    <div className="rdet-person-sub">{formatDate(postInfo.createdAt, true)}</div>
                  </div>
                  <Tag color={postInfo.archived ? 'red' : 'green'} style={{ marginLeft: 'auto' }}>
                    {postInfo.archived ? 'Đã ẩn' : 'Hiển thị'}
                  </Tag>
                </div>
                {postInfo.imageUrl && (
                  <img
                    src={postInfo.imageUrl}
                    alt="Post"
                    className="rdet-post-image"
                    onError={(e) => { (e.target as HTMLImageElement).style.display = 'none' }}
                  />
                )}
                <Paragraph
                  ellipsis={{ rows: 3, expandable: true, symbol: 'Xem thêm' }}
                  style={{ fontSize: 13, lineHeight: 1.6, color: '#262626', marginBottom: 10 }}
                >
                  {postInfo.content || '—'}
                </Paragraph>
                <div className="rdet-post-tags">
                  {postInfo.interestTag && <Tag color="blue">{cleanTagText(postInfo.interestTag)}</Tag>}
                  {postInfo.location && <Tag color="green">{postInfo.location}</Tag>}
                  {postInfo.maxMembers && <Tag color="purple">{postInfo.maxMembers} người</Tag>}
                  {postInfo.startTime && <Tag color="orange">{formatDate(postInfo.startTime)}</Tag>}
                </div>
              </>
            ) : (
              <div className="rdet-empty-hint">Không có thông tin đối tượng</div>
            )}
          </section>

          {/* D. Nội dung báo cáo */}
          <section className="rdet-section">
            {renderSectionTitle(<WarningOutlined style={{ color: '#ee4778' }} />, 'Nội dung báo cáo')}
            <div className="rdet-info-row rdet-info-row--stacked">
              <span>Lý do</span>
              <Tag color="red" style={{ fontSize: 13, padding: '3px 12px', borderRadius: 20, marginTop: 4 }}>
                {report.reason || '—'}
              </Tag>
            </div>
            <div className="rdet-info-row rdet-info-row--stacked" style={{ marginTop: 12 }}>
              <span>Mô tả chi tiết</span>
              <p className={`rdet-description ${!report.description ? 'rdet-description--empty' : ''}`}>
                {report.description || 'Không có mô tả'}
              </p>
            </div>
          </section>

          {/* E. Bằng chứng */}
          <section className="rdet-section">
            {renderSectionTitle(<PictureOutlined />, 'Bằng chứng')}
            {report.evidenceImages && report.evidenceImages.length > 0 ? (
              <Image.PreviewGroup>
                <div className="rdet-evidence-grid">
                  {report.evidenceImages.map((url, i) => (
                    <Image key={i} width={80} height={80} src={url}
                      style={{ objectFit: 'cover', borderRadius: 8, border: '1px solid #edf0f5', cursor: 'pointer' }}
                      preview={{ mask: 'Xem' }}
                    />
                  ))}
                </div>
              </Image.PreviewGroup>
            ) : (
              <div className="rdet-empty-hint">Không có hình ảnh bằng chứng</div>
            )}
          </section>

          {/* F. Kết quả xử lý */}
          {(report.status === 'VALID' || report.status === 'REJECTED') && (
            <section className="rdet-section">
              {renderSectionTitle(<SafetyCertificateOutlined style={{ color: '#52c41a' }} />, 'Kết quả xử lý')}
              <div className="rdet-timeline">
                <div className="rdet-timeline-item">
                  <div className="rdet-timeline-dot rdet-dot--blue" />
                  <div>
                    <div className="rdet-timeline-label">Báo cáo được tạo</div>
                    <div className="rdet-timeline-date">{formatDate(report.createdAt, true)}</div>
                  </div>
                </div>
                {report.reviewedAt && (
                  <div className="rdet-timeline-item">
                    <div className={`rdet-timeline-dot ${report.status === 'VALID' ? 'rdet-dot--green' : 'rdet-dot--red'}`} />
                    <div>
                      <div className="rdet-timeline-label">
                        {report.status === 'VALID' ? 'Xác nhận vi phạm' : 'Từ chối báo cáo'}
                      </div>
                      <div className="rdet-timeline-date">{formatDate(report.reviewedAt, true)}</div>
                      {report.status === 'VALID' && report.penaltyPoint !== undefined && (
                        <div style={{ marginTop: 4 }}>
                          <Tag color="red" style={{ fontSize: 11, margin: 0 }}>-{report.penaltyPoint} điểm uy tín</Tag>
                        </div>
                      )}
                      {report.reviewedBy && (
                        <div className="rdet-timeline-date" style={{ marginTop: 2 }}>Admin ID: {report.reviewedBy}</div>
                      )}
                    </div>
                  </div>
                )}
              </div>
            </section>
          )}

          {/* Action button at bottom */}
          {report.status === 'PENDING' && (
            <div className="rdet-action-footer">
              {report.targetType === 'POST' && (
                <Button
                  danger
                  icon={<TagsOutlined />}
                  className="rdet-wrong-tag-btn"
                  loading={approveWrongTagLoading}
                  onClick={() => onApproveWrongTag(report)}
                >
                  Phê duyệt sai Tag
                </Button>
              )}
              <Button
                type="primary"
                icon={<ToolOutlined />}
                className="rdet-resolve-btn"
                onClick={() => onOpenResolve(report)}
              >
                Xử lý báo cáo
              </Button>
            </div>
          )}
        </>
      ) : null}
    </div>
  )
}

// ─── Main Page ───────────────────────────────────────────────────────────────

export default function ReportsPage() {
  const navigate = useNavigate()

  const [reports, setReports] = useState<Report[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [search, setSearch] = useState('')
  const [targetTypeFilter, setTargetTypeFilter] = useState<TargetTypeFilter>('all')
  const [statusFilter, setStatusFilter] = useState<StatusGroupFilter>('all')
  const [pagination, setPagination] = useState({ current: 1, pageSize: PAGE_SIZE, total: 0 })
  const debouncedSearch = useDebouncedValue(search, 300)
  const searchPending = search !== debouncedSearch

  // Detail drawer
  const [detailVisible, setDetailVisible] = useState(false)
  const [detailReport, setDetailReport] = useState<Report | null>(null)
  const [detailLoading, setDetailLoading] = useState(false)
  const [approveWrongTagLoading, setApproveWrongTagLoading] = useState(false)
  const [wrongTagConfirmReport, setWrongTagConfirmReport] = useState<Report | null>(null)

  useEffect(() => { loadReports(1, debouncedSearch) }, [debouncedSearch, targetTypeFilter, statusFilter])

  const resetFilters = () => {
    setSearch('')
    setTargetTypeFilter('all')
    setStatusFilter('all')
  }

  const loadReports = async (page: number, searchValue = debouncedSearch) => {
    try {
      setLoading(true)
      setError(null)
      const filter: ReportFilter = {
        search: searchValue,
        targetType: targetTypeFilter === 'all' ? null : targetTypeFilter,
        statusGroup: statusFilter === 'all' ? null : statusFilter,
      }
      const result = await reportAdminService.getReports({ page, pageSize: PAGE_SIZE }, filter)
      setReports(result.data)
      setPagination({ current: page, pageSize: PAGE_SIZE, total: result.total })
    } catch {
      const msg = 'Không thể tải danh sách báo cáo'
      setError(msg)
      message.error(msg)
    } finally {
      setLoading(false)
    }
  }

  const openDetail = async (report: Report) => {
    setDetailVisible(true)
    setDetailReport(null)
    setDetailLoading(true)
    try {
      const full = await reportAdminService.getReport(report.id)
      setDetailReport(full)
    } catch {
      message.error('Không thể tải chi tiết báo cáo')
      setDetailVisible(false)
    } finally {
      setDetailLoading(false)
    }
  }

  const openResolveFromDrawer = (report: Report) => {
    setDetailVisible(false)
    navigate(`/admin/reports/${report.id}`)
  }

  const handleApproveWrongTag = (report: Report) => {
    setWrongTagConfirmReport(report)
  }

  const confirmApproveWrongTag = async () => {
    if (!wrongTagConfirmReport) return
    const report = wrongTagConfirmReport
    try {
      setApproveWrongTagLoading(true)
      await reportAdminService.approveWrongTag(report.id)
      setWrongTagConfirmReport(null)
      message.success('Đã xử lý vi phạm sai tag: bài viết bị ẩn, trừ 10 điểm uy tín, thông báo đã gửi!')
      setDetailVisible(false)
      setDetailReport(null)
      loadReports(pagination.current)
      // Thông báo cho Topbar refresh badge số báo cáo chưa đọc ngay lập tức
      window.dispatchEvent(new CustomEvent('adminReportApproved'))
    } catch (error: any) {
      message.error(error?.message || 'Không thể xử lý báo cáo sai tag')
    } finally {
      setApproveWrongTagLoading(false)
    }
  }

  const columns: ColumnsType<Report> = [
    {
      title: 'Mã',
      dataIndex: 'id',
      key: 'id',
      width: 72,
      sorter: (a, b) => a.id - b.id,
      render: (id: number) => <span className="reports-id">#{id}</span>,
    },
    {
      title: 'Người báo cáo',
      key: 'reporter',
      width: 200,
      render: (_, record) => <ReporterCell report={record} />,
    },
    {
      title: 'Đối tượng bị báo cáo',
      key: 'target',
      width: 250,
      render: (_, record) => <TargetCell report={record} />,
    },
    {
      title: 'Lý do',
      dataIndex: 'reason',
      key: 'reason',
      width: 170,
      render: (reason: string) => (
        <span className="reports-reason" title={reason || '—'}>{reason || '—'}</span>
      ),
    },
    {
      title: 'Mô tả',
      dataIndex: 'description',
      key: 'description',
      width: 210,
      render: (desc: string) => (
        <span className="reports-description" title={desc || '—'}>{desc || '—'}</span>
      ),
    },
    {
      title: 'Trạng thái',
      dataIndex: 'status',
      key: 'status',
      width: 128,
      render: (status: Report['status']) => {
        const m = getStatusMeta(status)
        return <span className={`reports-status-badge status-${m.key}`}>{m.label}</span>
      },
    },
    {
      title: 'Ngày tạo',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 108,
      sorter: (a, b) => (normalizeDate(b.createdAt)?.valueOf() || 0) - (normalizeDate(a.createdAt)?.valueOf() || 0),
      render: (date: unknown) => <span className="reports-muted">{formatDate(date)}</span>,
    },
    {
      title: 'Thao tác',
      key: 'actions',
      width: 176,
      align: 'right',
      render: (_, record) => (
        <div className="reports-actions">
          <Button className="reports-detail-button" size="small" icon={<EyeOutlined />} onClick={() => openDetail(record)}>
            Chi tiết
          </Button>
          {(record.status === 'VALID' || record.status === 'REJECTED') ? (
            <Button
              disabled
              size="small"
              className="reports-resolved-button"
              icon={record.status === 'VALID' ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
            >
              {record.status === 'VALID' ? 'Đã xác nhận' : 'Đã từ chối'}
            </Button>
          ) : (
            <Button className="reports-process-button" size="small" icon={<ToolOutlined />} onClick={() => navigate(`/admin/reports/${record.id}`)}>
              Xử lý
            </Button>
          )}
        </div>
      ),
    },
  ]

  return (
    <MainLayout>
      <div className="reports-page">
        <div className="reports-admin-card">
          {/* Header */}
          <div className="reports-page-head">
            <div className="reports-title-wrap">
              <span className="reports-title-icon"><WarningOutlined /></span>
              <div>
                <h2>Danh sách báo cáo</h2>
                <p>Quản lý, theo dõi và xử lý các báo cáo từ người dùng trong hệ thống.</p>
              </div>
            </div>
            <div className="reports-head-right">
              <span className="reports-total-badge">Tổng {pagination.total} báo cáo</span>
              <Button icon={<ReloadOutlined />} onClick={() => loadReports(1, search)} loading={loading} className="reports-refresh">
                Làm mới
              </Button>
            </div>
          </div>

          {/* Toolbar */}
          <div className="reports-toolbar">
            <Input
              className="reports-search"
              placeholder="Tìm theo người báo cáo, đối tượng, lý do hoặc trạng thái"
              prefix={<SearchOutlined />}
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              allowClear
            />
            <Select
              className="reports-filter"
              value={targetTypeFilter}
              onChange={(v) => setTargetTypeFilter(v)}
              options={[
                { label: 'Tất cả đối tượng', value: 'all' },
                { label: 'Người dùng', value: 'USER' },
                { label: 'Bài viết', value: 'POST' },
              ]}
            />
            <Select
              className="reports-filter"
              value={statusFilter}
              onChange={(v) => setStatusFilter(v)}
              options={[
                { label: 'Tất cả trạng thái', value: 'all' },
                { label: 'Chờ xử lý', value: 'OPEN' },
                { label: 'Đã xử lý', value: 'CLOSED' },
              ]}
            />
          </div>

          {/* Table */}
          {error ? (
            <div className="reports-state">
              <Empty description={error} image={Empty.PRESENTED_IMAGE_SIMPLE}>
                <Button type="primary" icon={<ReloadOutlined />} onClick={() => loadReports(1, search)}>Thử lại</Button>
              </Empty>
            </div>
          ) : loading && reports.length === 0 ? (
            <ReportSkeletonRows />
          ) : (
            <div className="reports-table-shell">
              <Table
                columns={columns}
                dataSource={reports.map((r) => ({ ...r, key: r.id }))}
                loading={loading || searchPending}
                pagination={{
                  current: pagination.current,
                  pageSize: pagination.pageSize,
                  total: pagination.total,
                  showSizeChanger: false,
                  showTotal: (total, range) => `Hiển thị ${range[0]}-${range[1]} trong ${total} báo cáo`,
                  onChange: (page) => loadReports(page, debouncedSearch),
                }}
                scroll={{ x: 'max-content' }}
                locale={{
                  emptyText: (
                    <Empty description="Không tìm thấy kết quả phù hợp." image={Empty.PRESENTED_IMAGE_SIMPLE}>
                      <Button onClick={resetFilters}>Xóa bộ lọc</Button>
                    </Empty>
                  ),
                }}
                rowKey="id"
              />
            </div>
          )}
        </div>
      </div>

      {/* ── Detail Drawer ── */}
      <Drawer
        className="report-detail-drawer"
        title={
          <div className="rdet-drawer-title">
            <span>Chi tiết báo cáo{detailReport ? ` #${detailReport.id}` : ''}</span>
            <small>Thông tin đầy đủ về báo cáo</small>
          </div>
        }
        closeIcon={<CloseOutlined />}
        placement="right"
        width={520}
        open={detailVisible}
        onClose={() => { setDetailVisible(false); setDetailReport(null) }}
        destroyOnClose
      >
        <DetailDrawer
          report={detailReport}
          loading={detailLoading}
          onOpenResolve={openResolveFromDrawer}
          onApproveWrongTag={handleApproveWrongTag}
          approveWrongTagLoading={approveWrongTagLoading}
        />
      </Drawer>

      <ConfirmActionModal
        open={!!wrongTagConfirmReport}
        title="Xác nhận trừ điểm báo cáo vi phạm"
        message={`Báo cáo #${wrongTagConfirmReport?.id ?? ''} sẽ được xác nhận là vi phạm sai Tag. Bài viết sẽ bị ẩn, tác giả bị trừ 10 điểm uy tín và nhận thông báo kỷ luật. Bạn có chắc chắn muốn tiếp tục?`}
        okText="Xác nhận trừ 10 điểm"
        cancelText="Hủy"
        type="warning"
        loading={approveWrongTagLoading}
        onConfirm={confirmApproveWrongTag}
        onCancel={() => setWrongTagConfirmReport(null)}
      />

    </MainLayout>
  )
}
