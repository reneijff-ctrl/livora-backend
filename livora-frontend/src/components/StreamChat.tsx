import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../auth/useAuth';
import webSocketService from '../websocket/webSocketService';
import { showToast } from './Toast';
import badgeService, { UserBadge } from '../api/badgeService';

interface ChatMessage {
  id?: string;
  userId?: string;
  username?: string;
  senderUsername?: string;
  message?: string;
  content?: string;
  isPaid?: boolean;
  amount?: number;
  badgeType?: string;
  createdAt?: string;
  timestamp?: string;
  system?: boolean;
  moderated?: boolean;
  highlight?: string;
  type?: string;
}

interface StreamChatProps {
  streamId: string;
  creatorUserId: string | number;
  minChatTokens?: number;
}

const GIF_URL_REGEX = /https?:\/\/\S+\.gif(?:\?\S+)?/i;

const StreamChat: React.FC<StreamChatProps> = ({ streamId, creatorUserId, minChatTokens = 0 }) => {
  const { user, tokenBalance, refreshTokenBalance } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isPaidMessage, setIsPaidMessage] = useState(false);
  const [paidAmount, setPaidAmount] = useState<number>(minChatTokens > 0 ? minChatTokens : 10);
  const [myBadges, setMyBadges] = useState<UserBadge[]>([]);
  const [isMuted, setIsMuted] = useState(false);
  const scrollRef = useRef<HTMLDivElement>(null);
  const [showNewMessages, setShowNewMessages] = useState(false);
  const isAtBottomRef = useRef(true);

  useEffect(() => {
    if (!streamId) return;

    const handleIncomingMessage = (msg: any) => {
      const incoming = JSON.parse(msg.body);
      
      if (
        incoming.type === 'CHAT' ||
        incoming.type === 'TIP' ||
        incoming.type === 'BOT' ||
        incoming.type === 'SYSTEM' ||
        incoming.type === 'SUPER_TIP'
      ) {
        if (incoming.system || incoming.type === 'SYSTEM') {
          const content = incoming.content || incoming.message;
          if (content) {
            showToast(content, 'info');
            if (content.includes('User muted') && user && content.includes(user.username)) {
              setIsMuted(true);
            }
          }
        }
        setMessages((prev) => [...prev, incoming]);
      } else {
        // Support legacy/wrapped messages
        const chatMsg = incoming.chatMessage || incoming;
        if (chatMsg.system || chatMsg.systemMessage) {
          const content = chatMsg.content || chatMsg.message;
          if (content) {
            showToast(content, 'info');
            if (content.includes('User muted') && user && content.includes(user.username)) {
              setIsMuted(true);
            }
          }
        }
        setMessages((prev) => [...prev, chatMsg]);
      }
    };

    const unsubscribe = webSocketService.subscribe(`/topic/chat/${creatorUserId}`, handleIncomingMessage);

    const unsubscribePrivate = webSocketService.subscribe('/user/queue/chat', (msg) => {
      const data = JSON.parse(msg.body);
      // For stream chat, roomId might be stream-${streamId} or just streamId
      const msgRoomId = data.roomId || (data.chatMessage && data.chatMessage.roomId);
      if (String(msgRoomId) === String(streamId) || String(msgRoomId) === `stream-${streamId}`) {
        handleIncomingMessage(msg);
      }
    });

    const unsubNotifications = webSocketService.subscribe('/user/queue/notifications', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.type === 'DISCONNECT') {
        showToast(data.payload?.reason || 'Disconnected', 'error');
      }
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

    return () => {
      unsubscribe();
      unsubscribePrivate();
      unsubNotifications();
    };
  }, [streamId]);

  useEffect(() => {
    if (scrollRef.current) {
      if (isAtBottomRef.current) {
        scrollRef.current.scrollTo({
          top: scrollRef.current.scrollHeight,
          behavior: 'smooth'
        });
      } else if (messages.length > 0) {
        setShowNewMessages(true);
      }
    }
  }, [messages]);

  const handleScroll = () => {
    if (scrollRef.current) {
      const { scrollTop, scrollHeight, clientHeight } = scrollRef.current;
      const atBottom = scrollHeight - scrollTop - clientHeight < 100;
      isAtBottomRef.current = atBottom;
      if (atBottom) {
        setShowNewMessages(false);
      }
    }
  };

  const scrollToBottom = () => {
    if (scrollRef.current) {
      scrollRef.current.scrollTo({
        top: scrollRef.current.scrollHeight,
        behavior: 'smooth'
      });
      setShowNewMessages(false);
      isAtBottomRef.current = true;
    }
  };

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

    webSocketService.send('/app/chat.send', {
      creatorUserId: creatorUserId,
      content: input,
      type: 'CHAT',
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

      <div 
        ref={scrollRef} 
        onScroll={handleScroll}
        style={{ flex: 1, overflowY: 'auto', padding: '1rem', display: 'flex', flexDirection: 'column', gap: '0.5rem', position: 'relative' }}
      >
        {messages.map((m, i) => (
          <div key={i} className={`chat-bubble ${m.isPaid || m.type === 'TIP' ? 'chat-tip neon-tip' : ''} ${m.type === 'BOT' ? 'chat-bot chat-bot-scanline' : ''} ${m.highlight === 'POSITIVE' ? 'border-emerald-500/30 bg-emerald-500/5 shadow-[0_0_15px_rgba(16,185,129,0.1)]' : ''} text-white/90`} style={{ 
            marginBottom: '4px',
            alignSelf: 'flex-start',
            width: 'fit-content',
            maxWidth: '90%'
          }}>
            <span className={`chat-username ${m.type === 'BOT' ? 'chat-username-bot' : ''}`} style={{ fontSize: '0.75rem', fontWeight: 'bold', marginRight: '8px' }}>
              {m.type === 'BOT' && <span className="mr-1.5 animate-bounce inline-block">🤖</span>}
              {m.badgeType && <span style={{ marginRight: '4px' }}>[{m.badgeType}]</span>}
              {m.senderUsername || m.username}:
            </span>
            <span className={m.type === 'BOT' ? 'animate-typewriter-subtle' : ''} style={{ marginLeft: '0.5rem', wordBreak: 'break-word', fontWeight: m.isPaid || m.type === 'TIP' ? 'bold' : 'normal', display: m.type === 'BOT' ? 'inline-block' : 'inline' }}>
              {m.type === 'TIP' ? (
                <span>💎 Tipped <span className="neon-gold font-black">{m.amount} tokens!</span></span>
              ) : (
                GIF_URL_REGEX.test(m.content || m.message || '') ? (
                  <div className="animate-in fade-in duration-500" style={{ marginTop: '0.5rem', maxWidth: '200px' }}>
                    <img 
                      src={(m.content || m.message || '').match(GIF_URL_REGEX)![0]} 
                      alt="GIF" 
                      style={{ width: '100%', maxHeight: '150px', objectFit: 'cover', borderRadius: '12px' }} 
                      loading="lazy"
                    />
                  </div>
                ) : (
                  m.content || m.message
                )
              )}
            </span>
            {(m.isPaid || m.type === 'TIP') && <span style={{ float: 'right', fontSize: '0.7rem', color: '#6772e5', marginLeft: '8px' }}>{m.amount} 🪙</span>}
          </div>
        ))}
      </div>

      {showNewMessages && (
        <button 
          onClick={scrollToBottom}
          style={{
            position: 'absolute',
            bottom: '120px',
            left: '50%',
            transform: 'translateX(-50%)',
            backgroundColor: '#6772e5',
            color: 'white',
            border: 'none',
            borderRadius: '20px',
            padding: '0.4rem 0.8rem',
            fontSize: '0.75rem',
            fontWeight: 'bold',
            cursor: 'pointer',
            boxShadow: '0 4px 12px rgba(0,0,0,0.15)',
            zIndex: 100,
            animation: 'fadeIn 0.3s ease-out'
          }}
        >
          New messages ↓
        </button>
      )}

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
            placeholder={isMuted ? "You are muted" : "Say something..."}
            disabled={isMuted}
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
            disabled={isMuted}
            style={{ 
              padding: '0 1.5rem', 
              backgroundColor: '#6772e5', 
              color: 'white', 
              border: 'none', 
              borderRadius: '8px', 
              cursor: isMuted ? 'not-allowed' : 'pointer',
              fontWeight: 'bold',
              minHeight: '44px',
              opacity: isMuted ? 0.5 : 1
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
