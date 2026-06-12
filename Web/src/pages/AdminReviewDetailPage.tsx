import { useEffect, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Button,
  Space,
  Descriptions,
  Tag,
  Empty,
  Spin,
  message,
  Row,
  Col,
  Popconfirm,
  Avatar,
  Divider,
} from 'antd'
import { ArrowLeftOutlined, DeleteOutlined, StarOutlined, UserOutlined } from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import { reviewAdminService } from '../services/reviewAdminService'
import { userAdminService } from '../services/userAdminService'
import { UserReview, User } from '../types'
import dayjs from 'dayjs'
import './AdminReviewDetailPage.css'
import { resolveAvatarUrl } from '../utils/avatar'
import { cleanTagText } from '../utils/text'

interface ReviewWithUsers extends UserReview {
  reviewerData?: User
  reviewedUserData?: User
}

export default function AdminReviewDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [review, setReview] = useState<ReviewWithUsers | null>(null)
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)

  useEffect(() => {
    if (id) {
      loadReview(parseInt(id))
    }
  }, [id])

  const loadReview = async (reviewId: number) => {
    try {
      setLoading(true)
      const reviewData = (await reviewAdminService.getReview(reviewId)) as ReviewWithUsers

      // Load reviewer info
      try {
        const reviewerData = await userAdminService.getUser(reviewData.reviewerId)
        reviewData.reviewerData = reviewerData
      } catch (error) {
        console.error('Failed to load reviewer', error)
      }

      // Load reviewed user info
      try {
        const reviewedUserData = await userAdminService.getUser(reviewData.reviewedUserId)
        reviewData.reviewedUserData = reviewedUserData
      } catch (error) {
        console.error('Failed to load reviewed user', error)
      }

      setReview(reviewData)
    } catch (error: any) {
      message.error(error?.message || 'Failed to load review')
      navigate('/reviews')
    } finally {
      setLoading(false)
    }
  }

  const handleDeleteReview = async () => {
    if (!review) return
    try {
      setActionLoading(true)
      await reviewAdminService.deleteReview(review.id)
      message.success('Review deleted successfully')
      navigate('/reviews')
    } catch (error: any) {
      message.error(error?.message || 'Failed to delete review')
    } finally {
      setActionLoading(false)
    }
  }

  if (loading) {
    return (
      <MainLayout>
        <div style={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '500px' }}>
          <Spin size="large" />
        </div>
      </MainLayout>
    )
  }

  if (!review) {
    return (
      <MainLayout>
        <Card>
          <Empty description="Review not found" style={{ marginTop: '48px' }} />
          <div style={{ textAlign: 'center', marginTop: '24px' }}>
            <Button type="primary" onClick={() => navigate('/reviews')}>
              Back to Reviews
            </Button>
          </div>
        </Card>
      </MainLayout>
    )
  }

  const getReputationColor = (label: string) => {
    switch (label?.toLowerCase()) {
      case 'excellent':
        return 'green'
      case 'good':
        return 'blue'
      case 'average':
        return 'orange'
      case 'poor':
        return 'red'
      default:
        return 'default'
    }
  }

  return (
    <MainLayout>
      <div className="admin-review-detail-page">
        {/* Back Button */}
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/reviews')}
          style={{ marginBottom: '16px' }}
        >
          Back to Reviews
        </Button>

        {/* Review Header Card */}
        <Card className="review-header-card" style={{ marginBottom: '24px' }}>
          <Row gutter={24} style={{ marginBottom: '16px' }}>
            <Col xs={24}>
              <div className="review-header-content">
                <div style={{ marginBottom: '16px' }}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginBottom: '8px' }}>
                    {Array.from({ length: 5 }).map((_, i) => (
                      <StarOutlined
                        key={i}
                        style={{
                          color: i < Math.floor(Math.random() * 5 + 1) ? '#fadb14' : '#d9d9d9',
                          fontSize: '18px',
                        }}
                      />
                    ))}
                  </div>
                  <p style={{ margin: 0, color: '#999', fontSize: '14px' }}>Review ID: {review.id}</p>
                </div>

                <div style={{ marginBottom: '16px' }}>
                  <Tag color={getReputationColor(review.reputationLabel)}>
                    {review.reputationLabel}
                  </Tag>
                </div>

                {/* Delete Button */}
                <Space>
                  <Popconfirm
                    title="Delete Review"
                    description="Are you sure you want to delete this review? This action cannot be undone."
                    onConfirm={handleDeleteReview}
                    okText="Yes"
                    cancelText="No"
                    okButtonProps={{ danger: true }}
                  >
                    <Button
                      icon={<DeleteOutlined />}
                      loading={actionLoading}
                      danger
                    >
                      Delete Review
                    </Button>
                  </Popconfirm>
                </Space>
              </div>
            </Col>
          </Row>

          <Divider />

          {/* Reviewer and Reviewed User Info */}
          <Row gutter={24} style={{ marginTop: '24px' }}>
            <Col xs={24} sm={12}>
              <div>
                <h3 style={{ marginBottom: '12px' }}>Reviewer</h3>
                {review.reviewerData ? (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '12px',
                      padding: '12px',
                      backgroundColor: '#fafafa',
                      borderRadius: '6px',
                      cursor: 'pointer',
                    }}
                    onClick={() => navigate(`/admin/users/${review.reviewerData?.id}`)}
                  >
                    <Avatar
                      size={48}
                      src={resolveAvatarUrl(review.reviewerData.avatarUrl)}
                      icon={!review.reviewerData.avatarUrl ? <UserOutlined /> : undefined}
                      style={{ border: 0, boxShadow: 'none', background: '#eef2f7', color: '#6b7280' }}
                    />
                    <div>
                      <div style={{ fontWeight: 500 }}>{review.reviewerData.fullName}</div>
                      <div style={{ fontSize: '12px', color: '#999' }}>{review.reviewerData.email}</div>
                    </div>
                  </div>
                ) : (
                  <Tag>{review.reviewerName}</Tag>
                )}
              </div>
            </Col>
            <Col xs={24} sm={12}>
              <div>
                <h3 style={{ marginBottom: '12px' }}>Reviewed User</h3>
                {review.reviewedUserData ? (
                  <div
                    style={{
                      display: 'flex',
                      alignItems: 'center',
                      gap: '12px',
                      padding: '12px',
                      backgroundColor: '#fafafa',
                      borderRadius: '6px',
                      cursor: 'pointer',
                    }}
                    onClick={() => navigate(`/admin/users/${review.reviewedUserData?.id}`)}
                  >
                    <Avatar
                      size={48}
                      src={resolveAvatarUrl(review.reviewedUserData.avatarUrl)}
                      icon={!review.reviewedUserData.avatarUrl ? <UserOutlined /> : undefined}
                      style={{ border: 0, boxShadow: 'none', background: '#eef2f7', color: '#6b7280' }}
                    />
                    <div>
                      <div style={{ fontWeight: 500 }}>{review.reviewedUserData.fullName}</div>
                      <div style={{ fontSize: '12px', color: '#999' }}>{review.reviewedUserData.email}</div>
                    </div>
                  </div>
                ) : (
                  <Tag>{review.reviewedUserName}</Tag>
                )}
              </div>
            </Col>
          </Row>
        </Card>

        {/* Review Details */}
        <Card title="Review Details" style={{ marginBottom: '24px' }}>
          <Descriptions bordered size="small" layout="vertical">
            <Descriptions.Item label="ID">{review.id}</Descriptions.Item>
            <Descriptions.Item label="Activity Name">{cleanTagText(review.activityName)}</Descriptions.Item>
            <Descriptions.Item label="Reputation Label">
              <Tag color={getReputationColor(review.reputationLabel)}>
                {review.reputationLabel}
              </Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Comment">{review.comment}</Descriptions.Item>
            <Descriptions.Item label="Created At">
              {dayjs(review.createdAt).format('MMMM DD, YYYY HH:mm')}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      </div>
    </MainLayout>
  )
}
