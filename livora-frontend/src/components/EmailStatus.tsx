import React, { useState } from 'react';
import { useAuth } from '../auth/useAuth';
import { resendVerification } from '../api/authService';
import { showToast } from './Toast';

const EmailStatus: React.FC = () => {
  const { user } = useAuth();
  const [loading, setLoading] = useState(false);

  if (!user || user.emailVerified) {
    return null;
  }

  const handleResend = async () => {
    setLoading(true);
    try {
      await resendVerification();
      showToast('Verification email sent successfully!', 'success');
    } catch (error: any) {
      const message = error.response?.data?.message || 'Failed to resend verification email.';
      showToast(message, 'error');
    } finally {
      setLoading(false);
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.content}>
        <span style={styles.icon}>⚠️</span>
        <div style={styles.textContainer}>
          <p style={styles.title}>Email not verified</p>
          <p style={styles.description}>
            Please verify your email address to access all features. 
            Check your inbox for the verification link.
          </p>
        </div>
        <button 
          onClick={handleResend} 
          disabled={loading}
          style={{
            ...styles.button,
            opacity: loading ? 0.7 : 1,
            cursor: loading ? 'not-allowed' : 'pointer'
          }}
        >
          {loading ? 'Sending...' : 'Resend link'}
        </button>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    backgroundColor: '#fff7e6',
    border: '1px solid #ffe58f',
    borderRadius: '8px',
    padding: '1rem',
    marginBottom: '1.5rem',
    width: '100%',
  },
  content: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
    flexWrap: 'wrap',
  },
  icon: {
    fontSize: '1.5rem',
  },
  textContainer: {
    flex: 1,
    minWidth: '200px',
  },
  title: {
    margin: 0,
    fontWeight: 'bold',
    color: '#874d00',
    fontSize: '0.95rem',
  },
  description: {
    margin: '0.25rem 0 0 0',
    fontSize: '0.85rem',
    color: '#874d00',
  },
  button: {
    padding: '0.5rem 1rem',
    backgroundColor: '#faad14',
    color: 'white',
    border: 'none',
    borderRadius: '4px',
    fontWeight: '600',
    fontSize: '0.85rem',
    transition: 'background-color 0.2s',
  },
};

export default EmailStatus;
