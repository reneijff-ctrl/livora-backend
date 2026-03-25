import React, { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import creatorService from '../api/creatorService';
import { CreatorPost } from '../types';
import Loader from '../components/Loader';
import EmptyState from '../components/EmptyState';
import SafeAvatar from '@/components/ui/SafeAvatar';
import SEO from '../components/SEO';
import { useAuth } from '../auth/useAuth';

const FeedPage: React.FC = () => {
  const { isAuthenticated } = useAuth();
  const navigate = useNavigate();
  const [posts, setPosts] = useState<CreatorPost[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  useEffect(() => {
    fetchFeed();
  }, [page]);

  const fetchFeed = async () => {
    try {
      setLoading(true);
      const data = await creatorService.getFeed(page, 20);
      if (page === 0) {
        setPosts(data.content);
      } else {
        setPosts(prev => [...prev, ...data.content]);
      }
      setTotalPages(data.totalPages);
    } catch (error) {
      console.error('Failed to fetch feed:', error);
    } finally {
      setLoading(false);
    }
  };

  const loadMore = () => {
    if (page < totalPages - 1) {
      setPage(prev => prev + 1);
    }
  };

  const handleLikeToggle = async (post: CreatorPost) => {
    try {
      const isLiked = post.likedByMe;
      
      // Optimistic update
      setPosts(prev => prev.map(p => {
        if (p.id === post.id) {
          return {
            ...p,
            likedByMe: !isLiked,
            likeCount: (p.likeCount || 0) + (isLiked ? -1 : 1)
          };
        }
        return p;
      }));

      if (isLiked) {
        await creatorService.unlikePost(post.id);
      } else {
        await creatorService.likePost(post.id);
      }
    } catch (error) {
      console.error('Failed to toggle like:', error);
      // Optional: Refresh feed or revert manually on error
    }
  };

  return (
    <div style={styles.container}>
      <SEO 
        title="Your Feed" 
        description="Stay updated with the latest posts from creators you follow."
      />
      
      <div style={styles.content}>
        <h1 style={styles.title}>Your Feed</h1>
        
        {loading && page === 0 ? (
          <Loader type="feed" />
        ) : posts.length > 0 ? (
          <div style={styles.feedList}>
            {posts.map(post => (
              <div key={post.id} style={styles.postCard}>
                <div style={styles.postHeader}>
                  <div style={styles.creatorInfo}>
                    <Link to={`/creators/${post.creatorId}`} style={{ textDecoration: 'none' }}>
                      <SafeAvatar 
                        src={post.avatarUrl} 
                        name={post.displayName || ''} 
                        size={40} 
                      />
                    </Link>
                    <div>
                      <Link to={`/creators/${post.creatorId}`} style={styles.displayNameLink}>
                        <div style={styles.displayName}>{post.displayName}</div>
                      </Link>
                      <div style={styles.timestamp}>
                        {post.createdAt ? new Date(post.createdAt).toLocaleDateString() : 'N/A'}
                      </div>
                    </div>
                  </div>
                </div>
                <div style={styles.postBody}>
                  <h2 style={styles.postTitle}>{post.title}</h2>
                  <p style={styles.postContent}>{post.content}</p>
                </div>
                <div style={styles.postActions}>
                  <button 
                    onClick={() => handleLikeToggle(post)} 
                    disabled={!isAuthenticated}
                    style={{
                      ...styles.likeBtn,
                      color: post.likedByMe ? '#e91e63' : (isAuthenticated ? '#888' : '#ccc'),
                      cursor: isAuthenticated ? 'pointer' : 'default',
                      opacity: isAuthenticated ? 1 : 0.7
                    }}
                  >
                    <span style={styles.likeIcon}>{post.likedByMe ? '❤️' : '🤍'}</span>
                    <span>{post.likeCount || 0}</span>
                  </button>
                </div>
              </div>
            ))}
            
            {page < totalPages - 1 && (
              <button 
                onClick={loadMore} 
                style={styles.loadMoreBtn}
                disabled={loading}
              >
                {loading ? 'Loading...' : 'Load More'}
              </button>
            )}
          </div>
        ) : (
          <EmptyState 
            message="Your feed is empty."
            icon="📭"
            actionLabel="Explore Creators"
            onAction={() => navigate('/explore')}
          />
        )}
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    padding: '4rem 1rem',
    minHeight: '80vh',
    backgroundColor: 'transparent',
    color: '#F4F4F5',
  },
  content: {
    maxWidth: '600px',
    margin: '0 auto',
  },
  title: {
    fontSize: '2rem',
    fontWeight: 'bold',
    marginBottom: '2rem',
  },
  feedList: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  postCard: {
    backgroundColor: '#0F0F14',
    borderRadius: '24px',
    padding: '2rem',
    border: '1px solid rgba(255, 255, 255, 0.05)',
    boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
  },
  postHeader: {
    marginBottom: '1rem',
  },
  creatorInfo: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
  },
  avatar: {
    width: '40px',
    height: '40px',
    borderRadius: '50%',
    objectFit: 'cover',
  },
  avatarFallback: {
    width: '40px',
    height: '40px',
    borderRadius: '50%',
    background: 'linear-gradient(135deg, #6772e5 0%, #4facfe 100%)',
    color: 'white',
    display: 'flex',
    justifyContent: 'center',
    alignItems: 'center',
    fontWeight: 'bold',
    fontSize: '1.125rem',
  },
  displayName: {
    fontWeight: '800',
    fontSize: '1rem',
    whiteSpace: 'nowrap',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    maxWidth: '350px',
    color: '#F4F4F5',
  },
  displayNameLink: {
    textDecoration: 'none',
    color: 'inherit',
    cursor: 'pointer',
  },
  timestamp: {
    fontSize: '0.8rem',
    color: '#71717A',
  },
  postBody: {
    marginTop: '0.5rem',
  },
  postTitle: {
    fontSize: '1.25rem',
    fontWeight: '600',
    marginBottom: '0.5rem',
  },
  postContent: {
    fontSize: '1rem',
    lineHeight: '1.6',
    color: '#A1A1AA',
    whiteSpace: 'pre-wrap',
  },
  postActions: {
    marginTop: '1.5rem',
    paddingTop: '1rem',
    borderTop: '1px solid rgba(255, 255, 255, 0.05)',
    display: 'flex',
    alignItems: 'center',
  },
  likeBtn: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    background: 'none',
    border: 'none',
    fontSize: '1rem',
    fontWeight: '600',
    cursor: 'pointer',
    padding: '0.5rem',
    borderRadius: '8px',
    transition: 'background-color 0.2s',
  },
  likeIcon: {
    fontSize: '1.25rem',
  },
  loadMoreBtn: {
    padding: '0.875rem 2rem',
    backgroundColor: 'rgba(255, 255, 255, 0.05)',
    color: '#F4F4F5',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    cursor: 'pointer',
    marginTop: '2rem',
    fontWeight: '700',
    transition: 'all 0.2s ease',
  },
  emptyState: {
    textAlign: 'center',
    padding: '4rem 2rem',
    backgroundColor: '#1a1a1a',
    borderRadius: '12px',
    border: '1px solid #333',
  },
  emptyText: {
    fontSize: '1.5rem',
    fontWeight: 'bold',
    marginBottom: '0.5rem',
  },
  emptySubtext: {
    color: '#888',
    marginBottom: '2rem',
  },
  exploreBtn: {
    display: 'inline-block',
    padding: '0.75rem 1.5rem',
    backgroundColor: '#e91e63',
    color: 'white',
    textDecoration: 'none',
    borderRadius: '8px',
    fontWeight: 'bold',
  },
};

export default FeedPage;
