import React, { useState } from 'react';
import { safeRender } from '@/utils/safeRender';
import ActionItem from './ActionItem';
import type { TipAction } from './ActionItem';

export interface TipMenuCategory {
  id: string;
  title: string;
  sortOrder: number;
  enabled: boolean;
}

interface CategoryCardProps {
  category: TipMenuCategory;
  actions: TipAction[];
  isCollapsed: boolean;
  onToggleCollapse: () => void;
  onUpdateCategory: (id: string, title: string) => Promise<void>;
  onDeleteCategory: (id: string) => Promise<void>;
  onToggleCategory: (id: string) => Promise<void>;
  onUpdateAction: (id: string, updates: Partial<TipAction>) => Promise<void>;
  onDeleteAction: (id: string) => Promise<void>;
  onCreateAction: (categoryId: string, amount: number, description: string) => Promise<void>;
  totalActions: number;
}

const CategoryCard: React.FC<CategoryCardProps> = ({
  category,
  actions,
  isCollapsed,
  onToggleCollapse,
  onUpdateCategory,
  onDeleteCategory,
  onToggleCategory,
  onUpdateAction,
  onDeleteAction,
  onCreateAction,
  totalActions,
}) => {
  const [isEditingTitle, setIsEditingTitle] = useState(false);
  const [editTitle, setEditTitle] = useState(category.title);
  const [showAddAction, setShowAddAction] = useState(false);
  const [newAmount, setNewAmount] = useState(100);
  const [newDescription, setNewDescription] = useState('');
  const [isCreating, setIsCreating] = useState(false);

  const handleSaveTitle = async () => {
    if (!editTitle.trim()) return;
    await onUpdateCategory(category.id, editTitle.trim());
    setIsEditingTitle(false);
  };

  const handleCancelEdit = () => {
    setIsEditingTitle(false);
    setEditTitle(category.title);
  };

  const handleCreateAction = async () => {
    if (!newDescription.trim() || totalActions >= 30) return;
    setIsCreating(true);
    try {
      await onCreateAction(category.id, newAmount, newDescription.trim());
      setNewAmount(100);
      setNewDescription('');
      setShowAddAction(false);
    } finally {
      setIsCreating(false);
    }
  };

  return (
    <div className="bg-black/30 backdrop-blur-md border border-white/10 rounded-2xl overflow-hidden transition-all duration-200 hover:border-white/15">
      {/* Category Header */}
      <div className="flex items-center gap-3 p-4 group">
        <button
          onClick={onToggleCollapse}
          className="p-1 rounded-lg hover:bg-white/10 transition-colors"
        >
          <svg
            className={`w-4 h-4 text-zinc-400 transition-transform duration-200 ${isCollapsed ? '' : 'rotate-90'}`}
            fill="none" stroke="currentColor" viewBox="0 0 24 24"
          >
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9 5l7 7-7 7" />
          </svg>
        </button>

        {isEditingTitle ? (
          <div className="flex-1 flex items-center gap-2">
            <input
              type="text"
              value={editTitle}
              onChange={(e) => setEditTitle(e.target.value)}
              className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-1.5 text-white text-sm focus:outline-none focus:border-purple-500/50"
              maxLength={100}
              autoFocus
              onKeyDown={(e) => {
                if (e.key === 'Enter') handleSaveTitle();
                if (e.key === 'Escape') handleCancelEdit();
              }}
            />
            <button onClick={handleSaveTitle} className="text-emerald-400 hover:text-emerald-300 text-xs font-bold">Save</button>
            <button onClick={handleCancelEdit} className="text-zinc-500 hover:text-zinc-300 text-xs">Cancel</button>
          </div>
        ) : (
          <>
            <span className="text-sm font-semibold text-zinc-200 flex-1">{safeRender(category.title)}</span>
            <span className="text-[10px] text-zinc-500 tabular-nums">{actions.length} actions</span>
            <button
              onClick={() => onToggleCategory(category.id)}
              className={`px-2 py-1 text-xs rounded-md transition-colors ${
                category.enabled
                  ? 'bg-green-500/20 text-green-400 border border-green-400/30'
                  : 'bg-red-500/20 text-red-400 border border-red-400/30'
              }`}
            >
              {category.enabled ? 'Active' : 'Inactive'}
            </button>
            <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
              <button
                onClick={() => { setIsEditingTitle(true); setEditTitle(category.title); }}
                className="p-1.5 text-zinc-500 hover:text-white hover:bg-white/10 rounded-lg transition-colors"
                title="Edit menu name"
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
                </svg>
              </button>
              <button
                onClick={() => onDeleteCategory(category.id)}
                className="p-1.5 text-zinc-500 hover:text-red-500 hover:bg-red-500/10 rounded-lg transition-colors"
                title="Delete menu"
              >
                <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                  <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
                </svg>
              </button>
            </div>
          </>
        )}
      </div>

      {/* Actions List + Add Action */}
      {!isCollapsed && (
        <div className="px-4 pb-4">
          {actions.length > 0 ? (
            <div className="space-y-2 mb-3">
              {actions.map(action => (
                <ActionItem
                  key={action.id}
                  action={action}
                  onUpdate={onUpdateAction}
                  onDelete={onDeleteAction}
                />
              ))}
            </div>
          ) : (
            <div className="text-center py-4 mb-3 border border-dashed border-white/10 rounded-xl">
              <p className="text-zinc-600 text-xs">No actions in this menu yet.</p>
            </div>
          )}

          {/* Inline Add Action */}
          {showAddAction ? (
            <div className="bg-black/40 border border-white/10 rounded-xl p-4 space-y-3">
              <div className="flex gap-3">
                <div className="w-24">
                  <label className="text-[10px] text-zinc-500 uppercase tracking-wider mb-1 block">Tokens</label>
                  <input
                    type="number"
                    value={newAmount}
                    onChange={(e) => setNewAmount(Number(e.target.value))}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-white text-sm focus:outline-none focus:border-purple-500/50"
                    min="1"
                  />
                </div>
                <div className="flex-1">
                  <label className="text-[10px] text-zinc-500 uppercase tracking-wider mb-1 block">Description</label>
                  <input
                    type="text"
                    value={newDescription}
                    onChange={(e) => setNewDescription(e.target.value)}
                    placeholder="e.g. 10 Pushups, Spin Wheel..."
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-white text-sm focus:outline-none focus:border-purple-500/50"
                    onKeyDown={(e) => { if (e.key === 'Enter') handleCreateAction(); }}
                  />
                </div>
              </div>
              <div className="flex gap-2 justify-end">
                <button
                  onClick={() => { setShowAddAction(false); setNewDescription(''); setNewAmount(100); }}
                  className="px-3 py-1.5 text-xs text-zinc-400 hover:text-white bg-white/5 hover:bg-white/10 rounded-lg transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleCreateAction}
                  disabled={isCreating || !newDescription.trim() || totalActions >= 30}
                  className="px-4 py-1.5 text-xs text-white bg-gradient-to-r from-purple-600 to-pink-600 hover:opacity-90 disabled:opacity-50 rounded-lg transition-all font-medium"
                >
                  {isCreating ? 'Adding...' : 'Add'}
                </button>
              </div>
            </div>
          ) : (
            <button
              onClick={() => setShowAddAction(true)}
              disabled={totalActions >= 30}
              className="w-full py-2.5 text-xs text-zinc-400 hover:text-purple-400 border border-dashed border-white/10 hover:border-purple-500/30 rounded-xl transition-all disabled:opacity-30"
            >
              + Add Action
            </button>
          )}
        </div>
      )}
    </div>
  );
};

export default CategoryCard;
