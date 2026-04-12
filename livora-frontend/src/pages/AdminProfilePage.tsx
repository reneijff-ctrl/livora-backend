import React, { useState } from 'react';
import SEO from '../components/SEO';
import { useAuth } from '../auth/useAuth';
import { showToast } from '../components/Toast';
import apiClient from '../api/apiClient';

const AdminProfilePage: React.FC = () => {
  const { user } = useAuth();
  const [name, setName] = useState(user?.username ?? '');
  const [saving, setSaving] = useState(false);

  const handleSave = async () => {
    try {
      setSaving(true);
      await apiClient.patch('/users/me', { username: name });
      showToast('Profile updated', 'success');
    } catch {
      showToast('Failed to update profile', 'error');
    } finally {
      setSaving(false);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-6">
      <SEO title="Admin Profile" />
      <div className="max-w-lg mx-auto">
        <h1 className="text-2xl font-bold mb-6">Admin Profile</h1>
        <div className="bg-zinc-900 rounded-xl p-6 space-y-4">
          <div>
            <span className="text-zinc-400 text-sm">Email</span>
            <p className="text-white font-medium">{user?.email}</p>
          </div>
          <div>
            <span className="text-zinc-400 text-sm">Admin Role</span>
            <p className="text-white font-medium">{user?.adminRole ?? '—'}</p>
          </div>
          <div>
            <label className="text-zinc-400 text-sm block mb-1">Display Name</label>
            <input
              className="w-full bg-zinc-800 border border-zinc-700 rounded-lg px-3 py-2 text-white focus:outline-none focus:border-purple-500"
              value={name}
              onChange={(e) => setName(e.target.value)}
              placeholder="Your name"
            />
          </div>
          <button
            onClick={handleSave}
            disabled={saving}
            className="w-full bg-purple-600 hover:bg-purple-700 disabled:opacity-50 text-white font-semibold py-2 rounded-lg transition"
          >
            {saving ? 'Saving…' : 'Save Changes'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default AdminProfilePage;
