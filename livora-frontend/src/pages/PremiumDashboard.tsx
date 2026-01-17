import React, { useState, useEffect } from 'react';
import { useAuth } from '../auth/useAuth';
import { Link } from 'react-router-dom';
import paymentService, { SubscriptionResponse } from '../api/paymentService';
import SEO from '../components/SEO';

const PremiumDashboard: React.FC = () => {
  const { user, subscriptionStatus } = useAuth();
  const [subscription, setSubscription] = useState<SubscriptionResponse | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    const fetchSubscription = async () => {
      try {
        const sub = await paymentService.getMySubscription();
        setSubscription(sub);
      } catch (err) {
        console.error('Failed to fetch subscription', err);
      } finally {
        setLoading(false);
      }
    };

    fetchSubscription();
  }, []);

  return (
    <div style={{ padding: '2rem' }}>
      <SEO title="Premium Dashboard" canonical="/premium-dashboard" />
      <h1>Premium Dashboard</h1>
      <div style={{ border: '1px solid #ffd700', padding: '1rem', borderRadius: '8px', backgroundColor: '#fffdf0' }}>
        <h2>Welcome Premium User!</h2>
        <p>Thank you for being a valued member of Livora.</p>
        
        {user && (
          <div style={{ marginTop: '1rem' }}>
            <p>User: <strong>{user.email}</strong></p>
            <p>Subscription Status: <strong style={{ color: 'green' }}>{subscriptionStatus}</strong></p>
            <p>Access Level: <strong>PREMIUM</strong></p>
            
            {loading ? (
              <p>Loading subscription details...</p>
            ) : subscription ? (
              <div style={{ marginTop: '0.5rem', padding: '0.5rem', background: '#e8f5e9', borderRadius: '4px' }}>
                <p>Status: <strong>{subscription.status}</strong></p>
                {subscription.currentPeriodEnd && (
                  <p>Current period ends on: <strong>{new Date(subscription.currentPeriodEnd).toLocaleDateString()}</strong></p>
                )}
              </div>
            ) : (
              <p>Could not load detailed subscription information.</p>
            )}
          </div>
        )}
      </div>

      <div style={{ marginTop: '2rem' }}>
        <Link to="/dashboard">Back to General Dashboard</Link>
      </div>
    </div>
  );
};

export default PremiumDashboard;
