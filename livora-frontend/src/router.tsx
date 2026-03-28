import { createBrowserRouter, Navigate, Outlet } from 'react-router-dom';
import Login from './pages/Login';
import ProtectedRoute from './auth/ProtectedRoute';
import RequireRole from './auth/RequireRole';
import PublicOnlyRoute from './auth/PublicOnlyRoute';
import RegisterPage from './pages/RegisterPage';
import App from './App';
import ErrorBoundary from './components/ErrorBoundary';
import Home from './pages/Home';
import PricingPage from './pages/PricingPage';
import ExploreCreatorsPage from './pages/ExploreCreatorsPage';
import AdminLandingPage from './pages/AdminLandingPage';
import AdminCreatorsPage from './pages/AdminCreatorsPage';
import AdminApplicationsPage from './pages/AdminApplicationsPage';
import AdminReportsPage from './pages/AdminReportsPage';
import AdminStreamsPage from './pages/AdminStreamsPage';
import AdminCreatorVerifications from './pages/AdminCreatorVerifications';
import Forbidden from './pages/Forbidden';
import BillingPage from './pages/BillingPage';
import SubscriptionPage from './pages/SubscriptionPage';
import PayoutDashboard from './pages/PayoutDashboard';
import ViewerDashboard from './components/ViewerDashboard';
import CreatorDashboard from './components/CreatorDashboard';
import BecomeCreatorPage from './pages/BecomeCreatorPage';
import StreamPage from './pages/StreamPage';
import VodPage from './pages/VodPage';
import LiveStreaming from './pages/LiveStreaming';
import CreatorUploadPage from './pages/CreatorUploadPage';
import CreatorContentPage from './pages/CreatorContentPage';
import CreatorEarningsDashboard from './pages/CreatorEarningsDashboard';
import CreatorVerificationPage from './pages/CreatorVerificationPage';
import CreatorAnalyticsPage from './pages/CreatorAnalyticsPage';
import CreatorLiveDashboard from './pages/CreatorLiveDashboard';
import CreatorPendingApprovalPage from './pages/CreatorPendingApprovalPage';
import PaymentSuccess from './pages/PaymentSuccess';
import PaymentCancel from './pages/PaymentCancel';
import ContentDetailPage from './pages/ContentDetailPage';
import PremiumDashboard from './pages/PremiumDashboard';
import PremiumPage from './pages/PremiumPage';
import TokenStorePage from './pages/TokenStorePage';
import TokenPurchaseSuccess from './pages/TokenPurchaseSuccess';
import TokenPurchaseCancel from './pages/TokenPurchaseCancel';
import VerifyEmailPage from './pages/VerifyEmailPage';
import SettingsPage from './pages/SettingsPage';
import FeedPage from './pages/FeedPage';
import NotFound from './pages/NotFound';

import TermsPage from './pages/legal/TermsPage';
import PrivacyPage from './pages/legal/PrivacyPage';
import DMCAPage from './pages/legal/DMCAPage';
import CompliancePage from './pages/legal/CompliancePage';
import RemovalPage from './pages/legal/RemovalPage';
import ContactPage from './pages/ContactPage';

import CreatorPublicProfile from './pages/CreatorPublicProfile';
import SupportCreatorPage from './pages/SupportCreatorPage';
import WatchPage from './pages/WatchPage';
import MainLayout from './components/MainLayout';

/**
 * Application router configuration using React Router's data API.
 * The root route '/' uses App.tsx as a layout.
 */
export const router = createBrowserRouter([
  {
    path: '/',
    element: (
      <ErrorBoundary>
        <App />
      </ErrorBoundary>
    ),
    errorElement: (
      <ErrorBoundary>
        <div className="min-h-screen bg-[#08080A] flex items-center justify-center text-white">
          <div className="text-center">
            <h1 className="text-4xl font-bold mb-4">Error</h1>
            <p className="text-zinc-400">A routing error occurred. Please try again or return home.</p>
            <a href="/" className="mt-6 inline-block text-indigo-500 hover:underline">Return to Home</a>
          </div>
        </div>
      </ErrorBoundary>
    ),
    children: [
      {
        path: 'creators/:identifier/live',
        element: <WatchPage />,
      },
      {
        /* Standard Layout with Navbar and Footer */
        element: <MainLayout />,
        children: [
          {
            index: true,
            element: <Home />,
          },
          {
            path: 'pricing',
            element: <PricingPage />,
          },
          {
            path: 'explore',
            element: <ExploreCreatorsPage />,
          },
          {
            path: 'login',
            element: (
              <PublicOnlyRoute>
                <Login />
              </PublicOnlyRoute>
            ),
          },
          {
            path: 'register',
            element: (
              <PublicOnlyRoute>
                <RegisterPage />
              </PublicOnlyRoute>
            ),
          },
          {
            path: 'verify-email',
            element: <VerifyEmailPage />,
          },
          {
            path: 'become-creator',
            element: (
              <ProtectedRoute>
                <BecomeCreatorPage />
              </ProtectedRoute>
            )
          },
          {
            path: 'feed',
            element: (
              <ProtectedRoute>
                <FeedPage />
              </ProtectedRoute>
            ),
          },
          {
            path: 'dashboard',
            element: <Outlet />,
            children: [
              {
                index: true,
                element: (
                  <RequireRole role={['USER', 'VIEWER', 'PREMIUM', 'CREATOR', 'ADMIN']}>
                    <ViewerDashboard />
                  </RequireRole>
                ),
              },
            ],
          },
          {
            path: 'user/dashboard',
            element: (
              <RequireRole role={['USER', 'PREMIUM', 'VIEWER', 'CREATOR', 'ADMIN']}>
                <ViewerDashboard />
              </RequireRole>
            ),
          },
          {
            path: 'billing',
            element: (
              <ProtectedRoute>
                <BillingPage />
              </ProtectedRoute>
            ),
          },
          {
            path: 'subscription',
            element: (
              <ProtectedRoute>
                <SubscriptionPage />
              </ProtectedRoute>
            ),
          },
          {
            path: 'settings',
            element: (
              <ProtectedRoute>
                <SettingsPage />
              </ProtectedRoute>
            ),
          },
          {
            path: 'payouts',
            element: (
              <RequireRole role="CREATOR">
                <PayoutDashboard />
              </RequireRole>
            ),
          },
          {
            path: 'creator',
            element: <Outlet />,
            children: [
              {
                path: 'onboard',
                element: (
                  <RequireRole role={['USER', 'VIEWER', 'PREMIUM', 'CREATOR', 'ADMIN']}>
                    <BecomeCreatorPage />
                  </RequireRole>
                ),
              },
              {
                path: 'pending',
                element: (
                  <RequireRole role="CREATOR">
                    <CreatorPendingApprovalPage />
                  </RequireRole>
                ),
              },
              {
                path: 'dashboard',
                element: (
                  <RequireRole role="CREATOR">
                    <CreatorDashboard />
                  </RequireRole>
                ),
              },
              {
                path: 'analytics',
                element: (
                  <RequireRole role="CREATOR">
                    <CreatorAnalyticsPage />
                  </RequireRole>
                ),
              },
              {
                path: 'earnings',
                element: (
                  <RequireRole role="CREATOR">
                    <CreatorEarningsDashboard />
                  </RequireRole>
                ),
              },
              {
                path: 'profile',
                element: <Navigate to="/creator/settings" replace />,
              },
              {
                path: 'upload',
                element: (
                  <RequireRole role="CREATOR">
                    <CreatorUploadPage />
                  </RequireRole>
                ),
              },
              {
                path: 'content',
                element: (
                  <RequireRole role="CREATOR">
                    <CreatorContentPage />
                  </RequireRole>
                ),
              },
              {
                path: 'live',
                element: (
                  <RequireRole role="CREATOR">
                    <CreatorLiveDashboard />
                  </RequireRole>
                ),
              },
              {
                path: 'verification',
                element: (
                  <RequireRole role="CREATOR">
                    <CreatorVerificationPage />
                  </RequireRole>
                ),
              }
            ],
          },
          {
            path: 'admin',
            element: (
              <RequireRole role="ADMIN">
                <AdminLandingPage />
              </RequireRole>
            ),
          },
          {
            path: 'admin/creators',
            element: (
              <RequireRole role="ADMIN">
                <AdminCreatorsPage />
              </RequireRole>
            ),
          },
          {
            path: 'admin/applications',
            element: (
              <RequireRole role="ADMIN">
                <AdminApplicationsPage />
              </RequireRole>
            ),
          },
          {
            path: 'admin/creator-verifications',
            element: (
              <RequireRole role="ADMIN">
                <AdminCreatorVerifications />
              </RequireRole>
            ),
          },
          {
            path: 'admin/reports',
            element: (
              <RequireRole role="ADMIN">
                <AdminReportsPage />
              </RequireRole>
            ),
          },
          {
            path: 'admin/streams',
            element: (
              <RequireRole role="ADMIN">
                <AdminStreamsPage />
              </RequireRole>
            ),
          },
          {
            path: 'live',
            element: (
              <ProtectedRoute>
                <LiveStreaming />
              </ProtectedRoute>
            ),
          },
          {
            path: 'stream/:roomId',
            element: (
              <ProtectedRoute>
                <StreamPage />
              </ProtectedRoute>
            ),
          },
          {
            path: 'vod/:streamId',
            element: (
              <ProtectedRoute>
                <VodPage />
              </ProtectedRoute>
            ),
          },
          {
            path: 'content/:id',
            element: (
              <ProtectedRoute>
                <ContentDetailPage />
              </ProtectedRoute>
            ),
          },
          {
            path: 'premium/dashboard',
            element: (
              <RequireRole role={['PREMIUM', 'ADMIN']}>
                <PremiumDashboard />
              </RequireRole>
            ),
          },
          {
            path: 'premium',
            element: (
              <ProtectedRoute>
                <PremiumPage />
              </ProtectedRoute>
            ),
          },
          {
            path: 'tokens/purchase',
            element: (
              <ProtectedRoute>
                <TokenStorePage />
              </ProtectedRoute>
            ),
          },
          {
            path: 'tokens/success',
            element: (
              <ProtectedRoute>
                <TokenPurchaseSuccess />
              </ProtectedRoute>
            ),
          },
          {
            path: 'tokens/cancel',
            element: (
              <ProtectedRoute>
                <TokenPurchaseCancel />
              </ProtectedRoute>
            ),
          },
          {
            path: 'payment/success',
            element: (
              <ProtectedRoute>
                <PaymentSuccess />
              </ProtectedRoute>
            ),
          },
          {
            path: 'payment/cancel',
            element: (
              <ProtectedRoute>
                <PaymentCancel />
              </ProtectedRoute>
            ),
          },
          {
            path: 'legal/terms',
            element: <TermsPage />,
          },
          {
            path: 'legal/privacy',
            element: <PrivacyPage />,
          },
          {
            path: 'legal/dmca',
            element: <DMCAPage />,
          },
          {
            path: 'legal/2257',
            element: <CompliancePage />,
          },
          {
            path: 'legal/removal',
            element: <RemovalPage />,
          },
          {
            path: 'contact',
            element: <ContactPage />,
          },
          {
            path: 'creators/:identifier',
            element: <CreatorPublicProfile />,
          },
          {
            path: 'support/:id',
            element: <SupportCreatorPage />,
          },
          {
            path: 'forbidden',
            element: <Forbidden />,
          },
          {
            path: 'privacy',
            element: <Navigate to="/legal/privacy" replace />,
          },
          {
            path: 'terms',
            element: <Navigate to="/legal/terms" replace />,
          },
          {
            path: 'dmca',
            element: <Navigate to="/legal/dmca" replace />,
          },
          {
            path: '2257',
            element: <Navigate to="/legal/2257" replace />,
          },
          {
            path: '*',
            element: <NotFound />,
          },
        ],
      },
    ],
  },
]);

export default router;
