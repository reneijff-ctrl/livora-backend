import React, { createContext, useContext, useEffect, useState, ReactNode, useRef, useMemo, useCallback } from 'react';
import { useAuth } from '../auth/useAuth';
import { webSocketService } from '../websocket/webSocketService';
import { IMessage } from '@stomp/stompjs';

const wsSessionId = Math.random().toString(36).substring(2, 9);
const log = (msg: string, data?: any) => {
  console.log(`[WSCTX-${wsSessionId}] ${new Date().toISOString()} - ${msg}`, data || "");
};

interface WsContextType {
  subscribe: (destination: string, callback: (message: IMessage) => void) => () => void;
  send: (destination: string, body: string, headers?: Record<string, string>) => void;
  isConnected: () => boolean;
  connected: boolean;
  disconnect: (reason: string) => void;
}

const WsContext = createContext<WsContextType | undefined>(undefined);

export const WsProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { token, isAuthenticated } = useAuth();
  const [connected, setConnected] = useState(webSocketService.isConnected());
  const instanceId = useRef(Math.random().toString(36).substring(2, 9)).current;

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
    if (!webSocketService.isConnected()) {
      console.warn(`[WSCTX] subscribe called while disconnected for ${destination}`);
      return () => {};
    }

    log(`[${instanceId}] Subscribing to ${destination}`);
    const unsub = webSocketService.subscribe(destination, callback);

    if (typeof unsub !== 'function') {
      console.warn(`[WSCTX] subscribe did not return unsubscribe function for ${destination}`);
      return () => {};
    }

    return () => {
      log(`[${instanceId}] Unsubscribing from ${destination}`);
      unsub();
    };
  }, [instanceId]);

  const send = useCallback((destination: string, body: string, headers?: Record<string, string>) => {
    webSocketService.send(destination, body, headers);
  }, []);

  const isConnected = useCallback(() => {
    return webSocketService.isConnected();
  }, []);

  // WebSocket connectivity management based on authentication state
  useEffect(() => {
    log(`[${instanceId}] WebSocket sync effect triggered. isAuthenticated: ${isAuthenticated}, token present: ${!!token}`);
    
    const isCurrentlyConnected = webSocketService.isConnected();
    
    if (isAuthenticated === true && token && !isCurrentlyConnected) {
      log(`[${instanceId}] Condition met: Authenticated and disconnected. Calling handleConnect()`);
      handleConnect();
    } else if (!isAuthenticated || !token) {
      if (isCurrentlyConnected) {
        log(`[${instanceId}] Condition met: Unauthenticated but connected. Calling handleDisconnect()`);
        handleDisconnect("Authentication lost");
      }
    }
  }, [token, isAuthenticated, handleConnect, handleDisconnect, instanceId]);

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

  // Memoized context value to prevent unnecessary re-renders of the tree
  const contextValue = useMemo(() => ({ 
    subscribe, 
    send,
    isConnected,
    connected, 
    disconnect: handleDisconnect,
  }), [subscribe, send, isConnected, connected, handleDisconnect]);

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
 * Hook to get a thumbnail cache buster value that updates every 30 seconds.
 */
export const useThumbnailCacheBuster = (): number => {
  const [cacheBuster, setCacheBuster] = useState(Date.now());

  useEffect(() => {
    const interval = setInterval(() => {
      setCacheBuster(Date.now());
    }, 30000);
    return () => clearInterval(interval);
  }, []);

  return cacheBuster;
};
