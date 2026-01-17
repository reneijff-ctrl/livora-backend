import React, { useState, useEffect } from 'react';
import { useAuth } from '../auth/useAuth';
import paymentService from '../api/paymentService';
import { useLocation, Link } from 'react-router-dom';
import { showToast } from '../components/Toast';
import SEO from '../components/SEO';

const PricingPage: React.FC = () => {
  const { subscriptionStatus, isAuthenticated, hasPremiumAccess } = useAuth();
  const [isProcessing, setIsProcessing] = useState(false);
  const location = useLocation();

  useEffect(() => {
    if (location.state?.reason === 'subscription_lost') {
      showToast('Your subscription is no longer active', 'error');
      // Clear state to avoid showing toast again on re-render
      window.history.replaceState({}, document.title);
    }
  }, [location]);

  const handleUpgrade = async () => {
    if (!isAuthenticated) {
      window.location.href = '/login';
      return;
    }

    if (hasPremiumAccess()) {
      showToast('You already have premium access!', 'success');
      return;
    }

    setIsProcessing(true);

    try {
      const { redirectUrl } = await paymentService.createCheckoutSession();
      // Redirect to Stripe Checkout
      window.location.href = redirectUrl;
    } catch (err) {
      console.error('Checkout error:', err);
      // Handled by global interceptor
      setIsProcessing(false);
    }
  };

  const isActive = subscriptionStatus === 'ACTIVE';
  const hasAccess = hasPremiumAccess();

  return (
    <div style={{ 
      padding: '4rem 2rem', 
      maxWidth: '900px', 
      margin: '0 auto', 
      fontFamily: 'sans-serif',
      textAlign: 'center'
    }}>
      <SEO 
        title="Pricing" 
        description="Choose the best plan for your needs. Upgrade to Premium for full access to all features."
        canonical="/pricing"
      />
      <h1>Simple, Transparent Pricing</h1>
      <p style={{ color: '#666', marginBottom: '3rem' }}>
        Unlock all premium features and support our development.
      </p>

      <div style={{ 
        display: 'flex', 
        gap: '2rem', 
        justifyContent: 'center',
        flexWrap: 'wrap'
      }}>
        {/* Free Plan */}
        <div style={{ 
          border: '1px solid #ddd', 
          borderRadius: '12px', 
          padding: '2rem', 
          backgroundColor: '#fff',
          flex: '1',
          minWidth: '300px',
          maxWidth: '400px'
        }}>
          <h2>Free</h2>
          <div style={{ fontSize: '3rem', fontWeight: 'bold', margin: '1rem 0' }}>
            €0 <span style={{ fontSize: '1rem', color: '#666', fontWeight: 'normal' }}>/ month</span>
          </div>
          <ul style={{ listStyle: 'none', padding: 0, margin: '2rem 0', textAlign: 'left' }}>
            <li style={{ marginBottom: '0.5rem' }}>✅ Basic content access</li>
            <li style={{ marginBottom: '0.5rem' }}>✅ Community forum</li>
            <li style={{ marginBottom: '0.5rem' }}>❌ Premium features</li>
            <li style={{ marginBottom: '0.5rem' }}>❌ Ad-free experience</li>
          </ul>
          <button
            disabled={true}
            style={{
              backgroundColor: '#f3f3f3',
              color: '#666',
              padding: '12px 30px',
              fontSize: '1.1rem',
              fontWeight: 'bold',
              border: 'none',
              borderRadius: '6px',
              cursor: 'default',
              width: '100%'
            }}
          >
            {subscriptionStatus === 'NONE' ? 'Current Plan' : 'Free Tier'}
          </button>
        </div>

        {/* Premium Plan */}
        <div style={{ 
          border: '2px solid #6772e5', 
          borderRadius: '12px', 
          padding: '2rem', 
          backgroundColor: '#fff',
          boxShadow: '0 4px 20px rgba(103, 114, 229, 0.15)',
          flex: '1',
          minWidth: '300px',
          maxWidth: '400px',
          position: 'relative'
        }}>
          <div style={{ 
            position: 'absolute', 
            top: '-15px', 
            left: '50%', 
            transform: 'translateX(-50%)',
            backgroundColor: '#6772e5',
            color: 'white',
            padding: '4px 12px',
            borderRadius: '20px',
            fontSize: '0.8rem',
            fontWeight: 'bold'
          }}>MOST POPULAR</div>
          <h2 style={{ color: '#6772e5' }}>Premium</h2>
          <div style={{ fontSize: '3rem', fontWeight: 'bold', margin: '1rem 0' }}>
            €9.99 <span style={{ fontSize: '1rem', color: '#666', fontWeight: 'normal' }}>/ month</span>
          </div>
          <ul style={{ listStyle: 'none', padding: 0, margin: '2rem 0', textAlign: 'left' }}>
            <li style={{ marginBottom: '0.5rem' }}>✅ Full access to all premium content</li>
            <li style={{ marginBottom: '0.5rem' }}>✅ Priority support</li>
            <li style={{ marginBottom: '0.5rem' }}>✅ Exclusive community features</li>
            <li style={{ marginBottom: '0.5rem' }}>✅ Ad-free experience</li>
          </ul>
          <button
            onClick={handleUpgrade}
            disabled={isProcessing || hasAccess}
            style={{
              backgroundColor: hasAccess ? '#4caf50' : '#6772e5',
              color: 'white',
              padding: '12px 30px',
              fontSize: '1.1rem',
              fontWeight: 'bold',
              border: 'none',
              borderRadius: '6px',
              cursor: (isProcessing || hasAccess) ? 'not-allowed' : 'pointer',
              width: '100%',
              transition: 'background-color 0.2s'
            }}
          >
            {isProcessing ? 'Processing...' : hasAccess ? (isActive ? 'Current Plan' : 'Premium Active') : 'Upgrade to Premium'}
          </button>
        </div>
      </div>

      <div style={{ marginTop: '3rem' }}>
        <Link to="/" style={{ color: '#666', textDecoration: 'none' }}>← Back to Home</Link>
      </div>
    </div>
  );
};

export default PricingPage;
