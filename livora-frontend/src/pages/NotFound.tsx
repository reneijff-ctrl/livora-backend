import React from 'react';
import { useNavigate, Link } from 'react-router-dom';
import SEO from '../components/SEO';

const NotFound: React.FC = () => {
  const navigate = useNavigate();

  return (
    <div style={styles.container}>
      <SEO title="404 - Page Not Found" />
      <div style={styles.content}>
        <div style={styles.iconContainer}>
          <span style={styles.icon}>🔍</span>
        </div>
        <h1 style={styles.title}>404 - Page Not Found</h1>
        <p style={styles.message}>
          The page you are looking for might have been removed, had its name changed, or is temporarily unavailable.
        </p>
        <div style={styles.actions}>
          <button 
            onClick={() => navigate(-1)} 
            style={styles.backButton}
          >
            Go Back
          </button>
          <Link to="/" style={styles.homeButton}>
            Go Home
          </Link>
        </div>
        
        <div style={styles.suggestions}>
          <h3 style={styles.suggestionTitle}>Looking for creators?</h3>
          <Link to="/explore" style={styles.exploreLink}>
            Explore Creators Hub
          </Link>
        </div>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    minHeight: '80vh',
    padding: '2rem',
    textAlign: 'center',
    backgroundColor: 'transparent',
  },
  content: {
    maxWidth: '500px',
    backgroundColor: '#0F0F14',
    padding: '3rem 2rem',
    borderRadius: '24px',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
    border: '1px solid rgba(255, 255, 255, 0.05)',
  },
  iconContainer: {
    fontSize: '4rem',
    marginBottom: '1.5rem',
  },
  icon: {
    display: 'inline-block',
    animation: 'float 3s ease-in-out infinite',
  },
  title: {
    fontSize: '2rem',
    color: '#F4F4F5',
    marginBottom: '1rem',
    fontWeight: '800',
  },
  message: {
    fontSize: '1.1rem',
    color: '#71717A',
    marginBottom: '2.5rem',
    lineHeight: '1.6',
  },
  actions: {
    display: 'flex',
    gap: '1rem',
    justifyContent: 'center',
    marginBottom: '3rem',
  },
  backButton: {
    padding: '0.75rem 1.5rem',
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    color: '#A1A1AA',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    fontSize: '1rem',
    fontWeight: '600',
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  homeButton: {
    padding: '0.75rem 1.5rem',
    backgroundColor: '#6366f1',
    color: 'white',
    textDecoration: 'none',
    borderRadius: '12px',
    fontSize: '1rem',
    fontWeight: '600',
    transition: 'all 0.2s',
    boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
  },
  suggestions: {
    borderTop: '1px solid rgba(255, 255, 255, 0.05)',
    paddingTop: '2rem',
  },
  suggestionTitle: {
    fontSize: '1rem',
    color: '#52525B',
    marginBottom: '0.75rem',
    fontWeight: '600',
  },
  exploreLink: {
    color: '#818cf8',
    textDecoration: 'none',
    fontWeight: '600',
    fontSize: '1.1rem',
  }
};

export default NotFound;
