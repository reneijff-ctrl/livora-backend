import React, { useEffect, useState } from 'react';
import { useWs } from '../../ws/WsContext';

interface SystemHealth {
  api: 'UP' | 'DOWN' | 'LOADING';
  db: 'UP' | 'DOWN' | 'LOADING';
  redis: 'UP' | 'DOWN' | 'LOADING';
  websocket: 'UP' | 'DOWN' | 'LOADING';
}

interface AdminSystemHealthBarProps {
  healthData: any;
  systemHealth: any;
}

const AdminSystemHealthBar: React.FC<AdminSystemHealthBarProps> = React.memo(({ healthData, systemHealth }) => {
  const { connected } = useWs();
  const [health, setHealth] = useState<SystemHealth>({
    api: 'LOADING',
    db: 'LOADING',
    redis: 'LOADING',
    websocket: 'LOADING'
  });

  useEffect(() => {
    if (healthData) {
      setHealth(prev => ({
        ...prev,
        api: healthData.status === 'UP' ? 'UP' : 'DOWN',
        db: healthData.components?.db?.status === 'UP' ? 'UP' : 'DOWN',
        redis: healthData.components?.redis?.status === 'UP' ? 'UP' : 'DOWN'
      }));
    }
  }, [healthData]);

  useEffect(() => {
    setHealth(prev => ({
      ...prev,
      websocket: connected ? 'UP' : 'DOWN'
    }));
  }, [connected]);

  const getLatencyStatus = (ms: number) => {
    if (ms === 0) return 'LOADING';
    if (ms < 150) return 'UP';
    if (ms < 500) return 'YELLOW';
    return 'DOWN';
  };

  const getDbPoolStatus = (active: number, max: number) => {
    if (max === 0) return 'LOADING';
    const usage = active / max;
    if (usage < 0.6) return 'UP';
    if (usage < 0.85) return 'YELLOW';
    return 'DOWN';
  };

  const getRedisMemoryStatus = (bytes: number) => {
    if (bytes === 0) return 'LOADING';
    const mb = bytes / (1024 * 1024);
    if (mb < 128) return 'UP';
    if (mb < 512) return 'YELLOW';
    return 'DOWN';
  };

  const formatMemory = (bytes: number) => {
    const mb = bytes / (1024 * 1024);
    return `${mb.toFixed(1)} MB`;
  };

  const StatusPill: React.FC<{ 
    label: string; 
    status: 'UP' | 'DOWN' | 'YELLOW' | 'LOADING'; 
    value?: string;
    icon?: string;
  }> = ({ label, status, value, icon }) => {
    // Dynamic icon for services if not provided explicitly (like for metrics)
    const displayIcon = icon || (
      status === 'UP' ? '🟢' : 
      status === 'DOWN' ? '🔴' : 
      status === 'YELLOW' ? '🟡' : '⚪'
    );

    return (
      <div className="bg-zinc-900/40 border border-zinc-800/60 rounded-full px-4 py-1.5 flex items-center gap-2.5 transition-all hover:bg-zinc-800/60 shadow-[0_2px_10px_rgba(0,0,0,0.1)] group">
        <span className="text-sm leading-none flex items-center justify-center">
          {displayIcon}
        </span>
        <span className="text-[10px] font-bold text-zinc-500 uppercase tracking-widest group-hover:text-zinc-400 transition-colors">
          {label}
        </span>
        <span className={`text-xs font-bold leading-none ${
          status === 'LOADING' ? 'text-zinc-600' : 
          status === 'UP' ? 'text-zinc-100' : 
          status === 'YELLOW' ? 'text-amber-500' :
          'text-red-500'
        }`}>
          {value || (status === 'LOADING' ? '...' : status)}
        </span>
      </div>
    );
  };

  return (
    <div className="mb-8">
      <div className="flex flex-wrap gap-3">
        {/* Connection Status */}
        <StatusPill label="API" status={health.api} />
        <StatusPill label="DB" status={health.db} />
        <StatusPill label="Redis" status={health.redis} />
        <StatusPill label="WS" status={health.websocket} />

        {/* System Metrics */}
        {systemHealth && (
          <>
            <StatusPill 
              label="Latency" 
              status={getLatencyStatus(systemHealth.apiLatency)} 
              value={`${systemHealth.apiLatency.toFixed(0)}ms`}
              icon="⚡"
            />
            <StatusPill 
              label="DB Pool" 
              status={getDbPoolStatus(systemHealth.dbPoolActiveConnections, systemHealth.dbPoolMaxConnections)} 
              value={`${systemHealth.dbPoolActiveConnections}/${systemHealth.dbPoolMaxConnections}`}
              icon="💾"
            />
            <StatusPill 
              label="Redis Memory" 
              status={getRedisMemoryStatus(systemHealth.redisMemoryUsed)} 
              value={formatMemory(systemHealth.redisMemoryUsed)}
              icon="📦"
            />
            <StatusPill 
              label="WebSocket Sessions" 
              status="UP" 
              value={systemHealth.activeWebSocketSessions.toString()}
              icon="📡"
            />
          </>
        )}
      </div>
    </div>
  );
});

export default AdminSystemHealthBar;
