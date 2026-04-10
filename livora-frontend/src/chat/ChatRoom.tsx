import React, { useState, useEffect, useRef } from 'react';
import { useWs } from '../ws/WsContext';
import { chatService } from './chatService';
import { useAuth } from '../auth/useAuth';

interface ChatMessage {
  id?: string;
  senderId?: string;
  senderEmail?: string;
  senderUsername?: string;
  content: string;
  timestamp: string;
  system?: boolean;
  moderated?: boolean;
  highlight?: string;
  type?: string;
  amount?: number;
}

interface ChatRoomProps {
  roomId: string;
}

const GIF_URL_REGEX = /https?:\/\/\S+\.gif(?:\?\S+)?/i;

const ChatRoom: React.FC<ChatRoomProps> = ({ roomId }) => {
  const { user } = useAuth();
  const { subscribe, send } = useWs();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [isMuted, setIsMuted] = useState(false);
  const [showNewMessages, setShowNewMessages] = useState(false);
  const isAtBottomRef = useRef(true);
  const messageListRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!user) return;

    // Clear messages when room changes
    setMessages([]);

    console.log(`Subscribing to chat room: ${roomId}`);
    const handleIncomingMessage = (message: any) => {
      try {
        const incoming = JSON.parse(message.body);

        // Support batched messages from server
        if (incoming.type === 'CHAT_BATCH' && Array.isArray(incoming.messages)) {
          for (const msg of incoming.messages) {
            handleIncomingMessage({ body: JSON.stringify(msg) });
          }
          return;
        }
        
        // Normalize ALL messages for consistency and unique keys
        const rawMsg = incoming.chatMessage || incoming;
        
        if (
          incoming.type === 'CHAT' ||
          incoming.type === 'TIP' ||
          incoming.type === 'BOT' ||
          incoming.type === 'SYSTEM' ||
          incoming.type === 'SUPER_TIP' ||
          rawMsg.content || 
          rawMsg.message
        ) {
          const normalized: ChatMessage = {
            id: String(rawMsg.messageId || rawMsg.id || rawMsg.clientRequestId || `msg-${Date.now()}-${Math.random().toString(36).substring(2, 9)}`),
            type: incoming.type || rawMsg.type || 'CHAT',
            senderId: String(rawMsg.senderId || '0'),
            senderEmail: rawMsg.senderEmail || rawMsg.email,
            senderUsername: rawMsg.senderUsername || rawMsg.username || 'User',
            content: rawMsg.content || rawMsg.message || '',
            timestamp: rawMsg.timestamp || new Date().toISOString(),
            system: Boolean(rawMsg.system || rawMsg.systemMessage || incoming.type === 'SYSTEM'),
            highlight: rawMsg.highlight,
            amount: rawMsg.amount
          };

          if (normalized.system) {
            const content = normalized.content;
            if (content) {
              import('../components/Toast').then(({ showToast }) => {
                showToast(content, 'info');
              });
              if (content.includes('User muted') && user && content.includes(user.username)) {
                setIsMuted(true);
              }
            }
          }
          
          setMessages((prev) => [...prev, normalized]);
        }
      } catch (e) {
        console.error('Chat: Failed to parse incoming message', e);
      }
    };

    const unsubChat = subscribe(`/exchange/amq.topic/chat.${roomId}`, handleIncomingMessage);

    const unsubPrivate = subscribe('/user/queue/chat', (message) => {
      const data = JSON.parse(message.body);
      const msgRoomId = data.roomId || (data.chatMessage && data.chatMessage.roomId);
      if (String(msgRoomId) === String(roomId)) {
        handleIncomingMessage(message);
      }
    });

    const unsubNotifications = subscribe('/user/queue/notifications', (message) => {
      const data = JSON.parse(message.body);
      if (data.type === 'DISCONNECT') {
        import('../components/Toast').then(({ showToast }) => {
          showToast(data.payload?.reason || 'Disconnected', 'error');
        });
      }
    });

    return () => {
      if (typeof unsubChat === 'function') unsubChat();
      if (typeof unsubPrivate === 'function') unsubPrivate();
      if (typeof unsubNotifications === 'function') unsubNotifications();
    };
  }, [roomId, subscribe, user]);

  useEffect(() => {
    if (messageListRef.current) {
      if (isAtBottomRef.current) {
        messageListRef.current.scrollTo({
          top: messageListRef.current.scrollHeight,
          behavior: 'smooth'
        });
      } else if (messages.length > 0) {
        setShowNewMessages(true);
      }
    }
  }, [messages]);

  const handleScroll = () => {
    if (messageListRef.current) {
      const { scrollTop, scrollHeight, clientHeight } = messageListRef.current;
      const atBottom = scrollHeight - scrollTop - clientHeight < 100;
      isAtBottomRef.current = atBottom;
      if (atBottom) {
        setShowNewMessages(false);
      }
    }
  };

  const scrollToBottom = () => {
    if (messageListRef.current) {
      messageListRef.current.scrollTo({
        top: messageListRef.current.scrollHeight,
        behavior: 'smooth'
      });
      setShowNewMessages(false);
      isAtBottomRef.current = true;
    }
  };

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (input.trim() && user) {
      chatService.sendMessage(roomId, user.id, user.role, input.trim(), undefined, send);
      setInput('');
    }
  };

  const isInputEmpty = !input.trim();

  return (
    <div style={styles.container}>
      <div style={styles.header}>
        Room: {roomId}
      </div>
      <div 
        ref={messageListRef} 
        onScroll={handleScroll}
        style={styles.messageList}
      >
        {messages.length === 0 ? (
          <div style={styles.emptyState}>No messages yet. Say hi!</div>
        ) : (
          messages.map((msg, index) => {
            const isMe = String(msg.senderId) === String(user?.id);
            const isBot = msg.type === 'BOT';
            return (
              <div 
                key={index} 
                className={`chat-bubble ${isBot ? 'chat-bot chat-bot-scanline' : ''} ${msg.type === 'TIP' ? 'chat-tip neon-tip' : ''} ${isBot ? 'animate-in fade-in slide-in-from-bottom-2' : ''}`}
                style={{
                  ...styles.messageItem,
                  alignSelf: isMe ? 'flex-end' : 'flex-start',
                  backgroundColor: isBot || msg.type === 'TIP' ? undefined : (msg.highlight === 'POSITIVE' ? '#d1fae5' : (isMe ? '#6772e5' : '#f3f4f6')),
                  color: isBot || msg.type === 'TIP' ? '#ffffff' : ((msg.highlight === 'POSITIVE' && !isMe) ? '#065f46' : (isMe ? '#ffffff' : '#111827')),
                  border: isBot || msg.type === 'TIP' ? undefined : (msg.highlight === 'POSITIVE' ? '1px solid #10b981' : 'none'),
                  borderBottomRightRadius: isMe ? '2px' : '12px',
                  borderBottomLeftRadius: isMe ? '12px' : '2px',
                }}
              >
                {!isMe && (
                  <div className={isBot ? 'chat-username chat-username-bot' : ''} style={{ ...styles.sender, color: isBot ? undefined : '#6b7280' }}>
                    {isBot && <span className="mr-1.5 animate-bounce inline-block">🤖</span>}
                    {msg.senderUsername}
                  </div>
                )}
                <div className={isBot ? 'animate-typewriter-subtle' : ''} style={{ ...styles.content, display: isBot ? 'inline-block' : 'block' }}>
                  {msg.type === 'TIP' ? (
                    <span>💎 Tipped <span className="neon-gold font-black">{msg.amount} tokens!</span></span>
                  ) : (
                    GIF_URL_REGEX.test(msg.content) ? (
                      <div className="animate-in fade-in duration-500" style={{ marginTop: '0.5rem', maxWidth: '200px' }}>
                        <img 
                          src={msg.content.match(GIF_URL_REGEX)![0]} 
                          alt="GIF" 
                          style={{ width: '100%', maxHeight: '150px', objectFit: 'cover', borderRadius: '12px' }} 
                          loading="lazy"
                        />
                      </div>
                    ) : (
                      msg.content
                    )
                  )}
                </div>
                <div style={{
                  ...styles.timestamp,
                  color: isMe ? '#e0e7ff' : '#6b7280'
                }}>
                  {new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}
                </div>
              </div>
            );
          })
        )}
      </div>
      
      {showNewMessages && (
        <button 
          onClick={scrollToBottom}
          style={styles.newMessageButton}
        >
          New messages ↓
        </button>
      )}
      <form onSubmit={handleSend} style={styles.inputArea}>
        <input
          type="text"
          value={input}
          onChange={(e) => setInput(e.target.value)}
          placeholder={isMuted ? "You are muted" : (user ? "Type a message..." : "Please login to chat")}
          style={styles.input}
          disabled={!user || isMuted}
        />
        <button 
          type="submit" 
          style={{
            ...styles.sendButton,
            opacity: (isInputEmpty || !user || isMuted) ? 0.5 : 1,
            cursor: (isInputEmpty || !user || isMuted) ? 'not-allowed' : 'pointer'
          }} 
          disabled={isInputEmpty || !user || isMuted}
        >
          Send
        </button>
      </form>
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  container: {
    display: 'flex',
    flexDirection: 'column',
    height: '400px',
    width: '100%',
    maxWidth: '400px',
    border: '1px solid #e5e7eb',
    borderRadius: '8px',
    backgroundColor: '#ffffff',
    overflow: 'hidden',
    boxShadow: '0 4px 6px -1px rgba(0, 0, 0, 0.1)',
    position: 'relative',
  },
  header: {
    padding: '0.75rem',
    backgroundColor: '#f9fafb',
    borderBottom: '1px solid #e5e7eb',
    fontWeight: '600',
    color: '#374151',
  },
  messageList: {
    flex: 1,
    overflowY: 'auto',
    padding: '1rem',
    display: 'flex',
    flexDirection: 'column',
    gap: '0.75rem',
  },
  emptyState: {
    textAlign: 'center',
    color: '#9ca3af',
    marginTop: '2rem',
    fontSize: '0.875rem',
  },
  messageItem: {
    maxWidth: '80%',
    padding: '0.5rem 0.75rem',
    borderRadius: '12px',
    position: 'relative',
  },
  sender: {
    fontSize: '0.75rem',
    fontWeight: '700',
    marginBottom: '0.125rem',
    opacity: 0.8,
  },
  content: {
    fontSize: '0.9375rem',
    lineHeight: '1.25rem',
    wordBreak: 'break-word',
  },
  timestamp: {
    fontSize: '0.625rem',
    marginTop: '0.25rem',
    textAlign: 'right',
  },
  inputArea: {
    display: 'flex',
    padding: '0.75rem',
    borderTop: '1px solid #e5e7eb',
    gap: '0.5rem',
  },
  input: {
    flex: 1,
    padding: '0.5rem 0.75rem',
    borderRadius: '6px',
    border: '1px solid #d1d5db',
    outline: 'none',
    fontSize: '0.875rem',
  },
  sendButton: {
    padding: '0.5rem 1rem',
    backgroundColor: '#6772e5',
    color: '#ffffff',
    border: 'none',
    borderRadius: '6px',
    fontWeight: '600',
    cursor: 'pointer',
    fontSize: '0.875rem',
    transition: 'opacity 0.2s',
  },
  newMessageButton: {
    position: 'absolute',
    bottom: '80px',
    left: '50%',
    transform: 'translateX(-50%)',
    backgroundColor: '#6772e5',
    color: '#ffffff',
    border: 'none',
    borderRadius: '20px',
    padding: '0.4rem 0.8rem',
    fontSize: '0.75rem',
    fontWeight: '700',
    cursor: 'pointer',
    boxShadow: '0 4px 12px rgba(103, 114, 229, 0.3)',
    zIndex: 10,
    animation: 'fadeIn 0.3s ease-out',
  },
};

export default ChatRoom;
