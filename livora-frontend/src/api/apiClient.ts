import axios from 'axios';
import { getAccessToken, clearTokens, getRefreshToken, setAccessToken, setRefreshToken } from '../auth/jwt';
import { showToast } from '../components/Toast';

/**
 * Centralized Axios instance for making API requests to the backend.
 * Configured with withCredentials=true to support cookie-based authentication and CSRF.
 */
const apiClient = axios.create({
  baseURL: 'http://localhost:8080',
  withCredentials: true,
  headers: {
    'Content-Type': 'application/json',
    'Accept': 'application/json',
  },
  timeout: 10000,
});

/**
 * Public Axios instance. Reuses the primary instance to avoid duplication.
 */
export const publicApiClient = apiClient;

// Add request interceptor to attach JWT token
apiClient.interceptors.request.use(
  (config) => {
    const token = getAccessToken();
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }
    return config;
  },
  (error) => {
    return Promise.reject(error);
  }
);

let isRefreshing = false;
let failedQueue: any[] = [];

const processQueue = (error: any, token: string | null = null) => {
  failedQueue.forEach((prom) => {
    if (error) {
      prom.reject(error);
    } else {
      prom.resolve(token);
    }
  });

  failedQueue = [];
};

/**
 * Centralized error handler for all API requests.
 */
const handleApiError = async (error: any) => {
  const originalRequest = error.config;

  if (error.response?.status === 401 && !originalRequest._retry) {
    if (originalRequest.url === '/api/auth/refresh' || originalRequest.url === '/api/auth/login') {
      clearTokens();
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
      return Promise.reject(error);
    }

    if (isRefreshing) {
      return new Promise(function (resolve, reject) {
        failedQueue.push({ resolve, reject });
      })
        .then((token) => {
          originalRequest.headers['Authorization'] = 'Bearer ' + token;
          return apiClient(originalRequest);
        })
        .catch((err) => {
          return Promise.reject(err);
        });
    }

    originalRequest._retry = true;
    isRefreshing = true;

    const refreshToken = getRefreshToken();
    if (refreshToken) {
      try {
        const response = await axios.post(`${apiClient.defaults.baseURL}/api/auth/refresh`, {
          refreshToken: refreshToken
        }, { withCredentials: true });

        const { accessToken, refreshToken: newRefreshToken } = response.data;
        
        setAccessToken(accessToken);
        if (newRefreshToken) {
          setRefreshToken(newRefreshToken);
        }

        originalRequest.headers['Authorization'] = 'Bearer ' + accessToken;
        processQueue(null, accessToken);
        return apiClient(originalRequest);
      } catch (refreshError) {
        processQueue(refreshError, null);
        clearTokens();
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
        return Promise.reject(refreshError);
      } finally {
        isRefreshing = false;
      }
    }
  }

  if (error.response) {
    const { status, data } = error.response;
    const message = data?.message || data?.error || null;
    const skipToast = error.config?._skipToast;

    switch (status) {
      case 401:
        // Clear auth tokens
        clearTokens();
        
        // Force redirect to login if we are not already there
        if (window.location.pathname !== '/login') {
          window.location.href = '/login';
        }
        break;
      case 400:
        if (!skipToast) showToast(message || 'Bad request', 'error');
        break;
      case 403:
        if (!skipToast) showToast(message || 'No permission', 'error');
        break;
      case 404:
        if (!skipToast) showToast(message || 'Not found', 'error');
        break;
      case 500:
        if (!skipToast) showToast('An unexpected error occurred. Please try again later.', 'error');
        break;
      case 502:
        // Stripe and other provider errors are surfaced as 502 by the backend
        if (!skipToast) showToast(message || 'Payment provider error', 'error');
        break;
      default:
        if (!skipToast) showToast(message || 'Request failed', 'error');
        break;
    }
  } else if (error.request) {
    // The request was made but no response was received
    console.error('API Network Error:', error.request);
    // Ensure network errors are visible to users
    showToast('Network error. Please check your connection and try again.', 'error');
  } else {
    // Something happened in setting up the request
    console.error('API Error:', error.message);
    showToast('Unexpected error. Please try again.', 'error');
  }
  return Promise.reject(error);
};

// Add response interceptors
apiClient.interceptors.response.use(
  (response) => response,
  handleApiError
);

export default apiClient;
