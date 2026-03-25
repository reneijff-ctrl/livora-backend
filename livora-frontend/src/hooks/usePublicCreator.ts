import { useState, useEffect } from 'react';
import apiClient from '@/api/apiClient';
import { adaptCreator } from '@/adapters/CreatorAdapter';

export const usePublicCreator = (identifier: string | undefined) => {
  const [creator, setCreator] = useState<any>(null);
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
        console.debug("Creator fetch response", res.data);
        // Use adapter to ensure standardized ID and fields
        setCreator({ profile: adaptCreator(res.data) });
        setError(null);
      })
      .catch(err => {
        console.error("Failed to fetch creator", err);
        setCreator(null);
        setError(err);
      })
      .finally(() => {
        setLoading(false);
      });
  }, [identifier]);

  return { creator, loading, error };
};
