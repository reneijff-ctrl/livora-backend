import React, { useEffect, useState } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import contentService, { ContentItem } from '../api/contentService';
import Loader from '../components/Loader';
import SEO from '../components/SEO';
import { showToast } from '../components/Toast';
import TipButton from '../components/TipButton';

const ContentDetailPage: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [content, setContent] = useState<ContentItem | null>(null);
  const [mediaUrl, setMediaUrl] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const fetchContent = async () => {
      if (!id) return;
      try {
        const item = await contentService.getContentById(id);
        setContent(item);
        
        // Fetch media access
        const signedUrl = await contentService.getMediaAccess(id);
        setMediaUrl(signedUrl);
      } catch (error: any) {
        if (error.response?.status === 403) {
          showToast('Access denied. Please upgrade to view this content.', 'error');
          navigate('/pricing');
        } else {
          showToast('Failed to load content.', 'error');
          navigate('/');
        }
      } finally {
        setIsLoading(false);
      }
    };

    fetchContent();
  }, [id, navigate]);

  if (isLoading) return <Loader />;
  if (!content) return <div>Content not found.</div>;

  return (
    <div style={{ padding: '2rem', maxWidth: '1000px', margin: '0 auto' }}>
      <SEO title={content.title} description={content.description} />
      
      <button onClick={() => navigate(-1)} style={{ marginBottom: '1rem', cursor: 'pointer' }}>
        ← Back
      </button>

      <div style={{ backgroundColor: '#000', width: '100%', aspectRatio: '16/9', borderRadius: '8px', overflow: 'hidden', marginBottom: '2rem' }}>
        {mediaUrl ? (
          <video 
            src={mediaUrl} 
            controls 
            controlsList="nodownload"
            style={{ width: '100%', height: '100%' }}
            poster={content.thumbnailUrl}
          >
            Your browser does not support the video tag.
          </video>
        ) : (
          <div style={{ width: '100%', height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#fff' }}>
            Initializing player...
          </div>
        )}
      </div>

      <h1>{content.title}</h1>
      <div style={{ display: 'flex', gap: '1rem', alignItems: 'center', marginBottom: '1rem' }}>
        <span style={{ 
          fontSize: '0.85rem', 
          fontWeight: 'bold', 
          padding: '4px 12px', 
          borderRadius: '15px', 
          backgroundColor: '#f3f4f6' 
        }}>
          {content.accessLevel}
        </span>
        <span style={{ color: '#666' }}>By {content.creatorEmail}</span>
        <span style={{ color: '#999', fontSize: '0.85rem' }}>{content.createdAt ? new Date(content.createdAt).toLocaleDateString() : 'N/A'}</span>
        <div style={{ marginLeft: 'auto' }}>
           <TipButton userId={content.userId.toString()} creatorEmail={content.creatorEmail} />
        </div>
      </div>
      
      <div style={{ borderTop: '1px solid #eee', paddingTop: '1rem', lineHeight: '1.6' }}>
        <p>{content.description}</p>
      </div>
    </div>
  );
};

export default ContentDetailPage;
