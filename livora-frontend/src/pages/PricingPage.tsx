import React, { useState, useEffect } from 'react';
import { useAuth } from '../auth/useAuth';
import paymentService, { SubscriptionPlan } from '../api/paymentService';
import { useLocation, Link, useNavigate } from 'react-router-dom';
import { showToast } from '../components/Toast';
import SEO from '../components/SEO';
import { getDashboardRouteByRole } from '../store/authStore';

const PricingPage: React.FC = () => {
  const { subscriptionStatus, isAuthenticated, hasPremiumAccess, user } = useAuth();
  const [plans, setPlans] = useState<SubscriptionPlan[]>([]);
  const [isLoadingPlans, setIsLoadingPlans] = useState(true);
  const [isProcessing, setIsProcessing] = useState<string | null>(null);
  const location = useLocation();
  const navigate = useNavigate();

  useEffect(() => {
    const fetchPlans = async () => {
      try {
        const data = await paymentService.getPlans();
        setPlans(data);
      } catch (err) {
        console.error('Failed to fetch plans:', err);
        showToast('Failed to load pricing plans. Please try again later.', 'error');
      } finally {
        setIsLoadingPlans(false);
      }
    };

    fetchPlans();
  }, []);

  useEffect(() => {
    if (location.state?.reason === 'subscription_lost') {
      showToast('Your subscription is no longer active', 'error');
      // Clear state to avoid showing toast again on re-render
      window.history.replaceState({}, document.title);
    }
  }, [location]);

  const handleUpgrade = async (plan: SubscriptionPlan) => {
    if (!isAuthenticated) {
      navigate('/login', { state: { from: location } });
      return;
    }

    if (plan.id === 'free') {
      return;
    }

    if (hasPremiumAccess()) {
      showToast('You already have premium access!', 'success');
      return;
    }

    setIsProcessing(plan.id);

    try {
      // Pass the stripe price ID if available, otherwise the plan id
      const { redirectUrl } = await paymentService.createCheckoutSession(plan.stripePriceId || plan.id);
      // Redirect to Stripe Checkout
      window.location.href = redirectUrl;
    } catch (err) {
      console.error('Checkout error:', err);
      // Handled by global interceptor
      setIsProcessing(null);
    }
  };

  const isActive = subscriptionStatus === 'ACTIVE';
  const hasAccess = hasPremiumAccess();

  if (isLoadingPlans) {
    return (
      <div style={{ padding: '4rem 2rem', textAlign: 'center' }}>
        <SEO title="Pricing" canonical="/pricing" />
        <h1>Loading Plans...</h1>
      </div>
    );
  }

  return (
    <div style={{ 
      padding: '4rem 2rem', 
      maxWidth: '1200px', 
      margin: '0 auto', 
      fontFamily: 'system-ui, -apple-system, sans-serif',
      textAlign: 'center'
    }}>
      <SEO 
        title="Pricing" 
        description="Choose the best plan for your needs. Upgrade to Premium for full access to all features."
        canonical="/pricing"
      />
      <h1 style={{ fontSize: '2.5rem', fontWeight: '800', marginBottom: '1rem', color: '#F4F4F5' }}>Simple, Transparent Pricing</h1>
      <p style={{ color: '#71717A', fontSize: '1.125rem', marginBottom: '4rem', maxWidth: '600px', margin: '0 auto 4rem' }}>
        Unlock all premium features and support our development.
      </p>

      <div style={{ 
        display: 'flex', 
        gap: '2rem', 
        justifyContent: 'center',
        flexWrap: 'wrap',
        alignItems: 'stretch'
      }}>
        {plans.map((plan) => (
          <div 
            key={plan.id}
            style={{ 
              border: plan.isPopular ? '2px solid #6366F1' : '1px solid rgba(255, 255, 255, 0.05)', 
              borderRadius: '24px', 
              padding: '3rem 2rem', 
              backgroundColor: '#0F0F14',
              boxShadow: '0 20px 60px rgba(0,0,0,0.6)',
              flex: '1',
              minWidth: '300px',
              maxWidth: '400px',
              position: 'relative',
              display: 'flex',
              flexDirection: 'column',
              transition: 'transform 0.2s ease',
            }}
          >
            {plan.isPopular && (
              <div style={{ 
                position: 'absolute', 
                top: '-14px', 
                left: '50%', 
                transform: 'translateX(-50%)',
                backgroundColor: '#6366F1',
                color: 'white',
                padding: '4px 14px',
                borderRadius: '20px',
                fontSize: '0.75rem',
                fontWeight: 'bold',
                letterSpacing: '0.05em'
              }}>MOST POPULAR</div>
            )}
            
            <h2 style={{ 
              fontSize: '1.5rem', 
              fontWeight: '700', 
              color: plan.isPopular ? '#6366F1' : '#F4F4F5',
              marginBottom: '1rem'
            }}>
              {plan.name}
            </h2>
            
            <div style={{ display: 'flex', alignItems: 'baseline', justifyContent: 'center', margin: '1.5rem 0' }}>
              <span style={{ fontSize: '3.5rem', fontWeight: '800', color: '#F4F4F5' }}>
                {plan.currency === 'EUR' ? '€' : plan.currency}{plan.price}
              </span>
              <span style={{ fontSize: '1.125rem', color: '#71717A', marginLeft: '0.25rem' }}>/{plan.interval}</span>
            </div>
            
            <ul style={{ 
              listStyle: 'none', 
              padding: 0, 
              margin: '2rem 0', 
              textAlign: 'left',
              flex: 1
            }}>
              {plan.features.map((feature, idx) => (
                <li key={idx} style={{ 
                  display: 'flex', 
                  alignItems: 'flex-start', 
                  marginBottom: '1rem',
                  fontSize: '1rem',
                  color: '#A1A1AA'
                }}>
                  <span style={{ color: '#10b981', marginRight: '0.75rem', fontWeight: 'bold' }}>✓</span>
                  {feature}
                </li>
              ))}
            </ul>
            
            <button
              onClick={() => handleUpgrade(plan)}
              disabled={!!isProcessing || (plan.id === 'free') || (plan.id === 'premium' && hasAccess)}
              style={{
                backgroundColor: (plan.id === 'premium' && hasAccess) ? '#10b981' : (plan.id === 'free' ? 'rgba(255, 255, 255, 0.05)' : '#6366F1'),
                color: plan.id === 'free' ? '#71717A' : 'white',
                padding: '1rem 2rem',
                fontSize: '1.125rem',
                fontWeight: '700',
                border: 'none',
                borderRadius: '12px',
                cursor: (isProcessing || (plan.id === 'free') || (plan.id === 'premium' && hasAccess)) ? 'not-allowed' : 'pointer',
                width: '100%',
                transition: 'all 0.2s ease',
                boxShadow: plan.isPopular && !hasAccess ? '0 4px 12px rgba(99, 102, 241, 0.3)' : 'none'
              }}
            >
              {isProcessing === plan.id ? 'Processing...' : 
               (plan.id === 'free') ? (subscriptionStatus === 'NONE' ? 'Current Plan' : 'Free Tier') :
               hasAccess ? (isActive ? 'Current Plan' : 'Premium Active') : 
               `Get ${plan.name}`}
            </button>
          </div>
        ))}
      </div>

      <div style={{ marginTop: '4rem' }}>
        <Link 
          to={getDashboardRouteByRole(user?.role)} 
          style={{ 
            color: '#71717A', 
            textDecoration: 'none',
            fontWeight: '600',
            display: 'inline-flex',
            alignItems: 'center',
            gap: '0.5rem'
          }}
        >
          <span>←</span> {getDashboardRouteByRole(user?.role).includes('dashboard') || getDashboardRouteByRole(user?.role) === '/admin' ? 'Back to Dashboard' : (isAuthenticated ? 'Back to Viewer Hub' : 'Back to Home')}
        </Link>
      </div>
    </div>
  );
};

export default PricingPage;
