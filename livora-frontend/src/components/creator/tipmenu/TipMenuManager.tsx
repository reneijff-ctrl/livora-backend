import React, { useState, useEffect, useCallback } from 'react';
import apiClient from '@/api/apiClient';
import CategoryCard from './CategoryCard';
import ActionItem from './ActionItem';
import type { TipMenuCategory } from './CategoryCard';
import type { TipAction } from './ActionItem';

const TipMenuManager: React.FC = () => {
  const [categories, setCategories] = useState<TipMenuCategory[]>([]);
  const [tipActions, setTipActions] = useState<TipAction[]>([]);
  const [collapsedCategories, setCollapsedCategories] = useState<Set<string>>(new Set());
  const [isFetching, setIsFetching] = useState(false);

  // Add Menu state
  const [showAddMenu, setShowAddMenu] = useState(false);
  const [newMenuTitle, setNewMenuTitle] = useState('');
  const [isCreatingMenu, setIsCreatingMenu] = useState(false);

  // Fetch data
  const fetchCategories = useCallback(async () => {
    try {
      const res = await apiClient.get('/creator/tip-menu-categories');
      setCategories(res.data);
    } catch (err) {
      console.error('TIP_MENU: Failed to fetch categories', err);
    }
  }, []);

  const fetchActions = useCallback(async () => {
    setIsFetching(true);
    try {
      const res = await apiClient.get('/creator/tip-actions');
      setTipActions(res.data);
    } catch (err) {
      console.error('TIP_MENU: Failed to fetch actions', err);
    } finally {
      setIsFetching(false);
    }
  }, []);

  useEffect(() => {
    fetchCategories();
    fetchActions();
  }, [fetchCategories, fetchActions]);

  // Category operations
  const createCategory = async () => {
    if (!newMenuTitle.trim() || categories.length >= 10) return;
    setIsCreatingMenu(true);
    try {
      const res = await apiClient.post('/creator/tip-menu-categories', {
        title: newMenuTitle.trim(),
        enabled: true,
      });
      setCategories(prev => [...prev, res.data]);
      setNewMenuTitle('');
      setShowAddMenu(false);
    } catch (err) {
      console.error('TIP_MENU: Failed to create category', err);
    } finally {
      setIsCreatingMenu(false);
    }
  };

  const updateCategory = async (id: string, title: string) => {
    try {
      const cat = categories.find(c => c.id === id);
      const res = await apiClient.put(`/creator/tip-menu-categories/${id}`, {
        title,
        enabled: true,
        sortOrder: cat?.sortOrder ?? 0,
      });
      setCategories(prev => prev.map(c => c.id === id ? res.data : c));
    } catch (err) {
      console.error('TIP_MENU: Failed to update category', err);
    }
  };

  const deleteCategory = async (id: string) => {
    if (!confirm('Delete this menu? Actions will become uncategorized.')) return;
    try {
      await apiClient.delete(`/creator/tip-menu-categories/${id}`);
      setCategories(prev => prev.filter(c => c.id !== id));
      setTipActions(prev => prev.map(a => a.categoryId === id ? { ...a, categoryId: undefined } : a));
    } catch (err) {
      console.error('TIP_MENU: Failed to delete category', err);
    }
  };

  const toggleCategory = async (id: string) => {
    try {
      const res = await apiClient.patch(`/creator/tip-menu-categories/${id}/toggle`);
      setCategories(prev => prev.map(c => c.id === id ? res.data : c));
    } catch (err) {
      console.error('TIP_MENU: Failed to toggle category', err);
    }
  };

  const toggleCollapse = (id: string) => {
    setCollapsedCategories(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  // Action operations
  const createAction = async (categoryId: string, amount: number, description: string) => {
    if (tipActions.length >= 30) {
      alert('Maximum 30 actions allowed.');
      return;
    }
    const res = await apiClient.post('/creator/tip-actions', {
      amount,
      description,
      enabled: true,
      categoryId: categoryId || null,
      sortOrder: 0,
    });
    setTipActions(prev => [...prev, res.data]);
  };

  const updateAction = async (id: string, updates: Partial<TipAction>) => {
    try {
      // Transform isEnabled → enabled for backend DTO compatibility
      const { isEnabled, ...rest } = updates;
      const payload = { ...rest, ...(isEnabled !== undefined ? { enabled: isEnabled } : {}) };
      const res = await apiClient.put(`/creator/tip-actions/${id}`, payload);
      setTipActions(prev => prev.map(a => a.id === id ? res.data : a));
    } catch (err) {
      console.error('TIP_MENU: Failed to update action', err);
    }
  };

  const deleteAction = async (id: string) => {
    if (!confirm('Delete this action?')) return;
    try {
      await apiClient.delete(`/creator/tip-actions/${id}`);
      setTipActions(prev => prev.filter(a => a.id !== id));
    } catch (err) {
      console.error('TIP_MENU: Failed to delete action', err);
    }
  };

  // Derived data
  const sortedCategories = [...categories].sort((a, b) => a.sortOrder - b.sortOrder);
  const actionsForCategory = (categoryId: string) =>
    tipActions.filter(a => a.categoryId === categoryId).sort((a, b) => a.amount - b.amount);
  const uncategorizedActions = tipActions.filter(a => !a.categoryId).sort((a, b) => a.amount - b.amount);

  return (
    <div className="bg-black/40 backdrop-blur-xl border border-white/10 rounded-2xl p-6">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <h2 className="font-bold text-zinc-100 flex items-center gap-2">
          <span className="text-xl">💎</span> Tip Menu Builder
        </h2>
        <div className="flex items-center gap-3">
          {isFetching && <span className="text-[10px] text-indigo-400 animate-pulse">Syncing...</span>}
          <span className="text-[10px] text-zinc-500 tabular-nums">{tipActions.length}/30 actions</span>
        </div>
      </div>

      {/* Category Cards */}
      <div className="space-y-4">
        {sortedCategories.map(cat => (
          <CategoryCard
            key={cat.id}
            category={cat}
            actions={actionsForCategory(cat.id)}
            isCollapsed={collapsedCategories.has(cat.id)}
            onToggleCollapse={() => toggleCollapse(cat.id)}
            onUpdateCategory={updateCategory}
            onDeleteCategory={deleteCategory}
            onToggleCategory={toggleCategory}
            onUpdateAction={updateAction}
            onDeleteAction={deleteAction}
            onCreateAction={createAction}
            totalActions={tipActions.length}
          />
        ))}

        {/* Uncategorized Actions */}
        {uncategorizedActions.length > 0 && (
          <div className="bg-black/20 border border-white/5 rounded-2xl p-4">
            <div className="flex items-center gap-2 mb-3">
              <span className="text-xs font-semibold text-zinc-500 uppercase tracking-widest">Uncategorized</span>
              <span className="text-[10px] text-zinc-600 tabular-nums">{uncategorizedActions.length} actions</span>
            </div>
            <div className="space-y-2">
              {uncategorizedActions.map(action => (
                <ActionItem
                  key={action.id}
                  action={action}
                  onUpdate={updateAction}
                  onDelete={deleteAction}
                />
              ))}
            </div>
          </div>
        )}

        {/* Empty State */}
        {categories.length === 0 && tipActions.length === 0 && !isFetching && (
          <div className="text-center py-16 border border-dashed border-white/10 rounded-2xl">
            <p className="text-zinc-500 text-sm mb-1">No tip menus created yet.</p>
            <p className="text-zinc-600 text-xs">Click "Add Menu" to start building your tip menu.</p>
          </div>
        )}
      </div>

      {/* Add Menu Button */}
      <div className="mt-6">
        {showAddMenu ? (
          <div className="bg-black/30 border border-white/10 rounded-xl p-4 flex gap-3 items-end">
            <div className="flex-1">
              <label className="text-[10px] text-zinc-500 uppercase tracking-wider mb-1 block">Menu Name</label>
              <input
                type="text"
                value={newMenuTitle}
                onChange={(e) => setNewMenuTitle(e.target.value)}
                placeholder="Tip menu name..."
                className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-2.5 text-white text-sm focus:outline-none focus:border-purple-500/50 transition-colors"
                maxLength={100}
                autoFocus
                onKeyDown={(e) => {
                  if (e.key === 'Enter') createCategory();
                  if (e.key === 'Escape') { setShowAddMenu(false); setNewMenuTitle(''); }
                }}
              />
            </div>
            <button
              onClick={() => { setShowAddMenu(false); setNewMenuTitle(''); }}
              className="px-4 py-2.5 text-sm text-zinc-400 hover:text-white bg-white/5 hover:bg-white/10 rounded-lg transition-colors"
            >
              Cancel
            </button>
            <button
              onClick={createCategory}
              disabled={isCreatingMenu || !newMenuTitle.trim() || categories.length >= 10}
              className="px-6 py-2.5 text-sm text-white bg-gradient-to-r from-purple-600 to-pink-600 hover:opacity-90 disabled:opacity-50 rounded-lg transition-all font-semibold"
            >
              {isCreatingMenu ? 'Creating...' : 'Create Menu'}
            </button>
          </div>
        ) : (
          <button
            onClick={() => setShowAddMenu(true)}
            disabled={categories.length >= 10}
            className="w-full py-3 text-sm text-zinc-400 hover:text-purple-400 border border-dashed border-white/10 hover:border-purple-500/30 rounded-xl transition-all disabled:opacity-30 flex items-center justify-center gap-2"
          >
            <span className="text-lg">+</span> Add Menu ({categories.length}/10)
          </button>
        )}
      </div>
    </div>
  );
};

export default TipMenuManager;
