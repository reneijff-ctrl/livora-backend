import React, { useEffect, useState, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useCreatorPublicProfile } from '@/hooks/useCreatorPublicProfile';
import webRtcService, { SignalingMessage, SignalingType } from '@/websocket/webRtcService';
import { Device, Transport, Consumer } from 'mediasoup-client';
import apiClient from '@/api/apiClient';
import { useAuth } from '@/auth/useAuth';
import { useWs } from '@/ws/WsContext';
import { usePresence } from '@/ws/PresenceContext';
import { safeRender } from '@/utils/safeRender';

interface Message {
  id?: number;
  content: string;
  senderId: number;
  senderRole: string;
  timestamp: string;
}

// Removed local SignalingMessage interface - using webRtcService version

const ChatPage: React.FC = () => {
  const { identifier } = useParams<{ identifier: string }>();
  const { creator, loading: profileLoading } = useCreatorPublicProfile();
  const { user } = useAuth();
  const { subscribe, send } = useWs();
  const { presenceMap } = usePresence();
  const navigate = useNavigate();

  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [availability, setAvailability] = useState<"ONLINE" | "LIVE" | "OFFLINE" | null>(null);

  const [roomId, setRoomId] = useState<number | null>(null);
  const [messages, setMessages] = useState<Message[]>([]);
  const [inputValue, setInputValue] = useState('');
  const scrollRef = useRef<HTMLDivElement>(null);

  const videoRef = useRef<HTMLVideoElement>(null);
  const recvTransport = useRef<Transport | null>(null);
  const consumers = useRef<Map<string, Consumer>>(new Map());
  const remoteStreamRef = useRef<MediaStream | null>(null);

  // 1. Initial data fetch
  useEffect(() => {
    if (!creator?.profile?.userId) return;
    const creatorUserId = creator.profile.userId;

    const fetchData = async () => {
      setLoading(true);
      setError(null);
      try {
        const [availRes, roomRes] = await Promise.all([
          apiClient.get(`/v2/public/creators/${creatorUserId}/availability`),
          apiClient.get(`/v2/chat/rooms/creator/${creatorUserId}`)
        ]);
        setAvailability(availRes.data.availability);
        setRoomId(roomRes.data.id);
      } catch (err: any) {
        console.error('CHAT: Error fetching initial data:', err);
        if (err.response?.status === 401) {
          navigate('/login');
        } else if (err.response?.status === 404) {
          setError("Creator not found");
        } else {
          setError("Failed to load chat session.");
        }
      } finally {
        setLoading(false);
      }
    };

    fetchData();
  }, [creator?.profile?.userId, navigate]);

  // 2. Presence-driven availability updates (from global PresenceContext subscription)
  useEffect(() => {
    if (!creator?.profile?.userId) return;
    const creatorUserId = Number(creator.profile.userId);
    const presence = presenceMap[creatorUserId];
    if (presence?.availability) {
      setAvailability(presence.availability as "ONLINE" | "LIVE" | "OFFLINE");
    }
  }, [creator?.profile?.userId, presenceMap]);

  // 3. WebSocket for chat messages
  useEffect(() => {
    if (!roomId) return;

    const chatSub = subscribe(`/exchange/amq.topic/chat.${roomId}`, (msg) => {
      try {
        const newMessage = JSON.parse(msg.body);
        // Support batched messages from server
        if (newMessage.type === 'CHAT_BATCH' && Array.isArray(newMessage.messages)) {
          setMessages(prev => [...prev, ...newMessage.messages]);
          return;
        }
        setMessages(prev => [...prev, newMessage]);
      } catch (e) {
        console.error('CHAT: Failed to parse incoming message', e);
      }
    });

    return () => {
      if (typeof chatSub === 'function') chatSub();
    };
  }, [roomId, subscribe]);

  // 4. Mediasoup for video player (only if LIVE)
  useEffect(() => {
    if (availability !== 'LIVE' || !creator?.profile?.userId || !user) {
      if (recvTransport.current) {
        recvTransport.current.close();
        recvTransport.current = null;
      }
      webRtcService.cleanup();
      consumers.current.clear();
      return;
    }

    const creatorUserId = creator.profile.userId;
    const roomIdStr = creatorUserId.toString();
    let isMounted = true;

    const handleSignalingData = async (signal: SignalingMessage) => {
      console.debug("CHAT: Received signaling message (SFU mode):", signal.type);
      if (signal.type === 'NEW_PRODUCER') {
        const { producerId, kind } = signal.data;
        consumeProducer(producerId, kind);
      }
    };

    const consumeProducer = async (producerId: string, kind: string) => {
      if (!recvTransport.current || !webRtcService.getDevice() || !isMounted) return;

      try {
        const { id, kind: cKind, rtpParameters } = await webRtcService.sendRequest(SignalingType.CONSUME, roomIdStr, {
          transportId: recvTransport.current.id,
          producerId,
          rtpCapabilities: webRtcService.getDevice()!.rtpCapabilities
        });

        const consumer = await recvTransport.current.consume({
          id,
          producerId,
          kind: cKind,
          rtpParameters,
        });

        consumers.current.set(id, consumer);
        
        const { track } = consumer;
        
        if (!remoteStreamRef.current) {
          remoteStreamRef.current = new MediaStream();
        }
        remoteStreamRef.current.addTrack(track);

        if (videoRef.current) {
          videoRef.current.srcObject = remoteStreamRef.current;
        }

        await webRtcService.sendRequest(SignalingType.RESUME_CONSUMER, roomIdStr, { consumerId: id });
        consumer.resume();
      } catch (error) {
        console.error("CHAT: Mediasoup consume failed", error);
      }
    };

    const initMediasoup = async () => {
      try {
        webRtcService.setCurrentUserId(Number(user.id));
        await webRtcService.connect(roomIdStr, (signal) => {
          handleSignalingData(signal);
        });

        // a. JOIN and Get Router Capabilities
        const { routerRtpCapabilities, producers: existingProducers } = await webRtcService.sendRequest(SignalingType.JOIN, roomIdStr, {
          streamId: 'chat-' + Math.random().toString(36).substring(2, 9)
        });

        if (!isMounted) return;

        // b. Load Device
        const device = await webRtcService.initDevice(routerRtpCapabilities);

        // c. Create Receive Transport on server
        const transportData = await webRtcService.sendRequest(SignalingType.CREATE_TRANSPORT, roomIdStr, {
          direction: 'recv'
        });

        // d. Create Receive Transport on client
        const transport = device.createRecvTransport(transportData);
        recvTransport.current = transport;

        transport.on('connect', async ({ dtlsParameters }, callback, errback) => {
          try {
            await webRtcService.sendRequest(SignalingType.CONNECT_TRANSPORT, roomIdStr, {
              transportId: transport.id,
              dtlsParameters
            });
            callback();
          } catch (error: any) {
            errback(error);
          }
        });

        // e. Consume existing producers
        if (existingProducers && Array.isArray(existingProducers)) {
          for (const producer of existingProducers) {
            consumeProducer(producer.id, producer.kind);
          }
        }
      } catch (err) {
        console.error("CHAT: Mediasoup init failed", err);
      }
    };

    initMediasoup();

    return () => {
      isMounted = false;
      webRtcService.leaveStream();
      if (recvTransport.current) {
        recvTransport.current.close();
        recvTransport.current = null;
      }
      consumers.current.clear();
      remoteStreamRef.current = null;
    };
  }, [availability, creator?.profile?.userId, user]);

  // Auto-scroll to bottom on new messages
  useEffect(() => {
    if (scrollRef.current) {
      scrollRef.current.scrollTop = scrollRef.current.scrollHeight;
    }
  }, [messages]);

  const handleSend = () => {
    if (!inputValue.trim() || !roomId || !user) return;

    send('/app/v2/chat.send', {
      roomId: roomId,
      senderId: user.id,
      senderRole: user.role,
      content: inputValue.trim()
    });
    setInputValue('');
  };

  if (loading || profileLoading) {
    return (
      <div className="min-h-screen flex items-center justify-center bg-[#08080A]">
        <p className="text-zinc-500 animate-pulse font-medium">Loading Chat...</p>
      </div>
    );
  }

  if (error) {
    return (
      <div className="min-h-screen flex flex-col items-center justify-center bg-[#08080A] p-6">
        <p className="text-zinc-100 font-bold text-xl mb-4">{error}</p>
        <button 
          onClick={() => navigate('/explore')}
          className="px-6 py-2 bg-zinc-100 text-black rounded-full font-semibold"
        >
          Back to Explore
        </button>
      </div>
    );
  }

  if (availability === 'OFFLINE') {
    return (
      <div className="min-h-screen flex flex-col bg-[#08080A] text-zinc-100">
        <div className="flex items-center justify-between p-4 border-b border-[#16161D] sticky top-0 bg-[#08080A] z-10">
          <div className="flex items-center gap-3">
            <button onClick={() => navigate(`/creators/${identifier}`)} className="p-2 hover:bg-white/5 rounded-full transition">←</button>
            <div>
              <h2 className="font-bold text-lg leading-tight text-white">{creator.profile.displayName}</h2>
              <p className="text-[10px] uppercase tracking-widest text-zinc-500 font-bold">Live Chat</p>
            </div>
          </div>
        </div>
        <div className="flex-1 flex flex-col items-center justify-center p-6 text-center">
          <div className="p-6 bg-[#0F0F14] rounded-full mb-6 border border-[#16161D]">
            <svg className="w-8 h-8 text-zinc-500" fill="none" stroke="currentColor" viewBox="0 0 24 24">
              <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
            </svg>
          </div>
          <h3 className="text-xl font-bold text-white mb-2">Creator is currently offline.</h3>
          <p className="text-zinc-500 max-w-xs mx-auto">Stay tuned for future sessions!</p>
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen flex flex-col bg-[#08080A] text-zinc-100">
      <div className="flex items-center justify-between p-4 border-b border-[#16161D] sticky top-0 bg-[#08080A] z-10">
        <div className="flex items-center gap-3">
          <button onClick={() => navigate(`/creators/${identifier}`)} className="p-2 hover:bg-white/5 rounded-full transition">←</button>
          <div>
            <h2 className="font-bold text-lg leading-tight text-white">{safeRender(creator.profile.displayName)}</h2>
            <div className="flex items-center gap-2">
               <span className={`w-1.5 h-1.5 rounded-full ${availability === 'LIVE' ? 'bg-red-500 animate-pulse' : 'bg-green-500'}`} />
               <p className="text-[10px] uppercase tracking-widest text-zinc-500 font-bold">
                 {availability === 'LIVE' ? 'Live' : 'Online'}
               </p>
            </div>
          </div>
        </div>
      </div>

      <div className="flex-1 flex flex-col overflow-hidden">
        {availability === 'LIVE' && (
          <div className="aspect-video bg-black w-full max-w-4xl mx-auto mt-4 rounded-xl overflow-hidden shadow-2xl border border-[#16161D]">
            <video ref={videoRef} autoPlay playsInline className="w-full h-full object-contain" />
          </div>
        )}

        <div ref={scrollRef} className="flex-1 overflow-y-auto p-4 space-y-6 max-w-2xl mx-auto w-full">
          {messages.length === 0 && (
            <div className="py-20 text-center">
              <p className="text-zinc-500 italic text-sm">No messages yet. Say hi!</p>
            </div>
          )}
          {messages.map((msg, i) => (
            <div key={i} className="flex flex-col animate-in fade-in slide-in-from-bottom-2 duration-300">
              <div className="flex items-center gap-2 mb-1.5">
                <span className={`text-[10px] font-bold uppercase tracking-widest px-1.5 py-0.5 rounded ${
                  msg.senderRole === 'CREATOR' ? 'bg-white text-black' : 'bg-[#0F0F14] text-zinc-400'
                }`}>{msg.senderRole}</span>
                <span className="text-[10px] text-zinc-500 font-medium">
                  {msg.timestamp ? new Date(msg.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : ''}
                </span>
              </div>
              <p className="text-zinc-100 leading-relaxed text-sm bg-[#0F0F14]/50 p-3 rounded-2xl rounded-tl-none border border-[#16161D]">{safeRender(msg.content)}</p>
            </div>
          ))}
        </div>
      </div>

      <div className="p-4 border-t border-[#16161D] bg-[#08080A] sticky bottom-0">
        <div className="max-w-2xl mx-auto flex gap-2">
          <input
            type="text"
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onKeyDown={(e) => e.key === 'Enter' && handleSend()}
            placeholder="Type your message..."
            className="flex-1 px-5 py-3 bg-[#0F0F14] border-none rounded-2xl focus:ring-2 focus:ring-indigo-500 text-white text-sm transition-all disabled:opacity-50"
          />
          <button
            onClick={handleSend}
            disabled={!inputValue.trim()}
            className="px-6 py-3 bg-indigo-600 text-white rounded-2xl font-bold text-sm transition hover:bg-indigo-700 active:scale-95 disabled:opacity-30 disabled:pointer-events-none shadow-lg shadow-indigo-600/20"
          >
            Send
          </button>
        </div>
      </div>
    </div>
  );
};

export default ChatPage;
