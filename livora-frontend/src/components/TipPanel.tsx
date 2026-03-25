import React, { useState } from 'react';
import tipService from '../api/tipService';
import { showToast } from './Toast';
import { useWallet } from '../hooks/useWallet';
import TokenBalance from './TokenBalance';

interface TipPanelProps {
  roomId: string;
  userId: number;
}

const TipPanel: React.FC<TipPanelProps> = ({ roomId }: TipPanelProps) => {
  const { refreshBalance, hasSufficientBalance } = useWallet();
  const [amount, setAmount] = useState<number>(10);
  const [message, setMessage] = useState('');
  const [isSending, setIsSending] = useState(false);
  const [lastCreatorBalance, setLastCreatorBalance] = useState<number | null>(null);

  const handleSendTip = async () => {
    if (amount <= 0) {
      showToast('Please enter a valid amount', 'error');
      return;
    }

    if (!hasSufficientBalance(amount)) {
      showToast('Insufficient token balance', 'error');
      return;
    }

    setIsSending(true);
    try {
      const clientRequestId = crypto.randomUUID();
      const result = await tipService.sendTokenTip(roomId, amount, message, clientRequestId);
      
      showToast(result.message || `Success! Sent ${amount} tokens.`, 'success');
      setAmount(10);
      setMessage('');
      if (result.creatorBalance !== undefined) {
        setLastCreatorBalance(result.creatorBalance);
      }
      
      // Auto-refresh balance after spending
      refreshBalance();
    } catch (error: any) {
      const errorMsg = error.response?.data?.message || 'Failed to send tip';
      showToast(errorMsg, 'error');
    } finally {
      setIsSending(false);
    }
  };

  const tipOptions = [10, 50, 100, 500];

  return (
    <div style={styles.panel}>
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1rem' }}>
        <h3 style={styles.title}>Support Creator</h3>
        <TokenBalance />
      </div>
      
      {lastCreatorBalance !== null && (
        <div style={styles.balanceInfo}>
          Creator Tokens: {(lastCreatorBalance ?? 0).toLocaleString()}
        </div>
      )}

      <div style={styles.optionsGrid}>
        {tipOptions.map((opt) => (
          <button
            key={opt}
            onClick={() => setAmount(opt)}
            style={{
              ...styles.optionButton,
              backgroundColor: amount === opt ? '#6772e5' : '#2d2d35',
              color: amount === opt ? 'white' : '#a0a0ab',
              border: amount === opt ? '1px solid #6772e5' : '1px solid #3f3f46',
            }}
          >
            {opt}
          </button>
        ))}
      </div>

      <div style={styles.inputGroup}>
        <input
          type="number"
          value={amount}
          onChange={(e) => setAmount(parseInt(e.target.value) || 0)}
          style={styles.input}
          min="1"
          placeholder="Amount"
        />
      </div>

      <div style={styles.inputGroup}>
        <textarea
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          placeholder="Optional message..."
          style={styles.textarea}
        />
      </div>

      <button
        onClick={handleSendTip}
        disabled={isSending || amount <= 0}
        style={{
          ...styles.sendButton,
          opacity: isSending || amount <= 0 ? 0.7 : 1,
          cursor: isSending || amount <= 0 ? 'not-allowed' : 'pointer',
        }}
      >
        {isSending ? 'Sending...' : `Tip ${amount} Tokens`}
      </button>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  panel: {
    backgroundColor: '#1c1c21',
    padding: '1.25rem',
    borderRadius: '12px',
    border: '1px solid #2d2d35',
    marginTop: '1rem',
    color: '#fff',
    fontFamily: 'system-ui, -apple-system, sans-serif',
  },
  title: {
    margin: '0 0 1rem 0',
    fontSize: '0.9rem',
    fontWeight: '700',
    color: '#ffffff',
    textTransform: 'uppercase',
    letterSpacing: '0.05em',
  },
  balanceInfo: {
    fontSize: '0.8rem',
    color: '#10b981',
    marginBottom: '1rem',
    fontWeight: '600',
    backgroundColor: 'rgba(16, 185, 129, 0.1)',
    padding: '0.5rem',
    borderRadius: '6px',
    textAlign: 'center',
  },
  optionsGrid: {
    display: 'grid',
    gridTemplateColumns: 'repeat(4, 1fr)',
    gap: '0.5rem',
    marginBottom: '1rem',
  },
  optionButton: {
    padding: '0.5rem',
    borderRadius: '6px',
    fontWeight: '600',
    fontSize: '0.8rem',
    cursor: 'pointer',
    transition: 'all 0.2s',
  },
  inputGroup: {
    marginBottom: '0.75rem',
  },
  input: {
    width: '100%',
    padding: '0.6rem 0.8rem',
    backgroundColor: '#09090b',
    border: '1px solid #2d2d35',
    borderRadius: '8px',
    color: '#ffffff',
    fontSize: '0.9rem',
    boxSizing: 'border-box',
    outline: 'none',
  },
  textarea: {
    width: '100%',
    padding: '0.6rem 0.8rem',
    backgroundColor: '#09090b',
    border: '1px solid #2d2d35',
    borderRadius: '8px',
    color: '#ffffff',
    fontSize: '0.85rem',
    minHeight: '50px',
    maxHeight: '100px',
    resize: 'vertical',
    boxSizing: 'border-box',
    outline: 'none',
  },
  sendButton: {
    width: '100%',
    padding: '0.75rem',
    backgroundColor: '#10b981',
    color: 'white',
    border: 'none',
    borderRadius: '8px',
    fontWeight: '700',
    fontSize: '0.9rem',
    transition: 'background-color 0.2s',
    textTransform: 'uppercase',
  },
};

export default TipPanel;
