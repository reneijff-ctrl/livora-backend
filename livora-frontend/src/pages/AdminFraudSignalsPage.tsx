import React, { useEffect, useState, useCallback } from 'react';
import { useSearchParams, Link } from 'react-router-dom';
import SEO from '../components/SEO';
import adminService from '../api/adminService';
import { FraudSignal } from '../types';

const AdminFraudSignalsPage: React.FC = () => {
  const [searchParams, setSearchParams] = useSearchParams();
  const [signals, setSignals] = useState<FraudSignal[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);

  const resolved = searchParams.get('resolved');
  const riskLevel = searchParams.get('riskLevel');

  const fetchSignals = useCallback(async () => {
    setLoading(true);
    try {
      const params: any = {
        page,
        size: 20,
      };
      if (resolved !== null) params.resolved = resolved === 'true';
      if (riskLevel) params.riskLevel = riskLevel;

      const response = await adminService.getFraudSignals(params);
      setSignals(response.content);
      setTotalPages(response.totalPages);
    } catch (error) {
      console.error('Failed to fetch fraud signals:', error);
    } finally {
      setLoading(false);
    }
  }, [page, resolved, riskLevel]);

  useEffect(() => {
    fetchSignals();
  }, [fetchSignals]);

  const handleFilterChange = (key: string, value: string | null) => {
    const newParams = new URLSearchParams(searchParams);
    if (value === null) {
      newParams.delete(key);
    } else {
      newParams.set(key, value);
    }
    setSearchParams(newParams);
    setPage(0);
  };

  const handleResolve = async (id: string) => {
    const reason = window.prompt('Enter reason for resolution:');
    if (reason === null) return;
    
    try {
      await adminService.resolveFraudSignal(id, reason);
      fetchSignals();
    } catch (error) {
      alert('Failed to resolve signal');
    }
  };

  return (
    <div className="min-h-screen bg-black text-white p-4 md:p-8">
      <SEO title="Fraud Signals | Admin" />
      
      <div className="max-w-7xl mx-auto">
        <div className="mb-8">
          <div className="flex items-center gap-2 text-zinc-500 text-sm mb-4">
            <Link to="/admin" className="hover:text-white transition-colors">Admin</Link>
            <span>/</span>
            <span className="text-zinc-300">Fraud Signals</span>
          </div>
          
          <div className="flex flex-col md:flex-row justify-between items-start md:items-center gap-4">
            <div>
              <h1 className="text-3xl font-bold mb-2">Fraud Signals</h1>
              <p className="text-zinc-500">Monitor and resolve suspicious platform activity</p>
            </div>
            
            <div className="flex flex-wrap gap-3">
              <select 
                className="bg-zinc-900 border border-zinc-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-red-500/20 text-white"
                value={resolved || ''}
                onChange={(e) => handleFilterChange('resolved', e.target.value || null)}
              >
                <option value="">All Statuses</option>
                <option value="false">Unresolved</option>
                <option value="true">Resolved</option>
              </select>

              <select 
                className="bg-zinc-900 border border-zinc-800 rounded-xl px-4 py-2.5 text-sm focus:outline-none focus:ring-2 focus:ring-red-500/20 text-white"
                value={riskLevel || ''}
                onChange={(e) => handleFilterChange('riskLevel', e.target.value || null)}
              >
                <option value="">All Risk Levels</option>
                <option value="CRITICAL">Critical</option>
                <option value="HIGH">High</option>
                <option value="MEDIUM">Medium</option>
                <option value="LOW">Low</option>
              </select>
            </div>
          </div>
        </div>

        <div className="bg-zinc-900/50 border border-zinc-800/60 rounded-2xl overflow-hidden shadow-2xl">
          <div className="overflow-x-auto">
            <table className="w-full text-left">
              <thead>
                <tr className="border-b border-zinc-800/60 text-zinc-500 text-[10px] uppercase font-bold tracking-[0.2em] bg-zinc-900/80">
                  <th className="px-6 py-5">Signal Type</th>
                  <th className="px-6 py-5">User</th>
                  <th className="px-6 py-5">Creator</th>
                  <th className="px-6 py-5">Risk / Score</th>
                  <th className="px-6 py-5">Timestamp</th>
                  <th className="px-6 py-5">Status</th>
                  <th className="px-6 py-5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/40">
                {loading ? (
                  <tr>
                    <td colSpan={7} className="px-6 py-20 text-center">
                      <div className="flex flex-col items-center gap-3">
                        <div className="w-8 h-8 border-2 border-red-500 border-t-transparent rounded-full animate-spin"></div>
                        <span className="text-zinc-500 text-sm">Loading fraud signals...</span>
                      </div>
                    </td>
                  </tr>
                ) : signals.length === 0 ? (
                  <tr>
                    <td colSpan={7} className="px-6 py-20 text-center">
                      <div className="text-zinc-500 flex flex-col items-center gap-2">
                        <svg className="w-10 h-10 opacity-20" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                          <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="1.5" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
                        </svg>
                        <span>No signals found matching the current filters</span>
                      </div>
                    </td>
                  </tr>
                ) : (
                  signals.map((signal) => (
                    <tr key={signal.id} className="group hover:bg-white/[0.02] transition-colors">
                      <td className="px-6 py-5">
                        <span className="font-mono text-xs text-blue-400 font-bold bg-blue-500/5 border border-blue-500/10 px-2 py-1 rounded">
                          {signal.type}
                        </span>
                      </td>
                      <td className="px-6 py-5">
                        <div className="text-sm font-medium text-zinc-200">{signal.userEmail}</div>
                        <div className="text-[10px] text-zinc-500 font-mono mt-0.5">ID: {signal.userId}</div>
                      </td>
                      <td className="px-6 py-5">
                        {signal.creatorEmail ? (
                          <>
                            <div className="text-sm font-medium text-zinc-200">{signal.creatorEmail}</div>
                            <div className="text-[10px] text-zinc-500 font-mono mt-0.5">ID: {signal.creatorId}</div>
                          </>
                        ) : (
                          <span className="text-zinc-700 font-medium">N/A</span>
                        )}
                      </td>
                      <td className="px-6 py-5">
                        <div className="flex items-center gap-2.5">
                          <RiskBadge level={signal.riskLevel} />
                          {signal.score !== undefined && signal.score !== null && (
                            <span className="text-zinc-500 font-mono text-[11px] font-bold">
                              {signal.score}
                            </span>
                          )}
                        </div>
                      </td>
                      <td className="px-6 py-5">
                        <div className="text-sm text-zinc-400">
                          {new Date(signal.createdAt).toLocaleDateString()}
                        </div>
                        <div className="text-[11px] text-zinc-500 mt-0.5">
                          {new Date(signal.createdAt).toLocaleTimeString()}
                        </div>
                      </td>
                      <td className="px-6 py-5">
                        {signal.resolved ? (
                          <div className="flex items-center gap-1.5 text-green-500">
                            <div className="w-1.5 h-1.5 bg-green-500 rounded-full shadow-[0_0_8px_rgba(34,197,94,0.4)]"></div>
                            <span className="text-[10px] font-black uppercase tracking-widest">Resolved</span>
                          </div>
                        ) : (
                          <div className="flex items-center gap-1.5 text-red-500">
                            <div className="w-1.5 h-1.5 bg-red-500 rounded-full animate-pulse shadow-[0_0_8px_rgba(239,68,68,0.4)]"></div>
                            <span className="text-[10px] font-black uppercase tracking-widest">Unresolved</span>
                          </div>
                        )}
                      </td>
                      <td className="px-6 py-5 text-right">
                        {!signal.resolved ? (
                          <button 
                            onClick={() => handleResolve(signal.id)}
                            className="bg-zinc-800 hover:bg-zinc-700 text-white text-[10px] font-bold uppercase tracking-widest px-4 py-2 rounded-lg transition-all active:scale-95"
                          >
                            Resolve
                          </button>
                        ) : (
                          <button 
                            disabled
                            className="text-zinc-600 text-[10px] font-bold uppercase tracking-widest px-4 py-2"
                          >
                            Resolved
                          </button>
                        )}
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        </div>

        {totalPages > 1 && (
          <div className="flex justify-center items-center gap-4 mt-10">
            <button 
              disabled={page === 0}
              onClick={() => setPage(p => p - 1)}
              className="px-5 py-2.5 bg-zinc-900 border border-zinc-800 rounded-xl text-sm font-bold hover:bg-zinc-800 disabled:opacity-30 disabled:hover:bg-zinc-900 transition-all"
            >
              Previous
            </button>
            <div className="text-zinc-500 text-xs font-bold uppercase tracking-widest">
              Page <span className="text-white">{page + 1}</span> of <span className="text-white">{totalPages}</span>
            </div>
            <button 
              disabled={page === totalPages - 1}
              onClick={() => setPage(p => p + 1)}
              className="px-5 py-2.5 bg-zinc-900 border border-zinc-800 rounded-xl text-sm font-bold hover:bg-zinc-800 disabled:opacity-30 disabled:hover:bg-zinc-900 transition-all"
            >
              Next
            </button>
          </div>
        )}
      </div>
    </div>
  );
};

const RiskBadge = ({ level }: { level: string }) => {
  const colors = {
    CRITICAL: 'text-red-500 bg-red-500/10 border-red-500/20 shadow-[0_0_10px_rgba(239,68,68,0.1)]',
    HIGH: 'text-orange-500 bg-orange-500/10 border-orange-500/20',
    MEDIUM: 'text-yellow-500 bg-yellow-500/10 border-yellow-500/20',
    LOW: 'text-green-500 bg-green-500/10 border-green-500/20',
  };
  const color = (colors as any)[level] || 'text-zinc-500 bg-zinc-500/10 border-zinc-500/20';
  
  return (
    <span className={`text-[9px] font-black uppercase tracking-[0.15em] px-2.5 py-1 border rounded-md ${color}`}>
      {level}
    </span>
  );
};

export default AdminFraudSignalsPage;
