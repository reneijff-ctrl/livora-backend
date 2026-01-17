import React, { useState } from 'react';
import { useAuth } from '../auth/useAuth';
import paymentService from '../api/paymentService';
import SEO from '../components/SEO';
import Loader from '../components/Loader';
import { showToast } from '../components/Toast';
import { Link } from 'react-router-dom';

const SubscriptionPage: React.FC = () => {
  const { isAuthenticated, isLoading, hasPremiumAccess, subscriptionStatus } = useAuth();
  const [isProcessing, setIsProcessing] = useState(false);

  if (isLoading) {
    return <Loader type="skeleton" />;
  }

  const handleSubscribe = async () => {
    if (hasPremiumAccess()) {
      showToast('You already have premium access!', 'success');
      return;
    }

    setIsProcessing(true);
    
    try {
      const { redirectUrl } = await paymentService.createCheckoutSession();
      if (redirectUrl) {
        window.location.href = redirectUrl;
      } else {
        throw new Error('No checkout URL received');
      }
    } catch (err: any) {
      console.error('Checkout error:', err);
      setIsProcessing(false);
    }
  };

  if (!isLoading && !isAuthenticated) {
    window.location.href = '/login';
    return null;
  }

  return (
    <div style={{ padding: '4rem 2rem', maxWidth: '600px', margin: '0 auto', fontFamily: 'sans-serif', textAlign: 'center' }}>
      <SEO title="Upgrade to Premium" canonical="/subscription" />
      
      {hasPremiumAccess() ? (
        <div>
          <div style={{ fontSize: '4rem', marginBottom: '1rem' }}>✨</div>
          <h1>You're a Premium Member!</h1>
          <p style={{ color: '#666', marginBottom: '2rem' }}>
            Thank you for supporting Livora. Your subscription status is: <strong>{subscriptionStatus}</strong>
          </p>
          <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
            <Link to="/dashboard" style={{ color: '#6772e5', textDecoration: 'none', fontWeight: 'bold' }}>Dashboard</Link>
            <Link to="/billing" style={{ color: '#6772e5', textDecoration: 'none', fontWeight: 'bold' }}>Manage Billing</Link>
          </div>
        </div>
      ) : (
        <>
          <h1>Upgrade to Premium</h1>
          <p style={{ color: '#666', marginBottom: '2rem' }}>
            Get unlimited access to all premium content, priority support, and an ad-free experience.
          </p>
          
          <div style={{ 
            padding: '3rem 2rem', 
            border: '2px solid #6772e5', 
            borderRadius: '12px', 
            backgroundColor: '#fff',
            boxShadow: '0 4px 12px rgba(103, 114, 229, 0.1)'
          }}>
            <h3 style={{ color: '#6772e5', marginTop: 0 }}>Premium Monthly</h3>
            <p style={{ fontSize: '2.5rem', fontWeight: 'bold', margin: '1rem 0' }}>€9.99<span style={{ fontSize: '1rem', color: '#666' }}>/month</span></p>
            
            <ul style={{ listStyle: 'none', padding: 0, margin: '2rem 0', textAlign: 'left', display: 'inline-block' }}>
              <li>✅ Full access to all content</li>
              <li>✅ Priority customer support</li>
              <li>✅ No advertisements</li>
            </ul>

            <button
              onClick={handleSubscribe}
              disabled={isProcessing}
              style={{
                padding: '14px 30px',
                fontSize: '1.1rem',
                fontWeight: 'bold',
                backgroundColor: isProcessing ? '#aab1f0' : '#6772e5',
                color: 'white',
                border: 'none',
                borderRadius: '6px',
                cursor: isProcessing ? 'not-allowed' : 'pointer',
                opacity: isProcessing ? 0.7 : 1,
                width: '100%',
                marginTop: '1rem',
                transition: 'all 0.2s ease'
              }}
            >
              {isProcessing ? (
                <span style={{ display: 'flex', alignItems: 'center', justifyContent: 'center', gap: '10px' }}>
                   <div className="spinner" style={{ 
                    width: '18px', 
                    height: '18px', 
                    border: '2px solid rgba(255,255,255,0.3)', 
                    borderTop: '2px solid white', 
                    borderRadius: '50%',
                    animation: 'spin 0.8s linear infinite'
                  }} />
                  Redirecting to Stripe...
                </span>
              ) : 'Upgrade Now'}
            </button>
          </div>
          
          <div style={{ marginTop: '2rem' }}>
            <Link to="/dashboard" style={{ color: '#666', textDecoration: 'none' }}>Back to Dashboard</Link>
          </div>
        </>
      )}
      <style>{`
        @keyframes spin {
          0% { transform: rotate(0deg); }
          100% { transform: rotate(360deg); }
        }
      `}</style>
    </div>
  );
};

export default SubscriptionPage;
