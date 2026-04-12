import React, { useCallback, useEffect, useState } from 'react';
import { useNavigate, useParams } from 'react-router-dom';
import adminService from '../../../api/adminService';
import SEO from '../../../components/SEO';
import CreatorStatusBadge from '../../../components/admin/creators/CreatorStatusBadge';
import AdminActionPanel from '../../../components/admin/creators/AdminActionPanel';
import { safeRender } from '@/utils/safeRender';
import AdminAvatar from '../../../components/admin/AdminAvatar';

const LIFECYCLE_STEPS = ['DRAFT', 'PENDING', 'ACTIVE'];

const Card: React.FC<{ title: string; children: React.ReactNode; className?: string }> = ({ title, children, className = '' }) => (
  <div
    className={`rounded-2xl p-5 mb-4 ${className}`}
    style={{ backgroundColor: '#151517', border: '1px solid #1f1f23' }}
  >
    <h3 className="text-xs font-semibold uppercase tracking-widest mb-4" style={{ color: '#4b5563' }}>{title}</h3>
    {children}
  </div>
);

const Field: React.FC<{ label: string; value?: string | null }> = ({ label, value }) => (
  <div className="flex gap-3 mb-3 text-sm">
    <span className="flex-shrink-0 w-40" style={{ color: '#6b7280' }}>{label}</span>
    <span className="font-medium text-white">{value ?? <span style={{ color: '#4b5563' }}>—</span>}</span>
  </div>
);

const CreatorDetailPage: React.FC = () => {
  const { userId } = useParams<{ userId: string }>();
  const navigate = useNavigate();
  const [creator, setCreator] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(async () => {
    if (!userId) return;
    setLoading(true);
    setError(null);
    try {
      const data = await adminService.getUnifiedCreator(Number(userId));
      setCreator(data);
    } catch {
      setError('Creator not found or failed to load.');
    } finally {
      setLoading(false);
    }
  }, [userId]);

  useEffect(() => { load(); }, [load]);

  if (loading) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ backgroundColor: '#0f0f11' }}>
        <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin" />
      </div>
    );
  }

  if (error || !creator) {
    return (
      <div className="min-h-screen flex items-center justify-center" style={{ backgroundColor: '#0f0f11' }}>
        <div className="text-center">
          <div className="text-4xl mb-4">⚠️</div>
          <p className="text-white font-semibold mb-2">{error ?? 'Unknown error'}</p>
          <button
            onClick={() => navigate('/admin/creators')}
            className="mt-4 px-4 py-2 rounded-lg text-sm font-semibold"
            style={{ backgroundColor: '#1c1c1f', border: '1px solid #2a2a2e', color: '#9ca3af' }}
          >
            ← Back to Creators
          </button>
        </div>
      </div>
    );
  }

  const currentIdx = LIFECYCLE_STEPS.indexOf(creator.status);
  const isSuspended = creator.status === 'SUSPENDED';

  return (
    <div className="min-h-screen" style={{ backgroundColor: '#0f0f11' }}>
      <SEO title={`Admin — ${creator.displayName || creator.username}`} />

      <div className="max-w-4xl mx-auto px-6 py-8">

        {/* Breadcrumb */}
        <div className="flex items-center gap-2 text-sm mb-6" style={{ color: '#6b7280' }}>
          <button onClick={() => navigate('/admin/creators')} className="hover:text-white transition-colors">Creators</button>
          <span>/</span>
          <button onClick={() => navigate('/admin/creators/queue')} className="hover:text-white transition-colors">Queue</button>
          <span>/</span>
          <span style={{ color: '#e5e7eb' }}>{safeRender(creator.displayName || creator.username)}</span>
        </div>

        {/* Hero header card */}
        <div
          className="rounded-2xl p-6 mb-6 relative overflow-hidden"
          style={{ backgroundColor: '#151517', border: '1px solid #1f1f23' }}
        >
          {/* Subtle gradient glow behind avatar */}
          <div
            className="absolute top-0 left-0 w-48 h-48 rounded-full blur-3xl opacity-20 pointer-events-none"
            style={{ background: 'radial-gradient(circle, #6366f1, transparent)' }}
          />

          <div className="flex items-start gap-5 relative">
            {/* Large avatar */}
            <AdminAvatar name={creator.displayName || creator.username || '?'} src={creator.profileImage} seed={creator.userId} size="xl" rounded="rounded-2xl" />

            {/* Name + meta */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-3 flex-wrap mb-1">
                <h1 className="text-2xl font-bold text-white">
                  {safeRender(creator.displayName || creator.username || '—')}
                </h1>
                <CreatorStatusBadge status={creator.status} />
              </div>
              <p className="text-sm mb-3" style={{ color: '#6b7280' }}>
                @{safeRender(creator.username)} · {safeRender(creator.email)} · ID #{creator.userId}
              </p>
              {/* Quick stats row */}
              <div className="flex gap-4 flex-wrap">
                {creator.applicationSubmittedAt && (
                  <span className="text-xs px-2.5 py-1 rounded-lg" style={{ backgroundColor: '#1c1c1f', color: '#9ca3af', border: '1px solid #2a2a2e' }}>
                    Applied {new Date(creator.applicationSubmittedAt).toLocaleDateString()}
                  </span>
                )}
                {creator.verifiedAt && (
                  <span className="text-xs px-2.5 py-1 rounded-lg" style={{ backgroundColor: '#1c1c1f', color: '#10b981', border: '1px solid rgba(16,185,129,0.2)' }}>
                    Verified {new Date(creator.verifiedAt).toLocaleDateString()}
                  </span>
                )}
                {creator.suspendedAt && (
                  <span className="text-xs px-2.5 py-1 rounded-lg" style={{ backgroundColor: 'rgba(239,68,68,0.1)', color: '#f87171', border: '1px solid rgba(239,68,68,0.2)' }}>
                    Suspended {new Date(creator.suspendedAt).toLocaleDateString()}
                  </span>
                )}
              </div>
            </div>

            {/* Back button */}
            <button
              onClick={() => navigate(-1)}
              className="px-4 py-2 rounded-lg text-sm font-semibold transition-all flex-shrink-0"
              style={{ backgroundColor: '#1c1c1f', border: '1px solid #2a2a2e', color: '#9ca3af' }}
              onMouseEnter={e => { (e.currentTarget as HTMLButtonElement).style.color = '#fff'; }}
              onMouseLeave={e => { (e.currentTarget as HTMLButtonElement).style.color = '#9ca3af'; }}
            >
              ← Back
            </button>
          </div>
        </div>

        {/* Lifecycle timeline */}
        <Card title="Lifecycle">
          <div className="flex items-center gap-0">
            {LIFECYCLE_STEPS.map((step, i) => {
              const done = currentIdx >= i;
              const active = currentIdx === i && !isSuspended;
              return (
                <React.Fragment key={step}>
                  <div className="flex flex-col items-center gap-1.5">
                    <div
                      className="w-9 h-9 rounded-full flex items-center justify-center font-bold text-sm transition-all"
                      style={{
                        backgroundColor: active ? '#6366f1' : done ? 'rgba(99,102,241,0.25)' : '#1c1c1f',
                        color: done ? '#a5b4fc' : '#4b5563',
                        boxShadow: active ? '0 0 16px rgba(99,102,241,0.5)' : 'none',
                        border: active ? '2px solid #6366f1' : done ? '2px solid rgba(99,102,241,0.4)' : '2px solid #2a2a2e',
                      }}
                    >
                      {done && !active ? '✓' : i + 1}
                    </div>
                    <span
                      className="text-xs font-semibold"
                      style={{ color: active ? '#a5b4fc' : done ? '#6b7280' : '#374151' }}
                    >
                      {step}
                    </span>
                  </div>
                  {i < LIFECYCLE_STEPS.length - 1 && (
                    <div
                      className="flex-1 h-0.5 mx-1 mb-5 transition-all"
                      style={{
                        backgroundColor: currentIdx > i ? 'rgba(99,102,241,0.4)' : '#1f1f23',
                        boxShadow: currentIdx > i ? '0 0 6px rgba(99,102,241,0.3)' : 'none',
                      }}
                    />
                  )}
                </React.Fragment>
              );
            })}

            {/* Suspended branch */}
            {isSuspended && (
              <>
                <div className="flex-1 h-0.5 mx-1 mb-5" style={{ backgroundColor: 'rgba(239,68,68,0.3)' }} />
                <div className="flex flex-col items-center gap-1.5">
                  <div
                    className="w-9 h-9 rounded-full flex items-center justify-center font-bold text-sm"
                    style={{ backgroundColor: 'rgba(239,68,68,0.15)', color: '#f87171', border: '2px solid rgba(239,68,68,0.4)', boxShadow: '0 0 12px rgba(239,68,68,0.3)' }}
                  >
                    !
                  </div>
                  <span className="text-xs font-semibold" style={{ color: '#f87171' }}>SUSPENDED</span>
                </div>
              </>
            )}
          </div>

          {creator.suspendReason && (
            <div className="mt-4 px-4 py-3 rounded-xl text-sm" style={{ backgroundColor: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', color: '#fca5a5' }}>
              <span className="font-semibold">Suspend reason: </span>{creator.suspendReason}
            </div>
          )}
        </Card>

        {/* Identity */}
        <Card title="Identity">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8">
            <Field label="Display Name" value={creator.displayName} />
            <Field label="Username" value={creator.username} />
            <Field label="Email" value={creator.email} />
            <Field label="User ID" value={String(creator.userId)} />
            <Field label="Profile Status" value={creator.status} />
          </div>
        </Card>

        {/* Profile Details */}
        {(creator.gender || creator.birthDate || creator.country || creator.interestedIn || creator.languages) && (
          <Card title="Profile Details">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8">
              <Field label="Gender" value={creator.gender || '-'} />
              <Field
                label="Birth Date"
                value={creator.birthDate ? new Date(creator.birthDate).toLocaleDateString() : '-'}
              />
              <Field label="Country" value={creator.country || '-'} />
              <Field label="Interested In" value={creator.interestedIn || '-'} />
              <Field
                label="Languages"
                value={creator.languages ? creator.languages.split(',').map((l: string) => l.trim()).filter(Boolean).join(', ') : '-'}
              />
            </div>
          </Card>
        )}
        {/* Application */}
        {creator.applicationId && (
          <Card title="Application">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8">
              <Field label="Application ID" value={String(creator.applicationId)} />
              <Field label="Status" value={creator.applicationStatus} />
              <Field label="Submitted" value={creator.applicationSubmittedAt ? new Date(creator.applicationSubmittedAt).toLocaleString() : null} />
              <Field label="Approved" value={creator.applicationApprovedAt ? new Date(creator.applicationApprovedAt).toLocaleString() : null} />
              <Field label="Terms Accepted" value={creator.termsAccepted ? 'Yes' : 'No'} />
              <Field label="Age Verified" value={creator.ageVerified ? 'Yes' : 'No'} />
            </div>
            {creator.applicationReviewNotes && (
              <div className="mt-3 px-4 py-3 rounded-xl text-sm" style={{ backgroundColor: '#1c1c1f', border: '1px solid #2a2a2e', color: '#9ca3af' }}>
                <span className="font-semibold text-white">Review Notes: </span>{creator.applicationReviewNotes}
              </div>
            )}
          </Card>
        )}

        {/* Verification */}
        {creator.verificationId && (
          <Card title="Verification">
            <div className="grid grid-cols-1 md:grid-cols-2 gap-x-8 mb-4">
              <Field label="Verification ID" value={String(creator.verificationId)} />
              <Field label="Status" value={creator.verificationStatus} />
              <Field label="Submitted" value={creator.verificationSubmittedAt ? new Date(creator.verificationSubmittedAt).toLocaleString() : null} />
              <Field label="Legal Name" value={[creator.legalFirstName, creator.legalLastName].filter(Boolean).join(' ')} />
              <Field label="Country" value={creator.country} />
            </div>

            {creator.verificationRejectionReason && (
              <div className="mb-4 px-4 py-3 rounded-xl text-sm" style={{ backgroundColor: 'rgba(239,68,68,0.08)', border: '1px solid rgba(239,68,68,0.2)', color: '#fca5a5' }}>
                <span className="font-semibold">Rejection Reason: </span>{creator.verificationRejectionReason}
              </div>
            )}

            {/* Documents with hover zoom */}
            {(creator.idDocumentUrl || creator.selfieDocumentUrl) && (
              <div>
                <p className="text-xs font-semibold uppercase tracking-widest mb-3" style={{ color: '#4b5563' }}>Documents</p>
                <div className="flex gap-4 flex-wrap">
                  {[
                    { url: creator.idDocumentUrl, label: 'Front ID' },
                    { url: creator.documentBackUrl, label: 'Back ID' },
                    { url: creator.selfieDocumentUrl, label: 'Selfie' },
                  ].filter(d => d.url).map(doc => (
                    <div key={doc.label}>
                      <p className="text-xs font-medium mb-2" style={{ color: '#6b7280' }}>{doc.label}</p>
                      <a href={doc.url} target="_blank" rel="noreferrer" className="block group relative">
                        <img
                          src={doc.url}
                          alt={doc.label}
                          className="rounded-xl object-cover transition-transform duration-200 group-hover:scale-105"
                          style={{ maxHeight: '160px', border: '1px solid #2a2a2e' }}
                        />
                        <div
                          className="absolute inset-0 rounded-xl opacity-0 group-hover:opacity-100 transition-opacity flex items-center justify-center"
                          style={{ backgroundColor: 'rgba(0,0,0,0.55)' }}
                        >
                          <span className="text-white text-xs font-semibold px-3 py-1.5 rounded-lg" style={{ backgroundColor: 'rgba(99,102,241,0.8)' }}>
                            Open ↗
                          </span>
                        </div>
                      </a>
                    </div>
                  ))}
                </div>
              </div>
            )}
          </Card>
        )}

        {/* Admin Actions */}
        <Card title="Admin Actions">
          <AdminActionPanel creator={creator} onActionComplete={load} />
        </Card>

      </div>
    </div>
  );
};

export default CreatorDetailPage;
