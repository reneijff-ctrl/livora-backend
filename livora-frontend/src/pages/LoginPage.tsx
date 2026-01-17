import React, { useState, useEffect } from 'react';
import { useNavigate, useLocation, Link } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import SEO from '../components/SEO';

const LoginPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState('');
  const [isError, setIsError] = useState(false);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();
  const { login: authLogin, isAuthenticated } = useAuth();

  useEffect(() => {
    if (location.state?.message) {
      setMessage(location.state.message);
      setIsError(false);
      // Clear state to prevent message showing again on refresh
      window.history.replaceState({}, document.title);
    }
  }, [location]);

  useEffect(() => {
    if (isAuthenticated) {
      navigate('/dashboard', { replace: true });
    }
  }, [isAuthenticated, navigate]);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setMessage('');
    setIsError(false);
    setIsSubmitting(true);

    try {
      await authLogin(email, password);
      navigate('/dashboard');
    } catch (error: any) {
      setMessage(error.response?.data?.message || 'Login failed. Please check your credentials.');
      setIsError(true);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={styles.container}>
      <SEO 
        title="Login" 
        description="Sign in to your Livora account to access your dashboard and premium content."
        canonical="/login"
      />
      <div style={styles.card}>
        <h2 style={styles.title}>Welcome Back</h2>
        <p style={styles.subtitle}>Sign in to your account</p>

        <form onSubmit={handleLogin} style={styles.form}>
          <div style={styles.field}>
            <label htmlFor="email" style={styles.label}>Email</label>
            <input
              id="email"
              type="email"
              placeholder="your@email.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              disabled={isSubmitting}
              style={styles.input}
            />
          </div>

          <div style={styles.field}>
            <label htmlFor="password" style={styles.label}>Password</label>
            <input
              id="password"
              type="password"
              placeholder="••••••••"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              disabled={isSubmitting}
              style={styles.input}
            />
          </div>

          {message && (
            <div style={isError ? styles.error : styles.success}>
              {message}
            </div>
          )}

          <button 
            type="submit" 
            disabled={isSubmitting}
            style={isSubmitting ? { ...styles.button, ...styles.buttonDisabled } : styles.button}
          >
            {isSubmitting ? 'Signing in...' : 'Login'}
          </button>
        </form>

        <div style={styles.footer}>
          No account yet? <Link to="/register" style={styles.link}>Register</Link>
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
    minHeight: 'calc(100vh - 80px)', // Adjust for navbar height
    fontFamily: 'system-ui, -apple-system, sans-serif',
    backgroundColor: '#f9fafb',
    padding: '20px',
  },
  card: {
    width: '100%',
    maxWidth: '400px',
    padding: '2.5rem',
    backgroundColor: '#ffffff',
    borderRadius: '12px',
    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06)',
  },
  title: {
    fontSize: '1.875rem',
    fontWeight: '700',
    color: '#111827',
    marginBottom: '0.5rem',
    textAlign: 'center',
  },
  subtitle: {
    fontSize: '1rem',
    color: '#6b7280',
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
    color: '#374151',
  },
  input: {
    padding: '0.75rem',
    borderRadius: '6px',
    border: '1px solid #d1d5db',
    fontSize: '1rem',
    outline: 'none',
    transition: 'border-color 0.2s ease',
  },
  error: {
    padding: '0.75rem',
    borderRadius: '6px',
    backgroundColor: '#fef2f2',
    color: '#dc2626',
    fontSize: '0.875rem',
    border: '1px solid #fee2e2',
  },
  success: {
    padding: '0.75rem',
    borderRadius: '6px',
    backgroundColor: '#ecfdf5',
    color: '#059669',
    fontSize: '0.875rem',
    border: '1px solid #d1fae5',
  },
  button: {
    marginTop: '0.5rem',
    padding: '0.75rem',
    backgroundColor: '#6772e5',
    color: 'white',
    border: 'none',
    borderRadius: '6px',
    fontSize: '1rem',
    fontWeight: '600',
    cursor: 'pointer',
    transition: 'background-color 0.2s ease',
  },
  buttonDisabled: {
    backgroundColor: '#9ca3af',
    cursor: 'not-allowed',
  },
  footer: {
    textAlign: 'center',
    marginTop: '1.5rem',
    fontSize: '0.875rem',
    color: '#4b5563',
  },
  link: {
    color: '#6772e5',
    textDecoration: 'none',
    fontWeight: '600',
  },
};

export default LoginPage;
