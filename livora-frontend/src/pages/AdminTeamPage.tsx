import React, { useEffect, useState } from 'react';
import SEO from '../components/SEO';
import { showToast } from '../components/Toast';
import adminService, { AdminUser } from '../api/adminService';
import { usePermissions } from '../hooks/usePermissions';

const ADMIN_ROLES = ['CEO', 'ADMIN', 'MODERATOR', 'SUPPORT'];

const AdminTeamPage: React.FC = () => {
  const [admins, setAdmins] = useState<AdminUser[]>([]);
  const [loading, setLoading] = useState(true);
  const [updating, setUpdating] = useState<number | null>(null);
  const { hasPermission } = usePermissions();
  const canManage = hasPermission('ADMIN_MANAGE');

  useEffect(() => {
    fetchAdmins();
  }, []);

  const fetchAdmins = async () => {
    try {
      setLoading(true);
      const response = await adminService.getAdmins();
      console.log('Admins response:', response);
      const data = (response as any).content || (response as any).data || response;
      setAdmins(Array.isArray(data) ? data : []);
    } catch {
      showToast('Failed to load admin team', 'error');
    } finally {
      setLoading(false);
    }
  };

  const updateRole = async (id: number, adminRole: string) => {
    try {
      setUpdating(id);
      const updated = await adminService.updateAdminRole(id, adminRole);
      setAdmins((prev) => prev.map((a) => (a.id === updated.id ? updated : a)));
      showToast('Role updated', 'success');
    } catch {
      showToast('Failed to update role', 'error');
    } finally {
      setUpdating(null);
    }
  };

  return (
    <div className="min-h-screen bg-zinc-950 text-white p-6">
      <SEO title="Admin Team" />
      <div className="max-w-3xl mx-auto">
        <h1 className="text-2xl font-bold mb-6">Admin Team</h1>
        {loading ? (
          <p className="text-zinc-400">Loading…</p>
        ) : admins.length === 0 ? (
          <p className="text-zinc-400">No admins found.</p>
        ) : (
          <div className="space-y-3">
            {Array.isArray(admins) && admins.map((admin) => (
              <div
                key={admin.id}
                className="bg-zinc-900 rounded-xl px-5 py-4 flex items-center justify-between gap-4"
              >
                <div>
                  <p className="font-medium">{admin.email}</p>
                  <p className="text-zinc-400 text-sm">{admin.username ?? '—'}</p>
                </div>
                {canManage ? (
                  <select
                    value={admin.adminRole ?? 'ADMIN'}
                    disabled={updating === admin.id}
                    onChange={(e) => updateRole(admin.id, e.target.value)}
                    className="bg-zinc-800 border border-zinc-700 text-white rounded-lg px-3 py-1.5 text-sm focus:outline-none focus:border-purple-500 disabled:opacity-50"
                  >
                    {ADMIN_ROLES.map((r) => (
                      <option key={r} value={r}>
                        {r}
                      </option>
                    ))}
                  </select>
                ) : (
                  <span className="text-zinc-400 text-sm bg-zinc-800 px-3 py-1.5 rounded-lg">
                    {admin.adminRole ?? 'ADMIN'}
                  </span>
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};

export default AdminTeamPage;
