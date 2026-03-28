import React, { useState, useEffect, useCallback } from 'react';
import apiClient from '@/api/apiClient';
import { safeRender } from '@/utils/safeRender';

interface Milestone {
  id: string;
  title: string;
  targetAmount: number;
  reached: boolean;
}

interface GoalGroup {
  id: string;
  title: string;
  targetAmount: number;
  currentAmount: number;
  active: boolean;
  autoReset: boolean;
  orderIndex: number;
  milestones: Milestone[];
}

const GoalGroupBuilder: React.FC = () => {
  const [groups, setGroups] = useState<GoalGroup[]>([]);
  const [loading, setLoading] = useState(false);
  const [newGroupTitle, setNewGroupTitle] = useState('');
  const [newGroupTarget, setNewGroupTarget] = useState(1000);
  const [showAddGroup, setShowAddGroup] = useState(false);
  const [expandedGroups, setExpandedGroups] = useState<Set<string>>(new Set());

  // Inline milestone form state per group
  const [addingMilestoneTo, setAddingMilestoneTo] = useState<string | null>(null);
  const [milestoneTitle, setMilestoneTitle] = useState('');
  const [milestoneTarget, setMilestoneTarget] = useState(0);

  const fetchGroups = useCallback(async () => {
    try {
      const res = await apiClient.get('/creator/tip-goal-groups', { _skipToast: true } as any);
      setGroups(res.data || []);
    } catch {
      setGroups([]);
    }
  }, []);

  useEffect(() => {
    fetchGroups();
  }, [fetchGroups]);

  const handleCreateGroup = async () => {
    if (!newGroupTitle || newGroupTarget <= 0) return;
    setLoading(true);
    try {
      await apiClient.post('/creator/tip-goal-groups', {
        title: newGroupTitle,
        targetAmount: newGroupTarget,
        active: groups.length === 0,
        autoReset: false,
        orderIndex: groups.length,
      });
      setNewGroupTitle('');
      setNewGroupTarget(1000);
      setShowAddGroup(false);
      fetchGroups();
    } catch (err) {
      console.error('Failed to create goal group', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteGroup = async (id: string) => {
    if (!confirm('Delete this goal group and unlink all milestones?')) return;
    try {
      await apiClient.delete(`/creator/tip-goal-groups/${id}`);
      fetchGroups();
    } catch (err) {
      console.error('Failed to delete goal group', err);
    }
  };

  const handleActivateGroup = async (group: GoalGroup) => {
    try {
      await apiClient.put(`/creator/tip-goal-groups/${group.id}`, {
        ...group,
        active: true,
      });
      fetchGroups();
    } catch (err) {
      console.error('Failed to activate goal group', err);
    }
  };

  const handleDeactivateGroup = async (group: GoalGroup) => {
    try {
      await apiClient.put(`/creator/tip-goal-groups/${group.id}`, {
        ...group,
        active: false,
      });
      fetchGroups();
    } catch (err) {
      console.error('Failed to deactivate goal group', err);
    }
  };

  const handleResetGroup = async (id: string) => {
    if (!confirm('Reset goal progress to 0?')) return;
    try {
      await apiClient.patch(`/creator/tip-goal-groups/${id}/reset`);
      fetchGroups();
    } catch (err) {
      console.error('Failed to reset goal group', err);
    }
  };

  const handleAddMilestone = async (groupId: string) => {
    if (!milestoneTitle || milestoneTarget <= 0) return;
    setLoading(true);
    try {
      await apiClient.post(`/creator/tip-goal-groups/${groupId}/milestones`, {
        title: milestoneTitle,
        targetAmount: milestoneTarget,
      });
      setAddingMilestoneTo(null);
      setMilestoneTitle('');
      setMilestoneTarget(0);
      fetchGroups();
    } catch (err) {
      console.error('Failed to add milestone', err);
    } finally {
      setLoading(false);
    }
  };

  const handleDeleteMilestone = async (milestoneId: string) => {
    try {
      await apiClient.delete(`/creator/tip-goal-groups/milestones/${milestoneId}`);
      fetchGroups();
    } catch (err) {
      console.error('Failed to delete milestone', err);
    }
  };

  const toggleExpanded = (id: string) => {
    setExpandedGroups(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  return (
    <div className="bg-black/40 backdrop-blur-xl border border-white/5 rounded-3xl p-8 shadow-2xl shadow-black/40">
      <div className="flex justify-between items-center mb-6">
        <h2 className="font-bold text-zinc-100 flex items-center gap-2">
          <span className="text-xl">🎯</span> Goal Groups
        </h2>
        {!showAddGroup && (
          <button
            onClick={() => setShowAddGroup(true)}
            className="text-[10px] font-black uppercase tracking-widest bg-indigo-500/10 text-indigo-400 px-3 py-1.5 rounded-lg border border-indigo-500/20 hover:bg-indigo-500/20 transition-all"
          >
            + Add Goal Group
          </button>
        )}
      </div>

      {/* Add Group Form */}
      {showAddGroup && (
        <div className="mb-6 p-4 rounded-xl border border-indigo-500/20 bg-indigo-500/5">
          <h3 className="text-xs font-bold text-zinc-500 uppercase tracking-wider mb-4">New Goal Group</h3>
          <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
            <div>
              <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider mb-1 block">Title</label>
              <input
                type="text"
                value={newGroupTitle}
                onChange={(e) => setNewGroupTitle(e.target.value)}
                placeholder="e.g. Cum Show"
                className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2.5 focus:outline-none focus:border-purple-500/50 transition-colors text-white placeholder:text-zinc-700"
              />
            </div>
            <div>
              <label className="text-xs font-bold text-zinc-500 uppercase tracking-wider mb-1 block">Main Goal Target</label>
              <input
                type="number"
                value={newGroupTarget}
                onChange={(e) => setNewGroupTarget(Number(e.target.value))}
                min="1"
                className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2.5 focus:outline-none focus:border-purple-500/50 transition-colors text-white"
              />
            </div>
            <div className="flex items-end gap-2">
              <button
                onClick={handleCreateGroup}
                disabled={loading || !newGroupTitle || newGroupTarget <= 0}
                className="px-4 py-2.5 bg-indigo-600 hover:bg-indigo-700 disabled:bg-zinc-800 disabled:text-zinc-600 text-white rounded-xl font-bold transition-all"
              >
                {loading ? 'Creating...' : '✨ Create'}
              </button>
              <button
                onClick={() => { setShowAddGroup(false); setNewGroupTitle(''); setNewGroupTarget(1000); }}
                className="px-4 py-2.5 bg-white/5 hover:bg-white/10 text-white rounded-xl font-bold transition-all"
              >
                Cancel
              </button>
            </div>
          </div>
        </div>
      )}

      {/* Group List */}
      <div className="space-y-4">
        {groups.length === 0 && !showAddGroup && (
          <p className="text-zinc-600 text-sm text-center py-8">No goal groups yet. Create one to set up milestone-based goals.</p>
        )}

        {groups.map(group => {
          const isExpanded = expandedGroups.has(group.id);
          const percentage = group.targetAmount > 0 ? Math.min(100, (group.currentAmount / group.targetAmount) * 100) : 0;

          return (
            <div key={group.id} className={`rounded-xl border transition-all ${group.active ? 'border-indigo-500/20 bg-indigo-500/5' : 'border-white/5 bg-[#08080A]'}`}>
              {/* Group Header */}
              <div
                className="flex items-center justify-between p-4 cursor-pointer"
                onClick={() => toggleExpanded(group.id)}
              >
                <div className="flex items-center gap-3 flex-1">
                  <span className="text-zinc-500 text-sm">{isExpanded ? '▼' : '▶'}</span>
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-1">
                      <span className="font-bold text-white">{safeRender(group.title)}</span>
                      {group.active && (
                        <span className="text-[9px] font-black bg-emerald-500 text-white px-1.5 py-0.5 rounded uppercase tracking-tighter">ACTIVE</span>
                      )}
                      <span className="text-xs text-zinc-500">{group.milestones.length} milestones</span>
                    </div>
                    {/* Progress bar */}
                    <div className="h-2 bg-zinc-800 rounded-full overflow-hidden">
                      <div
                        className="h-2 bg-gradient-to-r from-purple-500 via-pink-500 to-purple-400 rounded-full transition-all duration-700 ease-out"
                        style={{ width: `${percentage}%` }}
                      />
                    </div>
                    <div className="text-xs text-gray-400 mt-1">
                      {safeRender(group.currentAmount)} / {safeRender(group.targetAmount)} Tokens ({safeRender(Math.round(percentage))}%)
                    </div>
                  </div>
                </div>
                <div className="flex items-center gap-2" onClick={(e) => e.stopPropagation()}>
                  {group.active ? (
                    <button
                      onClick={() => handleDeactivateGroup(group)}
                      className="text-[10px] font-bold text-zinc-500 hover:text-red-400 transition-colors"
                    >
                      Deactivate
                    </button>
                  ) : (
                    <button
                      onClick={() => handleActivateGroup(group)}
                      className="text-[10px] font-bold text-zinc-500 hover:text-emerald-400 transition-colors"
                    >
                      Activate
                    </button>
                  )}
                  <button
                    onClick={() => handleResetGroup(group.id)}
                    className="text-[10px] font-bold text-zinc-500 hover:text-orange-400 transition-colors"
                  >
                    Reset
                  </button>
                  <button
                    onClick={() => handleDeleteGroup(group.id)}
                    className="p-2 hover:bg-red-500/10 rounded-lg text-zinc-400 hover:text-red-500 transition-all"
                  >
                    <svg className="w-4 h-4" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" /></svg>
                  </button>
                </div>
              </div>

              {/* Expanded: Milestones */}
              {isExpanded && (
                <div className="px-4 pb-4 border-t border-white/5 pt-3">
                  <div className="space-y-2">
                    {group.milestones.map(milestone => (
                      <div key={milestone.id} className="flex items-center justify-between px-3 py-2 rounded-lg hover:bg-white/5 transition">
                        <div className="flex items-center gap-3">
                          <span className="text-sm">{milestone.reached ? '✅' : (group.currentAmount > 0 && group.currentAmount < milestone.targetAmount ? '🔥' : '⚪')}</span>
                          <span className={`text-sm ${milestone.reached ? 'text-white' : 'text-zinc-400'}`}>{safeRender(milestone.title)}</span>
                        </div>
                        <div className="flex items-center gap-3">
                          <span className="px-2 py-0.5 rounded-full bg-indigo-600/20 text-indigo-300 text-xs font-semibold border border-indigo-500/20">
                            {safeRender(milestone.targetAmount)} tokens
                          </span>
                          <button
                            onClick={() => handleDeleteMilestone(milestone.id)}
                            className="p-1 hover:bg-red-500/10 rounded text-zinc-500 hover:text-red-500 transition-all"
                          >
                            <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24"><path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" /></svg>
                          </button>
                        </div>
                      </div>
                    ))}

                    {group.milestones.length === 0 && (
                      <p className="text-zinc-600 text-xs text-center py-2">No milestones yet.</p>
                    )}
                  </div>

                  {/* Add Milestone Inline */}
                  {addingMilestoneTo === group.id ? (
                    <div className="mt-3 pt-3 border-t border-white/5 flex items-end gap-3">
                      <div className="flex-1">
                        <label className="text-[10px] font-bold text-zinc-500 uppercase tracking-wider mb-1 block">Milestone Title</label>
                        <input
                          type="text"
                          value={milestoneTitle}
                          onChange={(e) => setMilestoneTitle(e.target.value)}
                          placeholder="e.g. Oil Show"
                          className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-purple-500/50 transition-colors text-white placeholder:text-zinc-700"
                        />
                      </div>
                      <div className="w-32">
                        <label className="text-[10px] font-bold text-zinc-500 uppercase tracking-wider mb-1 block">Tokens</label>
                        <input
                          type="number"
                          value={milestoneTarget}
                          onChange={(e) => setMilestoneTarget(Number(e.target.value))}
                          min="1"
                          className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-sm focus:outline-none focus:border-purple-500/50 transition-colors text-white"
                        />
                      </div>
                      <button
                        onClick={() => handleAddMilestone(group.id)}
                        disabled={loading || !milestoneTitle || milestoneTarget <= 0}
                        className="px-3 py-2 bg-indigo-600 hover:bg-indigo-700 disabled:bg-zinc-800 disabled:text-zinc-600 text-white rounded-lg text-sm font-bold transition-all"
                      >
                        Add
                      </button>
                      <button
                        onClick={() => { setAddingMilestoneTo(null); setMilestoneTitle(''); setMilestoneTarget(0); }}
                        className="px-3 py-2 bg-white/5 hover:bg-white/10 text-white rounded-lg text-sm font-bold transition-all"
                      >
                        ✕
                      </button>
                    </div>
                  ) : (
                    <button
                      onClick={() => { setAddingMilestoneTo(group.id); setMilestoneTitle(''); setMilestoneTarget(0); }}
                      className="mt-3 text-xs font-bold text-indigo-400 hover:text-indigo-300 transition-colors"
                    >
                      + Add Milestone
                    </button>
                  )}
                </div>
              )}
            </div>
          );
        })}
      </div>
    </div>
  );
};

export default GoalGroupBuilder;
