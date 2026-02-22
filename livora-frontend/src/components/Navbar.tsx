import React from 'react';
import { Link, useLocation } from 'react-router-dom';
import { useAuth } from '@/auth/useAuth';
import { useWallet } from '@/wallet/WalletContext';
import { colors, gradients } from '@/utils/theme';

const TokenDisplay: React.FC = () => {
  const { balance, lastDelta } = useWallet();

  return (
    <div style={styles.balanceContainer}>
      <div style={styles.balanceBox}>
        <span style={styles.coinIcon}>🪙</span>
        <span style={styles.balanceText}>{balance.toLocaleString()}</span>
      </div>
      {lastDelta !== null && (
        <div 
          key={Date.now()} 
          style={{
            ...styles.deltaBadge,
            color: lastDelta > 0 ? '#10b981' : '#ef4444',
          }}
          className="wallet-delta-animation"
        >
          {lastDelta > 0 ? `+${lastDelta}` : lastDelta}
        </div>
      )}
    </div>
  );
};

const Navbar: React.FC = () => {
  const { isAuthenticated, user, logout } = useAuth();
  const location = useLocation();

  const handleLogout = () => {
    logout();
    // Do not force navigation; ProtectedRoute will handle it if the current page is protected
  };

  const renderLinks = () => {
    const commonLinks = (
      <>
        <Link 
          to="/" 
          style={{
            ...styles.link,
            color: location.pathname === '/' ? colors.textPrimary : colors.textSecondary
          }}
        >
          Home
        </Link>
        <Link 
          to="/explore" 
          style={{
            ...styles.link,
            color: location.pathname === '/explore' ? colors.textPrimary : colors.textSecondary
          }}
        >
          Explore Creators
        </Link>
      </>
    );

    if (!isAuthenticated) {
      return (
        <>
          {commonLinks}
          <Link to="/pricing" style={styles.link}>Pricing</Link>
          <Link to="/login" style={styles.link}>Login</Link>
          <Link to="/register" style={styles.registerButton}>Register</Link>
        </>
      );
    }

    if (user?.role === 'ADMIN') {
      return (
        <>
          {commonLinks}
          <Link 
            to="/admin" 
            style={{
              ...styles.link,
              color: location.pathname.startsWith('/admin') ? colors.textPrimary : colors.textSecondary
            }}
          >
            Admin Dashboard
          </Link>
          <TokenDisplay />
          <button onClick={handleLogout} style={styles.logoutButton}>Logout</button>
        </>
      );
    }

    if (user?.role === 'CREATOR') {
      return (
        <>
          {commonLinks}
          <Link 
            to="/creator/dashboard" 
            style={{
              ...styles.link,
              color: location.pathname === '/creator/dashboard' ? colors.textPrimary : colors.textSecondary
            }}
          >
            Creator Dashboard
          </Link>
          <TokenDisplay />
          <button onClick={handleLogout} style={styles.logoutButton}>Logout</button>
        </>
      );
    }

    // Logged in USER (includes VIEWER, PREMIUM)
    return (
      <>
        {commonLinks}
        <Link 
          to="/dashboard" 
          style={{
            ...styles.link,
            color: location.pathname === '/dashboard' ? colors.textPrimary : colors.textSecondary
          }}
        >
          Dashboard
        </Link>
        <TokenDisplay />
        <button onClick={handleLogout} style={styles.logoutButton}>Logout</button>
      </>
    );
  };

  return (
    <nav className="sticky top-0 z-50 w-full backdrop-blur-xl bg-black/40 border-b border-[#16161D] shadow-[0_1px_0_rgba(255,255,255,0.05)] py-4">
      <div style={styles.container}>
        <div style={{ ...styles.logo, display: 'flex', alignItems: 'center' }}>
          <Link to="/" className="flex items-center">
            <img 
              src="/joinlivora_livewebcams.png"
              alt="JoinLivora Live Webcams"
              className="h-[56px] w-auto hover:scale-105 transition-transform duration-300"
            />
          </Link>
        </div>
        <div style={styles.links}>
          {renderLinks()}
        </div>
      </div>
    </nav>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    maxWidth: '1200px',
    margin: '0 auto',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    padding: '0 20px',
  },
  logo: {
    fontSize: '1.5rem',
    fontWeight: '800',
    letterSpacing: '-0.025em',
  },
  logoText: {
    fontSize: '1.5rem',
    fontWeight: '800',
    letterSpacing: '-0.025em',
    backgroundImage: gradients.primary,
    WebkitBackgroundClip: 'text',
    backgroundClip: 'text',
    color: 'transparent',
  },
  links: {
    display: 'flex',
    gap: '1.5rem',
    alignItems: 'center',
  },
  link: {
    textDecoration: 'none',
    color: colors.textSecondary,
    fontWeight: '600',
    fontSize: '0.875rem',
    transition: 'color 0.2s ease',
  },
  registerButton: {
    padding: '0.5rem 1rem',
    fontSize: '0.875rem',
    fontWeight: '600',
    textDecoration: 'none',
    color: colors.textPrimary,
    backgroundImage: gradients.primary,
    borderRadius: 8,
    transition: 'opacity 0.2s ease',
  },
  logoutButton: {
    padding: '0.5rem 1rem',
    fontSize: '0.875rem',
    fontWeight: '600',
    textDecoration: 'none',
    color: colors.textPrimary,
    backgroundColor: 'transparent',
    border: `1px solid ${colors.accentPurple}`,
    borderRadius: 8,
    cursor: 'pointer',
    transition: 'all 0.2s ease',
  },
  balanceContainer: {
    display: 'flex',
    alignItems: 'center',
    position: 'relative',
    marginRight: '0.5rem',
    marginLeft: '0.5rem',
  },
  balanceBox: {
    display: 'flex',
    alignItems: 'center',
    gap: '6px',
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    padding: '6px 12px',
    borderRadius: '12px',
    border: '1px solid rgba(255, 255, 255, 0.1)',
  },
  coinIcon: {
    fontSize: '1rem',
  },
  balanceText: {
    color: colors.textPrimary,
    fontWeight: '700',
    fontSize: '0.9rem',
  },
  deltaBadge: {
    position: 'absolute',
    top: '-20px',
    right: '0',
    fontWeight: '800',
    fontSize: '0.85rem',
    pointerEvents: 'none',
    textShadow: '0 2px 4px rgba(0,0,0,0.3)',
  },
};

export default Navbar;
