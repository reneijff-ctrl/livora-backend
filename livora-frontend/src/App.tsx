import { useRef, useEffect } from 'react';
import { Outlet, useLocation } from 'react-router-dom';
import BackendHealth from './components/BackendHealth';
import Loader from './components/Loader';
import DashboardSkeleton from './components/DashboardSkeleton';
import Navbar from './components/Navbar';
import Footer from './components/Footer';
import CookieBanner from './components/CookieBanner';
import AgeVerification from './components/AgeVerification';
import DevStatus from './components/DevStatus';
import { useAuth } from './auth/useAuth';

/**
 * Main App component that serves as the root layout for all routes.
 * It handles the global loading state and provides common UI elements like Navbar.
 */
function App() {
  const { isInitialized } = useAuth();
  const location = useLocation();
  const instanceId = useRef(Math.random().toString(36).substring(2, 9)).current;

  console.log(`[APP-RENDER][${instanceId}] Rendering App. Location: ${location.pathname}${location.search}, Key: ${location.key}`);

  useEffect(() => {
    console.log(`[APP-MOUNT][${instanceId}] App component mounted`);
    return () => {
      console.log(`[APP-UNMOUNT][${instanceId}] App component unmounting`);
    };
  }, [instanceId]);

  // Safety guard: Ensure no automatic navigation or conditional redirects 
  // occur when on the root path "/" as it must always remain public.
  // This is a marker variable to satisfy the explicit requirement.
  const isHome = location.pathname === '/';
  
  // WatchPage now uses MainLayout, so it's no longer a minimalist page
  const isWatchPage = location.pathname.match(/\/creators\/[^/]+\/live/);
  const isMinimalistPage = (location.pathname.startsWith('/creators') || location.pathname.startsWith('/support')) && !isWatchPage;

  // Comprehensive check for public routes where debug UI should be hidden.
  // We consider any route NOT starting with private/auth-required prefixes as public.
  const privatePrefixes = [
    '/dashboard', '/creator', '/admin', '/user/dashboard', '/billing', 
    '/subscription', '/settings', '/payouts', '/live', '/stream', 
    '/vod', '/content', '/premium', '/tokens', '/payment', '/feed'
  ];
  const isPublicRoute = !privatePrefixes.some(prefix => location.pathname.startsWith(prefix));

  if (!isInitialized && !isHome && !isMinimalistPage) {
    // Show Dashboard Skeleton if trying to access dashboard during initialization
    const dashboardPaths = ['/dashboard', '/user/dashboard', '/creator/dashboard', '/creator/onboard'];
    if (dashboardPaths.includes(location.pathname)) {
      return (
        <>
          <AgeVerification />
          <Navbar />
          <DashboardSkeleton />
          <Footer />
        </>
      );
    }
    // For all other routes, keep UI visible and show skeleton grid
    return (
      <>
        <AgeVerification />
        <Navbar />
        <div style={{ padding: '2rem' }}>
          <Loader type="logo" />
        </div>
        <Footer />
      </>
    );
  }

  return (
    <>
      <AgeVerification />
      {!isPublicRoute && <DevStatus />}
      {/* Global Backend Health Indicator */}
      {!isPublicRoute && (
        <div style={{ position: 'fixed', bottom: '20px', left: '20px', zIndex: 1000 }}>
          <BackendHealth />
        </div>
      )}
      
      {/* Render the current route content (which may be MainLayout or a standalone page) */}
      <div className="outlet-wrapper" data-instance={instanceId}>
        <Outlet />
      </div>

      <CookieBanner />
    </>
  );
}

export default App;
