import { Badge, Tag } from 'antd'
import {
  CheckCircleOutlined,
  ExclamationCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
  LockOutlined,
  UnlockOutlined,
} from '@ant-design/icons'

interface StatusBadgeProps {
  status: 'success' | 'warning' | 'processing' | 'error' | 'blocked' | 'active'
  text?: string
  icon?: boolean
  showDot?: boolean
}

export const StatusBadge: React.FC<StatusBadgeProps> = ({
  status,
  text,
  icon = true,
  showDot = false,
}) => {
  const statusConfig: Record<string, any> = {
    success: {
      color: 'success',
      icon: icon ? <CheckCircleOutlined /> : undefined,
      label: text || 'Success',
      bg: '#f6ffed',
      color_text: '#52c41a',
    },
    warning: {
      color: 'warning',
      icon: icon ? <ExclamationCircleOutlined /> : undefined,
      label: text || 'Warning',
      bg: '#fff7e6',
      color_text: '#fa8c16',
    },
    processing: {
      color: 'processing',
      icon: icon ? <ClockCircleOutlined /> : undefined,
      label: text || 'Processing',
      bg: '#e6f7ff',
      color_text: '#1890ff',
    },
    error: {
      color: 'error',
      icon: icon ? <CloseCircleOutlined /> : undefined,
      label: text || 'Error',
      bg: '#fff1f0',
      color_text: '#ff4d4f',
    },
    blocked: {
      color: 'red',
      icon: icon ? <LockOutlined /> : undefined,
      label: text || 'Blocked',
      bg: '#fff1f0',
      color_text: '#ff4d4f',
    },
    active: {
      color: 'green',
      icon: icon ? <UnlockOutlined /> : undefined,
      label: text || 'Active',
      bg: '#f6ffed',
      color_text: '#52c41a',
    },
  }

  const config = statusConfig[status]

  if (showDot) {
    return (
      <Badge
        status={config.color}
        text={
          <span style={{ color: '#262626', marginLeft: '4px' }}>
            {config.label}
          </span>
        }
      />
    )
  }

  return (
    <Tag
      style={{
        background: config.bg,
        border: `1px solid ${config.color_text}`,
        color: config.color_text,
        display: 'inline-flex',
        alignItems: 'center',
        gap: '4px',
        borderRadius: '4px',
        padding: '2px 8px',
        fontWeight: 500,
      }}
      icon={config.icon}
    >
      {config.label}
    </Tag>
  )
}

export default StatusBadge
