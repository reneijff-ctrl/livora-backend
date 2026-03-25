import React, { useState } from 'react';
import { ReportReason, CreateReportRequest, ReportTargetType } from '../types/report';
import reportService from '../api/reportService';
import { showToast } from './Toast';

interface AbuseReportModalProps {
  isOpen: boolean;
  onClose: () => void;
  targetType: ReportTargetType;
  targetId: string;
  targetLabel?: string;
  reportedUserId: number;
  streamId?: string;
}

const reasonLabels: Record<ReportReason, string> = {
  [ReportReason.HARASSMENT]: 'Harassment or bullying',
  [ReportReason.VIOLENCE]: 'Violence or threats',
  [ReportReason.NON_CONSENSUAL]: 'Non-consensual content',
  [ReportReason.COPYRIGHT]: 'Copyright violation',
  [ReportReason.UNDERAGE]: 'Underage content',
  [ReportReason.SPAM]: 'Spam or scams',
  [ReportReason.OTHER]: 'Other',
};

const AbuseReportModal: React.FC<AbuseReportModalProps> = ({ isOpen, onClose, targetType, targetId, targetLabel, reportedUserId, streamId }) => {
  const [reason, setReason] = useState<ReportReason>(ReportReason.OTHER);
  const [description, setDescription] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [showConfirmation, setShowConfirmation] = useState(false);

  if (!isOpen) return null;

  const resetAndClose = () => {
    setShowConfirmation(false);
    setReason(ReportReason.OTHER);
    setDescription('');
    onClose();
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setIsSubmitting(true);
    try {
      const request: CreateReportRequest = {
        reportedUserId,
        reason,
        description: targetType === ReportTargetType.CHAT_MESSAGE 
          ? `[CHAT MESSAGE ID: ${targetId}] ${description}`
          : description
      };
      
      if (streamId) {
        request.streamId = streamId;
      }
      
      await reportService.submitReport(request);
      localStorage.setItem('report_cooldown', Date.now().toString());
      showToast('Report submitted successfully', 'success');
      setShowConfirmation(true);
    } catch (error: any) {
      showToast(error.response?.data?.message || 'Failed to submit report. Please try again.', 'error');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (showConfirmation) {
    return (
      <div style={styles.overlay}>
        <div style={styles.modal}>
          <div style={styles.confirmationBody}>
            <div style={styles.confirmationIcon}>✓</div>
            <h2 style={styles.confirmationTitle}>Report submitted</h2>
            <p style={styles.confirmationText}>
              Thank you for helping keep the community safe.<br />
              Our moderation team will review this report.
            </p>
            <button onClick={resetAndClose} style={styles.confirmationButton}>OK</button>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div style={styles.overlay}>
      <div style={styles.modal}>
        <div style={styles.header}>
          <h2 style={styles.title}>Report this livestream</h2>
          <button onClick={onClose} style={styles.closeButton}>&times;</button>
        </div>
        
        <form onSubmit={handleSubmit} style={styles.form}>
          <p style={styles.targetInfo}>
            Reporting stream of <strong>{targetLabel || 'this creator'}</strong>
          </p>

          <div style={styles.field}>
            <label style={styles.label}>Reason for report</label>
            <select 
              value={reason} 
              onChange={(e) => setReason(e.target.value as ReportReason)}
              style={styles.select}
              required
            >
              {Object.values(ReportReason).map((res) => (
                <option key={res} value={res}>{reasonLabels[res] || res}</option>
              ))}
            </select>
          </div>

          <div style={styles.field}>
            <label style={styles.label}>Additional details</label>
            <textarea 
              value={description}
              onChange={(e) => setDescription(e.target.value)}
              placeholder="Provide additional details about the issue (optional)"
              style={styles.textarea}
            />
          </div>

          <div style={styles.actions}>
            <button 
              type="button" 
              onClick={onClose} 
              style={styles.cancelButton}
              disabled={isSubmitting}
            >
              Cancel
            </button>
            <button 
              type="submit" 
              style={{...styles.submitButton, ...(isSubmitting ? styles.submitButtonDisabled : {})}}
              disabled={isSubmitting}
            >
              {isSubmitting ? 'Submitting...' : 'Submit Report'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  overlay: {
    position: 'fixed',
    top: 0,
    left: 0,
    width: '100%',
    height: '100%',
    backgroundColor: 'rgba(0,0,0,0.6)',
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    zIndex: 2000,
  },
  modal: {
    backgroundColor: 'white',
    borderRadius: '12px',
    width: '90%',
    maxWidth: '500px',
    boxShadow: '0 20px 40px rgba(0,0,0,0.2)',
    overflow: 'hidden',
  },
  header: {
    padding: '1.25rem 1.5rem',
    borderBottom: '1px solid #eee',
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
  },
  title: {
    margin: 0,
    fontSize: '18px',
    fontWeight: 600,
    color: '#111827',
  },
  closeButton: {
    background: 'none',
    border: 'none',
    fontSize: '1.5rem',
    cursor: 'pointer',
    color: '#999',
  },
  form: {
    padding: '1.5rem',
  },
  targetInfo: {
    fontSize: '14px',
    color: '#374151',
    marginBottom: '1.5rem',
    padding: '0.75rem',
    backgroundColor: '#f8f9fa',
    borderRadius: '6px',
  },
  field: {
    marginBottom: '1.25rem',
  },
  label: {
    display: 'block',
    marginBottom: '0.5rem',
    fontWeight: 500,
    fontSize: '14px',
    color: '#111827',
  },
  select: {
    width: '100%',
    padding: '0.75rem',
    borderRadius: '6px',
    border: '1px solid #d1d5db',
    fontSize: '1rem',
    backgroundColor: '#ffffff',
    color: '#111827',
  },
  textarea: {
    width: '100%',
    padding: '0.75rem',
    borderRadius: '6px',
    border: '1px solid #d1d5db',
    fontSize: '1rem',
    minHeight: '150px',
    fontFamily: 'inherit',
    resize: 'vertical',
    backgroundColor: '#ffffff',
    color: '#111827',
  },
  actions: {
    display: 'flex',
    gap: '1rem',
    marginTop: '2rem',
  },
  cancelButton: {
    flex: 1,
    padding: '0.75rem',
    borderRadius: '6px',
    border: '1px solid #ddd',
    backgroundColor: 'white',
    fontWeight: '600',
    cursor: 'pointer',
  },
  submitButton: {
    flex: 2,
    padding: '0.75rem',
    borderRadius: '6px',
    border: 'none',
    backgroundColor: '#dc3545',
    color: 'white',
    fontWeight: '700',
    cursor: 'pointer',
  },
  submitButtonDisabled: {
    opacity: 0.6,
    cursor: 'not-allowed',
  },
  confirmationBody: {
    padding: '2rem 1.5rem',
    textAlign: 'center' as const,
  },
  confirmationIcon: {
    width: '48px',
    height: '48px',
    borderRadius: '50%',
    backgroundColor: '#10b981',
    color: 'white',
    fontSize: '24px',
    fontWeight: 700,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'center',
    margin: '0 auto 1rem',
  },
  confirmationTitle: {
    fontSize: '18px',
    fontWeight: 600,
    color: '#111827',
    margin: '0 0 0.5rem',
  },
  confirmationText: {
    fontSize: '14px',
    color: '#374151',
    lineHeight: 1.6,
    margin: '0 0 1.5rem',
  },
  confirmationButton: {
    padding: '0.6rem 2rem',
    borderRadius: '6px',
    border: 'none',
    backgroundColor: '#111827',
    color: 'white',
    fontWeight: 600,
    cursor: 'pointer',
    fontSize: '14px',
  },
};

export default AbuseReportModal;
