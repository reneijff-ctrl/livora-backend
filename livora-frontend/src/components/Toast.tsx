import React, { useState, useEffect, useCallback } from 'react';

interface Toast {
  id: number;
  message: string;
  type: 'info' | 'error' | 'success';
}

let toastCallback: ((message: string, type: 'info' | 'error' | 'success') => void) | null = null;

export const showToast = (message: string, type: 'info' | 'error' | 'success' = 'info') => {
  if (toastCallback) {
    toastCallback(message, type);
  }
};

export const ToastContainer: React.FC = () => {
  const [toasts, setToasts] = useState<Toast[]>([]);

  const addToast = useCallback((message: string, type: 'info' | 'error' | 'success') => {
    const id = Date.now();
    setToasts((prev) => [...prev, { id, message, type }]);
    setTimeout(() => {
      setToasts((prev) => prev.filter((t) => t.id !== id));
    }, 5000);
  }, []);

  useEffect(() => {
    toastCallback = addToast;
    return () => {
      toastCallback = null;
    };
  }, [addToast]);

  return (
    <div
      style={{
        position: 'fixed',
        bottom: '20px',
        right: '20px',
        zIndex: 9999,
        display: 'flex',
        flexDirection: 'column',
        gap: '10px',
      }}
    >
      {toasts.map((toast) => (
        <div
          key={toast.id}
          style={{
            padding: '12px 20px',
            borderRadius: '4px',
            backgroundColor: toast.type === 'error' ? '#ff4d4f' : toast.type === 'success' ? '#52c41a' : '#1890ff',
            color: 'white',
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
            minWidth: '200px',
            animation: 'fadeIn 0.3s ease-out',
          }}
        >
          {toast.message}
        </div>
      ))}
      <style>{`
        @keyframes fadeIn {
          from { opacity: 0; transform: translateY(20px); }
          to { opacity: 1; transform: translateY(0); }
        }
      `}</style>
    </div>
  );
};
