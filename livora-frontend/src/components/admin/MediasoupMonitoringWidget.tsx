import React from 'react';
import { MediasoupStats } from '../../types';
import { safeRender } from '../../utils/safeRender';
import MetricCard from './MetricCard';

interface MediasoupMonitoringWidgetProps {
  stats: MediasoupStats;
}

const MediasoupMonitoringWidget: React.FC<MediasoupMonitoringWidgetProps> = React.memo(({ stats }) => {
  const { workers, global } = stats;

  const avgCpu = workers.length > 0
    ? workers.reduce((acc, w) => acc + w.cpuUsage, 0) / workers.length
    : 0;

  const avgMemory = workers.length > 0
    ? workers.reduce((acc, w) => acc + w.memoryUsage, 0) / workers.length
    : 0;

  return (
    <div className="bg-gradient-to-br from-zinc-900 to-zinc-950 border border-zinc-800/60 rounded-2xl overflow-hidden shadow-2xl mt-8">
      <div className="px-6 py-5 border-b border-zinc-800/60 flex justify-between items-center bg-zinc-900/50">
        <h3 className="text-white font-semibold text-lg flex items-center gap-2">
          <svg className="w-5 h-5 text-indigo-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 19v-6a2 2 0 00-2-2H5a2 2 0 00-2 2v6a2 2 0 002 2h2a2 2 0 002-2zm0 0V9a2 2 0 012-2h2a2 2 0 012 2v10m-6 0a2 2 0 002 2h2a2 2 0 002-2m0 0V5a2 2 0 012-2h2a2 2 0 012 2v14a2 2 0 01-2 2h-2a2 2 0 01-2-2z" />
          </svg>
          Mediasoup Health
        </h3>
        <div className="flex items-center gap-2 bg-indigo-500/10 border border-indigo-500/20 rounded-full px-3 py-1">
          <div className="w-1.5 h-1.5 bg-indigo-500 rounded-full animate-pulse shadow-[0_0_8px_rgba(99,102,241,0.6)]"></div>
          <span className="text-indigo-500 text-[10px] font-bold uppercase tracking-wider">
            {safeRender(workers.length)} Workers Active
          </span>
        </div>
      </div>

      <div className="p-6">
        <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-8">
          <div className="bg-zinc-900/40 border border-zinc-800/40 rounded-xl p-5 relative overflow-hidden group">
            <div className="absolute top-0 right-0 p-3 opacity-10 group-hover:opacity-20 transition-opacity">
              <svg className="w-12 h-12 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M9 3v2m6-2v2M9 19v2m6-2v2M5 9H3m2 6H3m18-6h-2m2 6h-2M7 19h10a2 2 0 002-2V7a2 2 0 00-2-2H7a2 2 0 00-2 2v10a2 2 0 002 2zM9 9h6v6H9V9z" />
              </svg>
            </div>
            <div className="text-zinc-500 text-[10px] font-bold uppercase tracking-[0.2em] mb-2">Workers CPU Average</div>
            <div className="flex items-center gap-4">
              <span className="text-3xl font-bold text-white leading-none">{safeRender(avgCpu.toFixed(1))}%</span>
              <div className="flex-1 h-2 bg-zinc-800/80 rounded-full overflow-hidden flex">
                <div 
                  className={`h-full transition-all duration-1000 ease-out shadow-[0_0_10px_rgba(0,0,0,0.5)] ${
                    avgCpu > 80 ? 'bg-gradient-to-r from-red-600 to-red-400' : 
                    avgCpu > 50 ? 'bg-gradient-to-r from-amber-600 to-amber-400' : 
                    'bg-gradient-to-r from-emerald-600 to-emerald-400'
                  }`}
                  style={{ width: `${Math.min(avgCpu, 100)}%` }}
                ></div>
              </div>
            </div>
          </div>

          <div className="bg-zinc-900/40 border border-zinc-800/40 rounded-xl p-5 relative overflow-hidden group">
            <div className="absolute top-0 right-0 p-3 opacity-10 group-hover:opacity-20 transition-opacity">
              <svg className="w-12 h-12 text-white" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M19 11H5m14 0a2 2 0 012 2v6a2 2 0 01-2 2H5a2 2 0 01-2-2v-6a2 2 0 012-2m14 0V9a2 2 0 00-2-2M5 11V9a2 2 0 012-2m0 0V5a2 2 0 012-2h6a2 2 0 012 2v2M7 7h10" />
              </svg>
            </div>
            <div className="text-zinc-500 text-[10px] font-bold uppercase tracking-[0.2em] mb-2">Workers Memory Average</div>
            <div className="flex items-center gap-4">
              <span className="text-3xl font-bold text-white leading-none">{safeRender(avgMemory.toFixed(1))} <span className="text-sm font-medium text-zinc-500">MB</span></span>
              <div className="flex-1 h-2 bg-zinc-800/80 rounded-full overflow-hidden">
                <div 
                  className="h-full bg-gradient-to-r from-blue-600 to-cyan-400 transition-all duration-1000 ease-out shadow-[0_0_10px_rgba(0,0,0,0.5)]"
                  style={{ width: '100%' }}
                ></div>
              </div>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-2 lg:grid-cols-4 gap-4">
          <MetricCard title="Routers" value={global.routers} type="neutral" />
          <MetricCard title="Transports" value={global.transports} type="success" />
          <MetricCard title="Producers" value={global.producers} type="warning" />
          <MetricCard title="Consumers" value={global.consumers} type="neutral" />
        </div>
      </div>
    </div>
  );
});

export default MediasoupMonitoringWidget;
