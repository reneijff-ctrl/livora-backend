import React, { useState } from 'react';
import tipService from '../api/tipService';
import { showToast } from './Toast';
import { loadStripe } from '@stripe/stripe-js';

// Use environment variable for Stripe publishable key
const stripePromise = loadStripe(import.meta.env.VITE_STRIPE_PUBLISHABLE_KEY || 'pk_test_mock');

interface TipButtonProps {
  creatorId: string;
  creatorEmail: string;
}

const TipButton: React.FC<TipButtonProps> = ({ creatorId, creatorEmail }) => {
  const [showModal, setShowModal] = useState(false);
  const [amount, setAmount] = useState<number>(5);
  const [message, setMessage] = useState('');
  const [isProcessing, setIsProcessing] = useState(false);

  const handleTip = async () => {
    setIsProcessing(true);
    try {
      const { clientSecret } = await tipService.createTipIntent(creatorId, amount, message);
      
      const stripe = await stripePromise;
      if (!stripe) throw new Error('Stripe failed to load');

      const { error } = await stripe.confirmCardPayment(clientSecret);

      if (error) {
        showToast(error.message || 'Payment failed', 'error');
      } else {
        showToast(`Successfully sent €${amount} tip to ${creatorEmail}!`, 'success');
        setShowModal(false);
        setMessage('');
      }
    } catch (error: any) {
      showToast(error.message || 'Failed to process tip', 'error');
    } finally {
      setIsProcessing(false);
    }
  };

  return (
    <>
      <button 
        onClick={() => setShowModal(true)}
        style={{
          padding: '8px 16px',
          backgroundColor: '#10b981',
          color: 'white',
          border: 'none',
          borderRadius: '4px',
          fontWeight: 'bold',
          cursor: 'pointer'
        }}
      >
        💸 Send Tip
      </button>

      {showModal && (
        <div style={{
          position: 'fixed',
          top: 0,
          left: 0,
          width: '100%',
          height: '100%',
          backgroundColor: 'rgba(0,0,0,0.5)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          zIndex: 1000
        }}>
          <div style={{
            backgroundColor: 'white',
            padding: '2rem',
            borderRadius: '12px',
            maxWidth: '400px',
            width: '90%',
            boxShadow: '0 10px 25px rgba(0,0,0,0.1)'
          }}>
            <h2 style={{ marginTop: 0 }}>Support {creatorEmail}</h2>
            <p style={{ color: '#666' }}>Your tips help creators keep making great content.</p>
            
            <div style={{ display: 'flex', gap: '0.5rem', margin: '1.5rem 0' }}>
              {[2, 5, 10, 20].map(val => (
                <button 
                  key={val}
                  onClick={() => setAmount(val)}
                  style={{
                    flex: 1,
                    padding: '10px',
                    borderRadius: '4px',
                    border: amount === val ? '2px solid #6772e5' : '1px solid #ddd',
                    backgroundColor: amount === val ? '#f8f9ff' : 'white',
                    fontWeight: 'bold',
                    cursor: 'pointer'
                  }}
                >
                  €{val}
                </button>
              ))}
            </div>

            <div style={{ marginBottom: '1rem' }}>
              <label style={{ display: 'block', marginBottom: '0.5rem', fontSize: '0.9rem', color: '#444' }}>Custom Amount (EUR)</label>
              <input 
                type="number" 
                min="1"
                value={amount}
                onChange={(e) => setAmount(Number(e.target.value))}
                style={{ width: '100%', padding: '0.8rem', borderRadius: '4px', border: '1px solid #ddd' }}
              />
            </div>

            <div style={{ marginBottom: '1.5rem' }}>
              <label style={{ display: 'block', marginBottom: '0.5rem', fontSize: '0.9rem', color: '#444' }}>Optional Message</label>
              <textarea 
                value={message}
                onChange={(e) => setMessage(e.target.value)}
                placeholder="Say something nice..."
                style={{ width: '100%', padding: '0.8rem', borderRadius: '4px', border: '1px solid #ddd', minHeight: '80px' }}
              />
            </div>

            <div style={{ display: 'flex', gap: '1rem' }}>
              <button 
                onClick={() => setShowModal(false)}
                style={{ flex: 1, padding: '12px', border: '1px solid #ddd', borderRadius: '4px', background: 'none', cursor: 'pointer' }}
              >
                Cancel
              </button>
              <button 
                onClick={handleTip}
                disabled={isProcessing || amount < 1}
                style={{ 
                  flex: 1, 
                  padding: '12px', 
                  backgroundColor: '#6772e5', 
                  color: 'white', 
                  border: 'none', 
                  borderRadius: '4px', 
                  fontWeight: 'bold',
                  cursor: isProcessing ? 'not-allowed' : 'pointer',
                  opacity: isProcessing ? 0.7 : 1
                }}
              >
                {isProcessing ? 'Processing...' : `Tip €${amount}`}
              </button>
            </div>
            <p style={{ fontSize: '0.75rem', color: '#999', marginTop: '1rem', textAlign: 'center' }}>
              Tips are non-refundable. Thank you for your support!
            </p>
          </div>
        </div>
      )}
    </>
  );
};

export default TipButton;
