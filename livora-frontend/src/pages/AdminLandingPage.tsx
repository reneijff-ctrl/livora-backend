import React, { useEffect, useState, useCallback, useRef } from 'react';
import SEO from '../components/SEO';
import { useAuth } from '../auth/useAuth';
import { Link } from 'react-router-dom';
import adminService from '../api/adminService';
import MetricCard from '../components/admin/MetricCard';
import AdminActivityFeed from '../components/admin/AdminActivityFeed';
import AdminSystemHealthBar from '../components/admin/AdminSystemHealthBar';
import AdminPlatformCharts from '../components/admin/AdminPlatformCharts';
import AdminAlertBanner from '../components/admin/AdminAlertBanner';
import AdminLiveStreamsWidget from '../components/admin/AdminLiveStreamsWidget';
import MediasoupMonitoringWidget from '../components/admin/MediasoupMonitoringWidget';
import AdminFraudMonitoringWidget from '../components/admin/AdminFraudMonitoringWidget';
import AdminModerationEventsFeed from '../components/admin/AdminModerationEventsFeed';
import LiveStreamRiskMonitor from "../components/admin/LiveStreamRiskMonitor";
import RealtimeAbuseRadar from '../components/admin/RealtimeAbuseRadar';
import AIModerationRadar from "../components/admin/AIModerationRadar";
import { webSocketService } from '../websocket/webSocketService';
import { AdminRealtimeEventDTO, MediasoupStats, FraudDashboardMetrics, LiveStreamInfo } from '../types';

let cachedStreams: LiveStreamInfo[] = [];


const DEFAULT_METRICS = {
  totalCreators: 0,
  verifiedCreators: 0,
  pendingApplications: 0,
  activeFreezes: 0,
  pendingPayouts: 0,
  pendingAmount: 0,
  onlineUsers: 0,
  activeStreams: 0,
  newUsersToday: 0,
  todayRevenue: 0,
  openReports: 0,
  auditEvents24h: 0,
  websocketSessions: 0
};

const AdminLandingPage: React.FC = () => {
  const { user, authLoading, isAuthenticated } = useAuth();

  const [metrics, setMetrics] = useState<any>(DEFAULT_METRICS);
  const [activity, setActivity] = useState<any[]>([]);
  const [charts, setCharts] = useState<any>(null);
  const [health, setHealth] = useState<any>(null);
  const [systemHealth, setSystemHealth] = useState<any>(null);
  const [mediasoupStats, setMediasoupStats] = useState<MediasoupStats | null>(null);
  const [fraudStats, setFraudStats] = useState<FraudDashboardMetrics | null>(null);
  const [streams, setStreams] = useState<LiveStreamInfo[]>(cachedStreams);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [lastUpdated, setLastUpdated] = useState<Date | null>(null);

  const isFetchingRef = useRef<boolean>(false);
  const timerRef = useRef<NodeJS.Timeout | null>(null);

  const handleAdminEvent = useCallback((event: AdminRealtimeEventDTO) => {
    setMetrics((prev: any) => {
      if (!prev) return prev;
      const next = { ...prev };
      
      switch (event.type) {
        case 'USER_REGISTRATION':
          next.newUsersToday = (next.newUsersToday || 0) + 1;
          break;
        case 'CREATOR_APPLICATION':
          next.pendingApplications = (next.pendingApplications || 0) + 1;
          break;
        case 'PAYMENT_COMPLETED':
          if (event.metadata && event.metadata.amount) {
            const amount = typeof event.metadata.amount === 'string' 
              ? parseFloat(event.metadata.amount) 
              : event.metadata.amount;
            next.todayRevenue = (next.todayRevenue || 0) + amount;
          }
          break;
        case 'STREAM_STARTED':
          next.activeStreams = (next.activeStreams || 0) + 1;
          break;
        case 'STREAM_STOPPED':
          next.activeStreams = Math.max(0, (next.activeStreams || 0) - 1);
          break;
      }
      
      return next;
    });

    // Separately update streams state to keep it persistent and handle metadata
    if (event.type === 'STREAM_STARTED' && event.metadata) {
      const { sessionId, username, viewerCount, startedAt, userId, creatorId, creator, fraudRiskScore, messageRate } = event.metadata;
      const streamId = sessionId || event.metadata.id;
      
      setStreams(prev => {
        if (prev.find(s => s.id === streamId)) return prev;
        const newStream: LiveStreamInfo = {
          id: streamId,
          username: username,
          viewerCount: viewerCount || 0,
          startedAt: startedAt,
          userId: userId || creatorId || creator,
          creatorId: creatorId || userId || creator,
          fraudRiskScore: fraudRiskScore,
          messageRate: messageRate
        };
        const nextStreams = [...prev, newStream];
        cachedStreams = nextStreams;
        return nextStreams;
      });
    } else if (event.type === 'STREAM_STOPPED' && event.metadata) {
      const { sessionId, streamId, creatorId } = event.metadata;
      setStreams(prev => {
        const nextStreams = prev.filter(s => {
          // Match by streamId (UUID), sessionId (legacy Long), or creatorId as fallback
          if (streamId && s.id === streamId) return false;
          if (sessionId && s.id === sessionId) return false;
          if (creatorId && (s.creatorId === creatorId || s.userId === creatorId)) return false;
          return true;
        });
        cachedStreams = nextStreams;
        return nextStreams;
      });
    }
  }, []);

  const fetchDashboardData = useCallback(async () => {
    if (isFetchingRef.current) {
      return;
    }

    // Prevent API calls when user is not authenticated
    if (!isAuthenticated) {
      return;
    }
    
    console.log("Admin dashboard fetch started", {
      authenticated: isAuthenticated,
      token: localStorage.getItem("token") ? "present" : "missing"
    });

    isFetchingRef.current = true;
    setRefreshing(true);
    
    try {
      // Combined dashboard data reduces round-trips
      const results = await Promise.allSettled([
        adminService.getDashboardData(),
        adminService.getActivity(),
        adminService.getHealth(),
        adminService.getSystemHealth(),
        adminService.getMediasoupStats(),
        adminService.getFraudDashboard(),
        adminService.getActiveStreams()
      ]);
      
      if (results[0].status === 'fulfilled' && results[0].value) {
        const { metrics: newMetrics, charts: newCharts } = results[0].value;
        setMetrics((prev: any) => JSON.stringify(prev) === JSON.stringify(newMetrics) ? prev : newMetrics);
        setCharts((prev: any) => JSON.stringify(prev) === JSON.stringify(newCharts) ? prev : newCharts);
      } else {
        console.error('Failed to load dashboard metrics/charts, using fallback values', 
          results[0].status === 'rejected' ? (results[0] as PromiseRejectedResult).reason : 'Received empty data');
        setMetrics((prev: any) => prev || DEFAULT_METRICS);
      }
      
      if (results[1].status === 'fulfilled') {
        const newActivity = results[1].value;
        setActivity((prev: any) => JSON.stringify(prev) === JSON.stringify(newActivity) ? prev : newActivity);
      }
      if (results[2].status === 'fulfilled') {
        const newHealth = results[2].value;
        setHealth((prev: any) => JSON.stringify(prev) === JSON.stringify(newHealth) ? prev : newHealth);
      }
      if (results[3].status === 'fulfilled') {
        const newSystemHealth = results[3].value;
        setSystemHealth((prev: any) => JSON.stringify(prev) === JSON.stringify(newSystemHealth) ? prev : newSystemHealth);
      }
      
      if (results[4] && results[4].status === 'fulfilled') {
        const newMediasoupStats = results[4].value;
        setMediasoupStats((prev: any) => JSON.stringify(prev) === JSON.stringify(newMediasoupStats) ? prev : newMediasoupStats);
      }

      if (results[5] && results[5].status === 'fulfilled') {
        const newFraudStats = results[5].value;
        setFraudStats((prev: any) => JSON.stringify(prev) === JSON.stringify(newFraudStats) ? prev : newFraudStats);
      }

      if (results[6] && results[6].status === 'fulfilled' && Array.isArray(results[6].value)) {
        const data = results[6].value;
        
        // Only update cachedStreams if the API returned a valid array AND it contains stream data
        // or if we currently have nothing in cache.
        if (data.length > 0 || cachedStreams.length === 0) {
          const mapped = data.map((s: any) => ({
            id: s.id || s.streamId,
            username: s.username || s.creatorUsername,
            viewerCount: s.viewerCount || 0,
            startedAt: s.startedAt,
            userId: s.userId || s.creatorId || s.creator,
            creatorId: s.creatorId || s.userId || s.creator,
            slowMode: s.slowMode,
            fraudRiskScore: s.fraudRiskScore,
            messageRate: s.messageRate,
            privateActive: s.privateActive ?? false,
            privatePricePerMinute: s.privatePricePerMinute ?? null,
            spyEnabled: s.spyEnabled ?? false,
            activeSpyCount: s.activeSpyCount ?? 0
          }));
          setStreams(mapped);
          cachedStreams = mapped;
        }
      }
      
      setLastUpdated(new Date());
    } catch (err) {
      console.error('Failed to load dashboard data', err);
      // Ensure metrics is not null even on total failure to allow rendering
      setMetrics((prev: any) => prev || DEFAULT_METRICS);
    } finally {
      isFetchingRef.current = false;
      setRefreshing(false);
      setLoading(false);
    }
  }, [isAuthenticated]);

  const startPolling = useCallback(() => {
    if (timerRef.current) return;
    timerRef.current = setInterval(() => {
      if (!document.hidden && isAuthenticated) {
        fetchDashboardData();
      }
    }, 15000); // Poll every 15 seconds
  }, [fetchDashboardData, isAuthenticated]);

  const stopPolling = useCallback(() => {
    if (timerRef.current) {
      clearInterval(timerRef.current);
      timerRef.current = null;
    }
  }, []);

  // Safety timeout: force loading to false after 4 seconds in case API never resolves
  useEffect(() => {
    const timeout = setTimeout(() => {
      setLoading(false);
    }, 4000);

    return () => clearTimeout(timeout);
  }, []);

  useEffect(() => {
    if (authLoading) return;
    
    if (!isAuthenticated || !user || user.role !== 'ADMIN') {
      setLoading(false);
      return;
    }

    const handleVisibilityChange = () => {
      if (document.hidden) {
        stopPolling();
      } else {
        fetchDashboardData();
        startPolling();
      }
    };

    setLoading(true);
    fetchDashboardData();
    
    if (!document.hidden) {
      startPolling();
    }

    // Subscribe to real-time admin events only when WS is connected
    let adminUnsub: (() => void) | null = null;

    const trySubscribe = () => {
      if (adminUnsub) return; // already subscribed
      if (webSocketService.isConnected()) {
        adminUnsub = webSocketService.subscribeToAdminEvents(handleAdminEvent);
      }
    };

    // Attempt immediately, then re-attempt on state changes
    trySubscribe();
    const unsubState = webSocketService.subscribeStateChange((connected) => {
      if (connected) trySubscribe();
    });

    document.addEventListener('visibilitychange', handleVisibilityChange);

    return () => {
      stopPolling();
      if (adminUnsub) adminUnsub();
      unsubState();
      document.removeEventListener('visibilitychange', handleVisibilityChange);
    };
  }, [authLoading, isAuthenticated, user?.role, fetchDashboardData, startPolling, stopPolling, handleAdminEvent]);

  const formatCurrency = useCallback((amount: number | string) => {
    const val = typeof amount === 'string' ? parseFloat(amount) : amount;
    return `€${(val || 0).toFixed(2)}`;
  }, []);

  return (
    <div className="min-h-screen bg-[#08080a] py-9 px-4 sm:px-6 lg:px-8">
      <SEO title="Admin Dashboard" />
      <div className="max-w-7xl mx-auto">
        <AdminAlertBanner />
        
        {/* Header Section */}
        <div className="bg-zinc-900/40 backdrop-blur-md border border-zinc-800 rounded-2xl p-8 mb-6 shadow-2xl">
          <div className="flex flex-col lg:flex-row lg:items-center justify-between gap-6 mb-6">
            <div>
              <div className="flex items-center gap-3 mb-1">
                <h1 className="text-3xl font-extrabold text-white tracking-tight">Admin Control Center</h1>
                {refreshing && (
                  <svg className="animate-spin h-5 w-5 text-indigo-500" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
                    <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
                    <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
                  </svg>
                )}
              </div>
              <p className="text-gray-400 text-lg">Real-time platform monitoring and moderation</p>
              {lastUpdated && (
                <p className="text-[10px] text-zinc-500 uppercase tracking-widest mt-2 font-semibold">
                  System Sync: {lastUpdated.toLocaleTimeString()}
                </p>
              )}
            </div>

            <div className="flex flex-wrap gap-3">
              {/* Online Users Badge */}
              <div className="bg-zinc-800/40 border border-zinc-700/50 rounded-full px-4 py-2 flex items-center gap-2.5 transition-all hover:bg-zinc-800/60">
                <div className="w-2 h-2 rounded-full bg-green-500 shadow-[0_0_8px_rgba(34,197,94,0.4)]"></div>
                <span className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Online Users</span>
                <span className="text-sm font-bold text-white leading-none">{(metrics?.onlineUsers || 0).toLocaleString()}</span>
              </div>
              
              {/* Active Streams Badge */}
              <div className="bg-zinc-800/40 border border-zinc-700/50 rounded-full px-4 py-2 flex items-center gap-2.5 transition-all hover:bg-zinc-800/60">
                <div className={`w-2 h-2 rounded-full bg-red-500 shadow-[0_0_8px_rgba(239,68,68,0.4)] ${(metrics?.activeStreams || 0) > 0 ? 'animate-pulse' : ''}`}></div>
                <span className="text-xs font-medium text-zinc-400 uppercase tracking-wider">Active Streams</span>
                <span className="text-sm font-bold text-white leading-none">{(metrics?.activeStreams || 0).toLocaleString()}</span>
              </div>

              {/* Open Reports Badge */}
              <Link to="/admin/reports" className="bg-zinc-800/40 border border-zinc-700/50 rounded-full px-4 py-2 flex items-center gap-2.5 transition-all hover:bg-zinc-800/60 no-underline group">
                <div className={`w-2 h-2 rounded-full bg-yellow-500 shadow-[0_0_8px_rgba(234,179,8,0.4)] ${(metrics?.openReports || 0) > 0 ? 'animate-pulse' : ''}`}></div>
                <span className="text-xs font-medium text-zinc-400 uppercase tracking-wider group-hover:text-zinc-300">Open Reports</span>
                <span className="text-sm font-bold text-white leading-none">{(metrics?.openReports || 0).toLocaleString()}</span>
              </Link>

              {/* Pending Applications Badge */}
              <Link to="/admin/applications" className="bg-zinc-800/40 border border-zinc-700/50 rounded-full px-4 py-2 flex items-center gap-2.5 transition-all hover:bg-zinc-800/60 no-underline group">
                <div className={`w-2 h-2 rounded-full bg-blue-500 shadow-[0_0_8px_rgba(59,130,246,0.4)] ${(metrics?.pendingApplications || 0) > 0 ? 'animate-pulse' : ''}`}></div>
                <span className="text-xs font-medium text-zinc-400 uppercase tracking-wider group-hover:text-zinc-300">Pending Apps</span>
                <span className="text-sm font-bold text-white leading-none">{(metrics?.pendingApplications || 0).toLocaleString()}</span>
              </Link>

              {/* Creator Verifications Badge */}
              <Link to="/admin/creator-verifications" className="bg-zinc-800/40 border border-zinc-700/50 rounded-full px-4 py-2 flex items-center gap-2.5 transition-all hover:bg-zinc-800/60 no-underline group">
                <div className="w-2 h-2 rounded-full bg-indigo-500 shadow-[0_0_8px_rgba(99,102,241,0.4)]"></div>
                <span className="text-xs font-medium text-zinc-400 uppercase tracking-wider group-hover:text-zinc-300">ID Verification</span>
                <span className="text-sm font-bold text-white leading-none">{(metrics?.pendingVerifications || 0).toLocaleString()}</span>
              </Link>
            </div>
          </div>
          
          <div className="flex flex-wrap gap-4 items-center justify-start border-t border-zinc-800/60 pt-4">
            <Link to="/admin/creators" className="px-5 py-2.5 bg-indigo-600 hover:bg-indigo-500 text-white rounded-xl text-sm font-semibold transition-all shadow-lg shadow-indigo-600/20 no-underline">
              Manage Creators
            </Link>
            <Link to="/admin/creator-verifications" className="px-5 py-2.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded-xl text-sm font-semibold transition-all border border-zinc-700 no-underline">
              Review Verifications
            </Link>
            <Link to="/admin/applications" className="px-5 py-2.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded-xl text-sm font-semibold transition-all border border-zinc-700 no-underline">
              Manage Applications
            </Link>
            <Link to="/admin/reports" className="px-5 py-2.5 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 rounded-xl text-sm font-semibold transition-all border border-zinc-700 no-underline">
              Manage Reports
            </Link>
          </div>
        </div>

        {loading && (
          <div className="flex flex-col items-center justify-center py-20 text-zinc-500">
            <svg className="animate-spin h-8 w-8 text-zinc-600 mb-4" xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4"></circle>
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"></path>
            </svg>
            <span className="text-sm font-medium tracking-wide">Initializing Control Center...</span>
          </div>
        )}

        {!loading && (
          <div className="space-y-9">
            <AdminSystemHealthBar healthData={health} systemHealth={systemHealth} />
            
            {/* Platform Health */}
            <div>
              <div className="flex items-center gap-4 mb-6 mt-8 border-b border-zinc-800/60 pb-4">
                <span className="text-2xl">🖥</span>
                <div className="w-px h-8 bg-zinc-800/80"></div>
                <h2 className="text-2xl font-bold text-white tracking-tight">Platform Health</h2>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                <MetricCard title="Online Users" value={metrics.onlineUsers} type="success" />
                <MetricCard title="Active Sessions" value={metrics.websocketSessions} />
                <MetricCard title="Active Streams" value={metrics.activeStreams} to="/admin/streams" alert={metrics.activeStreams > 0} />
                <MetricCard title="Audit Events (24h)" value={metrics.auditEvents24h} />
              </div>
              
              {mediasoupStats && (
                <MediasoupMonitoringWidget stats={mediasoupStats} />
              )}
            </div>
            
            <AdminLiveStreamsWidget 
              streams={streams} 
              setStreams={setStreams} 
              loading={loading}
              onRefresh={fetchDashboardData}
            />
            
            {/* Business Metrics */}
            <div>
              <div className="flex items-center gap-4 mb-6 mt-8 border-b border-zinc-800/60 pb-4">
                <span className="text-2xl">📊</span>
                <div className="w-px h-8 bg-zinc-800/80"></div>
                <h2 className="text-2xl font-bold text-white tracking-tight">Business Metrics</h2>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                <MetricCard title="Total Creators" value={metrics.totalCreators} to="/admin/creators" />
                <MetricCard title="Verified Creators" value={metrics.verifiedCreators} type="success" />
                <MetricCard title="New Users Today" value={metrics.newUsersToday} type="neutral" />
                <MetricCard title="Revenue Today" value={formatCurrency(metrics.todayRevenue)} type="success" />
              </div>
            </div>

            {/* Historical Charts */}
            <AdminPlatformCharts chartsData={charts} />

            {/* Moderation & Platform Activity Tracking */}
            <div>
              <div className="flex items-center gap-4 mb-6 mt-8 border-b border-zinc-800/60 pb-4">
                <span className="text-2xl">🛡️</span>
                <div className="w-px h-8 bg-zinc-800/80"></div>
                <h2 className="text-2xl font-bold text-white tracking-tight">Moderation & Monitoring</h2>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
                <MetricCard title="Open Reports" value={metrics.openReports} to="/admin/reports" type={metrics.openReports > 0 ? 'warning' : 'neutral'} alert={metrics.openReports > 5} />
                <MetricCard title="Pending Applications" value={metrics.pendingApplications} to="/admin/applications" type={metrics.pendingApplications > 0 ? 'warning' : 'neutral'} alert={metrics.pendingApplications > 0} />
                <MetricCard title="Active Freezes" value={metrics.activeFreezes} type="danger" />
                <MetricCard title="Pending Payouts" value={metrics.pendingPayouts} type="warning" />
                <MetricCard title="Pending Amount" value={formatCurrency(metrics.pendingAmount)} type="warning" />
              </div>

              {fraudStats && (
                <div className="mt-6">
                  <AdminFraudMonitoringWidget metrics={fraudStats} />
                </div>
              )}

              <div className="mt-8">
                <AIModerationRadar />
              </div>

              <div className="mt-8">
                <LiveStreamRiskMonitor />
              </div>

              <div className="mt-8">
                <RealtimeAbuseRadar />
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-6 mt-8">
                <AdminModerationEventsFeed />
                <AdminActivityFeed events={activity} />
              </div>
            </div>
          </div>
        )}

        <div className="text-center text-zinc-800 text-4xl mt-12 opacity-20">⚙️</div>
      </div>
    </div>
  );
};

export default AdminLandingPage;
