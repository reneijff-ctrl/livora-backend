import React, { useState, useEffect, useRef } from 'react';
import { useAuth } from '../auth/useAuth';
import webSocketService from '../websocket/webSocketService';
import chatRoomService, { ChatRoomDto } from '../api/chatRoomService';
import { showToast } from './Toast';

interface ChatMessage {
  roomId: string;
  sender: string;
  message: string;
  timestamp: string;
  systemMessage?: boolean;
}

const GlobalChat: React.FC = () => {
  const { user, hasPremiumAccess } = useAuth();
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [input, setInput] = useState('');
  const [rooms, setRooms] = useState<ChatRoomDto[]>([]);
  const [activeRoom, setActiveRoom] = useState<ChatRoomDto | null>(null);
  const [onlineCount, setOnlineCount] = useState<number>(0);
  const [isMuted, setIsMuted] = useState(false);
  const messagesEndRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const loadRooms = async () => {
      try {
        const liveRooms = await chatRoomService.getLiveRooms();
        setRooms(liveRooms);
        const publicRoom = liveRooms.find(r => (r.name || '').toLowerCase() === 'public');
        if (publicRoom) {
          setActiveRoom(publicRoom);
        } else if (liveRooms.length > 0) {
          setActiveRoom(liveRooms[0]);
        }
      } catch (e) {
        console.error('Failed to load chat rooms', e);
      }
    };
    loadRooms();
  }, []);

  useEffect(() => {
    if (!activeRoom) return;

    const unsubChat = webSocketService.subscribe(`/topic/chat/${activeRoom.id}`, (msg) => {
      const data = JSON.parse(msg.body);
      // New format is ChatMessageDto directly
      const chatMsg: ChatMessage = data;

      if (chatMsg.systemMessage) {
        showToast(chatMsg.message, 'info');
        if (chatMsg.message.includes('User muted') && user && chatMsg.message.includes(user.email)) {
          setIsMuted(true);
        }
      }
      setMessages((prev) => [...prev, chatMsg]);
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

    const unsubNotifications = webSocketService.subscribe('/user/queue/notifications', (msg) => {
      const data = JSON.parse(msg.body);
      if (data.type === 'DISCONNECT') {
        showToast(data.payload?.reason || 'Disconnected', 'error');
      }
    });

    // Send join message
    webSocketService.send('/app/chat.join', { roomId: activeRoom.id });

    return () => {
      unsubChat();
      unsubPresence();
      unsubErrors();
      unsubNotifications();
    };
  }, [activeRoom, user]);

  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  const handleSend = (e: React.FormEvent) => {
    e.preventDefault();
    if (!input.trim() || !activeRoom) return;

    if (((activeRoom.name || '').toLowerCase()) === 'premium' && !hasPremiumAccess()) {
      showToast('Premium access required for this room', 'error');
      return;
    }

    webSocketService.send('/app/chat.send', {
      roomId: activeRoom.id,
      message: input,
    });
    setInput('');
  };

  const switchRoom = (roomName: string) => {
    const targetRoom = rooms.find(r => (r.name || '').toLowerCase() === (roomName || '').toLowerCase());
    if (targetRoom) {
      setActiveRoom(targetRoom);
      setMessages([]);
    }
  };

  const isPremiumLocked = (roomName: string) => {
    return (roomName || '').toLowerCase() === 'premium' && !hasPremiumAccess();
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
            onClick={() => switchRoom('PUBLIC')}
            style={{ 
              fontWeight: activeRoom?.name === 'PUBLIC' ? 'bold' : 'normal',
              color: activeRoom?.name === 'PUBLIC' ? '#6772e5' : '#666',
              border: 'none',
              background: 'none',
              cursor: 'pointer'
            }}
          >
            Public Room
          </button>
          <button 
            onClick={() => switchRoom('PREMIUM')}
            style={{ 
              fontWeight: activeRoom?.name === 'PREMIUM' ? 'bold' : 'normal',
              color: activeRoom?.name === 'PREMIUM' ? '#d4af37' : '#666',
              border: 'none',
              background: 'none',
              cursor: 'pointer',
              opacity: isPremiumLocked('PREMIUM') ? 0.5 : 1
            }}
          >
            Premium Room {isPremiumLocked('PREMIUM') && '🔒'}
          </button>
        </div>
        <div style={{ fontSize: '0.8rem', color: '#28a745' }}>
          ● {onlineCount} online
        </div>
      </div>

      <div style={{ flex: 1, overflowY: 'auto', padding: '1rem' }}>
        {messages.map((m, i) => (
          <div key={i} style={{ marginBottom: '1rem', textAlign: m.sender === user?.email ? 'right' : 'left' }}>
            <div style={{ fontSize: '0.7rem', color: '#888' }}>{m.sender}</div>
            <div style={{ 
              display: 'inline-block', 
              padding: '0.5rem 1rem', 
              borderRadius: '12px', 
              backgroundColor: m.sender === user?.email ? '#6772e5' : '#e9ecef',
              color: m.sender === user?.email ? '#fff' : '#333',
              maxWidth: '80%',
              wordBreak: 'break-word'
            }}>
              {m.message}
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
          placeholder={isMuted ? "You are muted" : (activeRoom && isPremiumLocked(activeRoom.name) ? "Unlock premium to chat here..." : "Type a message...")}
          disabled={isMuted || (activeRoom ? isPremiumLocked(activeRoom.name) : true)}
          style={{ 
            flex: 1, 
            padding: '0.5rem', 
            borderRadius: '4px', 
            border: '1px solid #ddd' 
          }}
        />
        <button 
          type="submit"
          disabled={isMuted || (activeRoom ? isPremiumLocked(activeRoom.name) : true)}
          style={{ 
            padding: '0.5rem 1rem', 
            backgroundColor: '#6772e5', 
            color: 'white', 
            border: 'none', 
            borderRadius: '4px',
            cursor: 'pointer',
            opacity: (isMuted || (activeRoom ? isPremiumLocked(activeRoom.name) : true)) ? 0.5 : 1
          }}
        >
          Send
        </button>
      </form>
    </div>
  );
};

export default GlobalChat;
