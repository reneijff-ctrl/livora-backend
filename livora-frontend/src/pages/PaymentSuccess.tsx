import React, { useEffect, useState } from 'react';
import { useSearchParams, useNavigate } from 'react-router-dom';
import { verifyPayment } from '../api/paymentApi';
import { showToast } from '../components/Toast';

const PaymentSuccess: React.FC = () => {
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const sessionId = searchParams.get('session_id');
  const [status, setStatus] = useState<'LOADING' | 'SUCCESS' | 'FAILURE'>('LOADING');
  const [amount, setAmount] = useState<number | null>(null);

  useEffect(() => {
    if (sessionId) {
      verifyPayment(sessionId)
        .then((data) => {
          if (data.status === 'PAID') {
            setStatus('SUCCESS');
            setAmount(data.amount);
          } else {
            setStatus('FAILURE');
            showToast('Payment not completed. Please try again or contact support.', 'error');
          }
        })
        .catch((error: any) => {
          console.error('Verification error:', error);
          const message = error?.response?.data?.message || 'Failed to verify payment';
          showToast(message, 'error');
          setStatus('FAILURE');
        });
    } else {
      setStatus('FAILURE');
    }
  }, [sessionId]);

  const containerStyle: React.CSSProperties = {
    display: 'flex',
    flexDirection: 'column',
    alignItems: 'center',
    justifyContent: 'center',
    height: '70vh',
    textAlign: 'center',
    padding: '2rem',
    fontFamily: 'system-ui, -apple-system, sans-serif'
  };

  if (status === 'LOADING') {
    return (
      <div style={containerStyle}>
        <h2>Verifying your payment...</h2>
        <div className="spinner" style={{
          width: '40px',
          height: '40px',
          border: '4px solid #f3f3f3',
          borderTop: '4px solid #3498db',
          borderRadius: '50%',
          animation: 'spin 1s linear infinite',
          marginTop: '1rem'
        }} />
        <style>{`
          @keyframes spin {
            0% { transform: rotate(0deg); }
            100% { transform: rotate(360deg); }
          }
        `}</style>
      </div>
    );
  }

  if (status === 'FAILURE') {
    return (
      <div style={containerStyle}>
        <div style={{ fontSize: '4rem', marginBottom: '1rem' }}>❌</div>
        <h1 style={{ color: '#f87171' }}>Payment could not be verified</h1>
        <p style={{ color: '#71717A', maxWidth: '400px', lineHeight: '1.6' }}>
          We couldn't confirm your transaction with Stripe. If you've been charged, please contact our support team.
        </p>
        <button
          onClick={() => navigate('/explore')}
          style={{
            marginTop: '2rem',
            padding: '0.875rem 2rem',
            backgroundColor: '#6366f1',
            color: 'white',
            border: 'none',
            borderRadius: '12px',
            cursor: 'pointer',
            fontSize: '1rem',
            fontWeight: 'bold',
            boxShadow: '0 4px 12px rgba(99, 102, 241, 0.3)'
          }}
        >
          Return to Explore
        </button>
      </div>
    );
  }

  return (
    <div style={containerStyle}>
      <div style={{ fontSize: '5rem', marginBottom: '1rem' }}>🎉</div>
      <h1 style={{ color: '#10b981' }}>Payment successful 🎉</h1>
      {amount !== null && (
        <p style={{ fontSize: '1.25rem', margin: '1rem 0', color: '#F4F4F5' }}>
          Amount paid: <strong>€{(amount / 100).toFixed(2)}</strong>
        </p>
      )}
      <p style={{ color: '#71717A', marginBottom: '2rem' }}>
        Thank you for your support! Your payment has been processed successfully.
      </p>
      <div style={{ display: 'flex', gap: '1rem' }}>
        <button
          onClick={() => navigate('/dashboard')}
          style={{
            padding: '0.875rem 2rem',
            backgroundColor: '#10b981',
            color: 'white',
            border: 'none',
            borderRadius: '12px',
            cursor: 'pointer',
            fontSize: '1rem',
            fontWeight: '800',
            boxShadow: '0 4px 12px rgba(16, 185, 129, 0.3)'
          }}
        >
          Go to Viewer Hub
        </button>
        <button
          onClick={() => navigate('/explore')}
          style={{
            padding: '0.875rem 2rem',
            backgroundColor: 'rgba(255, 255, 255, 0.05)',
            color: '#F4F4F5',
            border: '1px solid rgba(255, 255, 255, 0.1)',
            borderRadius: '12px',
            cursor: 'pointer',
            fontSize: '1rem',
            fontWeight: '700'
          }}
        >
          Keep Exploring
        </button>
      </div>
    </div>
  );
};

export default PaymentSuccess;
