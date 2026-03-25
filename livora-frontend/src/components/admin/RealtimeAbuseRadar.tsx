import { useEffect, useRef, useState } from "react";
import { webSocketService } from "../../websocket/webSocketService";

interface AbuseEvent {
  type: string;
  streamId: string;
  creatorUsername: string;
  description: string;
  timestamp: string;
}

export default function RealtimeAbuseRadar() {
  const [events, setEvents] = useState<AbuseEvent[]>([]);
  const subscriptions = useRef<Array<() => void>>([]);

  useEffect(() => {
    const trySubscribe = () => {
      if (webSocketService.isConnected()) {
        const unsub = webSocketService.subscribeToAbuseEvents((event: AbuseEvent) => {
          setEvents((prev) => [event, ...prev.slice(0, 19)]);
        });
        if (typeof unsub === 'function') {
          subscriptions.current.push(unsub);
        }
      }
    };

    trySubscribe();
    const off = webSocketService.subscribeStateChange((connected) => {
      if (connected) trySubscribe();
    });

    return () => {
      subscriptions.current.forEach(fn => fn());
      subscriptions.current = [];
      off();
    };
  }, []);

  return (
    <div className="bg-zinc-900 p-4 rounded-xl border border-zinc-800 shadow-xl overflow-hidden flex flex-col h-full">
      <h2 className="text-lg font-semibold mb-4 flex items-center gap-2">
        <span className="text-red-500">🚨</span> Realtime Abuse Radar
      </h2>

      <div className="space-y-2 overflow-y-auto max-h-[400px] custom-scrollbar">
        {events.length === 0 ? (
          <div className="h-full flex items-center justify-center p-8">
            <p className="text-zinc-500 text-sm italic">No recent abuse events detected</p>
          </div>
        ) : (
          events.map((e, i) => (
            <div
              key={i}
              className="flex justify-between items-center text-sm bg-zinc-800/40 p-3 rounded-lg border border-zinc-800/50 hover:bg-zinc-800/60 transition-colors"
            >
              <div className="flex flex-col min-w-0">
                <span className="font-bold text-zinc-300 truncate">{e.creatorUsername}</span>
                <span className="text-zinc-500 text-xs truncate">{e.description}</span>
              </div>
              <div className="flex flex-col items-end shrink-0 ml-4">
                <span className={`text-[10px] px-1.5 py-0.5 rounded font-bold uppercase tracking-wider mb-1 ${
                  e.type === 'VIEWER_SPIKE' ? 'bg-orange-500/10 text-orange-500' :
                  e.type === 'TIP_CLUSTER' ? 'bg-red-500/10 text-red-500' :
                  'bg-zinc-700 text-zinc-400'
                }`}>
                  {e.type.replace('_', ' ')}
                </span>
                <span className="text-zinc-600 text-[10px] font-mono">
                  {new Date(e.timestamp).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit', second: '2-digit' })}
                </span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
}
