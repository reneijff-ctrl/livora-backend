import React, { useState, useEffect } from 'react';
import { useAuth } from '../auth/useAuth';
import payoutService, { StripeAccount, Payout } from '../api/payoutService';
import { showToast } from '../components/Toast';
import SEO from '../components/SEO';
import { Link } from 'react-router-dom';

const PayoutDashboard: React.FC = () => {
  const { tokenBalance, user, authLoading } = useAuth();
  const [account, setAccount] = useState<StripeAccount | null>(null);
  const [payouts, setPayouts] = useState<Payout[]>([]);
  const [loading, setLoading] = useState(true);
  const [isProcessing, setIsProcessing] = useState(false);

  useEffect(() => {
    // Requirement: Do NOT fetch dashboard data until role is resolved
    if (!user || authLoading) {
      return;
    }
    fetchData();
  }, [user, authLoading]);

  const fetchData = async () => {
    try {
      setLoading(true);
      const [accStatus, history] = await Promise.all([
        payoutService.getAccountStatus().catch(() => null),
        payoutService.getPayoutHistory().catch(() => [])
      ]);
      setAccount(accStatus);
      setPayouts(history);
    } catch (error) {
      console.error('Failed to fetch payout data', error);
    } finally {
      setLoading(false);
    }
  };

  const handleStartOnboarding = async () => {
    setIsProcessing(true);
    try {
      const { onboardingUrl } = await payoutService.startOnboarding();
      window.location.href = onboardingUrl;
    } catch (error) {
      showToast('Failed to start onboarding', 'error');
      setIsProcessing(false);
    }
  };


  if (loading) return <div style={{ padding: '2rem' }}>Loading Payout Dashboard...</div>;

  return (
    <div style={{ padding: '2rem', maxWidth: '800px', margin: '0 auto', fontFamily: 'sans-serif' }}>
      <SEO title="Creator Payouts" />
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '2rem' }}>
        <h1>💰 Creator Payouts</h1>
        <Link to="/dashboard">Back to Viewer Hub</Link>
      </div>

      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2rem', marginBottom: '3rem' }}>
        <div style={{ padding: '1.5rem', border: '1px solid #ddd', borderRadius: '8px', backgroundColor: '#f9f9f9' }}>
          <h3>Stripe Connect Status</h3>
          {!account ? (
            <div>
              <p>You haven't connected your Stripe account yet.</p>
              <button 
                onClick={handleStartOnboarding} 
                disabled={isProcessing}
                style={{ padding: '10px 20px', backgroundColor: '#6772e5', color: 'white', border: 'none', borderRadius: '4px', cursor: 'pointer' }}
              >
                Connect with Stripe
              </button>
            </div>
          ) : (
            <div>
              <p>Account ID: <code>{account.stripeAccountId}</code></p>
              <p>Status: <strong style={{ color: account.onboardingCompleted ? 'green' : 'orange' }}>
                {account.onboardingCompleted ? 'Ready' : 'Incomplete'}
              </strong></p>
              <p>Payouts: <strong>{account.payoutsEnabled ? 'Enabled' : 'Disabled'}</strong></p>
              {!account.onboardingCompleted && (
                <button onClick={handleStartOnboarding} style={{ marginTop: '0.5rem' }}>Complete Onboarding</button>
              )}
            </div>
          )}
        </div>

        <div style={{ padding: '1.5rem', border: '1px solid #ddd', borderRadius: '8px', backgroundColor: '#f0f7ff' }}>
          <h3>Automatic Payouts</h3>
          <p>Payouts are processed automatically every day at 03:00 UTC for all eligible creators who meet their minimum payout threshold.</p>
          <p>Available Balance: <strong>{tokenBalance} tokens</strong></p>
          <p style={{ fontSize: '0.8rem', color: '#666', marginTop: '0.5rem' }}>* Ensure your Stripe account is connected and payouts are enabled to receive funds.</p>
        </div>
      </div>

      <h2>Payout History</h2>
      <div style={{ overflowX: 'auto' }}>
        <table style={{ width: '100%', borderCollapse: 'collapse' }}>
          <thead>
            <tr style={{ textAlign: 'left', borderBottom: '2px solid #eee' }}>
              <th style={{ padding: '12px' }}>Date</th>
              <th style={{ padding: '12px' }}>Tokens</th>
              <th style={{ padding: '12px' }}>Amount</th>
              <th style={{ padding: '12px' }}>Status</th>
            </tr>
          </thead>
          <tbody>
            {payouts.length === 0 ? (
              <tr>
                <td colSpan={4} style={{ padding: '3rem', textAlign: 'center' }}>
                  <div style={{ color: '#666', display: 'flex', flexDirection: 'column', alignItems: 'center' }}>
                    <span style={{ fontSize: '2rem', marginBottom: '1rem' }}>🏦</span>
                    <p style={{ fontWeight: '600', margin: 0 }}>No payouts yet</p>
                    <p style={{ fontSize: '0.875rem', marginTop: '0.5rem' }}>Completed payouts will be listed here.</p>
                  </div>
                </td>
              </tr>
            ) : (
              payouts.map(p => (
                <tr key={p.id} style={{ borderBottom: '1px solid #f9f9f9' }}>
                  <td style={{ padding: '12px' }}>{p.createdAt ? new Date(p.createdAt).toLocaleDateString() : 'N/A'}</td>
                  <td style={{ padding: '12px' }}>{p.tokenAmount}</td>
                  <td style={{ padding: '12px' }}>€{p.eurAmount.toFixed(2)}</td>
                  <td style={{ padding: '12px' }}>
                    <span style={{ 
                      padding: '4px 8px', 
                      borderRadius: '4px', 
                      fontSize: '0.8rem',
                      backgroundColor: p.status === 'PAID' ? '#e6ffed' : p.status === 'PENDING' ? '#fff7e6' : '#fff1f0',
                      color: p.status === 'PAID' ? '#28a745' : p.status === 'PENDING' ? '#faad14' : '#cf1322'
                    }}>
                      {p.status}
                    </span>
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
};

export default PayoutDashboard;
