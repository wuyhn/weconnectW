import { Spin, Space } from 'antd'
import { LoadingOutlined } from '@ant-design/icons'

interface LoadingStateProps {
  message?: string
  fullPage?: boolean
  size?: 'small' | 'default' | 'large'
}

export const LoadingState: React.FC<LoadingStateProps> = ({
  message = 'Loading...',
  fullPage = false,
  size = 'default',
}) => {
  const sizeMap = {
    small: 24,
    default: 48,
    large: 64,
  }

  const spin = (
    <Space direction="vertical" align="center" style={{ width: '100%' }}>
      <Spin
        indicator={
          <LoadingOutlined style={{ fontSize: sizeMap[size], color: '#1890ff' }} spin />
        }
      />
      {message && <p style={{ color: '#8c8c8c', fontSize: '14px' }}>{message}</p>}
    </Space>
  )

  if (fullPage) {
    return (
      <div
        style={{
          display: 'flex',
          justifyContent: 'center',
          alignItems: 'center',
          height: '100vh',
          width: '100%',
        }}
      >
        {spin}
      </div>
    )
  }

  return (
    <div
      style={{
        display: 'flex',
        justifyContent: 'center',
        alignItems: 'center',
        minHeight: '200px',
        width: '100%',
      }}
    >
      {spin}
    </div>
  )
}

export default LoadingState
