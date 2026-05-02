import { Card, Row, Col, Statistic } from 'antd'
import { ReactNode } from 'react'
import './DashboardStatCard.css'

interface DashboardStatCardProps {
  title: string
  value: number | string
  icon?: ReactNode
  trend?: 'up' | 'down' | 'neutral'
  trendValue?: string
  color?: 'blue' | 'green' | 'orange' | 'red' | 'purple'
  onClick?: () => void
}

export const DashboardStatCard: React.FC<DashboardStatCardProps> = ({
  title,
  value,
  icon,
  trend = 'neutral',
  trendValue,
  color = 'blue',
  onClick,
}) => {
  const colorMap: Record<string, { bg: string; text: string; border: string }> = {
    blue: {
      bg: '#f0f5ff',
      text: '#1890ff',
      border: '#91caff',
    },
    green: {
      bg: '#f6ffed',
      text: '#52c41a',
      border: '#b7eb8f',
    },
    orange: {
      bg: '#fff7e6',
      text: '#fa8c16',
      border: '#ffd591',
    },
    red: {
      bg: '#fff1f0',
      text: '#ff4d4f',
      border: '#ffccc7',
    },
    purple: {
      bg: '#f9f0ff',
      text: '#722ed1',
      border: '#d3adf7',
    },
  }

  const colors = colorMap[color]

  return (
    <Card
      className="stat-card"
      style={{
        background: colors.bg,
        borderLeft: `4px solid ${colors.text}`,
        cursor: onClick ? 'pointer' : 'default',
        transition: 'all 0.3s ease',
      }}
      onMouseEnter={(e) => {
        if (onClick) {
          e.currentTarget.style.boxShadow = '0 4px 12px rgba(0, 0, 0, 0.1)'
        }
      }}
      onMouseLeave={(e) => {
        if (onClick) {
          e.currentTarget.style.boxShadow = '0 1px 4px rgba(0, 0, 0, 0.06)'
        }
      }}
      onClick={onClick}
    >
      <Row align="middle" justify="space-between">
        <Col>
          <div style={{ color: '#8c8c8c', fontSize: '14px', marginBottom: '8px' }}>
            {title}
          </div>
          <Statistic
            value={value}
            valueStyle={{ color: colors.text, fontSize: '28px', fontWeight: 600 }}
          />
          {trendValue && (
            <div
              style={{
                marginTop: '8px',
                fontSize: '12px',
                color: trend === 'up' ? '#52c41a' : trend === 'down' ? '#ff4d4f' : '#8c8c8c',
              }}
            >
              {trend === 'up' && '↑ '}
              {trend === 'down' && '↓ '}
              {trendValue}
            </div>
          )}
        </Col>
        {icon && (
          <Col>
            <div
              style={{
                fontSize: '32px',
                color: colors.text,
                opacity: 0.6,
              }}
            >
              {icon}
            </div>
          </Col>
        )}
      </Row>
    </Card>
  )
}

export default DashboardStatCard
