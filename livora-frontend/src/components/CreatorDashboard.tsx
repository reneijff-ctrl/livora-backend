import React from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { useCreatorDashboard } from '../hooks/useCreatorDashboard';
import creatorService from '../api/creatorService';
import { showToast } from './Toast';
import { safeRender } from '@/utils/safeRender';
import DashboardSkeleton from './DashboardSkeleton';
import EmptyState from './EmptyState';
import SEO from './SEO';
import CreatorSidebar from './CreatorSidebar';

/**
 * CreatorDashboard component.
 * Displays sections for Earnings summary, Live status, Payout status, and Recent activity.
 * Fetches real data from the backend.
 */
const CreatorDashboard: React.FC = () => {
  const navigate = useNavigate();
  
  // Use the new dashboard hook
  const { 
    data: creatorDashboard, 
    loading: dashboardLoading
  } = useCreatorDashboard();

  const { user, authLoading } = useAuth();
  const timeoutsRef = React.useRef<Set<NodeJS.Timeout>>(new Set());

  // Cleanup all timeouts on unmount
  React.useEffect(() => {
    return () => {
      timeoutsRef.current.forEach(clearTimeout);
      timeoutsRef.current.clear();
    };
  }, []);

  // State for posts
  const [postContent, setPostContent] = React.useState('');
  const [isSubmitting, setIsSubmitting] = React.useState(false);
  const [myPosts, setMyPosts] = React.useState<any[]>([]);
  const [postsLoading, setPostsLoading] = React.useState(false);
  const [followerCount, setFollowerCount] = React.useState<number>(0);
  const [followerCountLoading, setFollowerCountLoading] = React.useState(true);
  const [creatorStats, setCreatorStats] = React.useState<any>(null);
  const [creatorStatsLoading, setCreatorStatsLoading] = React.useState(true);
  const [stripeStatus, setStripeStatus] = React.useState<{ hasAccount: boolean; onboardingCompleted: boolean; payoutsEnabled: boolean } | null>(null);
  const [stripeLoading, setStripeLoading] = React.useState(true);

  const fetchFollowerCount = React.useCallback(async (retryCount = 0) => {
    const username = creatorDashboard?.creatorProfile?.username;
    if (!username) return;

    try {
      if (retryCount === 0) setFollowerCountLoading(true);
      const count = await creatorService.getFollowerCount(username, retryCount === 0);
      setFollowerCount(count);
    } catch (err) {
      if (retryCount === 0) {
        const timeout = setTimeout(() => {
          fetchFollowerCount(1);
          timeoutsRef.current.delete(timeout);
        }, 1000);
        timeoutsRef.current.add(timeout);
      } else {
        console.error('Error fetching follower count after retry:', err);
        // Fallback to dashboard stats if dedicated call fails
        if (creatorDashboard?.stats?.totalFollowers) {
          setFollowerCount(creatorDashboard?.stats?.totalFollowers || 0);
        }
      }
    } finally {
      if (retryCount !== 0 || !followerCountLoading) {
        setFollowerCountLoading(false);
      }
    }
  }, [creatorDashboard?.creatorProfile?.username, creatorDashboard?.stats?.totalFollowers]);


  const fetchCreatorStats = React.useCallback(async (retryCount = 0) => {
    try {
      if (retryCount === 0) setCreatorStatsLoading(true);
      const stats = await creatorService.getMyStats(retryCount === 0);
      setCreatorStats(stats);
    } catch (err) {
      if (retryCount === 0) {
        const timeout = setTimeout(() => {
          fetchCreatorStats(1);
          timeoutsRef.current.delete(timeout);
        }, 1000);
        timeoutsRef.current.add(timeout);
      } else {
        console.error('Error fetching creator stats after retry:', err);
      }
    } finally {
      if (retryCount !== 0 || !creatorStatsLoading) {
        setCreatorStatsLoading(false);
      }
    }
  }, []);

  const fetchStripeStatus = React.useCallback(async () => {
    try {
      setStripeLoading(true);
      const status = await creatorService.getStripeStatus();
      setStripeStatus(status);
    } catch (err) {
      console.error('Error fetching Stripe status:', err);
    } finally {
      setStripeLoading(false);
    }
  }, []);




  const fetchMyPosts = React.useCallback(async (retryCount = 0) => {
    const username = creatorDashboard?.creatorProfile?.username;
    if (!username) return;
    
    try {
      if (retryCount === 0) setPostsLoading(true);
      const posts = await creatorService.getCreatorPosts(username, retryCount === 0);
      setMyPosts(posts);
    } catch (err) {
      if (retryCount === 0) {
        const timeout = setTimeout(() => {
          fetchMyPosts(1);
          timeoutsRef.current.delete(timeout);
        }, 1000);
        timeoutsRef.current.add(timeout);
      } else {
        console.error('Error fetching my posts after retry:', err);
      }
    } finally {
      if (retryCount !== 0 || !postsLoading) {
        setPostsLoading(false);
      }
    }
  }, [creatorDashboard?.creatorProfile?.username]);

  React.useEffect(() => {
    fetchCreatorStats();
  }, [fetchCreatorStats]);

  React.useEffect(() => {
    fetchStripeStatus();
  }, [fetchStripeStatus]);

  React.useEffect(() => {
    if (creatorDashboard?.creatorProfile?.username) {
      fetchFollowerCount();
    }
  }, [creatorDashboard?.creatorProfile?.username, fetchFollowerCount]);

  React.useEffect(() => {
    if (creatorDashboard?.creatorProfile?.username) {
      fetchMyPosts();
    }
  }, [creatorDashboard?.creatorProfile?.username, fetchMyPosts]);

  const handleCreatePost = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!postContent.trim()) {
      showToast('Post content is required', 'error');
      return;
    }

    setIsSubmitting(true);
    try {
      // Create a title from the first line or first 80 chars of content
      const firstLine = postContent.trim().split('\n')[0];
      const generatedTitle = firstLine.substring(0, 80) || 'New Post';

      const newPost = await creatorService.createPost({ 
        title: generatedTitle, 
        content: postContent.trim() 
      });

      showToast('Post created successfully!', 'success');
      setPostContent('');
      
      // Prepend new post to the list for immediate update
      setMyPosts(prev => [newPost, ...prev]);
    } catch (err) {
      console.error('Error creating post:', err);
      // Handled by global interceptor
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleViewPublicProfile = () => {
    // Navigate to public profile using the creator profile ID from backend
    const creatorId = creatorDashboard?.creatorProfile?.creatorId;
    if (creatorId) {
      navigate(`/creators/${creatorId}`);
    } else if (user?.id) {
      // Fallback to user ID if profile not loaded yet
      navigate(`/creators/${user.id}`);
    }
  };

  const handleConnectStripe = async () => {
    try {
      const { onboardingUrl } = await creatorService.createStripeAccount();
      if (onboardingUrl) {
        window.location.href = onboardingUrl;
      }
    } catch (err) {
      console.error('Error connecting Stripe:', err);
    }
  };

  const handleContinueOnboarding = async () => {
    try {
      const { onboardingUrl } = await creatorService.getStripeOnboardingLink();
      if (onboardingUrl) {
        window.location.href = onboardingUrl;
      }
    } catch (err) {
      console.error('Error getting onboarding link:', err);
    }
  };



  if (authLoading || dashboardLoading) {
    return <DashboardSkeleton title="Creator Dashboard" />;
  }

  return (
    <div style={styles.layout}>
      <SEO 
        title="Creator Command Center" 
        description="Manage your Livora creator account, broadcasting status, and hub activity."
      />
      <CreatorSidebar />

      <main style={styles.main}>
        {/* 1. CREATOR IDENTITY HEADER */}
        <header style={styles.hubHeader}>
          <div style={styles.identityProfile}>
            <div style={styles.avatarCircle}>
               {safeRender(creatorDashboard?.creatorProfile?.displayName?.[0] || user?.email?.[0] || 'C')}
            </div>
            <div style={styles.identityText}>
              <div style={styles.identityBadge}>CREATOR HUB</div>
              <h1 style={styles.identityName}>
                {safeRender(creatorDashboard?.creatorProfile?.displayName || user?.displayName || 'Creator')}
              </h1>
              <p style={styles.identityHandle}>
                {safeRender(creatorDashboard?.creatorProfile?.username ? `@${creatorDashboard.creatorProfile.username}` : user?.email)}
              </p>
            </div>
          </div>
          <div style={styles.headerStats}>
            <div style={styles.headerStatBox}>
              <span style={styles.headerStatLabel}>Followers</span>
              <span style={styles.headerStatValue}>
                {followerCountLoading ? '...' : safeRender((followerCount ?? 0).toLocaleString())}
              </span>
            </div>
            <div style={styles.headerStatBox}>
              <span style={styles.headerStatLabel}>Trust Score</span>
              <span style={styles.headerStatValue}>{safeRender(user?.trustScore ?? 'N/A')}</span>
            </div>
          </div>
        </header>

        {/* 2. DOMINANT LIVE STATUS HERO */}
        <section style={styles.liveHero}>
          <div style={styles.liveHeroBackground}>
            <div style={(creatorDashboard?.stats?.activeStreams ?? 0) > 0 ? styles.glowLive : styles.glowOffline} />
          </div>
          <div style={styles.liveHeroBody}>
            <div style={styles.statusCluster}>
              <div style={styles.liveStatusBadge((creatorDashboard?.stats?.activeStreams ?? 0) > 0)}>
                <span style={styles.liveDot((creatorDashboard?.stats?.activeStreams ?? 0) > 0)} />
                {(creatorDashboard?.stats?.activeStreams ?? 0) > 0 ? 'SYSTEM LIVE' : 'STATION STANDBY'}
              </div>
              {creatorDashboard?.stats?.status && (
                <div style={styles.verificationBadge(creatorDashboard?.stats?.status === 'ACTIVE')}>
                  {creatorDashboard?.stats?.status === 'ACTIVE' ? '✓ Verified' : '• Pending'}
                </div>
              )}
            </div>
            
            <h2 style={styles.liveHeroHeadline}>
              {(creatorDashboard?.stats?.activeStreams ?? 0) > 0 
                ? 'Your broadcast is currently live to the global feed' 
                : 'The broadcasting station is currently on standby'}
            </h2>
            
            <div style={styles.liveHeroActions}>
              <button 
                onClick={() => navigate('/creator/live')} 
                style={styles.heroPrimaryBtn}
                className="hover:scale-[1.02] active:scale-[0.98] hover:shadow-[0_0_20px_rgba(255,255,255,0.1)] transition-all"
              >
                {(creatorDashboard?.stats?.activeStreams ?? 0) > 0 ? 'Open Control Room' : 'Initiate Broadcast'}
              </button>
              <button 
                onClick={handleViewPublicProfile} 
                style={styles.heroSecondaryBtn}
                className="hover:bg-white/5 transition-colors"
              >
                Public Profile View
              </button>
            </div>
          </div>
        </section>

        {/* 3. QUICK ACTION ROW */}
        <div style={styles.quickActionRow}>
          <button onClick={() => navigate('/creator/upload')} style={styles.quickActionBtn}>
            <span style={styles.actionIcon}>📤</span>
            <div style={styles.actionText}>
              <span style={styles.actionLabel}>Upload</span>
              <span style={styles.actionDesc}>Media Content</span>
            </div>
          </button>
          <button onClick={() => navigate('/creator/analytics')} style={styles.quickActionBtn}>
            <span style={styles.actionIcon}>📈</span>
            <div style={styles.actionText}>
              <span style={styles.actionLabel}>Analytics</span>
              <span style={styles.actionDesc}>Growth Data</span>
            </div>
          </button>
          <button onClick={() => navigate('/creator/earnings')} style={styles.quickActionBtn}>
            <span style={styles.actionIcon}>💰</span>
            <div style={styles.actionText}>
              <span style={styles.actionLabel}>Revenue</span>
              <span style={styles.actionDesc}>Token Balance</span>
            </div>
          </button>
          <button onClick={() => navigate('/creator/settings')} style={styles.quickActionBtn}>
            <span style={styles.actionIcon}>⚙️</span>
            <div style={styles.actionText}>
              <span style={styles.actionLabel}>Settings</span>
              <span style={styles.actionDesc}>Hub Config</span>
            </div>
          </button>
        </div>

        {/* 4. HUB COMMAND GRID */}
        <div style={styles.hubGrid}>
          {/* Main Column */}
          <div style={styles.gridColMain}>
            {/* Quick Publish */}
            <section style={styles.hubCard}>
              <div style={styles.cardHeader}>
                <h3 style={styles.cardTitle}>Quick Publish</h3>
              </div>
              <form onSubmit={handleCreatePost} style={styles.publishForm}>
                <textarea
                  value={postContent}
                  onChange={(e) => setPostContent(e.target.value)}
                  placeholder="Broadcast a message to your followers..."
                  style={styles.publishTextarea}
                  required
                />
                <div style={styles.publishFooter}>
                  <p style={styles.publishHint}>Messages are visible to your followers only.</p>
                  <button 
                    type="submit" 
                    disabled={isSubmitting} 
                    style={styles.publishBtn}
                  >
                    {isSubmitting ? '...' : 'Publish'}
                  </button>
                </div>
              </form>
            </section>

            {/* Activity Ledger */}
            <section style={styles.hubCard}>
              <div style={styles.cardHeader}>
                <h3 style={styles.cardTitle}>Hub Activity</h3>
              </div>
              <div style={styles.activityLedger}>
                {postsLoading ? (
                  <div style={styles.ledgerLoading}>Updating activity log...</div>
                ) : (myPosts || []).length > 0 ? (
                  (myPosts || []).slice(0, 5).map((post) => (
                    <div key={post.id} style={styles.ledgerItem}>
                      <div style={styles.ledgerMeta}>
                        <span style={styles.ledgerDate}>
                          {post.createdAt ? new Date(post.createdAt).toLocaleDateString() : 'Today'}
                        </span>
                        <span style={styles.ledgerBadge}>POST</span>
                      </div>
                      <p style={styles.ledgerContent}>{post.content}</p>
                    </div>
                  ))
                ) : (
                  <div style={{ padding: '2rem 0' }}>
                    <EmptyState 
                      message="No recent activity. Start by publishing your first post!"
                      icon="📝"
                      showLogo={false}
                    />
                  </div>
                )}
              </div>
            </section>
          </div>

          {/* Side Column */}
          <div style={styles.gridColSide}>
            {/* Station Health */}
            <section style={styles.hubCard}>
              <div style={styles.cardHeader}>
                <h3 style={styles.cardTitle}>Station Health</h3>
              </div>
              <div style={styles.statsStrip}>
                <div style={styles.statItem}>
                  <span style={styles.statItemValue}>{creatorDashboard?.stats?.contentCount || 0}</span>
                  <span style={styles.statItemLabel}>Broadcasts</span>
                </div>
                <div style={styles.statDivider} />
                <div style={styles.statItem}>
                  <span style={styles.statItemValue}>{creatorStats?.totalPosts || 0}</span>
                  <span style={styles.statItemLabel}>Total Posts</span>
                </div>
              </div>
            </section>

            {/* Payout Security */}
            <section style={styles.hubCard}>
              <div style={styles.cardHeader}>
                <h3 style={styles.cardTitle}>Payout Channel</h3>
              </div>
              <div style={styles.payoutStatus}>
                {stripeLoading ? (
                  <div style={styles.ledgerLoading}>Verifying connection...</div>
                ) : !stripeStatus?.hasAccount ? (
                  <div style={styles.payoutAlert}>
                    <p style={styles.payoutAlertText}>Connection to Stripe required for revenue.</p>
                    <button onClick={handleConnectStripe} style={styles.setupBtn}>Connect Stripe</button>
                  </div>
                ) : !stripeStatus.payoutsEnabled ? (
                  <div style={styles.payoutWarning}>
                    <p style={styles.payoutAlertText}>Onboarding incomplete. Payouts are restricted.</p>
                    <button onClick={handleContinueOnboarding} style={styles.setupBtn}>Complete Setup</button>
                  </div>
                ) : (
                  <div style={styles.payoutVerified}>
                    <div style={styles.verifiedTag}>
                      <span style={styles.verifiedDot} />
                      ACTIVE CHANNEL
                    </div>
                    <p style={styles.payoutSuccessText}>Your payout channel is verified and secure.</p>
                  </div>
                )}
              </div>
            </section>
          </div>
        </div>
      </main>
    </div>
  );
};

const styles: { [key: string]: any } = {
  layout: {
    display: 'flex',
    minHeight: 'calc(100vh - 64px)',
    backgroundColor: '#050505',
    color: '#F4F4F5',
    fontFamily: 'system-ui, -apple-system, sans-serif',
  },
  main: {
    flex: 1,
    padding: '2.5rem 2rem', // py-10 px-8
    maxWidth: '1100px',
    margin: '0 auto',
    overflowY: 'auto',
  },
  hubHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '2.5rem',
    flexWrap: 'wrap',
    gap: '2rem',
  },
  identityProfile: {
    display: 'flex',
    alignItems: 'center',
    gap: '1.5rem',
  },
  avatarCircle: {
    width: '72px',
    height: '72px',
    borderRadius: '20px',
    backgroundColor: '#0C0C0E',
    border: '1px solid rgba(255,255,255,0.08)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    fontSize: '1.75rem',
    fontWeight: '800',
    color: '#6366f1',
    boxShadow: '0 10px 30px rgba(0,0,0,0.5)',
  },
  identityText: {
    display: 'flex',
    flexDirection: 'column',
  },
  identityBadge: {
    fontSize: '0.65rem',
    fontWeight: '900',
    color: '#6366f1',
    letterSpacing: '0.1em',
    textTransform: 'uppercase',
    marginBottom: '0.25rem',
  },
  identityName: {
    fontSize: '1.75rem',
    fontWeight: '800',
    margin: 0,
    letterSpacing: '-0.02em',
    color: '#fff',
  },
  identityHandle: {
    fontSize: '0.875rem',
    color: '#71717A',
    fontWeight: '500',
    margin: 0,
  },
  headerStats: {
    display: 'flex',
    gap: '2rem',
  },
  headerStatBox: {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'flex-end',
  },
  headerStatLabel: {
    fontSize: '0.7rem',
    fontWeight: '700',
    color: '#52525B',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  headerStatValue: {
    fontSize: '1.25rem',
    fontWeight: '800',
    color: '#fff',
  },
  liveHero: {
    position: 'relative',
    backgroundColor: '#0B0B0D',
    borderRadius: '32px',
    padding: '4rem 3.5rem',
    overflow: 'hidden',
    border: '1px solid rgba(255,255,255,0.06)',
    marginBottom: '2.5rem',
    boxShadow: '0 50px 100px rgba(0,0,0,0.7), inset 0 1px 0 rgba(255,255,255,0.05)',
  },
  liveHeroBackground: {
    position: 'absolute',
    top: 0,
    right: 0,
    bottom: 0,
    width: '40%',
    pointerEvents: 'none',
  },
  glowLive: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    width: '300px',
    height: '300px',
    background: 'radial-gradient(circle, rgba(239, 68, 68, 0.15) 0%, transparent 70%)',
  },
  glowOffline: {
    position: 'absolute',
    top: '50%',
    left: '50%',
    transform: 'translate(-50%, -50%)',
    width: '300px',
    height: '300px',
    background: 'radial-gradient(circle, rgba(99, 102, 241, 0.1) 0%, transparent 70%)',
  },
  liveHeroBody: {
    position: 'relative',
    zIndex: 1,
    maxWidth: '600px',
  },
  statusCluster: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.75rem',
    marginBottom: '1.5rem',
  },
  liveStatusBadge: (isLive: boolean) => ({
    display: 'flex',
    alignItems: 'center',
    gap: '0.6rem',
    padding: '0.6rem 1.25rem',
    backgroundColor: isLive ? 'rgba(239, 68, 68, 0.12)' : 'rgba(255, 255, 255, 0.04)',
    borderRadius: '14px',
    border: `1px solid ${isLive ? 'rgba(239, 68, 68, 0.25)' : 'rgba(255, 255, 255, 0.1)'}`,
    fontSize: '0.8rem',
    fontWeight: '900',
    color: isLive ? '#ef4444' : '#8E8E93',
    letterSpacing: '0.1em',
    textTransform: 'uppercase',
  }),
  liveDot: (isLive: boolean) => ({
    width: '6px',
    height: '6px',
    borderRadius: '50%',
    backgroundColor: isLive ? '#ef4444' : '#71717A',
    boxShadow: isLive ? '0 0 10px #ef4444' : 'none',
  }),
  verificationBadge: (isVerified: boolean) => ({
    fontSize: '0.75rem',
    fontWeight: '700',
    color: isVerified ? '#4ade80' : '#fbbf24',
    padding: '0.5rem 1rem',
    backgroundColor: isVerified ? 'rgba(34, 197, 94, 0.1)' : 'rgba(234, 179, 8, 0.1)',
    borderRadius: '12px',
    border: `1px solid ${isVerified ? 'rgba(34, 197, 94, 0.2)' : 'rgba(234, 179, 8, 0.2)'}`,
  }),
  liveHeroHeadline: {
    fontSize: '3.5rem',
    fontWeight: '800',
    lineHeight: '1',
    marginBottom: '2.5rem',
    letterSpacing: '-0.06em',
    color: '#fff',
    maxWidth: '600px',
    textShadow: '0 4px 20px rgba(0,0,0,0.4)',
  },
  liveHeroActions: {
    display: 'flex',
    gap: '1rem',
  },
  heroPrimaryBtn: {
    padding: '1.125rem 2.5rem',
    backgroundColor: '#fff',
    color: '#000',
    borderRadius: '18px',
    fontSize: '1rem',
    fontWeight: '900',
    cursor: 'pointer',
    border: 'none',
    boxShadow: '0 20px 40px rgba(0,0,0,0.4)',
    transition: 'all 0.4s cubic-bezier(0.16, 1, 0.3, 1)',
  },
  heroSecondaryBtn: {
    padding: '1rem 2rem',
    backgroundColor: 'rgba(255,255,255,0.03)',
    color: '#fff',
    borderRadius: '16px',
    fontSize: '0.9rem',
    fontWeight: '700',
    cursor: 'pointer',
    border: '1px solid rgba(255,255,255,0.08)',
    transition: 'all 0.2s ease',
  },
  quickActionRow: {
    display: 'grid',
    gridTemplateColumns: 'repeat(auto-fit, minmax(180px, 1fr))',
    gap: '0.75rem',
    marginBottom: '2.5rem',
  },
  quickActionBtn: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.875rem',
    padding: '0.75rem 1.125rem',
    backgroundColor: '#08080A',
    borderRadius: '16px',
    border: '1px solid rgba(255,255,255,0.04)',
    cursor: 'pointer',
    transition: 'all 0.2s ease',
    textAlign: 'left',
  },
  actionIcon: {
    fontSize: '1.125rem',
    width: '32px',
    height: '32px',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: 'rgba(255,255,255,0.02)',
    borderRadius: '10px',
  },
  actionText: {
    display: 'flex',
    flexDirection: 'column',
  },
  actionLabel: {
    fontSize: '0.875rem',
    fontWeight: '700',
    color: '#fff',
    lineHeight: '1.1',
  },
  actionDesc: {
    fontSize: '0.65rem',
    color: '#52525B',
    fontWeight: '600',
    lineHeight: '1.1',
  },
  hubGrid: {
    display: 'grid',
    gridTemplateColumns: '1.8fr 1fr',
    gap: '1.5rem',
  },
  gridColMain: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  gridColSide: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1.5rem',
  },
  hubCard: {
    backgroundColor: '#0C0C0E',
    borderRadius: '24px',
    border: '1px solid rgba(255,255,255,0.05)',
    overflow: 'hidden',
    boxShadow: '0 10px 30px rgba(0,0,0,0.2)',
  },
  cardHeader: {
    padding: '1.5rem',
    borderBottom: '1px solid rgba(255,255,255,0.05)',
  },
  cardTitle: {
    fontSize: '0.9rem',
    fontWeight: '800',
    color: '#71717A',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
    margin: 0,
  },
  publishForm: {
    padding: '1.5rem',
  },
  publishTextarea: {
    width: '100%',
    backgroundColor: 'transparent',
    border: 'none',
    outline: 'none',
    color: '#fff',
    fontSize: '1rem',
    minHeight: '80px',
    resize: 'none',
    padding: 0,
    marginBottom: '1rem',
  },
  publishFooter: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    paddingTop: '1rem',
    borderTop: '1px solid rgba(255,255,255,0.03)',
  },
  publishHint: {
    fontSize: '0.75rem',
    color: '#3F3F46',
    fontWeight: '500',
  },
  publishBtn: {
    padding: '0.5rem 1.5rem',
    backgroundColor: '#6366f1',
    color: '#fff',
    borderRadius: '10px',
    fontSize: '0.875rem',
    fontWeight: '700',
    border: 'none',
    cursor: 'pointer',
    boxShadow: '0 5px 15px rgba(99, 102, 241, 0.2)',
  },
  activityLedger: {
    display: 'flex',
    flexDirection: 'column',
  },
  ledgerItem: {
    padding: '1.25rem 1.5rem',
    borderBottom: '1px solid rgba(255,255,255,0.02)',
  },
  ledgerMeta: {
    display: 'flex',
    justifyContent: 'space-between',
    marginBottom: '0.5rem',
  },
  ledgerDate: {
    fontSize: '0.7rem',
    fontWeight: '700',
    color: '#3F3F46',
  },
  ledgerBadge: {
    fontSize: '0.6rem',
    fontWeight: '900',
    color: '#6366f1',
    backgroundColor: 'rgba(99, 102, 241, 0.1)',
    padding: '2px 6px',
    borderRadius: '4px',
  },
  ledgerContent: {
    fontSize: '0.875rem',
    color: '#A1A1AA',
    lineHeight: '1.5',
    margin: 0,
  },
  ledgerLoading: {
    padding: '3rem',
    textAlign: 'center',
    color: '#3F3F46',
    fontSize: '0.875rem',
    fontStyle: 'italic',
  },
  statsStrip: {
    padding: '2rem 1.5rem',
    display: 'flex',
    alignItems: 'center',
    gap: '2.5rem',
  },
  statDivider: {
    width: '1px',
    height: '2.5rem',
    backgroundColor: 'rgba(255, 255, 255, 0.08)',
  },
  statItem: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.125rem',
  },
  statItemLabel: {
    fontSize: '0.65rem',
    fontWeight: '800',
    color: 'rgba(255, 255, 255, 0.25)',
    textTransform: 'uppercase',
    letterSpacing: '0.15em',
  },
  statItemValue: {
    fontSize: '2.5rem',
    fontWeight: '900',
    color: '#fff',
    letterSpacing: '-0.05em',
    lineHeight: '1',
    fontVariantNumeric: 'tabular-nums',
  },
  payoutStatus: {
    padding: '1.5rem',
  },
  payoutAlert: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
  },
  payoutAlertText: {
    fontSize: '0.875rem',
    color: '#71717A',
    lineHeight: '1.4',
    margin: 0,
  },
  payoutWarning: {
    display: 'flex',
    flexDirection: 'column',
    gap: '1rem',
  },
  setupBtn: {
    padding: '0.75rem',
    backgroundColor: 'rgba(255,255,255,0.03)',
    border: '1px solid rgba(255,255,255,0.1)',
    borderRadius: '12px',
    color: '#fff',
    fontSize: '0.875rem',
    fontWeight: '700',
    cursor: 'pointer',
  },
  payoutVerified: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
  },
  verifiedTag: {
    display: 'flex',
    alignItems: 'center',
    gap: '0.5rem',
    color: '#10b981',
    fontSize: '0.7rem',
    fontWeight: '900',
    letterSpacing: '0.05em',
  },
  verifiedDot: {
    width: '6px',
    height: '6px',
    backgroundColor: '#10b981',
    borderRadius: '50%',
    boxShadow: '0 0 8px #10b981',
  },
  payoutSuccessText: {
    fontSize: '0.875rem',
    color: '#71717A',
    margin: 0,
  },
};

export default CreatorDashboard;
