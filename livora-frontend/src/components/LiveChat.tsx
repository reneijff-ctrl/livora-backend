import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../auth/useAuth';
import webSocketService from '../websocket/webSocketService';
import { showToast } from './Toast';

interface ChatMessage {
  id: string;
  senderEmail: string;
  content: string;
  timestamp: string;
  roomType: 'PUBLIC' | 'PREMIUM';
}

const LiveChat: React.FC = () => {
  const { user, hasPremiumAccess } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [room, setRoom] = useState<'PUBLIC' | 'PREMIUM'>('PUBLIC');
  const [onlineCount, setOnlineCount] = useState<number>(0);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const unsubPublic = webSocketService.subscribe('/topic/public', (msg) => {
      if (room === 'PUBLIC') {
        const data = JSON.parse(msg.body);
        if (data.chatMessage) {
          setMessages((prev) => [...prev, data.chatMessage]);
        }
      }
    });

    const unsubPremium = webSocketService.subscribe('/topic/premium', (msg) => {
      if (room === 'PREMIUM') {
        const data = JSON.parse(msg.body);
        if (data.chatMessage) {
          setMessages((prev) => [...prev, data.chatMessage]);
        }
      }
    });

    const unsubPresence = webSocketService.subscribe('/topic/presence', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.onlineCount !== undefined) {
        setOnlineCount(data.onlineCount);
      }
    });

    const unsubErrors = webSocketService.subscribe('/user/queue/errors', (msg) => {
      const data = JSON.parse(msg.body);
      showToast(data.payload?.message || 'WebSocket Error', 'error');
    });

    return () => {
      unsubPublic();
      unsubPremium();
      unsubPresence();
      unsubErrors();
    };
  }, [room]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim()) return;

    if (room === 'PREMIUM' && !hasPremiumAccess()) {
      showToast('Premium access required for this room', 'error');
      return;
    }

    webSocketService.send('/app/chat.send', {
      content: input,
      roomType: room,
    });
    setInput('');
  };

  return (
    <div style={{ 
      border: '1px solid #ddd', 
      borderRadius: '8px', 
      height: '500px', 
      display: 'flex', 
      flexDirection: 'column',
      backgroundColor: '#fff',
      boxShadow: '0 4px 6px rgba(0,0,0,0.05)'
    }}>
      <div style={{ 
        padding: '1rem', 
        borderBottom: '1px solid #eee', 
        display: 'flex', 
        justifyContent: 'space-between',
        alignItems: 'center',
        backgroundColor: '#f8f9fa',
        borderTopLeftRadius: '8px',
        borderTopRightRadius: '8px'
      }}>
        <div style={{ display: 'flex', gap: '1rem' }}>
          <button 
            onClick={() => { setRoom('PUBLIC'); setMessages([]); }}
            style={{ 
              fontWeight: room === 'PUBLIC' ? 'bold' : 'normal',
              color: room === 'PUBLIC' ? '#6772e5' : '#666',
              border: 'none',
              background: 'none',
              cursor: 'pointer'
            }}
          >
            Public Room
          </button>
          <button 
            onClick={() => { setRoom('PREMIUM'); setMessages([]); }}
            style={{ 
              fontWeight: room === 'PREMIUM' ? 'bold' : 'normal',
              color: room === 'PREMIUM' ? '#d4af37' : '#666',
              border: 'none',
              background: 'none',
              cursor: 'pointer',
              opacity: hasPremiumAccess() ? 1 : 0.5
            }}
          >
            Premium Room {!hasPremiumAccess() && '🔒'}
          </button>
        </div>
        <div style={{ fontSize: '0.8rem', color: '#28a745' }}>
          ● {onlineCount} online
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '1rem' }}>
        {messages.map((m, i) => (
          <div key={m.id || i} style={{ marginBottom: '1rem', textAlign: m.senderEmail === user?.email ? 'right' : 'left' }}>
            <div style={{ fontSize: '0.7rem', color: '#888' }}>{m.senderEmail}</div>
            <div style={{ 
              display: 'inline-block', 
              padding: '0.5rem 1rem', 
              borderRadius: '12px', 
              backgroundColor: m.senderEmail === user?.email ? '#6772e5' : '#e9ecef',
              color: m.senderEmail === user?.email ? '#fff' : '#333',
              maxWidth: '80%',
              wordBreak: 'break-word'
            }}>
              {m.content}
            </div>
          </div>
        ))}
        <div ref={messagesEndRef} />
      </div>

      <form onSubmit={handleSend} style={{ padding: '1rem', borderTop: '1px solid #eee', display: 'flex', gap: '0.5rem' }}>
        <input 
          type="text" 
          value={input} 
          onChange={(e) => setInput(e.target.value)} 
          placeholder={room === 'PREMIUM' && !hasPremiumAccess() ? "Unlock premium to chat here..." : "Type a message..."}
          disabled={room === 'PREMIUM' && !hasPremiumAccess()}
          style={{ 
            flex: 1, 
            padding: '0.5rem', 
            borderRadius: '4px', 
            border: '1px solid #ddd' 
          }}
        />
        <button 
          type="submit"
          disabled={room === 'PREMIUM' && !hasPremiumAccess()}
          style={{ 
            padding: '0.5rem 1rem', 
            backgroundColor: '#6772e5', 
            color: 'white', 
            border: 'none', 
            borderRadius: '4px',
            cursor: 'pointer',
            opacity: (room === 'PREMIUM' && !hasPremiumAccess()) ? 0.5 : 1
          }}
        >
          Send
        </button>
      </form>
    </div>
  );
};

export default LiveChat;
