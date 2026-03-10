import React, { useState } from 'react';
import { useAuth } from '../auth/useAuth';
import paymentService from '../api/paymentService';
import SEO from '../components/SEO';
import Loader from '../components/Loader';
import { showToast } from '../components/Toast';
import { Link } from 'react-router-dom';

const SubscriptionPage: React.FC = () => {
  const { isInitialized, hasPremiumAccess, subscriptionStatus } = useAuth();
  const [isProcessing, setIsProcessing] = useState(false);

  if (!isInitialized) {
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


  return (
    <div style={{ padding: '4rem 2rem', maxWidth: '600px', margin: '0 auto', fontFamily: 'system-ui, -apple-system, sans-serif', textAlign: 'center', color: '#F4F4F5' }}>
      <SEO title="Upgrade to Premium" canonical="/subscription" />
      
      {hasPremiumAccess() ? (
        <div>
          <div style={{ fontSize: '4rem', marginBottom: '1.5rem' }}>✨</div>
          <h1 style={{ fontWeight: 800, marginBottom: '1rem' }}>You're a Premium Member!</h1>
          <p style={{ color: '#71717A', marginBottom: '2.5rem', fontSize: '1.1rem' }}>
            Thank you for supporting Livora. Your subscription status is: <strong style={{ color: '#10b981' }}>{subscriptionStatus}</strong>
          </p>
          <div style={{ display: 'flex', gap: '1.25rem', justifyContent: 'center' }}>
            <Link to="/dashboard" style={{ color: '#6366f1', textDecoration: 'none', fontWeight: 'bold' }}>Viewer Hub</Link>
            <Link to="/billing" style={{ color: '#6366f1', textDecoration: 'none', fontWeight: 'bold' }}>Manage Billing</Link>
          </div>
        </div>
      ) : (
        <>
          <h1 style={{ fontWeight: 800, marginBottom: '1rem' }}>Upgrade to Premium</h1>
          <p style={{ color: '#71717A', marginBottom: '3rem', fontSize: '1.1rem' }}>
            Get unlimited access to all premium content, priority support, and an ad-free experience.
          </p>
          
          <div style={{ 
            padding: '4rem 2rem', 
            border: '1px solid rgba(255, 255, 255, 0.05)', 
            borderRadius: '24px', 
            backgroundColor: '#0F0F14',
            boxShadow: '0 20px 60px rgba(0,0,0,0.6)'
          }}>
            <h3 style={{ color: '#6366f1', marginTop: 0, fontWeight: 800, textTransform: 'uppercase', letterSpacing: '0.1em' }}>Premium Monthly</h3>
            <p style={{ fontSize: '3.5rem', fontWeight: '800', margin: '1.5rem 0', color: '#F4F4F5' }}>€9.99<span style={{ fontSize: '1.25rem', color: '#71717A', fontWeight: 500 }}>/month</span></p>
            
            <ul style={{ listStyle: 'none', padding: 0, margin: '2rem 0', textAlign: 'left', display: 'inline-block' }}>
              <li style={{ marginBottom: '0.75rem', color: '#A1A1AA' }}>✅ Full access to all content</li>
              <li style={{ marginBottom: '0.75rem', color: '#A1A1AA' }}>✅ Priority customer support</li>
              <li style={{ marginBottom: '0.75rem', color: '#A1A1AA' }}>✅ No advertisements</li>
            </ul>

            <button
              onClick={handleSubscribe}
              disabled={isProcessing}
              style={{
                padding: '1rem 2rem',
                fontSize: '1.125rem',
                fontWeight: '800',
                backgroundColor: '#6366f1',
                color: 'white',
                border: 'none',
                borderRadius: '12px',
                cursor: isProcessing ? 'not-allowed' : 'pointer',
                width: '100%',
                marginTop: '1rem',
                transition: 'all 0.2s ease',
                boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)'
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
          
          <div style={{ marginTop: '3rem' }}>
            <Link to="/dashboard" style={{ color: '#71717A', textDecoration: 'none', fontWeight: '600' }}>Back to Viewer Hub</Link>
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
