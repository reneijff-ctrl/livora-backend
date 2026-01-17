import React, { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import creatorService from '../api/creatorService';
import SEO from '../components/SEO';
import { showToast } from '../components/Toast';

const CreatorUploadPage: React.FC = () => {
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
    <div style={{ padding: '2rem', maxWidth: '600px', margin: '0 auto' }}>
      <SEO title="Upload Content" />
      <h1>Upload New Content</h1>
      <form onSubmit={handleSubmit} style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem', marginTop: '2rem' }}>
        <div>
          <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 'bold' }}>Title</label>
          <input 
            type="text" 
            required 
            value={formData.title} 
            onChange={e => setFormData({ ...formData, title: e.target.value })}
            style={{ width: '100%', padding: '0.8rem', borderRadius: '4px', border: '1px solid #ddd' }}
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 'bold' }}>Description</label>
          <textarea 
            required 
            value={formData.description} 
            onChange={e => setFormData({ ...formData, description: e.target.value })}
            style={{ width: '100%', padding: '0.8rem', borderRadius: '4px', border: '1px solid #ddd', minHeight: '100px' }}
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 'bold' }}>Thumbnail URL</label>
          <input 
            type="url" 
            required 
            value={formData.thumbnailUrl} 
            onChange={e => setFormData({ ...formData, thumbnailUrl: e.target.value })}
            style={{ width: '100%', padding: '0.8rem', borderRadius: '4px', border: '1px solid #ddd' }}
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 'bold' }}>Media URL (Video)</label>
          <input 
            type="url" 
            required 
            value={formData.mediaUrl} 
            onChange={e => setFormData({ ...formData, mediaUrl: e.target.value })}
            style={{ width: '100%', padding: '0.8rem', borderRadius: '4px', border: '1px solid #ddd' }}
          />
        </div>
        <div>
          <label style={{ display: 'block', marginBottom: '0.5rem', fontWeight: 'bold' }}>Access Level</label>
          <select 
            value={formData.accessLevel} 
            onChange={e => setFormData({ ...formData, accessLevel: e.target.value as any })}
            style={{ width: '100%', padding: '0.8rem', borderRadius: '4px', border: '1px solid #ddd' }}
          >
            <option value="FREE">Free</option>
            <option value="PREMIUM">Premium</option>
            <option value="CREATOR">Creator Only</option>
          </select>
        </div>
        <button 
          type="submit" 
          disabled={isUploading}
          style={{ 
            backgroundColor: '#6772e5', 
            color: 'white', 
            padding: '12px', 
            borderRadius: '4px', 
            border: 'none', 
            fontWeight: 'bold',
            cursor: isUploading ? 'not-allowed' : 'pointer',
            opacity: isUploading ? 0.7 : 1
          }}
        >
          {isUploading ? 'Uploading...' : 'Publish Content'}
        </button>
      </form>
    </div>
  );
};

export default CreatorUploadPage;
