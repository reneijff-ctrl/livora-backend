import React, { useState, useEffect } from 'react';
import webSocketService from '../websocket/webSocketService';

interface AdminEvent {
  id: number;
  type: string;
  message: string;
  timestamp: string;
}

const AdminRealtimeDashboard: React.FC = () => {
  const [events, setEvents] = useState<AdminEvent[]>([]);
  const [metrics, setMetrics] = useState<any>({});

  useEffect(() => {
    // Subscribe to admin activity
    const unsubActivity = webSocketService.subscribe('/topic/admin/activity', (msg) => {
      const data = JSON.parse(msg.body);
      const newEvent: AdminEvent = {
        id: Date.now(),
        type: data.type,
        message: data.type === 'user_join' ? `User ${data.payload.email} joined` : `User ${data.payload.email} left`,
        timestamp: new Date().toLocaleTimeString(),
      };
      setEvents((prev) => [newEvent, ...prev].slice(0, 50));
    });

    // Subscribe to admin payments
    const unsubPayments = webSocketService.subscribe('/topic/admin/payments', (msg) => {
      const data = JSON.parse(msg.body);
      const newEvent: AdminEvent = {
        id: Date.now(),
        type: 'PAYMENT',
        message: `New payment/subscription event: ${data.type}`,
        timestamp: new Date().toLocaleTimeString(),
      };
      setEvents((prev) => [newEvent, ...prev].slice(0, 50));
    });

    // Subscribe to admin metrics
    const unsubMetrics = webSocketService.subscribe('/topic/admin/metrics', (msg) => {
      const data = JSON.parse(msg.body);
      setMetrics((prev: any) => ({ ...prev, lastEvent: data.event, lastTimestamp: data.timestamp }));
    });

    return () => {
      unsubActivity();
      unsubPayments();
      unsubMetrics();
    };
  }, []);

  return (
    <div style={{ marginTop: '2rem', display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '2rem' }}>
      <div style={{ padding: '1rem', border: '1px solid #ddd', borderRadius: '8px', backgroundColor: '#fdfdfd' }}>
        <h3>Live Activity Feed</h3>
        <div style={{ height: '300px', overflowY: 'auto', fontSize: '0.9rem' }}>
          {events.length === 0 && <p style={{ color: '#999' }}>Waiting for events...</p>}
          {events.map((e) => (
            <div key={e.id} style={{ padding: '0.5rem 0', borderBottom: '1px solid #eee' }}>
              <span style={{ color: '#888', marginRight: '0.5rem' }}>[{e.timestamp}]</span>
              <span style={{ 
                fontWeight: 'bold', 
                color: e.type.includes('join') ? '#28a745' : e.type.includes('leave') ? '#dc3545' : '#6772e5' 
              }}>
                {e.type.toUpperCase()}:
              </span> {e.message}
            </div>
          ))}
        </div>
      </div>

      <div style={{ padding: '1rem', border: '1px solid #ddd', borderRadius: '8px', backgroundColor: '#fdfdfd' }}>
        <h3>Real-time Metrics</h3>
        <div style={{ display: 'flex', flexDirection: 'column', gap: '1rem' }}>
          <div style={{ padding: '1rem', backgroundColor: '#eef2ff', borderRadius: '4px' }}>
            <div style={{ fontSize: '0.8rem', color: '#666' }}>Last System Event</div>
            <div style={{ fontSize: '1.2rem', fontWeight: 'bold', color: '#6772e5' }}>{metrics.lastEvent || 'None'}</div>
            <div style={{ fontSize: '0.7rem', color: '#888' }}>{metrics.lastTimestamp ? new Date(metrics.lastTimestamp).toLocaleString() : '-'}</div>
          </div>
          <div style={{ fontSize: '0.9rem' }}>
            <p>WebSocket Status: <span style={{ color: '#28a745', fontWeight: 'bold' }}>CONNECTED</span></p>
            <p>Admin Channels: <span style={{ color: '#666' }}>3 active</span></p>
          </div>
        </div>
      </div>
    </div>
  );
};

export default AdminRealtimeDashboard;
