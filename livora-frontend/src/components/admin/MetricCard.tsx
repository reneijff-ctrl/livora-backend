import React from 'react';
import { Link } from 'react-router-dom';
import { safeRender } from '@/utils/safeRender';

interface MetricCardProps {
  title: string;
  value: string | number;
  to?: string;
  alert?: boolean;
  type?: 'success' | 'warning' | 'danger' | 'neutral';
}

const MetricCard: React.FC<MetricCardProps> = React.memo(({ title, value, to, alert, type }) => {
  const activeType = type || (alert ? 'danger' : 'neutral');

  const typeStyles = {
    success: 'border-green-500/30 shadow-green-500/10 from-green-500/5 hover:shadow-green-500/20',
    warning: 'border-amber-500/30 shadow-amber-500/10 from-amber-500/5 hover:shadow-amber-500/20',
    danger: 'border-red-500/30 shadow-red-500/10 from-red-500/5 hover:shadow-red-500/20',
    neutral: 'border-purple-500/30 shadow-purple-500/10 from-purple-500/5 hover:shadow-purple-500/20',
  };

  const currentStyle = typeStyles[activeType];
  const pulseClass = alert ? 'animate-pulse' : '';

  const content = (
    <div className={`
      relative overflow-hidden
      bg-zinc-900 bg-gradient-to-br ${currentStyle} to-transparent
      p-6 rounded-xl border shadow-md
      text-center transition-all duration-300
      hover:scale-[1.02] hover:shadow-lg ${pulseClass}
      ${to ? 'cursor-pointer hover:bg-zinc-800/80' : ''}
    `}>
      <div className="text-gray-400 text-xs font-bold uppercase tracking-wider mb-2 relative z-10">{safeRender(title)}</div>
      <div className="text-3xl font-bold text-white relative z-10">{safeRender(value)}</div>
    </div>
  );

  if (to) {
    return (
      <Link to={to} className="block no-underline">
        {content}
      </Link>
    );
  }

  return content;
});

export default MetricCard;
