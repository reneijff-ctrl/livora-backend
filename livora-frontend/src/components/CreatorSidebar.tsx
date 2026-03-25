import React from 'react';
import { Link, useLocation } from 'react-router-dom';

const CreatorSidebar: React.FC = () => {
  const location = useLocation();

  const isActive = (path: string) => {
    if (path === '/creator/dashboard') {
        return location.pathname === '/creator/dashboard';
    }
    return location.pathname === path;
  };

  const getLinkStyle = (path: string) => ({
    ...styles.navLink,
    ...(isActive(path) ? styles.activeNavLink : {}),
  });

  const getLinkClass = (path: string) => {
    return `transition-all duration-200 ${isActive(path) ? '' : 'hover:bg-white/5 hover:text-white'}`;
  };

  return (
    <aside style={styles.sidebar}>
      <div style={styles.sidebarHeader}>
        <h2 style={styles.sidebarTitle}>Creator Hub</h2>
      </div>
      <nav style={styles.nav}>
        <Link to="/" style={getLinkStyle('/')} className={getLinkClass('/')}>
          <span style={styles.navIcon}>🏠</span> Home
        </Link>
        <Link to="/explore" style={getLinkStyle('/explore')} className={getLinkClass('/explore')}>
          <span style={styles.navIcon}>🔍</span> Explore Creators
        </Link>
        <Link to="/creator/dashboard" style={getLinkStyle('/creator/dashboard')} className={getLinkClass('/creator/dashboard')}>
          <span style={styles.navIcon}>📊</span> Creator Dashboard
        </Link>
        <Link to="/creator/settings" style={getLinkStyle('/creator/settings')} className={getLinkClass('/creator/settings')}>
          <span style={styles.navIcon}>⚙️</span> Creator Settings
        </Link>
        <Link to="/creator/live" style={getLinkStyle('/creator/live')} className={getLinkClass('/creator/live')}>
          <span style={styles.navIcon}>🔴</span> Go Live
        </Link>
        {/* Analytics hidden - not implemented yet */}
        <Link to="/creator/profile" style={getLinkStyle('/creator/profile')} className={getLinkClass('/creator/profile')}>
          <span style={styles.navIcon}>👤</span> Profile
        </Link>
        <Link to="/creator/earnings" style={getLinkStyle('/creator/earnings')} className={getLinkClass('/creator/earnings')}>
          <span style={styles.navIcon}>💰</span> Earnings
        </Link>
        <Link to="/creator/content" style={getLinkStyle('/creator/content')} className={getLinkClass('/creator/content')}>
          <span style={styles.navIcon}>📁</span> Content
        </Link>
        <Link to="/creator/upload" style={getLinkStyle('/creator/upload')} className={getLinkClass('/creator/upload')}>
          <span style={styles.navIcon}>📤</span> Upload
        </Link>
      </nav>
    </aside>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  sidebar: {
    width: '260px',
    backgroundColor: '#0F0F14',
    borderRight: '1px solid rgba(255, 255, 255, 0.05)',
    display: 'flex',
    flexDirection: 'column',
    padding: '1.5rem 0',
    minHeight: 'calc(100vh - 64px)',
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
};

export default CreatorSidebar;
