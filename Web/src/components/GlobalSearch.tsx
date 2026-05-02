import { useState, useCallback, useMemo } from 'react'
import { AutoComplete, Spin, Empty, Tag } from 'antd'
import { UserOutlined, FileTextOutlined, CommentOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { userAdminService } from '../services/userAdminService'
import { postAdminService } from '../services/postAdminService'
import { reviewAdminService } from '../services/reviewAdminService'
import { User, Post, UserReview } from '../types'
import './GlobalSearch.css'

interface SearchResult {
  type: 'user' | 'post' | 'review'
  id: number
  label: string
  description: string
  data: User | Post | UserReview
}

export const GlobalSearch: React.FC = () => {
  const navigate = useNavigate()
  const [searchValue, setSearchValue] = useState('')
  const [loading, setLoading] = useState(false)
  const [results, setResults] = useState<SearchResult[]>([])

  // Debounce search
  const handleSearch = useCallback(
    async (value: string) => {
      setSearchValue(value)

      if (value.length < 2) {
        setResults([])
        return
      }

      setLoading(true)
      try {
        const searchTerm = value.toLowerCase()
        const allResults: SearchResult[] = []

        // Search users
        try {
          const usersResult = await userAdminService.getUsers(
            { page: 1, pageSize: 100 },
            { search: searchTerm }
          )
          usersResult.data.slice(0, 5).forEach((user) => {
            allResults.push({
              type: 'user',
              id: user.id,
              label: user.fullName,
              description: user.email,
              data: user,
            })
          })
        } catch (error) {
          console.error('Failed to search users', error)
        }

        // Search posts
        try {
          const postsResult = await postAdminService.getPosts(
            { page: 1, pageSize: 100 },
            { search: searchTerm }
          )
          postsResult.data.slice(0, 5).forEach((post) => {
            allResults.push({
              type: 'post',
              id: post.id,
              label: post.content.substring(0, 50),
              description: `Tag: ${post.interestTag} | Location: ${post.location}`,
              data: post,
            })
          })
        } catch (error) {
          console.error('Failed to search posts', error)
        }

        // Search reviews
        try {
          const reviewsResult = await reviewAdminService.getReviews(
            { page: 1, pageSize: 100 },
            { search: searchTerm }
          )
          reviewsResult.data.slice(0, 5).forEach((review) => {
            allResults.push({
              type: 'review',
              id: review.id,
              label: review.comment.substring(0, 50),
              description: `Activity: ${review.activityName} | By: ${review.reviewerName}`,
              data: review,
            })
          })
        } catch (error) {
          console.error('Failed to search reviews', error)
        }

        setResults(allResults)
      } finally {
        setLoading(false)
      }
    },
    []
  )

  // Group results by type
  const groupedOptions = useMemo(() => {
    if (results.length === 0 && searchValue.length >= 2) {
      return [
        {
          label: 'No Results',
          options: [
            {
              label: 'Không tìm thấy kết quả',
              value: 'no-results',
              disabled: true,
            },
          ],
        },
      ]
    }

    const grouped: Record<
      string,
      Array<{
        label: React.ReactNode
        value: string
        data: SearchResult
      }>
    > = {
      users: [],
      posts: [],
      reviews: [],
    }

    results.forEach((result) => {
      const groupKey = result.type === 'user' ? 'users' : result.type === 'post' ? 'posts' : 'reviews'

      grouped[groupKey].push({
        label: (
          <div className="search-result-item">
            <div className="search-result-icon">
              {result.type === 'user' && <UserOutlined />}
              {result.type === 'post' && <FileTextOutlined />}
              {result.type === 'review' && <CommentOutlined />}
            </div>
            <div className="search-result-content">
              <div className="search-result-title">{result.label}</div>
              <div className="search-result-description">{result.description}</div>
            </div>
            <Tag color={result.type === 'user' ? 'blue' : result.type === 'post' ? 'green' : 'orange'}>
              {result.type === 'user' ? 'User' : result.type === 'post' ? 'Post' : 'Review'}
            </Tag>
          </div>
        ),
        value: `${result.type}:${result.id}`,
        data: result,
      })
    })

    const options: any[] = []
    if (grouped.users.length > 0) {
      options.push({
        label: `Users (${grouped.users.length})`,
        options: grouped.users,
      })
    }
    if (grouped.posts.length > 0) {
      options.push({
        label: `Posts (${grouped.posts.length})`,
        options: grouped.posts,
      })
    }
    if (grouped.reviews.length > 0) {
      options.push({
        label: `Reviews (${grouped.reviews.length})`,
        options: grouped.reviews,
      })
    }

    return options
  }, [results, searchValue])

  const handleSelect = (value: string) => {
    const [type, id] = value.split(':')
    if (type === 'user') {
      navigate(`/admin/users/${id}`)
    } else if (type === 'post') {
      navigate(`/admin/posts/${id}`)
    } else if (type === 'review') {
      navigate(`/admin/reviews/${id}`)
    }
    setSearchValue('')
    setResults([])
  }

  const handlePressEnter = () => {
    if (searchValue.length >= 2) {
      navigate(`/search?q=${encodeURIComponent(searchValue)}`)
      setSearchValue('')
      setResults([])
    }
  }

  return (
    <AutoComplete
      className="global-search"
      placeholder="Tìm kiếm người dùng, bài viết, đánh giá..."
      value={searchValue}
      onSearch={handleSearch}
      onSelect={handleSelect}
      options={groupedOptions}
      notFoundContent={
        loading ? (
          <Spin size="small" />
        ) : searchValue.length > 0 ? (
          <Empty description="Không tìm thấy kết quả" style={{ marginTop: '12px' }} />
        ) : null
      }
      allowClear
      style={{ width: '100%', maxWidth: '350px' }}
      onKeyDown={(e) => {
        if (e.key === 'Enter' && searchValue.length >= 2) {
          handlePressEnter()
        }
      }}
    />
  )
}
