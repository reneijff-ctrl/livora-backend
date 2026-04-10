import React, { useState, useEffect } from 'react';
import { Link } from 'react-router-dom';
import { safeRender } from '@/utils/safeRender';
import { useWs } from '../../ws/WsContext';

interface ActivityEvent {
  id: string | number;
  type: string;
  description: string;
  timestamp: string | Date;
}

interface AdminActivityFeedProps {
  events: ActivityEvent[];
}

const AdminActivityFeed: React.FC<AdminActivityFeedProps> = React.memo(({ events }) => {
  const { subscribe, connected } = useWs();
  const [localEvents, setLocalEvents] = useState<ActivityEvent[]>(events);

  useEffect(() => {
    // Sync with polling events from parent
    if (events && events.length > 0) {
      setLocalEvents(prev => {
        // Filter out real-time events that have now been captured by the polled results
        // We match by description and timestamp as real-time events don't have IDs
        const uniqueRealtimeEvents = prev.filter(p => {
          if (typeof p.id === 'string' && p.id.startsWith('rt-')) {
            const alreadyInPolled = events.some(e => 
              e.description === p.description && 
              new Date(e.timestamp).getTime() === new Date(p.timestamp).getTime()
            );
            return !alreadyInPolled;
          }
          return false;
        });

        // Prepend any pending unique real-time events to the polled results
        const combined = [...uniqueRealtimeEvents, ...events];
        return combined.slice(0, 10);
      });
    }
  }, [events]);

  useEffect(() => {
    if (!connected) return;

    const unsub = subscribe('/exchange/amq.topic/admin.events', (msg) => {
      try {
        const event = JSON.parse(msg.body);
        const newEvent: ActivityEvent = {
          id: `rt-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`,
          type: event.type,
          description: event.message,
          timestamp: event.timestamp
        };

        setLocalEvents(prev => {
          const isDuplicate = prev.some(e => e.description === newEvent.description && 
            new Date(e.timestamp).getTime() === new Date(newEvent.timestamp).getTime());
          
          if (isDuplicate) return prev;
          
          return [newEvent, ...prev].slice(0, 10);
        });
      } catch (e) {
        console.error('Failed to parse admin event', e);
      }
    });

    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [subscribe, connected]);

  const formatTimestamp = (ts: string | Date) => {
    const date = typeof ts === 'string' ? new Date(ts) : ts;
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' });
  };

  const getEventLink = (type: string) => {
    switch (type) {
      case 'USER_REGISTRATION':
        return '/admin/users';
      case 'CREATOR_APPLICATION':
        return '/admin/applications';
      case 'REPORT_CREATED':
        return '/admin/reports';
      case 'STREAM_STARTED':
        return '/admin/streams';
      case 'PAYMENT_COMPLETED':
        return '/admin/payments';
      default:
        return null;
    }
  };

  const typeIcons: Record<string, string> = {
    'USER_REGISTRATION': '👤',
    'STREAM_STARTED': '🎥',
    'PAYMENT_COMPLETED': '💰',
    'REPORT_CREATED': '🚨',
    'CREATOR_APPLICATION': '📝',
  };

  if (!localEvents || localEvents.length === 0) {
    return (
      <div className="bg-zinc-900 rounded-xl border border-purple-500/30 shadow-xl overflow-hidden h-full flex items-center justify-center p-8">
        <p className="text-zinc-500 text-sm">No recent activity</p>
      </div>
    );
  }

  return (
    <div className="bg-zinc-900 rounded-xl border border-purple-500/30 shadow-xl overflow-hidden h-full flex flex-col">
      <div className="p-4 border-b border-zinc-800 flex justify-between items-center bg-zinc-900/50">
        <h3 className="text-white font-bold uppercase text-xs tracking-widest">Platform Activity</h3>
        <span className="text-zinc-500 text-[10px] font-mono uppercase">Live Feed</span>
      </div>
      
      <div className="overflow-y-auto max-h-[400px] divide-y divide-zinc-800/50 custom-scrollbar">
        {localEvents.slice(0, 10).map((event) => {
          const link = getEventLink(event.type);
          const icon = typeIcons[event.type] || '🔔';
          
          const content = (
            <div className={`p-3 transition-all duration-200 ${link ? 'hover:bg-zinc-800/50 cursor-pointer group' : ''}`}>
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-lg bg-zinc-800/50 flex items-center justify-center text-xl shrink-0 group-hover:bg-zinc-700/50 transition-colors">
                  {icon}
                </div>
                
                <div className="min-w-0 flex-1">
                  <div className="flex items-center gap-2 mb-1">
                    <span className={`text-[9px] px-2 py-0.5 rounded-full font-bold uppercase tracking-wider ${
                      event.type === 'PAYMENT_COMPLETED' ? 'bg-green-500/10 text-green-500' :
                      event.type === 'REPORT_CREATED' ? 'bg-red-500/10 text-red-500' :
                      event.type === 'STREAM_STARTED' ? 'bg-blue-500/10 text-blue-500' :
                      event.type === 'CREATOR_APPLICATION' ? 'bg-orange-500/10 text-orange-500' :
                      event.type === 'USER_REGISTRATION' ? 'bg-indigo-500/10 text-indigo-500' :
                      'bg-zinc-700 text-zinc-400'
                    }`}>
                      {safeRender(event.type.replace(/_/g, ' '))}
                    </span>
                    <span className="text-zinc-600 text-[10px] font-mono ml-auto">
                      {safeRender(formatTimestamp(event.timestamp))}
                    </span>
                  </div>
                  <p className={`text-zinc-300 text-sm truncate transition-colors ${link ? 'group-hover:text-white' : ''}`}>
                    {safeRender(event.description)}
                  </p>
                </div>
              </div>
            </div>
          );

          if (link) {
            return (
              <Link key={event.id} to={link} className="block no-underline">
                {content}
              </Link>
            );
          }

          return <div key={event.id}>{content}</div>;
        })}
      </div>

      <div className="p-3 bg-zinc-950/30 text-center border-t border-zinc-800/50">
        <button className="text-purple-400 text-[10px] font-bold uppercase tracking-widest hover:text-purple-300 transition-colors">
          View All Audit Logs
        </button>
      </div>
    </div>
  );
});

export default AdminActivityFeed;
