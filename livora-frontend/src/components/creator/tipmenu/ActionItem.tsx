import React, { useState } from 'react';
import { safeRender } from '@/utils/safeRender';

export interface TipAction {
  id: string;
  amount: number;
  description: string;
  isEnabled: boolean;
  categoryId?: string;
  sortOrder?: number;
}

interface ActionItemProps {
  action: TipAction;
  onUpdate: (id: string, updates: Partial<TipAction>) => Promise<void>;
  onDelete: (id: string) => Promise<void>;
}

const ActionItem: React.FC<ActionItemProps> = ({ action, onUpdate, onDelete }) => {
  const [isEditing, setIsEditing] = useState(false);
  const [editAmount, setEditAmount] = useState(action.amount);
  const [editDescription, setEditDescription] = useState(action.description);
  const [isSaving, setIsSaving] = useState(false);

  const handleSave = async () => {
    if (!editDescription.trim()) return;
    setIsSaving(true);
    try {
      await onUpdate(action.id, {
        amount: editAmount,
        description: editDescription.trim(),
        categoryId: action.categoryId,
      });
      setIsEditing(false);
    } finally {
      setIsSaving(false);
    }
  };

  const handleCancel = () => {
    setIsEditing(false);
    setEditAmount(action.amount);
    setEditDescription(action.description);
  };

  if (isEditing) {
    return (
      <div className="bg-black/40 backdrop-blur-md border border-purple-500/30 rounded-xl p-4 space-y-3">
        <div className="flex gap-3">
          <input
            type="number"
            value={editAmount}
            onChange={(e) => setEditAmount(Number(e.target.value))}
            className="w-24 bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-white text-sm focus:outline-none focus:border-purple-500/50"
            min="1"
          />
          <input
            type="text"
            value={editDescription}
            onChange={(e) => setEditDescription(e.target.value)}
            className="flex-1 bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-white text-sm focus:outline-none focus:border-purple-500/50"
          />
        </div>
        <div className="flex gap-2 justify-end">
          <button
            onClick={handleCancel}
            className="px-3 py-1.5 text-xs text-zinc-400 hover:text-white bg-white/5 hover:bg-white/10 rounded-lg transition-colors"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={isSaving || !editDescription.trim()}
            className="px-3 py-1.5 text-xs text-white bg-purple-600 hover:bg-purple-500 disabled:opacity-50 rounded-lg transition-colors font-medium"
          >
            {isSaving ? 'Saving...' : 'Save'}
          </button>
        </div>
      </div>
    );
  }

  return (
    <div
      className={`group flex items-center gap-4 transition-all duration-200 rounded-xl p-3 ${
        action.isEnabled
          ? 'bg-black/30 border border-white/5 hover:border-purple-400/30 hover:bg-black/40'
          : 'bg-black/20 border border-white/5 opacity-50'
      }`}
    >
      <div className={`w-16 h-9 rounded-lg flex items-center justify-center font-mono text-sm shrink-0 ${
        action.isEnabled
          ? 'bg-purple-500/20 text-purple-400 font-semibold'
          : 'bg-white/5 text-zinc-500'
      }`}>
        {safeRender(action.amount)}
      </div>
      <div className="min-w-0 flex-1">
        <p className={`text-sm font-medium truncate ${action.isEnabled ? 'text-white' : 'text-zinc-500'}`}>
          {safeRender(action.description)}
        </p>
      </div>
      <div className="flex items-center gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
        <button
          onClick={() => onUpdate(action.id, { isEnabled: !action.isEnabled })}
          className={`p-1.5 rounded-lg transition-colors ${
            action.isEnabled ? 'text-emerald-500 hover:bg-emerald-500/10' : 'text-zinc-500 hover:bg-white/10'
          }`}
          title={action.isEnabled ? 'Disable' : 'Enable'}
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            {action.isEnabled ? (
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M5 13l4 4L19 7" />
            ) : (
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M18.364 18.364A9 9 0 005.636 5.636m12.728 12.728A9 9 0 015.636 5.636m12.728 12.728L5.636 5.636" />
            )}
          </svg>
        </button>
        <button
          onClick={() => setIsEditing(true)}
          className="p-1.5 text-zinc-400 hover:text-white hover:bg-white/10 rounded-lg transition-colors"
          title="Edit"
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M15.232 5.232l3.536 3.536m-2.036-5.036a2.5 2.5 0 113.536 3.536L6.5 21.036H3v-3.572L16.732 3.732z" />
          </svg>
        </button>
        <button
          onClick={() => onDelete(action.id)}
          className="p-1.5 text-zinc-500 hover:text-red-500 hover:bg-red-500/10 rounded-lg transition-colors"
          title="Delete"
        >
          <svg className="w-3.5 h-3.5" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 7l-.867 12.142A2 2 0 0116.138 21H7.862a2 2 0 01-1.995-1.858L5 7m5 4v6m4-6v6m1-10V4a1 1 0 00-1-1h-4a1 1 0 00-1 1v3M4 7h16" />
          </svg>
        </button>
      </div>
    </div>
  );
};

export default ActionItem;
