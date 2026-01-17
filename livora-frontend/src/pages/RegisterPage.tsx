import React, { useState } from 'react';
import { useNavigate, Link } from 'react-router-dom';
import authService from '../api/authService';
import SEO from '../components/SEO';

const RegisterPage: React.FC = () => {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [confirmPassword, setConfirmPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [successMessage, setSuccessMessage] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const navigate = useNavigate();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError(null);

    // Basic validation
    if (!email) {
      setError('Email is required');
      return;
    }

    if (password.length < 8) {
      setError('Password must be at least 8 characters long');
      return;
    }

    if (password !== confirmPassword) {
      setError('Passwords do not match');
      return;
    }

    setIsSubmitting(true);
    try {
      await authService.register({ email, password });
      setError(null);
      // Show local success message
      const successMsg = 'Registration successful! Redirecting to login...';
      setSuccessMessage(successMsg);
      
      setTimeout(() => {
        navigate('/login', { state: { message: 'Registration successful! Please login.' } });
      }, 1500);
    } catch (err: any) {
      console.error('Registration failed:', err);
      if (err.code === 'ERR_NETWORK' || !err.response) {
        setError('The server is currently unreachable. Please check your internet connection or try again later.');
      } else {
        setError(err.response?.data?.message || 'Registration failed. Please try again.');
      }
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div style={styles.container}>
      <SEO 
        title="Register" 
        description="Create a new Livora account to access exclusive features."
        canonical="/register"
      />
      <div style={styles.card}>
        <h2 style={styles.title}>Create account</h2>
        <p style={styles.subtitle}>Join our community today</p>
        
        <form onSubmit={handleSubmit} style={styles.form}>
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
              placeholder="Min 8 characters"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              disabled={isSubmitting}
              style={styles.input}
            />
          </div>

          <div style={styles.field}>
            <label htmlFor="confirmPassword" style={styles.label}>Confirm Password</label>
            <input
              id="confirmPassword"
              type="password"
              placeholder="Repeat your password"
              value={confirmPassword}
              onChange={(e) => setConfirmPassword(e.target.value)}
              required
              disabled={isSubmitting}
              style={styles.input}
            />
          </div>

          {error && <div style={styles.error}>{error}</div>}
          {successMessage && <div style={styles.success}>{successMessage}</div>}

          <button 
            type="submit" 
            disabled={isSubmitting}
            style={isSubmitting ? { ...styles.button, ...styles.buttonDisabled } : styles.button}
          >
            {isSubmitting ? 'Creating account...' : 'Create account'}
          </button>
        </form>

        <div style={styles.footer}>
          Already have an account? <Link to="/login" style={styles.link}>Login</Link>
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

export default RegisterPage;
