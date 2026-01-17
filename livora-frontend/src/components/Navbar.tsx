import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const Navbar: React.FC = () => {
  const { isAuthenticated } = useAuth();

  return (
    <nav style={styles.nav}>
      <div style={styles.container}>
        <div style={styles.logo}>
          <Link to="/" style={styles.logoLink}>JoinLivora</Link>
        </div>
        <div style={styles.links}>
          <Link to="/" style={styles.link}>Home</Link>
          {!isAuthenticated && (
            <>
              <Link to="/login" style={styles.link}>Login</Link>
              <Link to="/register" style={styles.registerButton}>Register</Link>
            </>
          )}
        </div>
      </div>
    </nav>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  nav: {
    width: '100%',
    backgroundColor: '#ffffff',
    borderBottom: '1px solid #e5e7eb',
    padding: '1rem 0',
    fontFamily: 'system-ui, -apple-system, sans-serif',
  },
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
  logoLink: {
    color: '#6772e5',
    textDecoration: 'none',
  },
  links: {
    display: 'flex',
    gap: '1.5rem',
    alignItems: 'center',
  },
  link: {
    textDecoration: 'none',
    color: '#4b5563',
    fontWeight: '600',
    fontSize: '1rem',
    transition: 'color 0.2s ease',
  },
  registerButton: {
    padding: '0.5rem 1rem',
    fontSize: '1rem',
    fontWeight: '600',
    textDecoration: 'none',
    color: '#ffffff',
    backgroundColor: '#6772e5',
    borderRadius: '6px',
    transition: 'opacity 0.2s ease',
  },
};

export default Navbar;
