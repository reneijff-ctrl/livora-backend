import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import adminService from '../../../api/adminService';
import SEO from '../../../components/SEO';
import AdminActionPanel from '../../../components/admin/creators/AdminActionPanel';
import CreatorStatusBadge from '../../../components/admin/creators/CreatorStatusBadge';
import { safeRender } from '@/utils/safeRender';
import AdminAvatar from '../../../components/admin/AdminAvatar';

type Tab = 'applications' | 'verifications';

function timeAgo(dateStr: string | null | undefined): string {
  if (!dateStr) return '—';
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  return `${days}d ago`;
}

function urgencyClass(dateStr: string | null | undefined): string {
  if (!dateStr) return '';
  const hrs = (Date.now() - new Date(dateStr).getTime()) / 3600000;
  if (hrs > 72) return 'border-l-4 border-l-red-500/70';
  if (hrs > 24) return 'border-l-4 border-l-amber-500/60';
  return 'border-l-4 border-l-transparent';
}


const CreatorQueuePage: React.FC = () => {
  const navigate = useNavigate();
  const [tab, setTab] = useState<Tab>('applications');
  const [items, setItems] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [expanded, setExpanded] = useState<number | null>(null);
  const [appCount, setAppCount] = useState<number>(0);
  const [verCount, setVerCount] = useState<number>(0);

  const load = useCallback(async () => {
    setLoading(true);
    setExpanded(null);
    try {
      const [apps, vers] = await Promise.allSettled([
        adminService.getApplicationQueue(),
        adminService.getVerificationQueue(),
      ]);
      const appData = apps.status === 'fulfilled' ? apps.value : [];
      const verData = vers.status === 'fulfilled' ? vers.value : [];
      setAppCount(appData.length);
      setVerCount(verData.length);
      setItems(tab === 'applications' ? appData : verData);
    } catch {
      setItems([]);
    } finally {
      setLoading(false);
    }
  }, [tab]);

  useEffect(() => { load(); }, [load]);

  const totalPending = appCount + verCount;

  return (
    <div className="min-h-screen" style={{ backgroundColor: '#0f0f11' }}>
      <SEO title="Admin — Action Queue" />

      <div className="max-w-5xl mx-auto px-6 py-8">

        {/* Breadcrumb */}
        <div className="flex items-center gap-2 text-sm mb-6" style={{ color: '#6b7280' }}>
          <button
            onClick={() => navigate('/admin/creators')}
            className="hover:text-white transition-colors"
          >
            Creators
          </button>
          <span>/</span>
          <span style={{ color: '#e5e7eb' }}>Action Queue</span>
        </div>

        {/* Header */}
        <div className="flex items-start justify-between mb-8 gap-4 flex-wrap">
          <div>
            <h1 className="text-2xl font-bold text-white mb-1">Action Queue</h1>
            <p className="text-sm" style={{ color: '#6b7280' }}>
              Review and process pending creator requests
            </p>
          </div>
          <button
            onClick={() => navigate('/admin/creators')}
            className="flex items-center gap-2 px-4 py-2 rounded-lg text-sm font-semibold transition-all"
            style={{ backgroundColor: '#1c1c1f', border: '1px solid #2a2a2e', color: '#9ca3af' }}
            onMouseEnter={e => {
              (e.currentTarget as HTMLButtonElement).style.color = '#fff';
              (e.currentTarget as HTMLButtonElement).style.borderColor = '#3f3f46';
            }}
            onMouseLeave={e => {
              (e.currentTarget as HTMLButtonElement).style.color = '#9ca3af';
              (e.currentTarget as HTMLButtonElement).style.borderColor = '#2a2a2e';
            }}
          >
            ← All Creators
          </button>
        </div>

        {/* Stats row */}
        <div className="grid grid-cols-3 gap-4 mb-8">
          {[
            { label: 'Pending Applications', value: appCount, color: '#6366f1' },
            { label: 'Pending Verifications', value: verCount, color: '#f59e0b' },
            { label: 'Total Awaiting Review', value: totalPending, color: totalPending > 0 ? '#ef4444' : '#10b981' },
          ].map(stat => (
            <div
              key={stat.label}
              className="rounded-xl p-4"
              style={{ backgroundColor: '#151517', border: '1px solid #1f1f23' }}
            >
              <p className="text-xs font-medium mb-2" style={{ color: '#6b7280' }}>{stat.label}</p>
              <p className="text-3xl font-bold" style={{ color: stat.color }}>{stat.value}</p>
            </div>
          ))}
        </div>

        {/* Segmented tab control */}
        <div
          className="flex p-1 rounded-xl mb-6 w-fit"
          style={{ backgroundColor: '#151517', border: '1px solid #1f1f23' }}
        >
          {([
            { key: 'applications' as Tab, label: 'Applications', count: appCount },
            { key: 'verifications' as Tab, label: 'Verifications', count: verCount },
          ]).map(t => (
            <button
              key={t.key}
              onClick={() => setTab(t.key)}
              className="flex items-center gap-2 px-5 py-2 rounded-lg text-sm font-semibold transition-all"
              style={
                tab === t.key
                  ? { backgroundColor: '#6366f1', color: '#fff', boxShadow: '0 1px 8px rgba(99,102,241,0.4)' }
                  : { backgroundColor: 'transparent', color: '#6b7280' }
              }
            >
              {t.label}
              {t.count > 0 && (
                <span
                  className="text-xs px-1.5 py-0.5 rounded-full font-bold"
                  style={
                    tab === t.key
                      ? { backgroundColor: 'rgba(255,255,255,0.2)', color: '#fff' }
                      : { backgroundColor: '#1f1f23', color: '#9ca3af' }
                  }
                >
                  {t.count}
                </span>
              )}
            </button>
          ))}
        </div>

        {/* Content */}
        {loading ? (
          <div className="flex items-center justify-center py-24">
            <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
          </div>
        ) : items.length === 0 ? (
          /* Empty state */
          <div
            className="flex flex-col items-center justify-center py-24 rounded-2xl"
            style={{ backgroundColor: '#151517', border: '1px solid #1f1f23' }}
          >
            <div
              className="w-16 h-16 rounded-2xl flex items-center justify-center mb-4 text-3xl"
              style={{ backgroundColor: '#1c1c1f' }}
            >
              ✅
            </div>
            <h3 className="text-lg font-semibold text-white mb-2">Queue is clear</h3>
            <p className="text-sm text-center max-w-xs" style={{ color: '#6b7280' }}>
              No pending {tab} to review right now. Check back later or switch tabs.
            </p>
            <button
              onClick={() => setTab(tab === 'applications' ? 'verifications' : 'applications')}
              className="mt-6 px-4 py-2 rounded-lg text-sm font-semibold transition-all"
              style={{ backgroundColor: '#1f1f23', color: '#9ca3af', border: '1px solid #2a2a2e' }}
            >
              Check {tab === 'applications' ? 'Verifications' : 'Applications'}
            </button>
          </div>
        ) : (
          <div className="flex flex-col gap-3">
            {items.map((item, idx) => {
              const submittedAt = tab === 'applications' ? item.applicationSubmittedAt : item.verificationSubmittedAt;
              const ago = timeAgo(submittedAt);
              const isOld = submittedAt && (Date.now() - new Date(submittedAt).getTime()) > 86400000;
              const isVeryOld = submittedAt && (Date.now() - new Date(submittedAt).getTime()) > 259200000;
              const isExpanded = expanded === item.userId;

              return (
                <div
                  key={item.userId}
                  className={`rounded-xl overflow-hidden transition-all ${urgencyClass(submittedAt)}`}
                  style={{ backgroundColor: '#151517', border: '1px solid #1f1f23' }}
                >
                  {/* Card header */}
                  <div
                    className="flex items-center gap-4 p-4 cursor-pointer"
                    style={{ borderBottom: isExpanded ? '1px solid #1f1f23' : 'none' }}
                    onClick={() => setExpanded(isExpanded ? null : item.userId)}
                  >
                    {/* Avatar */}
                    <AdminAvatar name={item.displayName || item.username || '?'} src={item.profileImage} seed={item.userId} size="md" rounded="rounded-full" />

                    {/* Info */}
                    <div className="flex-1 min-w-0">
                      <div className="flex items-center gap-2 flex-wrap">
                        <span className="font-semibold text-white text-sm">
                          {safeRender(item.displayName || item.username || '—')}
                        </span>
                        <CreatorStatusBadge status={item.status} />
                        {isVeryOld && (
                          <span className="text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: 'rgba(239,68,68,0.15)', color: '#f87171', border: '1px solid rgba(239,68,68,0.3)' }}>
                            ⚠ Overdue
                          </span>
                        )}
                        {isOld && !isVeryOld && (
                          <span className="text-xs px-2 py-0.5 rounded-full font-semibold" style={{ backgroundColor: 'rgba(245,158,11,0.15)', color: '#fbbf24', border: '1px solid rgba(245,158,11,0.3)' }}>
                            Waiting
                          </span>
                        )}
                      </div>
                      <p className="text-xs mt-0.5" style={{ color: '#6b7280' }}>
                        @{safeRender(item.username)} · {safeRender(item.email)}
                      </p>
                    </div>

                    {/* Right side */}
                    <div className="flex items-center gap-3 flex-shrink-0">
                      <div className="text-right hidden sm:block">
                        <p className="text-xs font-medium" style={{ color: isVeryOld ? '#f87171' : isOld ? '#fbbf24' : '#9ca3af' }}>
                          {ago}
                        </p>
                        <p className="text-xs" style={{ color: '#4b5563' }}>submitted</p>
                      </div>
                      <button
                        onClick={e => { e.stopPropagation(); navigate(`/admin/creators/${item.userId}`); }}
                        className="px-3 py-1.5 rounded-lg text-xs font-semibold transition-all"
                        style={{ backgroundColor: '#1c1c1f', border: '1px solid #2a2a2e', color: '#9ca3af' }}
                        onMouseEnter={e => {
                          (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#6366f1';
                          (e.currentTarget as HTMLButtonElement).style.color = '#fff';
                          (e.currentTarget as HTMLButtonElement).style.borderColor = '#6366f1';
                        }}
                        onMouseLeave={e => {
                          (e.currentTarget as HTMLButtonElement).style.backgroundColor = '#1c1c1f';
                          (e.currentTarget as HTMLButtonElement).style.color = '#9ca3af';
                          (e.currentTarget as HTMLButtonElement).style.borderColor = '#2a2a2e';
                        }}
                      >
                        Full Profile
                      </button>
                      <span className="text-xs" style={{ color: '#4b5563' }}>
                        {isExpanded ? '▲' : '▼'}
                      </span>
                    </div>
                  </div>

                  {/* Expanded panel */}
                  {isExpanded && (
                    <div className="p-4" style={{ backgroundColor: '#111113' }}>
                      {/* Verification documents */}
                      {tab === 'verifications' && (item.idDocumentUrl || item.selfieDocumentUrl) && (
                        <div className="flex gap-4 mb-4 flex-wrap">
                          {[
                            { url: item.idDocumentUrl, label: 'Front ID' },
                            { url: item.documentBackUrl, label: 'Back ID' },
                            { url: item.selfieDocumentUrl, label: 'Selfie' },
                          ].filter(d => d.url).map(doc => (
                            <div key={doc.label}>
                              <p className="text-xs font-medium mb-1.5" style={{ color: '#6b7280' }}>{doc.label}</p>
                              <a href={doc.url} target="_blank" rel="noreferrer" className="block group relative">
                                <img
                                  src={doc.url}
                                  alt={doc.label}
                                  className="rounded-lg object-cover transition-transform group-hover:scale-105"
                                  style={{ maxHeight: '130px', border: '1px solid #2a2a2e' }}
                                />
                                <div className="absolute inset-0 rounded-lg opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center" style={{ backgroundColor: 'rgba(0,0,0,0.5)' }}>
                                  <span className="text-white text-xs font-semibold">View</span>
                                </div>
                              </a>
                            </div>
                          ))}
                        </div>
                      )}
                      <AdminActionPanel creator={item} onActionComplete={load} />
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        )}
      </div>
    </div>
  );
};

export default CreatorQueuePage;
