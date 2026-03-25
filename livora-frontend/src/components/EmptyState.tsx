import React from 'react';
import Logo from '@/components/Logo';

interface EmptyStateProps {
  message: string;
  icon?: string;
  showLogo?: boolean;
  actionLabel?: string;
  onAction?: () => void;
}

/**
 * A reusable component for displaying a friendly message when a list or dataset is empty.
 * Following the requirements: no loaders, no error treatment, just a friendly message.
 * Now supports a subtle logo watermark.
 */
const EmptyState: React.FC<EmptyStateProps> = ({ message, icon, showLogo = true, actionLabel, onAction }) => {
  return (
    <div style={styles.container}>
      <style>{`
        @keyframes calm-fade-in {
          from { opacity: 0; transform: translateY(5px); }
          to { opacity: 0.15; transform: translateY(0); }
        }
      `}</style>
      
      {showLogo && (
        <div style={styles.logoWrapper}>
          <Logo 
            size={60} 
            glow={false} 
            style={{ 
              filter: 'grayscale(1) brightness(2)',
              pointerEvents: 'none' 
            }} 
          />
        </div>
      )}

      {icon && !showLogo && <span style={styles.icon}>{icon}</span>}
      <p style={styles.message}>{message}</p>
      {actionLabel && onAction && (
        <button onClick={onAction} style={styles.button}>
          {actionLabel}
        </button>
      )}
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    padding: '4rem 2rem',
    textAlign: 'center',
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    width: '100%',
    position: 'relative',
    backgroundColor: 'transparent',
  },
  logoWrapper: {
    marginBottom: '1.5rem',
    animation: 'calm-fade-in 1.5s ease-out forwards',
    opacity: 0, 
    filter: 'opacity(0.1)',
  },
  icon: {
    fontSize: '3rem',
    marginBottom: '1rem',
    opacity: 0.2,
    color: '#71717A',
  },
  message: {
    fontSize: '1rem',
    fontWeight: '500',
    color: '#71717A',
    margin: 0,
    lineHeight: '1.6',
    maxWidth: '320px',
    zIndex: 1,
  },
  button: {
    marginTop: '1rem',
    padding: '0.625rem 1.25rem',
    fontSize: '0.875rem',
    fontWeight: '700',
    backgroundImage: 'var(--gradient-primary)',
    color: 'white',
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
    transition: 'transform 0.2s ease',
    boxShadow: '0 4px 12px rgba(168, 85, 247, 0.25)',
  }
};

export default EmptyState;
