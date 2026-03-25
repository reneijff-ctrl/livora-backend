import React, { createContext, useContext, useEffect, useState, ReactNode, useRef, useMemo, useCallback } from 'react';
import { useAuth } from '../auth/useAuth';
import { webSocketService } from '../websocket/webSocketService';
import { IMessage } from '@stomp/stompjs';

const wsSessionId = Math.random().toString(36).substring(2, 9);
const log = (msg: string, data?: any) => {
  console.log(`[WSCTX-${wsSessionId}] ${new Date().toISOString()} - ${msg}`, data || "");
};

interface PresenceUpdate {
  creatorUserId: number;
  online: boolean;
  availability: string;
  viewerCount: number;
}

interface WsContextType {
  subscribe: (destination: string, callback: (message: IMessage) => void) => { unsubscribe: () => void } | null;
  connected: boolean;
  disconnect: (reason: string) => void;
  presenceMap: Record<number, PresenceUpdate>;
  trackPresence: (userIds: number[]) => void;
  untrackPresence: (userIds: number[]) => void;
  thumbnailCacheBuster: number;
}

const WsContext = createContext<WsContextType | undefined>(undefined);

export const WsProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { token, isAuthenticated } = useAuth();
  const [connected, setConnected] = useState(webSocketService.isConnected());
  const [presenceMap, setPresenceMap] = useState<Record<number, PresenceUpdate>>({});
  const [thumbnailCacheBuster, setThumbnailCacheBuster] = useState(Date.now());
  const instanceId = useRef(Math.random().toString(36).substring(2, 9)).current;

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

    log(`[${instanceId}] Flushing ${updatesCount} presence updates to state`);
    const updatesToApply = { ...pendingUpdates.current };
    pendingUpdates.current = {};
    batchTimer.current = null;

    setPresenceMap(prev => ({
      ...prev,
      ...updatesToApply
    }));
  }, [instanceId]);

  // Memoized handlers
  const handleConnect = useCallback(() => {
    log(`[${instanceId}] handleConnect() starting`);
    webSocketService.connect();
    log(`[${instanceId}] handleConnect() finished (async trigger)`);
  }, [instanceId]);

  const handleDisconnect = useCallback((reason: string) => {
    log(`[${instanceId}] handleDisconnect() called. Reason: ${reason}`);
    console.trace(`[WSCTX-${wsSessionId}][${instanceId}] Disconnect stack trace`);
    webSocketService.disconnect();
    setConnected(false);
  }, [instanceId]);

  const subscribe = useCallback((destination: string, callback: (message: IMessage) => void) => {
    log(`[${instanceId}] Subscribing to ${destination}`);
    const unsub = webSocketService.subscribe(destination, callback);
    return { 
      unsubscribe: () => {
        log(`[${instanceId}] Unsubscribing from ${destination}`);
        if (unsub) {
          unsub();
        }
      } 
    };
  }, [instanceId]);

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

  // Periodic refresh of thumbnail cache buster (every 30 seconds)
  useEffect(() => {
    const busterInterval = setInterval(() => {
      setThumbnailCacheBuster(Date.now());
    }, 30000);

    return () => clearInterval(busterInterval);
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
          log(`[${instanceId}] Cleaned up presenceMap. Removed non-tracked entries.`);
          return newMap;
        }
        return prev;
      });
    }, 60000); // Clean up every minute

    return () => clearInterval(cleanupInterval);
  }, [instanceId]);

  // WebSocket connectivity management based on authentication state
  useEffect(() => {
    log(`[${instanceId}] WebSocket sync effect triggered. isAuthenticated: ${isAuthenticated}, token present: ${!!token}`);
    if (token) {
      log(`[${instanceId}] Current token: ${token}`);
    }
    
    const isCurrentlyConnected = webSocketService.isConnected();
    
    // Connect ONLY when authenticated, token exists, and not already connected
    if (isAuthenticated === true && token && !isCurrentlyConnected) {
      log(`[${instanceId}] Condition met: Authenticated and disconnected. Calling handleConnect()`);
      handleConnect();
    } else if (!isAuthenticated || !token) {
      // If we lose authentication, we should ensure we are disconnected
      if (isCurrentlyConnected) {
        log(`[${instanceId}] Condition met: Unauthenticated but connected. Calling handleDisconnect()`);
        handleDisconnect("Authentication lost");
      }
    }
  }, [token]); // Dependency array strictly as requested: [authState.token]

  // Reactive connection state synchronization
  useEffect(() => {
    log(`[${instanceId}] Setting up WebSocket state listener`);
    const unsubscribe = webSocketService.subscribeStateChange((isConnected) => {
      log(`[${instanceId}] WebSocket connected state changed: ${isConnected}`);
      setConnected(isConnected);
    });

    return () => {
      log(`[${instanceId}] Cleaning up WebSocket state listener`);
      unsubscribe();
    };
  }, [instanceId]);

  // IMPORTANT:
  // creators.presence must only be subscribed here.
  // Components must consume presence state from the presenceMap exposed via useWs().
  // Do NOT add additional subscriptions to /exchange/amq.topic/creators.presence elsewhere.
  useEffect(() => {
    if (!connected) return;

    log(`[${instanceId}] Subscribing to global presence updates`);
    const presenceSub = subscribe('/exchange/amq.topic/creators.presence', (msg) => {
      try {
        const data = JSON.parse(msg.body);
        const payload = data.payload || data;
        
        if (payload.creatorUserId) {
          log(`[${instanceId}] Presence: Queuing update for creatorUserId=${payload.creatorUserId}`);
          
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
        log(`[${instanceId}] Global presence update parse error`, e);
      }
    });

    return () => {
      log(`[${instanceId}] Cleaning up global presence subscription`);
      if (presenceSub) presenceSub.unsubscribe();
      if (batchTimer.current) {
        clearTimeout(batchTimer.current);
        batchTimer.current = null;
      }
    };
  }, [connected, subscribe, instanceId, flushPresenceUpdates]);

  // Memoized context value to prevent unnecessary re-renders of the tree
  const contextValue = useMemo(() => ({ 
    subscribe, 
    connected, 
    disconnect: handleDisconnect,
    presenceMap,
    trackPresence,
    untrackPresence,
    thumbnailCacheBuster
  }), [subscribe, connected, handleDisconnect, presenceMap, trackPresence, untrackPresence, thumbnailCacheBuster]);

  return (
    <WsContext.Provider value={contextValue}>
      {children}
    </WsContext.Provider>
  );
};

export const useWs = () => {
  const context = useContext(WsContext);
  if (context === undefined) {
    throw new Error('useWs must be used within a WsProvider');
  }
  return context;
};

/**
 * Hook to track presence for a set of creators.
 * Ensuring they are kept in the presenceMap and not cleaned up.
 */
export const useTrackPresence = (userIds: number[]) => {
  const { trackPresence, untrackPresence } = useWs();
  useEffect(() => {
    if (userIds.length > 0) {
      trackPresence(userIds);
      return () => untrackPresence(userIds);
    }
    return undefined;
  }, [userIds, trackPresence, untrackPresence]);
};
