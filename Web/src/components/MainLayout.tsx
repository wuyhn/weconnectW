import { Layout } from 'antd'
import { useEffect, useState } from 'react'
import AppSidebar from './AppSidebar'
import AppTopbar from './AppTopbar'
import './MainLayout.css'

interface MainLayoutProps {
  children: React.ReactNode
}

const { Content } = Layout

export const MainLayout: React.FC<MainLayoutProps> = ({ children }) => {
  const [collapsed, setCollapsed] = useState(false)

  useEffect(() => {
    const media = window.matchMedia('(max-width: 1180px)')
    const syncCollapsed = () => setCollapsed(media.matches)

    syncCollapsed()
    media.addEventListener('change', syncCollapsed)

    return () => media.removeEventListener('change', syncCollapsed)
  }, [])

  return (
    <Layout className="admin-layout-shell">
      <AppSidebar collapsed={collapsed} onCollapse={setCollapsed} />

      <Layout
        className="admin-layout-main"
        style={{
          marginLeft: collapsed ? 88 : 250,
          width: collapsed ? 'calc(100% - 88px)' : 'calc(100% - 250px)',
        }}
      >
        <AppTopbar collapsed={collapsed} onToggleSidebar={() => setCollapsed((value) => !value)} />
        <Content className="main-content">{children}</Content>
      </Layout>
    </Layout>
  )
}

export default MainLayout
