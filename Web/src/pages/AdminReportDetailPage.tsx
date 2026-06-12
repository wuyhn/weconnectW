import { useCallback, useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Button,
  Space,
  Avatar,
  Tag,
  Empty,
  Spin,
  message,
  Row,
  Col,
  Popconfirm,
  Divider,
  Typography,
  Image,
  Input,
  Switch,
  Slider,
} from 'antd'
import {
  ArrowLeftOutlined,
  UserOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  StopOutlined,
  DeleteOutlined,
  ExclamationCircleOutlined,
  FileTextOutlined,
  ClockCircleOutlined,
  SafetyCertificateOutlined,
  EyeInvisibleOutlined,
  PictureOutlined,
  MinusOutlined,
  PlusOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import { reportAdminService } from '../services/reportAdminService'
import { Report, PostTargetInfo, UserTargetInfo } from '../types'
import dayjs from 'dayjs'
import './AdminReportDetailPage.css'
import { resolveAvatarUrl } from '../utils/avatar'
import { userAdminService } from '../services/userAdminService'
import { cleanTagText } from '../utils/text'

const { Text, Paragraph } = Typography

const STATUS_CONFIG: Record<string, { color: string; label: string }> = {
  PENDING:  { color: 'orange', label: 'Chờ xử lý' },
  VALID:    { color: 'green',  label: 'Đã xác nhận vi phạm' },
  REJECTED: { color: 'red',   label: 'Đã từ chối' },
}

function StatusBadge({ status }: { status: string }) {
  const cfg = STATUS_CONFIG[status] || { color: 'default', label: status }
  return (
    <Tag
      color={cfg.color}
      style={{ fontSize: 13, padding: '4px 14px', borderRadius: 20, fontWeight: 600, margin: 0 }}
    >
      {cfg.label}
    </Tag>
  )
}

function ReputationTier({ score }: { score: number }) {
  if (score >= 80) return <Tag color="green">Rất uy tín</Tag>
  if (score >= 60) return <Tag color="blue">Uy tín ổn</Tag>
  if (score >= 40) return <Tag color="orange">Cần cân nhắc</Tag>
  if (score >= 20) return <Tag color="volcano">Uy tín thấp</Tag>
  return <Tag color="red">Rủi ro cao</Tag>
}


// ── ViolationCode Matrix — đồng bộ 1:1 với backend ViolationCode.java ──
// points = -1 nghĩa là điểm phạt do Admin tự nhập qua slider tùy chỉnh (khoảng [0, 50])

type ViolationPreset = {
  code: string
  name: string
  points: number
  desc: string
}

export const USER_VIOLATION_PRESETS: ViolationPreset[] = [
  { code: 'SPAM',          name: 'Spam / Làm phiền',       points: 10, desc: 'Gửi nội dung lặp đi lặp lại, làm phiền người dùng khác' },
  { code: 'INAPPROPRIATE', name: 'Nội dung không phù hợp', points: 15, desc: 'Ngôn từ, hình ảnh phản cảm vi phạm tiêu chuẩn cộng đồng' },
  { code: 'FRAUD',         name: 'Lừa đảo / Giả mạo',      points: 30, desc: 'Giả mạo danh tính, thông tin gây hại nghiêm trọng' },
  { code: 'HARASSMENT',    name: 'Quấy rối / Xúc phạm',    points: 30, desc: 'Đe dọa, xúc phạm hoặc quấy rối người dùng khác' },
  { code: 'U_OTHER',       name: 'Khác',                   points: -1, desc: 'Admin tự nhập điểm phạt tùy chỉnh (0–50 điểm)' },
]

export const POST_VIOLATION_PRESETS: ViolationPreset[] = [
  { code: 'SPAM_POST',  name: 'Spam / Quảng cáo',          points: 5,  desc: 'Bài viết spam, quảng cáo rác không liên quan' },
  { code: 'MISLEADING', name: 'Thông tin sai lệch',        points: 10, desc: 'Tin giả, thông tin gây hiểu lầm hoặc bịa đặt' },
  { code: 'VULGAR',     name: 'Nội dung thô tục',          points: 10, desc: 'Nội dung phản cảm, thô tục, không phù hợp cộng đồng' },
  { code: 'VIOLATION',  name: 'Vi phạm quy định',          points: 10, desc: 'Vi phạm quy định chung của nền tảng WeConnect' },
  { code: 'BULLYING',   name: 'Quấy rối / Bắt nạt',        points: 20, desc: 'Bài viết nhằm mục đích bắt nạt, quấy rối người khác' },
  { code: 'P_OTHER',    name: 'Khác',                      points: -1, desc: 'Admin tự nhập điểm phạt tùy chỉnh (0–50 điểm)' },
]

// Nhóm mã vi phạm dành riêng cho báo cáo NHẬN XÉT (targetType = REVIEW)
export const REVIEW_VIOLATION_PRESETS: ViolationPreset[] = [
  { code: 'FAKE_REVIEW',  name: 'Đánh giá sai sự thật', points: 25, desc: 'Nhận xét cố tình vu khống, sai sự thật nhằm hạ uy tín người dùng' },
  { code: 'REVIEW_SPAM',  name: 'Spam nhận xét',        points: 10, desc: 'Lạm dụng tính năng đánh giá để spam hoặc quấy rối' },
  { code: 'R_OTHER',      name: 'Khác',                 points: -1, desc: 'Admin tự nhập điểm phạt tùy chỉnh (0–50 điểm)' },
]

// Tự động gợi ý mã vi phạm phù hợp nhất dựa trên lý do người dùng báo cáo
function suggestViolationCode(reason: string | undefined, targetType: string): ViolationPreset {
  const r = (reason ?? '').toLowerCase()
  if (targetType === 'REVIEW') {
    if (r.includes('spam')) return REVIEW_VIOLATION_PRESETS.find(p => p.code === 'REVIEW_SPAM')!
    return REVIEW_VIOLATION_PRESETS.find(p => p.code === 'FAKE_REVIEW')!
  }
  if (targetType === 'USER') {
    if (r.includes('lừa đảo') || r.includes('giả mạo')) return USER_VIOLATION_PRESETS.find(p => p.code === 'FRAUD')!
    if (r.includes('quấy rối') || r.includes('xúc phạm')) return USER_VIOLATION_PRESETS.find(p => p.code === 'HARASSMENT')!
    if (r.includes('không phù hợp') || r.includes('tục tĩu') || r.includes('phản cảm')) return USER_VIOLATION_PRESETS.find(p => p.code === 'INAPPROPRIATE')!
    if (r.includes('spam') || r.includes('làm phiền')) return USER_VIOLATION_PRESETS.find(p => p.code === 'SPAM')!
    return USER_VIOLATION_PRESETS[0]
  }
  // POST
  if (r.includes('quấy rối') || r.includes('bắt nạt')) return POST_VIOLATION_PRESETS.find(p => p.code === 'BULLYING')!
  if (r.includes('sai lệch') || r.includes('tin giả')) return POST_VIOLATION_PRESETS.find(p => p.code === 'MISLEADING')!
  if (r.includes('thô tục') || r.includes('phản cảm')) return POST_VIOLATION_PRESETS.find(p => p.code === 'VULGAR')!
  if (r.includes('spam') || r.includes('quảng cáo')) return POST_VIOLATION_PRESETS.find(p => p.code === 'SPAM_POST')!
  return POST_VIOLATION_PRESETS.find(p => p.code === 'VIOLATION')!
}

export default function AdminReportDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()

  const [report, setReport] = useState<Report | null>(null)
  const [loading, setLoading] = useState(true)
  const [decision, setDecision] = useState<'VALID' | 'REJECTED' | ''>('')
  const [selectedPenalty, setSelectedPenalty] = useState<number>(0)
  const [submitting, setSubmitting] = useState(false)
  const [actionLoading, setActionLoading] = useState<string>('')
  const [adminNote, setAdminNote] = useState('')
  const [isCustomMode, setIsCustomMode] = useState(false)

  const wheelMax = 50

  const adjustPoints = useCallback((delta: number) => {
    setSelectedPenalty((prev) => Math.max(0, Math.min(wheelMax, prev + delta)))
  }, [wheelMax])

  useEffect(() => {
    if (id) loadReport(parseInt(id))
  }, [id])

  useEffect(() => {
    setSelectedPenalty(0)
    setAdminNote('')
    setIsCustomMode(false)
    if (decision === 'VALID' && report) {
      const preset = suggestViolationCode(report.reason, report.targetType as 'USER' | 'POST')
      setSelectedPenalty(Math.max(0, Math.min(wheelMax, preset.points)))
    }
  }, [decision, report])

  const loadReport = async (reportId: number) => {
    try {
      setLoading(true)
      const data = await reportAdminService.getReport(reportId)
      setReport(data)
    } catch (error: any) {
      message.error(error?.message || 'Không tìm thấy báo cáo')
      navigate('/reports')
    } finally {
      setLoading(false)
    }
  }

  // Handler: Từ chối báo cáo (REJECTED)
  const handleSubmit = async () => {
    if (!report || decision !== 'REJECTED') return
    try {
      setSubmitting(true)
      await reportAdminService.rejectReport(report.id)
      message.success('Đã từ chối báo cáo.')
      await loadReport(report.id)
      setDecision('')
    } catch (error: any) {
      message.error(error?.message || 'Không thể từ chối báo cáo')
    } finally {
      setSubmitting(false)
    }
  }

  // Handler: Phê duyệt mặc định — dùng mã vi phạm đề xuất + điểm phạt cố định từ Ma trận
  const handleDefaultApprove = async (suggestedPreset: ViolationPreset, note?: string) => {
    if (!report) return
    try {
      setSubmitting(true)
      const result = await reportAdminService.approveViolation(
        report.id,
        suggestedPreset.code,
        undefined,   // customPenalty = null → backend dùng điểm cố định từ Ma trận
        note?.trim() || undefined,
      )
      const appliedPenalty = result?.penaltyPoint ?? suggestedPreset.points
      const newScore = result?.newReputationScore
      const newStatus = result?.userStatus

      let msg = `Đã phê duyệt vi phạm [${suggestedPreset.code}] — trừ ${appliedPenalty} điểm uy tín.`
      if (newScore !== undefined) msg += ` Điểm mới: ${Math.round(newScore)}.`
      if (newStatus === 'LOCKED_TEMP') msg += ' Tài khoản bị khóa tạm thời 7 ngày.'
      else if (newStatus === 'BANNED') msg += ' Tài khoản bị khóa vĩnh viễn.'
      message.success(msg, 6)

      await loadReport(report.id)
      setDecision('')
      setIsCustomMode(false)
    } catch (error: any) {
      message.error(error?.message || 'Không thể phê duyệt báo cáo')
    } finally {
      setSubmitting(false)
    }
  }

  const handleConfirmDecision = async (suggestedPreset: ViolationPreset) => {
    if (decision === 'REJECTED') {
      await handleSubmit()
      return
    }

    if (decision !== 'VALID') return

    if (selectedPenalty !== suggestedPreset.points) {
      await handleCustomApprove()
      return
    }

    await handleDefaultApprove(suggestedPreset, adminNote)
  }

  // Handler: Phê duyệt tùy biến — dùng U_OTHER / P_OTHER + điểm admin chọn + ghi chú bắt buộc
  const handleCustomApprove = async () => {
    if (!report) return
    if (adminNote.trim().length < 10) {
      message.warning('Vui lòng nhập ghi chú giải trình tối thiểu 10 ký tự')
      return
    }
    if (selectedPenalty < 0 || selectedPenalty > 50) {
      message.warning('Điểm phạt tùy biến phải trong khoảng 0–50 điểm')
      return
    }
    // Chọn mã "Khác" tương ứng với loại đối tượng báo cáo
    const customCode = report.targetType === 'USER'
      ? 'U_OTHER'
      : report.targetType === 'REVIEW'
        ? 'R_OTHER'
        : 'P_OTHER'
    try {
      setSubmitting(true)
      const result = await reportAdminService.approveViolation(
        report.id,
        customCode,
        selectedPenalty,
        adminNote.trim(),
      )
      const appliedPenalty = result?.penaltyPoint ?? selectedPenalty
      const newScore = result?.newReputationScore
      const newStatus = result?.userStatus

      let msg = `Đã phê duyệt vi phạm tùy biến — trừ ${appliedPenalty} điểm uy tín.`
      if (newScore !== undefined) msg += ` Điểm mới: ${Math.round(newScore)}.`
      if (newStatus === 'LOCKED_TEMP') msg += ' Tài khoản bị khóa tạm thời 7 ngày.'
      else if (newStatus === 'BANNED') msg += ' Tài khoản bị khóa vĩnh viễn.'
      message.success(msg, 6)

      await loadReport(report.id)
      setDecision('')
      setIsCustomMode(false)
      setSelectedPenalty(0)
      setAdminNote('')
    } catch (error: any) {
      message.error(error?.message || 'Không thể phê duyệt báo cáo')
    } finally {
      setSubmitting(false)
    }
  }

  const handleHidePost = async () => {
    if (!report) return
    try {
      setActionLoading('hide')
      await reportAdminService.hidePost(report.id)
      message.success('Đã ẩn bài viết')
      await loadReport(report.id)
    } catch (error: any) {
      message.error(error?.message || 'Không thể ẩn bài viết')
    } finally {
      setActionLoading('')
    }
  }

  const handleDeletePost = async () => {
    if (!report) return
    try {
      setActionLoading('delete')
      await reportAdminService.deletePost(report.id)
      message.success('Đã xóa bài viết')
      navigate('/reports')
    } catch (error: any) {
      message.error(error?.message || 'Không thể xóa bài viết')
    } finally {
      setActionLoading('')
    }
  }

  const handleBlockUser = async (userId: number) => {
    try {
      setActionLoading('block')
      await userAdminService.blockUser(userId)
      message.success('Đã khóa tài khoản')
      await loadReport(report!.id)
    } catch (error: any) {
      message.error(error?.message || 'Không thể khóa tài khoản')
    } finally {
      setActionLoading('')
    }
  }

  if (loading) {
    return (
      <MainLayout>
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '60vh' }}>
          <Spin size="large" />
        </div>
      </MainLayout>
    )
  }

  if (!report) {
    return (
      <MainLayout>
        <Card>
          <Empty description="Không tìm thấy báo cáo" style={{ marginTop: 48 }} />
          <div style={{ textAlign: 'center', marginTop: 24 }}>
            <Button type="primary" onClick={() => navigate('/reports')}>Quay lại</Button>
          </div>
        </Card>
      </MainLayout>
    )
  }

  const postInfo   = report.targetType === 'POST'   ? (report.targetInfo as PostTargetInfo)   : null
  const userInfo   = report.targetType === 'USER'   ? (report.targetInfo as UserTargetInfo)   : null
  // reviewInfo: dữ liệu nhận xét bị báo cáo (rating, comment, tên người viết, tên nạn nhân)
  const reviewInfo = report.targetType === 'REVIEW' ? (report.targetInfo as Record<string, any>) : null
  const isClosed   = report.status === 'VALID' || report.status === 'REJECTED'

  // Mã vi phạm đề xuất tự động — hỗ trợ USER / POST / REVIEW
  const suggestedPreset = suggestViolationCode(report.reason, report.targetType)

  const isPenaltyAdjusted = decision === 'VALID' && selectedPenalty !== suggestedPreset.points
  const requiresAdminNote = isPenaltyAdjusted
  const canSubmitDecision =
    decision === 'REJECTED' ||
    (
      decision === 'VALID' &&
      selectedPenalty >= 0 &&
      selectedPenalty <= wheelMax &&
      (!requiresAdminNote || adminNote.trim().length >= 10)
    )
  const selectedPenaltyLabel  = selectedPenalty > 0 ? `−${selectedPenalty}` : '0'
  const suggestedPenaltyLabel = suggestedPreset.points > 0 ? `−${suggestedPreset.points}` : '0'
  const primaryActionLabel    = decision === 'REJECTED' ? 'Từ chối báo cáo' : 'Xác nhận xử lý'
  const confirmTitle          = decision === 'REJECTED' ? 'Từ chối báo cáo' : 'Xác nhận xử lý báo cáo'
  const confirmDescription    = decision === 'REJECTED'
    ? 'Đánh dấu báo cáo này là không hợp lệ? Không trừ điểm uy tín.'
    : report.targetType === 'REVIEW'
      ? `Gỡ nhận xét và trừ ${selectedPenaltyLabel} điểm uy tín kẻ viết vu khống?`
      : `Áp dụng mức phạt ${selectedPenaltyLabel} điểm uy tín${report.targetType === 'POST' ? ' và ẩn bài viết' : ''}?`

  const renderInfoRow = (label: string, value: React.ReactNode) => (
    <div className="info-row">
      <Text className="info-label">{label}</Text>
      <div className="info-value">{value}</div>
    </div>
  )

  return (
    <MainLayout>
      <div className="admin-report-detail-page">

        {/* ── Header ── */}
        <div className="report-page-header">
          <div className="report-page-header-left">
            <Button
              type="text"
              icon={<ArrowLeftOutlined />}
              onClick={() => navigate('/reports')}
            >
              Quay lại
            </Button>
            <h1 className="report-page-title">
              Chi tiết báo cáo{' '}
              <span className="report-id-badge">#{report.id}</span>
            </h1>
          </div>
          <StatusBadge status={report.status} />
        </div>

        {/* ── Closed banner ── */}
        {report.status === 'VALID' && (
          <div className="resolved-banner resolved-banner--valid">
            <CheckCircleOutlined style={{ color: '#52c41a', fontSize: 18 }} />
            <div>
              <Text strong style={{ color: '#52c41a' }}>Báo cáo đã xác nhận vi phạm</Text>
              {report.reviewedAt && (
                <Text type="secondary" style={{ display: 'block', fontSize: 13 }}>
                  {dayjs(report.reviewedAt).format('DD/MM/YYYY HH:mm')}
                  {report.penaltyPoint ? ` — Trừ ${report.penaltyPoint} điểm uy tín` : ''}
                </Text>
              )}
            </div>
          </div>
        )}
        {report.status === 'REJECTED' && (
          <div className="resolved-banner resolved-banner--rejected">
            <CloseCircleOutlined style={{ color: '#ff4d4f', fontSize: 18 }} />
            <div>
              <Text strong style={{ color: '#ff4d4f' }}>Báo cáo đã bị từ chối</Text>
              {report.reviewedAt && (
                <Text type="secondary" style={{ display: 'block', fontSize: 13 }}>
                  {dayjs(report.reviewedAt).format('DD/MM/YYYY HH:mm')}
                </Text>
              )}
            </div>
          </div>
        )}

        {/* ── Summary ── */}
        <Card className="section-card">
          <Row gutter={[24, 16]}>
            <Col xs={12} sm={8} md={6}>
              {renderInfoRow('Report ID', <Text strong>#{report.id}</Text>)}
            </Col>
            <Col xs={12} sm={8} md={6}>
              {renderInfoRow(
                'Loại đối tượng',
                <Tag
                  color={report.targetType === 'USER' ? 'purple' : report.targetType === 'REVIEW' ? 'gold' : 'cyan'}
                  style={{ fontWeight: 600 }}
                >
                  {report.targetType === 'USER' ? 'Người dùng' : report.targetType === 'REVIEW' ? 'Nhận xét' : 'Bài viết'}
                </Tag>,
              )}
            </Col>
            <Col xs={12} sm={8} md={6}>
              {renderInfoRow(
                'Ngày tạo',
                <Space size={6}>
                  <CalendarOutlined style={{ color: '#8c8c8c' }} />
                  <Text>{dayjs(report.createdAt).format('DD/MM/YYYY HH:mm')}</Text>
                </Space>,
              )}
            </Col>
            {report.reviewedAt && (
              <Col xs={12} sm={8} md={6}>
                {renderInfoRow(
                  'Ngày xử lý',
                  <Space size={6}>
                    <ClockCircleOutlined style={{ color: '#52c41a' }} />
                    <Text>{dayjs(report.reviewedAt).format('DD/MM/YYYY HH:mm')}</Text>
                  </Space>,
                )}
              </Col>
            )}
            {report.status === 'VALID' && report.penaltyPoint !== undefined && (
              <Col xs={12} sm={8} md={6}>
                {renderInfoRow(
                  'Điểm phạt',
                  <Tag color="red" style={{ fontWeight: 600 }}>-{report.penaltyPoint} điểm uy tín</Tag>,
                )}
              </Col>
            )}
          </Row>
        </Card>

        {/* ── Nội dung báo cáo ── */}
        <Card
          className="section-card"
          title={<Space><FileTextOutlined /><span>Nội dung báo cáo</span></Space>}
        >
          <div style={{ marginBottom: 16 }}>
            <Text style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6, color: '#8c8c8c', fontWeight: 500 }}>
              Lý do
            </Text>
            <div style={{ marginTop: 6 }}>
              <Tag color="red" style={{ fontSize: 14, padding: '4px 14px', borderRadius: 20 }}>
                {report.reason}
              </Tag>
            </div>
          </div>
          <Divider style={{ margin: '14px 0' }} />
          <div>
            <Text style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6, color: '#8c8c8c', fontWeight: 500 }}>
              Mô tả chi tiết
            </Text>
            <Paragraph
              style={{
                marginTop: 8,
                marginBottom: 0,
                fontSize: 14,
                lineHeight: 1.7,
                color: report.description ? '#262626' : '#bfbfbf',
                fontStyle: report.description ? 'normal' : 'italic',
              }}
            >
              {report.description || 'Không có mô tả'}
            </Paragraph>
          </div>
        </Card>

        {/* ── Bằng chứng ── */}
        <Card
          className="section-card"
          title={<Space><PictureOutlined /><span>Bằng chứng</span></Space>}
        >
          {report.evidenceImages && report.evidenceImages.length > 0 ? (
            <Image.PreviewGroup>
              <div style={{ display: 'flex', flexWrap: 'wrap', gap: 10 }}>
                {report.evidenceImages.map((url, i) => (
                  <Image
                    key={i}
                    width={120}
                    height={120}
                    src={url}
                    style={{ objectFit: 'cover', borderRadius: 8, border: '1px solid #f0f0f0', cursor: 'pointer' }}
                    preview={{ mask: 'Xem ảnh' }}
                  />
                ))}
              </div>
            </Image.PreviewGroup>
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={<span style={{ color: '#8c8c8c' }}>Không có hình ảnh bằng chứng</span>}
            />
          )}
        </Card>

        {/* ── Reporter + Target ── */}
        <Row gutter={16} style={{ marginBottom: 0 }}>
          <Col xs={24} md={12} style={{ marginBottom: 16 }}>
            <Card
              className="section-card"
              style={{ marginBottom: 0, height: '100%' }}
              title={<Space><UserOutlined /><span>Người báo cáo</span></Space>}
            >
              <div className="person-card-body">
                <Avatar
                  size={52}
                  icon={<UserOutlined />}
                  style={{ backgroundColor: '#eef2f7', color: '#6b7280', border: 0, boxShadow: 'none', flexShrink: 0 }}
                />
                <div className="person-card-info">
                  <div className="person-card-name">{report.reporterName || 'Người dùng'}</div>
                  <Text type="secondary" style={{ fontSize: 13 }}>ID: {report.reporterId}</Text>
                </div>
              </div>
              <Button block icon={<UserOutlined />} onClick={() => navigate(`/admin/users/${report.reporterId}`)}>
                Xem hồ sơ
              </Button>
            </Card>
          </Col>

          <Col xs={24} md={12} style={{ marginBottom: 16 }}>
            {report.targetType === 'USER' && userInfo ? (
              <Card
                className="section-card"
                style={{ marginBottom: 0, height: '100%' }}
                title={<Space><ExclamationCircleOutlined style={{ color: '#ff4d4f' }} /><span>Người dùng bị báo cáo</span></Space>}
              >
                <div className="person-card-body">
                  <Avatar
                    size={52}
                    src={resolveAvatarUrl(userInfo.avatarUrl)}
                    icon={!userInfo.avatarUrl ? <UserOutlined /> : undefined}
                    style={{ backgroundColor: '#eef2f7', color: '#6b7280', border: 0, boxShadow: 'none', flexShrink: 0 }}
                  />
                  <div className="person-card-info">
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8, flexWrap: 'wrap' }}>
                      <span className="person-card-name">{userInfo.fullName || 'Người dùng'}</span>
                      {userInfo.isBlocked && <Tag color="red" style={{ fontSize: 11, margin: 0 }}>Đã khóa</Tag>}
                    </div>
                    {userInfo.email && (
                      <Text type="secondary" style={{ display: 'block', fontSize: 13 }}>{userInfo.email}</Text>
                    )}
                    <Text type="secondary" style={{ fontSize: 13 }}>ID: {report.targetId}</Text>
                  </div>
                </div>

                <div className="user-stats-grid">
                  <div className="user-stat-item">
                    <span className="user-stat-value" style={{ color: '#faad14' }}>
                      {typeof userInfo.averageRating === 'number' ? userInfo.averageRating.toFixed(1) : '—'}
                    </span>
                    <span className="user-stat-label">Rating</span>
                  </div>
                  <div className="user-stat-item">
                    <span className="user-stat-value" style={{ color: '#1890ff' }}>
                      {userInfo.reputationScore != null ? Math.round(userInfo.reputationScore as number) : '—'}
                    </span>
                    <span className="user-stat-label">Uy tín</span>
                  </div>
                  {typeof userInfo.reputationScore === 'number' && (
                    <div className="user-stat-item">
                      <ReputationTier score={userInfo.reputationScore} />
                      <span className="user-stat-label">Mức</span>
                    </div>
                  )}
                </div>

                <Space direction="vertical" style={{ width: '100%', marginTop: 8 }}>
                  <Button block icon={<UserOutlined />} onClick={() => navigate(`/admin/users/${report.targetId}`)}>
                    Xem hồ sơ
                  </Button>
                  {!userInfo.isBlocked && (
                    <Popconfirm
                      title="Khóa tài khoản"
                      description="Bạn có chắc muốn khóa tài khoản này?"
                      onConfirm={() => handleBlockUser(report.targetId)}
                      okText="Khóa"
                      cancelText="Huỷ"
                      okButtonProps={{ danger: true }}
                    >
                      <Button block icon={<StopOutlined />} danger loading={actionLoading === 'block'}>
                        Khóa tài khoản
                      </Button>
                    </Popconfirm>
                  )}
                </Space>
              </Card>
            ) : report.targetType === 'POST' && postInfo ? (
              <Card
                className="section-card"
                style={{ marginBottom: 0, height: '100%' }}
                title={<Space><FileTextOutlined style={{ color: '#1890ff' }} /><span>Bài viết bị báo cáo</span></Space>}
              >
                <div style={{ display: 'flex', alignItems: 'center', gap: 10, marginBottom: 12 }}>
                  <Avatar
                    size={36}
                    icon={<UserOutlined />}
                    style={{ backgroundColor: '#eef2f7', color: '#6b7280', border: 0, boxShadow: 'none', flexShrink: 0 }}
                  />
                  <div style={{ flex: 1, minWidth: 0 }}>
                    <Text strong style={{ fontSize: 14 }}>{postInfo.authorName || 'Ẩn danh'}</Text>
                    <Text type="secondary" style={{ display: 'block', fontSize: 12 }}>
                      {postInfo.createdAt ? dayjs(postInfo.createdAt).format('DD/MM/YYYY HH:mm') : '—'}
                    </Text>
                  </div>
                  <Tag color={postInfo.archived ? 'red' : 'green'} style={{ margin: 0, flexShrink: 0 }}>
                    {postInfo.archived ? 'Đã ẩn' : 'Hiển thị'}
                  </Tag>
                </div>

                <Paragraph
                  ellipsis={{ rows: 4, expandable: true, symbol: 'Xem thêm' }}
                  style={{ fontSize: 14, lineHeight: 1.6, color: '#262626', marginBottom: 12 }}
                >
                  {postInfo.content || '—'}
                </Paragraph>

                {postInfo.imageUrl && (
                  <div style={{ marginBottom: 12 }}>
                    <img
                      src={postInfo.imageUrl}
                      alt="Post"
                      style={{ width: '100%', maxHeight: 180, objectFit: 'cover', borderRadius: 8, border: '1px solid #f0f0f0' }}
                    />
                  </div>
                )}

                <Space wrap size={[6, 6]} style={{ marginBottom: 12 }}>
                  {postInfo.interestTag && <Tag color="blue">{cleanTagText(postInfo.interestTag)}</Tag>}
                  {postInfo.location && <Tag color="green">{postInfo.location}</Tag>}
                  {postInfo.maxMembers && <Tag color="purple">{postInfo.maxMembers} người</Tag>}
                  {postInfo.startTime && (
                    <Tag color="orange">{dayjs(postInfo.startTime).format('DD/MM/YYYY')}</Tag>
                  )}
                </Space>

                {!postInfo.archived && (
                  <Popconfirm
                    title="Ẩn bài viết"
                    description="Bài viết sẽ bị ẩn khỏi nền tảng?"
                    onConfirm={handleHidePost}
                    okText="Ẩn"
                    cancelText="Huỷ"
                  >
                    <Button block icon={<EyeInvisibleOutlined />} loading={actionLoading === 'hide'} style={{ marginBottom: 8 }}>
                      Ẩn bài viết
                    </Button>
                  </Popconfirm>
                )}
                <Popconfirm
                  title="Xóa vĩnh viễn"
                  description="Bài viết sẽ bị xóa vĩnh viễn, không thể phục hồi?"
                  onConfirm={handleDeletePost}
                  okText="Xóa"
                  cancelText="Huỷ"
                  okButtonProps={{ danger: true }}
                >
                  <Button block icon={<DeleteOutlined />} danger loading={actionLoading === 'delete'}>
                    Xóa bài viết
                  </Button>
                </Popconfirm>

                <div style={{ marginTop: 8, fontSize: 12, color: '#bfbfbf' }}>
                  Post ID: {report.targetId} · Author ID: {postInfo.authorId}
                </div>
              </Card>
            ) : report.targetType === 'REVIEW' && reviewInfo ? (
              /* ── REVIEW: Hiển thị nhận xét bị tố cáo ── */
              <Card
                className="section-card"
                style={{ marginBottom: 0, height: '100%' }}
                title={
                  <Space>
                    <ExclamationCircleOutlined style={{ color: '#faad14' }} />
                    <span>Nhận xét bị tố cáo</span>
                  </Space>
                }
              >
                {/* Người viết nhận xét vu khống */}
                <div style={{ marginBottom: 12, padding: '10px 12px', background: '#fff1f0', borderRadius: 8, border: '1px solid #ffd8d8' }}>
                  <Text style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6, color: '#cf1322', fontWeight: 600 }}>
                    Người viết nhận xét (sẽ bị phạt khi duyệt)
                  </Text>
                  <div style={{ marginTop: 4 }}>
                    <Text strong>ID: {report.reporterId}</Text>
                    <Text type="secondary" style={{ marginLeft: 8, fontSize: 13 }}>{report.reporterName}</Text>
                  </div>
                </div>

                {/* Nạn nhân bị vu khống */}
                <div style={{ marginBottom: 12, padding: '10px 12px', background: '#f6ffed', borderRadius: 8, border: '1px solid #b7eb8f' }}>
                  <Text style={{ fontSize: 11, textTransform: 'uppercase', letterSpacing: 0.6, color: '#389e0d', fontWeight: 600 }}>
                    Nạn nhân (điểm sẽ được hoàn lại khi duyệt)
                  </Text>
                  <div style={{ marginTop: 4 }}>
                    <Text strong>{reviewInfo.reviewedUserName || 'Người dùng'}</Text>
                    <Text type="secondary" style={{ marginLeft: 8, fontSize: 13 }}>
                      ID: {reviewInfo.reviewedUserId}
                    </Text>
                  </div>
                </div>

                {/* Nội dung nhận xét bị tố cáo */}
                <div style={{ padding: '12px', background: '#fafafa', borderRadius: 8, border: '1px solid #f0f0f0', marginBottom: 12 }}>
                  {/* Rating sao */}
                  <div style={{ marginBottom: 8 }}>
                    {Array.from({ length: 5 }).map((_, i) => (
                      <span key={i} style={{ color: i < (reviewInfo.rating ?? 0) ? '#faad14' : '#d9d9d9', fontSize: 18 }}>★</span>
                    ))}
                    <Text type="secondary" style={{ marginLeft: 8, fontSize: 13 }}>
                      {reviewInfo.rating}/5 sao
                    </Text>
                  </div>

                  {/* Nhãn uy tín */}
                  {reviewInfo.reputationLabel && (
                    <Tag color="orange" style={{ marginBottom: 8 }}>{reviewInfo.reputationLabel}</Tag>
                  )}

                  {/* Bình luận */}
                  <Paragraph
                    style={{ margin: 0, fontSize: 14, lineHeight: 1.6, color: '#262626', fontStyle: 'italic' }}
                  >
                    "{reviewInfo.comment || 'Không có bình luận'}"
                  </Paragraph>

                  {/* Hoạt động liên quan */}
                  {reviewInfo.activityName && (
                    <Text type="secondary" style={{ display: 'block', fontSize: 12, marginTop: 8 }}>
                      Hoạt động: {reviewInfo.activityName}
                    </Text>
                  )}
                </div>

                <Button block icon={<UserOutlined />} onClick={() => navigate(`/admin/users/${reviewInfo.reviewedUserId}`)}>
                  Xem hồ sơ nạn nhân
                </Button>
              </Card>
            ) : (
              <Card className="section-card" style={{ marginBottom: 0, height: '100%' }}>
                <Empty description="Không có thông tin đối tượng" />
              </Card>
            )}
          </Col>
        </Row>

        {/* ── Lịch sử xử lý ── */}
        {isClosed && (
          <Card
            className="section-card"
            title={<Space><SafetyCertificateOutlined style={{ color: '#52c41a' }} /><span>Lịch sử xử lý</span></Space>}
          >
            <div className="history-timeline">
              <div className="timeline-item">
                <div className="timeline-dot" style={{ background: '#1890ff' }} />
                <div className="timeline-content">
                  <div className="timeline-title">Báo cáo được tạo</div>
                  <div className="timeline-meta">{dayjs(report.createdAt).format('DD/MM/YYYY HH:mm')}</div>
                </div>
              </div>
              {report.reviewedAt && (
                <div className="timeline-item">
                  <div className="timeline-dot" style={{ background: report.status === 'VALID' ? '#52c41a' : '#ff4d4f' }} />
                  <div className="timeline-content">
                    <div className="timeline-title">
                      {report.status === 'VALID' ? 'Xác nhận vi phạm' : 'Từ chối báo cáo'}
                    </div>
                    <div className="timeline-meta">{dayjs(report.reviewedAt).format('DD/MM/YYYY HH:mm')}</div>
                    {report.status === 'VALID' && report.penaltyPoint !== undefined && (
                      <div style={{ marginTop: 6 }}>
                        <Tag color="red">-{report.penaltyPoint} điểm uy tín</Tag>
                      </div>
                    )}
                    {report.adminAction && (
                      <div style={{ marginTop: 6 }}>
                        <Text type="secondary" style={{ fontSize: 12 }}>Ghi chú: </Text>
                        <Text style={{ fontSize: 12 }}>{report.adminAction}</Text>
                      </div>
                    )}
                    {report.reviewedBy && (
                      <div className="timeline-meta" style={{ marginTop: 4 }}>Admin ID: {report.reviewedBy}</div>
                    )}
                  </div>
                </div>
              )}
            </div>
          </Card>
        )}

        {/* ── Khu vực xử lý (chỉ hiện khi chưa đóng) ── */}
        {!isClosed && (
          <Card
            className="section-card moderation-workbench-card"
            title={<Space><SafetyCertificateOutlined style={{ color: '#1677ff' }} /><span>Xử lý báo cáo</span></Space>}
          >
            <div className="moderation-decision-section">
              <div className="section-kicker">Quyết định báo cáo</div>
              <div className="decision-segmented" role="radiogroup" aria-label="Quyết định báo cáo">
                <button
                  type="button"
                  className={['decision-option', decision === 'VALID' ? 'is-active' : ''].filter(Boolean).join(' ')}
                  onClick={() => setDecision('VALID')}
                  aria-pressed={decision === 'VALID'}
                >
                  <CheckCircleOutlined className="decision-option-icon" />
                  <span>Xác nhận vi phạm</span>
                </button>
                <button
                  type="button"
                  className={['decision-option', decision === 'REJECTED' ? 'is-active' : ''].filter(Boolean).join(' ')}
                  onClick={() => setDecision('REJECTED')}
                  aria-pressed={decision === 'REJECTED'}
                >
                  <CloseCircleOutlined className="decision-option-icon" />
                  <span>Từ chối báo cáo</span>
                </button>
              </div>
            </div>

            {decision === 'VALID' && (
              <div className="penalty-admin-section">
                <div className="penalty-section-header">
                  <div>
                    <div className="section-kicker">Điểm phạt áp dụng</div>
                    <div className="penalty-section-subtitle">
                      {suggestedPreset.name} · {suggestedPreset.code}
                    </div>
                  </div>
                  <label className="custom-toggle-control">
                    <span>Điều chỉnh tùy chỉnh</span>
                    <Switch
                      checked={isCustomMode}
                      onChange={(checked) => {
                        setIsCustomMode(checked)
                        if (!checked) {
                          setSelectedPenalty(suggestedPreset.points)
                          setAdminNote('')
                        }
                      }}
                    />
                  </label>
                </div>

                <div className="penalty-summary-grid">
                  <div className="penalty-metric">
                    <span className="penalty-metric-label">Điểm đề xuất</span>
                    <span className="penalty-metric-value">{suggestedPenaltyLabel}</span>
                  </div>
                  <div className={['penalty-metric', isPenaltyAdjusted ? 'is-adjusted' : ''].filter(Boolean).join(' ')}>
                    <span className="penalty-metric-label">Điểm áp dụng</span>
                    <span className="penalty-metric-value">{selectedPenaltyLabel}</span>
                  </div>
                </div>

                <div className="penalty-stepper">
                  <Button
                    type="default"
                    icon={<MinusOutlined />}
                    className="penalty-step-button"
                    onClick={() => adjustPoints(5)}
                    disabled={!isCustomMode || selectedPenalty >= wheelMax}
                    aria-label="Giảm thêm 5 điểm"
                  />
                  <div className="penalty-slider-wrap">
                    <Slider
                      min={0}
                      max={wheelMax}
                      step={5}
                      value={selectedPenalty}
                      disabled={!isCustomMode}
                      onChange={(value) => {
                        const nextValue = Array.isArray(value) ? value[0] : value
                        setSelectedPenalty(nextValue)
                      }}
                      tooltip={{ formatter: (value) => value ? `−${value}` : '0' }}
                    />
                    <div className="penalty-range-labels">
                      <span>0</span>
                      <span>−50</span>
                    </div>
                  </div>
                  <Button
                    type="default"
                    icon={<PlusOutlined />}
                    className="penalty-step-button"
                    onClick={() => adjustPoints(-5)}
                    disabled={!isCustomMode || selectedPenalty <= 0}
                    aria-label="Tăng lại 5 điểm"
                  />
                </div>

                {isCustomMode && (
                  <div className="custom-adjustment-notice">
                    <InfoCircleOutlined />
                    <span>Điểm phạt đã được điều chỉnh thủ công. Hệ thống sẽ lưu ghi chú xử lý.</span>
                  </div>
                )}

                <div className="admin-note-box">
                  <div className="admin-note-label-row">
                    <div>
                      <Text strong style={{ fontSize: 13 }}>Ghi chú xử lý</Text>
                      {requiresAdminNote && (
                        <Text type="danger" style={{ fontSize: 13 }}> *</Text>
                      )}
                      <Text type="secondary" style={{ display: 'block', fontSize: 12, marginTop: 2 }}>
                        {requiresAdminNote
                          ? 'Bắt buộc khi điểm áp dụng khác điểm đề xuất.'
                          : 'Tuỳ chọn cho ghi chú nội bộ.'}
                      </Text>
                    </div>
                    <span className="admin-note-counter">{adminNote.length}/300</span>
                  </div>
                  <Input.TextArea
                    value={adminNote}
                    onChange={(e) => setAdminNote(e.target.value)}
                    placeholder="Nhập lý do điều chỉnh hoặc ghi chú nội bộ..."
                    rows={4}
                    maxLength={300}
                    status={requiresAdminNote && adminNote.trim().length < 10 ? 'error' : undefined}
                  />
                  {requiresAdminNote && adminNote.trim().length > 0 && adminNote.trim().length < 10 && (
                    <Text type="danger" style={{ fontSize: 12, marginTop: 6, display: 'block' }}>
                      Ghi chú cần tối thiểu 10 ký tự để lưu điều chỉnh.
                    </Text>
                  )}
                </div>
              </div>
            )}

            <Divider className="moderation-action-divider" />
            <div className="action-footer moderation-action-footer">
              <Button
                size="large"
                onClick={() => {
                  setDecision('')
                  setIsCustomMode(false)
                  setAdminNote('')
                  setSelectedPenalty(0)
                }}
              >
                Hủy
              </Button>
              <Popconfirm
                title={confirmTitle}
                description={confirmDescription}
                onConfirm={() => handleConfirmDecision(suggestedPreset)}
                okText="Xác nhận"
                cancelText="Huỷ"
                okButtonProps={{ danger: decision === 'VALID' && selectedPenalty >= 25 }}
                disabled={!canSubmitDecision || submitting}
              >
                <Button
                  type="primary"
                  size="large"
                  loading={submitting}
                  disabled={!canSubmitDecision}
                  icon={decision === 'REJECTED' ? <CloseCircleOutlined /> : <CheckCircleOutlined />}
                >
                  {primaryActionLabel}
                </Button>
              </Popconfirm>
            </div>
          </Card>
        )}
      </div>
    </MainLayout>
  )
}
