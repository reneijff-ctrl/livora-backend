import { User, SubscriptionStatus } from '../types';
import api from '../api/apiClient';
import AuthService from '../api/authService';
import { getAccessToken, setAccessToken, setRefreshToken } from '../auth/jwt';
import { adaptCreator } from '../adapters/CreatorAdapter';

/**
 * AuthState represents the centralized authentication state.
 */
export interface AuthState {
  user: User | null;
  token: string | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  authLoading: boolean;
  isInitialized: boolean;
  requiresTwoFactor: boolean;
  requiresTwoFactorSetup: boolean;
  preAuthToken: string | null;
}

type Listener = (state: AuthState) => void;

export const getDashboardRouteByRole = (role?: string): string => {
  if (role === 'ADMIN') return '/admin';
  if (role === 'CREATOR') return '/creator/dashboard';
  // Non-creator roles land on homepage by default
  if (role === 'USER' || role === 'VIEWER' || role === 'PREMIUM') return '/';
  return '/';
};

/**
 * AuthStore is a centralized, non-React-dependent store for authentication state.
 * It handles state updates and auth logic, coordinating with AuthService.
 */
class AuthStore {
  private state: AuthState;
  private listeners: Set<Listener> = new Set();

  constructor() {
    const token = getAccessToken();
    this.state = {
      user: null,
      token,
      isAuthenticated: !!token,
      isLoading: true, // Always start in loading state to verify auth on startup
      authLoading: true,
      isInitialized: false,
      requiresTwoFactor: false,
      requiresTwoFactorSetup: false,
      preAuthToken: null,
    };
  }

  /**
   * Returns the current state.
   */
  getState(): AuthState {
    return { ...this.state };
  }

  /**
   * Subscribes to state changes.
   * @param listener Callback function called on state change.
   * @returns Unsubscribe function.
   */
  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  /**
   * Internal helper to update state and notify listeners.
   */
  private setState(update: Partial<AuthState>) {
    this.state = { ...this.state, ...update };
    this.notify();
  }

  /**
   * Sets the authentication state from a backend response containing a token and user data.
   * This is the primary method for updating auth after login, token refresh, or role upgrade.
   */
  public setAuthFromBackend(response: { accessToken: string; refreshToken?: string; user: any }) {
    const { accessToken, refreshToken, user } = response;

    // 1. Save tokens to persistent storage
    if (accessToken) {
      setAccessToken(accessToken);
    }
    if (refreshToken) {
      setRefreshToken(refreshToken);
    }

    // 2. Normalize and update user state
    const normalizedUser = this.normalizeUser(user);

    // 3. Update internal state and emit changes
    this.setState({
      token: accessToken || this.state.token,
      user: normalizedUser,
      isAuthenticated: !!(accessToken || this.state.token),
      isLoading: false,
      authLoading: false,
      isInitialized: true,
    });
  }

  private normalizeUser(user: any): User | null {
    if (!user) return null;

    const normalized = { ...user };
    
    // Ensure ID is a string
    if (typeof normalized.id === 'number') {
      normalized.id = normalized.id.toString();
    }
    
    // Normalize role
    if (normalized.role === 'USER') {
      normalized.role = 'VIEWER';
    }

    // Adapt creator profile if present
    if (normalized.creatorProfile) {
      normalized.creatorProfile = adaptCreator(normalized.creatorProfile);
    }
    
    // Add default subscription if missing
    if (!normalized.subscription) {
       normalized.subscription = { status: SubscriptionStatus.NONE };
    }

    return normalized as User;
  }

  private notify() {
    this.listeners.forEach((listener) => listener({ ...this.state }));
  }

  /**
   * Performs login via AuthService and updates store state.
   */
  async login(email: string, password: string): Promise<void> {
    this.setState({ isLoading: true });
    try {
      // AuthService handles the API call and setting the token in localStorage via jwt.ts
      const response = await AuthService.login(email, password);

      // 2FA gate: if backend requires TOTP, store pre-auth token in memory only
      if (response.requiresTwoFactor) {
        this.setState({
          isLoading: false,
          requiresTwoFactor: true,
          preAuthToken: response.preAuthToken ?? null,
        });
        return;
      }

      // Admin 2FA setup gate: admin must configure TOTP before first login
      if (response.requiresTwoFactorSetup) {
        this.setState({
          isLoading: false,
          requiresTwoFactorSetup: true,
          preAuthToken: response.preAuthToken ?? null,
        });
        return;
      }

      // Use the centralized method to set auth state from backend response
      this.setAuthFromBackend({
        accessToken: response.token,
        refreshToken: response.refreshToken,
        user: response.user
      });
      
      // Fetch full user profile (subscription, balance, etc.)
      await this.refresh();
    } catch (error) {
      this.setState({ isLoading: false });
      throw error;
    }
  }

  /**
   * Refreshes the full user profile from the backend.
   * Alias for legacy fetchUser/fetchMe.
   */
  async refresh(): Promise<User> {
    const currentToken = getAccessToken();
    if (!currentToken) {
      this.setState({ 
        isLoading: false, 
        authLoading: false, 
        isInitialized: true,
        isAuthenticated: false,
        user: null 
      });
      return null as any;
    }

    this.setState({ isLoading: true, authLoading: true });
    try {
      // Use /api/auth/me for the full profile including subscription and balance
      const user = await AuthService.getCurrentUser();
      
      // Prevent race condition: ensure token hasn't changed (e.g. logout) while fetching
      if (getAccessToken() !== currentToken) {
        return user;
      }

      // Normalize and set the refreshed user
      const normalizedUser = this.normalizeUser(user);
      this.setState({ 
        user: normalizedUser, 
        isAuthenticated: true, 
        isLoading: false,
        authLoading: false,
        isInitialized: true 
      });
      return normalizedUser as User;
    } catch (error: any) {
      // Prevent race condition for errors too
      if (getAccessToken() !== currentToken) {
        throw error;
      }

      // If we have a token but /me fails, we should clear it and logout
      // to ensure a clean state, especially on startup.
      this.logout();
      throw error;
    }
  }

  /**
   * Refreshes only the token balance.
   */
  async refreshBalance(): Promise<number> {
    try {
      const response = await api.get<{ balance: number }>('/tokens/balance');
      if (this.state.user) {
        this.setState({
          user: { ...this.state.user, tokenBalance: response.data.balance }
        });
      }
      return response.data.balance;
    } catch (error) {
      console.error('Failed to refresh balance', error);
      throw error;
    }
  }

  /**
   * Clears the authentication state and removes the token from localStorage.
   */
  logout() {
    // AuthService handles backend notification and clearing localStorage via jwt.ts
    AuthService.logout().catch(() => {});

    // Clear local state
    this.setState({
      user: null,
      token: null,
      isAuthenticated: false,
      isLoading: false,
      authLoading: false,
      isInitialized: true,
      requiresTwoFactor: false,
      requiresTwoFactorSetup: false,
      preAuthToken: null,
    });
  }

  /**
   * Helper to check if the user is currently authenticated.
   */
  get isAuthenticated(): boolean {
    return this.state.isAuthenticated;
  }

  /**
   * Helper to get the current token.
   */
  get token(): string | null {
    return this.state.token;
  }

  /**
   * Helper to get the current user.
   */
  get user(): User | null {
    return this.state.user;
  }
}

export const authStore = new AuthStore();
export default authStore;
