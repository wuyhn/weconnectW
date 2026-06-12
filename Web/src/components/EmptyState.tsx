import { Empty, Button } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import './EmptyState.css'

interface EmptyStateProps {
  title?: string
  description?: string
  image?: 'default' | 'simple' | 'custom'
  onAction?: () => void
  actionText?: string
  showAction?: boolean
  children?: React.ReactNode
}

export const EmptyState: React.FC<EmptyStateProps> = ({
  title = 'No Data Found',
  description = 'No records available at the moment',
  image = 'default',
  onAction,
  actionText = 'Create New',
  showAction = false,
  children,
}) => {
  return (
    <Empty
      className="empty-state"
      image={image === 'simple' ? Empty.PRESENTED_IMAGE_SIMPLE : Empty.PRESENTED_IMAGE_DEFAULT}
      description={
        <div className="empty-content">
          <h3>{title}</h3>
          <p>{description}</p>
        </div>
      }
    >
      {showAction && onAction && (
        <Button
          type="primary"
          size="large"
          icon={<PlusOutlined />}
          onClick={onAction}
          style={{ marginTop: '16px' }}
        >
          {actionText}
        </Button>
      )}
      {children}
    </Empty>
  )
}

export default EmptyState
