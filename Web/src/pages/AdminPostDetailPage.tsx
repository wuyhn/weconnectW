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
  Image,
  Avatar,
} from 'antd'
import { ArrowLeftOutlined, DeleteOutlined } from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import { postAdminService } from '../services/postAdminService'
import { userAdminService } from '../services/userAdminService'
import { Post, User } from '../types'
import dayjs from 'dayjs'
import './AdminPostDetailPage.css'

export default function AdminPostDetailPage() {
  const { id } = useParams<{ id: string }>()
  const navigate = useNavigate()
  const [post, setPost] = useState<Post | null>(null)
  const [author, setAuthor] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)
  const [actionLoading, setActionLoading] = useState(false)

  useEffect(() => {
    if (id) {
      loadPost(parseInt(id))
    }
  }, [id])

  const loadPost = async (postId: number) => {
    try {
      setLoading(true)
      const postData = await postAdminService.getPost(postId)
      setPost(postData)

      // Load author info
      try {
        const authorData = await userAdminService.getUser(postData.authorId)
        setAuthor(authorData)
      } catch (error) {
        console.error('Failed to load author', error)
      }
    } catch (error: any) {
      message.error(error?.message || 'Failed to load post')
      navigate('/posts')
    } finally {
      setLoading(false)
    }
  }

  const handleDeletePost = async () => {
    if (!post) return
    try {
      setActionLoading(true)
      await postAdminService.deletePost(post.id)
      message.success('Post deleted successfully')
      navigate('/posts')
    } catch (error: any) {
      message.error(error?.message || 'Failed to delete post')
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

  if (!post) {
    return (
      <MainLayout>
        <Card>
          <Empty description="Post not found" style={{ marginTop: '48px' }} />
          <div style={{ textAlign: 'center', marginTop: '24px' }}>
            <Button type="primary" onClick={() => navigate('/posts')}>
              Back to Posts
            </Button>
          </div>
        </Card>
      </MainLayout>
    )
  }

  return (
    <MainLayout>
      <div className="admin-post-detail-page">
        {/* Back Button */}
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/posts')}
          style={{ marginBottom: '16px' }}
        >
          Back to Posts
        </Button>

        {/* Post Header Card */}
        <Card className="post-header-card" style={{ marginBottom: '24px' }}>
          <Row gutter={24} style={{ marginBottom: '16px' }}>
            <Col xs={24}>
              <div className="post-header-content">
                <div style={{ marginBottom: '16px' }}>
                  <h1 style={{ margin: '0 0 8px 0', fontSize: '24px' }}>
                    {post.content.substring(0, 100)}
                    {post.content.length > 100 ? '...' : ''}
                  </h1>
                  <p style={{ margin: 0, color: '#999', fontSize: '14px' }}>Post ID: {post.id}</p>
                </div>

                {/* Action Buttons */}
                <Space>
                  <Popconfirm
                    title="Delete Post"
                    description="Are you sure you want to delete this post? This action cannot be undone."
                    onConfirm={handleDeletePost}
                    okText="Yes"
                    cancelText="No"
                    okButtonProps={{ danger: true }}
                  >
                    <Button
                      icon={<DeleteOutlined />}
                      loading={actionLoading}
                      danger
                    >
                      Delete
                    </Button>
                  </Popconfirm>
                </Space>
              </div>
            </Col>
          </Row>

          {/* Author Info */}
          {author && (
            <div className="author-info">
              <h3 style={{ marginBottom: '12px' }}>Author</h3>
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
                onClick={() => navigate(`/admin/users/${author.id}`)}
              >
                <Avatar size={48} src={author.avatarUrl} />
                <div>
                  <div style={{ fontWeight: 500 }}>{author.fullName}</div>
                  <div style={{ fontSize: '12px', color: '#999' }}>{author.email}</div>
                </div>
              </div>
            </div>
          )}
        </Card>

        {/* Post Image */}
        {post.imageUrl && (
          <Card title="Post Image" style={{ marginBottom: '24px' }}>
            <div style={{ textAlign: 'center' }}>
              <Image
                src={post.imageUrl}
                alt="Post"
                style={{ maxWidth: '100%', maxHeight: '400px', borderRadius: '6px' }}
              />
            </div>
          </Card>
        )}

        {/* Post Details */}
        <Card title="Post Details" style={{ marginBottom: '24px' }}>
          <Descriptions bordered size="small" layout="vertical">
            <Descriptions.Item label="ID">{post.id}</Descriptions.Item>
            <Descriptions.Item label="Content">{post.content}</Descriptions.Item>
            <Descriptions.Item label="Interest Tag">
              <Tag>{post.interestTag}</Tag>
            </Descriptions.Item>
            <Descriptions.Item label="Location">{post.location}</Descriptions.Item>
            <Descriptions.Item label="Max Members">{post.maxMembers}</Descriptions.Item>
            <Descriptions.Item label="Start Time">
              {dayjs(post.startTime).format('MMMM DD, YYYY HH:mm')}
            </Descriptions.Item>
            <Descriptions.Item label="End Time">
              {dayjs(post.endTime).format('MMMM DD, YYYY HH:mm')}
            </Descriptions.Item>
            <Descriptions.Item label="Created At">
              {dayjs(post.createdAt).format('MMMM DD, YYYY HH:mm')}
            </Descriptions.Item>
          </Descriptions>
        </Card>
      </div>
    </MainLayout>
  )
}
