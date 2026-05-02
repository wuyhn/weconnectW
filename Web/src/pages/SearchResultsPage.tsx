import { useEffect, useState } from 'react'
import { useSearchParams, useNavigate } from 'react-router-dom'
import {
  Card,
  Button,
  Input,
  Empty,
  Tabs,
  List,
  Avatar,
  Tag,
  Popconfirm,
  message,
} from 'antd'
import { SearchOutlined, ArrowLeftOutlined, UserOutlined, FileTextOutlined, CommentOutlined, DeleteOutlined } from '@ant-design/icons'
import MainLayout from '../components/MainLayout'
import StatusBadge from '../components/StatusBadge'
import { userAdminService } from '../services/userAdminService'
import { postAdminService } from '../services/postAdminService'
import { reviewAdminService } from '../services/reviewAdminService'
import { User, Post, UserReview } from '../types'
import dayjs from 'dayjs'
import './SearchResultsPage.css'

export default function SearchResultsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const navigate = useNavigate()
  const query = searchParams.get('q') || ''

  const [users, setUsers] = useState<User[]>([])
  const [posts, setPosts] = useState<Post[]>([])
  const [reviews, setReviews] = useState<UserReview[]>([])
  const [loading, setLoading] = useState(false)
  const [searchValue, setSearchValue] = useState(query)
  const [actionLoading, setActionLoading] = useState(false)

  useEffect(() => {
    if (query.length > 0) {
      performSearch()
    }
  }, [query])

  const performSearch = async () => {
    if (searchValue.length < 2) return

    setLoading(true)
    try {
      const searchTerm = searchValue.toLowerCase()

      // Search users
      try {
        const usersResult = await userAdminService.getUsers(
          { page: 1, pageSize: 100 },
          { search: searchTerm }
        )
        setUsers(usersResult.data)
      } catch (error) {
        console.error('Failed to search users', error)
        setUsers([])
      }

      // Search posts
      try {
        const postsResult = await postAdminService.getPosts(
          { page: 1, pageSize: 100 },
          { search: searchTerm }
        )
        setPosts(postsResult.data)
      } catch (error) {
        console.error('Failed to search posts', error)
        setPosts([])
      }

      // Search reviews
      try {
        const reviewsResult = await reviewAdminService.getReviews(
          { page: 1, pageSize: 100 },
          { search: searchTerm }
        )
        setReviews(reviewsResult.data)
      } catch (error) {
        console.error('Failed to search reviews', error)
        setReviews([])
      }
    } finally {
      setLoading(false)
    }
  }

  const handleSearch = (value: string) => {
    setSearchValue(value)
    if (value.length >= 2) {
      setSearchParams({ q: value })
    }
  }

  const handleUserClick = (userId: number) => {
    navigate(`/admin/users/${userId}`)
  }

  const handlePostClick = (postId: number) => {
    navigate(`/admin/posts/${postId}`)
  }

  const handleReviewClick = (reviewId: number) => {
    navigate(`/admin/reviews/${reviewId}`)
  }

  const handleDeletePost = async (postId: number) => {
    try {
      setActionLoading(true)
      await postAdminService.deletePost(postId)
      message.success('Post deleted successfully')
      // Remove the deleted post from state
      setPosts(posts.filter((p) => p.id !== postId))
    } catch (error: any) {
      message.error(error?.message || 'Failed to delete post')
    } finally {
      setActionLoading(false)
    }
  }

  const userListItems = users.map((user) => (
    <List.Item key={user.id} className="search-result-list-item">
      <List.Item.Meta
        avatar={<Avatar size={48} src={user.avatarUrl} />}
        title={
          <div onClick={() => handleUserClick(user.id)} className="result-title">
            {user.fullName}
          </div>
        }
        description={
          <div>
            <div style={{ fontSize: '12px', color: '#999' }}>{user.email}</div>
            <div style={{ fontSize: '12px', marginTop: '4px' }}>
              <StatusBadge
                status={user.isBlocked ? 'blocked' : 'active'}
                text={user.isBlocked ? 'Blocked' : 'Active'}
              />
              {user.averageRating && (
                <span style={{ marginLeft: '8px', color: '#fadb14' }}>
                  {user.averageRating.toFixed(1)} ⭐
                </span>
              )}
            </div>
          </div>
        }
      />
      <Button type="primary" size="small" onClick={() => handleUserClick(user.id)}>
        View
      </Button>
    </List.Item>
  ))

  const postListItems = posts.map((post) => (
    <List.Item key={post.id} className="search-result-list-item">
      <List.Item.Meta
        title={
          <div onClick={() => handlePostClick(post.id)} className="result-title">
            {post.content.substring(0, 80)}
            {post.content.length > 80 ? '...' : ''}
          </div>
        }
        description={
          <div>
            <div style={{ fontSize: '12px', color: '#999' }}>
              Tag: <Tag color="blue">{post.interestTag}</Tag> | Location: {post.location}
            </div>
            <div style={{ fontSize: '12px', marginTop: '4px' }}>
              Created: {dayjs(post.createdAt).format('MMM DD, YYYY')}
            </div>
          </div>
        }
      />
      <Button type="primary" size="small" onClick={() => handlePostClick(post.id)}>
        View
      </Button>
      <Popconfirm
        title="Delete Post"
        description="Are you sure you want to delete this post? This action cannot be undone."
        onConfirm={() => handleDeletePost(post.id)}
        okText="Yes"
        cancelText="No"
        okButtonProps={{ danger: true }}
      >
        <Button danger size="small" icon={<DeleteOutlined />} loading={actionLoading}>
          Delete
        </Button>
      </Popconfirm>
    </List.Item>
  ))

  const reviewListItems = reviews.map((review) => (
    <List.Item key={review.id} className="search-result-list-item">
      <List.Item.Meta
        title={
          <div onClick={() => handleReviewClick(review.id)} className="result-title">
            {review.comment.substring(0, 80)}
            {review.comment.length > 80 ? '...' : ''}
          </div>
        }
        description={
          <div>
            <div style={{ fontSize: '12px', color: '#999' }}>
              By: {review.reviewerName} | Activity: {review.activityName}
            </div>
            <div style={{ fontSize: '12px', marginTop: '4px' }}>
              <Tag color="orange">{review.reputationLabel}</Tag>
              <span style={{ marginLeft: '8px', color: '#999' }}>
                {dayjs(review.createdAt).format('MMM DD, YYYY')}
              </span>
            </div>
          </div>
        }
      />
      <Button type="primary" size="small" onClick={() => handleReviewClick(review.id)}>
        View
      </Button>
    </List.Item>
  ))

  const tabItems = [
    {
      key: 'users',
      label: `Users (${users.length})`,
      icon: <UserOutlined />,
      children:
        users.length > 0 ? (
          <List dataSource={userListItems} renderItem={(item) => item} />
        ) : (
          <Empty description="No users found" style={{ marginTop: '24px' }} />
        ),
    },
    {
      key: 'posts',
      label: `Posts (${posts.length})`,
      icon: <FileTextOutlined />,
      children:
        posts.length > 0 ? (
          <List dataSource={postListItems} renderItem={(item) => item} />
        ) : (
          <Empty description="No posts found" style={{ marginTop: '24px' }} />
        ),
    },
    {
      key: 'reviews',
      label: `Reviews (${reviews.length})`,
      icon: <CommentOutlined />,
      children:
        reviews.length > 0 ? (
          <List dataSource={reviewListItems} renderItem={(item) => item} />
        ) : (
          <Empty description="No reviews found" style={{ marginTop: '24px' }} />
        ),
    },
  ]

  return (
    <MainLayout>
      <div className="search-results-page">
        {/* Back Button */}
        <Button
          type="text"
          icon={<ArrowLeftOutlined />}
          onClick={() => navigate('/dashboard')}
          style={{ marginBottom: '16px' }}
        >
          Back to Dashboard
        </Button>

        {/* Search Card */}
        <Card style={{ marginBottom: '24px' }}>
          <Input
            size="large"
            prefix={<SearchOutlined />}
            placeholder="Search Users, Posts, Reviews..."
            value={searchValue}
            onChange={(e) => handleSearch(e.target.value)}
            allowClear
            style={{ maxWidth: '500px' }}
          />
        </Card>

        {/* Results */}
        <Card loading={loading}>
          {searchValue.length < 2 ? (
            <Empty
              description="Enter at least 2 characters to search"
              style={{ marginTop: '48px' }}
            />
          ) : users.length === 0 && posts.length === 0 && reviews.length === 0 ? (
            <Empty description="No results found" style={{ marginTop: '48px' }} />
          ) : (
            <Tabs items={tabItems} defaultActiveKey="users" />
          )}
        </Card>
      </div>
    </MainLayout>
  )
}
