import { useEffect, useState } from "react";
import adminService from "../../api/adminService";

interface StreamRisk {
  streamId: any;
  creatorUsername: string;
  viewerCount: number;
  riskLevel: "LOW" | "MEDIUM" | "HIGH" | "CRITICAL";
  viewerSpike: boolean;
  suspiciousTips: boolean;
  chatSpam: boolean;
  newAccountCluster: boolean;
}

export default function LiveStreamRiskMonitor() {
  const [streams, setStreams] = useState<StreamRisk[]>([]);

  useEffect(() => {
    adminService.getStreamRisk().then(setStreams);
  }, []);

  const riskColor = (risk: string) => {
    switch (risk) {
      case "LOW":
        return "text-green-400";
      case "MEDIUM":
        return "text-yellow-400";
      case "HIGH":
        return "text-orange-400";
      case "CRITICAL":
        return "text-red-500";
      default:
        return "text-gray-400";
    }
  };

  return (
    <div className="card">
      <h2 className="text-lg font-semibold mb-4">🚨 Live Stream Risk Monitor</h2>

      <table className="w-full text-sm">
        <thead>
          <tr className="text-left border-b border-zinc-700">
            <th>Creator</th>
            <th>Viewers</th>
            <th>Risk</th>
            <th>Signals</th>
          </tr>
        </thead>

        <tbody>
          {Array.isArray(streams) && streams.map((s) => (
            <tr key={s.streamId} className="border-b border-zinc-800">
              <td>{s.creatorUsername}</td>

              <td>{s.viewerCount}</td>

              <td className={riskColor(s.riskLevel)}>
                {s.riskLevel}
              </td>

              <td>
                {s.viewerSpike && "👀 "}
                {s.suspiciousTips && "💰 "}
                {s.chatSpam && "💬 "}
                {s.newAccountCluster && "🆕 "}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
