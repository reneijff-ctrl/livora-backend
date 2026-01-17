import React, { useEffect, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import SEO from '../components/SEO';

const PaymentSuccessPage: React.FC = () => {
  const { bootstrap, subscriptionStatus } = useAuth();
  const navigate = useNavigate();
  const [isActivating, setIsActivating] = useState(true);

  useEffect(() => {
    let pollInterval: number;
    let attempts = 0;
    const maxAttempts = 5;

    const poll = async () => {
      attempts++;
      console.log(`Polling bootstrap, attempt ${attempts}`);
      try {
        await bootstrap();
      } catch (error) {
        console.error('Error bootstrapping during poll', error);
      }
    };

    if (subscriptionStatus === 'NONE' || subscriptionStatus === 'EXPIRED' || subscriptionStatus === 'PAST_DUE') {
      poll();
      pollInterval = window.setInterval(() => {
        if (attempts >= maxAttempts || (subscriptionStatus !== 'NONE' && subscriptionStatus !== 'EXPIRED')) {
          clearInterval(pollInterval);
          setIsActivating(false);
          return;
        }
        poll();
      }, 3000);
    } else {
      setIsActivating(false);
    }

    return () => clearInterval(pollInterval);
  }, [bootstrap, subscriptionStatus]);

  return (
    <div style={{ padding: '4rem 2rem', textAlign: 'center', fontFamily: 'sans-serif' }}>
      <SEO title="Payment Successful" canonical="/payment/success" />
      
      {isActivating && subscriptionStatus !== 'ACTIVE' ? (
        <div>
          <div className="spinner" style={{ 
            margin: '0 auto 2rem',
            width: '50px', 
            height: '50px', 
            border: '5px solid #f3f3f3', 
            borderTop: '5px solid #6772e5', 
            borderRadius: '50%', 
            animation: 'spin 1s linear infinite' 
          }} />
          <h1>Completing your purchase...</h1>
          <p>We're just waiting for Stripe to confirm your payment. This usually takes a few seconds.</p>
        </div>
      ) : (
        <div style={{ animation: 'fadeIn 1s ease-out' }}>
          <div style={{ fontSize: '5rem', marginBottom: '1rem' }}>🎉</div>
          <h1 style={{ color: '#4caf50' }}>Payment successful!</h1>
          <p style={{ fontSize: '1.2rem' }}>Welcome to <strong>Livora Premium</strong>. Your account has been upgraded.</p>
          
          <div style={{ marginTop: '3rem' }}>
            <button 
              onClick={() => navigate('/dashboard')}
              style={{ 
                padding: '12px 30px', 
                backgroundColor: '#6772e5', 
                color: 'white', 
                border: 'none', 
                borderRadius: '6px',
                cursor: 'pointer',
                fontSize: '1.1rem',
                fontWeight: 'bold',
                boxShadow: '0 4px 6px rgba(103, 114, 229, 0.2)'
              }}
            >
              Go to my Dashboard
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

export default PaymentSuccessPage;
