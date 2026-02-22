import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import creatorService from '../api/creatorService';
import SEO from '../components/SEO';
import { showToast } from '../components/Toast';
import CreatorSidebar from '../components/CreatorSidebar';
import { useAuth } from '../auth/useAuth';

const CreatorUploadPage: React.FC = () => {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ADMIN';
  const navigate = useNavigate();
  const [isUploading, setIsUploading] = useState(false);
  const [formData, setFormData] = useState({
    title: '',
    description: '',
    thumbnailUrl: '',
    mediaUrl: '',
    accessLevel: 'FREE' as const
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!isAdmin) return;
    setIsUploading(true);
    try {
      await creatorService.createContent(formData);
      showToast('Content uploaded successfully!', 'success');
      navigate('/creator/dashboard');
    } catch (error) {
      showToast('Failed to upload content.', 'error');
    } finally {
      setIsUploading(false);
    }
  };

  return (
    <div style={styles.layout}>
      <SEO title="Upload Content" />
      <CreatorSidebar />
      <main style={styles.main}>
        <h1 style={styles.title}>Upload New Content</h1>
        
        {!isAdmin && (
          <div style={styles.infoBanner}>
            ℹ️ Content uploading is currently in read-only mode for creators.
          </div>
        )}

        <form onSubmit={handleSubmit} style={styles.form}>
          <div>
            <label style={styles.label}>Title</label>
            <input 
              type="text" 
              required 
              value={formData.title} 
              onChange={e => setFormData({ ...formData, title: e.target.value })}
              style={{ ...styles.input, ...(!isAdmin ? styles.readOnlyInput : {}) }}
              readOnly={!isAdmin}
            />
          </div>
          <div>
            <label style={styles.label}>Description</label>
            <textarea 
              required 
              value={formData.description} 
              onChange={e => setFormData({ ...formData, description: e.target.value })}
              style={{ ...styles.input, minHeight: '100px', ...(!isAdmin ? styles.readOnlyInput : {}) }}
              readOnly={!isAdmin}
            />
          </div>
          <div>
            <label style={styles.label}>Thumbnail URL</label>
            <input 
              type="url" 
              required 
              value={formData.thumbnailUrl} 
              onChange={e => setFormData({ ...formData, thumbnailUrl: e.target.value })}
              style={{ ...styles.input, ...(!isAdmin ? styles.readOnlyInput : {}) }}
              readOnly={!isAdmin}
            />
          </div>
          <div>
            <label style={styles.label}>Media URL (Video)</label>
            <input 
              type="url" 
              required 
              value={formData.mediaUrl} 
              onChange={e => setFormData({ ...formData, mediaUrl: e.target.value })}
              style={{ ...styles.input, ...(!isAdmin ? styles.readOnlyInput : {}) }}
              readOnly={!isAdmin}
            />
          </div>
          <div>
            <label style={styles.label}>Access Level</label>
            <select 
              value={formData.accessLevel} 
              onChange={e => setFormData({ ...formData, accessLevel: e.target.value as any })}
              style={{ ...styles.input, ...(!isAdmin ? styles.readOnlyInput : {}) }}
              disabled={!isAdmin}
            >
              <option value="FREE">Free</option>
              <option value="PREMIUM">Premium</option>
              <option value="CREATOR">Creator Only</option>
            </select>
          </div>
          
          {isAdmin && (
            <button 
              type="submit" 
              disabled={isUploading}
              style={{ 
                ...styles.submitButton,
                cursor: isUploading ? 'not-allowed' : 'pointer',
                opacity: isUploading ? 0.7 : 1
              }}
            >
              {isUploading ? 'Uploading...' : 'Publish Content'}
            </button>
          )}
        </form>
      </main>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  layout: {
    display: 'flex',
    minHeight: 'calc(100vh - 64px)',
    backgroundColor: '#08080A',
    fontFamily: 'system-ui, -apple-system, sans-serif',
    color: '#F4F4F5',
  },
  main: {
    flex: 1,
    padding: '2rem',
    maxWidth: '800px',
    margin: '0 auto',
  },
  title: {
    fontSize: '1.875rem',
    fontWeight: '800',
    color: '#F4F4F5',
    marginBottom: '2rem',
    letterSpacing: '-0.02em',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
    backgroundColor: '#0F0F14',
    padding: '2rem',
    borderRadius: '24px',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
  },
  label: {
    display: 'block',
    marginBottom: '0.5rem',
    fontWeight: '600',
    color: '#A1A1AA',
    fontSize: '0.875rem',
  },
  input: {
    width: '100%',
    padding: '0.75rem',
    borderRadius: '10px',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    fontSize: '1rem',
    outline: 'none',
    backgroundColor: '#08080A',
    color: '#F4F4F5',
    boxSizing: 'border-box',
  },
  readOnlyInput: {
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    cursor: 'default',
    color: '#71717A',
    borderColor: 'rgba(255, 255, 255, 0.05)',
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
  submitButton: {
    backgroundColor: '#6366f1',
    color: 'white',
    padding: '1rem',
    borderRadius: '12px',
    border: 'none',
    fontWeight: '800',
    fontSize: '1rem',
    marginTop: '1rem',
    transition: 'all 0.2s ease',
    boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
  },
};

export default CreatorUploadPage;
