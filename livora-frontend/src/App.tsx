import { Suspense, lazy, useEffect } from 'react'
import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom'
import { AuthProvider } from './auth/AuthContext'
import PremiumGuard from './auth/PremiumGuard'
import ProtectedRoute from './auth/ProtectedRoute'
import RequireRole from './auth/RequireRole'
import HomePage from './pages/HomePage'
import LoginPage from './pages/LoginPage'
import RegisterPage from './pages/RegisterPage'
import PricingPage from './pages/PricingPage'
import PremiumPage from './pages/PremiumPage'
import PremiumDashboard from './pages/PremiumDashboard'
import AdminPanel from './pages/AdminPanel'
import PaymentSuccessPage from './pages/PaymentSuccessPage'
import PaymentCancelPage from './pages/PaymentCancelPage'
import { ToastContainer } from './components/Toast'
import SubscriptionDebugPanel from './components/SubscriptionDebugPanel'
import Navbar from './components/Navbar'
import Loader from './components/Loader'
import { authStore } from './store/authStore'
import authService from './api/authService'

// Lazy load heavy pages
const Dashboard = lazy(() => import('./pages/Dashboard'))
const BillingPage = lazy(() => import('./pages/BillingPage'))
const SubscriptionPage = lazy(() => import('./pages/SubscriptionPage'))
const LiveStreaming = lazy(() => import('./pages/LiveStreaming'))
const PayoutDashboard = lazy(() => import('./pages/PayoutDashboard'))
const ContentDetailPage = lazy(() => import('./pages/ContentDetailPage'))
const CreatorDashboard = lazy(() => import('./pages/CreatorDashboard'))
const CreatorUploadPage = lazy(() => import('./pages/CreatorUploadPage'))
const Forbidden = lazy(() => import('./pages/Forbidden'))

function App() {
  useEffect(() => {
    const initAuth = async () => {
      const token = localStorage.getItem('access_token');
      if (token) {
        try {
          const { accessToken } = await authService.refresh();
          const userData = await authService.getMe();
          // The current UserResponse doesn't have the same structure as AuthContext's User
          // but for authStore it might be okay depending on its definition
          authStore.setAuth(userData as any, accessToken);
        } catch (error) {
          console.error('Failed to refresh token on startup', error);
          // authStore.clearAuth() is likely not needed here as the interceptor 
          // should handle 401 from refresh by clearing auth and redirecting
        }
      }
    };
    initAuth();
  }, []);

  return (
    <AuthProvider>
      <Router>
        <Navbar />
        <ToastContainer />
        <SubscriptionDebugPanel />
        <Suspense fallback={<Loader />}>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/register" element={<RegisterPage />} />
            <Route path="/pricing" element={<PricingPage />} />
            <Route path="/forbidden" element={<Forbidden />} />
            <Route path="/upgrade" element={<Navigate to="/pricing" replace />} />
            
            <Route path="/dashboard" element={
              <ProtectedRoute>
                <Dashboard />
              </ProtectedRoute>
            } />
            
            <Route path="/admin" element={
              <ProtectedRoute requiredRole="ADMIN">
                <AdminPanel />
              </ProtectedRoute>
            } />

            <Route path="/creator" element={
              <ProtectedRoute>
                <RequireRole role="ROLE_CREATOR">
                  <CreatorDashboard />
                </RequireRole>
              </ProtectedRoute>
            } />

            <Route path="/content/:id" element={
              <ProtectedRoute>
                <ContentDetailPage />
              </ProtectedRoute>
            } />

            <Route path="/billing" element={
              <ProtectedRoute>
                <BillingPage />
              </ProtectedRoute>
            } />

            <Route path="/subscription" element={
              <ProtectedRoute>
                <SubscriptionPage />
              </ProtectedRoute>
            } />

            <Route path="/live" element={
              <ProtectedRoute>
                <LiveStreaming />
              </ProtectedRoute>
            } />

            <Route path="/payment/success" element={
              <ProtectedRoute>
                <PaymentSuccessPage />
              </ProtectedRoute>
            } />

            <Route path="/payment/cancel" element={
              <ProtectedRoute>
                <PaymentCancelPage />
              </ProtectedRoute>
            } />

            <Route path="/payouts" element={
              <ProtectedRoute requiredRole="CREATOR">
                <PayoutDashboard />
              </ProtectedRoute>
            } />

            <Route path="/creator/dashboard" element={
              <ProtectedRoute requiredRole="CREATOR">
                <CreatorDashboard />
              </ProtectedRoute>
            } />

            <Route path="/creator/upload" element={
              <ProtectedRoute requiredRole="CREATOR">
                <CreatorUploadPage />
              </ProtectedRoute>
            } />

            <Route path="/premium" element={
              <ProtectedRoute>
                <PremiumGuard>
                  <PremiumPage />
                </PremiumGuard>
              </ProtectedRoute>
            } />
            <Route path="/premium-dashboard" element={
              <ProtectedRoute>
                <PremiumGuard>
                  <PremiumDashboard />
                </PremiumGuard>
              </ProtectedRoute>
            } />
          </Routes>
        </Suspense>
      </Router>
    </AuthProvider>
  )
}

export default App;
