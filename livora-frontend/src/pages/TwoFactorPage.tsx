import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import authStore, { getDashboardRouteByRole } from '../store/authStore';
import apiClient from '../api/apiClient';
import { setAccessToken } from '../auth/jwt';
import SEO from '../components/SEO';

const TwoFactorPage: React.FC = () => {
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);

    const { preAuthToken } = authStore.getState();

    if (!preAuthToken) {
      setError('Session expired. Please log in again.');
      navigate('/login', { replace: true });
      return;
    }

    try {
      const response = await apiClient.post<{ accessToken: string }>(
        `/auth/2fa/verify?code=${code}`,
        {},
        { headers: { Authorization: `Bearer ${preAuthToken}` } }
      );

      const { accessToken } = response.data;

      // Store the real access token and complete authentication
      setAccessToken(accessToken);
      authStore.setAuthFromBackend({ accessToken, user: null });

      // Fetch full profile to populate user state
      await authStore.refresh();

      const { user } = authStore.getState();
      navigate(getDashboardRouteByRole(user?.role), { replace: true });
    } catch (err: any) {
      if (err.response?.status === 401) {
        setError('Invalid authentication code. Please try again.');
      } else if (err.response?.status === 403) {
        setError('Session expired. Please log in again.');
        navigate('/login', { replace: true });
      } else {
        setError('Something went wrong. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={styles.container}>
      <SEO
        title="Two-Factor Authentication"
        description="Enter your authentication code to complete sign in."
        canonical="/auth/2fa"
      />
      <div style={styles.card}>
        <h2 style={styles.title}>Two-Factor Authentication</h2>
        <p style={styles.subtitle}>
          Enter the 6-digit code from your authenticator app.
        </p>

        <form onSubmit={handleSubmit} style={styles.form}>
          <div style={styles.field}>
            <label htmlFor="code" style={styles.label}>Authentication Code</label>
            <input
              id="code"
              type="text"
              inputMode="numeric"
              pattern="[0-9]{6}"
              maxLength={6}
              placeholder="000000"
              value={code}
              onChange={(e) => setCode(e.target.value.replace(/\D/g, ''))}
              required
              disabled={isSubmitting}
              style={styles.input}
              autoComplete="one-time-code"
              autoFocus
            />
          </div>

          {error && <div style={styles.error}>{error}</div>}

          <button
            type="submit"
            disabled={isSubmitting || code.length !== 6}
            style={isSubmitting || code.length !== 6 ? { ...styles.button, ...styles.buttonDisabled } : styles.button}
          >
            {isSubmitting ? 'Verifying...' : 'Verify'}
          </button>
        </form>

        <div style={styles.footer}>
          <button onClick={() => navigate('/login', { replace: true })} style={styles.backLink}>
            ← Back to login
          </button>
        </div>
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
    fontFamily: 'system-ui, -apple-system, sans-serif',
    backgroundColor: 'transparent',
    padding: '20px',
  },
  card: {
    width: '100%',
    maxWidth: '400px',
    padding: '2.5rem',
    backgroundColor: '#0F0F14',
    borderRadius: '16px',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
  },
  title: {
    fontSize: '1.875rem',
    fontWeight: '700',
    color: '#F4F4F5',
    marginBottom: '0.5rem',
    textAlign: 'center',
  },
  subtitle: {
    fontSize: '1rem',
    color: '#71717A',
    marginBottom: '2rem',
    textAlign: 'center',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.25rem',
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.375rem',
  },
  label: {
    fontSize: '0.875rem',
    fontWeight: '600',
    color: '#A1A1AA',
  },
  input: {
    padding: '0.75rem',
    borderRadius: '8px',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    backgroundColor: '#08080A',
    color: '#FFFFFF',
    fontSize: '1.5rem',
    letterSpacing: '0.5rem',
    textAlign: 'center',
    outline: 'none',
  },
  error: {
    padding: '0.75rem',
    borderRadius: '8px',
    backgroundColor: 'rgba(239, 68, 68, 0.1)',
    color: '#ef4444',
    fontSize: '0.875rem',
    border: '1px solid rgba(239, 68, 68, 0.2)',
    textAlign: 'center',
  },
  button: {
    marginTop: '0.5rem',
    padding: '0.75rem',
    backgroundColor: '#6366F1',
    color: 'white',
    border: 'none',
    borderRadius: '8px',
    fontSize: '1rem',
    fontWeight: '600',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
  },
  buttonDisabled: {
    backgroundColor: '#27272A',
    color: '#71717A',
    cursor: 'not-allowed',
  },
  footer: {
    textAlign: 'center',
    marginTop: '1.5rem',
  },
  backLink: {
    background: 'none',
    border: 'none',
    color: '#6366F1',
    fontSize: '0.875rem',
    fontWeight: '600',
    cursor: 'pointer',
    padding: 0,
  },
};

export default TwoFactorPage;
