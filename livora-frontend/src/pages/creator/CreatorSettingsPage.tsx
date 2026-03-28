import React, { useState, useEffect } from 'react';
import { CreatorProfileSettings } from '../../services/creatorSettingsService';
import * as creatorSettingsService from '../../services/creatorSettingsService';
import creatorService from '../../api/creatorService';
import CreatorSidebar from '../../components/CreatorSidebar';
import DashboardSkeleton from '../../components/DashboardSkeleton';
import SEO from '../../components/SEO';
import SafeAvatar from '../../components/ui/SafeAvatar';
import CountrySelect from '../../components/ui/CountrySelect';
import StateSelect from '../../components/ui/StateSelect';
import LanguageSelect from '../../components/ui/LanguageSelect';
import { useAuth } from '../../auth/useAuth';
import { getCountryLabel } from '../../data/countries';

const INTERESTED_IN_OPTIONS = ['Men', 'Women', 'Couples', 'Trans', 'Everyone'];

const CreatorSettingsPage: React.FC = () => {
  const { user, authLoading } = useAuth();
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [isUploadingAvatar, setIsUploadingAvatar] = useState(false);
  const [isUploadingBanner, setIsUploadingBanner] = useState(false);
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
    state: '',
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
    locationVisibility: 'full',
    customLocation: '',
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
            // Migration: showLocation boolean → locationVisibility
            locationVisibility: data.locationVisibility ?? (data.showLocation === false ? 'hidden' : 'full'),
            customLocation: data.customLocation ?? '',
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

  const handleImageUpload = async (file: File, type: 'PROFILE' | 'BANNER') => {
    try {
      if (type === 'PROFILE') setIsUploadingAvatar(true);
      else setIsUploadingBanner(true);
      setError(null);
      const updatedProfile = await creatorService.uploadCreatorImage(file, type);
      if (type === 'PROFILE') {
        setFormData(prev => ({ ...prev, avatarUrl: updatedProfile.avatarUrl || '' }));
      } else {
        setFormData(prev => ({ ...prev, bannerUrl: updatedProfile.bannerUrl || '' }));
      }
      setSuccess(true);
      setTimeout(() => setSuccess(false), 3000);
    } catch (err: any) {
      console.error(`Failed to upload ${type.toLowerCase()} image:`, err);
      setError(err.response?.data?.message || `Failed to upload ${type.toLowerCase()} image. Please try again.`);
    } finally {
      if (type === 'PROFILE') setIsUploadingAvatar(false);
      else setIsUploadingBanner(false);
    }
  };

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
          <h1 style={styles.title}>Profile Settings</h1>
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
                <div style={styles.uploadArea}>
                  <SafeAvatar
                    src={formData.avatarUrl || null}
                    name={formData.displayName}
                    size={64}
                  />
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    <input
                      type="file"
                      id="avatar-upload"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) handleImageUpload(file, 'PROFILE');
                        e.target.value = '';
                      }}
                    />
                    <label htmlFor="avatar-upload" style={styles.uploadButton}>
                      {isUploadingAvatar ? '⏳ Uploading...' : '📸 Change Avatar'}
                    </label>
                    <p style={styles.uploadHelp}>JPG or PNG. Max 10MB.</p>
                  </div>
                </div>
              </div>
              <div style={styles.field}>
                <label style={styles.label}>Banner Image</label>
                <div style={styles.uploadArea}>
                  {formData.bannerUrl ? (
                    <img
                      src={formData.bannerUrl}
                      alt="Banner"
                      style={{ width: '120px', height: '64px', objectFit: 'cover', borderRadius: '8px' }}
                    />
                  ) : (
                    <div style={{ width: '120px', height: '64px', borderRadius: '8px', backgroundColor: 'rgba(255,255,255,0.05)', display: 'flex', alignItems: 'center', justifyContent: 'center', color: '#71717A', fontSize: '0.8rem' }}>No banner</div>
                  )}
                  <div style={{ display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
                    <input
                      type="file"
                      id="banner-upload"
                      accept="image/*"
                      style={{ display: 'none' }}
                      onChange={(e) => {
                        const file = e.target.files?.[0];
                        if (file) handleImageUpload(file, 'BANNER');
                        e.target.value = '';
                      }}
                    />
                    <label htmlFor="banner-upload" style={styles.uploadButton}>
                      {isUploadingBanner ? '⏳ Uploading...' : '🖼️ Change Banner'}
                    </label>
                    <p style={styles.uploadHelp}>Recommended: 1200×400px.</p>
                  </div>
                </div>
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

            <div style={styles.field}>
              <label style={styles.label}>Languages</label>
              <LanguageSelect
                value={formData.languages as string[] || []}
                onChange={(codes) =>
                  setFormData({
                    ...formData,
                    languages: codes
                  })
                }
              />
            </div>

            <div style={styles.grid}>
              <div style={styles.field}>
                <label style={styles.label}>Country</label>
                <CountrySelect
                  value={formData.location ?? ''}
                  onChange={(code) =>
                    setFormData({
                      ...formData,
                      location: code,
                      state: ''
                    })
                  }
                  placeholder="Select your country..."
                />
              </div>
              
              <div style={styles.field}>
                <label style={styles.label}>State / Province</label>
                <StateSelect
                  countryCode={formData.location ?? ''}
                  value={formData.state ?? ''}
                  onChange={(val) =>
                    setFormData({
                      ...formData,
                      state: val
                    })
                  }
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

            {/* Location Visibility */}
            <div style={{ marginTop: '2rem', borderTop: '1px solid rgba(255, 255, 255, 0.08)', paddingTop: '2rem' }}>
              <div style={styles.field}>
                <label style={styles.label}>Location Visibility</label>
                <p style={{ fontSize: '0.85rem', color: '#71717A', margin: '0 0 0.5rem 0' }}>
                  Control how your location appears to viewers
                </p>
                <select
                  value={formData.locationVisibility ?? 'full'}
                  onChange={(e) => {
                    const val = e.target.value as 'hidden' | 'country' | 'full' | 'custom';
                    setFormData({
                      ...formData,
                      locationVisibility: val,
                      // Keep showLocation in sync for backward compatibility
                      showLocation: val !== 'hidden',
                    });
                  }}
                  style={styles.select}
                >
                  <option value="hidden">🚫 Hide location</option>
                  <option value="country">🌍 Show country only</option>
                  <option value="full">📍 Show full location</option>
                  <option value="custom">✏️ Custom location text</option>
                </select>
              </div>

              {formData.locationVisibility === 'custom' && (
                <div style={{ ...styles.field, marginTop: '1rem' }}>
                  <label style={styles.label}>Custom Location</label>
                  <input
                    type="text"
                    value={formData.customLocation ?? ''}
                    onChange={(e) =>
                      setFormData({
                        ...formData,
                        customLocation: e.target.value
                      })
                    }
                    maxLength={60}
                    placeholder='e.g. On your screen 🔥'
                    style={styles.input}
                  />
                  <span style={{ fontSize: '0.8rem', color: '#71717A' }}>
                    {(formData.customLocation ?? '').length}/60
                  </span>
                </div>
              )}

              {/* Preview */}
              {formData.locationVisibility !== 'hidden' && (
                <div style={{
                  marginTop: '1rem',
                  padding: '0.875rem 1.25rem',
                  backgroundColor: '#08080A',
                  border: '1px solid rgba(255, 255, 255, 0.08)',
                  borderRadius: '12px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: '0.5rem',
                }}>
                  <span style={{ fontSize: '0.8rem', color: '#71717A' }}>Preview:</span>
                  <span style={{ fontSize: '0.95rem', color: '#F4F4F5' }}>
                    📍{' '}
                    {formData.locationVisibility === 'country'
                      ? (formData.location ? getCountryLabel(formData.location) : 'No country set')
                      : formData.locationVisibility === 'full'
                        ? (formData.state && formData.location
                          ? `${formData.state}, ${getCountryLabel(formData.location)}`
                          : formData.location
                            ? getCountryLabel(formData.location)
                            : 'No location set')
                        : formData.locationVisibility === 'custom'
                          ? (formData.customLocation || formData.location ? (formData.customLocation || getCountryLabel(formData.location ?? '')) : 'No custom text set')
                          : ''}
                  </span>
                </div>
              )}
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
  uploadArea: {
    display: 'flex',
    alignItems: 'center',
    gap: '1rem',
    padding: '1rem',
    backgroundColor: 'rgba(255, 255, 255, 0.03)',
    border: '1px solid rgba(255, 255, 255, 0.08)',
    borderRadius: '12px',
  },
  uploadButton: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '0.5rem',
    padding: '0.625rem 1.25rem',
    backgroundColor: 'rgba(99, 102, 241, 0.15)',
    border: '1px solid rgba(99, 102, 241, 0.3)',
    borderRadius: '10px',
    color: '#818cf8',
    fontSize: '0.875rem',
    fontWeight: '600',
    cursor: 'pointer',
    transition: 'background-color 0.2s',
  },
  uploadHelp: {
    fontSize: '0.75rem',
    color: '#71717A',
    margin: 0,
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
