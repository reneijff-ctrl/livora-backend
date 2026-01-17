import React from 'react';
import { Link } from 'react-router-dom';
import SEO from '../components/SEO';

const PaymentCancelPage: React.FC = () => {
  return (
    <div style={{ padding: '4rem 2rem', textAlign: 'center', fontFamily: 'sans-serif' }}>
      <SEO title="Payment Canceled" canonical="/payment/cancel" />
      <div style={{ fontSize: '5rem', marginBottom: '1rem' }}>🛒</div>
      <h1>Payment canceled</h1>
      <p style={{ fontSize: '1.2rem', color: '#666' }}>Your account was not charged. You can try again whenever you're ready.</p>
      
      <div style={{ marginTop: '3rem', display: 'flex', gap: '1rem', justifyContent: 'center' }}>
        <Link 
          to="/pricing" 
          style={{ 
            padding: '12px 30px', 
            backgroundColor: '#6772e5', 
            color: 'white', 
            textDecoration: 'none', 
            borderRadius: '6px',
            fontWeight: 'bold',
            fontSize: '1.1rem'
          }}
        >
          View Plans
        </Link>
        <Link 
          to="/dashboard" 
          style={{ 
            padding: '12px 30px', 
            backgroundColor: '#f3f3f3', 
            color: '#333', 
            textDecoration: 'none', 
            borderRadius: '6px',
            fontWeight: 'bold',
            fontSize: '1.1rem'
          }}
        >
          Back to Dashboard
        </Link>
      </div>
    </div>
  );
};

export default PaymentCancelPage;
