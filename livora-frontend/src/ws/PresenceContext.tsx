import React, { createContext, useContext, useEffect, useState, ReactNode, useRef, useMemo, useCallback } from 'react';
import { IMessage } from '@stomp/stompjs';
import { useWs } from './WsContext';

export interface PresenceUpdate {
  creatorUserId: number;
  online: boolean;
  availability: string;
  viewerCount: number;
}

interface PresenceContextType {
  presenceMap: Record<number, PresenceUpdate>;
  trackPresence: (userIds: number[]) => void;
  untrackPresence: (userIds: number[]) => void;
}

const PresenceContext = createContext<PresenceContextType | undefined>(undefined);

export const PresenceProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { connected, subscribe } = useWs();
  const [presenceMap, setPresenceMap] = useState<Record<number, PresenceUpdate>>({});
  
  // Tracked creators for cleanup strategy
  const trackingMap = useRef<Map<number, number>>(new Map());

  // Batching logic for presence updates to prevent excessive re-renders
  const pendingUpdates = useRef<Record<number, PresenceUpdate>>({});
  const batchTimer = useRef<NodeJS.Timeout | null>(null);

  const flushPresenceUpdates = useCallback(() => {
    const updatesCount = Object.keys(pendingUpdates.current).length;
    if (updatesCount === 0) {
      batchTimer.current = null;
      return;
    }

    const updatesToApply = { ...pendingUpdates.current };
    pendingUpdates.current = {};
    batchTimer.current = null;

    setPresenceMap(prev => {
      const newMap = { ...prev };
      Object.keys(updatesToApply).forEach(key => {
        const userId = parseInt(key, 10);
        newMap[userId] = updatesToApply[userId];
      });
      return newMap;
    });
  }, []);

  const trackPresence = useCallback((userIds: number[]) => {
    userIds.forEach(id => {
      const count = trackingMap.current.get(id) || 0;
      trackingMap.current.set(id, count + 1);
    });
  }, []);

  const untrackPresence = useCallback((userIds: number[]) => {
    userIds.forEach(id => {
      const count = trackingMap.current.get(id) || 0;
      if (count <= 1) {
        trackingMap.current.delete(id);
      } else {
        trackingMap.current.set(id, count - 1);
      }
    });
  }, []);

  // Periodic cleanup of presenceMap to prevent unbounded growth
  useEffect(() => {
    const cleanupInterval = setInterval(() => {
      setPresenceMap(prev => {
        const newMap = { ...prev };
        let changed = false;
        
        Object.keys(newMap).forEach(key => {
          const userId = parseInt(key, 10);
          if (!trackingMap.current.has(userId)) {
            delete newMap[userId];
            changed = true;
          }
        });
        
        if (changed) {
          return newMap;
        }
        return prev;
      });
    }, 60000); // Clean up every minute

    return () => clearInterval(cleanupInterval);
  }, []);

  // creators.presence subscription
  useEffect(() => {
    let unsub = () => {};

    if (connected) {
      const result = subscribe('/exchange/amq.topic/creators.presence', (msg: IMessage) => {
        try {
          const data = JSON.parse(msg.body);
          const payload = data.payload || data;
          
          if (payload.creatorUserId) {
            pendingUpdates.current[payload.creatorUserId] = {
              creatorUserId: payload.creatorUserId,
              online: payload.online,
              availability: payload.availability,
              viewerCount: payload.viewerCount
            };

            if (!batchTimer.current) {
              batchTimer.current = setTimeout(flushPresenceUpdates, 500);
            }
          }
        } catch (e) {
          console.error('[PRESENCE] Update parse error', e);
        }
      });
      if (typeof result === 'function') {
        unsub = result;
      }
    }

    return () => {
      unsub();
      if (batchTimer.current) {
        clearTimeout(batchTimer.current);
        batchTimer.current = null;
      }
    };
  }, [connected, subscribe, flushPresenceUpdates]);

  const value = useMemo(() => ({
    presenceMap,
    trackPresence,
    untrackPresence
  }), [presenceMap, trackPresence, untrackPresence]);

  return (
    <PresenceContext.Provider value={value}>
      {children}
    </PresenceContext.Provider>
  );
};

export const usePresence = () => {
  const context = useContext(PresenceContext);
  if (context === undefined) {
    throw new Error('usePresence must be used within a PresenceProvider');
  }
  return context;
};

/**
 * Hook to track presence for a set of creators.
 * Ensuring they are kept in the presenceMap and not cleaned up.
 */
export const useTrackPresence = (userIds: number[]) => {
  const { trackPresence, untrackPresence } = usePresence();
  useEffect(() => {
    if (userIds.length > 0) {
      trackPresence(userIds);
      return () => untrackPresence(userIds);
    }
    return undefined;
  }, [userIds, trackPresence, untrackPresence]);
};
