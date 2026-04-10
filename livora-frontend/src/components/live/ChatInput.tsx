import React, { useState, useRef, useEffect, useCallback } from 'react';
import EmojiPicker, { Theme, EmojiClickData } from 'emoji-picker-react';
import GifPicker from './GifPicker';
import { useWs } from '../../ws/WsContext';

interface ChatInputProps {
  onSend: (message: string) => void;
  disabled?: boolean;
}

/**
 * ChatInput - A specialized input component for the chat.
 * Includes an emoji picker, handles cursor-based insertion.
 */
const ChatInput: React.FC<ChatInputProps> = ({ onSend, disabled = false }) => {
  const { subscribe } = useWs();
  const [message, setMessage] = useState('');
  const [showPicker, setShowPicker] = useState(false);
  const [showGifPicker, setShowGifPicker] = useState(false);
  const [notification, setNotification] = useState<{ message: string; severity: string } | null>(null);
  const [isShaking, setIsShaking] = useState(false);
  const inputRef = useRef<HTMLInputElement>(null);
  const pickerRef = useRef<HTMLDivElement>(null);
  const gifPickerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    let unsub = () => {};

    const result = subscribe('/user/queue/moderation', (msg) => {
      try {
        const data = JSON.parse(msg.body);
        if (data.type === 'MODERATION_BLOCKED' || data.type === 'RATE_LIMIT_EXCEEDED') {
          const payload = data.payload || {};
          setNotification({
            message: payload.reason || 'Message blocked by moderation',
            severity: payload.severity || 'LOW'
          });

          if (payload.severity === 'HIGH') {
            setIsShaking(true);
          }

          if (payload.originalMessage) {
            setMessage(payload.originalMessage);
          }
        }
      } catch (e) {
        console.error('Error parsing moderation notification', e);
      }
    });
    if (typeof result === 'function') {
      unsub = result;
    }

    return () => {
      unsub();
    };
  }, [subscribe]);

  useEffect(() => {
    if (notification) {
      const timer = setTimeout(() => setNotification(null), 4000);
      return () => clearTimeout(timer);
    }
  }, [notification]);

  useEffect(() => {
    if (isShaking) {
      const timer = setTimeout(() => setIsShaking(false), 500);
      return () => clearTimeout(timer);
    }
  }, [isShaking]);

  const handleSend = useCallback(() => {
    if (!message.trim() || disabled) return;
    onSend(message);
    setMessage('');
    setShowPicker(false);
  }, [message, disabled, onSend]);

  const onEmojiClick = (emojiData: EmojiClickData) => {
    const input = inputRef.current;
    if (!input) return;

    const start = input.selectionStart || 0;
    const end = input.selectionEnd || 0;
    
    const before = message.substring(0, start);
    const after = message.substring(end);
    
    const newValue = before + emojiData.emoji + after;
    setMessage(newValue);
    
    // Position cursor after the inserted emoji
    // We use setTimeout to ensure React has updated the state and input value
    setTimeout(() => {
      input.focus();
      const newCursorPos = start + emojiData.emoji.length;
      input.setSelectionRange(newCursorPos, newCursorPos);
    }, 0);
  };

  const onGifSelect = (url: string) => {
    setMessage(url);
    setTimeout(() => {
      onSend(url);
      setMessage('');
    }, 0);
    setShowGifPicker(false);
  };

  // Close picker when clicking outside
  useEffect(() => {
    const handleClickOutside = (event: MouseEvent) => {
      if (pickerRef.current && !pickerRef.current.contains(event.target as Node)) {
        setShowPicker(false);
      }
      if (gifPickerRef.current && !gifPickerRef.current.contains(event.target as Node)) {
        setShowGifPicker(false);
      }
    };

    if (showPicker || showGifPicker) {
      document.addEventListener('mousedown', handleClickOutside);
    }
    return () => {
      document.removeEventListener('mousedown', handleClickOutside);
    };
  }, [showPicker, showGifPicker]);

  return (
    <div className="p-4 border-t border-[#16161D] relative">
      {/* Moderation Notification Bubble */}
      {notification && (
        <div className="absolute bottom-full left-4 right-4 mb-2 z-50 animate-in fade-in slide-in-from-bottom-2 duration-300">
          <div className="glass-red px-4 py-2 rounded-xl border border-red-500/30 flex items-center gap-2 shadow-lg">
            <span className="text-red-400 text-sm">⚠️</span>
            <p className="text-[11px] font-bold text-red-200 tracking-tight leading-tight">
              {notification.message}
            </p>
          </div>
        </div>
      )}

      {/* Emoji Picker Overlay */}
      {showPicker && (
        <div 
          ref={pickerRef}
          className="absolute bottom-full left-4 mb-2 z-50 animate-in fade-in slide-in-from-bottom-2 duration-200 shadow-2xl"
        >
          <EmojiPicker 
            theme={Theme.DARK}
            onEmojiClick={onEmojiClick}
            lazyLoadEmojis={true}
            searchDisabled={false}
            skinTonesDisabled={true}
            width={320}
            height={400}
          />
        </div>
      )}
      
      {/* Gif Picker Overlay */}
      {showGifPicker && (
        <div 
          ref={gifPickerRef}
          className="absolute bottom-full left-4 mb-2 z-50 shadow-2xl"
        >
          <GifPicker 
            onSelect={onGifSelect}
            onClose={() => setShowGifPicker(false)}
          />
        </div>
      )}
      
      <div className="flex gap-2">
        {/* Toggle Button */}
        <div className="flex gap-1">
          <button
            type="button"
            onClick={() => {
              setShowPicker(!showPicker);
              setShowGifPicker(false);
            }}
            disabled={disabled}
            className="px-3 py-2.5 rounded-xl bg-[#08080A] border border-[#16161D] hover:bg-white/5 transition-colors text-lg flex items-center justify-center disabled:opacity-50 active:scale-95"
            title="Add Emoji"
          >
            😀
          </button>
          
          <button
            type="button"
            onClick={() => {
              setShowGifPicker(!showGifPicker);
              setShowPicker(false);
            }}
            disabled={disabled}
            className="px-3 py-2.5 rounded-xl bg-[#08080A] border border-[#16161D] hover:bg-white/5 transition-colors text-[10px] font-bold text-white/50 flex items-center justify-center disabled:opacity-50 active:scale-95 uppercase tracking-tighter"
            title="Add GIF"
          >
            GIF
          </button>
        </div>

        {/* Text Input */}
        <input
          ref={inputRef}
          type="text"
          value={message}
          onChange={(e) => setMessage(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !disabled) {
              handleSend();
            }
          }}
          placeholder={disabled ? "Chat is disabled" : "Say something..."}
          disabled={disabled}
          className={`flex-1 px-4 py-2.5 rounded-xl text-xs focus:ring-1 focus:ring-zinc-400 transition-all outline-none border bg-[#08080A] border-[#16161D] text-white placeholder-zinc-600 ${disabled ? 'opacity-50 cursor-not-allowed' : ''} ${isShaking ? 'input-shake' : ''}`}
        />
        
        {/* Send Button */}
        <button
          onClick={handleSend}
          disabled={!message.trim() || disabled}
          className={`px-4 py-2.5 rounded-xl font-bold text-xs transition active:scale-95 disabled:opacity-30 bg-white text-black hover:bg-zinc-200`}
        >
          Send
        </button>
      </div>
    </div>
  );
};

export default React.memo(ChatInput);
