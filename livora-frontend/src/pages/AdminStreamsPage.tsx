import React, { useEffect, useRef, useState } from 'react';
import adminService from '../api/adminService';
import SEO from '../components/SEO';
import { useAuth } from '../auth/useAuth';
import { useNavigate } from 'react-router-dom';
import { showToast } from '../components/Toast';
import AdminAvatar from '../components/admin/AdminAvatar';

// ─── helpers ────────────────────────────────────────────────────────────────

function formatDuration(seconds: number): string {
  if (!seconds || seconds < 0) return '0:00';
  const h = Math.floor(seconds / 3600);
  const m = Math.floor((seconds % 3600) / 60);
  const s = seconds % 60;
  if (h > 0) return `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
  return `${m}:${String(s).padStart(2, '0')}`;
}


function riskColor(score: number): string {
  if (score >= 70) return 'text-red-400';
  if (score >= 40) return 'text-amber-400';
  return 'text-emerald-400';
}

function riskLabel(score: number): string {
  if (score >= 70) return 'HIGH';
  if (score >= 40) return 'MED';
  return 'LOW';
}

// ─── live duration ticker ────────────────────────────────────────────────────

function useLiveDuration(startedAt: string | null, initialSeconds: number) {
  const [seconds, setSeconds] = useState(initialSeconds);
  useEffect(() => {
    const interval = setInterval(() => {
      if (startedAt) {
        const elapsed = Math.floor((Date.now() - new Date(startedAt).getTime()) / 1000);
        setSeconds(elapsed);
      } else {
        setSeconds(s => s + 1);
      }
    }, 1000);
    return () => clearInterval(interval);
  }, [startedAt]);
  return seconds;
}

// ─── confirmation modal ──────────────────────────────────────────────────────

interface ConfirmModalProps {
  stream: any;
  onConfirm: () => void;
  onCancel: () => void;
  stopping: boolean;
}

const ConfirmModal: React.FC<ConfirmModalProps> = ({ stream, onConfirm, onCancel, stopping }) => (
  <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
    <div className="absolute inset-0 bg-black/70 backdrop-blur-sm" onClick={onCancel} />
    <div className="relative w-full max-w-md rounded-2xl border border-white/10 bg-[#1a1a1f] p-6 shadow-2xl">
      <div className="mb-4 flex h-12 w-12 items-center justify-center rounded-full bg-red-500/10 ring-1 ring-red-500/30">
        <svg className="h-6 w-6 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01M10.29 3.86L1.82 18a2 2 0 001.71 3h16.94a2 2 0 001.71-3L13.71 3.86a2 2 0 00-3.42 0z" />
        </svg>
      </div>
      <h3 className="mb-1 text-lg font-semibold text-white">Force Stop Stream</h3>
      <p className="mb-1 text-sm text-zinc-400">
        You are about to forcefully terminate the live stream by{' '}
        <span className="font-medium text-white">@{stream.creatorUsername}</span>.
      </p>
      <p className="mb-6 text-sm text-zinc-500">
        This action is irreversible and will immediately disconnect all viewers.
      </p>
      <div className="flex gap-3">
        <button
          onClick={onCancel}
          disabled={stopping}
          className="flex-1 rounded-xl border border-white/10 bg-white/5 py-2.5 text-sm font-medium text-zinc-300 transition hover:bg-white/10 disabled:opacity-50"
        >
          Cancel
        </button>
        <button
          onClick={onConfirm}
          disabled={stopping}
          className="flex-1 rounded-xl bg-red-600 py-2.5 text-sm font-semibold text-white transition hover:bg-red-500 disabled:opacity-50"
        >
          {stopping ? 'Stopping…' : 'Force Stop'}
        </button>
      </div>
    </div>
  </div>
);

// ─── stream card ─────────────────────────────────────────────────────────────

interface StreamCardProps {
  stream: any;
  onStop: (stream: any) => void;
  stoppingId: string | null;
}

const StreamCard: React.FC<StreamCardProps> = ({ stream, onStop, stoppingId }) => {
  const duration = useLiveDuration(stream.startedAt, stream.durationSeconds ?? 0);
  const isStopping = stoppingId === String(stream.streamId);

  return (
    <div className="group relative flex flex-col gap-4 rounded-2xl border border-white/[0.07] bg-[#151517] p-5 transition hover:border-white/[0.12] hover:bg-[#1a1a1f]">
      {/* thumbnail / avatar area */}
      <div className="relative flex h-36 items-center justify-center overflow-hidden rounded-xl bg-[#0f0f11]">
        <AdminAvatar name={stream.creatorUsername ?? '?'} src={stream.creatorAvatarUrl} size="lg" rounded="rounded-full" className="shadow-lg" />
        {/* LIVE badge */}
        <div className="absolute left-3 top-3 flex items-center gap-1.5 rounded-full bg-red-600/90 px-2.5 py-1 text-xs font-bold uppercase tracking-wider text-white shadow">
          <span className="relative flex h-2 w-2">
            <span className="absolute inline-flex h-full w-full animate-ping rounded-full bg-white opacity-75" />
            <span className="relative inline-flex h-2 w-2 rounded-full bg-white" />
          </span>
          LIVE
        </div>
        {/* private badge */}
        {stream.privateActive && (
          <div className="absolute right-3 top-3 rounded-full bg-violet-600/90 px-2.5 py-1 text-xs font-semibold text-white">
            Private
          </div>
        )}
        {/* duration overlay */}
        <div className="absolute bottom-3 right-3 rounded-lg bg-black/60 px-2 py-0.5 font-mono text-xs text-zinc-300">
          {formatDuration(duration)}
        </div>
      </div>

      {/* info */}
      <div className="flex flex-col gap-1">
        <p className="truncate font-semibold text-white">
          {stream.title || `${stream.creatorUsername}'s Stream`}
        </p>
        <p className="text-sm text-zinc-400">@{stream.creatorUsername}</p>
      </div>

      {/* stats row */}
      <div className="flex items-center gap-4 text-sm">
        <div className="flex items-center gap-1.5 text-zinc-400">
          <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
          </svg>
          <span className="font-medium text-white">{stream.viewerCount ?? 0}</span>
        </div>
        <div className="flex items-center gap-1.5">
          <span className="text-zinc-500">Risk:</span>
          <span className={`font-semibold ${riskColor(stream.fraudRiskScore ?? 0)}`}>
            {riskLabel(stream.fraudRiskScore ?? 0)} ({stream.fraudRiskScore ?? 0})
          </span>
        </div>
        {stream.activeSpyCount > 0 && (
          <div className="flex items-center gap-1 text-zinc-400">
            <svg className="h-3.5 w-3.5" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M13.875 18.825A10.05 10.05 0 0112 19c-4.478 0-8.268-2.943-9.543-7a9.97 9.97 0 011.563-3.029m5.858.908a3 3 0 114.243 4.243M9.878 9.878l4.242 4.242M9.88 9.88l-3.29-3.29m7.532 7.532l3.29 3.29M3 3l3.59 3.59m0 0A9.953 9.953 0 0112 5c4.478 0 8.268 2.943 9.543 7a10.025 10.025 0 01-4.132 5.411m0 0L21 21" />
            </svg>
            <span className="text-xs">{stream.activeSpyCount} spy</span>
          </div>
        )}
      </div>

      {/* force stop */}
      <button
        onClick={() => onStop(stream)}
        disabled={isStopping}
        className="mt-auto w-full rounded-xl border border-red-500/30 bg-red-500/10 py-2.5 text-sm font-semibold text-red-400 transition hover:border-red-500/60 hover:bg-red-500/20 hover:text-red-300 disabled:cursor-not-allowed disabled:opacity-50"
      >
        {isStopping ? (
          <span className="flex items-center justify-center gap-2">
            <svg className="h-4 w-4 animate-spin" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
            </svg>
            Stopping…
          </span>
        ) : (
          '⏹ Force Stop'
        )}
      </button>
    </div>
  );
};

// ─── empty state ─────────────────────────────────────────────────────────────

const EmptyState: React.FC = () => (
  <div className="flex flex-col items-center justify-center py-24 text-center">
    <div className="mb-4 flex h-16 w-16 items-center justify-center rounded-2xl bg-white/5 ring-1 ring-white/10">
      <svg className="h-8 w-8 text-zinc-500" fill="none" viewBox="0 0 24 24" stroke="currentColor">
        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={1.5} d="M15 10l4.553-2.069A1 1 0 0121 8.82v6.36a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h10a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
      </svg>
    </div>
    <p className="text-base font-medium text-zinc-300">No active streams</p>
    <p className="mt-1 text-sm text-zinc-600">All quiet — no creators are live right now.</p>
  </div>
);

// ─── main page ────────────────────────────────────────────────────────────────

const AdminStreamsPage: React.FC = () => {
  const [streams, setStreams] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [error, setError] = useState<string | null>(null);
  const [stoppingId, setStoppingId] = useState<string | null>(null);
  const [confirmStream, setConfirmStream] = useState<any | null>(null);
  const { user } = useAuth();
  const navigate = useNavigate();
  const intervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  useEffect(() => {
    if (user?.role !== 'ADMIN') { navigate('/'); return; }
    fetchActiveStreams(0);
    intervalRef.current = setInterval(() => fetchActiveStreams(page), 30_000);
    return () => { if (intervalRef.current) clearInterval(intervalRef.current); };
  }, [user, navigate]);

  useEffect(() => {
    fetchActiveStreams(page);
  }, [page]);

  const fetchActiveStreams = async (pageNum: number) => {
    try {
      setLoading(true);
      const data = await adminService.getStreams(pageNum, 20);
      setStreams(data.content || []);
      setTotalPages(data.totalPages || 1);
      setError(null);
    } catch (err) {
      setError('Failed to load active streams');
      console.error(err);
    } finally {
      setLoading(false);
    }
  };

  const handleStopConfirmed = async () => {
    if (!confirmStream) return;
    const streamId = String(confirmStream.streamId);
    try {
      setStoppingId(streamId);
      await adminService.stopStream(streamId);
      await fetchActiveStreams(page);
      showToast('Stream stopped successfully', 'success');
    } catch (err) {
      console.error(err);
      showToast('Failed to stop stream', 'error');
    } finally {
      setStoppingId(null);
      setConfirmStream(null);
    }
  };

  const totalViewers = streams.reduce((sum, s) => sum + (s.viewerCount ?? 0), 0);
  const avgDuration = streams.length
    ? Math.floor(streams.reduce((sum, s) => sum + (s.durationSeconds ?? 0), 0) / streams.length)
    : 0;

  return (
    <div className="min-h-screen bg-[#0f0f11] text-white">
      <SEO title="Admin — Active Streams" />

      {confirmStream && (
        <ConfirmModal
          stream={confirmStream}
          onConfirm={handleStopConfirmed}
          onCancel={() => setConfirmStream(null)}
          stopping={stoppingId === String(confirmStream.streamId)}
        />
      )}

      <div className="mx-auto max-w-7xl px-4 py-10 sm:px-6 lg:px-8">

        {/* header */}
        <div className="mb-8 flex flex-col gap-1 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <p className="text-xs font-medium uppercase tracking-widest text-zinc-500">Admin</p>
            <h1 className="text-2xl font-bold text-white">Active Streams</h1>
          </div>
          <button
            onClick={() => fetchActiveStreams(page)}
            className="flex items-center gap-2 self-start rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm text-zinc-300 transition hover:bg-white/10 sm:self-auto"
          >
            <svg className="h-4 w-4" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M4 4v5h.582m15.356 2A8.001 8.001 0 004.582 9m0 0H9m11 11v-5h-.581m0 0a8.003 8.003 0 01-15.357-2m15.357 2H15" />
            </svg>
            Refresh
          </button>
        </div>

        {/* stats row */}
        <div className="mb-8 grid grid-cols-1 gap-4 sm:grid-cols-3">
          {[
            {
              label: 'Live Streams',
              value: loading ? '—' : streams.length,
              icon: (
                <svg className="h-5 w-5 text-red-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 10l4.553-2.069A1 1 0 0121 8.82v6.36a1 1 0 01-1.447.894L15 14M3 8a2 2 0 012-2h10a2 2 0 012 2v8a2 2 0 01-2 2H5a2 2 0 01-2-2V8z" />
                </svg>
              ),
              accent: 'ring-red-500/20',
            },
            {
              label: 'Total Viewers',
              value: loading ? '—' : totalViewers.toLocaleString(),
              icon: (
                <svg className="h-5 w-5 text-indigo-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15 12a3 3 0 11-6 0 3 3 0 016 0z" />
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M2.458 12C3.732 7.943 7.523 5 12 5c4.478 0 8.268 2.943 9.542 7-1.274 4.057-5.064 7-9.542 7-4.477 0-8.268-2.943-9.542-7z" />
                </svg>
              ),
              accent: 'ring-indigo-500/20',
            },
            {
              label: 'Avg Duration',
              value: loading ? '—' : formatDuration(avgDuration),
              icon: (
                <svg className="h-5 w-5 text-emerald-400" fill="none" viewBox="0 0 24 24" stroke="currentColor">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                </svg>
              ),
              accent: 'ring-emerald-500/20',
            },
          ].map(stat => (
            <div
              key={stat.label}
              className={`flex items-center gap-4 rounded-2xl border border-white/[0.07] bg-[#151517] p-5 ring-1 ${stat.accent}`}
            >
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-white/5">
                {stat.icon}
              </div>
              <div>
                <p className="text-xs text-zinc-500">{stat.label}</p>
                <p className="text-xl font-bold text-white">{stat.value}</p>
              </div>
            </div>
          ))}
        </div>

        {/* error */}
        {error && (
          <div className="mb-6 flex items-center gap-3 rounded-xl border border-red-500/20 bg-red-500/10 px-4 py-3 text-sm text-red-400">
            <svg className="h-4 w-4 shrink-0" fill="none" viewBox="0 0 24 24" stroke="currentColor">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4m0 4h.01M21 12a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
            {error}
          </div>
        )}

        {/* grid */}
        {loading ? (
          <div className="flex items-center justify-center py-24">
            <svg className="h-8 w-8 animate-spin text-indigo-500" fill="none" viewBox="0 0 24 24">
              <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
              <path className="opacity-75" fill="currentColor" d="M4 12a8 8 0 018-8v8H4z" />
            </svg>
          </div>
        ) : streams.length === 0 ? (
          <EmptyState />
        ) : (
          <div className="grid grid-cols-1 gap-5 sm:grid-cols-2 lg:grid-cols-3 xl:grid-cols-4">
            {streams.map(stream => (
              <StreamCard
                key={String(stream.streamId)}
                stream={stream}
                onStop={setConfirmStream}
                stoppingId={stoppingId}
              />
            ))}
          </div>
        )}

        {/* pagination */}
        {totalPages > 1 && (
          <div className="mt-8 flex items-center justify-center gap-3">
            <button
              disabled={page <= 0}
              onClick={() => setPage(p => p - 1)}
              className="rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm text-zinc-300 transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
            >
              ← Previous
            </button>
            <span className="text-sm text-zinc-500">
              Page <span className="font-medium text-zinc-300">{page + 1}</span> of{' '}
              <span className="font-medium text-zinc-300">{totalPages}</span>
            </span>
            <button
              disabled={page + 1 >= totalPages}
              onClick={() => setPage(p => p + 1)}
              className="rounded-xl border border-white/10 bg-white/5 px-4 py-2 text-sm text-zinc-300 transition hover:bg-white/10 disabled:cursor-not-allowed disabled:opacity-40"
            >
              Next →
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

export default AdminStreamsPage;
