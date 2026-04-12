import React, { useState, useEffect } from 'react';
import { useWs } from '../../ws/WsContext';
import { AdminRealtimeEventDTO } from '../../types';
import { safeRender } from '@/utils/safeRender';
import adminService from '../../api/adminService';

interface ModerationEvent {
  id: string;
  type: string;
  message: string;
  timestamp: string | Date;
  severity?: string;
}

const AdminModerationEventsFeed: React.FC = () => {
  const { subscribe, connected } = useWs();
  const [events, setEvents] = useState<ModerationEvent[]>([]);

  // Seed feed with historical audit events on mount
  useEffect(() => {
    adminService.getRecentAuditEvents('CREATOR', 20).then((data) => {
      if (!data || data.length === 0) return;
      const historical: ModerationEvent[] = data.map((e: any) => ({
        id: `audit-${e.timestamp}-${e.creatorId}`,
        type: e.action,
        message: e.message,
        timestamp: e.timestamp,
        severity: 'INFO',
      }));
      setEvents(historical);
    });
  }, []);

  useEffect(() => {
    if (!connected) return;

    const unsub = subscribe('/exchange/amq.topic/admin.events', (msg) => {
      try {
        const event = JSON.parse(msg.body) as AdminRealtimeEventDTO;
        const relevantTypes = [
          'USER_MUTED', 
          'USER_SHADOW_MUTED', 
          'STREAM_STOPPED', 
          'FRAUD_SIGNAL_DETECTED',
          'REPORT_FILED',
          'VIEWER_SPIKE_DETECTED',
          'CHAT_SPAM_DETECTED'
        ];
        
        const type = event.eventType || event.type;
        if (!relevantTypes.includes(type)) return;

        const newEvent: ModerationEvent = {
          id: `mod-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
          type: type,
          message: event.message,
          timestamp: event.timestamp,
          severity: event.severity
        };

        setEvents(prev => {
          const isDuplicate = prev.some(e => 
            e.message === newEvent.message && 
            new Date(e.timestamp).getTime() === new Date(newEvent.timestamp).getTime()
          );
          
          if (isDuplicate) return prev;
          
          return [newEvent, ...prev].slice(0, 20);
        });
      } catch (e) {
        console.error('Failed to parse admin event', e);
      }
    });

    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [subscribe, connected]);

  const getEventStyle = (event: ModerationEvent) => {
    switch (event.severity) {
      case 'CRITICAL':
        return { dot: '🔴' };
      case 'WARNING':
        return { dot: '🟠' };
      case 'INFO':
      default:
        return { dot: '🟢' };
    }
  };

  const formatTimestamp = (ts: string | Date) => {
    try {
      const date = typeof ts === 'string' ? new Date(ts) : ts;
      return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
    } catch (e) {
      return '00:00:00';
    }
  };

  return (
    <div className="bg-zinc-900 rounded-xl border border-zinc-800 shadow-xl overflow-hidden h-full flex flex-col">
      <div className="p-4 border-b border-zinc-800 flex justify-between items-center bg-zinc-900/50">
        <h3 className="text-white font-bold uppercase text-xs tracking-widest">Moderation Feed</h3>
        <span className="flex items-center gap-1.5">
          <span className="w-2 h-2 bg-green-500 rounded-full animate-pulse"></span>
          <span className="text-zinc-500 text-[10px] font-mono uppercase">Live</span>
        </span>
      </div>

      <div className="flex-1 overflow-y-auto divide-y divide-zinc-800/50 custom-scrollbar min-h-[300px]">
        {events.length === 0 ? (
          <div className="h-full flex items-center justify-center p-8">
            <p className="text-zinc-500 text-sm italic">No recent moderation events</p>
          </div>
        ) : (
          events.map((event) => {
            const { dot } = getEventStyle(event);
            return (
              <div key={event.id} className="p-3 hover:bg-zinc-800/30 transition-colors border-b border-zinc-800/40 last:border-0">
                <div className="flex items-center gap-3">
                  <span className="text-sm shrink-0">{dot}</span>
                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <p className="text-zinc-300 text-[13px] leading-tight font-medium truncate">
                        {safeRender(event.message)}
                      </p>
                      <span className="text-zinc-600 text-[10px] font-mono shrink-0">
                        {formatTimestamp(event.timestamp)}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>
      
      <div className="p-2 bg-zinc-950/20 text-center border-t border-zinc-800/30">
        <span className="text-zinc-600 text-[9px] uppercase tracking-tighter">
          Real-time Event Stream v1.0
        </span>
      </div>
    </div>
  );
};

export default AdminModerationEventsFeed;
