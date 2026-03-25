import React from 'react';
import { useWallet } from '../hooks/useWallet';

interface TokenBalanceProps {
  className?: string;
  showIcon?: boolean;
}

const TokenBalance: React.FC<TokenBalanceProps> = ({ className, showIcon = true }) => {
  const { balance } = useWallet();

  return (
    <div className={`token-balance-display ${className}`} style={styles.container}>
      {showIcon && <span style={styles.icon}>🪙</span>}
      <span style={styles.amount}>{(balance ?? 0).toLocaleString()}</span>
    </div>
  );
};

const styles = {
  container: {
    display: 'inline-flex',
    alignItems: 'center',
    gap: '4px',
    fontWeight: 'bold',
    color: '#ffd700',
    backgroundColor: 'rgba(255, 215, 0, 0.1)',
    padding: '4px 10px',
    borderRadius: '20px',
    border: '1px solid rgba(255, 215, 0, 0.3)',
  },
  icon: {
    fontSize: '1.1rem',
  },
  amount: {
    fontSize: '0.9rem',
  }
};

export default TokenBalance;
