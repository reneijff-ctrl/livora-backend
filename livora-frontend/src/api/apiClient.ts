import axios, { AxiosResponse, InternalAxiosRequestConfig } from 'axios';
import { getCsrfToken } from '../utils/csrf';
import { showToast } from '../components/Toast';

// Extend AxiosRequestConfig to support _retry
declare module 'axios' {
  export interface AxiosRequestConfig {
    _retry?: boolean;
  }
}

/**
 * Centralized Axios instance for making API requests to the backend.
 */
const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_URL,
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
  timeout: 10000,
});

// In-memory storage for the access token
let accessToken: string | null = null;

export const setAccessToken = (token: string | null) => {
  accessToken = token;
};

export const getAccessToken = () => accessToken;

// Request deduplication
const pendingRequests = new Map<string, Promise<any>>();

const getRequestKey = (config: InternalAxiosRequestConfig) => {
  return `${config.method}:${config.url}:${JSON.stringify(config.params)}:${JSON.stringify(config.data)}`;
};

// Wrapper to handle deduplication
const originalRequest = apiClient.request.bind(apiClient);
apiClient.request = (config: any) => {
  const method = config.method?.toUpperCase() || 'GET';
  if (method === 'GET') {
    const key = getRequestKey(config as InternalAxiosRequestConfig);
    if (pendingRequests.has(key)) {
      return pendingRequests.get(key)!;
    }
    const promise = originalRequest(config).finally(() => {
      pendingRequests.delete(key);
    });
    pendingRequests.set(key, promise);
    return promise;
  }
  return originalRequest(config);
};

// Request interceptor: Attach JWT and CSRF
apiClient.interceptors.request.use(
  (config: InternalAxiosRequestConfig) => {
    // Attach JWT if available
    if (accessToken) {
      config.headers['Authorization'] = `Bearer ${accessToken}`;
    }

    const method = config.method?.toUpperCase();
    
    // Spring Security by default expects CSRF token for state-changing methods
    if (method && !['GET', 'HEAD', 'OPTIONS', 'TRACE'].includes(method)) {
      const csrfToken = getCsrfToken();
      if (csrfToken) {
        config.headers['X-XSRF-TOKEN'] = csrfToken;
      }
    }
    
    return config;
  },
  (error) => Promise.reject(error)
);

// Response interceptor: Silent refresh logic
let isRefreshing = false;
let refreshPromise: Promise<string> | null = null;

apiClient.interceptors.response.use(
  (response: AxiosResponse) => {
    return response;
  },
  async (error) => {
    if (!error.response) {
      showToast('Network error. Please check your connection.', 'error');
      return Promise.reject(error);
    }

    const { status, config: requestConfig } = error.response;
    const originalRequest = error.config as InternalAxiosRequestConfig & { _retry?: boolean };

    // 401 Unauthorized -> Silent Refresh
    if (status === 401) {
      const url = originalRequest.url || '';
      if (
        originalRequest._retry ||
        url.includes('/auth/refresh') ||
        url.includes('/auth/login') ||
        url.includes('/auth/logout')
      ) {
        // If refresh fails or login is invalid, we should eventually clear state
        if (url.includes('/auth/refresh')) {
          setAccessToken(null);
          // Force reload to clear all state and redirect to login via AuthContext/Guard
          window.location.href = '/login';
        }
        return Promise.reject(error);
      }

      originalRequest._retry = true;

      if (!isRefreshing) {
        isRefreshing = true;
        refreshPromise = apiClient.post('/auth/refresh')
          .then(res => {
            const newToken = res.data.accessToken;
            setAccessToken(newToken);
            return newToken;
          })
          .finally(() => {
            isRefreshing = false;
            refreshPromise = null;
          });
      }

      try {
        const token = await refreshPromise;
        originalRequest.headers['Authorization'] = `Bearer ${token}`;
        return apiClient(originalRequest);
      } catch (refreshError) {
        setAccessToken(null);
        return Promise.reject(refreshError);
      }
    }

    // 403 Forbidden -> Redirect to Access Denied or Upgrade
    if (status === 403) {
      showToast('Access denied. You do not have permission for this action.', 'error');
      window.location.href = '/403';
      return Promise.reject(error);
    }

    // 500 Internal Server Error -> Log and let component handle
    if (status >= 500) {
      showToast('A server error occurred. Please try again later.', 'error');
      console.error('SERVER ERROR:', error.response.data);
    }

    // 400 Bad Request (Validation errors)
    if (status === 400) {
      const message = error.response.data.message || 'Invalid request parameters.';
      showToast(message, 'error');
    }

    return Promise.reject(error);
  }
);

export default apiClient;
