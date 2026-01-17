import React from 'react';
import { Link } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';

const HomePage: React.FC = () => {
  const { isAuthenticated } = useAuth();

  return (
    <div style={styles.container}>
      <header style={styles.hero}>
        <h1 style={styles.title}>JoinLivora</h1>
        <p style={styles.subtitle}>Live creators. Real interaction.</p>
        <div style={styles.buttonContainer}>
          {isAuthenticated ? (
            <Link to="/dashboard" style={styles.registerButton}>Go to Dashboard</Link>
          ) : (
            <>
              <Link to="/login" style={styles.loginButton}>Login</Link>
              <Link to="/register" style={styles.registerButton}>Register</Link>
            </>
          )}
        </div>
      </header>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '100vh',
    fontFamily: 'system-ui, -apple-system, sans-serif',
    backgroundColor: '#ffffff',
    color: '#1a1a1a',
    textAlign: 'center',
    padding: '20px',
  },
  hero: {
    maxWidth: '800px',
    width: '100%',
  },
  title: {
    fontSize: 'clamp(2.5rem, 8vw, 4.5rem)',
    fontWeight: '800',
    marginBottom: '1rem',
    letterSpacing: '-0.025em',
    color: '#6772e5',
  },
  subtitle: {
    fontSize: 'clamp(1.25rem, 4vw, 1.75rem)',
    color: '#4b5563',
    marginBottom: '2.5rem',
  },
  buttonContainer: {
    display: 'flex',
    gap: '1rem',
    justifyContent: 'center',
    flexWrap: 'wrap',
  },
  loginButton: {
    padding: '0.75rem 2rem',
    fontSize: '1.125rem',
    fontWeight: '600',
    textDecoration: 'none',
    color: '#6772e5',
    border: '2px solid #6772e5',
    borderRadius: '8px',
    transition: 'all 0.2s ease',
  },
  registerButton: {
    padding: '0.75rem 2rem',
    fontSize: '1.125rem',
    fontWeight: '600',
    textDecoration: 'none',
    color: '#ffffff',
    backgroundColor: '#6772e5',
    border: '2px solid #6772e5',
    borderRadius: '8px',
    transition: 'all 0.2s ease',
  },
};

export default HomePage;
