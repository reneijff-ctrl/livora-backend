import React, { useEffect, useState, useRef, useCallback } from 'react';
import { useWs } from '@/ws/WsContext';
import { webSocketService } from '@/websocket/webSocketService';
import apiClient from '@/api/apiClient';
import { useAuth } from '@/auth/useAuth';
import TipBar from '@/components/live/TipBar';
import ChatMessage from '@/components/live/ChatMessage';
import ChatInput from '@/components/live/ChatInput';

import { ChatState, Message } from '@/types/chat';
import { chatService } from '@/chat/chatService';
import { normalizeLiveEvent } from './live/LiveEventsController';


interface ChatComponentProps {
  creatorId: number | string;
  disabled?: boolean;
  showTipBar?: boolean;
  streamRoomId?: string | null;
  onTip?: (payload: any) => void;
  onGoalUpdate?: (payload: any) => void;
  onSuperTipEnd?: () => void;
  chatFontSize?: number;
  hideControls?: boolean; // New prop to hide internal controls
  hiddenUserIds?: Set<number>;
  followerUserIds?: Set<number>;
}

const ChatComponent: React.FC<ChatComponentProps> = ({ 
  creatorId, 
  disabled = false,
  showTipBar = false,
  streamRoomId = null,
  onTip,
  onGoalUpdate,
  onSuperTipEnd,
  chatFontSize = 16,
  hideControls = false,
  hiddenUserIds,
  followerUserIds,
}) => {
  const { user } = useAuth();
  const { subscribe: wsSubscribe, connected } = useWs();
  const subscribedRef = useRef(false);
  const chatStatusSubRef = useRef<{ unsubscribe: () => void } | (() => void) | null>(null);
  const [roomId, setRoomId] = useState<number | null>(null);
  const [chatState, setChatState] = useState<ChatState>('IDLE');
  const [messages, setMessages] = useState<Message[]>([]);
  const [pinnedMessage, setPinnedMessage] = useState<any>(null);
  const prevCreatorIdRef = useRef<number | string | null>(null);
  const processedMessages = useRef<Set<string>>(new Set());
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const [isNearBottom, setIsNearBottom] = useState(true);

  const handleScroll = () => {
    if (!messagesContainerRef.current) return;
    const el = messagesContainerRef.current;
    const threshold = 80;
    const atBottom =
      el.scrollHeight - el.scrollTop - el.clientHeight < threshold;
    setIsNearBottom(atBottom);
  };


  const onTipRef = useRef(onTip);
  useEffect(() => {
    onTipRef.current = onTip;
  }, [onTip]);

  const handleIncomingMessage = useCallback((msg: any) => {
    if (!msg.body) return;
    try {
      const incoming = JSON.parse(msg.body);
      if (!incoming || typeof incoming !== "object") return;

      // Support batched messages from server
      if (incoming.type === 'CHAT_BATCH' && Array.isArray(incoming.messages)) {
        for (const m of incoming.messages) {
          handleIncomingMessage({ body: JSON.stringify(m) });
        }
        return;
      }

      console.debug('CHAT: Received message', incoming);

      // Normalize first — single entry point for all event types
      const message: Message = normalizeLiveEvent(incoming);

      // Route by normalized type — side effects before chat insertion
      switch (message.type) {
        case 'GOAL_PROGRESS':
        case 'GOAL_COMPLETED':
        case 'GOAL_SWITCH':
        case 'GOAL_STATUS':
          console.debug('CHAT: Routing goal event to handler', message);
          onGoalUpdate?.(message.payload || message);
          return;

        case 'PIN_MESSAGE':
          console.debug('PINNED MESSAGE RECEIVED', message);
          setPinnedMessage(message);
          return;

        case 'SUPER_TIP_END':
          console.debug('SUPER_TIP_END received, clearing highlight');
          onSuperTipEnd?.();
          return;

        case 'TIP':
        case 'SUPER_TIP':
          // Overlay animations are driven by the dedicated monetization stream in WatchPage.
          // ChatComponent only renders the tip as a chat message.
          break;

        case 'CHAT':
        case 'BOT':
        case 'SYSTEM':
        case 'ACTION_TRIGGERED':
        case 'TIP_MENU':
          break;

        default:
          console.warn('Ignoring unhandled event type:', message.type);
          return;
      }

      // Dedup check
      if (processedMessages.current.has(message.messageId!)) {
        return;
      }
      processedMessages.current.add(message.messageId!);

      console.debug('CHAT MESSAGE NORMALIZED', message);

      // Push to chat message list
      setMessages(prev => [...prev, message]);
    } catch (e) {
      console.error('CHAT-V2: Failed to parse incoming message', e);
    }
  }, [onGoalUpdate]);

  const sendMessage = useCallback((content: string) => {
    if (!webSocketService.isConnected() || !content.trim()) return;

    const payload = {
        creatorUserId: creatorId,
        content: content,
        type: "CHAT"
    };

    console.log('CHAT-V2: Sending message', payload);
    webSocketService.send("/app/chat.send", payload);
  }, [creatorId, streamRoomId]);

  useEffect(() => {
    // Spinner timeout: max 2 seconds
    if (chatState !== 'IDLE') return;
    const timer = setTimeout(() => {
      setChatState('ERROR');
    }, 2000);
    return () => clearTimeout(timer);
  }, [chatState]);


  useEffect(() => {
    const fetchChatData = async () => {
      if (!creatorId) {
        setChatState('ERROR');
        return;
      }

      try {
        const res = await apiClient.get(`/v2/chat/rooms/creator/${creatorId}`);
        setRoomId(res.data.id);
        const backendStatus = res.data.status;
        if (backendStatus === 'ACTIVE') setChatState('ACTIVE');
        else if (backendStatus === 'WAITING_FOR_CREATOR' || backendStatus === 'PAUSED') setChatState('WAITING_FOR_CREATOR');
        else if (backendStatus === 'ENDED') setChatState('ENDED');
        else setChatState('ACTIVE');
        
        // Also fetch pinned message
        try {
          const pRes = await apiClient.get(`/stream/creator/${creatorId}/pinned`);
          if (pRes.status === 200 && pRes.data) {
            setPinnedMessage(pRes.data);
          }
        } catch (err: any) {
          if (err.response?.status !== 404) {
            console.error("Pinned fetch failed", err);
          }
        }
      } catch (err) {
        console.error('CHAT-V2: Error fetching chat room:', err);
        setChatState('ERROR');
      }
    };

    fetchChatData();
  }, [creatorId]);

  // Subscribe to chat-status once when we have a roomId — do NOT depend on chatState
  // to avoid unsubscribe/resubscribe cycles when chatState changes from the subscription itself.
  useEffect(() => {
    if (!roomId) return;

    // Clean up previous subscription if any
    if (chatStatusSubRef.current) {
      if (typeof (chatStatusSubRef.current as any).unsubscribe === 'function') {
        (chatStatusSubRef.current as any).unsubscribe();
      } else if (typeof chatStatusSubRef.current === 'function') {
        (chatStatusSubRef.current as any)();
      }
      chatStatusSubRef.current = null;
    }

    const subscription = webSocketService.subscribe('/user/queue/chat-status', (msg) => {
      try {
        const data = JSON.parse(msg.body);
        if (data.chatRoomId === roomId) {
          if (data.status === 'ACTIVE') setChatState('ACTIVE');
          if (data.status === 'PAUSED' || data.status === 'WAITING_FOR_CREATOR') setChatState('WAITING_FOR_CREATOR');
          if (data.status === 'ENDED') setChatState('ENDED');
        }
      } catch (e) {
        console.error('CHAT-V2: Failed to parse status update', e);
      }
    });
    chatStatusSubRef.current = subscription;

    return () => {
      if (subscription && typeof (subscription as any).unsubscribe === "function") {
        (subscription as any).unsubscribe();
      } else if (typeof subscription === 'function') {
        (subscription as any)();
      }
      chatStatusSubRef.current = null;
    };
  }, [roomId]);

  // Load chat history via REST when creatorId changes
  useEffect(() => {
    if (!creatorId) return;

    const rulesMessage: Message = {
      id: 'rules',
      type: 'SYSTEM',
      username: 'System',
      content: `Welcome to the stream 👋\n\nRules:\n• Be respectful\n• No spam\n• No advertising\n• Respect the creator\n• No external links`,
      timestamp: new Date().toISOString()
    };

    apiClient.get(`/chat/history/${creatorId}`)
      .then(res => {
        const history: Message[] = (res.data || []).map((m: any) => normalizeLiveEvent(m));
        setMessages(prev => {
          if (prev.some(m => m.id === 'rules')) return prev;
          return [rulesMessage, ...history];
        });
      })
      .catch(() => {});
  }, [creatorId]);

  useEffect(() => {
    if (!creatorId || !connected) {
      subscribedRef.current = false;
      return;
    }

    // Guard: don't re-subscribe if already subscribed for this creatorId
    if (subscribedRef.current) return;
    subscribedRef.current = true;

    console.log("CHAT: subscribing to", `/exchange/amq.topic/chat.${creatorId}`, "connected:", connected);

    const subscription = wsSubscribe(
      `/exchange/amq.topic/chat.${creatorId}`,
      handleIncomingMessage
    );

    return () => {
      subscribedRef.current = false;
      if (subscription && typeof subscription.unsubscribe === "function") {
        subscription.unsubscribe();
      }
    };
  }, [creatorId, connected]);

  // Listen for externally dispatched system messages (e.g. milestone reached)
  useEffect(() => {
    const handler = (e: Event) => {
      const detail = (e as CustomEvent).detail;
      if (!detail || !detail.content) return;
      const msg: Message = {
        id: detail.id || `system-${Date.now()}`,
        type: 'SYSTEM',
        username: 'System',
        content: detail.content,
        timestamp: new Date().toISOString()
      };
      setMessages(prev => [...prev, msg]);
    };
    window.addEventListener('chat:system-message', handler);
    return () => window.removeEventListener('chat:system-message', handler);
  }, []);

  // Only clear chat when creatorId truly changes
  useEffect(() => {
    if (creatorId && String(creatorId) !== String(prevCreatorIdRef.current)) {
      console.log(`CHAT-V2: Clearing messages because creatorId changed from ${prevCreatorIdRef.current || 'none'} to ${creatorId}`);
      chatService.clear();
      setMessages([]);
      processedMessages.current.clear();
      prevCreatorIdRef.current = creatorId;
    }
  }, [creatorId]);

  useEffect(() => {
    if (!messagesContainerRef.current) return;
    if (isNearBottom) {
      const el = messagesContainerRef.current;
      el.scrollTop = el.scrollHeight;
    }
  }, [messages]);


  const isChatDisabled = chatState !== 'ACTIVE';

  function safeRender(value: any) {
    if (value === null || value === undefined) return ""

    if (typeof value === "string") return value
    if (typeof value === "number") return value

    if (typeof value === "object") {
      return (
        value.username ||
        value.senderUsername ||
        value.content ||
        value.message ||
        value.value ||
        ""
      )
    }

    return ""
  }

  return (
    <div className="relative flex flex-col h-full min-h-0 bg-transparent text-white">
      {/* Header */}
      <div className={`px-4 py-3 border-b border-[#16161D]`}>
        <p className="text-[11px] text-zinc-400 font-bold">Live Chat</p>
      </div>

      {pinnedMessage && (
        <div className="px-4 py-2 bg-indigo-500/10 border-b border-[#16161D] relative animate-in slide-in-from-top duration-300">
          <div className="flex justify-between items-center mb-1">
            <span className="text-[10px] font-black text-indigo-400 uppercase tracking-widest">📌 Pinned Tip</span>
            <button onClick={() => setPinnedMessage(null)} className="text-zinc-500 hover:text-white transition">
              <svg className="w-3 h-3" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={3} d="M6 18L18 6M6 6l12 12" />
              </svg>
            </button>
          </div>
          <p className="text-[12px] leading-relaxed">
            <span className="font-bold text-indigo-300">
              {safeRender(pinnedMessage.username || pinnedMessage.senderUsername || pinnedMessage.sender || pinnedMessage.viewer || "Anonymous")}: 
            </span>
            <span className="text-zinc-200">
              {safeRender(pinnedMessage.message || pinnedMessage.content || "")}
            </span>
            <span className="font-black text-indigo-400 ml-1">🪙{safeRender(pinnedMessage.amount)}</span>
          </p>
        </div>
      )}

      {/* Messages List */}
      <div 
        ref={messagesContainerRef}
        onScroll={handleScroll}
        className="flex-1 overflow-y-auto min-h-0"
      >
        {chatState === 'IDLE' && (
          <div className="py-10 text-center">
            <p className="text-zinc-500 animate-pulse text-[11px] font-bold">Loading Chat...</p>
          </div>
        )}
        {chatState === 'ERROR' && messages.length === 0 && (
          <div className="py-10 text-center">
            <p className="text-xs font-medium text-zinc-500">Failed to load chat</p>
          </div>
        )}
        {chatState === 'WAITING_FOR_CREATOR' && messages.length === 0 && (
          <div className="py-10 text-center">
            <p className="text-sm font-medium text-zinc-400">Creator is not online yet</p>
            <p className="text-[11px] text-zinc-500 mt-1 font-bold">Waiting for session to start</p>
          </div>
        )}
        {chatState === 'ENDED' && messages.length === 0 && (
          <div className="py-10 text-center">
            <p className="text-sm font-medium text-zinc-400">Chat ended</p>
            <p className="text-[11px] text-zinc-500 mt-1 font-bold">This session has concluded</p>
          </div>
        )}
        {messages.length === 0 && chatState === 'ACTIVE' && (
          <div className="py-10 text-center">
            <p className="text-zinc-500 italic text-xs">No messages yet.</p>
          </div>
        )}
        
        {messages.filter((msg) => {
          if (!hiddenUserIds || hiddenUserIds.size === 0) return true;
          const sid = msg.senderId != null ? Number(msg.senderId) : NaN;
          if (isNaN(sid)) return true;
          const t = msg.type?.toUpperCase();
          if (t === 'SYSTEM' || t === 'BOT' || t === 'GOAL_STATUS') return true;
          return !hiddenUserIds.has(sid);
        }).map((msg) => {
          const username =
            msg.username ||
            msg.senderUsername ||
            (msg as any).sender?.username ||
            (msg as any).viewer?.username ||
            "Anonymous";

          const text =
            msg.content ||
            (msg as any).message ||
            "";

          console.debug("CHAT MESSAGE NORMALIZED", msg);

          return (
            <ChatMessage 
              key={msg.id}
              message={{...msg, username, content: text}}
              fontSize={chatFontSize}
              isFollower={followerUserIds != null && msg.senderId != null && followerUserIds.has(Number(msg.senderId))}
            />
          );
        })}
      </div>

      {!hideControls && (
        <div className="viewer-chat-controls shrink-0">
          {/* TipBar - Fixed at bottom of chat panel */}
          {showTipBar && streamRoomId && (
            <TipBar creatorId={Number(creatorId)} roomId={streamRoomId} />
          )}

          {/* Input Area */}
          <ChatInput 
            onSend={sendMessage}
            disabled={disabled || isChatDisabled}
          />
        </div>
      )}
    </div>
  );
};

export default React.memo(ChatComponent);
