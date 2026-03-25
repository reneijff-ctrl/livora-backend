import { useEffect, useRef, useState } from "react";
import { webSocketService } from "../../websocket/webSocketService";
import apiClient from "../../api/apiClient";
import { safeRender } from "../../utils/safeRender";

interface StreamRisk {
    streamId: string;
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
    const subscriptions = useRef<Array<() => void>>([]);

    useEffect(() => {
        const fetchRisks = async () => {
            try {
                const res = await apiClient.get("/admin/streams/risk");
                const data = res.data;
                if (Array.isArray(data)) {
                    setStreams(data);
                } else if (data && Array.isArray(data.streams)) {
                    setStreams(data.streams);
                }
            } catch (err) {
                console.error("Failed to fetch stream risks", err);
            }
        };

        fetchRisks();
        const pollInterval = setInterval(fetchRisks, 15000);

        // WebSocket subscription for live updates
        const handleUpdate = (message: any) => {
            try {
                const data = JSON.parse(message.body);
                if (Array.isArray(data)) {
                    setStreams(data);
                }
            } catch (err) {
                console.error("WS: Failed to parse stream risk update", err);
            }
        };

        const trySubscribe = () => {
            if (webSocketService.isConnected()) {
                const unsub = webSocketService.subscribe("/exchange/amq.topic/admin.streams", handleUpdate);
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
            clearInterval(pollInterval);
            subscriptions.current.forEach(fn => fn());
            subscriptions.current = [];
            off();
        };
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

    const activeStreams = Array.isArray(streams) ? streams : [];

    return (
        <div className="bg-zinc-900 p-4 rounded-xl">
            <h2 className="text-lg font-semibold mb-4">
                🚨 Live Stream Risk Monitor
            </h2>

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
                {activeStreams.length > 0 ? activeStreams.map((s) => (
                    <tr key={s.streamId} className="border-b border-zinc-800">
                        <td className="py-2">{safeRender(s.creatorUsername)}</td>
                        <td>{safeRender(s.viewerCount)}</td>
                        <td className={riskColor(s.riskLevel)}>
                            {safeRender(s.riskLevel)}
                        </td>
                        <td>
                            {s.viewerSpike && "👀 "}
                            {s.suspiciousTips && "💰 "}
                            {s.chatSpam && "💬 "}
                            {s.newAccountCluster && "🆕 "}
                        </td>
                    </tr>
                )) : (
                    <tr>
                        <td colSpan={4} className="py-8 text-center text-zinc-500">
                            No active streams
                        </td>
                    </tr>
                )}
                </tbody>
            </table>
        </div>
    );
}