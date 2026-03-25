import React, { useState, useEffect } from 'react';
import { useAuth } from '../auth/useAuth';
import apiClient from '../api/apiClient';
import SEO from '../components/SEO';

/**
 * SettingsPage component.
 * Allows users to manage their account settings.
 * Handles cases where the user settings API might not be available.
 */
const SettingsPage: React.FC = () => {
  const { user, authLoading, refresh } = useAuth();
  const [settings, setSettings] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [apiAvailable, setApiAvailable] = useState(true);

  // Username editing state
  const [username, setUsername] = useState('');
  const [usernameLoaded, setUsernameLoaded] = useState(false);
  const [usernameSaving, setUsernameSaving] = useState(false);
  const [usernameSuccess, setUsernameSuccess] = useState('');
  const [usernameError, setUsernameError] = useState('');

  useEffect(() => {
    // Requirement: Do NOT fetch dashboard data until role is resolved
    if (!user || authLoading) {
        return;
    }

    const fetchSettings = async () => {
      try {
        setLoading(true);
        // Attempt to fetch advanced settings from the API
        const response = await apiClient.get('/user/settings');
        setSettings(response.data);
        setApiAvailable(true);
      } catch (error) {
        // Requirement: If user settings API is not available, do not treat as error
        console.warn('User settings API not available, loading defaults from user profile');
        setApiAvailable(false);
        
        // Requirement: Load defaults from auth user object
        if (user) {
          setSettings({
            email: user.email,
            role: user.role,
            status: user.status,
            emailVerified: user.emailVerified,
          });
        }
      } finally {
        setLoading(false);
      }
    };

    fetchSettings();
  }, [user, authLoading]);

  // Preload current username from auth user
  useEffect(() => {
    if (user && !usernameLoaded) {
      setUsername(user.username || '');
      setUsernameLoaded(true);
    }
  }, [user, usernameLoaded]);

  const validateUsername = (value: string): string | null => {
    if (!value.trim()) return 'Username is required';
    if (value.trim().length < 3) return 'Username must be at least 3 characters';
    if (value.trim().length > 20) return 'Username must be at most 20 characters';
    if (!/^[a-zA-Z0-9_.]+$/.test(value.trim())) return 'Only letters, numbers, underscores, and dots are allowed';
    return null;
  };

  const handleSaveUsername = async () => {
    const trimmed = username.trim();
    setUsernameSuccess('');
    setUsernameError('');

    const validationError = validateUsername(trimmed);
    if (validationError) {
      setUsernameError(validationError);
      return;
    }

    // Skip if unchanged
    if (trimmed === user?.username) {
      setUsernameSuccess('No changes to save.');
      return;
    }

    setUsernameSaving(true);
    try {
      const response = await apiClient.patch('/user/username', { username: trimmed });
      setUsernameSuccess(response.data.message || 'Username updated successfully');
      setUsername(response.data.username);
      // Refresh auth state so new username propagates everywhere
      if (refresh) await refresh();
    } catch (err: any) {
      const msg = err?.response?.data?.error || 'Failed to update username';
      setUsernameError(msg);
    } finally {
      setUsernameSaving(false);
    }
  };

  if (loading) {
    return (
      <div style={styles.centered}>
        <div style={styles.spinner} />
        <p>Loading settings...</p>
      </div>
    );
  }

  return (
    <div style={styles.container}>
      <SEO title="Settings" />
      <h1 style={styles.title}>Account Settings</h1>

      {/* Requirement: Show informational message instead of error */}
      {!apiAvailable && (
        <div style={styles.infoMessage}>
          <span style={{ marginRight: '8px' }}>ℹ️</span>
          Some advanced settings are currently unavailable. Basic profile information is loaded from your session.
        </div>
      )}

      <div style={styles.settingsGrid}>
        <section style={styles.section}>
          <h2 style={styles.sectionTitle}>Profile Information</h2>
          
          <div style={styles.field}>
            <label style={styles.label}>Email Address</label>
            <input 
              type="text" 
              value={settings?.email || ''} 
              readOnly 
              style={{ ...styles.input, ...styles.readOnlyInput }} 
            />
            <p style={styles.helpText}>Your email address is managed via identity provider.</p>
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Account Role</label>
            <div style={{ display: 'flex', alignItems: 'center', gap: '10px' }}>
              <input 
                type="text" 
                value={settings?.role || ''} 
                readOnly 
                style={{ ...styles.input, ...styles.readOnlyInput, flex: 1 }} 
              />
              <span style={{ 
                ...styles.badge, 
                backgroundColor: settings?.role === 'CREATOR' ? '#eff6ff' : '#f3f4f6',
                color: settings?.role === 'CREATOR' ? '#1e40af' : '#4b5563'
              }}>
                {settings?.role}
              </span>
            </div>
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Account Status</label>
            <div style={{ display: 'flex', alignItems: 'center' }}>
              <span style={{ 
                ...styles.statusBadge, 
                backgroundColor: settings?.status === 'ACTIVE' ? 'rgba(34, 197, 94, 0.1)' : 'rgba(239, 68, 68, 0.1)',
                color: settings?.status === 'ACTIVE' ? '#4ade80' : '#f87171',
              }}>
                {settings?.status || 'UNKNOWN'}
              </span>
            </div>
          </div>
        </section>

        {/* Public Profile — username editing */}
        <section style={styles.section}>
          <h2 style={styles.sectionTitle}>Public Profile</h2>

          <div style={styles.field}>
            <label style={styles.label}>Username</label>
            <input
              type="text"
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
                setUsernameError('');
                setUsernameSuccess('');
              }}
              placeholder="your_username"
              maxLength={20}
              style={{
                ...styles.input,
                ...(usernameError ? { borderColor: 'rgba(239, 68, 68, 0.5)' } : {}),
              }}
            />
            <p style={styles.helpText}>This username is visible to creators and other users.</p>
            {usernameError && (
              <p style={{ fontSize: '0.8rem', color: '#f87171', marginTop: '0.25rem' }}>{usernameError}</p>
            )}
            {usernameSuccess && (
              <p style={{ fontSize: '0.8rem', color: '#4ade80', marginTop: '0.25rem' }}>{usernameSuccess}</p>
            )}
          </div>

          <button
            onClick={handleSaveUsername}
            disabled={usernameSaving}
            style={{
              ...styles.saveButton,
              ...(usernameSaving ? styles.disabledButton : {}),
              padding: '0.75rem 2rem',
              fontSize: '0.9rem',
            }}
          >
            {usernameSaving ? 'Saving...' : 'Save Username'}
          </button>
        </section>

        {/* Requirement: Disable save button if API not available */}
        <div style={styles.footer}>
          <button 
            disabled={!apiAvailable} 
            style={{ 
              ...styles.saveButton, 
              ...(apiAvailable ? {} : styles.disabledButton)
            }}
          >
            Save Changes
          </button>
          {!apiAvailable && (
            <p style={styles.footerNote}>Saving is disabled because the settings service is unreachable.</p>
          )}
        </div>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    padding: '2rem',
    maxWidth: '800px',
    margin: '0 auto',
    fontFamily: 'system-ui, -apple-system, sans-serif',
    color: '#F4F4F5',
  },
  centered: {
    display: 'flex',
    flexDirection: 'column',
    justifyContent: 'center',
    alignItems: 'center',
    height: '60vh',
    color: '#71717A',
  },
  spinner: {
    width: '40px',
    height: '40px',
    border: '4px solid rgba(255, 255, 255, 0.05)',
    borderTop: '4px solid #6366F1',
    borderRadius: '50%',
    animation: 'spin 1s linear infinite',
    marginBottom: '1rem',
  },
  title: {
    fontSize: '2.25rem',
    fontWeight: '800',
    marginBottom: '2rem',
    letterSpacing: '-0.025em',
    color: '#F4F4F5',
  },
  infoMessage: {
    padding: '1rem 1.25rem',
    backgroundColor: 'rgba(99, 102, 241, 0.1)',
    color: '#818cf8',
    borderRadius: '12px',
    marginBottom: '2rem',
    border: '1px solid rgba(99, 102, 241, 0.2)',
    fontSize: '0.95rem',
    display: 'flex',
    alignItems: 'center',
    lineHeight: '1.5',
  },
  settingsGrid: {
    display: 'flex',
    flexDirection: 'column',
    gap: '2.5rem',
  },
  section: {
    backgroundColor: '#0F0F14',
    padding: '2rem',
    borderRadius: '24px',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
  },
  sectionTitle: {
    fontSize: '1.25rem',
    fontWeight: '700',
    marginBottom: '1.5rem',
    paddingBottom: '0.75rem',
    borderBottom: '1px solid rgba(255, 255, 255, 0.05)',
    color: '#F4F4F5',
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
    marginBottom: '1.5rem',
  },
  label: {
    fontSize: '0.875rem',
    fontWeight: '600',
    color: '#A1A1AA',
  },
  input: {
    padding: '0.75rem',
    borderRadius: '10px',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    fontSize: '1rem',
    outline: 'none',
    transition: 'all 0.2s',
    backgroundColor: '#08080A',
    color: '#F4F4F5',
  },
  readOnlyInput: {
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    color: '#71717A',
    cursor: 'default',
    borderColor: 'rgba(255, 255, 255, 0.05)',
  },
  helpText: {
    fontSize: '0.75rem',
    color: '#52525B',
    marginTop: '0.25rem',
  },
  badge: {
    padding: '0.25rem 0.75rem',
    borderRadius: '6px',
    fontSize: '0.75rem',
    fontWeight: '700',
    textTransform: 'uppercase',
    backgroundColor: 'rgba(99, 102, 241, 0.1)',
    color: '#818cf8',
    border: '1px solid rgba(99, 102, 241, 0.2)',
  },
  statusBadge: {
    padding: '0.375rem 1rem',
    borderRadius: '9999px',
    fontSize: '0.75rem',
    fontWeight: '800',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  footer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-start',
    gap: '1rem',
    paddingTop: '1rem',
  },
  saveButton: {
    padding: '0.875rem 2.5rem',
    backgroundColor: '#6366f1',
    color: 'white',
    border: 'none',
    borderRadius: '12px',
    fontSize: '1rem',
    fontWeight: '700',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
  },
  disabledButton: {
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    color: '#52525B',
    cursor: 'not-allowed',
    boxShadow: 'none',
  },
  footerNote: {
    fontSize: '0.875rem',
    color: '#f87171',
    fontWeight: '500',
  }
};

export default SettingsPage;
