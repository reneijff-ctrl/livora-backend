import axios from 'axios';

export type HealthStatus = 'loading' | 'up' | 'down' | 'unauthorized' | 'error';

type Listener = (status: HealthStatus) => void;

/**
 * healthStore manages the backend health check state.
 *
 * Invariants:
 * 1. Self-healing: Status is locked to 'up' once confirmed, but retries every 15s after any failure
 *    so the indicator clears automatically once the backend recovers.
 * 2. Independent: Status never flips based on failures in other API calls (e.g. dashboard).
 * 3. Explicit: Status only changes based on intentional /actuator/health/liveness checks.
 */
class HealthStore {
  private status: HealthStatus = 'loading';
  private listeners: Set<Listener> = new Set();
  private isChecking = false;
  private hasChecked = false;
  private retryTimer: ReturnType<typeof setTimeout> | null = null;

  getStatus(): HealthStatus {
    return this.status;
  }

  subscribe(listener: Listener): () => void {
    this.listeners.add(listener);
    return () => this.listeners.delete(listener);
  }

  private setStatus(newStatus: HealthStatus) {
    if (this.status !== newStatus) {
      this.status = newStatus;
      this.notify();
    }
  }

  private notify() {
    this.listeners.forEach((listener) => listener(this.status));
  }

  /**
   * Performs the health check. Skips if already in progress.
   * Can be called again after a previous failure to re-check.
   */
  async checkHealth(): Promise<void> {
    if (this.isChecking) {
      return;
    }
    // Only skip re-check if already confirmed 'up'
    if (this.hasChecked && this.status === 'up') {
      return;
    }

    this.isChecking = true;
    try {
      // Use plain axios (not apiClient) to avoid the /api baseURL prefix
      // Use /liveness probe so Stripe DOWN does not cause 503 and mark backend as offline
      await axios.get('/actuator/health/liveness', {
        timeout: 5000,
        headers: {
          'X-Requested-With': 'XMLHttpRequest',
          'Cache-Control': 'no-cache'
        },
        // @ts-ignore
        _skipToast: true
      });

      // If we got a 2xx response, the backend is reachable and online.
      // We set status to 'up' regardless of the internal health status (UP/DOWN)
      // because ONLY network errors or timeouts should mark it as offline.
      this.setStatus('up');
    } catch (error: any) {
      if (axios.isAxiosError(error)) {
        if (error.response) {
          // Case 1: Server responded with an error (4xx or 5xx)
          // The backend is reachable, so it is NOT offline.
          const status = error.response.status;
          if (status === 401 || status === 403) {
            // Explicitly handle 401/403: Backend is up but access is restricted
            this.setStatus('unauthorized');
          } else {
            // Explicitly handle other HTTP errors (e.g. 500, 503, 404).
            // Since we got a response, it's not a network error or timeout.
            // Requirement says only network/timeouts mark as offline, so we use 'error' (not offline).
            this.setStatus('error');
          }
        } else if (error.code === 'ECONNABORTED' || error.message.includes('timeout')) {
          // Case 2: Explicitly handle timeouts - mark as offline
          this.setStatus('down');
        } else if (error.request) {
          // Case 3: Explicitly handle network errors (no response received) - mark as offline
          this.setStatus('down');
        } else {
          // Case 4: Other request setup errors
          this.setStatus('down');
        }
      } else {
        // Non-Axios errors (unlikely here but handled for safety)
        this.setStatus('down');
      }
    } finally {
      this.isChecking = false;
      this.hasChecked = true;
      // Schedule a retry if not 'up', so the indicator clears once backend recovers
      if (this.status !== 'up') {
        if (this.retryTimer) clearTimeout(this.retryTimer);
        this.retryTimer = setTimeout(() => {
          this.hasChecked = false;
          this.checkHealth();
        }, 15000);
      } else if (this.retryTimer) {
        clearTimeout(this.retryTimer);
        this.retryTimer = null;
      }
    }
  }
}

export const healthStore = new HealthStore();
export default healthStore;
