import React, { useState, useEffect } from 'react';
import { Report, ReportStatus } from '../types/report';
import reportService from '../api/reportService';
import { safeRender } from '@/utils/safeRender';
import { showToast } from './Toast';
import { useAuth } from '../auth/useAuth';

const AdminReports: React.FC = () => {
  const { user, authLoading } = useAuth();
  const [reports, setReports] = useState<Report[]>([]);
  const [loading, setLoading] = useState(true);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(1);
  const [filter, setFilter] = useState<ReportStatus | undefined>(undefined);
  const [selectedReport, setSelectedReport] = useState<Report | null>(null);
  const [isUpdating, setIsUpdating] = useState(false);

  const fetchReports = async () => {
    // Guard against fetching if not authorized or still loading auth
    if (!user || authLoading || user.role !== 'ADMIN') {
        return;
    }

    setLoading(true);
    try {
      const data = await reportService.getReports(filter, page, 20);
      setReports(data.content || []);
      setTotalPages(data.totalPages || 1);
    } catch (error: any) {
      showToast('Failed to fetch reports', 'error');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchReports();
  }, [filter, user, authLoading, page]);

  const handleUpdateReport = async (status: ReportStatus) => {
    if (!selectedReport) return;
    setIsUpdating(true);
    try {
      await reportService.updateReport(selectedReport.id.toString(), {
        status
      });
      showToast(`Report ${(status || '').toString().toLowerCase()}`, 'success');
      setSelectedReport(null);
      fetchReports();
    } catch (error: any) {
      showToast('Failed to update report', 'error');
    } finally {
      setIsUpdating(false);
    }
  };

  const handleQuickAction = async (report: Report, status: ReportStatus) => {
    try {
      await reportService.updateReport(report.id.toString(), { status });
      showToast(`Report ${(status || '').toString().toLowerCase()}`, 'success');
      fetchReports();
    } catch (error: any) {
      showToast('Failed to update report', 'error');
    }
  };

  const getStatusColor = (status: ReportStatus) => {
    switch (status) {
      case ReportStatus.PENDING: return '#dc3545';
      case ReportStatus.REVIEWED: return '#ffc107';
      case ReportStatus.RESOLVED: return '#198754';
      case ReportStatus.REJECTED: return '#6c757d';
      default: return '#6c757d';
    }
  };

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        <h2>Abuse Reports</h2>
        <div style={styles.filters}>
          <label style={{ marginRight: '10px' }}>Filter by status:</label>
          <select 
            value={filter || ''} 
            onChange={(e) => setFilter(e.target.value as ReportStatus || undefined)}
            style={styles.select}
          >
            <option value="">All Statuses</option>
            {Object.values(ReportStatus).map(s => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
        </div>
      </div>

      {loading ? (
        <p>Loading reports...</p>
      ) : (
        <div style={styles.tableWrapper}>
          <table style={styles.table}>
            <thead>
              <tr style={styles.tableHeader}>
                <th>Target</th>
                <th>Reason</th>
                <th>Reported By</th>
                <th>Created At</th>
                <th>Status</th>
                <th>Action</th>
              </tr>
            </thead>
            <tbody>
              {reports.map(report => (
                <tr key={report.id} style={styles.tableRow}>
                  <td>{report.reportedUserId ? `User (${safeRender(report.reportedUserId)})` : `Stream (${safeRender(report.streamId)})`}</td>
                  <td>{safeRender(report.reason)}</td>
                  <td>{safeRender(report.reporterUserId)}</td>
                  <td>{safeRender(report.createdAt ? new Date(report.createdAt).toLocaleString() : 'N/A')}</td>
                  <td>
                    <span style={{ 
                      ...styles.statusBadge, 
                      backgroundColor: getStatusColor(report.status) 
                    }}>
                      {safeRender(report.status)}
                    </span>
                  </td>
                  <td style={{ display: 'flex', gap: '6px', alignItems: 'center' }}>
                    <button
                      onClick={() => setSelectedReport(report)}
                      style={styles.viewButton}
                    >
                      View
                    </button>
                    <button
                      onClick={() => handleQuickAction(report, ReportStatus.RESOLVED)}
                      style={styles.resolveButton}
                    >
                      Resolve
                    </button>
                    <button
                      onClick={() => handleQuickAction(report, ReportStatus.REJECTED)}
                      style={styles.rejectButton}
                    >
                      Reject
                    </button>
                  </td>
                </tr>
              ))}
              {reports.length === 0 && (
                <tr>
                  <td colSpan={6} style={{ textAlign: 'center', padding: '2rem' }}>No reports found</td>
                </tr>
              )}
            </tbody>
          </table>
          <div style={{ marginTop: '1rem', display: 'flex', gap: '1rem', alignItems: 'center', justifyContent: 'center' }}>
            <button disabled={page <= 0} onClick={() => setPage(page - 1)} style={styles.viewButton}>Prev</button>
            <span style={{ fontSize: '0.9rem' }}>Page {page + 1} of {totalPages}</span>
            <button disabled={page + 1 >= totalPages} onClick={() => setPage(page + 1)} style={styles.viewButton}>Next</button>
          </div>
        </div>
      )}

      {selectedReport && (
        <div style={styles.modalOverlay}>
          <div style={styles.modal}>
            <div style={styles.modalHeader}>
              <h3>Report Details</h3>
              <button onClick={() => setSelectedReport(null)} style={styles.closeButton}>&times;</button>
            </div>
            <div style={styles.modalBody}>
              <div style={styles.detailRow}>
                <strong>Target:</strong> {selectedReport.reportedUserId ? `User (${selectedReport.reportedUserId})` : `Stream (${selectedReport.streamId})`}
              </div>
              <div style={styles.detailRow}>
                <strong>Reported By:</strong> User ({selectedReport.reporterUserId})
              </div>
              {selectedReport.streamId && (
                <div style={styles.detailRow}>
                  <strong>Stream ID:</strong> {selectedReport.streamId}
                </div>
              )}
              <div style={styles.detailRow}>
                <strong>Reason:</strong> {selectedReport.reason}
              </div>
              <div style={styles.detailRow}>
                <strong>Description:</strong>
                <div style={styles.descriptionText}>
                  {selectedReport.description || 'No additional details provided.'}
                </div>
              </div>
              <div style={styles.detailRow}>
                <strong>Created:</strong> {new Date(selectedReport.createdAt).toLocaleString()}
              </div>
              <div style={styles.modalActions}>
                <button 
                  onClick={() => handleUpdateReport(ReportStatus.REVIEWED)}
                  disabled={isUpdating || selectedReport.status === ReportStatus.REVIEWED}
                  style={{ ...styles.actionButton, backgroundColor: '#ffc107' }}
                >
                  Review
                </button>
                <button 
                  onClick={() => handleUpdateReport(ReportStatus.RESOLVED)}
                  disabled={isUpdating || selectedReport.status === ReportStatus.RESOLVED}
                  style={{ ...styles.actionButton, backgroundColor: '#198754', color: 'white' }}
                >
                  Resolve
                </button>
                <button 
                  onClick={() => handleUpdateReport(ReportStatus.REJECTED)}
                  disabled={isUpdating || selectedReport.status === ReportStatus.REJECTED}
                  style={{ ...styles.actionButton, backgroundColor: '#dc3545', color: 'white' }}
                >
                  Reject
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    padding: '1.5rem',
    backgroundColor: 'white',
    borderRadius: '8px',
    boxShadow: '0 2px 10px rgba(0,0,0,0.05)',
  },
  header: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '1.5rem',
  },
  filters: {
    display: 'flex',
    alignItems: 'center',
  },
  select: {
    padding: '0.5rem',
    borderRadius: '4px',
    border: '1px solid #ddd',
  },
  tableWrapper: {
    overflowX: 'auto',
  },
  table: {
    width: '100%',
    borderCollapse: 'collapse',
    fontSize: '0.9rem',
  },
  tableHeader: {
    backgroundColor: '#f8f9fa',
    textAlign: 'left',
    borderBottom: '2px solid #eee',
  },
  tableRow: {
    borderBottom: '1px solid #eee',
  },
  statusBadge: {
    padding: '4px 8px',
    borderRadius: '12px',
    color: 'white',
    fontSize: '0.75rem',
    fontWeight: 'bold',
  },
  viewButton: {
    backgroundColor: '#2563eb',
    color: '#fff',
    border: 'none',
    padding: '6px 12px',
    borderRadius: '4px',
    cursor: 'pointer',
    marginRight: '6px',
    fontSize: '13px',
  },
  resolveButton: {
    padding: '6px 10px',
    backgroundColor: '#198754',
    color: '#fff',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '0.85rem',
  },
  rejectButton: {
    padding: '6px 10px',
    backgroundColor: '#dc3545',
    color: '#fff',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    fontSize: '0.85rem',
  },
  modalOverlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    width: '100%',
    height: '100%',
    backgroundColor: 'rgba(0,0,0,0.5)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 3000,
  },
  modal: {
    backgroundColor: 'white',
    borderRadius: '8px',
    width: '90%',
    maxWidth: '600px',
    boxShadow: '0 10px 30px rgba(0,0,0,0.2)',
    overflow: 'hidden',
  },
  modalHeader: {
    padding: '1rem 1.5rem',
    borderBottom: '1px solid #eee',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  modalBody: {
    padding: '1.5rem',
  },
  closeButton: {
    background: 'none',
    border: 'none',
    fontSize: '1.5rem',
    cursor: 'pointer',
    color: '#999',
  },
  detailRow: {
    marginBottom: '1rem',
  },
  descriptionText: {
    backgroundColor: '#f8f9fa',
    padding: '0.75rem',
    borderRadius: '4px',
    marginTop: '0.5rem',
    whiteSpace: 'pre-wrap',
  },
  textarea: {
    width: '100%',
    height: '100px',
    padding: '0.75rem',
    borderRadius: '4px',
    border: '1px solid #ddd',
    marginTop: '0.5rem',
    fontFamily: 'inherit',
  },
  modalActions: {
    display: 'flex',
    gap: '0.75rem',
    marginTop: '2rem',
  },
  actionButton: {
    flex: 1,
    padding: '10px',
    border: 'none',
    borderRadius: '4px',
    fontWeight: 'bold',
    cursor: 'pointer',
  }
};

export default AdminReports;
