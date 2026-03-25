import React, { useEffect, useState } from 'react';
import adminService from '../api/adminService';
import SEO from '../components/SEO';
import Loader from '../components/Loader';
import { useAuth } from '../auth/useAuth';
import { useNavigate } from 'react-router-dom';
import { showToast } from '../components/Toast';

const AdminCreatorVerifications: React.FC = () => {
  const [verifications, setVerifications] = useState<any[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [processingId, setProcessingId] = useState<number | null>(null);
  const [selectedVer, setSelectedVer] = useState<any | null>(null);
  const [showRejectModal, setShowRejectModal] = useState(false);
  const [showViewModal, setShowViewModal] = useState(false);
  const [reason, setReason] = useState('');
  const [activeFilter, setActiveFilter] = useState<string>('PENDING');
  
  const { user } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (user?.role !== 'ADMIN') {
      navigate('/');
      return;
    }
    fetchVerifications();
  }, [user, navigate, activeFilter]);

  const fetchVerifications = async () => {
    try {
      setLoading(true);
      const data = await adminService.getCreatorVerifications(activeFilter === 'ALL' ? undefined : activeFilter, 0, 50);
      setVerifications(data.content || []);
      setError(null);
    } catch (err) {
      console.error('Fetch error:', err);
      // If error occurs, we still want to show an empty list if it's just a 404 or empty response
      // But the service already handles this, so if we reach here, it might be a real error.
      setError('Failed to load verifications');
    } finally {
      setLoading(false);
    }
  };

  const handleApprove = async (id: number) => {
    if (!window.confirm('Approve this verification? The user will be upgraded to CREATOR.')) return;

    try {
      setProcessingId(id);
      await adminService.approveVerification(id);
      showToast('Verification approved', 'success');
      setShowViewModal(false);
      await fetchVerifications();
    } catch (err) {
      console.error(err);
      showToast('Approval failed', 'error');
    } finally {
      setProcessingId(null);
    }
  };

  const handleReject = async () => {
    if (!selectedVer || !reason.trim()) {
        showToast('Please provide a reason', 'error');
        return;
    }

    try {
      setProcessingId(selectedVer.id);
      await adminService.rejectVerification(selectedVer.id, reason);
      showToast('Verification rejected', 'success');
      setShowRejectModal(false);
      setShowViewModal(false);
      setReason('');
      await fetchVerifications();
    } catch (err) {
      console.error(err);
      showToast('Rejection failed', 'error');
    } finally {
      setProcessingId(null);
    }
  };

  const getStatusBadge = (status: string) => {
    switch (status) {
      case 'APPROVED':
        return (
          <span className="flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold bg-emerald-500/10 text-emerald-500 border border-emerald-500/20">
            ✅ APPROVED
          </span>
        );
      case 'REJECTED':
        return (
          <span className="flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold bg-red-500/10 text-red-500 border border-red-500/20">
            ❌ REJECTED
          </span>
        );
      case 'UNDER_REVIEW':
        return (
          <span className="flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold bg-blue-500/10 text-blue-500 border border-blue-500/20">
            🔍 UNDER REVIEW
          </span>
        );
      default:
        return (
          <span className="flex items-center gap-1.5 px-3 py-1 rounded-full text-[10px] font-bold bg-amber-500/10 text-amber-500 border border-amber-500/20">
            ⏳ PENDING
          </span>
        );
    }
  };

  if (loading && verifications.length === 0) return <Loader type="grid" />;

  return (
    <div className="min-h-screen bg-zinc-950 p-6 lg:p-10 text-white">
      <SEO title="Admin - Creator Verifications" />
      
      <div className="max-w-7xl mx-auto">
        <div className="flex flex-col md:flex-row md:items-center justify-between gap-6 mb-10">
          <div>
            <h1 className="text-4xl font-black tracking-tight mb-2">Creator Applications</h1>
            <p className="text-zinc-500 font-medium">Review and verify new platform creators</p>
          </div>

          <div className="flex bg-zinc-900/50 border border-zinc-800 p-1 rounded-xl overflow-hidden">
            {['PENDING', 'UNDER_REVIEW', 'APPROVED', 'REJECTED', 'ALL'].map((status) => (
              <button
                key={status}
                onClick={() => setActiveFilter(status)}
                className={`px-4 py-2 text-xs font-bold transition-all ${
                  activeFilter === status 
                    ? 'bg-zinc-800 text-white shadow-lg' 
                    : 'text-zinc-500 hover:text-zinc-300'
                }`}
              >
                {status.replace('_', ' ')}
              </button>
            ))}
          </div>
        </div>

        {error && (
          <div className="bg-red-500/10 border border-red-500/20 text-red-400 p-4 rounded-xl mb-6 flex items-center gap-3">
            ℹ️ {error}
          </div>
        )}

        <div className="bg-zinc-900/40 border border-zinc-800/60 rounded-3xl overflow-hidden backdrop-blur-md shadow-2xl">
          <div className="overflow-x-auto">
            <table className="w-full text-left border-collapse">
              <thead>
                <tr className="bg-zinc-900/80 border-b border-zinc-800">
                  <th className="px-6 py-5 text-[10px] font-black text-zinc-500 uppercase tracking-widest">Avatar</th>
                  <th className="px-6 py-5 text-[10px] font-black text-zinc-500 uppercase tracking-widest">Username</th>
                  <th className="px-6 py-5 text-[10px] font-black text-zinc-500 uppercase tracking-widest">Country</th>
                  <th className="px-6 py-5 text-[10px] font-black text-zinc-500 uppercase tracking-widest">Submitted</th>
                  <th className="px-6 py-5 text-[10px] font-black text-zinc-500 uppercase tracking-widest text-center">Status</th>
                  <th className="px-6 py-5 text-[10px] font-black text-zinc-500 uppercase tracking-widest text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-zinc-800/50">
                {verifications.map((v) => (
                  <tr key={v.id} className="group hover:bg-zinc-800/30 transition-all">
                    <td className="px-6 py-5">
                      <div className="w-10 h-10 rounded-full bg-zinc-800 flex items-center justify-center text-zinc-400 font-bold border border-zinc-700/50 group-hover:border-indigo-500/30 group-hover:bg-indigo-500/10 transition-all overflow-hidden">
                        {v.creator?.user?.profilePicture ? (
                           <img src={v.creator.user.profilePicture} alt="" className="w-full h-full object-cover" />
                        ) : (
                          (v.creator?.user?.username || 'U').charAt(0).toUpperCase()
                        )}
                      </div>
                    </td>
                    <td className="px-6 py-5">
                      <div>
                        <div className="font-bold text-zinc-100 group-hover:text-white transition-colors">{v.creator?.user?.username || 'Unknown'}</div>
                        <div className="text-[10px] text-zinc-500">{v.creator?.user?.email}</div>
                      </div>
                    </td>
                    <td className="px-6 py-5">
                      <div className="text-zinc-300 font-medium flex items-center gap-1.5">
                        📍 {v.country}
                      </div>
                    </td>
                    <td className="px-6 py-5">
                      <div className="text-[10px] font-bold text-zinc-500 uppercase">
                        {v.createdAt ? new Date(v.createdAt).toLocaleDateString() : '—'}
                      </div>
                    </td>
                    <td className="px-6 py-5">
                      <div className="flex justify-center">
                        {getStatusBadge(v.status)}
                      </div>
                    </td>
                    <td className="px-6 py-5">
                      <div className="flex justify-end">
                        <button 
                          onClick={async () => { 
                            try {
                              setLoading(true);
                              const detail = await adminService.getCreatorVerification(v.id);
                              setSelectedVer(detail); 
                              setShowViewModal(true); 
                            } catch (err) {
                              showToast('Failed to load application details', 'error');
                            } finally {
                              setLoading(false);
                            }
                          }}
                          className="flex items-center gap-2 bg-zinc-800 hover:bg-zinc-700 text-zinc-200 px-4 py-2 rounded-xl text-xs font-bold transition-all border border-zinc-700/50 hover:border-zinc-600 shadow-lg"
                        >
                          👁️ Review
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
                
                {verifications.length === 0 && !loading && (
                  <tr>
                    <td colSpan={6} className="px-6 py-20 text-center">
                      <div className="flex flex-col items-center gap-4">
                        <div className="w-16 h-16 rounded-3xl bg-zinc-900 flex items-center justify-center text-zinc-700 text-3xl">
                          🛡️
                        </div>
                        <div>
                          <div className="text-zinc-300 font-bold">No creator applications pending review.</div>
                          <div className="text-zinc-500 text-sm mt-1">Check back later or change filter to see history.</div>
                        </div>
                      </div>
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </div>
      </div>

      {/* Modern Detailed View Modal */}
      {showViewModal && selectedVer && (
        <div className="fixed inset-0 bg-black/90 backdrop-blur-xl z-50 flex items-center justify-center p-4 lg:p-8 overflow-hidden">
          <div className="bg-zinc-900 border border-zinc-800/50 rounded-[2.5rem] max-w-6xl w-full max-h-[95vh] overflow-y-auto shadow-2xl flex flex-col relative custom-scrollbar">
            
            {/* Modal Header */}
            <div className="sticky top-0 bg-zinc-900/80 backdrop-blur-md z-10 p-8 border-b border-zinc-800 flex justify-between items-center">
              <div>
                <div className="flex items-center gap-3 mb-1">
                  <h2 className="text-2xl font-black">Creator Application</h2>
                  {getStatusBadge(selectedVer.status)}
                </div>
                <p className="text-zinc-500 font-medium">Verification for user <span className="text-zinc-300">@{selectedVer.creator?.user?.username}</span></p>
              </div>
              <button 
                onClick={() => setShowViewModal(false)} 
                className="w-12 h-12 rounded-2xl bg-zinc-800 hover:bg-zinc-700 text-zinc-400 hover:text-white flex items-center justify-center transition-all border border-zinc-700/50 text-xl"
              >
                ✕
              </button>
            </div>

            <div className="p-8 lg:p-12">
                <div className="grid grid-cols-1 lg:grid-cols-12 gap-12">
                    
                    {/* Left Column: ID Verification */}
                    <div className="lg:col-span-7 space-y-10">
                        <div>
                            <div className="flex items-center gap-2 mb-6">
                                <span className="text-indigo-500 text-xl">🛡️</span>
                                <h3 className="text-lg font-bold">Identity Verification Documents</h3>
                            </div>
                            
                            <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                                <div className="space-y-3">
                                    <label className="text-[10px] font-black text-zinc-500 uppercase tracking-widest ml-1">Government ID (Front)</label>
                                    <div className="aspect-[3/2] rounded-[2rem] overflow-hidden border border-zinc-800 bg-zinc-950 group relative cursor-pointer">
                                        <img src={selectedVer.idDocumentUrl} alt="ID Front" className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                                        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors flex items-center justify-center opacity-0 group-hover:opacity-100">
                                            <span className="text-3xl">👁️</span>
                                        </div>
                                    </div>
                                </div>
                                
                                {selectedVer.documentBackUrl && (
                                    <div className="space-y-3">
                                        <label className="text-[10px] font-black text-zinc-500 uppercase tracking-widest ml-1">Government ID (Back)</label>
                                        <div className="aspect-[3/2] rounded-[2rem] overflow-hidden border border-zinc-800 bg-zinc-950 group relative cursor-pointer">
                                            <img src={selectedVer.documentBackUrl} alt="ID Back" className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                                            <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors flex items-center justify-center opacity-0 group-hover:opacity-100">
                                                <span className="text-3xl">👁️</span>
                                            </div>
                                        </div>
                                    </div>
                                )}

                                <div className="space-y-3">
                                    <label className="text-[10px] font-black text-zinc-500 uppercase tracking-widest ml-1">Selfie with ID</label>
                                    <div className="aspect-[3/2] rounded-[2rem] overflow-hidden border border-zinc-800 bg-zinc-950 group relative cursor-pointer">
                                        <img src={selectedVer.selfieDocumentUrl} alt="Selfie" className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-500" />
                                        <div className="absolute inset-0 bg-black/0 group-hover:bg-black/20 transition-colors flex items-center justify-center opacity-0 group-hover:opacity-100">
                                            <span className="text-3xl">👁️</span>
                                        </div>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {selectedVer.rejectionReason && (
                             <div className="bg-red-500/5 border border-red-500/20 p-6 rounded-[2rem]">
                                <h4 className="text-red-400 font-bold mb-2 flex items-center gap-2 text-sm">
                                    ❌ Previous Rejection Reason
                                </h4>
                                <p className="text-zinc-400 text-sm leading-relaxed">{selectedVer.rejectionReason}</p>
                             </div>
                        )}
                    </div>

                    {/* Right Column: Profile Data */}
                    <div className="lg:col-span-5 space-y-8">
                        <div>
                            <div className="flex items-center gap-2 mb-6">
                                <span className="text-indigo-500 text-xl">👤</span>
                                <h3 className="text-lg font-bold">Personal & Profile Data</h3>
                            </div>

                            <div className="space-y-4">
                                <div className="grid grid-cols-2 gap-4">
                                    <div className="bg-zinc-800/40 p-5 rounded-[1.5rem] border border-zinc-700/30 hover:border-zinc-700/60 transition-colors">
                                        <label className="block text-[10px] font-black text-zinc-500 uppercase mb-1.5 tracking-tighter">Real Name</label>
                                        <div className="font-bold text-zinc-100 leading-tight">{selectedVer.legalFirstName} {selectedVer.legalLastName}</div>
                                    </div>
                                    <div className="bg-zinc-800/40 p-5 rounded-[1.5rem] border border-zinc-700/30 hover:border-zinc-700/60 transition-colors">
                                        <label className="block text-[10px] font-black text-zinc-500 uppercase mb-1.5 tracking-tighter">Age & Birth Date</label>
                                        <div className="font-bold text-zinc-100 leading-tight flex items-center gap-2">
                                            📅 {selectedVer.dateOfBirth?.toString()}
                                            <span className="text-zinc-500 font-medium text-xs bg-zinc-900 px-2 py-0.5 rounded-full border border-zinc-700/50">
                                              {selectedVer.dateOfBirth ? Math.floor((new Date().getTime() - new Date(selectedVer.dateOfBirth).getTime()) / 31557600000) : '—'} y/o
                                            </span>
                                        </div>
                                    </div>
                                </div>
                                
                                <div className="bg-zinc-800/40 p-5 rounded-[1.5rem] border border-zinc-700/30 hover:border-zinc-700/60 transition-colors">
                                    <label className="block text-[10px] font-black text-zinc-500 uppercase mb-1.5 tracking-tighter">Legal Country & Location</label>
                                    <div className="font-bold text-zinc-100 leading-tight flex items-center gap-2 text-ellipsis overflow-hidden whitespace-nowrap">
                                        🌐 {selectedVer.country}
                                    </div>
                                </div>

                                <div className="bg-zinc-950/50 p-6 rounded-[2rem] border border-zinc-800/80">
                                    <h4 className="text-[10px] font-black text-zinc-500 uppercase mb-4 tracking-widest">Profile Details</h4>
                                    <div className="grid grid-cols-2 gap-x-8 gap-y-5">
                                        <div>
                                            <label className="block text-[10px] font-bold text-zinc-600 uppercase mb-1">Gender</label>
                                            <div className="text-sm font-semibold text-zinc-300">{selectedVer.gender || '—'}</div>
                                        </div>
                                        <div>
                                            <label className="block text-[10px] font-bold text-zinc-600 uppercase mb-1">Interested In</label>
                                            <div className="text-sm font-semibold text-zinc-300">{selectedVer.interestedIn || '—'}</div>
                                        </div>
                                        <div>
                                            <label className="block text-[10px] font-bold text-zinc-600 uppercase mb-1">Body Type</label>
                                            <div className="text-sm font-semibold text-zinc-300">{selectedVer.bodyType || '—'}</div>
                                        </div>
                                        <div>
                          <label className="block text-[10px] font-bold text-zinc-600 uppercase mb-1">Height (cm)</label>
                          <div className="text-sm font-semibold text-zinc-300">{selectedVer.heightCm || '—'}</div>
                        </div>
                        <div>
                          <label className="block text-[10px] font-bold text-zinc-600 uppercase mb-1">Weight (kg)</label>
                          <div className="text-sm font-semibold text-zinc-300">{selectedVer.weightKg || '—'}</div>
                        </div>
                                        <div>
                                            <label className="block text-[10px] font-bold text-zinc-600 uppercase mb-1">Ethnicity</label>
                                            <div className="text-sm font-semibold text-zinc-300">{selectedVer.ethnicity || '—'}</div>
                                        </div>
                                        <div>
                                            <label className="block text-[10px] font-bold text-zinc-600 uppercase mb-1">Hair Color</label>
                                            <div className="text-sm font-semibold text-zinc-300">{selectedVer.hairColor || '—'}</div>
                                        </div>
                                        <div>
                                            <label className="block text-[10px] font-bold text-zinc-600 uppercase mb-1">Eye Color</label>
                                            <div className="text-sm font-semibold text-zinc-300">{selectedVer.eyeColor || '—'}</div>
                                        </div>
                                        <div>
                                            <label className="block text-[10px] font-bold text-zinc-600 uppercase mb-1">Languages</label>
                                            <div className="text-sm font-semibold text-zinc-300">{selectedVer.languages || '—'}</div>
                                        </div>
                                    </div>
                                    
                                    <div className="mt-6 pt-6 border-t border-zinc-800/50">
                                        <label className="block text-[10px] font-bold text-zinc-600 uppercase mb-2">Short Bio</label>
                                        <p className="text-sm text-zinc-400 leading-relaxed italic">
                                            "{selectedVer.creator?.bio || 'No bio provided.'}"
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {(selectedVer.status === 'PENDING' || selectedVer.status === 'UNDER_REVIEW') && (
                            <div className="flex gap-4 pt-4">
                                <button 
                                    onClick={() => handleApprove(selectedVer.id)}
                                    disabled={processingId !== null}
                                    className="flex-1 bg-indigo-600 hover:bg-indigo-500 disabled:opacity-50 py-4 rounded-2xl font-black text-sm transition-all shadow-xl shadow-indigo-600/20 active:scale-[0.98] flex items-center justify-center gap-2"
                                >
                                    {processingId === selectedVer.id ? '⌛' : '✅'}
                                    Approve & Upgrade
                                </button>
                                <button 
                                    onClick={() => { setShowRejectModal(true); }}
                                    className="w-20 bg-red-600/10 hover:bg-red-600/20 text-red-500 py-4 rounded-2xl font-black transition-all border border-red-600/20 active:scale-[0.98] flex items-center justify-center text-xl shadow-lg shadow-red-600/5"
                                >
                                    ✕
                                </button>
                            </div>
                        )}
                    </div>
                </div>
            </div>
          </div>
        </div>
      )}

      {/* Modern Reject Modal */}
      {showRejectModal && (
        <div className="fixed inset-0 bg-black/95 backdrop-blur-md z-[60] flex items-center justify-center p-4">
          <div className="bg-zinc-900 border border-zinc-800 rounded-[2.5rem] max-w-md w-full p-10 shadow-2xl">
            <div className="w-16 h-16 rounded-3xl bg-red-500/10 flex items-center justify-center text-red-500 mb-6 text-2xl">
                ❌
            </div>
            <h2 className="text-2xl font-black mb-2">Reject Verification</h2>
            <p className="text-zinc-500 mb-8 font-medium leading-relaxed">Provide a clear reason for rejection. This feedback helps the user improve their next submission.</p>
            
            <textarea 
                value={reason} 
                onChange={e => setReason(e.target.value)}
                placeholder="e.g. Identity document is partially obscured or birth date doesn't match..."
                className="w-full bg-zinc-950 border border-zinc-800 rounded-2xl p-5 mb-8 focus:ring-2 focus:ring-red-500/50 focus:outline-none min-h-[140px] text-zinc-300 placeholder:text-zinc-700 transition-all font-medium"
            />
            
            <div className="flex flex-col gap-3">
                <button 
                    onClick={handleReject}
                    disabled={processingId !== null || !reason.trim()}
                    className="w-full bg-red-600 hover:bg-red-500 disabled:opacity-50 py-4 rounded-2xl font-black shadow-xl shadow-red-600/20 transition-all active:scale-[0.98] flex items-center justify-center gap-2"
                >
                    {processingId === selectedVer?.id ? '⌛' : '❌'}
                    Confirm Rejection
                </button>
                <button 
                    onClick={() => setShowRejectModal(false)} 
                    className="w-full py-4 text-zinc-500 hover:text-zinc-300 font-bold transition-colors"
                >
                    Go Back
                </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default AdminCreatorVerifications;
