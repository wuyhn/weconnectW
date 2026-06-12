import React from 'react'
import ReactDOM from 'react-dom/client'
import { ConfigProvider } from 'antd'
import viVN from 'antd/locale/vi_VN'
import App from './App.tsx'
import './App.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <ConfigProvider locale={viVN} theme={{
      token: {
        colorPrimary: '#E85B8A',
        borderRadius: 10,
        fontSize: 14,
      },
    }}>
      <App />
    </ConfigProvider>
  </React.StrictMode>,
)
