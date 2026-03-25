let accessToken: string | null = null;
let refreshToken: string | null = null;

const ACCESS_TOKEN_KEY = 'token';
const REFRESH_TOKEN_KEY = 'refreshToken';

/**
 * Sets the access token in memory and localStorage.
 * @param token The JWT access token.
 */
export const setAccessToken = (token: string): void => {
  accessToken = token;
  localStorage.setItem(ACCESS_TOKEN_KEY, token);
};

/**
 * Retrieves the access token from memory or localStorage.
 * @returns The access token or null if not found.
 */
export const getAccessToken = (): string | null => {
  if (!accessToken) {
    accessToken = localStorage.getItem(ACCESS_TOKEN_KEY);
  }
  return accessToken;
};

/**
 * Sets the refresh token in memory and localStorage.
 * @param token The refresh token.
 */
export const setRefreshToken = (token: string): void => {
  refreshToken = token;
  localStorage.setItem(REFRESH_TOKEN_KEY, token);
};

/**
 * Retrieves the refresh token from memory or localStorage.
 * @returns The refresh token or null if not found.
 */
export const getRefreshToken = (): string | null => {
  if (!refreshToken) {
    refreshToken = localStorage.getItem(REFRESH_TOKEN_KEY);
  }
  return refreshToken;
};

/**
 * Clears the access and refresh tokens from memory and localStorage.
 */
export const clearTokens = (): void => {
  accessToken = null;
  refreshToken = null;
  localStorage.removeItem(ACCESS_TOKEN_KEY);
  localStorage.removeItem(REFRESH_TOKEN_KEY);
};

/**
 * Clears the access token from memory and localStorage.
 * @deprecated Use clearTokens instead
 */
export const clearAccessToken = (): void => {
  accessToken = null;
  localStorage.removeItem(ACCESS_TOKEN_KEY);
};
