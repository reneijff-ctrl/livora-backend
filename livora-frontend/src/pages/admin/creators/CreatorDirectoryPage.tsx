import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import adminService from '../../../api/adminService';
import SEO from '../../../components/SEO';
import CreatorStatusBadge from '../../../components/admin/creators/CreatorStatusBadge';
import AdminAvatar from '../../../components/admin/AdminAvatar';
import { safeRender } from '@/utils/safeRender';

const STATUSES = [
  { value: '',          label: 'All' },
  { value: 'DRAFT',     label: 'Draft' },
  { value: 'PENDING',   label: 'Pending' },
  { value: 'ACTIVE',    label: 'Active' },
  { value: 'SUSPENDED', label: 'Suspended' },
];

const STAT_CARDS = [
  { key: 'total',     label: 'Total Creators', color: 'text-white',        icon: '👥' },
  { key: 'pending',   label: 'Pending Review', color: 'text-amber-400',    icon: '⏳' },
  { key: 'active',    label: 'Active',         color: 'text-emerald-400',  icon: '✅' },
  { key: 'suspended', label: 'Suspended',      color: 'text-red-400',      icon: '🚫' },
];


const CreatorDirectoryPage: React.FC = () => {
  const navigate = useNavigate();
  const [creators, setCreators] = useState<any[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const [searchInput, setSearchInput] = useState('');
  const [loading, setLoading] = useState(true);
  const [stats, setStats] = useState({ total: 0, pending: 0, active: 0, suspended: 0 });
  const [hoveredRow, setHoveredRow] = useState<string | number | null>(null);

  const fetchCreators = useCallback(async () => {
    setLoading(true);
    try {
      const data = await adminService.getUnifiedCreators({
        status: status || undefined,
        search: search || undefined,
        page,
        size: 20,
      });
      setCreators(data.content);
      setTotal(data.totalElements);
    } catch {
      setCreators([]);
    } finally {
      setLoading(false);
    }
  }, [status, search, page]);

  const fetchStats = useCallback(async () => {
    try {
      const [all, pending, active, suspended] = await Promise.allSettled([
        adminService.getUnifiedCreators({ page: 0, size: 1 }),
        adminService.getUnifiedCreators({ status: 'PENDING',   page: 0, size: 1 }),
        adminService.getUnifiedCreators({ status: 'ACTIVE',    page: 0, size: 1 }),
        adminService.getUnifiedCreators({ status: 'SUSPENDED', page: 0, size: 1 }),
      ]);
      setStats({
        total:     all.status       === 'fulfilled' ? all.value.totalElements       : 0,
        pending:   pending.status   === 'fulfilled' ? pending.value.totalElements   : 0,
        active:    active.status    === 'fulfilled' ? active.value.totalElements    : 0,
        suspended: suspended.status === 'fulfilled' ? suspended.value.totalElements : 0,
      });
    } catch { /* silent */ }
  }, []);

  useEffect(() => { fetchCreators(); }, [fetchCreators]);
  useEffect(() => { fetchStats(); }, [fetchStats]);

  const totalPages = Math.ceil(total / 20);

  return (
    <div className="min-h-screen bg-[#0f0f11] text-white">
      <SEO title="Admin — Creator Directory" />

      <div className="max-w-7xl mx-auto px-6 py-10 space-y-8">

        {/* ── Header ── */}
        <div className="flex items-start justify-between gap-4 flex-wrap">
          <div>
            <p className="text-xs font-semibold uppercase tracking-widest text-zinc-500 mb-1">Admin</p>
            <h1 className="text-3xl font-bold tracking-tight text-white">Creator Directory</h1>
            <p className="mt-1 text-sm text-zinc-400">Browse, filter, and manage all creators on the platform.</p>
          </div>
          <button
            onClick={() => navigate('/admin/creators/queue')}
            className="inline-flex items-center gap-2 px-4 py-2.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-sm font-semibold transition-all duration-150 shadow-lg shadow-indigo-900/40 hover:shadow-indigo-800/60 active:scale-95"
          >
            Action Queue
            <span className="text-indigo-300">→</span>
          </button>
        </div>

        {/* ── Stats Row ── */}
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-4">
          {STAT_CARDS.map(card => (
            <div
              key={card.key}
              className="relative overflow-hidden rounded-2xl bg-[#151517] border border-white/5 px-5 py-4 shadow-sm"
            >
              <div className="absolute inset-0 bg-gradient-to-br from-white/[0.02] to-transparent pointer-events-none" />
              <p className="text-xs font-medium text-zinc-500 uppercase tracking-wider mb-2">{card.label}</p>
              <div className="flex items-end justify-between">
                <span className={`text-3xl font-bold tabular-nums ${card.color}`}>
                  {stats[card.key as keyof typeof stats].toLocaleString()}
                </span>
                <span className="text-2xl opacity-40">{card.icon}</span>
              </div>
            </div>
          ))}
        </div>

        {/* ── Filter Bar ── */}
        <div className="flex flex-wrap items-center gap-4">
          {/* Segmented control */}
          <div className="flex items-center bg-[#151517] border border-white/5 rounded-xl p-1 gap-0.5">
            {STATUSES.map(s => (
              <button
                key={s.value}
                onClick={() => { setStatus(s.value); setPage(0); }}
                className={`px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all duration-150 ${
                  status === s.value
                    ? 'bg-indigo-600 text-white shadow-sm'
                    : 'text-zinc-400 hover:text-zinc-200 hover:bg-white/5'
                }`}
              >
                {s.label}
              </button>
            ))}
          </div>

          {/* Search */}
          <form
            onSubmit={e => { e.preventDefault(); setSearch(searchInput); setPage(0); }}
            className="flex items-center gap-2 flex-1 min-w-[260px]"
          >
            <div className="relative flex-1">
              <span className="absolute left-3 top-1/2 -translate-y-1/2 text-zinc-500 text-sm">🔍</span>
              <input
                value={searchInput}
                onChange={e => setSearchInput(e.target.value)}
                placeholder="Search username or email…"
                className="w-full bg-[#151517] border border-white/5 rounded-xl pl-9 pr-4 py-2 text-sm text-zinc-200 placeholder-zinc-600 focus:outline-none focus:ring-1 focus:ring-indigo-500/60 transition"
              />
            </div>
            <button
              type="submit"
              className="px-4 py-2 rounded-xl bg-[#1e1e22] border border-white/5 text-zinc-300 text-sm font-semibold hover:bg-white/5 transition"
            >
              Search
            </button>
            {search && (
              <button
                type="button"
                onClick={() => { setSearch(''); setSearchInput(''); setPage(0); }}
                className="px-3 py-2 rounded-xl bg-[#1e1e22] border border-white/5 text-zinc-500 text-sm hover:text-zinc-300 transition"
              >
                ✕
              </button>
            )}
          </form>

          <span className="ml-auto text-xs text-zinc-600 tabular-nums hidden sm:block">
            {total.toLocaleString()} result{total !== 1 ? 's' : ''}
          </span>
        </div>

        {/* ── Table Card ── */}
        <div className="rounded-2xl bg-[#151517] border border-white/5 shadow-xl overflow-hidden">
          {loading ? (
            <div className="flex flex-col items-center justify-center py-24 gap-3">
              <div className="w-8 h-8 rounded-full border-2 border-indigo-500 border-t-transparent animate-spin" />
              <p className="text-sm text-zinc-500">Loading creators…</p>
            </div>
          ) : creators.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-24 gap-4">
              <div className="w-16 h-16 rounded-2xl bg-[#1e1e22] border border-white/5 flex items-center justify-center text-3xl">
                👤
              </div>
              <div className="text-center">
                <p className="text-zinc-300 font-semibold">No creators found</p>
                <p className="text-zinc-600 text-sm mt-1">
                  {search ? `No results for "${search}"` : 'Try adjusting your filters.'}
                </p>
              </div>
              {(search || status) && (
                <button
                  onClick={() => { setSearch(''); setSearchInput(''); setStatus(''); setPage(0); }}
                  className="px-4 py-2 rounded-xl bg-[#1e1e22] border border-white/5 text-zinc-400 text-sm hover:text-white transition"
                >
                  Clear filters
                </button>
              )}
            </div>
          ) : (
            <table className="w-full text-left text-sm">
              <thead>
                <tr className="border-b border-white/5">
                  <th className="px-5 py-3.5 text-xs font-semibold uppercase tracking-wider text-zinc-500">Creator</th>
                  <th className="px-5 py-3.5 text-xs font-semibold uppercase tracking-wider text-zinc-500 hidden md:table-cell">Email</th>
                  <th className="px-5 py-3.5 text-xs font-semibold uppercase tracking-wider text-zinc-500">Status</th>
                  <th className="px-5 py-3.5 text-xs font-semibold uppercase tracking-wider text-zinc-500 hidden lg:table-cell">Application</th>
                  <th className="px-5 py-3.5 text-xs font-semibold uppercase tracking-wider text-zinc-500 hidden lg:table-cell">Verification</th>
                  <th className="px-5 py-3.5 text-xs font-semibold uppercase tracking-wider text-zinc-500 text-right">Action</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-white/[0.04]">
                {creators.map(c => {
                  const displayName = c.displayName || c.username || 'Unknown';
                  const isHovered = hoveredRow === c.userId;
                  return (
                    <tr
                      key={c.userId}
                      onMouseEnter={() => setHoveredRow(c.userId)}
                      onMouseLeave={() => setHoveredRow(null)}
                      className={`transition-colors duration-100 ${isHovered ? 'bg-white/[0.03]' : ''}`}
                    >
                      {/* Creator cell */}
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-3">
                          <AdminAvatar name={displayName} src={c.profileImage} seed={c.userId} size="sm" />
                          <div>
                            <div className="font-semibold text-zinc-100 leading-tight">{safeRender(displayName)}</div>
                            <div className="text-xs text-zinc-500 mt-0.5">@{safeRender(c.username || c.userId)}</div>
                          </div>
                        </div>
                      </td>

                      {/* Email */}
                      <td className="px-5 py-4 text-zinc-400 hidden md:table-cell">
                        {safeRender(c.email)}
                      </td>

                      {/* Status */}
                      <td className="px-5 py-4">
                        <CreatorStatusBadge status={c.status} />
                      </td>

                      {/* Application */}
                      <td className="px-5 py-4 hidden lg:table-cell">
                        {c.applicationStatus
                          ? <CreatorStatusBadge status={c.applicationStatus} />
                          : <span className="text-zinc-600 text-xs">—</span>
                        }
                      </td>

                      {/* Verification */}
                      <td className="px-5 py-4 hidden lg:table-cell">
                        {c.verificationStatus
                          ? <CreatorStatusBadge status={c.verificationStatus} />
                          : <span className="text-zinc-600 text-xs">—</span>
                        }
                      </td>

                      {/* Action */}
                      <td className="px-5 py-4 text-right">
                        <button
                          onClick={() => navigate(`/admin/creators/${c.userId}`)}
                          className={`inline-flex items-center gap-1.5 px-3.5 py-1.5 rounded-lg text-xs font-semibold transition-all duration-150 border ${
                            isHovered
                              ? 'bg-indigo-600 border-indigo-500 text-white shadow-sm shadow-indigo-900/40'
                              : 'bg-transparent border-white/10 text-zinc-400 hover:border-white/20 hover:text-zinc-200'
                          }`}
                        >
                          View <span className="opacity-70">→</span>
                        </button>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          )}
        </div>

        {/* ── Pagination ── */}
        {!loading && totalPages > 1 && (
          <div className="flex items-center justify-center gap-3">
            <button
              disabled={page === 0}
              onClick={() => setPage(p => p - 1)}
              className="px-4 py-2 rounded-xl bg-[#151517] border border-white/5 text-zinc-400 text-sm font-medium hover:text-white hover:bg-white/5 disabled:opacity-30 disabled:cursor-not-allowed transition"
            >
              ← Prev
            </button>
            <span className="text-sm text-zinc-500 tabular-nums">
              Page <span className="text-zinc-300 font-semibold">{page + 1}</span> of <span className="text-zinc-300 font-semibold">{totalPages}</span>
            </span>
            <button
              disabled={page >= totalPages - 1}
              onClick={() => setPage(p => p + 1)}
              className="px-4 py-2 rounded-xl bg-[#151517] border border-white/5 text-zinc-400 text-sm font-medium hover:text-white hover:bg-white/5 disabled:opacity-30 disabled:cursor-not-allowed transition"
            >
              Next →
            </button>
          </div>
        )}

      </div>
    </div>
  );
};

export default CreatorDirectoryPage;
