import React, { useState } from 'react';
import { useAuth } from '../auth/useAuth';
import paymentService from '../api/paymentService';
import { showToast } from '../components/Toast';
import { Link } from 'react-router-dom';
import InvoiceTable from '../components/InvoiceTable';
import SEO from '../components/SEO';

const BillingPage: React.FC = () => {
  const { user, subscriptionStatus, refreshSubscription } = useAuth();
  const [isProcessing, setIsProcessing] = useState(false);
  const [showCancelModal, setShowCancelModal] = useState(false);

  const currentPeriodEnd = user?.subscription.currentPeriodEnd;
  const nextInvoiceDate = user?.subscription.nextInvoiceDate;
  const paymentMethod = user?.subscription.paymentMethodBrand;
  const last4 = user?.subscription.last4;

  const handleManagePayment = async () => {
    if (isProcessing) return;
    setIsProcessing(true);
    try {
      await paymentService.openBillingPortal();
    } catch (error) {
      console.error('Failed to open billing portal', error);
      showToast('Something went wrong. Please try again.', 'error');
      setIsProcessing(false);
    }
  };

  const handleCancelSubscription = async () => {
    if (isProcessing) return;
    setIsProcessing(true);
    try {
      await paymentService.cancelSubscription();
      await refreshSubscription();
      showToast('Subscription canceled successfully', 'success');
      setShowCancelModal(false);
    } catch (error) {
      console.error('Failed to cancel subscription', error);
      showToast('Could not cancel subscription. Please contact support.', 'error');
    } finally {
      setIsProcessing(false);
    }
  };

  const handleResumeSubscription = async () => {
    if (isProcessing) return;
    setIsProcessing(true);
    try {
      await paymentService.resumeSubscription();
      await refreshSubscription();
      showToast('Subscription resumed successfully', 'success');
    } catch (error) {
      console.error('Failed to resume subscription', error);
      showToast('Could not resume subscription. Please try again.', 'error');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <div style={{ padding: '2rem', maxWidth: '600px', margin: '0 auto', fontFamily: 'system-ui, -apple-system, sans-serif', color: '#F4F4F5' }}>
      <SEO title="Billing" canonical="/billing" />
      <h1 style={{ fontWeight: 800, marginBottom: '2.5rem' }}>Billing & Subscription</h1>
      
      <div style={{ 
        border: '1px solid rgba(255, 255, 255, 0.05)', 
        borderRadius: '24px', 
        padding: '2rem', 
        backgroundColor: '#0F0F14',
        marginBottom: '2rem',
        boxShadow: '0 20px 60px rgba(0,0,0,0.6)'
      }}>
        <div style={{ marginBottom: '1.25rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span style={{ color: '#A1A1AA', fontWeight: 500 }}>Current Status:</span>
          <strong style={{ 
            color: subscriptionStatus === 'ACTIVE' || subscriptionStatus === 'TRIAL' ? '#4ade80' : '#f87171',
            backgroundColor: subscriptionStatus === 'ACTIVE' || subscriptionStatus === 'TRIAL' ? 'rgba(34, 197, 94, 0.1)' : 'rgba(239, 68, 68, 0.1)',
            padding: '6px 12px',
            borderRadius: '9999px',
            fontSize: '0.75rem',
            textTransform: 'uppercase',
            letterSpacing: '0.05em',
            border: '1px solid currentColor'
          }}>
            {subscriptionStatus}
          </strong>
        </div>

        {currentPeriodEnd && (
          <div style={{ marginBottom: '1.25rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ color: '#A1A1AA', fontWeight: 500 }}>{subscriptionStatus === 'CANCELED' ? 'Access ends on:' : 'Current period ends:'}</span>
            <strong style={{ color: '#F4F4F5' }}>{currentPeriodEnd ? new Date(currentPeriodEnd).toLocaleDateString() : 'N/A'}</strong>
          </div>
        )}

        {nextInvoiceDate && subscriptionStatus !== 'CANCELED' && (
          <div style={{ marginBottom: '1.25rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
            <span style={{ color: '#A1A1AA', fontWeight: 500 }}>Next invoice date:</span>
            <strong style={{ color: '#F4F4F5' }}>{nextInvoiceDate ? new Date(nextInvoiceDate).toLocaleDateString() : 'N/A'}</strong>
          </div>
        )}

        {paymentMethod && (
          <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: '1px solid rgba(255, 255, 255, 0.05)', paddingTop: '1.25rem', alignItems: 'center' }}>
            <span style={{ color: '#A1A1AA', fontWeight: 500 }}>Payment Method:</span>
            <span style={{ color: '#F4F4F5' }}>{paymentMethod.toUpperCase()} •••• {last4}</span>
          </div>
        )}
      </div>

      <p style={{ fontSize: '0.9rem', color: '#71717A', marginBottom: '2rem', textAlign: 'center', lineHeight: '1.5' }}>
        Invoices and payment confirmations are emailed automatically to <strong style={{ color: '#A1A1AA' }}>{user?.email}</strong>.
      </p>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1.25rem' }}>
        <button 
          onClick={handleManagePayment} 
          disabled={isProcessing}
          style={{ 
            padding: '1rem', 
            cursor: isProcessing ? 'not-allowed' : 'pointer', 
            backgroundColor: '#6366f1', 
            color: 'white', 
            border: 'none', 
            borderRadius: '12px',
            fontWeight: '800',
            fontSize: '1rem',
            boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)',
            transition: 'all 0.2s ease'
          }}
        >
          {isProcessing ? 'Processing...' : 'Manage Payment Method'}
        </button>

        {subscriptionStatus === 'PAST_DUE' && (
          <button 
            onClick={handleManagePayment} 
            disabled={isProcessing}
            style={{ 
              padding: '1rem', 
              cursor: isProcessing ? 'not-allowed' : 'pointer', 
              backgroundColor: '#ef4444', 
              color: 'white', 
              border: 'none', 
              borderRadius: '12px',
              fontWeight: '800',
              fontSize: '1rem',
              boxShadow: '0 4px 12px rgba(239, 68, 68, 0.3)'
            }}
          >
            Fix Failed Payment
          </button>
        )}

        {subscriptionStatus === 'EXPIRED' && (
          <Link 
            to="/subscription"
            style={{ 
              padding: '1rem', 
              textAlign: 'center',
              backgroundColor: '#6366f1', 
              color: 'white', 
              border: 'none', 
              borderRadius: '12px',
              textDecoration: 'none',
              fontWeight: '800',
              fontSize: '1rem',
              boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)'
            }}
          >
            Restart Subscription
          </Link>
        )}

        {(subscriptionStatus === 'ACTIVE' || subscriptionStatus === 'TRIAL') && (
          <button 
            onClick={() => setShowCancelModal(true)} 
            disabled={isProcessing}
            style={{ 
              padding: '1rem', 
              cursor: isProcessing ? 'not-allowed' : 'pointer', 
              backgroundColor: 'rgba(255, 255, 255, 0.05)', 
              color: '#f87171', 
              border: '1px solid rgba(248, 113, 113, 0.3)', 
              borderRadius: '12px',
              fontWeight: '700',
              fontSize: '1rem',
              transition: 'all 0.2s ease'
            }}
          >
            Cancel Subscription
          </button>
        )}

        {subscriptionStatus === 'CANCELED' && currentPeriodEnd && new Date(currentPeriodEnd) > new Date() && (
          <button 
            onClick={handleResumeSubscription} 
            disabled={isProcessing}
            style={{ 
              padding: '1rem', 
              cursor: isProcessing ? 'not-allowed' : 'pointer', 
              backgroundColor: '#10b981', 
              color: 'white', 
              border: 'none', 
              borderRadius: '12px',
              fontWeight: '800',
              fontSize: '1rem',
              boxShadow: '0 4px 12px rgba(16, 185, 129, 0.3)'
            }}
          >
            Resume Subscription
          </button>
        )}
      </div>

      <div style={{ marginTop: '4rem' }}>
        <h2 style={{ fontSize: '1.5rem', fontWeight: 800, marginBottom: '1.5rem' }}>Invoice History</h2>
        <InvoiceTable />
      </div>

      <div style={{ marginTop: '3rem' }}>
        <Link to="/dashboard" style={{ color: '#71717A', textDecoration: 'none', fontWeight: '600' }}>← Back to Dashboard</Link>
      </div>

      {showCancelModal && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.8)', display: 'flex', alignItems: 'center', justifyContent: 'center', zIndex: 1000, backdropFilter: 'blur(4px)'
        }}>
          <div style={{ backgroundColor: '#0F0F14', padding: '3rem 2rem', borderRadius: '24px', maxWidth: '400px', textAlign: 'center', border: '1px solid rgba(255, 255, 255, 0.05)', boxShadow: '0 20px 60px rgba(0,0,0,0.8)' }}>
            <h3 style={{ fontSize: '1.5rem', fontWeight: 800, marginBottom: '1rem', color: '#F4F4F5' }}>Confirm Cancellation</h3>
            <p style={{ color: '#71717A', lineHeight: '1.6', marginBottom: '2rem' }}>Are you sure you want to cancel your subscription? You will keep your access until the end of the current billing period.</p>
            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center' }}>
              <button 
                onClick={() => setShowCancelModal(false)}
                style={{ padding: '0.875rem 1.5rem', cursor: 'pointer', backgroundColor: 'rgba(255, 255, 255, 0.05)', color: '#F4F4F5', border: '1px solid rgba(255, 255, 255, 0.1)', borderRadius: '12px', fontWeight: '700' }}
              >
                Go Back
              </button>
              <button 
                onClick={handleCancelSubscription}
                disabled={isProcessing}
                style={{ padding: '0.875rem 1.5rem', cursor: isProcessing ? 'not-allowed' : 'pointer', backgroundColor: '#ef4444', color: 'white', border: 'none', borderRadius: '12px', fontWeight: '800', boxShadow: '0 4px 12px rgba(239, 68, 68, 0.3)' }}
              >
                {isProcessing ? 'Canceling...' : 'Yes, Cancel'}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default BillingPage;
