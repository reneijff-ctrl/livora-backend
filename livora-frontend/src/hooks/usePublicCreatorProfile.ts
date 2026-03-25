import { useState, useEffect } from 'react';
import apiClient from '@/api/apiClient';

export const usePublicCreatorProfile = (identifier: string | undefined) => {
  const [data, setData] = useState<any>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<any>(null);

  useEffect(() => {
    if (!identifier) {
      setLoading(false);
      return;
    }

    setLoading(true);
    apiClient.get(`/creators/${identifier}`)
      .then(res => {
        setData(res.data);
        setError(null);
      })
      .catch(err => {
        setData(null);
        setError(err);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [identifier]);

  return { data, loading, error };
};
