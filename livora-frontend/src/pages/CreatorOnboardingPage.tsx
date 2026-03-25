import React, { useState, useEffect } from 'react';
import { useNavigate, Navigate } from 'react-router-dom';
import creatorService, { CreatorApplicationResponse } from '../api/creatorService';
import { useAuth } from '../auth/useAuth';
import DashboardSkeleton from '../components/DashboardSkeleton';
import SEO from '../components/SEO';
import SafeAvatar from '@/components/ui/SafeAvatar';
import ImageWithFallback from '@/components/ImageWithFallback';
import { getDashboardRouteByRole } from '../store/authStore';
import { showToast } from '../components/Toast';
import { gradients } from '../utils/theme';
import { ProfileStatus } from '../types';

const CreatorOnboardingPage: React.FC = () => {
  const { user, authLoading } = useAuth();
  const navigate = useNavigate();
  const [loading, setLoading] = useState(false);
  const [application, setApplication] = useState<CreatorApplicationResponse | null>(null);
  const [termsAccepted, setTermsAccepted] = useState(false);
  const [ageVerified, setAgeVerified] = useState(false);
  
  // Profile form state
  const [displayName, setDisplayName] = useState('');
  const [bio, setBio] = useState('');
  const [avatarUrl, setAvatarUrl] = useState('');
  const [bannerUrl, setBannerUrl] = useState('');
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const fetchStatus = async () => {
      if (user?.role !== 'CREATOR' && user?.role !== 'ADMIN') {
        try {
          const status = await creatorService.getApplicationStatus();
          setApplication(status);
        } catch (err) {
          console.error('Failed to fetch application status', err);
        }
      }
    };
    fetchStatus();

    if (user?.creatorProfile) {
      setDisplayName(user.creatorProfile.displayName || '');
      setBio(user.creatorProfile.bio || '');
      setAvatarUrl(user.creatorProfile.avatarUrl || '');
      setBannerUrl(user.creatorProfile.bannerUrl || '');
    }
  }, [user]);

  const handleStartOnboarding = async () => {
    try {
      setLoading(true);
      const app = await creatorService.startApplication();
      setApplication(app);
    } catch (err: any) {
      console.error('Failed to onboard as creator', err);
      setError(err.response?.data?.message || 'Failed to start onboarding. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleSubmitApplication = async () => {
    if (!termsAccepted || !ageVerified) {
      setError('Please accept terms and verify your age.');
      return;
    }

    try {
      setLoading(true);
      const app = await creatorService.submitApplication(termsAccepted, ageVerified);
      setApplication(app);
      showToast('Application submitted successfully!', 'success');
    } catch (err: any) {
      console.error('Failed to submit application', err);
      setError(err.response?.data?.message || 'Failed to submit application. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  const handleImageUpload = async (e: React.ChangeEvent<HTMLInputElement>, type: 'PROFILE' | 'BANNER') => {
    const file = e.target.files?.[0];
    if (!file) return;

    if (!['image/jpeg', 'image/jpg', 'image/png', 'image/webp'].includes(file.type)) {
      setError('Invalid file type. Please upload a JPG, PNG, or WEBP image.');
      return;
    }
    if (file.size > 5 * 1024 * 1024) {
      setError('File too large. Maximum size is 5MB.');
      return;
    }

    try {
      setIsUploading(true);
      setError(null);
      const updatedProfile = await creatorService.uploadCreatorImage(file, type);
      if (type === 'PROFILE') {
        setAvatarUrl(updatedProfile.avatarUrl || '');
      } else {
        setBannerUrl(updatedProfile.bannerUrl || '');
      }
    } catch (err: any) {
      console.error(`Failed to upload ${type.toLowerCase()} image:`, err);
      setError(err.response?.data?.message || `Failed to upload ${type.toLowerCase()} image.`);
    } finally {
      setIsUploading(false);
    }
  };

  const handleSubmitProfile = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!displayName || !bio || !avatarUrl) {
      setError('Please fill in all required fields and upload an avatar.');
      return;
    }

    try {
      setLoading(true);
      setError(null);
      
      // Update profile data
      await creatorService.updateMyProfile({ displayName, bio });
      
      // Complete onboarding (DRAFT -> PENDING)
      // Note: creatorService.completeOnboarding already updates the local state
      await creatorService.completeOnboarding();
      
      showToast('Profile setup complete! Your profile is now pending approval.', 'success');
      navigate('/creator/dashboard');
    } catch (err: any) {
      console.error('Failed to complete onboarding:', err);
      setError(err.response?.data?.message || 'Failed to complete onboarding. Please try again.');
    } finally {
      setLoading(false);
    }
  };

  if (authLoading) {
    return <DashboardSkeleton title="Creator Onboarding" />;
  }

  // If not a creator, show the initial "Become a Creator" step
  if (user?.role !== 'CREATOR' && user?.role !== 'ADMIN') {
    return (
      <div style={styles.container}>
        <SEO title="Become a Creator" />
        <div style={styles.card}>
          {!application && (
            <>
              <div style={styles.header}>
                <span style={styles.icon}>🚀</span>
                <h1 style={styles.title}>Welcome to the Creator Journey</h1>
              </div>
              <div style={styles.content}>
                <p style={styles.description}>Ready to share your talent? By becoming a creator, you'll gain access to:</p>
                <ul style={styles.list}>
                  <li>Live streaming in HD</li>
                  <li>Exclusive VOD content</li>
                  <li>Token earnings from fans</li>
                  <li>Growth analytics</li>
                </ul>
              </div>
              <button 
                onClick={handleStartOnboarding} 
                disabled={loading}
                style={{...styles.button, ...(loading ? styles.buttonDisabled : {})}}
              >
                {loading ? 'Setting up...' : 'Become a Creator Now'}
              </button>
            </>
          )}

          {application?.status === 'PENDING' && (
            <>
              <div style={styles.header}>
                <h1 style={styles.title}>Terms & Verification</h1>
                <p style={styles.subtitle}>Last step before submitting your application</p>
              </div>
              
              {error && <div style={styles.errorBanner}>{error}</div>}

              <div style={styles.form}>
                <div style={{...styles.field, flexDirection: 'row', alignItems: 'center', gap: '1rem'}}>
                  <input 
                    type="checkbox" 
                    id="terms" 
                    checked={termsAccepted} 
                    onChange={(e) => setTermsAccepted(e.target.checked)}
                  />
                  <label htmlFor="terms" style={styles.label}>I accept the creator terms and conditions</label>
                </div>
                
                <div style={{...styles.field, flexDirection: 'row', alignItems: 'center', gap: '1rem'}}>
                  <input 
                    type="checkbox" 
                    id="age" 
                    checked={ageVerified} 
                    onChange={(e) => setAgeVerified(e.target.checked)}
                  />
                  <label htmlFor="age" style={styles.label}>I confirm that I am 18 years or older</label>
                </div>

                <button 
                  onClick={handleSubmitApplication} 
                  disabled={loading || !termsAccepted || !ageVerified}
                  style={{...styles.button, ...(loading || !termsAccepted || !ageVerified ? styles.buttonDisabled : {})}}
                >
                  {loading ? 'Submitting...' : 'Submit Application'}
                </button>
              </div>
            </>
          )}

          {application?.status === 'UNDER_REVIEW' && (
            <>
              <div style={styles.header}>
                <span style={styles.icon}>⏳</span>
                <h1 style={styles.title}>Application Under Review</h1>
              </div>
              <div style={styles.content}>
                <p style={styles.description}>
                  Our team is reviewing your application. You will receive an email once it has been processed.
                </p>
              </div>
              <div style={{...styles.button, ...styles.buttonDisabled, textAlign: 'center'}}>
                Waiting for Approval
              </div>
            </>
          )}

          {application?.status === 'REJECTED' && (
            <>
              <div style={styles.header}>
                <span style={styles.icon}>❌</span>
                <h1 style={styles.title}>Application Rejected</h1>
              </div>
              <div style={styles.content}>
                <p style={styles.description}>
                  Unfortunately, your application was not approved at this time.
                </p>
                {application.reviewNotes && (
                  <div style={styles.rejectionNotes}>
                    <strong>Notes from reviewers:</strong>
                    <p style={{marginTop: '0.5rem', color: '#EF4444'}}>{application.reviewNotes}</p>
                  </div>
                )}
              </div>
              <button 
                onClick={handleStartOnboarding} 
                disabled={loading}
                style={styles.button}
              >
                Re-apply Now
              </button>
            </>
          )}

          {application?.status === 'APPROVED' && (
            <>
              <div style={styles.header}>
                <span style={styles.icon}>✅</span>
                <h1 style={styles.title}>Application Approved!</h1>
              </div>
              <div style={styles.content}>
                <p style={styles.description}>
                  Congratulations! Your application has been approved. Please refresh to continue.
                </p>
              </div>
              <button 
                onClick={() => window.location.reload()} 
                style={styles.button}
              >
                Refresh Page
              </button>
            </>
          )}
        </div>
      </div>
    );
  }

  // If they are already ACTIVE or PENDING, they shouldn't be here (guard in router/ProtectedRoute should also handle this)
  if (user?.creatorProfile?.status !== ProfileStatus.DRAFT) {
    return <Navigate to={getDashboardRouteByRole(user.role)} replace />;
  }

  // Profile Setup Step (DRAFT status)
  return (
    <div style={styles.container}>
      <SEO title="Setup Your Creator Profile" />
      <div style={{...styles.card, maxWidth: '800px'}}>
        <div style={styles.header}>
          <h1 style={styles.title}>Setup Your Creator Profile</h1>
          <p style={styles.subtitle}>Complete these details to launch your channel</p>
        </div>

        {error && <div style={styles.errorBanner}>{error}</div>}

        <form onSubmit={handleSubmitProfile} style={styles.form}>
          <div style={styles.grid}>
            <div style={styles.field}>
              <label style={styles.label}>Profile Image *</label>
              <div style={styles.imageUploadContainer}>
                <SafeAvatar 
                  src={avatarUrl} 
                  name={displayName || user.email} 
                  size={80} 
                />
                <input 
                  type="file" 
                  id="avatar-upload" 
                  accept="image/*" 
                  style={{display: 'none'}} 
                  onChange={(e) => handleImageUpload(e, 'PROFILE')}
                />
                <label htmlFor="avatar-upload" style={styles.uploadLink}>
                  {isUploading ? 'Uploading...' : 'Upload Avatar'}
                </label>
              </div>
            </div>

            <div style={styles.field}>
              <label style={styles.label}>Banner Image (Optional)</label>
              <div style={styles.bannerUploadContainer}>
                <ImageWithFallback 
                  src={bannerUrl} 
                  alt="Banner" 
                  style={styles.bannerPreview}
                  fallback={<div style={styles.bannerPlaceholder} />}
                />
                <input 
                  type="file" 
                  id="banner-upload" 
                  accept="image/*" 
                  style={{display: 'none'}} 
                  onChange={(e) => handleImageUpload(e, 'BANNER')}
                />
                <label htmlFor="banner-upload" style={styles.uploadLink}>
                  {isUploading ? 'Uploading...' : 'Upload Banner'}
                </label>
              </div>
            </div>
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Display Name *</label>
            <input 
              type="text"
              value={displayName}
              onChange={(e) => setDisplayName(e.target.value)}
              style={styles.input}
              placeholder="e.g. AwesomeStreamer"
              required
            />
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Bio *</label>
            <textarea 
              value={bio}
              onChange={(e) => setBio(e.target.value)}
              style={styles.textarea}
              placeholder="Tell us about your content..."
              required
              rows={4}
            />
          </div>

          <button 
            type="submit" 
            disabled={loading || isUploading}
            style={{...styles.button, ...(loading || isUploading ? styles.buttonDisabled : {})}}
          >
            {loading ? 'Finalizing...' : 'Complete Setup & Submit for Review'}
          </button>
        </form>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    padding: '4rem 2rem',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    minHeight: 'calc(100vh - 64px)',
    backgroundColor: 'transparent',
    fontFamily: 'system-ui, -apple-system, sans-serif',
    color: '#F4F4F5',
  },
  card: {
    backgroundColor: '#0F0F14',
    borderRadius: '24px',
    padding: '3rem',
    maxWidth: '600px',
    width: '100%',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
    border: '1px solid rgba(255, 255, 255, 0.05)',
  },
  header: {
    textAlign: 'center',
    marginBottom: '2.5rem',
  },
  title: {
    fontSize: '2.25rem',
    fontWeight: '800',
    color: '#F4F4F5',
    margin: '0 0 0.5rem 0',
    letterSpacing: '-0.025em',
  },
  subtitle: {
    color: '#71717A',
    fontSize: '1.125rem',
  },
  icon: {
    fontSize: '3.5rem',
    display: 'block',
    marginBottom: '1rem',
  },
  content: {
    textAlign: 'left',
    marginBottom: '2rem',
  },
  description: {
    fontSize: '1.125rem',
    color: '#A1A1AA',
    marginBottom: '1rem',
  },
  list: {
    paddingLeft: '1.5rem',
    color: '#71717A',
    lineHeight: '1.8',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: '1fr 2fr',
    gap: '2rem',
    marginBottom: '1rem',
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.5rem',
    textAlign: 'left',
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
    backgroundColor: '#08080A',
    color: '#F4F4F5',
    outline: 'none',
  },
  textarea: {
    padding: '0.75rem 1rem',
    borderRadius: '10px',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    fontSize: '1rem',
    resize: 'vertical',
    backgroundColor: '#08080A',
    color: '#F4F4F5',
    outline: 'none',
  },
  imageUploadContainer: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    gap: '0.75rem',
  },
  bannerUploadContainer: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
  },
  avatarPreview: {
    width: '100px',
    height: '100px',
    borderRadius: '50%',
    objectFit: 'cover',
    border: '4px solid #f3f4f6',
  },
  avatarFallback: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    background: gradients.primary,
    color: 'white',
    fontSize: '2.5rem',
    fontWeight: 'bold',
  },
  bannerPreview: {
    width: '100%',
    height: '100px',
    borderRadius: '12px',
    objectFit: 'cover',
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    border: '1px solid rgba(255, 255, 255, 0.1)',
  },
  bannerPlaceholder: {
    width: '100%',
    height: '100px',
    borderRadius: '8px',
    background: gradients.primary,
    opacity: 0.1,
  },
  uploadLink: {
    fontSize: '0.875rem',
    color: '#6366f1',
    fontWeight: '600',
    cursor: 'pointer',
    textDecoration: 'underline',
  },
  button: {
    width: '100%',
    padding: '1rem',
    backgroundColor: '#6366f1',
    color: 'white',
    border: 'none',
    borderRadius: '12px',
    fontSize: '1.125rem',
    fontWeight: '800',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
  },
  buttonDisabled: {
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    color: '#52525B',
    cursor: 'not-allowed',
    boxShadow: 'none',
  },
  errorBanner: {
    padding: '1rem',
    backgroundColor: '#fef2f2',
    color: '#b91c1c',
    borderRadius: '8px',
    marginBottom: '1.5rem',
    fontSize: '0.875rem',
    textAlign: 'left',
  },
  rejectionNotes: {
    marginTop: '1.5rem',
    padding: '1rem',
    backgroundColor: 'rgba(239, 68, 68, 0.1)',
    borderRadius: '12px',
    border: '1px solid rgba(239, 68, 68, 0.2)',
    fontSize: '0.875rem',
    color: '#A1A1AA',
  },
};

export default CreatorOnboardingPage;
