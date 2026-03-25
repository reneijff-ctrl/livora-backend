import React, { useState, useEffect } from 'react';
import healthStore, { HealthStatus } from '../store/healthStore';

/**
 * A simple component that displays the backend health status from healthStore.
 */
const BackendHealth: React.FC = () => {
  const [status, setStatus] = useState<HealthStatus>(healthStore.getStatus());

  useEffect(() => {
    const unsubscribe = healthStore.subscribe(setStatus);
    // Explicitly removed healthStore.checkHealth() to ensure it only runs once on app start
    // and is not re-triggered by component mounting.
    return unsubscribe;
  }, []);

  const getStatusColor = () => {
    switch (status) {
      case 'up': return '#10b981'; // Green
      case 'down': return '#ef4444'; // Red
      case 'unauthorized': return '#f59e0b'; // Amber
      case 'error': return '#f59e0b'; // Amber
      case 'loading': return '#94a3b8'; // Slate
      default: return '#94a3b8';
    }
  };

  const getStatusText = () => {
    switch (status) {
      case 'up': return 'Backend: Online';
      case 'down': return 'Backend: Offline';
      case 'unauthorized': return 'Backend: Restricted';
      case 'error': return 'Backend: Error';
      case 'loading': return 'Backend: Checking...';
      default: return 'Backend: Unknown';
    }
  };

  return (
    <div 
      title={`Current Status: ${status.toUpperCase()}`}
      style={{
        display: 'flex',
        alignItems: 'center',
        gap: '8px',
        padding: '6px 12px',
        borderRadius: '20px',
        backgroundColor: 'var(--card-bg)',
        border: '1px solid var(--border-color)',
        boxShadow: '0 6px 16px rgba(0, 0, 0, 0.25)',
        fontSize: '0.75rem',
        fontWeight: '600',
        color: 'var(--text-secondary)',
        width: 'fit-content',
        transition: 'all 0.3s ease',
        fontFamily: 'system-ui, -apple-system, sans-serif'
      }}
    >
      <span style={{
        width: '8px',
        height: '8px',
        borderRadius: '50%',
        backgroundColor: getStatusColor(),
        display: 'inline-block',
        boxShadow: `0 0 0 2px ${getStatusColor()}20`
      }} />
      {getStatusText()}
    </div>
  );
};

export default BackendHealth;
