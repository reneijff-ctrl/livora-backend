import { useState, useEffect, useCallback } from 'react';

interface ViewerSettings {
  chatFontSize: number;
  showTipOverlays: boolean;
  showTopTipperBanner: boolean;
  hiddenUserIds: Set<number>;
}

interface UseViewerSettingsResult extends ViewerSettings {
  setChatFontSize: (size: number) => void;
  setShowTipOverlays: (show: boolean) => void;
  setShowTopTipperBanner: (show: boolean) => void;
  toggleHideUser: (userId: number) => void;
  unhideAll: () => void;
}

/**
 * useViewerSettings — Manages viewer preferences persisted to localStorage.
 * Covers: chat font size, tip overlay visibility, top tipper banner, hidden users.
 */
export const useViewerSettings = (): UseViewerSettingsResult => {
  const [chatFontSize, setChatFontSize] = useState(() => {
    try { const v = localStorage.getItem('livora-chat-font-size'); return v ? Number(v) : 14; } catch { return 14; }
  });
  const [showTipOverlays, setShowTipOverlays] = useState(() => {
    try { return localStorage.getItem('livora-hide-tip-overlays') !== 'true'; } catch { return true; }
  });
  const [showTopTipperBanner, setShowTopTipperBanner] = useState(() => {
    try { return localStorage.getItem('livora-hide-top-banner') !== 'true'; } catch { return true; }
  });
  const [hiddenUserIds, setHiddenUserIds] = useState<Set<number>>(() => {
    try {
      const saved = localStorage.getItem('livora-hidden-users');
      return saved ? new Set(JSON.parse(saved) as number[]) : new Set();
    } catch { return new Set(); }
  });

  // Persist to localStorage
  useEffect(() => {
    try { localStorage.setItem('livora-chat-font-size', String(chatFontSize)); } catch {}
  }, [chatFontSize]);
  useEffect(() => {
    try { localStorage.setItem('livora-hide-tip-overlays', showTipOverlays ? 'false' : 'true'); } catch {}
  }, [showTipOverlays]);
  useEffect(() => {
    try { localStorage.setItem('livora-hide-top-banner', showTopTipperBanner ? 'false' : 'true'); } catch {}
  }, [showTopTipperBanner]);
  useEffect(() => {
    try { localStorage.setItem('livora-hidden-users', JSON.stringify([...hiddenUserIds])); } catch {}
  }, [hiddenUserIds]);

  const toggleHideUser = useCallback((userId: number) => {
    setHiddenUserIds(prev => {
      const next = new Set(prev);
      if (next.has(userId)) next.delete(userId);
      else next.add(userId);
      return next;
    });
  }, []);

  const unhideAll = useCallback(() => setHiddenUserIds(new Set()), []);

  return {
    chatFontSize,
    showTipOverlays,
    showTopTipperBanner,
    hiddenUserIds,
    setChatFontSize,
    setShowTipOverlays,
    setShowTopTipperBanner,
    toggleHideUser,
    unhideAll,
  };
};
