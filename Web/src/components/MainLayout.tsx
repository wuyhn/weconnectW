import { Layout, Affix, Button } from 'antd'
import { MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons'
import { useState } from 'react'
import AppSidebar from './AppSidebar'
import AppTopbar from './AppTopbar'
import './MainLayout.css'

interface MainLayoutProps {
  children: React.ReactNode
  onSearch?: (value: string) => void
}

const { Content } = Layout

export const MainLayout: React.FC<MainLayoutProps> = ({ children, onSearch }) => {
  const [collapsed, setCollapsed] = useState(false)

  return (
    <Layout style={{ minHeight: '100vh' }}>
      <AppSidebar collapsed={collapsed} onCollapse={setCollapsed} />

      <Layout
        style={{
          marginLeft: collapsed ? 80 : 250,
          transition: 'margin-left 0.2s ease',
        }}
      >
        <AppTopbar collapsed={collapsed} onSearch={onSearch} />

        <Content className="main-content">{children}</Content>
      </Layout>

      <Affix style={{ position: 'fixed', bottom: 24, right: 24, zIndex: 1000 }}>
        <Button
          type="primary"
          size="large"
          icon={collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          onClick={() => setCollapsed(!collapsed)}
          shape="circle"
          style={{
            width: 48,
            height: 48,
            fontSize: 18,
          }}
        />
      </Affix>
    </Layout>
  )
}

export default MainLayout
