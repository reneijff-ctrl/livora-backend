import React from 'react';

interface DashboardSkeletonProps {
  title?: string;
}

const DashboardSkeleton: React.FC<DashboardSkeletonProps> = ({ title }) => {
  return (
    <div style={styles.layout}>
      <style>{`
        @keyframes pulse {
          0% { opacity: 0.6; }
          50% { opacity: 1; }
          100% { opacity: 0.6; }
        }
      `}</style>
      
      {/* Sidebar Navigation Placeholder */}
      <aside style={styles.sidebar}>
        <div style={styles.sidebarHeader}>
          <div style={{ ...styles.pulse, height: '24px', width: '120px' }} />
        </div>
        <nav style={styles.nav}>
          {[1, 2, 3, 4, 5].map((i) => (
            <div key={i} style={styles.navLinkPlaceholder}>
              <div style={{ ...styles.pulse, height: '20px', width: '20px', borderRadius: '4px', marginRight: '12px' }} />
              <div style={{ ...styles.pulse, height: '16px', width: '80px' }} />
            </div>
          ))}
        </nav>
      </aside>

      {/* Main Content Area Placeholder */}
      <main style={styles.main}>
        <header style={styles.contentHeader}>
          {title ? (
            <h1 style={styles.title}>{title}</h1>
          ) : (
            <div style={{ ...styles.pulse, height: '36px', width: '300px' }} />
          )}
          <div style={styles.userSummary}>
            <div style={{ ...styles.pulse, height: '30px', width: '120px', borderRadius: '9999px' }} />
            <div style={{ ...styles.pulse, height: '30px', width: '140px', borderRadius: '9999px' }} />
          </div>
        </header>

        <section style={styles.contentBody}>
          <div style={styles.welcomeCardPlaceholder}>
            <div style={{ ...styles.pulse, height: '28px', width: '240px', marginBottom: '1rem' }} />
            <div style={{ ...styles.pulse, height: '16px', width: '100%', marginBottom: '0.5rem' }} />
            <div style={{ ...styles.pulse, height: '16px', width: '90%' }} />
          </div>

          <div style={styles.statsGrid}>
            <div style={styles.statCardPlaceholder}>
              <div style={{ ...styles.pulse, height: '20px', width: '120px', margin: '0 auto 1rem auto' }} />
              <div style={{ ...styles.pulse, height: '16px', width: '100px', margin: '0 auto' }} />
            </div>
            <div style={styles.statCardPlaceholder}>
              <div style={{ ...styles.pulse, height: '20px', width: '120px', margin: '0 auto 1rem auto' }} />
              <div style={{ ...styles.pulse, height: '16px', width: '100px', margin: '0 auto' }} />
            </div>
          </div>
        </section>
      </main>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  layout: {
    display: 'flex',
    minHeight: 'calc(100vh - 64px)',
    backgroundColor: 'var(--background)',
    fontFamily: 'system-ui, -apple-system, sans-serif',
  },
  pulse: {
    backgroundColor: '#27272A',
    borderRadius: '4px',
    animation: 'pulse 1.5s ease-in-out infinite',
  },
  sidebar: {
    width: '260px',
    backgroundColor: 'var(--card-bg)',
    borderRight: '1px solid var(--border-color)',
    display: 'flex',
    flexDirection: 'column',
    padding: '1.5rem 0',
  },
  sidebarHeader: {
    padding: '0 1.5rem 1.5rem 1.5rem',
    borderBottom: '1px solid var(--border-color)',
    marginBottom: '1rem',
  },
  nav: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.25rem',
    padding: '0 0.75rem',
  },
  navLinkPlaceholder: {
    display: 'flex',
    alignItems: 'center',
    padding: '0.75rem 0.75rem',
    borderLeft: '2px solid transparent',
  },
  main: {
    flex: 1,
    padding: '2rem',
    overflowY: 'auto',
  },
  title: {
    fontSize: '1.875rem',
    fontWeight: '700',
    color: 'var(--text-primary)',
    margin: 0,
  },
  contentHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '2rem',
  },
  userSummary: {
    display: 'flex',
    gap: '0.75rem',
  },
  contentBody: {
    display: 'flex',
    flexDirection: 'column',
    gap: '2rem',
  },
  welcomeCardPlaceholder: {
    backgroundColor: 'var(--card-bg)',
    padding: '2rem',
    borderRadius: '12px',
    border: '1px solid var(--border-color)',
    boxShadow: '0 6px 24px rgba(0, 0, 0, 0.25)',
  },
  statsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(240px, 1fr))',
    gap: '1.5rem',
  },
  statCardPlaceholder: {
    backgroundColor: 'var(--card-bg)',
    padding: '1.5rem',
    borderRadius: '12px',
    border: '1px solid var(--border-color)',
    textAlign: 'center',
  }
};

export default DashboardSkeleton;
