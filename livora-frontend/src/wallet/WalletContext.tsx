import React, { createContext, useContext, useState, useEffect, ReactNode, useRef, useMemo, useCallback } from 'react';
import { useAuth } from '../auth/useAuth';
import { useWs } from '../ws/WsContext';
import apiClient from '../api/apiClient';

interface WalletContextType {
  balance: number;
  lastDelta: number | null;
  setBalance: (balance: number) => void;
  applyWalletUpdate: (balance: number, delta: number) => void;
  subscribeToExplosions: (listener: (amount: number) => void) => () => void;
}

const WalletContext = createContext<WalletContextType | undefined>(undefined);

export const WalletProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const { isAuthenticated } = useAuth();
  const { subscribe, connected } = useWs();
  const [balance, setBalance] = useState(0);
  const [lastDelta, setLastDelta] = useState<number | null>(null);
  const deltaTimeoutRef = useRef<NodeJS.Timeout | null>(null);
  const subscriptionRef = useRef<(() => void) | null>(null);
  const explosionListeners = useRef<Set<(amount: number) => void>>(new Set());

  const subscribeToExplosions = useCallback((listener: (amount: number) => void) => {
    explosionListeners.current.add(listener);
    return () => {
      explosionListeners.current.delete(listener);
    };
  }, []);

  const triggerExplosion = useCallback((amount: number) => {
    explosionListeners.current.forEach(l => l(amount));
  }, []);

  // Initial balance fetch
  useEffect(() => {
    if (isAuthenticated) {
      apiClient.get('/tokens/balance')
        .then(res => setBalance(res.data.balance))
        .catch(err => console.error('WALLET: Failed to fetch initial balance', err));
    } else {
      setBalance(0);
      setLastDelta(null);
    }
  }, [isAuthenticated]);

  const applyWalletUpdate = useCallback((newBalance: number, delta: number) => {
    console.log(`WALLET: Applying update. Balance: ${newBalance}, Delta: ${delta}`);
    setBalance(newBalance);
    setLastDelta(delta);

    // Trigger explosion if delta < 0 and absolute value > 50
    if (delta < -50) {
      triggerExplosion(Math.abs(delta));
    }

    if (deltaTimeoutRef.current) {
      clearTimeout(deltaTimeoutRef.current);
    }

    deltaTimeoutRef.current = setTimeout(() => {
      setLastDelta(null);
      deltaTimeoutRef.current = null;
    }, 1500);
  }, [triggerExplosion]);

  // Cleanup timeout on unmount
  useEffect(() => {
    return () => {
      if (deltaTimeoutRef.current) {
        clearTimeout(deltaTimeoutRef.current);
      }
    };
  }, []);

  // Handle WebSocket subscription
  useEffect(() => {
    // Only subscribe after login and when WS is connected
    if (!isAuthenticated || !connected || !subscribe) {
      if (subscriptionRef.current) {
        console.log('WALLET: Cleaning up subscription (not auth/connected)');
        subscriptionRef.current();
        subscriptionRef.current = null;
      }
      return;
    }

    // No duplicate subscriptions allowed
    if (subscriptionRef.current) return;

    console.log('WALLET: Subscribing to /user/queue/wallet');
    const result = subscribe('/user/queue/wallet', (msg) => {
      try {
        const data = JSON.parse(msg.body);
        console.log('WALLET: Received update from WS', data);
        if (data.type === 'WALLET_UPDATE') {
          applyWalletUpdate(data.balance, data.delta);
        }
      } catch (e) {
        console.error('WALLET: Failed to parse wallet update', e);
      }
    });

    if (typeof result === 'function') {
      subscriptionRef.current = result;
    }

    return () => {
      if (subscriptionRef.current) {
        console.log('WALLET: Cleaning up subscription on unmount/re-init');
        subscriptionRef.current();
        subscriptionRef.current = null;
      }
    };
  }, [isAuthenticated, connected, subscribe]);

  const contextValue = useMemo(() => ({ 
    balance, 
    lastDelta, 
    setBalance, 
    applyWalletUpdate, 
    subscribeToExplosions 
  }), [balance, lastDelta, applyWalletUpdate, subscribeToExplosions]);

  return (
    <WalletContext.Provider value={contextValue}>
      {children}
    </WalletContext.Provider>
  );
};

export const useWallet = () => {
  const context = useContext(WalletContext);
  if (context === undefined) {
    throw new Error('useWallet must be used within a WalletProvider');
  }
  return context;
};
