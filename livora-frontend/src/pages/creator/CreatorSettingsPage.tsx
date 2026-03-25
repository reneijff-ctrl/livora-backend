import React, { useState, useEffect } from 'react';
import { CreatorProfileSettings } from '../../services/creatorSettingsService';
import * as creatorSettingsService from '../../services/creatorSettingsService';
import CreatorSidebar from '../../components/CreatorSidebar';
import DashboardSkeleton from '../../components/DashboardSkeleton';
import SEO from '../../components/SEO';
import { useAuth } from '../../auth/useAuth';

const INTERESTED_IN_OPTIONS = ['Men', 'Women', 'Couples', 'Trans', 'Everyone'];
const LANGUAGE_OPTIONS = ['English', 'Dutch', 'Spanish', 'German', 'French', 'Italian'];

const CreatorSettingsPage: React.FC = () => {
  const { user, authLoading } = useAuth();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState(false);

  const [formData, setFormData] = useState<CreatorProfileSettings>({
    displayName: '',
    username: '',
    bio: '',
    avatarUrl: '',
    bannerUrl: '',
    gender: '',
    interestedIn: [],
    languages: [],
    location: '',
    bodyType: '',
    ethnicity: '',
    eyeColor: '',
    hairColor: '',
    heightCm: 0,
    weightKg: 0,
    onlyfansUrl: '',
    throneUrl: '',
    wishlistUrl: '',
    twitterUrl: '',
    instagramUrl: '',
    showAge: true,
    showLocation: true,
    showLanguages: true,
    showBodyType: true,
    showEthnicity: true,
    showHeightWeight: true,
  });

  useEffect(() => {
    if (!user || authLoading) return;

    const fetchSettings = async () => {
      try {
        setLoading(true);
        const response = await creatorSettingsService.getCreatorProfile();
        const data = response.data;
        if (data) {
          setFormData(prev => ({
            ...prev,
            ...data,
            interestedIn: data.interestedIn ? (typeof data.interestedIn === 'string' ? data.interestedIn.split(',').map(s => s.trim()).filter(Boolean) : data.interestedIn) : [],
            languages: data.languages ? (typeof data.languages === 'string' ? data.languages.split(',').map(s => s.trim()).filter(Boolean) : data.languages) : [],
            // Ensure defaults for booleans if missing
            showAge: data.showAge ?? true,
            showLocation: data.showLocation ?? true,
            showLanguages: data.showLanguages ?? true,
            showBodyType: data.showBodyType ?? true,
            showEthnicity: data.showEthnicity ?? true,
            showHeightWeight: data.showHeightWeight ?? true,
          }));
        }
      } catch (err) {
        console.error('Failed to fetch settings:', err);
        setError('Failed to load profile settings.');
      } finally {
        setLoading(false);
      }
    };

    fetchSettings();
  }, [user, authLoading]);

  const handleSave = async () => {
    try {
      setSaving(true);
      setError(null);
      await creatorSettingsService.updateProfile(formData);
      console.log("Profile updated");
      setSuccess(true);
      setTimeout(() => setSuccess(false), 5000);
    } catch (err) {
      console.error("Failed to save profile", err);
      setError('Failed to save changes. Please try again.');
    } finally {
      setSaving(false);
    }
  };

  if (loading || authLoading) {
    return <DashboardSkeleton title="Creator Settings" />;
  }

  return (
    <div style={styles.layout}>
      <SEO title="Creator Settings - JoinLivora" />
      <CreatorSidebar />
      
      <main style={styles.main}>
        <div style={styles.header}>
          <h1 style={styles.title}>Creator Settings</h1>
          <p style={styles.subtitle}>Edit your public profile and visibility preferences</p>
        </div>

        {error && (
          <div style={styles.errorBanner}>
            <span>⚠️</span> {error}
          </div>
        )}
        
        {success && (
          <div style={styles.successBanner}>
            <span>✅</span> Profile updated successfully!
          </div>
        )}

        <div style={styles.form}>
          {/* 1. Profile Info */}
          <section style={styles.section}>
            <div style={styles.sectionHeader}>
              <h2 style={styles.sectionTitle}>Profile Info</h2>
              <p style={styles.sectionSubtitle}>Basic information that identifies you</p>
            </div>
            
            <div style={styles.grid}>
              <div style={styles.field}>
                <label style={styles.label}>Display Name</label>
                <input
                  type="text"
                  name="displayName"
                  value={formData.displayName}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      displayName: e.target.value
                    })
                  }
                  style={styles.input}
                  placeholder="e.g. Alex Star"
                  required
                />
              </div>
              
              <div style={styles.field}>
                <label style={styles.label}>Username</label>
                <input
                  type="text"
                  name="username"
                  value={formData.username}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      username: e.target.value
                    })
                  }
                  style={styles.input}
                  placeholder="alexstar"
                  required
                />
              </div>
            </div>

            <div style={styles.field}>
              <label style={styles.label}>Bio</label>
              <textarea
                name="bio"
                value={formData.bio}
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    bio: e.target.value
                  })
                }
                style={{ ...styles.input, minHeight: '100px', resize: 'vertical' }}
                placeholder="Tell your fans a bit about yourself..."
              />
            </div>

            <div style={styles.grid}>
              <div style={styles.field}>
                <label style={styles.label}>Profile Image</label>
                <button type="button" style={styles.uploadPlaceholder}>
                  <span>📸</span> Upload Avatar
                </button>
              </div>
              <div style={styles.field}>
                <label style={styles.label}>Banner Image</label>
                <button type="button" style={styles.uploadPlaceholder}>
                  <span>🖼️</span> Upload Banner
                </button>
              </div>
            </div>
          </section>

          {/* 2. About Me */}
          <section style={styles.section}>
            <div style={styles.sectionHeader}>
              <h2 style={styles.sectionTitle}>About Me</h2>
              <p style={styles.sectionSubtitle}>Personal details for your profile</p>
            </div>
            
            <div style={styles.grid}>
              <div style={styles.field}>
                <label style={styles.label}>Gender</label>
                <select 
                  name="gender" 
                  value={formData.gender} 
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      gender: e.target.value
                    })
                  } 
                  style={styles.select}
                >
                  <option value="">Select Gender</option>
                  <option value="Female">Female</option>
                  <option value="Male">Male</option>
                  <option value="Trans">Trans</option>
                  <option value="Couple">Couple</option>
                  <option value="Non-binary">Non-binary</option>
                  <option value="Other">Other</option>
                </select>
              </div>
              
              <div style={styles.field}>
                <label style={styles.label}>Interested In</label>
                <div style={styles.checkboxGroup}>
                  {INTERESTED_IN_OPTIONS.map(option => (
                    <label key={option} style={styles.checkboxItem}>
                      <input
                        type="checkbox"
                        checked={(formData.interestedIn as string[] || []).includes(option)}
                        onChange={(e) => {
                          const current = formData.interestedIn as string[] || [];
                          const updated = e.target.checked
                            ? [...current, option]
                            : current.filter(item => item !== option);
                          setFormData({ ...formData, interestedIn: updated });
                        }}
                        style={styles.checkbox}
                      />
                      <span>{option}</span>
                    </label>
                  ))}
                </div>
              </div>
            </div>

            <div style={styles.grid}>
              <div style={styles.field}>
                <label style={styles.label}>Languages (Multi-select)</label>
                <select
                  multiple
                  name="languages"
                  value={formData.languages as string[] || []}
                  onChange={(e) => {
                    const options = Array.from(e.target.selectedOptions).map(o => o.value)
                    setFormData({
                      ...formData,
                      languages: options
                    })
                  }}
                  style={styles.multiSelect}
                >
                  {LANGUAGE_OPTIONS.map(lang => (
                    <option key={lang} value={lang}>{lang}</option>
                  ))}
                </select>
                <p style={{ ...styles.sectionSubtitle, fontSize: '0.8rem', marginTop: '0.25rem' }}>
                  Hold Ctrl (Cmd on Mac) to select multiple
                </p>
              </div>
              
              <div style={styles.field}>
                <label style={styles.label}>Location</label>
                <input
                  type="text"
                  name="location"
                  value={formData.location}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      location: e.target.value
                    })
                  }
                  style={styles.input}
                  placeholder="e.g. Los Angeles, CA"
                />
              </div>
            </div>

            <div style={styles.field}>
              <label style={styles.label}>Body Type</label>
              <select 
                name="bodyType" 
                value={formData.bodyType} 
                onChange={(e) =>
                  setFormData({
                    ...formData,
                    bodyType: e.target.value
                  })
                } 
                style={styles.select}
              >
                <option value="">Select Body Type</option>
                <option value="Slim">Slim</option>
                <option value="Athletic">Athletic</option>
                <option value="Average">Average</option>
                <option value="Curvy">Curvy</option>
                <option value="Muscular">Muscular</option>
                <option value="Plus Size">Plus Size</option>
              </select>
            </div>
          </section>

          {/* 3. Physical Attributes */}
          <section style={styles.section}>
            <div style={styles.sectionHeader}>
              <h2 style={styles.sectionTitle}>Physical Attributes</h2>
              <p style={styles.sectionSubtitle}>Provide more specific details if you wish</p>
            </div>
            
            <div style={styles.grid}>
              <div style={styles.field}>
                <label style={styles.label}>Ethnicity</label>
                <select 
                  name="ethnicity" 
                  value={formData.ethnicity} 
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      ethnicity: e.target.value
                    })
                  } 
                  style={styles.select}
                >
                  <option value="">Select Ethnicity</option>
                  <option value="Caucasian">Caucasian</option>
                  <option value="Latina">Latina</option>
                  <option value="Asian">Asian</option>
                  <option value="Black">Black</option>
                  <option value="Middle Eastern">Middle Eastern</option>
                  <option value="Mixed">Mixed</option>
                  <option value="Other">Other</option>
                </select>
              </div>
              
              <div style={styles.field}>
                <label style={styles.label}>Eye Color</label>
                <select 
                  name="eyeColor" 
                  value={formData.eyeColor} 
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      eyeColor: e.target.value
                    })
                  } 
                  style={styles.select}
                >
                  <option value="">Select Eye Color</option>
                  <option value="Blue">Blue</option>
                  <option value="Brown">Brown</option>
                  <option value="Green">Green</option>
                  <option value="Hazel">Hazel</option>
                  <option value="Grey">Grey</option>
                </select>
              </div>
            </div>

            <div style={styles.grid}>
              <div style={styles.field}>
                <label style={styles.label}>Hair Color</label>
                <select 
                  name="hairColor" 
                  value={formData.hairColor} 
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      hairColor: e.target.value
                    })
                  } 
                  style={styles.select}
                >
                  <option value="">Select Hair Color</option>
                  <option value="Blonde">Blonde</option>
                  <option value="Brown">Brown</option>
                  <option value="Black">Black</option>
                  <option value="Red">Red</option>
                  <option value="Grey">Grey</option>
                  <option value="Colored">Colored</option>
                </select>
              </div>
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1rem' }}>
                <div style={styles.field}>
                  <label style={styles.label}>Height (cm)</label>
                  <input
                    type="number"
                    name="heightCm"
                    value={formData.heightCm || ''}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        heightCm: parseInt(e.target.value, 10) || 0
                      })
                    }
                    style={styles.input}
                    placeholder="170"
                  />
                </div>
                <div style={styles.field}>
                  <label style={styles.label}>Weight (kg)</label>
                  <input
                    type="number"
                    name="weightKg"
                    value={formData.weightKg || ''}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        weightKg: parseInt(e.target.value, 10) || 0
                      })
                    }
                    style={styles.input}
                    placeholder="60"
                  />
                </div>
              </div>
            </div>
          </section>
          
          {/* 4. Social Links */}
          <section style={styles.section}>
            <div style={styles.sectionHeader}>
              <h2 style={styles.sectionTitle}>Social Links</h2>
              <p style={styles.sectionSubtitle}>Add your social media and wishlist links to your profile</p>
            </div>
            
            <div style={{ display: 'flex', flexDirection: 'column', gap: '1.5rem' }}>
              <div style={styles.field}>
                <label style={styles.label}>OnlyFans URL</label>
                <input
                  type="text"
                  name="onlyfansUrl"
                  value={formData.onlyfansUrl || ''}
                  onChange={(e) =>
                    setFormData({
                      ...formData,
                      onlyfansUrl: e.target.value
                    })
                  }
                  style={styles.input}
                  placeholder="https://onlyfans.com/username"
                />
              </div>
              
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
                <div style={styles.field}>
                  <label style={styles.label}>Throne URL</label>
                  <input
                    type="text"
                    name="throneUrl"
                    value={formData.throneUrl || ''}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        throneUrl: e.target.value
                      })
                    }
                    style={styles.input}
                    placeholder="https://throne.com/username"
                  />
                </div>
                <div style={styles.field}>
                  <label style={styles.label}>Wishlist URL</label>
                  <input
                    type="text"
                    name="wishlistUrl"
                    value={formData.wishlistUrl || ''}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        wishlistUrl: e.target.value
                      })
                    }
                    style={styles.input}
                    placeholder="https://www.amazon.com/hz/wishlist/ls/..."
                  />
                </div>
              </div>
              
              <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '1.5rem' }}>
                <div style={styles.field}>
                  <label style={styles.label}>Twitter URL</label>
                  <input
                    type="text"
                    name="twitterUrl"
                    value={formData.twitterUrl || ''}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        twitterUrl: e.target.value
                      })
                    }
                    style={styles.input}
                    placeholder="https://twitter.com/username"
                  />
                </div>
                <div style={styles.field}>
                  <label style={styles.label}>Instagram URL</label>
                  <input
                    type="text"
                    name="instagramUrl"
                    value={formData.instagramUrl || ''}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        instagramUrl: e.target.value
                      })
                    }
                    style={styles.input}
                    placeholder="https://instagram.com/username"
                  />
                </div>
              </div>
            </div>
          </section>

          {/* 5. Visibility Settings */}
          <section style={styles.section}>
            <div style={styles.sectionHeader}>
              <h2 style={styles.sectionTitle}>Visibility Settings</h2>
              <p style={styles.sectionSubtitle}>Control what information is visible on your public profile</p>
            </div>
            
            <div style={styles.toggleGrid}>
              {[
                { label: 'Show Age', name: 'showAge' },
                { label: 'Show Location', name: 'showLocation' },
                { label: 'Show Languages', name: 'showLanguages' },
                { label: 'Show Body Type', name: 'showBodyType' },
                { label: 'Show Ethnicity', name: 'showEthnicity' },
                { label: 'Show Height & Weight', name: 'showHeightWeight' },
              ].map((toggle) => (
                <div key={toggle.name} style={styles.toggleRow}>
                  <label style={styles.toggleLabel}>{toggle.label}</label>
                  <div style={styles.switchWrapper}>
                    <input
                      type="checkbox"
                      name={toggle.name}
                      checked={(formData as any)[toggle.name]}
                      onChange={(e) =>
                        setFormData({
                          ...formData,
                          [toggle.name]: e.target.checked
                        })
                      }
                      style={styles.checkbox}
                    />
                  </div>
                </div>
              ))}
            </div>
          </section>

          <div style={styles.actions}>
            <button
              type="button"
              className="save-button"
              onClick={handleSave}
              disabled={saving}
              style={{
                ...styles.saveButton,
                ...(saving ? styles.disabledButton : {})
              }}
            >
              {saving ? 'Saving Changes...' : 'Save Changes'}
            </button>
          </div>
        </div>
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
    fontFamily: 'Inter, system-ui, -apple-system, sans-serif',
  },
  main: {
    flex: 1,
    padding: '3rem 2rem',
    maxWidth: '1000px',
    margin: '0 auto',
  },
  header: {
    marginBottom: '3rem',
  },
  title: {
    fontSize: '2.5rem',
    fontWeight: '800',
    marginBottom: '0.75rem',
    color: '#F4F4F5',
    letterSpacing: '-0.025em',
  },
  subtitle: {
    fontSize: '1.125rem',
    color: '#A1A1AA',
    margin: 0,
  },
  form: {
    display: 'flex',
    flexDirection: 'column',
    gap: '2.5rem',
  },
  section: {
    backgroundColor: '#121217',
    padding: '2.5rem',
    borderRadius: '24px',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    boxShadow: '0 10px 30px rgba(0, 0, 0, 0.4)',
  },
  sectionHeader: {
    marginBottom: '2rem',
  },
  sectionTitle: {
    fontSize: '1.5rem',
    fontWeight: '700',
    color: '#F4F4F5',
    margin: '0 0 0.5rem 0',
  },
  sectionSubtitle: {
    fontSize: '0.95rem',
    color: '#71717A',
    margin: 0,
  },
  grid: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: '1.5rem',
    marginBottom: '1.5rem',
  },
  field: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
  },
  label: {
    fontSize: '0.875rem',
    fontWeight: '600',
    color: '#A1A1AA',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  input: {
    padding: '0.875rem 1.25rem',
    backgroundColor: '#08080A',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    color: '#F4F4F5',
    fontSize: '1rem',
    outline: 'none',
    transition: 'border-color 0.2s, box-shadow 0.2s',
    width: '100%',
    boxSizing: 'border-box',
  },
  select: {
    padding: '0.875rem 3rem 0.875rem 1.25rem',
    backgroundColor: '#08080A',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    color: '#F4F4F5',
    fontSize: '1rem',
    outline: 'none',
    width: '100%',
    cursor: 'pointer',
    appearance: 'none',
    WebkitAppearance: 'none',
    MozAppearance: 'none',
    backgroundImage: `url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' fill='none' viewBox='0 0 24 24' stroke='%23A1A1AA'%3E%3Cpath stroke-linecap='round' stroke-linejoin='round' stroke-width='2' d='M19 9l-7 7-7-7'%3E%3C/path%3E%3C/svg%3E")`,
    backgroundRepeat: 'no-repeat',
    backgroundPosition: 'right 1rem center',
    backgroundSize: '1.25rem',
  },
  multiSelect: {
    padding: '0.875rem 1.25rem',
    backgroundColor: '#08080A',
    border: '1px solid rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    color: '#F4F4F5',
    fontSize: '1rem',
    outline: 'none',
    width: '100%',
    minHeight: '120px',
  },
  checkboxGroup: {
    display: 'flex',
    flexWrap: 'wrap',
    gap: '1rem',
    marginTop: '0.25rem',
  },
  checkboxItem: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    cursor: 'pointer',
    fontSize: '0.95rem',
    color: '#F4F4F5',
  },
  uploadPlaceholder: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    gap: '0.75rem',
    padding: '1.5rem',
    backgroundColor: 'rgba(255, 255, 255, 0.03)',
    border: '2px dashed rgba(255, 255, 255, 0.1)',
    borderRadius: '12px',
    color: '#A1A1AA',
    fontSize: '1rem',
    fontWeight: '600',
    cursor: 'pointer',
    transition: 'background-color 0.2s',
  },
  toggleGrid: {
    display: 'grid',
    gridTemplateColumns: '1fr 1fr',
    gap: '1.5rem',
  },
  toggleRow: {
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-between',
    padding: '1.25rem',
    backgroundColor: 'rgba(255, 255, 255, 0.02)',
    borderRadius: '16px',
    border: '1px solid rgba(255, 255, 255, 0.05)',
  },
  toggleLabel: {
    fontSize: '1rem',
    fontWeight: '500',
    color: '#E4E4E7',
  },
  switchWrapper: {
    display: 'flex',
    alignItems: 'center',
  },
  checkbox: {
    width: '1.5rem',
    height: '1.5rem',
    accentColor: '#6366f1',
    cursor: 'pointer',
  },
  actions: {
    display: 'flex',
    justifyContent: 'flex-end',
    marginTop: '1rem',
    marginBottom: '4rem',
  },
  saveButton: {
    padding: '1rem 3.5rem',
    backgroundColor: '#6366f1',
    color: 'white',
    border: 'none',
    borderRadius: '14px',
    fontSize: '1.125rem',
    fontWeight: '700',
    cursor: 'pointer',
    transition: 'transform 0.2s, background-color 0.2s',
    boxShadow: '0 10px 20px -5px rgba(99, 102, 241, 0.4)',
  },
  disabledButton: {
    opacity: 0.5,
    cursor: 'not-allowed',
  },
  errorBanner: {
    padding: '1.25rem',
    backgroundColor: 'rgba(239, 68, 68, 0.1)',
    color: '#f87171',
    borderRadius: '12px',
    marginBottom: '2rem',
    border: '1px solid rgba(239, 68, 68, 0.2)',
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    fontWeight: '500',
  },
  successBanner: {
    padding: '1.25rem',
    backgroundColor: 'rgba(34, 197, 94, 0.1)',
    color: '#4ade80',
    borderRadius: '12px',
    marginBottom: '2rem',
    border: '1px solid rgba(34, 197, 94, 0.2)',
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    fontWeight: '600',
  },
};

export default CreatorSettingsPage;
