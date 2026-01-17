import React, { useState } from 'react';
import { useAuth } from '../auth/useAuth';

const SubscriptionDebugPanel: React.FC = () => {
  const { 
    user,
    subscriptionStatus, 
    hasPremiumAccess, 
    refreshSubscription,
    isAuthenticated
  } = useAuth();
  
  const currentPeriodEnd = user?.subscription.renewalDate;
  
  const [lastRefresh, setLastRefresh] = useState<string>(new Date().toLocaleTimeString());
  const [isRefreshing, setIsRefreshing] = useState(false);

  // Only show in development
  if (import.meta.env.VITE_APP_ENV !== 'development') {
    return null;
  }

  // Only show if authenticated
  if (!isAuthenticated) {
    return null;
  }

  const handleRefresh = async () => {
    setIsRefreshing(true);
    try {
      await refreshSubscription();
      setLastRefresh(new Date().toLocaleTimeString());
    } finally {
      setIsRefreshing(false);
    }
  };

  return (
    <div style={{
      position: 'fixed',
      bottom: '10px',
      right: '10px',
      background: '#222',
      color: '#fff',
      padding: '15px',
      borderRadius: '8px',
      fontSize: '12px',
      zIndex: 9999,
      boxShadow: '0 0 10px rgba(0,0,0,0.5)',
      fontFamily: 'monospace',
      maxWidth: '300px'
    }}>
      <h4 style={{ margin: '0 0 10px 0', borderBottom: '1px solid #444', paddingBottom: '5px' }}>
        Subscription Debug
      </h4>
      <div style={{ marginBottom: '5px' }}>
        Status: <strong style={{ color: subscriptionStatus === 'ACTIVE' ? '#4caf50' : '#f44336' }}>{subscriptionStatus}</strong>
      </div>
      <div style={{ marginBottom: '5px' }}>
        Period End: <strong>{currentPeriodEnd ? new Date(currentPeriodEnd).toLocaleString() : 'N/A'}</strong>
      </div>
      <div style={{ marginBottom: '5px' }}>
        Has Premium: <strong>{hasPremiumAccess() ? 'YES' : 'NO'}</strong>
      </div>
      <div style={{ marginBottom: '10px' }}>
        Last Refresh: <strong>{lastRefresh}</strong>
      </div>
      <button 
        onClick={handleRefresh}
        disabled={isRefreshing}
        style={{
          width: '100%',
          padding: '5px',
          background: '#444',
          color: '#fff',
          border: '1px solid #666',
          borderRadius: '4px',
          cursor: isRefreshing ? 'not-allowed' : 'pointer'
        }}
      >
        {isRefreshing ? 'Refreshing...' : 'Force refresh subscription'}
      </button>
    </div>
  );
};

export default SubscriptionDebugPanel;
