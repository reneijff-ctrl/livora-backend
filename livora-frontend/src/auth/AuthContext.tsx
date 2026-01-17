import React, { createContext, useContext, useState, useEffect, ReactNode, useCallback, useMemo } from 'react';
import authService from '../api/authService';
import paymentService from '../api/paymentService';
import tokenService from '../api/tokenService';
import { setAccessToken } from '../api/apiClient';
import webSocketService from '../websocket/webSocketService';
import { showToast } from '../components/Toast';

export interface User {
  id: string;
  email: string;
  role: 'USER' | 'PREMIUM' | 'ADMIN' | 'CREATOR';
  subscription: {
    status: SubscriptionStatus;
    renewalDate: string | null;
    cancelAtPeriodEnd?: boolean;
    nextInvoiceDate?: string | null;
    paymentMethodBrand?: string | null;
    last4?: string | null;
  };
}

export type SubscriptionStatus = 'NONE' | 'FREE' | 'ACTIVE' | 'PAST_DUE' | 'CANCELED' | 'TRIAL' | 'EXPIRED';

interface AuthContextType {
  user: User | null;
  role: 'USER' | 'PREMIUM' | 'ADMIN' | 'CREATOR' | null;
  subscriptionStatus: SubscriptionStatus;
  isAuthenticated: boolean;
  isLoading: boolean;
  tokenBalance: number;
  login: (email: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  bootstrap: () => Promise<void>;
  refreshUser: () => Promise<void>;
  refreshSubscription: () => Promise<void>;
  refreshTokenBalance: () => Promise<void>;
  hasPremiumAccess: () => boolean;
}

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [user, setUser] = useState<User | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [tokenBalance, setTokenBalance] = useState<number>(0);

  const fetchTokenBalance = useCallback(async () => {
    try {
      const data = await tokenService.getBalance();
      setTokenBalance(data.balance);
    } catch (error) {
      setTokenBalance(0);
    }
  }, []);

  const bootstrap = useCallback(async () => {
    setIsLoading(true);
    try {
      // 1. Fetch user data (source of truth)
      const userData = await authService.getMe();
      setUser(userData as any);
      
      // 2. Fetch auxiliary data
      await fetchTokenBalance();
      
      // 3. Connect WebSocket
      webSocketService.connect();
      
      // Subscribe to personal subscription updates
      const unsubscribeSub = webSocketService.subscribe(
        `/user/queue/subscription`,
        (message) => {
          try {
            const data = JSON.parse(message.body);
            if (data.payload && data.payload.status) {
              setUser(prev => prev ? {
                ...prev,
                subscription: {
                  ...prev.subscription,
                  status: data.payload.status
                }
              } : null);
            }
          } catch (e) {
            authService.getMe().then(setUser);
          }
        }
      );

      // Subscribe to personal token updates
      const unsubscribeTokens = webSocketService.subscribe(
        `/user/queue/tokens`,
        () => fetchTokenBalance()
      );

      // Subscribe to notifications (Tips, PPV sales)
      const unsubscribeNotifications = webSocketService.subscribe(
        `/user/queue/notifications`,
        (message) => {
          try {
            const data = JSON.parse(message.body);
            if (data.type === 'NEW_TIP') {
              const { amount, currency, fromUser, message: tipMsg } = data.payload;
              showToast(`New Tip! ${amount} ${currency.toUpperCase()} from ${fromUser}${tipMsg ? ': ' + tipMsg : ''}`, 'success');
            } else if (data.type === 'NEW_PPV_SALE') {
              const { title, amount } = data.payload;
              showToast(`PPV Sale! Someone bought "${title}" for €${amount.toFixed(2)}`, 'success');
            }
          } catch (e) {
            console.error('Error processing notification', e);
          }
        }
      );

      return () => {
        unsubscribeSub();
        unsubscribeTokens();
        unsubscribeNotifications();
      };
    } catch (error: any) {
      setUser(null);
      setAccessToken(null);
      webSocketService.disconnect();
      // Silently handle 401 as requested
      if (error?.response?.status !== 401) {
        console.error('Initial authentication check failed', error);
      }
    } finally {
      setIsLoading(false);
    }
  }, [fetchTokenBalance]);

  useEffect(() => {
    const cleanupPromise = bootstrap();
    return () => {
      cleanupPromise.then(cleanup => {
        if (typeof cleanup === 'function') cleanup();
      });
    };
  }, [bootstrap]);

  const login = async (email: string, password: string) => {
    const loginRes = await authService.login({ email, password });
    setAccessToken(loginRes.accessToken);
    
    const userData = await authService.getMe();
    setUser(userData as any);
    await fetchTokenBalance();
    webSocketService.connect();
  };

  const logout = async () => {
    try {
      await authService.logout();
    } finally {
      setUser(null);
      setAccessToken(null);
      setTokenBalance(0);
      webSocketService.disconnect();
      window.location.href = '/login';
    }
  };

  const hasPremiumAccess = useCallback(() => {
    return user?.subscription.status === 'ACTIVE' || user?.role === 'ADMIN';
  }, [user]);

  const refreshUser = useCallback(async () => {
    try {
      const userData = await authService.getMe();
      setUser(userData as any);
    } catch (error) {
      // Handle silently as requested or clear user if 401
      setUser(null);
    }
  }, []);

  const refreshSubscription = useCallback(async () => {
    await refreshUser();
  }, [refreshUser]);

  const value = useMemo(() => ({
    user,
    role: user?.role || null,
    subscriptionStatus: user?.subscription.status || 'NONE',
    isAuthenticated: !!user,
    isLoading,
    tokenBalance,
    login,
    logout,
    bootstrap,
    refreshUser,
    refreshSubscription,
    refreshTokenBalance: fetchTokenBalance,
    hasPremiumAccess,
  }), [user, isLoading, tokenBalance, bootstrap, refreshUser, refreshSubscription, fetchTokenBalance, hasPremiumAccess]);

  return (
    <AuthContext.Provider value={value as any}>
      {children}
    </AuthContext.Provider>
  );
};

