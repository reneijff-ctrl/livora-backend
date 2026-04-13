import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import authStore, { getDashboardRouteByRole } from '../store/authStore';
import apiClient from '../api/apiClient';
import { setAccessToken } from '../auth/jwt';
import SEO from '../components/SEO';

const AdminSetup2FAPage: React.FC = () => {
  const [step, setStep] = useState<'init' | 'confirm'>('init');
  const [qrUrl, setQrUrl] = useState<string | null>(null);
  const [secret, setSecret] = useState<string | null>(null);
  const [code, setCode] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const navigate = useNavigate();

  const { preAuthToken } = authStore.getState();

  useEffect(() => {
    if (!preAuthToken) {
      navigate('/login', { replace: true });
    }
  }, [preAuthToken, navigate]);

  const handleInit = async () => {
    setError(null);
    setIsSubmitting(true);
    try {
      const response = await apiClient.post<{ secret: string; qrUrl: string }>(
        '/auth/2fa/setup-init',
        {},
        { headers: { Authorization: `Bearer ${preAuthToken}` } }
      );
      setSecret(response.data.secret);
      setQrUrl(response.data.qrUrl);
      setStep('confirm');
    } catch (err: any) {
      setError(err.response?.data?.message || 'Failed to initiate 2FA setup. Please try again.');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleConfirm = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);
    setIsSubmitting(true);
    try {
      const response = await apiClient.post<{ accessToken: string }>(
        `/auth/2fa/setup-confirm?code=${code}`,
        {},
        { headers: { Authorization: `Bearer ${preAuthToken}` } }
      );

      const { accessToken } = response.data;

      setAccessToken(accessToken);
      authStore.setAuthFromBackend({ accessToken, user: null });

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
        title="Set Up Two-Factor Authentication"
        description="Configure two-factor authentication to secure your admin account."
        canonical="/admin/setup-2fa"
      />
      <div style={styles.card}>
        <h2 style={styles.title}>Admin 2FA Setup Required</h2>
        <p style={styles.subtitle}>
          Your admin account must have two-factor authentication enabled before you can log in.
        </p>

        {step === 'init' && (
          <div>
            <p style={styles.info}>
              Click below to generate your authenticator QR code. You will need the Google Authenticator
              or any TOTP-compatible app to scan it.
            </p>
            {error && <div style={styles.error}>{error}</div>}
            <button
              onClick={handleInit}
              disabled={isSubmitting}
              style={isSubmitting ? { ...styles.button, ...styles.buttonDisabled } : styles.button}
            >
              {isSubmitting ? 'Generating...' : 'Generate QR Code'}
            </button>
          </div>
        )}

        {step === 'confirm' && qrUrl && (
          <div>
            <p style={styles.info}>
              Scan the QR code below with your authenticator app, then enter the 6-digit code to confirm setup.
            </p>
            <div style={styles.qrContainer}>
              <img
                src={`https://api.qrserver.com/v1/create-qr-code/?size=200x200&data=${encodeURIComponent(qrUrl)}`}
                alt="TOTP QR Code"
                style={styles.qrImage}
              />
            </div>
            {secret && (
              <p style={styles.secretHint}>
                Manual entry key: <code style={styles.secretCode}>{secret}</code>
              </p>
            )}
            <form onSubmit={handleConfirm} style={styles.form}>
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
                {isSubmitting ? 'Verifying...' : 'Enable 2FA & Sign In'}
              </button>
            </form>
          </div>
        )}

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
    minHeight: '100vh',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#0a0a0a',
    padding: '20px',
  },
  card: {
    backgroundColor: '#1a1a1a',
    borderRadius: '12px',
    padding: '40px',
    width: '100%',
    maxWidth: '440px',
    border: '1px solid #2a2a2a',
  },
  title: {
    color: '#ffffff',
    fontSize: '24px',
    fontWeight: 700,
    marginBottom: '8px',
    textAlign: 'center',
  },
  subtitle: {
    color: '#888888',
    fontSize: '14px',
    textAlign: 'center',
    marginBottom: '24px',
  },
  info: {
    color: '#aaaaaa',
    fontSize: '14px',
    marginBottom: '20px',
    lineHeight: '1.5',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '16px',
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: '6px',
  },
  label: {
    color: '#cccccc',
    fontSize: '14px',
    fontWeight: 500,
  },
  input: {
    backgroundColor: '#2a2a2a',
    border: '1px solid #3a3a3a',
    borderRadius: '8px',
    color: '#ffffff',
    fontSize: '18px',
    letterSpacing: '0.3em',
    padding: '12px 16px',
    textAlign: 'center',
    outline: 'none',
  },
  button: {
    backgroundColor: '#7c3aed',
    border: 'none',
    borderRadius: '8px',
    color: '#ffffff',
    cursor: 'pointer',
    fontSize: '16px',
    fontWeight: 600,
    padding: '12px',
    width: '100%',
    marginTop: '8px',
  },
  buttonDisabled: {
    backgroundColor: '#4a4a4a',
    cursor: 'not-allowed',
  },
  error: {
    backgroundColor: '#2d1b1b',
    border: '1px solid #5a2020',
    borderRadius: '8px',
    color: '#ff6b6b',
    fontSize: '14px',
    padding: '10px 14px',
  },
  qrContainer: {
    display: 'flex',
    justifyContent: 'center',
    marginBottom: '16px',
  },
  qrImage: {
    borderRadius: '8px',
    border: '4px solid #ffffff',
  },
  secretHint: {
    color: '#888888',
    fontSize: '12px',
    textAlign: 'center',
    marginBottom: '16px',
  },
  secretCode: {
    backgroundColor: '#2a2a2a',
    borderRadius: '4px',
    color: '#cccccc',
    fontFamily: 'monospace',
    fontSize: '12px',
    padding: '2px 6px',
  },
  footer: {
    marginTop: '24px',
    textAlign: 'center',
  },
  backLink: {
    background: 'none',
    border: 'none',
    color: '#7c3aed',
    cursor: 'pointer',
    fontSize: '14px',
  },
};

export default AdminSetup2FAPage;
