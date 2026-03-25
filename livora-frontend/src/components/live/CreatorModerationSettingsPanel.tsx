import React, { useState, useEffect } from 'react';
import apiClient from '@/api/apiClient';
import { showToast } from '@/components/Toast';

interface CreatorModerationSettings {
  creatorUserId?: number;
  bannedWords: string[] | string;
  strictMode: boolean;
  aiHighlightEnabled: boolean;
  autoPinLargeTips: boolean;
}

interface CreatorModerationSettingsPanelProps {
  creatorId: number;
  isOpen: boolean;
  onClose: () => void;
}

const CreatorModerationSettingsPanel: React.FC<CreatorModerationSettingsPanelProps> = ({ creatorId, isOpen, onClose }) => {
  const [settings, setSettings] = useState<CreatorModerationSettings>({
    creatorUserId: creatorId,
    bannedWords: [],
    aiHighlightEnabled: false,
    autoPinLargeTips: false,
    strictMode: false
  });

  const safeBannedWords = Array.isArray(settings?.bannedWords)
    ? settings.bannedWords
    : typeof settings?.bannedWords === "string"
      ? settings.bannedWords.split(",").map(w => w.trim()).filter(Boolean)
      : [];

  const [newWord, setNewWord] = useState('');
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (isOpen) {
      fetchSettings();
    }
  }, [isOpen, creatorId]);

  const fetchSettings = async () => {
    try {
      setLoading(true);
      const response = await apiClient.get<CreatorModerationSettings>(`/stream/moderation/settings/${creatorId}`);
      setSettings(response.data);
    } catch (error) {
      console.error('Failed to fetch moderation settings', error);
      showToast('Failed to load settings', 'error');
    } finally {
      setLoading(false);
    }
  };

  const handleSave = async () => {
    try {
      setSaving(true);
      await apiClient.post('/stream/moderation/settings', settings);
      showToast('Settings saved successfully', 'success');
    } catch (error) {
      console.error('Failed to save moderation settings', error);
      showToast('Failed to save settings', 'error');
    } finally {
      setSaving(false);
    }
  };

  const addWord = () => {
    const word = newWord.trim().toLowerCase();
    if (word && !safeBannedWords.includes(word)) {
      setSettings(prev => ({
        ...prev,
        bannedWords: [...safeBannedWords, word]
      }));
      setNewWord('');
    }
  };

  const removeWord = (wordToRemove: string) => {
    setSettings(prev => ({
      ...prev,
      bannedWords: safeBannedWords.filter(w => w !== wordToRemove)
    }));
  };

  const toggleStrictMode = () => {
    setSettings(prev => ({
      ...prev,
      strictMode: !prev.strictMode
    }));
  };

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-[110] flex items-center justify-center p-4 bg-black/60 backdrop-blur-sm animate-in fade-in duration-300">
      <div className="w-full max-w-md glass-panel border border-white/10 shadow-2xl overflow-hidden flex flex-col max-h-[80vh] animate-in zoom-in-95 duration-300">
        {/* Header */}
        <div className="p-4 border-b border-white/10 flex justify-between items-center bg-white/5">
          <div>
            <h2 className="text-xl font-bold text-white tracking-tight">Moderation Settings</h2>
            <p className="text-xs text-white/50">Configure your stream safety rules</p>
          </div>
          <button onClick={onClose} className="text-white/40 hover:text-white transition-colors">
            <svg className="w-6 h-6" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
            </svg>
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 overflow-y-auto p-6 space-y-6 custom-scrollbar">
          {loading ? (
            <div className="flex justify-center py-12">
              <div className="w-8 h-8 border-2 border-indigo-500 border-t-transparent rounded-full animate-spin"></div>
            </div>
          ) : (
            <>
              {/* Strict Mode Toggle */}
              <div className="flex items-center justify-between p-4 rounded-2xl bg-white/5 border border-white/5">
                <div>
                  <h3 className="text-sm font-bold text-white">Strict Mode</h3>
                  <p className="text-[10px] text-white/40 max-w-[200px]">
                    Lower thresholds for caps and character repetition.
                  </p>
                </div>
                <button
                  onClick={toggleStrictMode}
                  className={`relative inline-flex h-6 w-11 items-center rounded-full transition-colors duration-200 focus:outline-none ${
                    settings.strictMode ? 'bg-indigo-500' : 'bg-zinc-700'
                  }`}
                >
                  <span
                    className={`inline-block h-4 w-4 transform rounded-full bg-white transition-transform duration-200 ${
                      settings.strictMode ? 'translate-x-6' : 'translate-x-1'
                    }`}
                  />
                </button>
              </div>

              {/* Banned Words Section */}
              <div className="space-y-3">
                <h3 className="text-sm font-bold text-white flex items-center gap-2">
                  <svg className="w-4 h-4 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                    <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 9v2m0 4h.01m-6.938 4h13.856c1.54 0 2.502-1.667 1.732-3L13.732 4c-.77-1.333-2.694-1.333-3.464 0L3.34 16c-.77 1.333.192 3 1.732 3z" />
                  </svg>
                  Custom Banned Words
                </h3>
                
                <div className="flex gap-2">
                  <input
                    type="text"
                    value={newWord}
                    onChange={(e) => setNewWord(e.target.value)}
                    onKeyDown={(e) => e.key === 'Enter' && addWord()}
                    placeholder="Add a word..."
                    className="flex-1 bg-white/5 border border-white/10 rounded-xl px-4 py-2 text-sm text-white placeholder:text-white/20 focus:outline-none focus:border-indigo-500/50 transition-colors"
                  />
                  <button
                    onClick={addWord}
                    className="px-4 py-2 bg-indigo-500 hover:bg-indigo-600 text-white text-sm font-bold rounded-xl transition-colors"
                  >
                    Add
                  </button>
                </div>

                <div className="flex flex-wrap gap-2 pt-2">
                  {safeBannedWords.length === 0 ? (
                    <p className="text-xs text-white/20 italic w-full text-center py-4">No custom words added yet</p>
                  ) : (
                    safeBannedWords.map(word => (
                      <span
                        key={word}
                        className="inline-flex items-center gap-1 px-3 py-1 rounded-full bg-white/10 border border-white/10 text-xs text-white"
                      >
                        {word}
                        <button
                          onClick={() => removeWord(word)}
                          className="hover:text-red-400 transition-colors"
                        >
                          <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M6 18L18 6M6 6l12 12" />
                          </svg>
                        </button>
                      </span>
                    ))
                  )}
                </div>
              </div>
            </>
          )}
        </div>

        {/* Footer */}
        <div className="p-4 border-t border-white/10 bg-white/5 flex gap-3">
          <button
            onClick={onClose}
            className="flex-1 px-4 py-2.5 rounded-xl border border-white/10 text-white/70 hover:bg-white/5 transition-colors text-sm font-bold"
          >
            Cancel
          </button>
          <button
            onClick={handleSave}
            disabled={saving || loading}
            className="flex-1 px-4 py-2.5 rounded-xl bg-indigo-500 hover:bg-indigo-600 disabled:opacity-50 disabled:cursor-not-allowed text-white text-sm font-bold transition-all shadow-lg shadow-indigo-500/20 flex items-center justify-center gap-2"
          >
            {saving ? (
              <>
                <div className="w-4 h-4 border-2 border-white border-t-transparent rounded-full animate-spin"></div>
                Saving...
              </>
            ) : 'Save Changes'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default CreatorModerationSettingsPanel;
