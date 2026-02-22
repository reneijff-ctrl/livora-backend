import React from 'react';
import Logo from '@/components/Logo';

interface TipButtonProps {
  userId: string;
  creatorEmail: string;
}

// Temporarily disabled TipButton (Stripe disabled). Keep props for compatibility.
const TipButton: React.FC<TipButtonProps> = () => {
  return (
    <button
      type="button"
      disabled
      style={{
        padding: '8px 16px',
        backgroundColor: '#9ca3af', // gray
        color: 'white',
        border: 'none',
        borderRadius: '6px',
        fontWeight: 'bold',
        cursor: 'not-allowed',
        opacity: 0.85,
        display: 'inline-flex',
        alignItems: 'center',
        gap: '8px'
      }}
      title="Coming soon"
      aria-label="Coming soon"
    >
      <Logo 
        size={16} 
        glow={false} 
        opacity={0.6}
      />
      <span>Coming soon</span>
    </button>
  );
};

export default TipButton;
