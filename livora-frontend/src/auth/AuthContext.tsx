import React, { createContext, useState, useEffect, ReactNode, useCallback, useMemo } from 'react';
import webSocketService from "../websocket/webSocketService";
import { AuthContextType } from '../types';
import authStore, { AuthState } from '../store/authStore';

export const AuthContext = createContext<AuthContextType | undefined>(undefined);

/**
 * AuthProvider provides authentication state and actions to the application via React Context.
 * It uses the centralized authStore for state management and logic.
 */
export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
  const [authState, setAuthState] = useState<AuthState>(authStore.getState());

  useEffect(() => {
    // Subscribe to authStore changes
    const unsubscribe = authStore.subscribe(setAuthState);
    
    // On mount, fetch user data to verify authentication status
    authStore.fetchUser().catch(() => {
      // fetchUser handles logout and state cleanup on error (e.g. 401)
    });
    
    return unsubscribe;
  }, []);

  const login = useCallback(async (email: string, password: string) => {
    try {
      await authStore.login(email, password);
      const token = localStorage.getItem("token");
      console.log("AUTH: Token stored:", token);
    } catch (err) {
      console.error('Login failed:', err);
      throw err;
    }
  }, []);

  const logout = useCallback(() => {
    authStore.logout();
    webSocketService.disconnect();
  }, []);

  const bootstrap = useCallback(async () => { await authStore.fetchUser(); }, []);
  const fetchMe = useCallback(async () => { await authStore.fetchMe(); }, []);
  const refreshTokenBalance = useCallback(async () => { return await authStore.refreshBalance(); }, []);
  const refreshSubscription = useCallback(async () => { await authStore.fetchUser(); }, []);

  const hasPremiumAccess = useCallback(() => {
    const user = authState.user;
    return user?.role === 'ADMIN' || user?.role === 'CREATOR' || user?.subscription?.status === 'ACTIVE';
  }, [authState.user]);

  const value = useMemo(() => ({
    user: authState.user,
    token: authState.token,
    loading: authState.isLoading,
    isLoading: authState.isLoading,
    authLoading: authState.authLoading,
    isInitialized: authState.isInitialized,
    isAuthenticated: authState.isAuthenticated,
    login,
    logout,
    hasPremiumAccess,
    subscriptionStatus: authState.user?.subscription?.status || 'NONE',
    tokenBalance: authState.user?.tokenBalance || 0,
    bootstrap,
    fetchMe,
    refreshTokenBalance,
    refreshSubscription,
  }), [authState, login, logout, bootstrap, fetchMe, refreshTokenBalance, refreshSubscription, hasPremiumAccess]);

  return (
    <AuthContext.Provider value={value}>
      {children}
    </AuthContext.Provider>
  );
};
