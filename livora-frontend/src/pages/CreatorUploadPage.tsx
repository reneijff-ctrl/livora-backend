import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import axios from 'axios';
import SEO from '../components/SEO';
import { showToast } from '../components/Toast';
import CreatorSidebar from '../components/CreatorSidebar';
import { useAuth } from '../auth/useAuth';

const CreatorUploadPage: React.FC = () => {
  const { user } = useAuth();
  const canUpload = user?.role === 'CREATOR' || user?.role === 'ADMIN';
  const navigate = useNavigate();
  const [isUploading, setIsUploading] = useState(false);
  
  const [title, setTitle] = useState('');
  const [description, setDescription] = useState('');
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [accessLevel, setAccessLevel] = useState<'FREE' | 'PREMIUM' | 'CREATOR'>('FREE');
  const [unlockPriceTokens, setUnlockPriceTokens] = useState<number>(100);
  const [selectedType, setSelectedType] = useState<'PHOTO' | 'VIDEO' | 'CLIP'>('PHOTO');

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canUpload || !selectedFile) {
      if (!selectedFile) showToast('Please select a file to upload.', 'error');
      return;
    }
    
    setIsUploading(true);
    try {
      const formData = new FormData();
      formData.append("file", selectedFile);
      formData.append("title", title);
      formData.append("description", description);
      formData.append("accessLevel", accessLevel);
      formData.append("type", selectedType);
      formData.append("unlockPriceTokens", unlockPriceTokens.toString());

      await axios.post(
        "http://localhost:8080/api/creators/content/upload",
        formData,
        {
          headers: {
            Authorization: `Bearer ${localStorage.getItem("token")}`
          }
        }
      );

      showToast('Content uploaded successfully!', 'success');
      navigate('/creator/dashboard');
    } catch (error: any) {
      console.error('Failed to upload content:', error);
      const message = error.response?.data?.message || 'Failed to upload content.';
      showToast(message, 'error');
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
        
        {!canUpload && (
          <div style={styles.infoBanner}>
            ℹ️ Upload is disabled due to account restrictions.
          </div>
        )}

        <form onSubmit={handleSubmit} style={styles.form}>
          <div>
            <label style={styles.label}>Title</label>
            <input 
              type="text" 
              required 
              value={title} 
              onChange={e => setTitle(e.target.value)}
              style={{ ...styles.input, ...(!canUpload ? styles.readOnlyInput : {}) }}
              readOnly={!canUpload}
            />
          </div>
          <div>
            <label style={styles.label}>Description</label>
            <textarea 
              required 
              value={description} 
              onChange={e => setDescription(e.target.value)}
              style={{ ...styles.input, minHeight: '100px', ...(!canUpload ? styles.readOnlyInput : {}) }}
              readOnly={!canUpload}
            />
          </div>

          <div>
            <label style={styles.label}>Content File</label>
            <input 
              type="file" 
              accept="image/*,video/*"
              required={canUpload}
              onChange={(e) => {
                const selected = e.target.files?.[0];
                if (!selected) return;

                setSelectedFile(selected);

                const url = URL.createObjectURL(selected);
                setPreviewUrl(url);

                if (selected.type.startsWith("video")) {
                  setSelectedType("VIDEO");
                } else {
                  setSelectedType("PHOTO");
                }
              }}
              style={{ ...styles.input, ...(!canUpload ? styles.readOnlyInput : {}) }}
              disabled={!canUpload}
            />
          </div>

          {previewUrl && (
            <div style={styles.previewContainer}>
              <label style={styles.label}>Preview</label>
              {selectedType === "VIDEO" ? 
                <video src={previewUrl} controls style={{ width: "100%", borderRadius: '10px' }} /> :
                <img src={previewUrl} alt="preview" style={{ width: "100%", borderRadius: '10px' }} />
              }
            </div>
          )}

          <div>
            <label style={styles.label}>Content Type</label>
            <select
              value={selectedType}
              onChange={(e) => setSelectedType(e.target.value as any)}
              style={{ ...styles.input, ...(!canUpload ? styles.readOnlyInput : {}) }}
              disabled={!canUpload}
            >
              <option value="PHOTO">Photo</option>
              <option value="VIDEO">Video</option>
              <option value="CLIP">Clip</option>
            </select>
          </div>

          <div>
            <label style={styles.label}>Access Level</label>
            <select 
              value={accessLevel} 
              onChange={e => setAccessLevel(e.target.value as any)}
              style={{ ...styles.input, ...(!canUpload ? styles.readOnlyInput : {}) }}
              disabled={!canUpload}
            >
              <option value="FREE">Free</option>
              <option value="PREMIUM">Premium</option>
              <option value="CREATOR">Creator Only</option>
            </select>
          </div>

          {accessLevel === "PREMIUM" && (
            <div>
              <label style={styles.label}>Unlock Price (Tokens)</label>
              <input
                type="number"
                min={10}
                max={5000}
                value={unlockPriceTokens}
                onChange={(e) => setUnlockPriceTokens(Number(e.target.value))}
                placeholder="Unlock price in tokens"
                style={{ ...styles.input, ...(!canUpload ? styles.readOnlyInput : {}) }}
                readOnly={!canUpload}
              />
            </div>
          )}
          
          {canUpload && (
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
  previewContainer: {
    marginTop: '0.5rem',
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
