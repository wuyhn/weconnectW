import { useState } from 'react'
import { Form, Input, Button, Card, Row, Col, message, Typography, Divider } from 'antd'
import { UserOutlined, LockOutlined, LikeOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuthStore } from '../stores/authStore'
import { authService } from '../services/authService'
import './LoginPage.css'

const { Title, Text, Paragraph } = Typography

export default function LoginPage() {
  const [form] = Form.useForm()
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const { login } = useAuthStore()

  const onFinish = async (values: { email: string; password: string }) => {
    console.log('[Login] Form submitted with:', values)
    setLoading(true)
    try {
      console.log('[Login] Calling authService.login()...')
      const result = await authService.login(values)
      console.log('[Login] Success:', result)
      const user = {
        id: result.user.id || 0,
        email: result.user.email || '',
        fullName: result.user.fullName || '',
        role: (result.user.role === 1 ? 1 : 0) as 0 | 1,
        isBlocked: false,
        createdAt: new Date().toISOString(),
      }
      login(user, result.token, result.refreshToken)
      message.success('Login successful!')
      navigate('/dashboard')
    } catch (error: any) {
      console.error('[Login] Error:', error)
      message.error(error || 'Login failed. Please check your credentials.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-page">
      <div className="login-container">
        <Row gutter={[32, 32]} align="middle" justify="center" style={{ minHeight: '100vh' }}>
          {/* Left side - Branding */}
          <Col xs={24} sm={24} md={12} lg={10}>
            <div className="login-brand">
              <div className="brand-icon">
                <LikeOutlined />
              </div>
              <Title level={2} className="brand-title">
                WeConnect
              </Title>
              <Paragraph className="brand-description">
                Bảng điều khiển Admin
              </Paragraph>
              <Divider />
              <div style={{ textAlign: 'center' }}>
                <Text type="secondary">
                  Kết nối, chia sẻ hoạt động và quản lý cộng đồng của bạn
                </Text>
              </div>
            </div>
          </Col>

          {/* Right side - Form */}
          <Col xs={24} sm={24} md={12} lg={10}>
            <Card className="login-card" variant="borderless">
              <Title level={3} className="login-title">
                Đăng Nhập Admin
              </Title>
              <Text type="secondary" style={{ display: 'block', marginBottom: '24px' }}>
                Đăng nhập vào tài khoản quản trị
              </Text>

              <Form
                form={form}
                size="large"
                layout="vertical"
                onFinish={onFinish}
                autoComplete="off"
              >
                <Form.Item
                  name="email"
                  label="Địa chỉ Email"
                  rules={[
                    {
                      required: true,
                      message: 'Vui lòng nhập email',
                    },
                    {
                      type: 'email',
                      message: 'Vui lòng nhập email hợp lệ',
                    },
                  ]}
                >
                  <Input
                    prefix={<UserOutlined />}
                    placeholder="admin@example.com"
                    className="login-input"
                  />
                </Form.Item>

                <Form.Item
                  name="password"
                  label="Mật khẩu"
                  rules={[
                    {
                      required: true,
                      message: 'Vui lòng nhập mật khẩu',
                    },
                    {
                      min: 6,
                      message: 'Mật khẩu phải có ít nhất 6 ký tự',
                    },
                  ]}
                >
                  <Input.Password
                    prefix={<LockOutlined />}
                    placeholder="••••••••"
                    className="login-input"
                  />
                </Form.Item>

                <Form.Item>
                  <Button
                    type="primary"
                    htmlType="submit"
                    block
                    size="large"
                    loading={loading}
                    style={{ height: '40px', fontSize: '16px', fontWeight: 600 }}
                  >
                    Đăng Nhập
                  </Button>
                </Form.Item>
              </Form>

              <div className="login-footer">
                <Text type="secondary" style={{ fontSize: '12px' }}>
                  © 2024 WeConnect. Tất cả quyền được bảo lưu.
                </Text>
              </div>
            </Card>
          </Col>
        </Row>
      </div>
    </div>
  )
}
