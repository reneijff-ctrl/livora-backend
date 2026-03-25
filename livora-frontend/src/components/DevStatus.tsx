import React, { useState, useEffect } from 'react';
import healthStore, { HealthStatus } from '../store/healthStore';

/**
 * DevStatus component that monitors backend health.
 * Only renders in development mode.
 */
const DevStatus: React.FC = () => {
  const isDev = import.meta.env.DEV;
  const [storeStatus, setStoreStatus] = useState<HealthStatus>(healthStore.getStatus());

  useEffect(() => {
    if (!isDev) return;

    const unsubscribe = healthStore.subscribe(setStoreStatus);
    // Explicitly removed healthStore.checkHealth() to ensure it only runs once on app start
    return unsubscribe;
  }, [isDev]);

  if (!isDev) return null;

  const status = storeStatus === 'up' ? 'UP' : 
                 storeStatus === 'unauthorized' ? 'RESTRICTED' : 
                 storeStatus === 'error' ? 'ERROR' : 
                 storeStatus === 'loading' ? 'CHECKING' : 'DOWN';

  return (
    <div style={{
      position: 'fixed',
      top: '10px',
      right: '10px',
      zIndex: 9999,
      padding: '4px 8px',
      borderRadius: '4px',
      backgroundColor: status === 'UP' ? '#22c55e' : (status === 'RESTRICTED' || status === 'ERROR' ? '#f59e0b' : (status === 'DOWN' ? '#ef4444' : '#64748b')),
      color: 'white',
      fontSize: '10px',
      fontWeight: 'bold',
      fontFamily: 'monospace',
      boxShadow: '0 2px 4px rgba(0,0,0,0.1)',
      pointerEvents: 'none',
      opacity: 0.9,
      display: 'flex',
      alignItems: 'center',
      gap: '5px'
    }}>
      <span style={{
        width: '6px',
        height: '6px',
        borderRadius: '50%',
        backgroundColor: 'white',
        display: 'inline-block',
        animation: status === 'CHECKING' ? 'pulse 1s infinite' : 'none'
      }} />
      DEV: BACKEND {status}
      <style>{`
        @keyframes pulse {
          0% { opacity: 1; }
          50% { opacity: 0.4; }
          100% { opacity: 1; }
        }
      `}</style>
    </div>
  );
};

export default DevStatus;
