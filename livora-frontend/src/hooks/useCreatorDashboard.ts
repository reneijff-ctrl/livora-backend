import { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { getCreatorDashboard, CreatorDashboard } from '../api/creatorApi';

/**
 * Hook to manage creator dashboard data, loading, and error states.
 * Fetches data automatically on mount.
 */
export const useCreatorDashboard = () => {
  const navigate = useNavigate();
  const [data, setData] = useState<CreatorDashboard | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<Error | null>(null);
  const retryTimeoutRef = useRef<NodeJS.Timeout | null>(null);

  const fetchDashboard = useCallback(async (retryCount = 0) => {
    try {
      if (retryCount === 0) setLoading(true);
      setError(null);
      const dashboardData = await getCreatorDashboard(retryCount === 0);
      setData(dashboardData);
    } catch (err: any) {
      if (retryCount === 0) {
        console.warn('Dashboard fetch failed, retrying once...', err);
        retryTimeoutRef.current = setTimeout(() => {
          fetchDashboard(1);
          retryTimeoutRef.current = null;
        }, 1000);
      } else {
        console.error('Failed to fetch creator dashboard after retry:', err);
        setError(err instanceof Error ? err : new Error('Failed to fetch creator dashboard'));
      }
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchDashboard();
    
    return () => {
      if (retryTimeoutRef.current) {
        clearTimeout(retryTimeoutRef.current);
      }
    };
  }, [fetchDashboard]);

  return {
    data,
    loading,
    error,
    refresh: fetchDashboard
  };
};

export default useCreatorDashboard;
