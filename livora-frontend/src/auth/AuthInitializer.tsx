import React, { useEffect, useState } from 'react';
import { useAuth } from './useAuth';

const AuthInitializer: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const { bootstrap } = useAuth();
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const initAuth = async () => {
      try {
        await bootstrap();
      } catch (e) {
        // Silent
      }
      setLoading(false);
    };
    initAuth();
  }, [bootstrap]);

  if (loading) {
    return <div>Loading...</div>;
  }

  return <>{children}</>;
};

export default AuthInitializer;
