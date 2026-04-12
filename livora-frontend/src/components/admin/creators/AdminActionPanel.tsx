import React, { useState } from 'react';
import adminService from '../../../api/adminService';
import { showToast } from '../../Toast';

interface Props {
  creator: any;
  onActionComplete: () => void;
}

const AdminActionPanel: React.FC<Props> = ({ creator, onActionComplete }) => {
  const [busy, setBusy] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [suspendReason, setSuspendReason] = useState('');

  const run = async (action: () => Promise<void>, successMsg: string) => {
    setBusy(true);
    try {
      await action();
      showToast(successMsg, 'success');
      onActionComplete();
    } catch {
      // apiClient interceptor already shows a toast
    } finally {
      setBusy(false);
    }
  };

  const userId: number = creator.userId;
  const status: string = creator.status ?? '';
  const appStatus: string = creator.applicationStatus ?? '';
  const verStatus: string = creator.verificationStatus ?? '';

  const inputClass = `
    w-full px-3 py-2 rounded-lg text-sm transition-all outline-none
    placeholder:text-zinc-600
  `;
  const inputStyle = {
    backgroundColor: '#1c1c1f',
    border: '1px solid #2a2a2e',
    color: '#e5e7eb',
  };
  const inputFocusStyle = {
    borderColor: '#6366f1',
    boxShadow: '0 0 0 2px rgba(99,102,241,0.15)',
  };

  return (
    <div className="flex flex-col gap-5">

      {/* Application actions */}
      {appStatus === 'UNDER_REVIEW' && (
        <div
          className="rounded-xl p-4"
          style={{ backgroundColor: '#1a1a1d', border: '1px solid #2a2a2e' }}
        >
          <p className="text-xs font-semibold uppercase tracking-widest mb-3" style={{ color: '#6b7280' }}>
            Application Review
          </p>
          <div className="flex flex-col gap-3">
            <button
              disabled={busy}
              onClick={() => run(() => adminService.approveCreatorApplication(userId), 'Application approved')}
              className="w-full py-2.5 rounded-lg text-sm font-semibold transition-all"
              style={{
                backgroundColor: busy ? '#1c1c1f' : 'rgba(16,185,129,0.15)',
                border: '1px solid rgba(16,185,129,0.3)',
                color: busy ? '#4b5563' : '#34d399',
                cursor: busy ? 'not-allowed' : 'pointer',
              }}
            >
              ✓ Approve Application
            </button>
            <div className="flex gap-2">
              <input
                className={inputClass}
                style={inputStyle}
                placeholder="Rejection reason…"
                value={rejectReason}
                onChange={e => setRejectReason(e.target.value)}
                onFocus={e => Object.assign(e.currentTarget.style, inputFocusStyle)}
                onBlur={e => Object.assign(e.currentTarget.style, inputStyle)}
              />
              <button
                disabled={busy || !rejectReason.trim()}
                onClick={() => run(() => adminService.rejectCreatorApplication(userId, rejectReason), 'Application rejected')}
                className="px-4 py-2 rounded-lg text-sm font-semibold flex-shrink-0 transition-all"
                style={{
                  backgroundColor: busy || !rejectReason.trim() ? '#1c1c1f' : 'rgba(239,68,68,0.15)',
                  border: `1px solid ${busy || !rejectReason.trim() ? '#2a2a2e' : 'rgba(239,68,68,0.3)'}`,
                  color: busy || !rejectReason.trim() ? '#4b5563' : '#f87171',
                  cursor: busy || !rejectReason.trim() ? 'not-allowed' : 'pointer',
                }}
              >
                Reject
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Verification actions */}
      {verStatus === 'PENDING' && (
        <div
          className="rounded-xl p-4"
          style={{ backgroundColor: '#1a1a1d', border: '1px solid #2a2a2e' }}
        >
          <p className="text-xs font-semibold uppercase tracking-widest mb-3" style={{ color: '#6b7280' }}>
            Verification Review
          </p>
          <div className="flex flex-col gap-3">
            <button
              disabled={busy}
              onClick={() => run(() => adminService.approveCreatorVerification(userId), 'Verification approved')}
              className="w-full py-2.5 rounded-lg text-sm font-semibold transition-all"
              style={{
                backgroundColor: busy ? '#1c1c1f' : 'rgba(16,185,129,0.15)',
                border: '1px solid rgba(16,185,129,0.3)',
                color: busy ? '#4b5563' : '#34d399',
                cursor: busy ? 'not-allowed' : 'pointer',
              }}
            >
              ✓ Approve Verification
            </button>
            <div className="flex gap-2">
              <input
                className={inputClass}
                style={inputStyle}
                placeholder="Rejection reason…"
                value={rejectReason}
                onChange={e => setRejectReason(e.target.value)}
                onFocus={e => Object.assign(e.currentTarget.style, inputFocusStyle)}
                onBlur={e => Object.assign(e.currentTarget.style, inputStyle)}
              />
              <button
                disabled={busy || !rejectReason.trim()}
                onClick={() => run(() => adminService.rejectCreatorVerification(userId, rejectReason), 'Verification rejected')}
                className="px-4 py-2 rounded-lg text-sm font-semibold flex-shrink-0 transition-all"
                style={{
                  backgroundColor: busy || !rejectReason.trim() ? '#1c1c1f' : 'rgba(239,68,68,0.15)',
                  border: `1px solid ${busy || !rejectReason.trim() ? '#2a2a2e' : 'rgba(239,68,68,0.3)'}`,
                  color: busy || !rejectReason.trim() ? '#4b5563' : '#f87171',
                  cursor: busy || !rejectReason.trim() ? 'not-allowed' : 'pointer',
                }}
              >
                Reject
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Profile lifecycle actions */}
      {(status === 'DRAFT' || status === 'PENDING') && (
        <div
          className="rounded-xl p-4"
          style={{ backgroundColor: '#1a1a1d', border: '1px solid #2a2a2e' }}
        >
          <p className="text-xs font-semibold uppercase tracking-widest mb-3" style={{ color: '#6b7280' }}>
            Profile Status
          </p>
          <div className="flex gap-3">
            <button
              disabled={busy}
              onClick={() => run(() => adminService.approveCreator(userId), 'Creator approved')}
              className="flex-1 py-2.5 rounded-lg text-sm font-semibold transition-all"
              style={{
                backgroundColor: busy ? '#1c1c1f' : 'rgba(16,185,129,0.15)',
                border: '1px solid rgba(16,185,129,0.3)',
                color: busy ? '#4b5563' : '#34d399',
                cursor: busy ? 'not-allowed' : 'pointer',
              }}
            >
              ✓ Approve
            </button>
            <button
              disabled={busy}
              onClick={() => run(() => adminService.rejectCreator(userId), 'Creator rejected')}
              className="flex-1 py-2.5 rounded-lg text-sm font-semibold transition-all"
              style={{
                backgroundColor: busy ? '#1c1c1f' : 'rgba(239,68,68,0.15)',
                border: '1px solid rgba(239,68,68,0.3)',
                color: busy ? '#4b5563' : '#f87171',
                cursor: busy ? 'not-allowed' : 'pointer',
              }}
            >
              ✕ Reject
            </button>
          </div>
        </div>
      )}

      {status === 'APPROVED' && (
        <div
          className="rounded-xl p-4"
          style={{ backgroundColor: '#1a1a1d', border: '1px solid #2a2a2e' }}
        >
          <p className="text-xs font-semibold uppercase tracking-widest mb-3" style={{ color: '#6b7280' }}>
            Profile Status
          </p>
          <button
            disabled={busy}
            onClick={() => run(() => adminService.activateCreator(userId), 'Creator set to active')}
            className="w-full py-2.5 rounded-lg text-sm font-semibold transition-all"
            style={{
              backgroundColor: busy ? '#1c1c1f' : 'rgba(99,102,241,0.15)',
              border: '1px solid rgba(99,102,241,0.3)',
              color: busy ? '#4b5563' : '#a5b4fc',
              cursor: busy ? 'not-allowed' : 'pointer',
            }}
          >
            ▶ Set Active
          </button>
        </div>
      )}

      {/* Moderation */}
      <div
        className="rounded-xl p-4"
        style={{ backgroundColor: '#1a1a1d', border: '1px solid #2a2a2e' }}
      >
        <p className="text-xs font-semibold uppercase tracking-widest mb-3" style={{ color: '#6b7280' }}>
          Moderation
        </p>
        {status !== 'SUSPENDED' ? (
          <div className="flex gap-2">
            <input
              className={inputClass}
              style={inputStyle}
              placeholder="Suspend reason…"
              value={suspendReason}
              onChange={e => setSuspendReason(e.target.value)}
              onFocus={e => Object.assign(e.currentTarget.style, inputFocusStyle)}
              onBlur={e => Object.assign(e.currentTarget.style, inputStyle)}
            />
            <button
              disabled={busy || !suspendReason.trim()}
              onClick={() => run(() => adminService.suspendCreatorProfile(userId, suspendReason), 'Creator suspended')}
              className="px-4 py-2 rounded-lg text-sm font-semibold flex-shrink-0 transition-all"
              style={{
                backgroundColor: busy || !suspendReason.trim() ? '#1c1c1f' : 'rgba(245,158,11,0.15)',
                border: `1px solid ${busy || !suspendReason.trim() ? '#2a2a2e' : 'rgba(245,158,11,0.3)'}`,
                color: busy || !suspendReason.trim() ? '#4b5563' : '#fbbf24',
                cursor: busy || !suspendReason.trim() ? 'not-allowed' : 'pointer',
              }}
            >
              Suspend
            </button>
          </div>
        ) : (
          <button
            disabled={busy}
            onClick={() => run(() => adminService.unsuspendCreator(userId), 'Creator unsuspended')}
            className="w-full py-2.5 rounded-lg text-sm font-semibold transition-all"
            style={{
              backgroundColor: busy ? '#1c1c1f' : 'rgba(99,102,241,0.15)',
              border: '1px solid rgba(99,102,241,0.3)',
              color: busy ? '#4b5563' : '#a5b4fc',
              cursor: busy ? 'not-allowed' : 'pointer',
            }}
          >
            ↩ Unsuspend Creator
          </button>
        )}
      </div>

    </div>
  );
};

export default AdminActionPanel;
