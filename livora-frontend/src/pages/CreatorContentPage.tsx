import React, { useState, useEffect } from 'react';
import SEO from '../components/SEO';
import { showToast } from '../components/Toast';
import CreatorSidebar from '../components/CreatorSidebar';
import creatorService from '../api/creatorService';
import { ContentItem, ContentAccessLevel } from '../api/contentService';
import Loader from '../components/Loader';

const CreatorContentPage: React.FC = () => {
  const [content, setContent] = useState<ContentItem[]>([]);
  const [loading, setLoading] = useState(true);
  const [editingContent, setEditingContent] = useState<ContentItem | null>(null);
  const [deletingContent, setDeletingContent] = useState<ContentItem | null>(null);
  const [isPreviewOpen, setIsPreviewOpen] = useState(false);
  const [previewItem, setPreviewItem] = useState<ContentItem | null>(null);

  useEffect(() => {
    fetchContent();
  }, []);

  const fetchContent = async () => {
    try {
      setLoading(true);
      const data = await creatorService.getMyContent();
      setContent(data);
    } catch (error) {
      console.error('Failed to fetch content:', error);
      showToast('Failed to load content.', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleDelete = async () => {
    if (!deletingContent) return;
    try {
      await creatorService.deleteContent(deletingContent.id);
      setContent(content.filter(item => item.id !== deletingContent.id));
      showToast('Content deleted successfully.', 'success');
      setDeletingContent(null);
    } catch (error) {
      console.error('Failed to delete content:', error);
      showToast('Failed to delete content.', 'error');
    }
  };

  const handleUpdate = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!editingContent) return;
    try {
      const { id, title, description, accessLevel, unlockPriceTokens } = editingContent;
      const updated = await creatorService.updateContent(id, {
        title,
        description,
        accessLevel,
        unlockPriceTokens
      });
      setContent(content.map(item => item.id === id ? { ...item, ...updated } : item));
      showToast('Content updated successfully.', 'success');
      setEditingContent(null);
    } catch (error) {
      console.error('Failed to update content:', error);
      showToast('Failed to update content.', 'error');
    }
  };

  if (loading) return (
    <div style={styles.layout}>
      <CreatorSidebar />
      <main style={styles.main}>
        <Loader type="grid" />
      </main>
    </div>
  );

  return (
    <div style={styles.layout}>
      <SEO title="Manage Content" />
      <CreatorSidebar />
      <main style={styles.main}>
        <div style={styles.header}>
          <h1 style={styles.title}>Manage Content</h1>
        </div>

        {content.length === 0 ? (
          <div style={styles.emptyState}>
            <p>You haven't uploaded any content yet.</p>
          </div>
        ) : (
          <div style={styles.grid}>
            {content.map(item => (
              <div key={item.id} style={styles.card}>
                <div style={styles.thumbnailContainer}>
                  <img 
                    src={(item.type === 'VIDEO' || item.type === 'CLIP' ? item.thumbnailUrl : (item.mediaUrl || item.thumbnailUrl)) || '/placeholder.jpg'} 
                    alt={item.title} 
                    style={styles.thumbnail} 
                  />
                  <div style={styles.badge}>
                    {item.type || 'PHOTO'}
                  </div>
                </div>
                <div style={styles.cardBody}>
                  <h3 style={styles.cardTitle}>{item.title}</h3>
                  <div style={styles.infoRow}>
                    <span style={styles.label}>Access:</span>
                    <span style={{ 
                      ...styles.value, 
                      color: item.accessLevel === 'FREE' ? '#10B981' : '#F59E0B' 
                    }}>
                      {item.accessLevel}
                    </span>
                  </div>
                  {item.accessLevel === 'PREMIUM' && (
                    <div style={styles.infoRow}>
                      <span style={styles.label}>Price:</span>
                      <span style={styles.value}>{item.unlockPriceTokens} Tokens</span>
                    </div>
                  )}
                  <div style={styles.infoRow}>
                    <span style={styles.label}>Created:</span>
                    <span style={styles.value}>
                      {new Date(item.createdAt).toLocaleDateString()}
                    </span>
                  </div>
                  <div style={styles.actions}>
                    <button 
                      onClick={() => { setPreviewItem(item); setIsPreviewOpen(true); }} 
                      style={styles.actionBtn}
                    >
                      👁️ Preview
                    </button>
                    <button 
                      onClick={() => setEditingContent(item)} 
                      style={styles.actionBtn}
                    >
                      ✏️ Edit
                    </button>
                    <button 
                      onClick={() => setDeletingContent(item)} 
                      style={{ ...styles.actionBtn, color: '#EF4444' }}
                    >
                      🗑️ Delete
                    </button>
                  </div>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Edit Modal */}
        {editingContent && (
          <div style={styles.modalOverlay}>
            <div style={styles.modal}>
              <h2 style={styles.modalTitle}>Edit Content</h2>
              <form onSubmit={handleUpdate} style={styles.form}>
                <div style={styles.formGroup}>
                  <label style={styles.formLabel}>Title</label>
                  <input 
                    style={styles.input}
                    value={editingContent.title}
                    onChange={e => setEditingContent({ ...editingContent, title: e.target.value })}
                    required
                  />
                </div>
                <div style={styles.formGroup}>
                  <label style={styles.formLabel}>Description</label>
                  <textarea 
                    style={{ ...styles.input, minHeight: '80px' }}
                    value={editingContent.description}
                    onChange={e => setEditingContent({ ...editingContent, description: e.target.value })}
                  />
                </div>
                <div style={styles.formGroup}>
                  <label style={styles.formLabel}>Access Level</label>
                  <select 
                    style={styles.input}
                    value={editingContent.accessLevel}
                    onChange={e => setEditingContent({ ...editingContent, accessLevel: e.target.value as ContentAccessLevel })}
                  >
                    <option value="FREE">FREE</option>
                    <option value="PREMIUM">PREMIUM</option>
                    <option value="CREATOR">CREATOR</option>
                  </select>
                </div>
                {editingContent.accessLevel === 'PREMIUM' && (
                  <div style={styles.formGroup}>
                    <label style={styles.formLabel}>Unlock Price (Tokens)</label>
                    <input 
                      type="number"
                      style={styles.input}
                      value={editingContent.unlockPriceTokens || 100}
                      onChange={e => setEditingContent({ ...editingContent, unlockPriceTokens: parseInt(e.target.value) })}
                      min="1"
                    />
                  </div>
                )}
                <div style={styles.modalActions}>
                  <button 
                    type="button" 
                    onClick={() => setEditingContent(null)} 
                    style={styles.cancelBtn}
                  >
                    Cancel
                  </button>
                  <button type="submit" style={styles.saveBtn}>
                    Save Changes
                  </button>
                </div>
              </form>
            </div>
          </div>
        )}

        {/* Delete Confirmation Modal */}
        {deletingContent && (
          <div style={styles.modalOverlay}>
            <div style={styles.modal}>
              <h2 style={styles.modalTitle}>Confirm Deletion</h2>
              <p style={{ color: '#A1A1AA', marginBottom: '1.5rem' }}>
                Are you sure you want to delete "{deletingContent.title}"? This action cannot be undone.
              </p>
              <div style={styles.modalActions}>
                <button 
                  onClick={() => setDeletingContent(null)} 
                  style={styles.cancelBtn}
                >
                  Cancel
                </button>
                <button 
                  onClick={handleDelete} 
                  style={{ ...styles.saveBtn, backgroundColor: '#EF4444' }}
                >
                  Delete
                </button>
              </div>
            </div>
          </div>
        )}

        {/* Preview Modal */}
        {isPreviewOpen && previewItem && (
          <div style={styles.modalOverlay} onClick={() => setIsPreviewOpen(false)}>
            <div style={styles.previewModal} onClick={e => e.stopPropagation()}>
              <button 
                style={styles.closeBtn} 
                onClick={() => setIsPreviewOpen(false)}
              >
                &times;
              </button>
              <div style={styles.previewContent}>
                {previewItem.type === 'VIDEO' || previewItem.type === 'CLIP' ? (
                  <video 
                    src={previewItem.mediaUrl || undefined} 
                    controls 
                    style={styles.previewMedia}
                    autoPlay
                    poster={previewItem.thumbnailUrl}
                  />
                ) : (
                  <img 
                    src={previewItem.mediaUrl || previewItem.thumbnailUrl} 
                    alt={previewItem.title} 
                    style={styles.previewMedia} 
                  />
                )}
              </div>
              <div style={{ padding: '1.5rem', backgroundColor: '#18181B' }}>
                <h3 style={styles.cardTitle}>{previewItem.title}</h3>
                <p style={{ color: '#A1A1AA', fontSize: '0.9rem', margin: 0 }}>{previewItem.description}</p>
              </div>
            </div>
          </div>
        )}
      </main>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  layout: {
    display: 'flex',
    minHeight: '100vh',
    backgroundColor: '#08080A',
    color: '#F4F4F5',
  },
  main: {
    flex: 1,
    padding: '2rem',
    maxWidth: '1200px',
    margin: '0 auto',
    width: '100%',
  },
  header: {
    marginBottom: '2rem',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: {
    fontSize: '2rem',
    fontWeight: '700',
    margin: 0,
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fill, minmax(280px, 1fr))',
    gap: '1.5rem',
  },
  card: {
    backgroundColor: '#18181B',
    borderRadius: '12px',
    overflow: 'hidden',
    border: '1px solid #27272A',
    transition: 'transform 0.2s',
  },
  thumbnailContainer: {
    position: 'relative',
    paddingTop: '56.25%', // 16:9
    backgroundColor: '#000',
  },
  thumbnail: {
    position: 'absolute',
    top: 0,
    left: 0,
    width: '100%',
    height: '100%',
    objectFit: 'cover',
  },
  badge: {
    position: 'absolute',
    top: '0.5rem',
    right: '0.5rem',
    backgroundColor: 'rgba(0,0,0,0.6)',
    padding: '0.2rem 0.5rem',
    borderRadius: '4px',
    fontSize: '0.75rem',
    fontWeight: '600',
  },
  cardBody: {
    padding: '1rem',
  },
  cardTitle: {
    fontSize: '1.125rem',
    fontWeight: '600',
    marginBottom: '0.75rem',
    overflow: 'hidden',
    textOverflow: 'ellipsis',
    whiteSpace: 'nowrap',
  },
  infoRow: {
    display: 'flex',
    justifyContent: 'space-between',
    marginBottom: '0.4rem',
    fontSize: '0.875rem',
  },
  label: {
    color: '#A1A1AA',
  },
  value: {
    fontWeight: '500',
  },
  actions: {
    marginTop: '1rem',
    display: 'flex',
    gap: '0.5rem',
    borderTop: '1px solid #27272A',
    paddingTop: '0.75rem',
  },
  actionBtn: {
    flex: 1,
    backgroundColor: 'transparent',
    border: 'none',
    color: '#A1A1AA',
    fontSize: '0.8rem',
    cursor: 'pointer',
    padding: '0.4rem',
    borderRadius: '4px',
    transition: 'background 0.2s, color 0.2s',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '0.25rem',
  },
  emptyState: {
    textAlign: 'center',
    padding: '4rem 2rem',
    backgroundColor: '#18181B',
    borderRadius: '12px',
    border: '1px solid #27272A',
    color: '#A1A1AA',
  },
  modalOverlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    backgroundColor: 'rgba(0,0,0,0.85)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 1000,
    padding: '1rem',
  },
  modal: {
    backgroundColor: '#18181B',
    padding: '2rem',
    borderRadius: '12px',
    width: '100%',
    maxWidth: '450px',
    border: '1px solid #27272A',
    boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.1), 0 10px 10px -5px rgba(0, 0, 0, 0.04)',
  },
  previewModal: {
    backgroundColor: '#000',
    borderRadius: '12px',
    width: '100%',
    maxWidth: '900px',
    maxHeight: '90vh',
    position: 'relative',
    display: 'flex',
    flexDirection: 'column',
    overflow: 'hidden',
    border: '1px solid #27272A',
  },
  closeBtn: {
    position: 'absolute',
    top: '1rem',
    right: '1rem',
    backgroundColor: 'rgba(0,0,0,0.5)',
    color: '#fff',
    border: 'none',
    width: '32px',
    height: '32px',
    borderRadius: '50%',
    fontSize: '1.5rem',
    cursor: 'pointer',
    zIndex: 10,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
  },
  previewContent: {
    flex: 1,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: '#000',
    minHeight: '300px',
  },
  previewMedia: {
    maxWidth: '100%',
    maxHeight: '70vh',
    objectFit: 'contain',
  },
  modalTitle: {
    fontSize: '1.5rem',
    fontWeight: '700',
    marginBottom: '1.5rem',
    color: '#fff',
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
  },
  formGroup: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.4rem',
  },
  formLabel: {
    fontSize: '0.875rem',
    fontWeight: '500',
    color: '#A1A1AA',
  },
  input: {
    backgroundColor: '#09090B',
    border: '1px solid #27272A',
    borderRadius: '6px',
    padding: '0.75rem',
    color: '#fff',
    fontSize: '1rem',
    outline: 'none',
    transition: 'border-color 0.2s',
  },
  modalActions: {
    display: 'flex',
    gap: '1rem',
    marginTop: '1.5rem',
  },
  cancelBtn: {
    flex: 1,
    padding: '0.75rem',
    borderRadius: '6px',
    border: '1px solid #27272A',
    backgroundColor: 'transparent',
    color: '#fff',
    cursor: 'pointer',
    fontWeight: '600',
  },
  saveBtn: {
    flex: 1,
    padding: '0.75rem',
    borderRadius: '6px',
    border: 'none',
    backgroundColor: '#6366F1',
    color: '#fff',
    fontWeight: '600',
    cursor: 'pointer',
  }
};

export default CreatorContentPage;
