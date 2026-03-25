import React, { useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useWallet } from '../hooks/useWallet';
import SEO from '../components/SEO';

const TokenPurchaseSuccess: React.FC = () => {
  const { balance, refreshBalance } = useWallet();
  const navigate = useNavigate();
  const [isRefreshing, setIsRefreshing] = useState(true);
  const [success, setSuccess] = useState(false);
  const [attempts, setAttempts] = useState(0);

  // Read the pre-purchase balance stored by TokenStore before Stripe redirect.
  // This is the only reliable "before" value because by the time this component
  // mounts, the global auth state may already reflect the new (post-purchase) balance.
  const prePurchaseBalanceRef = useRef<number | null>(null);
  if (prePurchaseBalanceRef.current === null) {
    const stored = sessionStorage.getItem('livora_pre_purchase_balance');
    if (stored !== null) {
      const parsed = Number(stored);
      prePurchaseBalanceRef.current = isNaN(parsed) ? null : parsed;
    }
  }

  // Clean up sessionStorage on mount so it doesn't persist across sessions
  useEffect(() => {
    sessionStorage.removeItem('livora_pre_purchase_balance');
  }, []);

  useEffect(() => {
    // If no stored pre-purchase balance exists, the user may have navigated here
    // directly or sessionStorage was cleared. In that case, just do a single
    // balance refresh and show success (they came from Stripe, payment succeeded).
    if (prePurchaseBalanceRef.current === null) {
      refreshBalance().then(() => {
        setSuccess(true);
        setIsRefreshing(false);
      }).catch(() => {
        setSuccess(true);
        setIsRefreshing(false);
      });
      return;
    }

    const pollBalance = async () => {
      try {
        const newBalance = await refreshBalance();
        if (newBalance > prePurchaseBalanceRef.current!) {
          setSuccess(true);
          setIsRefreshing(false);
        } else if (attempts >= 9) {
          setSuccess(false);
          setIsRefreshing(false);
        } else {
          setAttempts(prev => prev + 1);
        }
      } catch (error) {
        console.error('Failed to refresh balance', error);
        if (attempts >= 9) {
          setSuccess(false);
          setIsRefreshing(false);
        } else {
          setAttempts(prev => prev + 1);
        }
      }
    };

    if (isRefreshing) {
      const timer = setTimeout(pollBalance, 3000);
      return () => clearTimeout(timer);
    }
  }, [refreshBalance, isRefreshing, attempts]);

  return (
    <div style={{ padding: '4rem 2rem', textAlign: 'center', fontFamily: 'sans-serif', maxWidth: '600px', margin: '0 auto' }}>
      <SEO title="Purchase Successful" />
      
      {isRefreshing ? (
        <div style={{ animation: 'fadeIn 0.5s ease-in' }}>
          <div className="spinner" style={{ 
            margin: '0 auto 2rem',
            width: '60px', 
            height: '60px', 
            border: '5px solid #f3f3f3', 
            borderTop: '5px solid #6772e5', 
            borderRadius: '50%', 
            animation: 'spin 1s linear infinite' 
          }} />
          <h1>Finalizing your purchase...</h1>
          <p style={{ color: '#666', fontSize: '1.1rem' }}>We're just waiting for the tokens to arrive in your wallet. This usually takes a few seconds.</p>
        </div>
      ) : success ? (
        <div style={{ animation: 'fadeIn 0.8s ease-out' }}>
          <div style={{ fontSize: '5rem', marginBottom: '1.5rem' }}>💰</div>
          <h1 style={{ color: '#4caf50', marginBottom: '1rem' }}>Tokens Added!</h1>
          <p style={{ fontSize: '1.2rem', marginBottom: '2rem' }}>
            Your purchase was successful. Your new balance is: 
            <strong style={{ display: 'block', fontSize: '2.5rem', color: '#6772e5', marginTop: '0.5rem' }}>
              {balance} Tokens
            </strong>
          </p>
          
          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', marginTop: '3rem' }}>
            <button 
              onClick={() => navigate('/dashboard')}
              style={{ 
                padding: '12px 24px', 
                backgroundColor: '#6772e5', 
                color: 'white', 
                border: 'none', 
                borderRadius: '8px',
                cursor: 'pointer',
                fontSize: '1rem',
                fontWeight: 'bold',
                boxShadow: '0 4px 6px rgba(103, 114, 229, 0.2)'
              }}
            >
              Go to Viewer Hub
            </button>
            <button 
              onClick={() => navigate('/')}
              style={{ 
                padding: '12px 24px', 
                backgroundColor: '#f3f3f3', 
                color: '#333', 
                border: 'none', 
                borderRadius: '8px',
                cursor: 'pointer',
                fontSize: '1rem',
                fontWeight: 'bold'
              }}
            >
              Back Home
            </button>
          </div>
        </div>
      ) : (
        <div style={{ animation: 'fadeIn 0.8s ease-out' }}>
          <div style={{ fontSize: '5rem', marginBottom: '1.5rem' }}>⏳</div>
          <h1 style={{ color: '#f59e0b', marginBottom: '1rem' }}>Payment received — tokens on their way</h1>
          <p style={{ fontSize: '1.1rem', color: '#666', marginBottom: '2rem' }}>
            Your payment was successful, but the tokens haven't arrived in your wallet yet. 
            This can take up to a minute. If they don't appear shortly, please check your balance later or contact support.
          </p>
          <p style={{ fontSize: '1rem', color: '#999', marginBottom: '2rem' }}>
            Current balance: <strong style={{ color: '#6772e5' }}>{balance} Tokens</strong>
          </p>
          
          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', marginTop: '2rem' }}>
            <button 
              onClick={() => {
                setAttempts(0);
                setIsRefreshing(true);
              }}
              style={{ 
                padding: '12px 24px', 
                backgroundColor: '#6772e5', 
                color: 'white', 
                border: 'none', 
                borderRadius: '8px',
                cursor: 'pointer',
                fontSize: '1rem',
                fontWeight: 'bold',
                boxShadow: '0 4px 6px rgba(103, 114, 229, 0.2)'
              }}
            >
              Check Again
            </button>
            <button 
              onClick={() => navigate('/dashboard')}
              style={{ 
                padding: '12px 24px', 
                backgroundColor: '#f3f3f3', 
                color: '#333', 
                border: 'none', 
                borderRadius: '8px',
                cursor: 'pointer',
                fontSize: '1rem',
                fontWeight: 'bold'
              }}
            >
              Go to Viewer Hub
            </button>
          </div>
        </div>
      )}

      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(10px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>
  );
};

export default TokenPurchaseSuccess;
