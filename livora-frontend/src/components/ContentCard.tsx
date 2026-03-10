import React from 'react';
import { ContentItem } from '../api/contentService';
import { useAuth } from '../auth/useAuth';
import { Link } from 'react-router-dom';
import { safeRender } from '@/utils/safeRender';

import ImageWithFallback from '@/components/ImageWithFallback';

interface ContentCardProps {
  content: ContentItem;
}

const ContentCard: React.FC<ContentCardProps> = ({ content }) => {
  const { user, hasPremiumAccess } = useAuth();

  const isLocked = () => {
    if (content.accessLevel === 'FREE') return false;
    if (content.accessLevel === 'PREMIUM') return !hasPremiumAccess();
    if (content.accessLevel === 'CREATOR') return user?.role !== 'CREATOR' && user?.role !== 'ADMIN';
    return true;
  };

  const locked = isLocked();
  
  const getDisplayUrl = () => {
    if (content.type === 'VIDEO' || content.type === 'CLIP') {
      return content.thumbnailUrl;
    }
    return content.mediaUrl || content.thumbnailUrl;
  };

  return (
    <div style={{ 
      border: '1px solid #ddd', 
      borderRadius: '8px', 
      overflow: 'hidden', 
      backgroundColor: '#fff',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
      display: 'flex',
      flexDirection: 'column'
    }}>
      <div style={{ position: 'relative', width: '100%', paddingTop: '56.25%', backgroundColor: '#eee' }}>
        <ImageWithFallback 
          src={getDisplayUrl() || undefined} 
          alt={content.title}
          style={{ 
            position: 'absolute', 
            top: 0, 
            left: 0, 
            width: '100%', 
            height: '100%', 
            objectFit: 'cover',
            filter: locked ? 'blur(4px) grayscale(50%)' : 'none'
          }}
          fallback={
            <div style={{
              position: 'absolute', 
              top: 0, 
              left: 0, 
              width: '100%', 
              height: '100%', 
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              backgroundColor: '#2d2d39',
              color: '#666',
              fontSize: '0.875rem'
            }}>
              No Thumbnail
            </div>
          }
        />
        {locked && (
          <div style={{ 
            position: 'absolute', 
            top: 0, 
            left: 0, 
            width: '100%', 
            height: '100%', 
            display: 'flex', 
            alignItems: 'center', 
            justifyContent: 'center',
            backgroundColor: 'rgba(0,0,0,0.4)',
            color: '#fff',
            fontSize: '2rem'
          }}>
            🔒
          </div>
        )}
      </div>
      <div style={{ padding: '1rem', flexGrow: 1 }}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ 
            fontSize: '0.75rem', 
            fontWeight: 'bold', 
            padding: '2px 8px', 
            borderRadius: '10px', 
            backgroundColor: content.accessLevel === 'FREE' ? '#e2e8f0' : content.accessLevel === 'PREMIUM' ? '#fef3c7' : '#e0e7ff',
            color: content.accessLevel === 'FREE' ? '#475569' : content.accessLevel === 'PREMIUM' ? '#92400e' : '#3730a3'
          }}>
            {safeRender(content.accessLevel)}
          </span>
          <span style={{ fontSize: '0.75rem', color: '#666' }}>
            {safeRender(content.createdAt ? new Date(content.createdAt).toLocaleDateString() : 'N/A')}
          </span>
        </div>
        <h3 style={{ margin: '0.5rem 0', fontSize: '1.1rem' }}>{safeRender(content.title)}</h3>
        <p style={{ 
          fontSize: '0.9rem', 
          color: '#444', 
          margin: 0, 
          display: '-webkit-box', 
          WebkitLineClamp: 2, 
          WebkitBoxOrient: 'vertical', 
          overflow: 'hidden' 
        }}>
          {safeRender(content.description)}
        </p>
      </div>
      <div style={{ padding: '1rem', borderTop: '1px solid #eee' }}>
        {locked ? (
          <div style={{ textAlign: 'center' }}>
            {content.accessLevel === 'PREMIUM' ? (
              <Link 
                to="/pricing" 
                style={{ 
                  display: 'block',
                  backgroundColor: '#6772e5', 
                  color: '#fff', 
                  padding: '8px 16px', 
                  borderRadius: '4px', 
                  textDecoration: 'none',
                  fontWeight: 'bold'
                }}
              >
                Upgrade to View
              </Link>
            ) : (
              <p style={{ margin: 0, fontSize: '0.85rem', color: '#dc2626' }}>
                Creator Only Access
              </p>
            )}
          </div>
        ) : (
          <Link 
            to={`/content/${content.id}`}
            style={{ 
              display: 'block',
              textAlign: 'center',
              backgroundColor: '#10b981', 
              color: '#fff', 
              padding: '8px 16px', 
              borderRadius: '4px', 
              textDecoration: 'none',
              fontWeight: 'bold'
            }}
          >
            Watch Now
          </Link>
        )}
      </div>
    </div>
  );
};

export default ContentCard;
