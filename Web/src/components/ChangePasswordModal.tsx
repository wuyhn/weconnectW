import { useState } from 'react'
import {
  Modal,
  Form,
  Input,
  Button,
  Space,
  message,
  Spin,
} from 'antd'
import {
  EyeOutlined,
  EyeInvisibleOutlined,
} from '@ant-design/icons'
import { authService } from '../services/authService'
import './ChangePasswordModal.css'

interface ChangePasswordModalProps {
  isOpen: boolean
  onClose: () => void
  onSuccess?: () => void
}

export const ChangePasswordModal: React.FC<ChangePasswordModalProps> = ({
  isOpen,
  onClose,
  onSuccess,
}) => {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)

  const handleSubmit = async (values: any) => {
    try {
      setLoading(true)
      await authService.changePassword(values.currentPassword, values.newPassword)
      message.success('Mật khẩu đã được thay đổi thành công!')
      form.resetFields()
      onClose()
      onSuccess?.()
    } catch (error: any) {
      message.error(error || 'Không thể thay đổi mật khẩu. Vui lòng thử lại.')
    } finally {
      setLoading(false)
    }
  }

  const handleCancel = () => {
    form.resetFields()
    onClose()
  }

  return (
    <Modal
      title="Đổi mật khẩu"
      open={isOpen}
      onCancel={handleCancel}
      footer={null}
      width={420}
      centered
    >
      <Spin spinning={loading}>
        <Form
          form={form}
          layout="vertical"
          onFinish={handleSubmit}
          autoComplete="off"
        >
          {/* Current Password */}
          <Form.Item
            label="Mật khẩu hiện tại"
            name="currentPassword"
            rules={[
              {
                required: true,
                message: 'Vui lòng nhập mật khẩu hiện tại',
              },
            ]}
          >
            <Input.Password
              placeholder="Nhập mật khẩu hiện tại"
              iconRender={(visible) =>
                visible ? <EyeOutlined /> : <EyeInvisibleOutlined />
              }
              disabled={loading}
            />
          </Form.Item>

          {/* New Password */}
          <Form.Item
            label="Mật khẩu mới"
            name="newPassword"
            rules={[
              {
                required: true,
                message: 'Vui lòng nhập mật khẩu mới',
              },
              {
                min: 8,
                message: 'Mật khẩu phải có ít nhất 8 ký tự',
              },
              {
                pattern: /[a-zA-Z]/,
                message: 'Mật khẩu phải chứa ít nhất 1 chữ cái',
              },
              {
                pattern: /[0-9]/,
                message: 'Mật khẩu phải chứa ít nhất 1 số',
              },
              {
                validator: (_, value) => {
                  const currentPassword = form.getFieldValue('currentPassword')
                  if (value && currentPassword && value === currentPassword) {
                    return Promise.reject(
                      new Error('Mật khẩu mới không được trùng với mật khẩu hiện tại')
                    )
                  }
                  return Promise.resolve()
                },
              },
            ]}
          >
            <Input.Password
              placeholder="Nhập mật khẩu mới"
              iconRender={(visible) =>
                visible ? <EyeOutlined /> : <EyeInvisibleOutlined />
              }
              disabled={loading}
            />
          </Form.Item>

          {/* Confirm Password */}
          <Form.Item
            label="Xác nhận mật khẩu mới"
            name="confirmPassword"
            rules={[
              {
                required: true,
                message: 'Vui lòng xác nhận mật khẩu mới',
              },
              {
                validator: (_, value) => {
                  const newPassword = form.getFieldValue('newPassword')
                  if (value && newPassword && value !== newPassword) {
                    return Promise.reject(
                      new Error('Mật khẩu xác nhận không khớp với mật khẩu mới')
                    )
                  }
                  return Promise.resolve()
                },
              },
            ]}
          >
            <Input.Password
              placeholder="Xác nhận mật khẩu mới"
              iconRender={(visible) =>
                visible ? <EyeOutlined /> : <EyeInvisibleOutlined />
              }
              disabled={loading}
            />
          </Form.Item>

          {/* Buttons */}
          <Form.Item style={{ marginBottom: 0, marginTop: 24 }}>
            <Space style={{ width: '100%', justifyContent: 'flex-end' }} size="small">
              <Button
                onClick={handleCancel}
                disabled={loading}
              >
                Hủy
              </Button>
              <Button
                type="primary"
                htmlType="submit"
                loading={loading}
                disabled={loading}
              >
                Cập nhật mật khẩu
              </Button>
            </Space>
          </Form.Item>
        </Form>
      </Spin>
    </Modal>
  )
}

export default ChangePasswordModal
