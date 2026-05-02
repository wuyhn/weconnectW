import { Modal, ModalProps } from 'antd'
import { ExclamationCircleOutlined } from '@ant-design/icons'

interface ConfirmActionModalProps extends Omit<ModalProps, 'onOk'> {
  title: string
  message: string
  onConfirm: () => void | Promise<void>
  onCancel?: () => void
  okText?: string
  cancelText?: string
  type?: 'warning' | 'error' | 'success' | 'info'
  loading?: boolean
}

export const ConfirmActionModal: React.FC<ConfirmActionModalProps> = ({
  title,
  message,
  onConfirm,
  onCancel,
  okText = 'Confirm',
  cancelText = 'Cancel',
  type = 'warning',
  loading = false,
  ...restProps
}) => {
  const iconMap = {
    warning: ExclamationCircleOutlined,
    error: ExclamationCircleOutlined,
    success: ExclamationCircleOutlined,
    info: ExclamationCircleOutlined,
  }

  const IconComponent = iconMap[type]

  return (
    <Modal
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <IconComponent
            style={{
              fontSize: '20px',
              color: type === 'error' ? '#ff4d4f' : type === 'warning' ? '#fa8c16' : '#1890ff',
            }}
          />
          {title}
        </div>
      }
      okText={okText}
      cancelText={cancelText}
      okButtonProps={{
        danger: type === 'error' || type === 'warning',
        loading: loading,
      }}
      onOk={onConfirm}
      onCancel={onCancel}
      centered
      {...restProps}
    >
      <p style={{ color: '#262626', margin: '16px 0 0 0' }}>{message}</p>
    </Modal>
  )
}

export default ConfirmActionModal
