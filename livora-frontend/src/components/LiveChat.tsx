import React, { useState, useEffect, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { useAuth } from '../auth/useAuth';
import { useWallet } from '../hooks/useWallet';
import { useWs } from '../ws/WsContext';
import { showToast } from './Toast';
import chatModerationService from '../api/chatModerationService';
import apiClient from '../api/apiClient';
import TokenBalance from './TokenBalance';
import AbuseReportModal from './AbuseReportModal';
import { ReportTargetType } from '../types/report';

interface LiveChatMessage {
  id?: string;
  senderId?: string;
  senderEmail?: string;
  senderUsername?: string;
  senderRole?: 'VIEWER' | 'CREATOR' | 'SYSTEM' | 'ADMIN' | 'MODERATOR';
  senderType?: 'USER' | 'CREATOR' | 'OWNER' | 'ADMIN' | 'SYSTEM' | 'BOT';
  isStreamOwner?: boolean;
  creatorUserId?: number | string;
  message?: string;
  content?: string;
  timestamp?: string;
  system?: boolean;
  isPaid?: boolean;
  amount?: number;
  type?: string;
}

interface RealtimeMessage {
  type: string;
  chatMessage?: LiveChatMessage;
  payload?: any;
  username?: string;
  senderUsername?: string;
  message?: string;
  content?: string;
  amount?: number;
  timestamp?: string;
  systemMessage?: boolean;
}

interface LiveChatProps {
  streamId: string;
  userId: number;
  isPaid?: boolean;
  pricePerMessage?: number;
}

/**
 * LiveChat component for livestream pages.
 * Handles real-time chat via WebSockets (STOMP).
 */
const LiveChat: React.FC<LiveChatProps> = ({ streamId, userId, isPaid, pricePerMessage = 0 }) => {
  const { user } = useAuth();
  const { subscribe, send } = useWs();
  const navigate = useNavigate();
  const { refreshBalance, hasSufficientBalance } = useWallet();
  const [messages, setMessages] = useState<LiveChatMessage[]>([]);
  const [activeTip, setActiveTip] = useState<{ viewer: string, amount: number, message: string, animationType?: string } | null>(null);
  const [pinnedMessage, setPinnedMessage] = useState<any>(null);
  const [input, setInput] = useState('');
  const [isMuted, setIsMuted] = useState(false);
  const [mutedUsers, setMutedUsers] = useState<Set<string>>(new Set());
  const [bannedUsers, setBannedUsers] = useState<Set<string>>(new Set());
  const [reportModal, setReportModal] = useState<{isOpen: boolean, targetId: string, targetType: ReportTargetType, targetLabel?: string, reportedUserId: number} | null>(null);
  const [isModerator, setIsModerator] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  const isCreator = user && Number(user.id) === Number(userId);
  const isAdmin = user && user.role === 'ADMIN';
  const hasModPower = isCreator || isAdmin || isModerator;
  const hasInsufficientTokens = isPaid && !hasModPower && !hasSufficientBalance(pricePerMessage);

  // Check initial moderator status and subscribe to updates
  useEffect(() => {
    if (!user?.id || !userId) return;
    // Initial check
    apiClient.get(`/stream/moderators/${userId}`).then(res => {
      const modIds: number[] = res.data || [];
      setIsModerator(modIds.includes(Number(user.id)));
    }).catch(() => {});
    // Subscribe to personal moderator status updates
    let unsub = () => {};
    const result = subscribe('/user/queue/moderation', (msg) => {
      try {
        const data = JSON.parse(msg.body);
        if (data.type === 'MODERATOR_STATUS' && data.payload?.creatorId === Number(userId)) {
          setIsModerator(!!data.payload.isModerator);
        }
      } catch {}
    });
    if (typeof result === 'function') {
      unsub = result;
    }
    return () => { unsub(); };
  }, [user?.id, userId, subscribe]);

  useEffect(() => {
    // Inject animation keyframes
    const styleSheet = document.createElement("style");
    styleSheet.type = "text/css";
    styleSheet.innerText = `
      @keyframes slideDown {
        from { transform: translateY(-20px); opacity: 0; }
        to { transform: translateY(0); opacity: 1; }
      }
    `;
    document.head.appendChild(styleSheet);
    return () => {
      document.head.removeChild(styleSheet);
    };
  }, []);

  useEffect(() => {
    if (!streamId) return;

    // Subscribe to the chat topic for this creator
    const topic = `/exchange/amq.topic/chat.${userId}`;
    const handleLiveChatMessage = (message: any) => {
      try {
        const incoming: RealtimeMessage = JSON.parse(message.body);

        // Support batched messages from server
        if (incoming.type === 'CHAT_BATCH' && Array.isArray((incoming as any).messages)) {
          for (const m of (incoming as any).messages) {
            handleLiveChatMessage({ body: JSON.stringify(m) });
          }
          return;
        }
        
        if (incoming.type === 'PIN_MESSAGE') {
          console.debug("PIN_MESSAGE RECEIVED", incoming.payload);
          setPinnedMessage(incoming.payload);
        } else if (
          incoming.type === 'CHAT' ||
          incoming.type === 'TIP' ||
          incoming.type === 'BOT' ||
          incoming.type === 'SYSTEM' ||
          incoming.type === 'SUPER_TIP'
        ) {
          const normalized: LiveChatMessage = {
            id: incoming.chatMessage?.id || String(Date.now()),
            senderId: incoming.chatMessage?.senderId || '0',
            senderEmail: incoming.chatMessage?.senderEmail,
            senderUsername: incoming.senderUsername || incoming.username || incoming.chatMessage?.senderUsername || 'User',
            senderRole: incoming.chatMessage?.senderRole || 'VIEWER',
            senderType: (incoming as any).senderType ?? (incoming.chatMessage as any)?.senderType,
            isStreamOwner: (incoming as any).isStreamOwner ?? (incoming.chatMessage as any)?.isStreamOwner,
            creatorUserId: (incoming as any).creatorUserId ?? (incoming.chatMessage as any)?.creatorUserId,
            message: incoming.content || incoming.message || incoming.chatMessage?.message || '',
            content: incoming.content,
            timestamp: incoming.timestamp || incoming.chatMessage?.timestamp || new Date().toISOString(),
            system: incoming.type === 'SYSTEM' || incoming.systemMessage || incoming.chatMessage?.system,
            isPaid: incoming.type === 'TIP' || incoming.chatMessage?.isPaid,
            amount: incoming.amount || incoming.chatMessage?.amount,
            type: incoming.type
          };
          
          if (normalized.system) {
            const content = normalized.message || normalized.content;
            if (content && content.includes('User muted') && user && content.includes(user.username || '')) {
              setIsMuted(true);
            }
          }

          if (incoming.type === 'TIP' && !incoming.chatMessage) {
             // If it's the new TIP format, trigger active tip animation too
             setActiveTip({
               viewer: incoming.senderUsername || incoming.username || 'Someone',
               amount: incoming.amount || 0,
               message: incoming.content || incoming.message || '',
               animationType: incoming.payload?.animationType
             });
          }

          setMessages((prev) => [...prev, normalized]);
        } else if (incoming.type === 'chat' && incoming.chatMessage) {
          const chatMsg = incoming.chatMessage;
          if (chatMsg.system) {
            // Check if current user is muted
            if ((chatMsg.message || '').includes('User muted') && user && (chatMsg.message || '').includes(user.username || '')) {
              setIsMuted(true);
            }
            // Track muted/banned users for indicators
            if ((chatMsg.message || '').includes('User muted:')) {
              const username = (chatMsg.message || '').split('User muted: ')[1];
              setMutedUsers(prev => new Set(prev).add(username));
            } else if ((chatMsg.message || '').includes('User banned:')) {
              const username = (chatMsg.message || '').split('User banned: ')[1];
              setBannedUsers(prev => new Set(prev).add(username));
            }
          }
          setMessages((prev) => [...prev, chatMsg]);
        } else if (incoming.type === 'MESSAGE_DELETED') {
          const deletedId = incoming.payload?.messageId;
          setMessages((prev) => prev.filter(m => m.id !== deletedId));
        } else if (incoming.type === 'TIP') {
          const tipData = incoming.payload;
          setActiveTip({
            viewer: tipData.viewer,
            amount: tipData.amount,
            message: tipData.message,
            animationType: tipData.animationType
          });
          
          // Add as a system message to chat as well
          const systemMsg: LiveChatMessage = {
            id: `tip-${Date.now()}`,
            senderId: 'system',
            senderUsername: 'System',
            senderRole: 'SYSTEM',
            senderType: 'SYSTEM',
            message: `🪙 ${tipData.viewer} tipped ${tipData.amount} tokens!${tipData.message ? ` "${tipData.message}"` : ''}`,
            timestamp: new Date().toISOString(),
            system: true
          };
          setMessages((prev) => [...prev, systemMsg]);
          
          // Refresh balance if it's our tip or we are the creator
          if (isCreator || (user && tipData.viewer === user.username)) {
            refreshBalance();
          }
        }
      } catch (error) {
        // Fallback for direct ChatMessage (legacy/non-wrapped)
        try {
          const chatMsg: LiveChatMessage = JSON.parse(message.body);
          if (chatMsg.senderId) {
            setMessages((prev) => [...prev, chatMsg]);
          }
        } catch (e) {
          console.error('Failed to parse chat message', error);
        }
      }
    };

    const unsubscribe = subscribe(topic, handleLiveChatMessage);

    // Clean up subscription on unmount or streamId change
    return () => {
      if (typeof unsubscribe === 'function') unsubscribe();
    };
  }, [streamId, user, subscribe]);

  useEffect(() => {
    if (!streamId) return;
    
    const fetchPinned = async () => {
      try {
        const res = await apiClient.get(`/stream/${streamId}/pinned`);
        if (res.status === 200 && res.data) {
          setPinnedMessage(res.data);
        }
      } catch (e) {
        console.log('No pinned message found');
      }
    };
    fetchPinned();
  }, [streamId]);

  // Auto-scroll to latest message when messages update
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  useEffect(() => {
    if (activeTip) {
      const timer = setTimeout(() => {
        setActiveTip(null);
      }, 5000);
      return () => clearTimeout(timer);
    }
  }, [activeTip]);

  const handleSendMessage = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || !streamId || isMuted || hasInsufficientTokens) return;

    const isPaidMessage = isPaid && !hasModPower;

    // Send message to the backend mapping
    send("/app/chat.send", {
      creatorUserId: userId,
      content: input,
      type: "CHAT",
      isPaid: isPaidMessage,
      amount: isPaidMessage ? pricePerMessage : 0
    });

    setInput('');

    // Auto-refresh balance after spending
    if (isPaidMessage) {
      setTimeout(() => refreshBalance(), 1000); // Small delay to allow backend processing
    }
  };

  const handleMute = async (targetUserId: string) => {
    try {
      await chatModerationService.muteUser({
        userId: Number(targetUserId),
        durationSeconds: 300, // 5 minutes default
        roomId: `stream-${streamId}`,
      });
      showToast('User muted for 5 minutes', 'success');
    } catch (error) {
      showToast('Failed to mute user', 'error');
    }
  };

  const handleBan = async (targetUserId: string) => {
    if (!window.confirm('Are you sure you want to ban this user?')) return;
    try {
      await chatModerationService.banUser({
        userId: Number(targetUserId),
        roomId: `stream-${streamId}`,
      });
      showToast('User banned from stream', 'success');
    } catch (error) {
      showToast('Failed to ban user', 'error');
    }
  };

  const handleDeleteMessage = async (messageId: string) => {
    try {
      await chatModerationService.deleteMessage({
        messageId,
        roomId: `stream-${streamId}`,
      });
      showToast('Message deleted', 'success');
    } catch (error) {
      showToast('Failed to delete message', 'error');
    }
  };

  const formatTime = (timestamp: string) => {
    return new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  };

  return (
    <div className="flex flex-col h-full min-h-[400px] rounded-3xl bg-black/40 backdrop-blur-xl border border-white/5 overflow-hidden">
      <div style={styles.header}>
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <h3 style={styles.title}>Live Chat</h3>
          <TokenBalance />
        </div>
      </div>

      {pinnedMessage && (
        <div style={styles.pinnedOverlay}>
          <div style={styles.pinnedContent}>
            <div style={styles.pinnedHeader}>
               <span style={{ fontSize: '0.7rem', fontWeight: 'bold', color: '#ffd700' }}>📌 PINNED TIP</span>
               <button onClick={() => setPinnedMessage(null)} style={styles.closePin}>×</button>
            </div>
            <div style={styles.pinnedDetails}>
              <span style={styles.pinnedViewer}>
                {pinnedMessage.username || 
                 pinnedMessage.senderUsername || 
                 (typeof pinnedMessage.viewer === 'string' ? pinnedMessage.viewer : pinnedMessage.viewer?.username) || 
                 'Anonymous'}: 
              </span>
              <span style={styles.pinnedText}>{pinnedMessage.message || pinnedMessage.content || ''}</span>
              <span style={styles.pinnedAmount}> 🪙{pinnedMessage.amount}</span>
            </div>
          </div>
        </div>
      )}

      {activeTip && (
        <div style={styles.tipOverlay}>
          <div style={{
            ...styles.tipContent,
            backgroundColor: activeTip.animationType === 'fireworks' ? 'rgba(255, 69, 0, 0.95)' : 
                             activeTip.animationType === 'diamond' ? 'rgba(0, 191, 255, 0.95)' : 
                             activeTip.animationType === 'heart' ? 'rgba(255, 20, 147, 0.95)' : 
                             'rgba(103, 114, 229, 0.95)'
          }}>
            <div style={styles.tipIcon}>
              {activeTip.animationType === 'fireworks' ? '🔥' : 
               activeTip.animationType === 'diamond' ? '💎' : 
               activeTip.animationType === 'heart' ? '❤️' : 
               '🪙'}
            </div>
            <div style={styles.tipDetails}>
              <div style={styles.tipViewer}>{activeTip.viewer} tipped!</div>
              <div style={styles.tipAmount}>{activeTip.amount} tokens</div>
              {activeTip.message && <div style={styles.tipMessage}>"{activeTip.message}"</div>}
            </div>
          </div>
        </div>
      )}

      <div className="flex-1 overflow-y-auto px-4 py-4 space-y-3">
        {messages.length === 0 ? (
          <div style={styles.emptyState}>Welcome to the stream! Say hello.</div>
        ) : (
          messages.map((msg, index) => {
            const isMutedUser = msg.senderUsername ? mutedUsers.has(msg.senderUsername) : false;
            const isBannedUser = msg.senderUsername ? bannedUsers.has(msg.senderUsername) : false;
            const isBot = msg.type === 'BOT' || msg.senderType === 'BOT';
            const isOwnerMsg = msg.senderType === 'OWNER';
            const isCreatorMsg = msg.senderType === 'CREATOR';
            const isAdminMsg = msg.senderType === 'ADMIN';
            const isSystemMsg = msg.senderType === 'SYSTEM' || msg.system;
            const isMsgFromModerator = msg.senderRole === 'MODERATOR';
            // isOwnerMsg already covers the owner case; no legacy fallback needed
            const isPrivilegedSender = isOwnerMsg || isAdminMsg;
            
            return (
              <div key={index} 
                className={`chat-bubble ${isBot ? 'chat-bot chat-bot-scanline' : ''} ${msg.type === 'TIP' ? 'chat-tip neon-tip' : ''}`}
                style={{
                  ...styles.messageItem,
                  opacity: isBannedUser ? 0.5 : 1
                }}
              >
                <div style={styles.messageHeader}>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span className={isBot ? 'chat-username chat-username-bot' : ''} style={{
                      ...styles.senderName,
                      ...(isOwnerMsg && !isBot ? styles.creatorHighlight : {})
                    }}>
                      {isBot && <span className="mr-1.5 animate-bounce inline-block">🤖</span>}
                      {msg.senderUsername || 'Someone'}
                    </span>
                    {isOwnerMsg && !isBot && <span style={{ fontSize: '9px', padding: '1px 5px', borderRadius: '4px', backgroundColor: 'rgba(103,114,229,0.25)', color: '#a5b4fc', fontWeight: 700, border: '1px solid rgba(103,114,229,0.4)' }}>⭐ HOST</span>}
                    {isCreatorMsg && !isBot && <span style={{ fontSize: '9px', padding: '1px 5px', borderRadius: '4px', backgroundColor: 'rgba(63,63,70,0.5)', color: '#a1a1aa', fontWeight: 700, border: '1px solid rgba(63,63,70,0.6)' }}>CREATOR</span>}
                    {isAdminMsg && !isBot && <span style={{ fontSize: '9px', padding: '1px 5px', borderRadius: '4px', backgroundColor: 'rgba(239,68,68,0.15)', color: '#f87171', fontWeight: 700, border: '1px solid rgba(239,68,68,0.3)' }}>ADMIN</span>}
                    {isMsgFromModerator && !isOwnerMsg && !isAdminMsg && <span style={{ fontSize: '9px', padding: '1px 5px', borderRadius: '4px', backgroundColor: 'rgba(34,197,94,0.15)', color: '#4ade80', fontWeight: 700, border: '1px solid rgba(34,197,94,0.2)' }}>MOD</span>}
                    {isMutedUser && <span title="Muted" style={styles.modIndicator}>🔇</span>}
                    {isBannedUser && <span title="Banned" style={styles.modIndicator}>🚫</span>}
                    {msg.isPaid && <span style={styles.paidBadge}>🪙 {msg.amount}</span>}
                  </div>
                  <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                    <span style={styles.timestamp}>{formatTime(msg.timestamp || '')}</span>
                    {!msg.system && user && msg.senderId !== user.id.toString() && (
                      <div style={{ display: 'flex', gap: '2px' }}>
                        <button 
                          onClick={() => setReportModal({
                            isOpen: true, 
                            targetId: msg.id || '', 
                            targetType: ReportTargetType.CHAT_MESSAGE,
                            targetLabel: `Message: "${(msg.message || msg.content || '').substring(0, 20)}..."`,
                            reportedUserId: Number(msg.senderId)
                          })}
                          style={styles.reportButton} 
                          title="Report Message"
                        >
                          💬🚩
                        </button>
                        <button 
                          onClick={() => setReportModal({
                            isOpen: true, 
                            targetId: msg.senderId || '', 
                            targetType: ReportTargetType.USER,
                            targetLabel: `User: ${msg.senderUsername}`,
                            reportedUserId: Number(msg.senderId)
                          })}
                          style={styles.reportButton} 
                          title="Report User"
                        >
                          👤🚩
                        </button>
                      </div>
                    )}
                    {hasModPower && !isPrivilegedSender && !isMsgFromModerator && (
                      <div style={styles.modActions}>
                        <button onClick={() => msg.senderId && handleMute(msg.senderId)} style={styles.modButton} title="Mute User">M</button>
                        <button onClick={() => msg.senderId && handleBan(msg.senderId)} style={styles.modButton} title="Ban User">B</button>
                        <button onClick={() => msg.id && handleDeleteMessage(msg.id)} style={styles.modButton} title="Delete Message">D</button>
                      </div>
                    )}
                    {(isCreator || isAdmin) && isOwnerMsg && msg.id && (
                       <button onClick={() => msg.id && handleDeleteMessage(msg.id)} style={styles.modButton} title="Delete Message">D</button>
                    )}
                    {(isCreator || isAdmin) && isMsgFromModerator && !isOwnerMsg && (
                      <div style={styles.modActions}>
                        <button onClick={() => msg.senderId && handleMute(msg.senderId)} style={styles.modButton} title="Mute User">M</button>
                        <button onClick={() => msg.id && handleDeleteMessage(msg.id)} style={styles.modButton} title="Delete Message">D</button>
                      </div>
                    )}
                  </div>
                </div>
                <div className={isBot ? 'animate-typewriter-subtle' : ''} style={{
                  ...styles.messageText,
                  textDecoration: isBannedUser ? 'line-through' : 'none',
                  display: isBot ? 'inline-block' : 'block'
                }}>{msg.message}</div>
              </div>
            );
          })
        )}
        <div ref={messagesEndRef} />
      </div>

      <form onSubmit={handleSendMessage} className="sticky bottom-0 bg-black/60 backdrop-blur-md border-t border-white/5 p-4">
        <div style={{ display: 'flex', flexDirection: 'column', width: '100%', gap: '0.5rem' }}>
          {hasInsufficientTokens && !isMuted && (
            <div style={{ color: '#ef4444', fontSize: '0.8rem', marginBottom: '0.5rem', display: 'flex', justifyContent: 'space-between', alignItems: 'center', backgroundColor: 'rgba(239, 68, 68, 0.1)', padding: '0.5rem', borderRadius: '4px' }}>
              <span>Insufficient tokens to send messages.</span>
              <button 
                type="button"
                onClick={() => navigate('/tokens/purchase')}
                style={{ background: 'none', border: 'none', color: '#6772e5', cursor: 'pointer', fontWeight: 'bold', fontSize: '0.8rem', textDecoration: 'underline' }}
              >
                Buy Tokens 🪙
              </button>
            </div>
          )}
          <div style={{ display: 'flex', gap: '0.5rem' }}>
            <input
              type="text"
              value={input}
              onChange={(e) => setInput(e.target.value)}
              placeholder={isMuted ? "You are muted" : (hasInsufficientTokens ? "Insufficient tokens" : (isPaid && !hasModPower ? `Pay ${pricePerMessage} 🪙 per message` : "Send a message..."))}
              style={styles.input}
              maxLength={500}
              disabled={isMuted || hasInsufficientTokens}
            />
            <button type="submit" style={styles.sendButton} disabled={!input.trim() || isMuted || hasInsufficientTokens}>
              Send
            </button>
          </div>
        </div>
      </form>

      {reportModal && (
        <AbuseReportModal
          isOpen={reportModal.isOpen}
          onClose={() => setReportModal(null)}
          targetType={reportModal.targetType}
          targetId={reportModal.targetId}
          targetLabel={reportModal.targetLabel}
          reportedUserId={reportModal.reportedUserId}
          streamId={streamId}
        />
      )}
    </div>
  );
};

const styles: { [key: string]: React.CSSProperties } = {
  header: {
    padding: '1rem',
    borderBottom: '1px solid #2d2d35',
    backgroundColor: '#1c1c21',
  },
  title: {
    margin: 0,
    fontSize: '1rem',
    color: '#ffffff',
    fontWeight: '700',
  },
  emptyState: {
    textAlign: 'center',
    color: '#a0a0ab',
    marginTop: '2rem',
    fontSize: '0.9rem',
  },
  messageItem: {
    display: 'flex',
    flexDirection: 'column',
    gap: '0.25rem',
  },
  messageHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'baseline',
  },
  senderName: {
    fontWeight: '700',
    fontSize: '0.85rem',
    color: '#a0a0ab',
  },
  creatorHighlight: {
    color: '#6772e5',
    backgroundColor: 'rgba(103, 114, 229, 0.1)',
    padding: '2px 6px',
    borderRadius: '4px',
  },
  timestamp: {
    fontSize: '0.7rem',
    color: '#52525b',
  },
  messageText: {
    fontSize: '0.95rem',
    color: '#ffffff',
    wordBreak: 'break-word',
    lineHeight: '1.4',
  },
  input: {
    flex: 1,
    padding: '0.6rem 1rem',
    backgroundColor: '#09090b',
    border: '1px solid #2d2d35',
    borderRadius: '8px',
    color: '#ffffff',
    outline: 'none',
    fontSize: '0.9rem',
  },
  sendButton: {
    padding: '0.6rem 1.2rem',
    backgroundColor: '#6772e5',
    color: 'white',
    border: 'none',
    borderRadius: '8px',
    cursor: 'pointer',
    fontWeight: '600',
    transition: 'opacity 0.2s',
  },
  modActions: {
    display: 'flex',
    gap: '4px',
  },
  modButton: {
    padding: '2px 6px',
    fontSize: '0.7rem',
    backgroundColor: '#3f3f46',
    color: '#ffffff',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    opacity: 0.7,
  },
  reportButton: {
    padding: '2px 6px',
    fontSize: '0.7rem',
    backgroundColor: 'transparent',
    color: '#ef4444',
    border: 'none',
    borderRadius: '4px',
    cursor: 'pointer',
    opacity: 0.6,
  },
  modIndicator: {
    fontSize: '0.8rem',
  },
  paidBadge: {
    fontSize: '0.75rem',
    backgroundColor: 'rgba(255, 215, 0, 0.1)',
    color: '#ffd700',
    padding: '1px 6px',
    borderRadius: '4px',
    border: '1px solid rgba(255, 215, 0, 0.3)',
    fontWeight: '600',
  },
  pinnedOverlay: {
    padding: '0.5rem 1rem',
    borderBottom: '1px solid #2d2d35',
    backgroundColor: 'rgba(255, 215, 0, 0.05)',
    position: 'relative',
    zIndex: 5,
  },
  pinnedContent: {
    display: 'flex',
    flexDirection: 'column',
    gap: '2px',
  },
  pinnedHeader: {
    display: 'flex',
    justifyContent: 'space-between',
    alignItems: 'center',
    marginBottom: '2px',
  },
  closePin: {
    background: 'none',
    border: 'none',
    color: '#a0a0ab',
    cursor: 'pointer',
    fontSize: '1rem',
    lineHeight: 1,
    padding: 0,
  },
  pinnedDetails: {
    fontSize: '0.85rem',
    wordBreak: 'break-word',
  },
  pinnedViewer: {
    fontWeight: 'bold',
    color: '#ffd700',
  },
  pinnedText: {
    color: '#ffffff',
  },
  pinnedAmount: {
    color: '#ffd700',
    fontWeight: 'bold',
    marginLeft: '4px',
  },
  warningMessage: {
    fontSize: '0.8rem',
    color: '#ff4d4f',
    backgroundColor: 'rgba(255, 77, 79, 0.1)',
    padding: '0.5rem',
    borderRadius: '6px',
    textAlign: 'center',
  },
  tipOverlay: {
    position: 'absolute',
    top: '60px',
    left: '10px',
    right: '10px',
    zIndex: 10,
    animation: 'slideDown 0.5s ease-out',
  },
  tipContent: {
    display: 'flex',
    alignItems: 'center',
    gap: '12px',
    padding: '12px',
    backgroundColor: 'rgba(103, 114, 229, 0.95)',
    borderRadius: '8px',
    boxShadow: '0 4px 12px rgba(0,0,0,0.3)',
    border: '1px solid rgba(255,255,255,0.2)',
    color: 'white',
  },
  tipIcon: {
    fontSize: '2rem',
  },
  tipDetails: {
    display: 'flex',
    flexDirection: 'column',
  },
  tipViewer: {
    fontSize: '0.8rem',
    fontWeight: '600',
    opacity: 0.9,
  },
  tipAmount: {
    fontSize: '1.2rem',
    fontWeight: '800',
  },
  tipMessage: {
    fontSize: '0.85rem',
    fontStyle: 'italic',
    marginTop: '4px',
    borderTop: '1px solid rgba(255,255,255,0.2)',
    paddingTop: '4px',
  },
};

export default LiveChat;
