import { User, Role, UserStatus, SubscriptionStatus } from '../types';
import api from '../api/apiClient';
import AuthService from '../api/authService';
import { getAccessToken } from '../auth/jwt';

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
      
      const token = getAccessToken();
      
      // Store basic user info from login response immediately
      // This ensures role-based routing works before the full fetchUser completes
      this.setState({ 
        token, 
        isAuthenticated: true, 
        authLoading: true, // Keep loading true until full profile is fetched
        isInitialized: true,
        user: {
          id: String(response.user.id),
          email: response.user.email,
          role: response.user.role === 'USER' ? 'VIEWER' : response.user.role as Role,
          status: UserStatus.ACTIVE,
          emailVerified: false, // Default until fetchUser confirms
          subscription: { status: SubscriptionStatus.NONE }
        } as User
      });
      
      // Fetch full user profile (subscription, balance, etc.)
      await this.fetchUser();
    } catch (error) {
      this.setState({ isLoading: false });
      throw error;
    }
  }

  /**
   * Fetches the current user profile from the backend.
   */
  async fetchUser(): Promise<User> {
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

      this.setState({ 
        user, 
        isAuthenticated: true, 
        isLoading: false,
        authLoading: false,
        isInitialized: true 
      });
      return user;
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
      const response = await api.get<{ balance: number }>('/api/tokens/balance');
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
   * Refreshes the full user profile.
   */
  async fetchMe(): Promise<User> {
    return this.fetchUser();
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
