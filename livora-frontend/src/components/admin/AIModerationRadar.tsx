import { useEffect, useState } from "react";
import { useWs } from "../../ws/WsContext";

interface ModerationDecision {
  streamId: string | number;
  riskLevel: string;
  riskScore: number;
  action: string;
  type?: string;
}

export default function AIModerationRadar() {
  const { subscribe, connected } = useWs();
  const [decisions, setDecisions] = useState<ModerationDecision[]>([]);

  useEffect(() => {
    if (!connected) return;

    const unsub = subscribe(
      "/exchange/amq.topic/admin.abuse",
      (message: any) => {
        try {
          const data = JSON.parse(message.body);
          if (data && data.type === "AI_MODERATION_DECISION") {
            setDecisions((prev) => [data, ...prev.slice(0, 20)]);
          }
        } catch (e) {
          console.error("WS: Failed to parse moderation decision", e);
        }
      }
    );

    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [subscribe, connected]);

  return (
    <div className="card">
      <h2 className="text-lg font-semibold mb-4">
        🤖 AI Moderation Engine
      </h2>

      {decisions.map((d, i) => (
        <div key={i} className="p-2 border-b border-zinc-800">
          Stream {d.streamId} | Risk {d.riskLevel} | Action {d.action}
        </div>
      ))}
    </div>
  );
}
