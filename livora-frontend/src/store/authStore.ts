import { User } from '../api/auth';

interface AuthState {
  isAuthenticated: boolean;
  user: User | null;
}

class AuthStore {
  private state: AuthState;

  constructor() {
    const token = localStorage.getItem('access_token');
    this.state = {
      isAuthenticated: !!token,
      user: null,
    };
  }

  public get isAuthenticated(): boolean {
    return this.state.isAuthenticated;
  }

  public get user(): User | null {
    return this.state.user;
  }

  public get isLoggedIn(): boolean {
    return this.state.isAuthenticated;
  }

  public hasRole(role: string): boolean {
    return this.state.user?.role === role;
  }

  public setAuth(user: User, accessToken: string): void {
    localStorage.setItem('access_token', accessToken);
    this.state = {
      isAuthenticated: true,
      user,
    };
  }

  public clearAuth(): void {
    localStorage.removeItem('access_token');
    this.state = {
      isAuthenticated: false,
      user: null,
    };
  }

  public getAccessToken(): string | null {
    return localStorage.getItem('access_token');
  }
}

export const authStore = new AuthStore();
