import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { getViewerDashboard, ViewerDashboard as ViewerDashboardData } from '../api/dashboardApi';
import { useAuth } from '../auth/useAuth';
import DashboardSkeleton from './DashboardSkeleton';
import SEO from './SEO';
import ViewerHeroSection from './ViewerHeroSection';
import ViewerStatsGrid from './ViewerStatsGrid';
import ViewerActivitySection from './ViewerActivitySection';

/**
 * ViewerHub component.
 * Displays sections for Account balance, Subscriptions, Recent purchases, and Recommended creators.
 * Uses the same card layout system as CreatorDashboard.
 */
const ViewerDashboard: React.FC = () => {
  const { user, authLoading } = useAuth();
  const navigate = useNavigate();
  const [data, setData] = useState<ViewerDashboardData | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    // Requirement: Do NOT fetch data until role is resolved
    if (!user || authLoading) {
        return;
    }

    let isMounted = true;
    const fetchDashboard = async () => {
      try {
        setLoading(true);
        const dashboardData = await getViewerDashboard();
        if (isMounted) {
          setData(dashboardData);
        }
      } catch (err) {
        console.error('Failed to fetch viewer hub data', err);
      } finally {
        if (isMounted) {
          setLoading(false);
        }
      }
    };

    fetchDashboard();
    
    return () => { isMounted = false; };
  }, [user, authLoading]);

  if (authLoading || loading) {
    return <DashboardSkeleton title="Viewer Hub" />;
  }

  return (
    <div style={styles.layout}>
      <SEO 
        title="Viewer Hub" 
        description="Manage your Livora viewer account, subscriptions and token balance."
      />
      
      {/* Sidebar Navigation */}
      <aside style={styles.sidebar}>
        <div style={styles.sidebarHeader}>
          <h2 style={styles.sidebarTitle}>Viewer Hub</h2>
        </div>
        <nav style={styles.nav}>
          <Link to="/" style={styles.navLink} className="transition-all duration-200 hover:bg-white/5 hover:text-white">
            <span style={styles.navIcon}>🏠</span> Home
          </Link>
          <Link to="/explore" style={styles.navLink} className="transition-all duration-200 hover:bg-white/5 hover:text-white">
            <span style={styles.navIcon}>🔍</span> Explore Creators
          </Link>
          <Link to="/user/dashboard" style={{ ...styles.navLink, ...styles.activeNavLink }} className="transition-all duration-200">
            <span style={styles.navIcon}>📊</span> Viewer Hub
          </Link>
          <Link to="/tokens/purchase" style={styles.navLink} className="transition-all duration-200 hover:bg-white/5 hover:text-white">
            <span style={styles.navIcon}>🪙</span> Buy Tokens
          </Link>
          <Link to="/subscription" style={styles.navLink} className="transition-all duration-200 hover:bg-white/5 hover:text-white">
            <span style={styles.navIcon}>❤️</span> Subscriptions
          </Link>
          <Link to="/settings" style={styles.navLink} className="transition-all duration-200 hover:bg-white/5 hover:text-white">
            <span style={styles.navIcon}>⚙️</span> Settings
          </Link>
        </nav>
      </aside>

      {/* Main Content Area */}
      <main style={styles.main}>
        <section style={styles.contentBody}>
          {user && (
            <ViewerHeroSection 
              user={user}
              tokenBalance={data?.tokenBalance || user.tokenBalance || 0}
              activeSubscriptions={data?.activeSubscriptions || 0}
              totalSpent={data?.totalSpent || 0}
              role={user.role}
              onSecondaryActionClick={() => {
                if (user.role === 'CREATOR') {
                  navigate('/creator/dashboard');
                } else {
                  navigate('/creator/onboard');
                }
              }}
              onExploreClick={() => {
                const balance = data?.tokenBalance || user.tokenBalance || 0;
                if (balance === 0) {
                  navigate('/tokens/purchase');
                } else {
                  navigate('/explore');
                }
              }}
            />
          )}

          <ViewerStatsGrid data={data} />
          <ViewerActivitySection data={data} />
        </section>
      </main>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  layout: {
    display: 'flex',
    minHeight: 'calc(100vh - 64px)',
    backgroundColor: '#08080A',
    color: '#F4F4F5',
    fontFamily: 'system-ui, -apple-system, sans-serif',
  },
  sidebar: {
    width: '260px',
    backgroundColor: '#0F0F14',
    borderRight: '1px solid rgba(255, 255, 255, 0.05)',
    display: 'flex',
    flexDirection: 'column',
    padding: '1.5rem 0',
  },
  sidebarHeader: {
    padding: '0 1.5rem 1.5rem 1.5rem',
    borderBottom: '1px solid rgba(255, 255, 255, 0.05)',
    marginBottom: '1rem',
  },
  sidebarTitle: {
    fontSize: '1.25rem',
    fontWeight: '700',
    color: '#F4F4F5',
    margin: 0,
  },
  nav: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.25rem',
    padding: '0 0.75rem',
  },
  navLink: {
    display: 'flex',
    alignItems: 'center',
    padding: '0.75rem 0.75rem',
    textDecoration: 'none',
    color: '#71717A',
    fontWeight: '500',
    borderRadius: '8px',
    transition: 'all 0.2s ease',
    borderLeft: '2px solid transparent',
  },
  activeNavLink: {
    backgroundColor: 'rgba(99, 102, 241, 0.15)',
    color: '#fff',
    border: '1px solid rgba(99, 102, 241, 0.2)',
    borderLeft: '2px solid #a855f7',
  },
  navIcon: {
    marginRight: '0.75rem',
    fontSize: '1.1rem',
  },
  main: {
    flex: 1,
    padding: '2.5rem 2rem', // py-10 px-8
    maxWidth: '1100px',
    margin: '0 auto',
    overflowY: 'auto',
  },
  contentBody: {
    display: 'flex',
    flexDirection: 'column',
    gap: '4rem', // mt-16 between sections
  },
};

export default ViewerDashboard;
