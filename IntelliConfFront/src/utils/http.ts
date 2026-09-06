import axios from 'axios'
import { ElMessage, ElMessageBox } from 'element-plus'

// 创建 axios 实例
const http = axios.create({
  timeout: 10000
})

// 请求拦截器：自动添加 token
http.interceptors.request.use(
  (config) => {
    const token = localStorage.getItem('token')
    const username = localStorage.getItem('username')
    
    if (token) {
      // 根据后端要求添加 token 和 username 到请求头或查询参数
      config.headers['token'] = token
      config.headers['username'] = username
    }
    
    return config
  },
  (error) => {
    return Promise.reject(error)
  }
)

// 响应拦截器：统一处理错误
http.interceptors.response.use(
  (response) => {
    return response
  },
  async (error) => {
    // Token 过期检测：401 或者返回"用户 Token 错误"
    const isTokenError = error.response?.status === 401 || 
                        error.response?.data?.message?.includes('用户 Token 错误') ||
                        error.response?.data?.message?.includes('Token')
    
    if (isTokenError) {
      // 清除本地存储
      localStorage.removeItem('token')
      localStorage.removeItem('username')
      
      // 弹窗提示用户
      try {
        await ElMessageBox.alert(
          '您的登录已过期，请重新登录以继续使用系统功能。',
          '登录已过期',
          {
            confirmButtonText: '重新登录',
            type: 'warning',
            showClose: false,
            closeOnClickModal: false,
            closeOnPressEscape: false
          }
        )
      } catch {
        // 用户点击确定或关闭
      } finally {
        // 邀请确认场景必须保留邀请令牌，登录/注册后才能继续完成接受操作。
        const currentUrl = new URL(window.location.href)
        const inviteToken = currentUrl.pathname === '/committee-invite' ? currentUrl.searchParams.get('token') : ''
        window.location.href = inviteToken
          ? `/auth?token=${encodeURIComponent(inviteToken)}&mode=accept`
          : '/auth'
      }
    }
    return Promise.reject(error)
  }
)

export default http
