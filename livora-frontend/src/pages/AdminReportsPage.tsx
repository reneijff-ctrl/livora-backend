import React, { useEffect, useState } from 'react';
import { useAuth } from '../auth/useAuth';
import { useNavigate } from 'react-router-dom';
import reportService from '../api/reportService';
import { safeRender } from '@/utils/safeRender';
import { Report, ReportStatus } from '../types/report';
import { showToast } from '../components/Toast';
import Loader from '../components/Loader';
import SEO from '../components/SEO';

const AdminReportsPage: React.FC = () => {
  const [reports, setReports] = useState<Report[]>([]);
  const [loading, setLoading] = useState(true);
  const [selectedReport, setSelectedReport] = useState<Report | null>(null);
  const { user } = useAuth();
  const navigate = useNavigate();

  useEffect(() => {
    if (user?.role !== 'ADMIN') {
      navigate('/');
      return;
    }
    fetchReports();
  }, [user, navigate]);

  const fetchReports = async () => {
    setLoading(true);
    try {
      const data = await reportService.getReports();
      setReports(data.content); 
    } catch (err) {
      showToast('Failed to load reports', 'error');
    } finally {
      setLoading(false);
    }
  };

  const updateStatus = async (id: string, status: ReportStatus) => {
    try {
      await reportService.updateReport(id, { status });
      showToast(`Report ${status.toLowerCase()}`, 'success');
      setReports(prev =>
        prev.map(r => r.id === id ? { ...r, status } : r)
      );
    } catch (err) {
      showToast('Failed to update status', 'error');
    }
  };

  if (loading) return <Loader type="grid" />;

  return (
    <div className="bg-[#08080A] min-h-screen text-white px-6 py-12">
      <SEO title="Admin - Moderation Reports" />
      <div className="max-w-7xl mx-auto">
        <h1 className="text-2xl font-semibold mb-8">
          Moderation – Reports
        </h1>

        <div className="overflow-x-auto bg-[#0F0F14] rounded-2xl border border-[#16161D] shadow-[0_20px_60px_rgba(0,0,0,0.6)]">
          <table className="w-full text-sm">
            <thead className="border-b border-[#16161D] text-zinc-400">
              <tr>
                <th className="text-left p-4">ID</th>
                <th className="text-left p-4">Reason</th>
                <th className="text-left p-4">Reported User</th>
                <th className="text-left p-4">Status</th>
                <th className="text-left p-4">Created</th>
                <th className="text-left p-4">Actions</th>
              </tr>
            </thead>
            <tbody>
              {reports.length === 0 ? (
                <tr>
                   <td colSpan={6} className="p-8 text-center text-zinc-500 font-medium">No reports found.</td>
                </tr>
              ) : reports.map((report) => (
                <tr key={report.id} className="border-b border-[#16161D] hover:bg-white/[0.02] transition-colors">
                  <td className="p-4 text-zinc-500 font-mono text-[10px]">{safeRender(report.id.substring(0, 8))}...</td>
                  <td className="p-4">
                    <span className="px-2 py-1 rounded bg-zinc-800 text-[10px] font-bold uppercase tracking-wider">
                      {safeRender(report.reason)}
                    </span>
                  </td>
                  <td className="p-4 text-zinc-300">User ID: {safeRender(report.reportedUserId)}</td>
                  <td className="p-4">
                     <span className={`px-2 py-1 rounded-full text-[10px] font-bold uppercase ${
                        report.status === ReportStatus.PENDING ? 'bg-amber-500/10 text-amber-500' :
                        report.status === ReportStatus.REVIEWED ? 'bg-blue-500/10 text-blue-500' :
                        report.status === ReportStatus.RESOLVED ? 'bg-emerald-500/10 text-emerald-500' :
                        'bg-red-500/10 text-red-500'
                     }`}>
                        {safeRender(report.status)}
                     </span>
                  </td>
                  <td className="p-4 text-zinc-500">{safeRender(new Date(report.createdAt).toLocaleDateString())}</td>
                  <td className="p-4">
                    <div className="flex gap-2">
                      <button
                        onClick={() => setSelectedReport(report)}
                        className="px-3 py-1 bg-zinc-700 hover:bg-zinc-600 text-white rounded text-xs font-medium"
                      >
                        View
                      </button>
                      {report.status !== ReportStatus.REVIEWED && (
                        <button
                          onClick={() => updateStatus(report.id, ReportStatus.REVIEWED)}
                          className="px-3 py-1 bg-zinc-800 hover:bg-zinc-700 text-zinc-300 rounded transition text-xs font-medium border border-white/5"
                        >
                          Review
                        </button>
                      )}
                      {report.status !== ReportStatus.RESOLVED && (
                        <button
                          onClick={() => updateStatus(report.id, ReportStatus.RESOLVED)}
                          className="px-3 py-1 bg-emerald-600 hover:bg-emerald-500 text-white rounded transition text-xs font-medium shadow-lg shadow-emerald-600/20"
                        >
                          Resolve
                        </button>
                      )}
                      {report.status !== ReportStatus.REJECTED && (
                        <button
                          onClick={() => updateStatus(report.id, ReportStatus.REJECTED)}
                          className="px-3 py-1 bg-red-600/10 hover:bg-red-600/20 text-red-500 rounded transition text-xs font-medium border border-red-500/10"
                        >
                          Reject
                        </button>
                      )}
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        {selectedReport && (
          <div className="fixed inset-0 bg-black/60 flex items-center justify-center z-50">
            <div className="bg-zinc-900 rounded-lg w-full max-w-lg p-6 shadow-xl">

              <div className="flex justify-between items-center mb-4">
                <h3 className="text-lg font-semibold text-white">Report Details</h3>
                <button
                  onClick={() => setSelectedReport(null)}
                  className="text-zinc-400 hover:text-white"
                >
                  ✕
                </button>
              </div>

              <div className="space-y-3 text-sm text-zinc-300">

                <div>
                  <strong>Reported User:</strong> {selectedReport.reportedUserId}
                </div>

                <div>
                  <strong>Reason:</strong> {selectedReport.reason}
                </div>

                <div>
                  <strong>Description:</strong>
                  <div className="mt-1 p-3 bg-zinc-800 rounded">
                    {selectedReport.description || "No description provided"}
                  </div>
                </div>

                <div>
                  <strong>Created:</strong> {new Date(selectedReport.createdAt).toLocaleString()}
                </div>

              </div>

            </div>
          </div>
        )}

      </div>
    </div>
  );
};

export default AdminReportsPage;
