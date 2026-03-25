import React, { useEffect, useMemo, useState } from 'react';
import creatorService from '@/api/creatorService';
import { DocumentType, VerificationStatus, CreatorVerificationRequest, CreatorVerificationResponse } from '@/types/verification';
import HasRole from '@/components/HasRole';

const statusBadge = (status?: VerificationStatus) => {
  switch (status) {
    case VerificationStatus.APPROVED:
      return <span className="inline-flex items-center gap-2 text-emerald-400 bg-emerald-500/10 border border-emerald-400/20 rounded-full px-3 py-1 text-xs font-semibold">🟢 Approved</span>;
    case VerificationStatus.REJECTED:
      return <span className="inline-flex items-center gap-2 text-red-400 bg-red-500/10 border border-red-400/20 rounded-full px-3 py-1 text-xs font-semibold">🔴 Rejected</span>;
    case VerificationStatus.PENDING:
    default:
      return <span className="inline-flex items-center gap-2 text-amber-300 bg-amber-400/10 border border-amber-300/20 rounded-full px-3 py-1 text-xs font-semibold">🟡 Pending</span>;
  }
};

const countries = [
  'United States', 'United Kingdom', 'Netherlands', 'Germany', 'France', 'Spain', 'Italy', 'Canada', 'Australia'
];

const CreatorVerificationPage: React.FC = () => {
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [verification, setVerification] = useState<CreatorVerificationResponse | null>(null);
  const [error, setError] = useState<string | null>(null);

  const [form, setForm] = useState<CreatorVerificationRequest>({
    legalFirstName: '',
    legalLastName: '',
    dateOfBirth: '',
    country: '',
    documentType: DocumentType.PASSPORT,
    idDocumentUrl: '',
    documentBackUrl: '',
    selfieDocumentUrl: '',
  });

  useEffect(() => {
    let mounted = true;
    (async () => {
      try {
        const v = await creatorService.getMyVerification();
        if (mounted) {
          setVerification(v);
          if (v) {
            setForm((prev) => ({
              ...prev,
              legalFirstName: v.legalFirstName || '',
              legalLastName: v.legalLastName || '',
              dateOfBirth: v.dateOfBirth || '',
              country: v.country || '',
              documentType: v.documentType,
              idDocumentUrl: v.idDocumentUrl || '',
              documentBackUrl: v.documentBackUrl || '',
              selfieDocumentUrl: v.selfieDocumentUrl || '',
            }));
          }
        }
      } catch (e: any) {
        console.warn('Failed to load verification', e);
      } finally {
        if (mounted) setLoading(false);
      }
    })();
    return () => {
      mounted = false;
    };
  }, []);

  const onFile = async (file: File | null, type: 'front' | 'back' | 'selfie') => {
    if (!file) return;
    try {
      const url = await creatorService.uploadVerificationImage(file, type);
      setForm((prev) => ({
        ...prev,
        idDocumentUrl: type === 'front' ? url : prev.idDocumentUrl,
        documentBackUrl: type === 'back' ? url : prev.documentBackUrl,
        selfieDocumentUrl: type === 'selfie' ? url : prev.selfieDocumentUrl,
      }));
    } catch (e: any) {
      setError(e?.response?.data?.message || 'Upload failed. Please try again.');
      setTimeout(() => setError(null), 3000);
    }
  };

  const canSubmit = useMemo(() => {
    if (!form.legalFirstName || !form.legalLastName || !form.dateOfBirth || !form.country) return false;
    if (!form.idDocumentUrl || !form.selfieDocumentUrl) return false;
    return true;
  }, [form]);

  const onSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      const saved = await creatorService.submitVerification(form);
      setVerification(saved);
    } catch (e: any) {
      setError(e?.response?.data?.message || 'Submission failed. Please try again.');
      setTimeout(() => setError(null), 4000);
    } finally {
      setSubmitting(false);
    }
  };

  if (loading) {
    return (
      <div className="bg-[#08080A] min-h-screen py-24">
        <div className="max-w-4xl mx-auto px-6">
          <div className="h-10 w-40 bg-white/5 rounded animate-pulse mb-8" />
          <div className="h-20 w-full bg-white/5 rounded animate-pulse" />
        </div>
      </div>
    );
  }

  return (
    <div className="bg-[#08080A] min-h-screen py-24">
      <div className="max-w-4xl mx-auto px-6">
        {/* Header */}
        <div className="flex items-center justify-between mb-10">
          <h1 className="text-3xl md:text-4xl font-bold tracking-tight text-white">18+ Age & Identity Verification</h1>
          {statusBadge(verification?.status)}
        </div>
        {verification?.rejectionReason && (
          <div className="mb-8 text-sm text-red-400 bg-red-500/10 border border-red-400/20 rounded-xl p-4">
            Rejection reason: {verification.rejectionReason}
          </div>
        )}

        {/* Form */}
        <form onSubmit={onSubmit} className="bg-[#0F0F14] rounded-2xl ring-1 ring-white/10 p-6 md:p-8 shadow-[0_20px_60px_rgba(0,0,0,0.6)]">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
            <div>
              <label className="block text-sm text-zinc-400 mb-2">First name</label>
              <input
                type="text"
                value={form.legalFirstName}
                onChange={(e) => setForm({ ...form, legalFirstName: e.target.value })}
                className="w-full rounded-xl bg-black/40 border border-[#16161D] px-4 py-2.5 text-white placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                placeholder="Your legal first name"
              />
            </div>
            <div>
              <label className="block text-sm text-zinc-400 mb-2">Last name</label>
              <input
                type="text"
                value={form.legalLastName}
                onChange={(e) => setForm({ ...form, legalLastName: e.target.value })}
                className="w-full rounded-xl bg-black/40 border border-[#16161D] px-4 py-2.5 text-white placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
                placeholder="Your legal last name"
              />
            </div>
            <div>
              <label className="block text-sm text-zinc-400 mb-2">Date of birth</label>
              <input
                type="date"
                value={form.dateOfBirth}
                onChange={(e) => setForm({ ...form, dateOfBirth: e.target.value })}
                className="w-full rounded-xl bg-black/40 border border-[#16161D] px-4 py-2.5 text-white placeholder-zinc-500 focus:outline-none focus:ring-2 focus:ring-indigo-500"
              />
            </div>
            <div>
              <label className="block text-sm text-zinc-400 mb-2">Country</label>
              <select
                value={form.country}
                onChange={(e) => setForm({ ...form, country: e.target.value })}
                className="w-full rounded-xl bg-black/40 border border-[#16161D] px-4 py-2.5 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option value="" className="bg-[#0F0F14]">Select your country</option>
                {countries.map((c) => (
                  <option key={c} value={c} className="bg-[#0F0F14]">{c}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="block text-sm text-zinc-400 mb-2">Document type</label>
              <select
                value={form.documentType}
                onChange={(e) => setForm({ ...form, documentType: e.target.value as unknown as DocumentType })}
                className="w-full rounded-xl bg-black/40 border border-[#16161D] px-4 py-2.5 text-white focus:outline-none focus:ring-2 focus:ring-indigo-500"
              >
                <option value={DocumentType.PASSPORT} className="bg-[#0F0F14]">Passport</option>
                <option value={DocumentType.ID_CARD} className="bg-[#0F0F14]">ID Card</option>
                <option value={DocumentType.DRIVER_LICENSE} className="bg-[#0F0F14]">Driver License</option>
              </select>
            </div>
          </div>

          {/* Uploads */}
          <div className="mt-8 grid grid-cols-1 md:grid-cols-3 gap-6">
            <div>
              <label className="block text-sm text-zinc-400 mb-2">Document (front)</label>
              <div className="rounded-xl border border-[#16161D] bg-black/30 p-4">
                {form.idDocumentUrl ? (
                  <img src={form.idDocumentUrl} alt="Document front" className="w-full h-36 object-cover rounded-lg mb-3" />
                ) : (
                  <div className="w-full h-36 rounded-lg bg-white/5 mb-3" />
                )}
                <input type="file" accept="image/*" onChange={(e) => onFile(e.target.files?.[0] || null, 'front')} className="block w-full text-xs text-zinc-400 file:bg-indigo-600 file:text-white file:px-3 file:py-1.5 file:rounded-lg file:border-0 file:mr-3 hover:file:opacity-90" />
              </div>
            </div>

            <div>
              <label className="block text-sm text-zinc-400 mb-2">Document (back) — optional</label>
              <div className="rounded-xl border border-[#16161D] bg-black/30 p-4">
                {form.documentBackUrl ? (
                  <img src={form.documentBackUrl} alt="Document back" className="w-full h-36 object-cover rounded-lg mb-3" />
                ) : (
                  <div className="w-full h-36 rounded-lg bg-white/5 mb-3" />
                )}
                <input type="file" accept="image/*" onChange={(e) => onFile(e.target.files?.[0] || null, 'back')} className="block w-full text-xs text-zinc-400 file:bg-indigo-600 file:text-white file:px-3 file:py-1.5 file:rounded-lg file:border-0 file:mr-3 hover:file:opacity-90" />
              </div>
            </div>

            <div>
              <label className="block text-sm text-zinc-400 mb-2">Selfie</label>
              <div className="rounded-xl border border-[#16161D] bg-black/30 p-4">
                {form.selfieDocumentUrl ? (
                  <img src={form.selfieDocumentUrl} alt="Selfie" className="w-full h-36 object-cover rounded-lg mb-3" />
                ) : (
                  <div className="w-full h-36 rounded-lg bg-white/5 mb-3" />
                )}
                <input type="file" accept="image/*" onChange={(e) => onFile(e.target.files?.[0] || null, 'selfie')} className="block w-full text-xs text-zinc-400 file:bg-indigo-600 file:text-white file:px-3 file:py-1.5 file:rounded-lg file:border-0 file:mr-3 hover:file:opacity-90" />
              </div>
            </div>
          </div>

          {error && (
            <div className="mt-6 text-sm text-red-400 bg-red-500/10 border border-red-400/20 rounded-xl p-3">{error}</div>
          )}

          <div className="mt-8 flex justify-end">
            <button
              type="submit"
              disabled={!canSubmit || submitting}
              className={`px-6 py-3 rounded-xl text-sm font-semibold transition-all ${
                !canSubmit || submitting
                  ? 'bg-zinc-800 text-zinc-500 cursor-not-allowed'
                  : 'bg-gradient-to-r from-indigo-600 to-violet-600 text-white hover:scale-105 shadow-lg'
              }`}
            >
              {submitting ? 'Submitting…' : 'Submit for review'}
            </button>
          </div>
        </form>
      </div>
    </div>
  );
};

export default function PageGuarded() {
  // Guard this page to creators only via HasRole (route is already protected as well)
  return (
    <HasRole role="CREATOR" fallback={<div className="bg-[#08080A] min-h-screen py-24"><div className="max-w-4xl mx-auto px-6 text-zinc-400">You must be a creator to access verification.</div></div>}>
      <CreatorVerificationPage />
    </HasRole>
  );
}
