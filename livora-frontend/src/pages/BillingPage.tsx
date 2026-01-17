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

  const currentPeriodEnd = user?.subscription.renewalDate;
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
    <div style={{ padding: '2rem', maxWidth: '600px', margin: '0 auto', fontFamily: 'sans-serif' }}>
      <SEO title="Billing" canonical="/billing" />
      <h1>Billing & Subscription</h1>
      
      <div style={{ 
        border: '1px solid #ddd', 
        borderRadius: '8px', 
        padding: '1.5rem', 
        backgroundColor: '#f9f9f9',
        marginBottom: '2rem'
      }}>
        <div style={{ marginBottom: '1rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <span>Current Status:</span>
          <strong style={{ 
            color: subscriptionStatus === 'ACTIVE' || subscriptionStatus === 'TRIAL' ? '#4caf50' : '#f44336',
            backgroundColor: subscriptionStatus === 'ACTIVE' || subscriptionStatus === 'TRIAL' ? '#e8f5e9' : '#ffebee',
            padding: '4px 8px',
            borderRadius: '4px'
          }}>
            {subscriptionStatus}
          </strong>
        </div>

        {currentPeriodEnd && (
          <div style={{ marginBottom: '1rem', display: 'flex', justifyContent: 'space-between' }}>
            <span>{subscriptionStatus === 'CANCELED' ? 'Access ends on:' : 'Current period ends:'}</span>
            <strong>{new Date(currentPeriodEnd).toLocaleDateString()}</strong>
          </div>
        )}

        {nextInvoiceDate && subscriptionStatus !== 'CANCELED' && (
          <div style={{ marginBottom: '1rem', display: 'flex', justifyContent: 'space-between' }}>
            <span>Next invoice date:</span>
            <strong>{new Date(nextInvoiceDate).toLocaleDateString()}</strong>
          </div>
        )}

        {paymentMethod && (
          <div style={{ display: 'flex', justifyContent: 'space-between', borderTop: '1px solid #eee', paddingTop: '1rem' }}>
            <span>Payment Method:</span>
            <span>{paymentMethod.toUpperCase()} •••• {last4}</span>
          </div>
        )}
      </div>

      <p style={{ fontSize: '0.9rem', color: '#666', marginBottom: '1.5rem', textAlign: 'center' }}>
        Invoices and payment confirmations are emailed automatically to <strong>{user?.email}</strong>.
      </p>

      <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
        <button 
          onClick={handleManagePayment} 
          disabled={isProcessing}
          style={{ 
            padding: '0.8rem', 
            cursor: isProcessing ? 'not-allowed' : 'pointer', 
            backgroundColor: isProcessing ? '#aab1f0' : '#6772e5', 
            color: 'white', 
            border: 'none', 
            borderRadius: '4px',
            opacity: isProcessing ? 0.7 : 1
          }}
        >
          {isProcessing ? 'Processing...' : 'Manage Payment Method'}
        </button>

        {subscriptionStatus === 'PAST_DUE' && (
          <button 
            onClick={handleManagePayment} 
            disabled={isProcessing}
            style={{ 
              padding: '0.8rem', 
              cursor: isProcessing ? 'not-allowed' : 'pointer', 
              backgroundColor: '#f44336', 
              color: 'white', 
              border: 'none', 
              borderRadius: '4px',
              fontWeight: 'bold'
            }}
          >
            Fix Failed Payment
          </button>
        )}

        {subscriptionStatus === 'EXPIRED' && (
          <Link 
            to="/subscription"
            style={{ 
              padding: '0.8rem', 
              textAlign: 'center',
              backgroundColor: '#6772e5', 
              color: 'white', 
              border: 'none', 
              borderRadius: '4px',
              textDecoration: 'none',
              fontWeight: 'bold'
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
              padding: '0.8rem', 
              cursor: isProcessing ? 'not-allowed' : 'pointer', 
              backgroundColor: '#fff', 
              color: '#f44336', 
              border: '1px solid #f44336', 
              borderRadius: '4px',
              opacity: isProcessing ? 0.7 : 1
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
              padding: '0.8rem', 
              cursor: isProcessing ? 'not-allowed' : 'pointer', 
              backgroundColor: isProcessing ? '#a5d6a7' : '#4caf50', 
              color: 'white', 
              border: 'none', 
              borderRadius: '4px',
              opacity: isProcessing ? 0.7 : 1
            }}
          >
            Resume Subscription
          </button>
        )}
      </div>

      <div style={{ marginTop: '3rem' }}>
        <h2>Invoice History</h2>
        <InvoiceTable />
      </div>

      <div style={{ marginTop: '2rem' }}>
        <Link to="/dashboard">← Back to Dashboard</Link>
      </div>

      {showCancelModal && (
        <div style={{
          position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
          backgroundColor: 'rgba(0,0,0,0.5)', display: 'flex', alignItems: 'center', justifyContent: 'center'
        }}>
          <div style={{ backgroundColor: 'white', padding: '2rem', borderRadius: '8px', maxWidth: '400px', textAlign: 'center' }}>
            <h3>Confirm Cancellation</h3>
            <p>Are you sure you want to cancel your subscription? You will keep your access until the end of the current billing period.</p>
            <div style={{ display: 'flex', gap: '1rem', justifyContent: 'center', marginTop: '1.5rem' }}>
              <button 
                onClick={() => setShowCancelModal(false)}
                style={{ padding: '0.5rem 1rem', cursor: 'pointer' }}
              >
                Go Back
              </button>
              <button 
                onClick={handleCancelSubscription}
                disabled={isProcessing}
                style={{ padding: '0.5rem 1rem', cursor: 'pointer', backgroundColor: '#f44336', color: 'white', border: 'none', borderRadius: '4px' }}
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
