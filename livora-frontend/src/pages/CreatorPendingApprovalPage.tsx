import React from 'react';
import { Link } from 'react-router-dom';

const CreatorPendingApprovalPage: React.FC = () => {
  return (
    <div style={{
      display: 'flex',
      minHeight: '60vh',
      alignItems: 'center',
      justifyContent: 'center',
      padding: '24px'
    }}>
      <div style={{
        maxWidth: 640,
        width: '100%',
        background: 'var(--card-bg, #111)',
        border: '1px solid var(--border, #222)',
        borderRadius: 12,
        padding: 24,
        textAlign: 'center'
      }}>
        <div style={{ marginBottom: 16 }}>
          <div style={{
            width: 56,
            height: 56,
            margin: '0 auto 12px',
            borderRadius: '50%',
            background: 'linear-gradient(135deg, #6D28D9 0%, #9333EA 100%)'
          }} />
          <h1 style={{ margin: 0, fontSize: 24 }}>Waiting for approval</h1>
        </div>
        <p style={{ opacity: 0.8, margin: '0 0 16px' }}>
          Your creator profile has been submitted and is currently under review.
          You will receive a notification once its approved. In the meantime,
          you can review your profile details.
        </p>
        <div style={{ display: 'flex', gap: 12, justifyContent: 'center' }}>
          <Link to="/creator/profile" style={{
            padding: '10px 14px',
            borderRadius: 8,
            background: 'var(--btn-bg, #2563EB)',
            color: 'white',
            textDecoration: 'none',
            fontWeight: 600
          }}>Edit profile</Link>
          <Link to="/" style={{
            padding: '10px 14px',
            borderRadius: 8,
            border: '1px solid var(--border, #222)',
            color: 'inherit',
            textDecoration: 'none',
            fontWeight: 600
          }}>Go home</Link>
        </div>
      </div>
    </div>
  );
};

export default CreatorPendingApprovalPage;
