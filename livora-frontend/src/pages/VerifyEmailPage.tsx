import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { verifyEmail } from '../api/authService';
import { useAuth } from '../auth/useAuth';
import SEO from '../components/SEO';

const VerifyEmailPage: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const { bootstrap } = useAuth();
  const [status, setStatus] = useState<'loading' | 'success' | 'error'>('loading');
  const [message, setMessage] = useState('Verifying your email...');

  const token = searchParams.get('token');

  useEffect(() => {
    if (!token) {
      setStatus('error');
      setMessage('Invalid or missing verification token.');
      return;
    }

    const doVerify = async () => {
      try {
        await verifyEmail(token);
        setStatus('success');
        setMessage('Your email has been successfully verified!');
        // Refresh user state to reflect verified status
        await bootstrap();
        // Redirect to Viewer Hub after 3 seconds, which will route based on role
        setTimeout(() => {
          navigate('/dashboard');
        }, 3000);
      } catch (error: any) {
        setStatus('error');
        setMessage(error.response?.data?.message || 'Verification failed. The link may be expired or invalid.');
      }
    };

    doVerify();
  }, [token, navigate, bootstrap]);

  return (
    <div style={styles.container}>
      <SEO title="Verify Email" />
      <div style={styles.card}>
        <div style={styles.iconContainer}>
          {status === 'loading' && <span style={styles.loadingIcon}>⌛</span>}
          {status === 'success' && <span style={styles.successIcon}>✅</span>}
          {status === 'error' && <span style={styles.errorIcon}>❌</span>}
        </div>
        <h1 style={styles.title}>
          {status === 'loading' && 'Verifying...'}
          {status === 'success' && 'Verified!'}
          {status === 'error' && 'Verification Failed'}
        </h1>
        <p style={styles.message}>{message}</p>
        {status !== 'loading' && (
          <button 
            onClick={() => navigate('/dashboard')} 
            style={styles.button}
          >
            Go to Viewer Hub
          </button>
        )}
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    minHeight: 'calc(100vh - 80px)',
    backgroundColor: 'transparent',
    padding: '20px',
  },
  card: {
    width: '100%',
    maxWidth: '400px',
    padding: '2.5rem',
    backgroundColor: '#0F0F14',
    borderRadius: '24px',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    textAlign: 'center',
  },
  iconContainer: {
    fontSize: '3rem',
    marginBottom: '1.5rem',
  },
  loadingIcon: {
    animation: 'spin 2s linear infinite',
    display: 'inline-block',
  },
  successIcon: {
    color: '#52c41a',
  },
  errorIcon: {
    color: '#f5222d',
  },
  title: {
    fontSize: '1.5rem',
    fontWeight: '800',
    color: '#F4F4F5',
    marginBottom: '1rem',
  },
  message: {
    fontSize: '1rem',
    color: '#71717A',
    marginBottom: '2rem',
    lineHeight: '1.5',
  },
  button: {
    width: '100%',
    padding: '0.875rem',
    backgroundColor: '#6366f1',
    color: 'white',
    border: 'none',
    borderRadius: '12px',
    fontSize: '1rem',
    fontWeight: '700',
    cursor: 'pointer',
    boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
    transition: 'all 0.2s ease',
  },
};

export default VerifyEmailPage;
