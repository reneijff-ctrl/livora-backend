import React, { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import creatorService from '../api/creatorService';
import SEO from '../components/SEO';
import DashboardSkeleton from '../components/DashboardSkeleton';
import CreatorSidebar from '../components/CreatorSidebar';
import { useAuth } from '../auth/useAuth';
import SafeAvatar from '../components/ui/SafeAvatar';

const CreatorProfileEditPage: React.FC = () => {
  const { user, authLoading } = useAuth();
  const navigate = useNavigate();
  const [displayName, setDisplayName] = useState('');
  const [bio, setBio] = useState('');
  const [avatarUrl, setAvatarUrl] = useState<string | null>(null);
  const [creatorId, setCreatorId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [isUploading, setIsUploading] = useState(false);
  const [success, setSuccess] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!user || authLoading) {
      return;
    }

    const fetchProfile = async () => {
      try {
        setLoading(true);
        const profile = await creatorService.getMyProfile();
        setDisplayName(profile.displayName || '');
        setBio(profile.bio || '');
        setAvatarUrl(profile.avatarUrl || null);
        setCreatorId(profile.creatorId || null);
        setError(null);
      } catch (err: any) {
        console.error('Failed to load creator profile:', err);
        if (err.response?.status === 403) {
          setError('You do not have permission to view this profile');
        } else {
          setError('Failed to load profile. Please try again later.');
        }
      } finally {
        setLoading(false);
      }
    };

    fetchProfile();
  }, [user, authLoading]);

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    try {
      setIsUploading(true);
      setError(null);
      const updatedProfile = await creatorService.uploadCreatorImage(file, 'PROFILE');
      setAvatarUrl(updatedProfile.avatarUrl || null);
      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000); // Keep success visible for a bit
    } catch (err: any) {
      console.error('Failed to upload avatar:', err);
      setError(err.response?.data?.message || 'Failed to upload avatar image. Please try again.');
    } finally {
      setIsUploading(false);
    }
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      setSaving(true);
      setError(null);
      setSuccess(false);
      await creatorService.updateMyProfile({ 
        displayName, 
        bio,
        profileImageUrl: avatarUrl || undefined 
      });
      setSuccess(true);
      setTimeout(() => setSuccess(false), 5000);
    } catch (err: any) {
      console.error('Failed to update profile:', err);
      if (err.response?.status === 403) {
        setError('You do not have permission to update this profile.');
      } else if (err.response?.data?.message) {
        setError(err.response.data.message);
      } else {
        setError('Failed to update profile. Please try again.');
      }
    } finally {
      setSaving(false);
    }
  };

  if (authLoading || loading) {
    return <DashboardSkeleton title="Creator Profile" />;
  }

  return (
    <div style={styles.layout}>
      <SEO title="Creator Profile" />
      <CreatorSidebar />
      
      <main style={styles.main}>
        <header style={styles.header}>
          <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start' }}>
            <div>
              <h1 style={styles.title}>Creator Profile</h1>
              <p style={styles.subtitle}>Manage your public identity</p>
            </div>
            {user && (
              <button 
                onClick={() => navigate(`/creators/${creatorId || user.id}`)}
                style={styles.viewProfileButton}
              >
                View Public Profile
              </button>
            )}
          </div>
        </header>

        {error && <div style={styles.errorBanner}>{error}</div>}
        {success && <div style={styles.successBanner}>Profile updated successfully!</div>}
        
        <div style={styles.infoBanner}>
          ℹ️ You can update your Profile Picture, Display Name, and Bio.
        </div>

        <div style={styles.card}>
          <form onSubmit={handleSubmit} style={styles.form}>
            <div style={styles.field}>
              <label style={styles.label}>Profile Picture</label>
              <div style={styles.avatarSection}>
                <SafeAvatar 
                  src={avatarUrl} 
                  name={displayName} 
                  size={80} 
                />
                <div style={styles.uploadControls}>
                  <input 
                    type="file" 
                    id="avatar-upload" 
                    accept="image/*" 
                    style={{ display: 'none' }} 
                    onChange={handleImageUpload}
                  />
                  <label htmlFor="avatar-upload" style={styles.uploadButton}>
                    {isUploading ? 'Uploading...' : 'Change Photo'}
                  </label>
                  <p style={styles.fieldHelp}>JPG or PNG. Max 2MB recommended.</p>
                </div>
              </div>
            </div>

            <div style={styles.field}>
              <label style={styles.label}>Display Name</label>
              <input 
                type="text"
                value={displayName}
                onChange={(e) => setDisplayName(e.target.value)}
                style={styles.input}
                placeholder="Enter your display name"
                required
                minLength={2}
                maxLength={50}
              />
              <p style={styles.fieldHelp}>This name is shown to your audience.</p>
            </div>

            <div style={styles.field}>
              <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
                <label style={styles.label}>Bio</label>
                <span style={{
                  fontSize: '0.75rem',
                  color: bio.length > 300 ? '#dc2626' : '#6b7280'
                }}>
                  {bio.length}/300
                </span>
              </div>
              <textarea 
                value={bio}
                onChange={(e) => setBio(e.target.value)}
                style={styles.textarea}
                placeholder="Tell your audience about yourself"
                maxLength={300}
              />
              <p style={styles.fieldHelp}>Your public biography (Max 300 characters).</p>
            </div>

            <div style={styles.formFooter}>
              <button 
                type="submit" 
                style={{
                  ...styles.saveButton,
                  ...(saving ? styles.disabledButton : {})
                }}
                disabled={saving}
              >
                {saving ? 'Saving...' : 'Save Changes'}
              </button>
            </div>
          </form>
        </div>
      </main>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  layout: {
    display: 'flex',
    minHeight: 'calc(100vh - 64px)',
    backgroundColor: '#08080A',
    color: '#F4F4F5',
    fontFamily: 'system-ui, -apple-system, sans-serif',
  },
  main: {
    flex: 1,
    padding: '2rem',
    overflowY: 'auto',
    maxWidth: '800px',
  },
  header: {
    marginBottom: '2rem',
  },
  title: {
    fontSize: '1.875rem',
    fontWeight: '800',
    color: '#F4F4F5',
    margin: '0 0 0.5rem 0',
    letterSpacing: '-0.02em',
  },
  subtitle: {
    fontSize: '1rem',
    color: '#71717A',
    margin: 0,
  },
  card: {
    backgroundColor: '#0F0F14',
    borderRadius: '24px',
    padding: '2rem',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
  },
  label: {
    fontSize: '0.875rem',
    fontWeight: '600',
    color: '#A1A1AA',
  },
  input: {
    padding: '0.75rem 1rem',
    borderRadius: '10px',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    fontSize: '1rem',
    outline: 'none',
    transition: 'all 0.2s',
    fontFamily: 'inherit',
    backgroundColor: '#08080A',
    color: '#F4F4F5',
  },
  textarea: {
    padding: '0.75rem 1rem',
    borderRadius: '10px',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    fontSize: '1rem',
    outline: 'none',
    transition: 'all 0.2s',
    fontFamily: 'inherit',
    minHeight: '120px',
    resize: 'vertical',
    backgroundColor: '#08080A',
    color: '#F4F4F5',
  },
  fieldHelp: {
    fontSize: '0.75rem',
    color: '#52525B',
    margin: 0,
  },
  errorBanner: {
    padding: '1rem',
    backgroundColor: '#fef2f2',
    color: '#dc2626',
    borderRadius: '8px',
    border: '1px solid #fee2e2',
    marginBottom: '1.5rem',
    fontSize: '0.875rem',
  },
  successBanner: {
    padding: '1rem',
    backgroundColor: '#f0fdf4',
    color: '#166534',
    borderRadius: '8px',
    border: '1px solid #dcfce7',
    marginBottom: '1.5rem',
    fontSize: '0.875rem',
  },
  infoBanner: {
    padding: '1rem',
    backgroundColor: '#eff6ff',
    color: '#1e40af',
    borderRadius: '8px',
    border: '1px solid #dbeafe',
    marginBottom: '1.5rem',
    fontSize: '0.875rem',
  },
  formFooter: {
    marginTop: '1rem',
    display: 'flex',
    justifyContent: 'flex-end',
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
  avatarSection: {
    display: 'flex',
    alignItems: 'center',
    gap: '1.5rem',
    padding: '0.5rem 0',
  },
  uploadControls: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.25rem',
  },
  uploadButton: {
    padding: '0.5rem 1.25rem',
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    color: '#F4F4F5',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '10px',
    fontSize: '0.875rem',
    fontWeight: '600',
    cursor: 'pointer',
    textAlign: 'center',
    display: 'inline-block',
    transition: 'all 0.2s ease',
  },
  viewProfileButton: {
    padding: '0.625rem 1.25rem',
    backgroundColor: 'transparent',
    color: '#818cf8',
    border: '1px solid rgba(129, 140, 248, 0.3)',
    borderRadius: '10px',
    fontSize: '0.875rem',
    fontWeight: '600',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
  },
  disabledButton: {
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    color: '#52525B',
    borderColor: 'rgba(255, 255, 255, 0.05)',
    cursor: 'not-allowed',
  },
};

export default CreatorProfileEditPage;
