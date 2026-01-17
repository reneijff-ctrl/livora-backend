import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../auth/useAuth';
import webSocketService from '../websocket/webSocketService';
import { showToast } from './Toast';
import badgeService, { UserBadge } from '../api/badgeService';

interface ChatMessage {
  userId: string;
  username: string;
  message: string;
  isPaid: boolean;
  amount: number;
  badgeType?: string;
  createdAt: string;
}

interface StreamChatProps {
  streamId: string;
  minChatTokens?: number;
}

const StreamChat: React.FC<StreamChatProps> = ({ streamId, minChatTokens = 0 }) => {
  const { user, tokenBalance, refreshTokenBalance } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isPaidMessage, setIsPaidMessage] = useState(false);
  const [paidAmount, setPaidAmount] = useState<number>(minChatTokens > 0 ? minChatTokens : 10);
  const [myBadges, setMyBadges] = useState<UserBadge[]>([]);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!streamId) return;

    const unsubscribe = webSocketService.subscribe(`/topic/stream/${streamId}/chat`, (msg) => {
      const data = JSON.parse(msg.body);
      setMessages((prev) => [...prev, data]);
    });

    const loadBadges = async () => {
      try {
        const badges = await badgeService.getMyBadges();
        setMyBadges(badges);
      } catch (e) {
        console.error('Failed to load badges', e);
      }
    };
    loadBadges();

    return () => unsubscribe();
  }, [streamId]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim()) return;

    const effectivePaid = isPaidMessage || minChatTokens > 0;
    const amount = effectivePaid ? Math.max(paidAmount, minChatTokens) : 0;

    if (effectivePaid && tokenBalance < amount) {
      showToast('Insufficient token balance', 'error');
      return;
    }

    // Identify highest priority badge
    const badgeType = myBadges.length > 0 ? myBadges[0].badge.name : undefined;

    webSocketService.send(`/app/stream/${streamId}/chat`, {
      message: input,
      isPaid: effectivePaid,
      amount: amount,
      badgeType: badgeType
    });

    setInput('');
    setIsPaidMessage(false);
    if (effectivePaid) {
      setTimeout(refreshTokenBalance, 500);
    }
  };

  return (
    <div style={{ 
      border: '1px solid #ddd', 
      borderRadius: '8px', 
      height: '100%',
      minHeight: '400px',
      maxHeight: '100vh',
      display: 'flex', 
      flexDirection: 'column',
      backgroundColor: '#fff',
      boxShadow: '0 4px 10px rgba(0,0,0,0.1)',
      position: 'relative'
    }}>
      <div style={{ padding: '0.75rem', borderBottom: '1px solid #eee', backgroundColor: '#f8f9fa', display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
        <h4 style={{ margin: 0 }}>Live Chat</h4>
        {minChatTokens > 0 && <span style={{ fontSize: '0.7rem', color: '#6772e5' }}>Min: {minChatTokens} 🪙</span>}
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '1rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        {messages.map((m, i) => (
          <div key={i} style={{ 
            padding: m.isPaid ? '0.5rem' : '0.2rem', 
            borderRadius: '4px',
            backgroundColor: m.isPaid ? `rgba(103, 114, 229, ${Math.min(0.3, m.amount / 1000)})` : 'transparent',
            border: m.isPaid ? '1px solid #6772e5' : 'none'
          }}>
            <span style={{ fontSize: '0.75rem', fontWeight: 'bold', color: '#555' }}>
              {m.badgeType && <span style={{ marginRight: '4px' }}>[{m.badgeType}]</span>}
              {m.username}:
            </span>
            <span style={{ marginLeft: '0.5rem', wordBreak: 'break-word', fontWeight: m.isPaid ? 'bold' : 'normal' }}>
              {m.message}
            </span>
            {m.isPaid && <span style={{ float: 'right', fontSize: '0.7rem', color: '#6772e5' }}>{m.amount} 🪙</span>}
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <form 
        onSubmit={handleSend} 
        style={{ 
          padding: '1rem', 
          borderTop: '1px solid #eee',
          backgroundColor: '#fff',
          position: 'sticky',
          bottom: 0,
          zIndex: 10
        }}
      >
        <div style={{ display: 'flex', gap: '0.5rem', marginBottom: '0.5rem' }}>
          <input 
            type="text" 
            value={input} 
            onChange={(e) => setInput(e.target.value)} 
            placeholder="Say something..."
            style={{ 
              flex: 1, 
              padding: '0.8rem', 
              borderRadius: '8px', 
              border: '1px solid #ddd',
              fontSize: '16px', // Prevents iOS zoom on focus
              minHeight: '44px' // Tap target size
            }}
          />
          <button 
            type="submit" 
            style={{ 
              padding: '0 1.5rem', 
              backgroundColor: '#6772e5', 
              color: 'white', 
              border: 'none', 
              borderRadius: '8px', 
              cursor: 'pointer',
              fontWeight: 'bold',
              minHeight: '44px'
            }}
          >
            Send
          </button>
        </div>
        
        <div style={{ display: 'flex', alignItems: 'center', gap: '1rem', fontSize: '0.8rem', flexWrap: 'wrap' }}>
          <label style={{ display: 'flex', alignItems: 'center', gap: '8px', cursor: 'pointer', minHeight: '32px' }}>
            <input 
              type="checkbox" 
              checked={isPaidMessage || minChatTokens > 0} 
              disabled={minChatTokens > 0}
              onChange={(e) => setIsPaidMessage(e.target.checked)} 
              style={{ width: '18px', height: '18px' }}
            />
            Highlight (Paid)
          </label>
          {(isPaidMessage || minChatTokens > 0) && (
            <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
              <input 
                type="number" 
                value={paidAmount} 
                onChange={(e) => setPaidAmount(Number(e.target.value))}
                min={minChatTokens}
                style={{ width: '80px', padding: '8px', borderRadius: '4px', border: '1px solid #ddd' }}
              />
              <span>Tokens</span>
            </div>
          )}
        </div>
      </form>
    </div>
  );
};

export default StreamChat;
