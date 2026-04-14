import React, { useEffect, useRef } from 'react';
import { spatialSoundEngine } from '@/utils/SpatialSoundEngine';
import { safeRender } from '@/utils/safeRender';

import { Message } from '@/types/chat';

interface ChatMessageProps {
  message: Message;
  fontSize?: number;
  isFollower?: boolean;
}

/**
 * ChatMessage - A high-performance, glassmorphism-styled message component.
 * Features neon typography, soft hover glows, and specialized styling for different message types.
 * Optimized with React.memo and GPU-accelerated transforms.
 */
const GIF_URL_REGEX = /https?:\/\/\S+\.gif(?:\?\S+)?/i;

const ChatMessage: React.FC<ChatMessageProps> = ({ message, fontSize = 16, isFollower = false }) => {
  const elementRef = useRef<HTMLDivElement>(null);

  const {
    username,
    senderUsername,
    content,
    timestamp,
    role,
    senderType,
    type = 'CHAT',
    amount,
    giftName
  } = message;

  const isBot = type === 'BOT' || senderType === 'BOT';
  const isOwner = senderType === 'OWNER';
  const isCreatorType = senderType === 'CREATOR';
  const isAdminType = senderType === 'ADMIN';
  // Backward compat: legacy role field used only when senderType is absent
  const isLegacyCreator = !senderType && role === 'CREATOR';
  const displayName = senderUsername || username || (isBot ? 'Livora AI' : 'Someone');

  const soundProfile = message.payload?.soundProfile;

  const hasGif = GIF_URL_REGEX.test(content || '');

  // Handle spatial audio for tips
  useEffect(() => {
    if (type === 'TIP' || type === 'SUPER_TIP') {
      const calculateAndPlay = () => {
        if (elementRef.current) {
          const rect = elementRef.current.getBoundingClientRect();
          const viewportWidth = window.innerWidth;
          
          if (viewportWidth === 0) return;

          const centerX = rect.left + rect.width / 2;
          const pan = (centerX / viewportWidth) * 2 - 1;
          
          spatialSoundEngine.play(soundProfile || 'common', pan);
        }
      };

      const frameId = requestAnimationFrame(calculateAndPlay);
      return () => cancelAnimationFrame(frameId);
    }
  }, [type, soundProfile]);

  const formattedTime = timestamp ? new Date(timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' }) : '';

  const isPositive = message?.highlight === 'POSITIVE';
  const isTip = type === 'TIP' || type === 'SUPER_TIP';

  const renderContent = () => {
    if (hasGif) {
      // If it contains a GIF URL (possibly with text before/after)
      const match = content?.match(GIF_URL_REGEX);
      if (match) {
        const url = match[0];
        const textBefore = content.split(url)[0];
        const textAfter = content.split(url)[1];

        return (
          <div className="flex flex-col gap-1.5">
            {textBefore && <p className="break-words">{textBefore}</p>}
            <div className="mt-1 max-w-[240px] rounded-xl overflow-hidden border border-white/10 shadow-lg animate-in fade-in duration-500">
              <img 
                src={url} 
                alt="GIF" 
                className="w-full max-h-[150px] object-cover"
                loading="lazy"
              />
            </div>
            {textAfter && <p className="break-words">{textAfter}</p>}
          </div>
        );
      }
    }
    return <p className={`break-words ${isBot ? 'animate-typewriter-subtle' : ''}`}>{safeRender(content)}</p>;
  };

  // System Message Styling
  if (type === 'system' || type === 'SYSTEM' || type === 'GOAL_STATUS') {
    return (
      <div className="flex flex-col items-center my-3 px-4 animate-in fade-in duration-500">
        <div className="bg-white/5 backdrop-blur-md border border-white/5 px-4 py-1.5 rounded-full shadow-sm">
          <p className="text-[10px] font-bold tracking-widest text-zinc-500 uppercase text-center">
            {safeRender(content || (message.payload ? `${message.payload.title}: ${message.payload.currentAmount}/${message.payload.targetAmount} tokens (${message.payload.percentage}%)` : ''))}
          </p>
        </div>
      </div>
    );
  }

  // ACTION_TRIGGERED Rendering
  if (type === 'ACTION_TRIGGERED') {
    const donor = message.payload?.donor || 'Someone';
    const amount = message.payload?.amount || 0;
    const description = message.payload?.description || '';

    return (
      <div className="my-2 px-4 animate-in zoom-in duration-500">
        <div className="bg-gradient-to-r from-purple-500/20 to-indigo-500/20 border border-purple-500/30 rounded-xl p-3 shadow-[0_0_20px_rgba(168,85,247,0.15)] relative overflow-hidden group">
          <div className="absolute inset-0 bg-gradient-to-r from-transparent via-white/5 to-transparent -translate-x-full group-hover:translate-x-full transition-transform duration-1000" />
          <div className="flex items-center gap-3 relative z-10">
            <div className="w-10 h-10 rounded-full bg-purple-500 flex items-center justify-center text-xl animate-bounce shadow-lg shadow-purple-500/40 shrink-0">
              ⚡
            </div>
            <div>
              <p className="text-[10px] font-black text-purple-400 uppercase tracking-widest mb-0.5">Tip Action</p>
              <p className="text-xs text-white/90 leading-tight">
                <span className="font-bold text-white">{safeRender(donor)}</span> tipped <span className="font-black text-purple-300">{safeRender(amount)} tokens</span> for:
              </p>
              <p className="text-sm font-black text-white mt-1 italic">"{safeRender(description)}"</p>
            </div>
          </div>
        </div>
      </div>
    );
  }

  // TIP_MENU Rendering
  if (type === 'TIP_MENU') {
    const actions = message.payload?.actions || [];
    const categories = message.payload?.categories || [];
    const uncategorized = message.payload?.uncategorized || [];
    const hasCategories = categories.length > 0;

    const renderAction = (action: any, idx: number) => (
      <div key={idx} className="flex items-center justify-between px-2 py-2 rounded-lg hover:bg-white/5 transition">
        <span className="text-white/80 text-sm">{safeRender(action.description)}</span>
        <span className="px-2 py-1 rounded-full bg-indigo-600/20 text-indigo-300 text-xs font-semibold border border-indigo-500/20">
          {safeRender(action.amount)} tokens
        </span>
      </div>
    );
    
    return (
      <div className="my-3 px-4 animate-in fade-in slide-in-from-bottom-2 duration-300">
        <div className="rounded-xl border border-indigo-500/20 bg-gradient-to-br from-indigo-950/50 to-black/60 p-4 shadow-lg backdrop-blur-sm">
          
          <div className="text-xs uppercase tracking-wider text-white/40 mb-2">
            💎 TIP MENU
          </div>

          {hasCategories ? (
            <div>
              {categories.map((cat: any, catIdx: number) => (
                <div key={catIdx}>
                  {catIdx > 0 && <div className="border-t border-white/5 my-2"></div>}
                  <div className="text-indigo-300 text-sm font-semibold uppercase tracking-wide mt-2 mb-1">
                    {safeRender(cat.title)}
                  </div>
                  <div className="space-y-0.5">
                    {(cat.actions || []).map(renderAction)}
                  </div>
                </div>
              ))}
              {uncategorized.length > 0 && (
                <>
                  <div className="border-t border-white/5 my-2"></div>
                  <div className="space-y-0.5">
                    {uncategorized.map(renderAction)}
                  </div>
                </>
              )}
            </div>
          ) : (
            <div className="space-y-0.5">
              {actions.map(renderAction)}
            </div>
          )}
          
          <div className="text-xs text-white/30 italic mt-2">
            Tip the exact amount to trigger an action
          </div>
        </div>
      </div>
    );
  }

  // Special TIP rendering as requested
  if (type === 'TIP') {
    return (
      <div 
        ref={elementRef}
        className="tip-message neon-tip chat-bubble chat-tip my-1.5 animate-tip-pop duration-500 flex flex-col gap-1"
      >
        <div className="flex items-center gap-2">
          <span className="text-sm">💎</span>
          <strong className="chat-username text-indigo-300" style={{ fontSize: `${fontSize}px` }}>{safeRender(displayName)}</strong>
          <span style={{ fontSize: `${fontSize}px` }} className="text-white/80">tipped <span className="neon-gold font-black">{safeRender(amount)} tokens!</span></span>
        </div>
        {content && (
          <div style={{ fontSize: `${Math.max(10, fontSize - 2)}px` }} className="mt-1 italic text-white/50 pl-2 border-l border-white/10">
            {renderContent()}
          </div>
        )}
      </div>
    );
  }

  return (
    <div 
      ref={elementRef}
      className={`chat-bubble group my-1.5 flex flex-col gap-2 transition-all duration-300 ${
        isTip ? 'chat-tip' : ''
      } ${isBot ? 'chat-bot chat-bot-scanline' : ''} ${isPositive ? 'border-emerald-500/30 bg-emerald-500/5 shadow-[0_0_15px_rgba(16,185,129,0.1)]' : ''} ${isTip ? 'animate-tip-pop' : 'animate-in fade-in slide-in-from-bottom-2'} duration-500`}
    >
      {/* Header: Username & Time */}
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-2 overflow-hidden">
          {isOwner && !isBot && (
            <span className="text-[8px] font-black uppercase tracking-tighter px-1.5 py-0.5 rounded-sm bg-indigo-500/25 text-indigo-300 border border-indigo-500/40">
              ⭐ HOST
            </span>
          )}
          {isCreatorType && !isBot && (
            <span className="text-[8px] font-black uppercase tracking-tighter px-1.5 py-0.5 rounded-sm bg-white/10 text-white/50 border border-white/10">
              CREATOR
            </span>
          )}
          {isAdminType && !isBot && (
            <span className="text-[8px] font-black uppercase tracking-tighter px-1.5 py-0.5 rounded-sm bg-red-500/15 text-red-400 border border-red-500/30">
              ADMIN
            </span>
          )}
          {!senderType && role && role !== 'CREATOR' && (
            <span className={`text-[8px] font-black uppercase tracking-tighter px-1.5 py-0.5 rounded-sm ${
              role === 'MODERATOR' ? 'bg-green-500/20 text-green-400 border border-green-500/20' : 'bg-white/10 text-white/40'
            }`}>
              {role === 'MODERATOR' ? '🛡 MOD' : role}
            </span>
          )}
          {isLegacyCreator && (
            <span className="text-[8px] font-black uppercase tracking-tighter px-1.5 py-0.5 rounded-sm bg-white text-black">
              CREATOR
            </span>
          )}
          {isFollower && !isBot && (
            <span className="text-[8px] font-bold px-1 py-0.5 rounded-sm bg-amber-500/15 text-amber-400 border border-amber-500/20">★</span>
          )}
          <span style={{ fontSize: `${fontSize}px` }} className={`font-bold chat-username truncate ${isTip ? 'text-indigo-300' : isBot ? 'chat-username-bot' : 'text-white/90'}`}>
            {isBot && <span className="mr-1.5 animate-bounce inline-block">🤖</span>}
            {safeRender(displayName)}
          </span>
        </div>
        <span className="text-[9px] text-white/20 font-bold tabular-nums">
          {safeRender(formattedTime)}
        </span>
      </div>

      {/* Content Body */}
      <div className="flex flex-col gap-1.5">
        {isTip && (
          <div className="flex items-center gap-2 mb-0.5">
            <div className="flex items-center gap-1">
              <span className="text-xs">🎁</span>
              <span className="text-[10px] font-black uppercase tracking-widest text-indigo-400">Gift Received</span>
            </div>
            <div className="h-[1px] flex-1 bg-indigo-500/20" />
          </div>
        )}
        
        <div style={{ fontSize: `${fontSize}px` }} className={`leading-relaxed ${isTip ? 'text-white' : 'text-white/80'}`}>
          {isTip ? (
            <div className="flex flex-col gap-1">
              <p>
                Sent {giftName ? <span className="font-black text-yellow-500">{safeRender(giftName)} </span> : ''}
                (<span className="font-black neon-gold">{safeRender(amount)} tokens</span>)! 💖
              </p>
              {content && (
                <div style={{ fontSize: `${Math.max(10, fontSize - 2)}px` }} className="mt-1 italic text-white/50 pl-2 border-l border-white/10">
                  {renderContent()}
                </div>
              )}
            </div>
          ) : (
            renderContent()
          )}
        </div>
      </div>
    </div>
  );
};

export default React.memo(ChatMessage);
