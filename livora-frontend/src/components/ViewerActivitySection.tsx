import React from 'react';
import { useNavigate } from 'react-router-dom';
import { ViewerDashboard as ViewerDashboardData } from '../api/dashboardApi';

interface ViewerActivitySectionProps {
  data: ViewerDashboardData | null;
}

/**
 * ViewerActivitySection component.
 * Displays the recent activity and purchases for the viewer.
 * Refactored from ViewerStatsGrid into a dedicated section.
 */
const ViewerActivitySection: React.FC<ViewerActivitySectionProps> = ({ data }) => {
  const navigate = useNavigate();

  const balance = data?.tokenBalance || 0;
  const activeSubs = data?.activeSubscriptions || 0;

  let ctaText = "Explore More";
  let ctaRoute = "/explore";

  if (balance === 0) {
    ctaText = "Buy Tokens";
    ctaRoute = "/tokens/purchase";
  } else if (activeSubs === 0) {
    ctaText = "Discover Creators";
    ctaRoute = "/explore";
  }

  return (
    <section 
      className="glass-panel rounded-2xl p-12 bg-gradient-to-b from-white/3 to-transparent transition duration-300 hover:scale-[1.01] hover:shadow-lg w-full" 
      style={styles.container}
    >
      <div style={styles.header}>
        <div style={styles.headerTitle}>
          <span style={styles.icon}>🛍️</span>
          <h3 style={styles.title}>Recent Activity</h3>
        </div>
      </div>
      
      {(!data?.recentPurchases || data?.recentPurchases?.length === 0) ? (
        <div style={styles.emptyWrapper}>
          <div style={styles.emptyIcon}>🛍️</div>
          <p style={styles.emptyMessage}>No recent activity yet</p>
          
          <div className="flex flex-col gap-3 my-2 items-center opacity-70">
            <div className="flex items-center gap-2 text-sm">
              <span>✨</span>
              <span>Support your first creator</span>
            </div>
            <div className="flex items-center gap-2 text-sm">
              <span>🔓</span>
              <span>Unlock exclusive content</span>
            </div>
          </div>

          <button 
            onClick={() => navigate(ctaRoute)} 
            style={styles.actionButton}
            className="transition-all hover:scale-[1.03]"
          >
            {ctaText}
          </button>
        </div>
      ) : (
        <div style={styles.activityList}>
          <p style={{ textAlign: 'center', opacity: 0.6 }}>Your recent purchases will appear here.</p>
        </div>
      )}
    </section>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    display: 'flex',
    flexDirection: 'column',
  },
  header: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
    paddingBottom: '1rem',
    marginBottom: '1.5rem',
  },
  headerTitle: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
  },
  icon: {
    fontSize: '1.25rem',
  },
  title: {
    fontSize: '1.5rem',
    fontWeight: '700',
    color: '#F4F4F5',
    margin: 0,
    letterSpacing: '0.01em',
  },
  emptyWrapper: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '1rem',
    minHeight: '260px',
    backgroundColor: 'rgba(255, 255, 255, 0.01)',
    borderRadius: '16px',
    padding: '2rem',
  },
  emptyIcon: {
    fontSize: '3rem',
    opacity: 0.4,
  },
  emptyMessage: {
    fontSize: '1rem',
    fontWeight: '500',
    color: '#71717A',
    textAlign: 'center',
    maxWidth: '320px',
    margin: 0,
  },
  activityList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
  },
  actionButton: {
    marginTop: '0.5rem',
    padding: '0.875rem 2.5rem',
    background: 'linear-gradient(135deg, #6366f1 0%, #a855f7 100%)',
    color: 'white',
    border: 'none',
    borderRadius: '12px',
    fontWeight: '700',
    fontSize: '1rem',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
  },
};

export default ViewerActivitySection;
