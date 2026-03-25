import React from 'react';
import { useAuth } from '../auth/useAuth';
import SEO from '../components/SEO';

interface DashboardPlaceholderProps {
  title: string;
}

/**
 * A clean placeholder component for pages that are under construction.
 */
const DashboardPlaceholder: React.FC<DashboardPlaceholderProps> = ({ title }) => {
  const { user } = useAuth();

  const containerStyle: React.CSSProperties = {
    padding: '3rem 2rem',
    maxWidth: '1200px',
    margin: '0 auto',
    fontFamily: 'system-ui, -apple-system, sans-serif',
  };

  const cardStyle: React.CSSProperties = {
    backgroundColor: '#fff',
    borderRadius: '12px',
    padding: '3rem',
    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
    textAlign: 'center',
    border: '1px solid #e5e7eb',
  };

  const titleStyle: React.CSSProperties = {
    fontSize: '2rem',
    fontWeight: '700',
    color: '#111827',
    marginBottom: '1rem',
  };

  const textStyle: React.CSSProperties = {
    fontSize: '1.125rem',
    color: '#6b7280',
    marginBottom: '2rem',
    lineHeight: '1.6',
  };

  const badgeStyle: React.CSSProperties = {
    display: 'inline-block',
    padding: '0.5rem 1rem',
    backgroundColor: '#f3f4f6',
    color: '#374151',
    borderRadius: '9999px',
    fontSize: '0.875rem',
    fontWeight: '600',
    marginBottom: '1.5rem',
  };

  return (
    <div style={containerStyle}>
      <SEO title={`${title} - Under Construction`} />
      <div style={cardStyle}>
        <div style={badgeStyle}>Under Construction</div>
        <h1 style={titleStyle}>{title}</h1>
        <p style={textStyle}>
          Welcome, {user?.email || 'User'}. This section of the platform is currently being developed.
          Please check back later for more features and analytics.
        </p>
        <div style={{ fontSize: '3rem', marginBottom: '1rem' }}>🏗️</div>
      </div>
    </div>
  );
};

export default DashboardPlaceholder;
