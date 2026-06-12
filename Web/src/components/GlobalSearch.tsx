import { useCallback, useMemo, useState } from 'react'
import type { ReactNode } from 'react'
import { AutoComplete, Empty, Input, Spin, Tag } from 'antd'
import { CommentOutlined, FileTextOutlined, SearchOutlined, UserOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { userAdminService } from '../services/userAdminService'
import { postAdminService } from '../services/postAdminService'
import { reviewAdminService } from '../services/reviewAdminService'
import { Post, User, UserReview } from '../types'
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

  const handleSearch = useCallback(async (value: string) => {
    setSearchValue(value)

    if (value.length < 2) {
      setResults([])
      return
    }

    setLoading(true)
    try {
      const searchTerm = value.toLowerCase()
      const allResults: SearchResult[] = []

      try {
        const usersResult = await userAdminService.getUsers({ page: 1, pageSize: 100 }, { search: searchTerm })
        usersResult.data.slice(0, 5).forEach((user) => {
          allResults.push({
            type: 'user',
            id: user.id,
            label: user.fullName,
            description: user.email,
            data: user,
          })
        })
      } catch {}

      try {
        const postsResult = await postAdminService.getPosts({ page: 1, pageSize: 100 }, { search: searchTerm })
        postsResult.data.slice(0, 5).forEach((post) => {
          allResults.push({
            type: 'post',
            id: post.id,
            label: (post.content || 'Bài viết').slice(0, 50),
            description: `Tag: ${post.interestTag || 'Khác'} | Vị trí: ${post.location || '-'}`,
            data: post,
          })
        })
      } catch {}

      try {
        const reviewsResult = await reviewAdminService.getReviews({ page: 1, pageSize: 100 }, { search: searchTerm })
        reviewsResult.data.slice(0, 5).forEach((review) => {
          allResults.push({
            type: 'review',
            id: review.id,
            label: (review.comment || 'Đánh giá').slice(0, 50),
            description: `Bài viết: ${review.activityName || '-'} | Bởi: ${review.reviewerName || '-'}`,
            data: review,
          })
        })
      } catch {}

      setResults(allResults)
    } finally {
      setLoading(false)
    }
  }, [])

  const groupedOptions = useMemo(() => {
    if (results.length === 0 && searchValue.length >= 2) {
      return [
        {
          label: 'Không có kết quả',
          options: [
            {
              label: 'Không tìm thấy kết quả phù hợp',
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
        label: ReactNode
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
            <Tag color={result.type === 'user' ? 'pink' : result.type === 'post' ? 'green' : 'orange'}>
              {result.type === 'user' ? 'Người dùng' : result.type === 'post' ? 'Bài viết' : 'Đánh giá'}
            </Tag>
          </div>
        ),
        value: `${result.type}:${result.id}`,
        data: result,
      })
    })

    const options: any[] = []
    if (grouped.users.length > 0) {
      options.push({ label: `Người dùng (${grouped.users.length})`, options: grouped.users })
    }
    if (grouped.posts.length > 0) {
      options.push({ label: `Bài viết (${grouped.posts.length})`, options: grouped.posts })
    }
    if (grouped.reviews.length > 0) {
      options.push({ label: `Đánh giá (${grouped.reviews.length})`, options: grouped.reviews })
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
      value={searchValue}
      onSearch={handleSearch}
      onSelect={handleSelect}
      options={groupedOptions}
      notFoundContent={
        loading ? (
          <Spin size="small" />
        ) : searchValue.length > 0 ? (
          <Empty description="Không tìm thấy kết quả" style={{ marginTop: 12 }} />
        ) : null
      }
      allowClear
    >
      <Input
        className="global-search-input"
        prefix={<SearchOutlined />}
        placeholder="Tìm kiếm người dùng, bài viết, đánh giá..."
        onKeyDown={(event) => {
          if (event.key === 'Enter' && searchValue.length >= 2) {
            handlePressEnter()
          }
        }}
      />
    </AutoComplete>
  )
}
