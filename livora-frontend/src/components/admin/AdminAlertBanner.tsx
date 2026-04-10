import React, { useState, useEffect } from 'react';
import { AdminRealtimeEventDTO } from '../../types';
import { useWs } from '../../ws/WsContext';
import { safeRender } from '../../utils/safeRender';

interface Alert extends AdminRealtimeEventDTO {
  id: string;
  displayType: string;
}

const XIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="18" y1="6" x2="6" y2="18"></line><line x1="6" y1="6" x2="18" y2="18"></line></svg>
);

const UserIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M16 21v-2a4 4 0 0 0-4-4H5a4 4 0 0 0-4 4v2"></path><circle cx="8.5" cy="7" r="4"></circle><line x1="20" y1="8" x2="20" y2="14"></line><line x1="23" y1="11" x2="17" y2="11"></line></svg>
);

const FileIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z"></path><polyline points="14 2 14 8 20 8"></polyline><line x1="16" y1="13" x2="8" y2="13"></line><line x1="16" y1="17" x2="8" y2="17"></line><polyline points="10 9 9 9 8 9"></polyline></svg>
);

const FlagIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><path d="M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z"></path><line x1="4" y1="22" x2="4" y2="15"></line></svg>
);

const RadioIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="2"></circle><path d="M16.24 7.76a6 6 0 0 1 0 8.49m-8.48-.01a6 6 0 0 1 0-8.49m11.31-2.82a10 10 0 0 1 0 14.14m-14.14 0a10 10 0 0 1 0-14.14"></path></svg>
);

const DollarIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><line x1="12" y1="1" x2="12" y2="23"></line><path d="M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"></path></svg>
);

const ZapIcon = () => (
  <svg xmlns="http://www.w3.org/2000/svg" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round"><polygon points="13 2 3 14 12 14 11 22 21 10 12 10 13 2"></polygon></svg>
);

const AdminAlertBanner: React.FC = React.memo(() => {
  const { subscribe, connected } = useWs();
  const [alerts, setAlerts] = useState<Alert[]>([]);

  useEffect(() => {
    if (!connected) return;

    const unsub = subscribe('/exchange/amq.topic/admin.events', (msg) => {
      try {
        const event = JSON.parse(msg.body) as AdminRealtimeEventDTO;
        let displayType = event.type;
        let displayMessage = event.message;

        if (event.type === 'PAYMENT_COMPLETED' && event.metadata?.amount > 500) {
          displayType = 'LARGE_PAYMENT';
          displayMessage = `💰 WHALE ALERT: ${event.message}`;
        } else if (event.type === 'STREAM_STARTED' && event.metadata?.viewerCount > 100) {
          displayType = 'HIGH_TRAFFIC';
          displayMessage = `🔥 HIGH TRAFFIC: ${event.message}`;
        }

        const id = Math.random().toString(36).substr(2, 9);
        const newAlert: Alert = { ...event, id, displayType, message: displayMessage };
        
        setAlerts(prev => [newAlert, ...prev].slice(0, 5));

        setTimeout(() => {
          setAlerts(prev => prev.filter(a => a.id !== id));
        }, 5000);
      } catch (e) {
        console.error('Failed to parse admin event', e);
      }
    });

    return () => {
      if (typeof unsub === 'function') unsub();
    };
  }, [subscribe, connected]);

  const getAlertStyle = (type: string) => {
    switch (type) {
      case 'USER_REGISTRATION':
        return {
          bg: 'bg-indigo-950/90',
          border: 'border-indigo-500',
          icon: <UserIcon />,
          iconColor: 'text-indigo-400',
          title: 'New User'
        };
      case 'CREATOR_APPLICATION':
        return {
          bg: 'bg-orange-950/90',
          border: 'border-orange-500',
          icon: <FileIcon />,
          iconColor: 'text-orange-400',
          title: 'Creator Application'
        };
      case 'REPORT_CREATED':
        return {
          bg: 'bg-red-950/90',
          border: 'border-red-500',
          icon: <FlagIcon />,
          iconColor: 'text-red-400',
          title: 'New Report'
        };
      case 'STREAM_STARTED':
        return {
          bg: 'bg-blue-950/90',
          border: 'border-blue-500',
          icon: <RadioIcon />,
          iconColor: 'text-blue-400',
          title: 'Stream Started'
        };
      case 'HIGH_TRAFFIC':
        return {
          bg: 'bg-pink-950/90',
          border: 'border-pink-500',
          icon: <ZapIcon />,
          iconColor: 'text-pink-400 animate-pulse',
          title: 'High Traffic'
        };
      case 'LARGE_PAYMENT':
        return {
          bg: 'bg-yellow-950/90',
          border: 'border-yellow-500',
          icon: <DollarIcon />,
          iconColor: 'text-yellow-400 animate-bounce',
          title: 'Whale Alert'
        };
      case 'PAYMENT_COMPLETED':
        return {
          bg: 'bg-green-950/90',
          border: 'border-green-500',
          icon: <DollarIcon />,
          iconColor: 'text-green-400',
          title: 'Payment Received'
        };
      default:
        return {
          bg: 'bg-zinc-900/90',
          border: 'border-zinc-500',
          icon: <RadioIcon />,
          iconColor: 'text-zinc-400',
          title: 'Notification'
        };
    }
  };

  if (alerts.length === 0) return null;

  return (
    <div className="fixed top-20 right-4 z-[9999] flex flex-col gap-3 w-80 pointer-events-none">
      {alerts.map((alert) => {
        const style = getAlertStyle(alert.displayType);
        return (
          <div
            key={alert.id}
            className={`pointer-events-auto flex items-start gap-3 p-4 rounded-xl border-2 shadow-[0_0_20px_rgba(0,0,0,0.5)] backdrop-blur-md transform transition-all duration-500 animate-alert-slide-in ${style.bg} ${style.border}`}
          >
            <div className={`p-2 rounded-lg bg-white/5 ${style.iconColor}`}>
              {style.icon}
            </div>
            <div className="flex-grow min-w-0">
              <h4 className="text-[10px] font-black uppercase tracking-widest text-white/50">{style.title}</h4>
              <p className="text-sm font-medium text-white mt-0.5 leading-tight">{safeRender(alert.message)}</p>
              <span className="text-[9px] text-white/30 mt-1 block font-mono">
                {safeRender(new Date(alert.timestamp).toLocaleTimeString())}
              </span>
            </div>
            <button 
              onClick={() => setAlerts(prev => prev.filter(a => a.id !== alert.id))}
              className="text-white/20 hover:text-white transition-colors p-1"
            >
              <XIcon />
            </button>
          </div>
        );
      })}
    </div>
  );
});

export default AdminAlertBanner;
