import { useState, useEffect, useRef, useCallback } from 'react';
import { getActivePm, getPmMessages, sendPmMessage, markPmAsRead, PmSession, PmMessage } from '@/api/pmService';
import { showToast } from '@/components/Toast';

interface UsePmSessionResult {
  pmSession: PmSession | null;
  pmMessages: PmMessage[];
  pmEnded: boolean;
  activeTab: 'CHAT' | 'PM' | 'USERS';
  handleSendPm: (content: string) => void;
  handlePmTabOpen: () => void;
  handleTabChange: (tab: 'CHAT' | 'PM' | 'USERS') => void;
}

/**
 * usePmSession — Manages private messaging state and WS subscriptions.
 * Handles session lifecycle, message streaming, unread counts, and tab switching.
 */
export const usePmSession = (
  userId: string | undefined,
  connected: boolean,
  subscribe: ((dest: string, cb: (msg: any) => void) => (() => void)) | null | undefined,
  wsSend?: (destination: string, body: any) => void,
): UsePmSessionResult => {
  const [pmSession, setPmSession] = useState<PmSession | null>(null);
  const [pmMessages, setPmMessages] = useState<PmMessage[]>([]);
  const [pmEnded, setPmEnded] = useState(false);
  const [activeTab, setActiveTab] = useState<'CHAT' | 'PM' | 'USERS'>('CHAT');
  const activeTabRef = useRef<'CHAT' | 'PM' | 'USERS'>('CHAT');

  // Keep ref in sync
  useEffect(() => {
    activeTabRef.current = activeTab;
  }, [activeTab]);

  // WS subscriptions for PM events and messages
  useEffect(() => {
    if (!connected || !userId || !subscribe) return;

    const loadSession = () => {
      getActivePm().then(sessions => {
        if (sessions.length > 0) {
          setPmSession(sessions[0]);
          setActiveTab('PM');
          getPmMessages(sessions[0].roomId).then(msgs => setPmMessages(msgs)).catch(() => {});
        }
      }).catch(() => {});
    };

    loadSession();

    let unsubEvents = () => {};
    const eventsResult = subscribe('/user/queue/pm-events', (msg) => {
      try {
        const data = JSON.parse(msg.body);
        console.log('PM EVENT:', data);
        if (data.type === 'PM_SESSION_STARTED') {
          const session: PmSession = {
            roomId: data.roomId,
            creatorId: data.creatorId,
            creatorUsername: data.creatorUsername || '',
            viewerId: Number(userId),
            viewerUsername: '',
            createdAt: new Date().toISOString(),
            unreadCount: 0,
            lastMessage: null,
            lastMessageTime: null,
          };
          setPmSession(session);
          setPmEnded(false);
          setActiveTab('PM');
          showToast('Creator started a private chat', 'info');
        }
        if (data.type === 'PM_SESSION_ENDED') {
          setPmSession(null);
          setPmMessages([]);
          setPmEnded(true);
        }
      } catch (e) {}
    });
    if (typeof eventsResult === 'function') {
      unsubEvents = eventsResult;
    }

    let unsubMessages = () => {};
    const messagesResult = subscribe('/user/queue/pm-messages', (msg) => {
      try {
        const data: PmMessage = JSON.parse(msg.body);
        if (!data.roomId) return;
        setPmMessages(prev => [...prev, data]);
        if (activeTabRef.current !== 'PM') {
          setPmSession(prev => prev && prev.roomId === data.roomId ? { ...prev, unreadCount: (prev.unreadCount || 0) + 1 } : prev);
        }
      } catch (e) {}
    });
    if (typeof messagesResult === 'function') {
      unsubMessages = messagesResult;
    }

    return () => {
      unsubEvents();
      unsubMessages();
    };
  }, [connected, userId, subscribe]);

  const handleSendPm = useCallback((content: string) => {
    if (!pmSession || !content.trim()) return;
    sendPmMessage(pmSession.roomId, content.trim(), wsSend);
  }, [pmSession, wsSend]);

  const handlePmTabOpen = useCallback(() => {
    if (pmSession) {
      setPmSession(prev => prev ? { ...prev, unreadCount: 0 } : prev);
      markPmAsRead(pmSession.roomId).catch(() => {});
    }
  }, [pmSession]);

  const handleTabChange = useCallback((tab: 'CHAT' | 'PM' | 'USERS') => {
    setActiveTab(tab);
  }, []);

  return {
    pmSession,
    pmMessages,
    pmEnded,
    activeTab,
    handleSendPm,
    handlePmTabOpen,
    handleTabChange,
  };
};
