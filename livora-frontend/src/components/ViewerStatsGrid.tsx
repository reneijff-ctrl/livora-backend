import React from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ViewerDashboard as ViewerDashboardData } from '../api/dashboardApi';

interface ViewerStatsGridProps {
  data: ViewerDashboardData | null;
}

/**
 * ViewerStatsGrid component.
 * Displays the statistics and activity cards for the viewer.
 */
const ViewerStatsGrid: React.FC<ViewerStatsGridProps> = ({ data }) => {
  const navigate = useNavigate();
  const hasActiveSubs = (data?.activeSubscriptions || 0) > 0;
  const highlightClasses = 'border-purple-500/30 shadow-[0_0_40px_rgba(168,85,247,0.1)]';

  return (
    <div className="grid gap-8 grid-cols-1 lg:grid-cols-2 xl:grid-cols-3">
      {/* Account Balance Section */}
      <section className="glass-panel rounded-2xl p-9 h-full flex flex-col bg-gradient-to-b from-white/5 to-transparent border border-white/10 transition-all duration-300 hover:scale-[1.02] hover:shadow-2xl hover:border-purple-500/20 relative overflow-hidden before:absolute before:inset-0 before:bg-gradient-to-br before:from-white/5 before:to-transparent before:rounded-2xl before:pointer-events-none">
        <div style={styles.cardHeader}>
          <span style={styles.icon}>🪙</span>
          <h3 style={styles.cardTitle}>Account Balance</h3>
        </div>
        <div style={styles.placeholderContent} className="min-h-[220px] space-y-4">
          <div style={styles.metricRow}>
            <span style={styles.metricLabel}>Available Tokens:</span>
            <span style={styles.metricValue}>{data?.tokenBalance || 0} 🪙</span>
          </div>
          <div style={styles.metricRow}>
            <span style={styles.metricLabel}>Total Spent:</span>
            <span style={styles.metricValue}>€{(data?.totalSpent || 0).toFixed(2)}</span>
          </div>
          <div style={styles.skeletonBar} />
          <div className="mt-auto">
            <div className="mt-6">
              <Link to="/tokens/purchase" style={styles.actionButton} className="transition-all hover:scale-[1.03] w-full">Get More Tokens</Link>
            </div>
          </div>
        </div>
      </section>

      {/* Subscriptions Section */}
      <section className={`glass-panel rounded-2xl p-9 h-full flex flex-col bg-gradient-to-b from-white/5 to-transparent border border-white/10 transition-all duration-300 hover:scale-[1.02] hover:shadow-2xl hover:border-purple-500/20 relative overflow-hidden before:absolute before:inset-0 before:bg-gradient-to-br before:from-white/5 before:to-transparent before:rounded-2xl before:pointer-events-none ${hasActiveSubs ? highlightClasses : ''}`}>
        <div style={styles.cardHeader}>
          <span style={styles.icon}>❤️</span>
          <h3 style={styles.cardTitle}>Subscriptions</h3>
        </div>
        <div style={styles.placeholderContent} className="min-h-[220px] space-y-4">
          <div style={styles.metricRow}>
            <span style={styles.metricLabel}>Active Subscriptions:</span>
            <span style={styles.metricValue}>{data?.activeSubscriptions || 0}</span>
          </div>
          <div style={styles.metricRow}>
            <span style={styles.metricLabel}>Followed Creators:</span>
            <span style={styles.metricValue}>{data?.followedCreators || 0}</span>
          </div>
          <div style={styles.skeletonBar} />
          <div className="mt-auto">
            <div className="mt-6">
              <Link to="/subscription" style={styles.actionButton} className="transition-all hover:scale-[1.03] w-full">Manage Subscriptions</Link>
            </div>
          </div>
        </div>
      </section>

      {/* Recommended Creators Section */}
      <section className={`glass-panel rounded-2xl p-9 h-full flex flex-col bg-gradient-to-b from-white/5 to-transparent border border-white/10 transition-all duration-300 hover:scale-[1.02] hover:shadow-2xl hover:border-purple-500/20 relative overflow-hidden before:absolute before:inset-0 before:bg-gradient-to-br before:from-white/5 before:to-transparent before:rounded-2xl before:pointer-events-none ${!hasActiveSubs ? highlightClasses : ''}`}>
        <div style={styles.cardHeader}>
          <span style={styles.icon}>✨</span>
          <h3 style={styles.cardTitle}>Recommended Creators</h3>
        </div>

        <div className="flex-1 flex flex-col items-center justify-center text-center gap-4 min-h-[220px] mt-5">
          <span className="text-4xl opacity-40">✨</span>
          <p className="text-zinc-400 font-medium max-w-[280px]">
            Explore creators to find content you love!
          </p>
          <div className="mt-auto w-full">
            <p className="text-xs opacity-60 mb-3">
              Join thousands of viewers supporting creators daily.
            </p>
            <button 
              onClick={() => navigate('/explore')}
              className="transition-all hover:scale-[1.03] active:scale-95 w-full"
              style={styles.actionButton}
            >
              Explore Now
            </button>
          </div>
        </div>
      </section>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  cardHeader: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    borderBottom: '1px solid rgba(255, 255, 255, 0.1)',
    paddingBottom: '1rem',
  },
  icon: {
    fontSize: '1.25rem',
  },
  cardTitle: {
    fontSize: '1.125rem',
    fontWeight: '700',
    color: '#F4F4F5',
    margin: 0,
    letterSpacing: '0.01em',
  },
  placeholderContent: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
    flex: 1,
    marginTop: '1.25rem',
  },
  metricRow: {
    display: 'flex',
    justifyContent: 'space-between',
    fontSize: '0.875rem',
  },
  metricLabel: {
    color: '#71717A',
  },
  metricValue: {
    fontWeight: '700',
    color: '#F4F4F5',
  },
  actionButton: {
    display: 'block',
    padding: '0.875rem',
    background: 'linear-gradient(135deg, #6366f1 0%, #a855f7 100%)',
    color: 'white',
    textAlign: 'center',
    borderRadius: '12px',
    textDecoration: 'none',
    fontWeight: '700',
    fontSize: '0.875rem',
    transition: 'all 0.2s ease',
    boxShadow: '0 4px 12px rgba(99, 102, 241, 0.2)',
  },
  skeletonBar: {},
};

export default ViewerStatsGrid;
