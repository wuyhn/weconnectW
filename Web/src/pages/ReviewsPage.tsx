import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import {
  Card,
  Table,
  Button,
  Input,
  Select,
  Row,
  Col,
  message,
  Drawer,
  Descriptions,
  Tag,
  Empty,
  Rate,
} from 'antd'
import {
  SearchOutlined,
  EyeOutlined,
  ReloadOutlined,
} from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import LoadingState from '../components/LoadingState'
import { reviewAdminService } from '../services/reviewAdminService'
import { UserReview, PaginationParams } from '../types'
import dayjs from 'dayjs'
import './ReviewsPage.css'

export default function ReviewsPage() {
  const [searchParams] = useSearchParams()
  const [reviews, setReviews] = useState<UserReview[]>([])
  const [loading, setLoading] = useState(true)
  const [search, setSearch] = useState('')
  const [reputationFilter, setReputationFilter] = useState<string | null>(null)
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 })
  const [selectedReview, setSelectedReview] = useState<UserReview | null>(null)
  const [drawerVisible, setDrawerVisible] = useState(false)
  const [reputationLabels, setReputationLabels] = useState<string[]>([])

  useEffect(() => {
    const labels = reviewAdminService.getReputationLabels()
    setReputationLabels(labels)
    loadReviews(1)
  }, [])

  useEffect(() => {
    loadReviews(1)
  }, [search, reputationFilter])

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

  const loadReviews = async (page: number) => {
    try {
      setLoading(true)
      const params: PaginationParams = { page, pageSize: 10 }
      const result = await reviewAdminService.getReviews(params, {
        search,
        reputationLabel: reputationFilter,
      })
      setReviews(result.data)
      setPagination({ current: page, pageSize: 10, total: result.total })
    } catch (error) {
      message.error('Failed to load reviews')
    } finally {
      setLoading(false)
    }
  }

  const getReputationColor = (label: string) => {
    const colorMap: Record<string, string> = {
      'Đáng tin cậy': '#52c41a',
      'Nhiệt tình': '#1890ff',
      'Bình thường': '#faad14',
      'Không tốt': '#ff4d4f',
      Excellent: '#52c41a',
      Good: '#1890ff',
      Average: '#faad14',
      Poor: '#ff4d4f',
    }
    return colorMap[label] || '#262626'
  }

  const getReputationRating = (label: string) => {
    const ratingMap: Record<string, number> = {
      'Đáng tin cậy': 5,
      'Nhiệt tình': 4,
      'Bình thường': 3,
      'Không tốt': 2,
      Excellent: 5,
      Good: 4,
      Average: 3,
      Poor: 2,
    }
    return ratingMap[label] || 3
  }

  const columns = [
    {
      title: 'ID',
      dataIndex: 'id',
      key: 'id',
      width: 80,
      sorter: (a: UserReview, b: UserReview) => a.id - b.id,
    },
    {
      title: 'Activity Name',
      dataIndex: 'activityName',
      key: 'activityName',
      width: 180,
      render: (text: string) => (
        <div
          style={{
            whiteSpace: 'normal',
            wordBreak: 'break-word',
            lineHeight: '1.5',
          }}
        >
          {text}
        </div>
      ),
    },
    {
      title: 'Reputation Label',
      dataIndex: 'reputationLabel',
      key: 'reputationLabel',
      width: 120,
      render: (label: string) => (
        <Tag
          color={getReputationColor(label)}
          style={{
            color: 'white',
            fontWeight: 600,
          }}
        >
          {label}
        </Tag>
      ),
      sorter: (a: UserReview, b: UserReview) =>
        getReputationRating(b.reputationLabel) - getReputationRating(a.reputationLabel),
    },
    {
      title: 'Comment',
      dataIndex: 'comment',
      key: 'comment',
      width: 280,
      render: (text: string) => (
        <div
          style={{
            whiteSpace: 'normal',
            wordBreak: 'break-word',
            lineHeight: '1.5',
          }}
        >
          {text}
        </div>
      ),
    },
    {
      title: 'Reviewer',
      key: 'reviewerName',
      width: 130,
      render: (_: any, record: UserReview) => (
        <div>
          <div style={{ fontWeight: 500 }}>{record.reviewerName || 'Unknown'}</div>
          <div style={{ fontSize: '12px', color: '#8c8c8c' }}>ID: {record.reviewerId}</div>
        </div>
      ),
    },
    {
      title: 'Reviewed User',
      key: 'reviewedUserName',
      width: 130,
      render: (_: any, record: UserReview) => (
        <div>
          <div style={{ fontWeight: 500 }}>{record.reviewedUserName || 'Unknown'}</div>
          <div style={{ fontSize: '12px', color: '#8c8c8c' }}>ID: {record.reviewedUserId}</div>
        </div>
      ),
    },
    {
      title: 'Created',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 130,
      render: (date: string) => dayjs(date).format('MMM DD, YYYY'),
      sorter: (a: UserReview, b: UserReview) =>
        new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
    },
    {
      title: 'Actions',
      key: 'actions',
      width: 100,
      fixed: 'right' as const,
      render: (_: any, record: UserReview) => (
        <Button
          type="primary"
          size="small"
          icon={<EyeOutlined />}
          onClick={() => {
            setSelectedReview(record)
            setDrawerVisible(true)
          }}
        >
          View
        </Button>
      ),
    },
  ]

  if (loading && reviews.length === 0) {
    return <LoadingState fullPage message="Loading reviews..." />
  }

  return (
    <MainLayout>
      <div className="reviews-page">
        <Card className="reviews-card">
          {/* Header */}
          <Row justify="space-between" align="middle" style={{ marginBottom: '24px' }}>
            <Col>
              <h2 style={{ margin: 0 }}>Review Management</h2>
            </Col>
            <Col>
              <Button
                icon={<ReloadOutlined />}
                onClick={() => loadReviews(1)}
                loading={loading}
              >
                Refresh
              </Button>
            </Col>
          </Row>

          {/* Filters and Search */}
          <Row gutter={[16, 16]} style={{ marginBottom: '24px' }} className="filters-row">
            <Col xs={24} sm={24} md={14}>
              <Input
                placeholder="Search by activity name or comment"
                prefix={<SearchOutlined />}
                value={search}
                onChange={(e) => setSearch(e.target.value)}
                allowClear
              />
            </Col>
            <Col xs={24} sm={24} md={10}>
              <Select
                placeholder="Filter by reputation label"
                value={reputationFilter}
                onChange={setReputationFilter}
                style={{ width: '100%' }}
                allowClear
                options={reputationLabels.map((label) => ({
                  label: (
                    <span>
                      <Tag
                        color={getReputationColor(label)}
                        style={{ color: 'white', marginRight: '8px' }}
                      >
                        {label}
                      </Tag>
                    </span>
                  ),
                  value: label,
                }))}
              />
            </Col>
          </Row>

          {/* Table */}
          <Table
            columns={columns}
            dataSource={reviews.map((r) => ({ ...r, key: r.id }))}
            loading={loading}
            pagination={{
              current: pagination.current,
              pageSize: pagination.pageSize,
              total: pagination.total,
              showSizeChanger: false,
              showTotal: (total) => `Total ${total} reviews`,
              onChange: (page) => loadReviews(page),
            }}
            scroll={{ x: 1300 }}
            locale={{
              emptyText: (
                <Empty description="No reviews found" style={{ marginTop: '48px' }} />
              ),
            }}
          />
        </Card>

        {/* Review Detail Drawer */}
        <Drawer
          title="Review Details"
          placement="right"
          onClose={() => {
            setDrawerVisible(false)
            setSelectedReview(null)
          }}
          open={drawerVisible}
          width={500}
        >
          {selectedReview && (
            <div className="review-detail">
              <Descriptions bordered size="small" layout="vertical">
                <Descriptions.Item label="ID">{selectedReview.id}</Descriptions.Item>
                <Descriptions.Item label="Reviewer ID">{selectedReview.reviewerId}</Descriptions.Item>
                <Descriptions.Item label="Reviewed User ID">
                  {selectedReview.reviewedUserId}
                </Descriptions.Item>
                <Descriptions.Item label="Activity Name">
                  {selectedReview.activityName}
                </Descriptions.Item>
                <Descriptions.Item label="Reputation Label">
                  <Tag
                    color={getReputationColor(selectedReview.reputationLabel)}
                    style={{ color: 'white' }}
                  >
                    {selectedReview.reputationLabel}
                  </Tag>
                </Descriptions.Item>
                <Descriptions.Item label="Rating">
                  <Rate
                    disabled
                    value={getReputationRating(selectedReview.reputationLabel)}
                    style={{ color: '#faad14' }}
                  />
                </Descriptions.Item>
                <Descriptions.Item label="Comment">
                  <p style={{ whiteSpace: 'pre-wrap', margin: 0 }}>
                    {selectedReview.comment}
                  </p>
                </Descriptions.Item>
                <Descriptions.Item label="Created">
                  {dayjs(selectedReview.createdAt).format('MMMM DD, YYYY HH:mm')}
                </Descriptions.Item>
              </Descriptions>
            </div>
          )}
        </Drawer>
      </div>
    </MainLayout>
  )
}
