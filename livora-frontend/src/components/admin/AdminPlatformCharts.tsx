import React from 'react';
import { LineChart, Line, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Legend } from 'recharts';

interface AdminPlatformChartsProps {
  chartsData: any;
}

const AdminPlatformCharts: React.FC<AdminPlatformChartsProps> = React.memo(({ chartsData }) => {
  if (!chartsData) {
    return <div className="text-zinc-500 animate-pulse text-sm mt-8">Loading historical charts...</div>;
  }

  return (
    <div className="space-y-8 mt-8">
      <div className="grid grid-cols-1 xl:grid-cols-2 gap-8">
        {/* New Users (7 days) */}
        <div className="bg-zinc-900/50 p-6 rounded-2xl border border-zinc-800 shadow-2xl backdrop-blur-sm transition-all duration-300 hover:border-zinc-700">
          <h3 className="text-white font-semibold mb-4 text-left flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-purple-500 animate-pulse"></span>
            New Users (Last 7 Days)
          </h3>
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartsData.dailyUsersLast7Days} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                <defs>
                  <linearGradient id="userGradient" x1="0" y1="0" x2="1" y2="0">
                    <stop offset="0%" stopColor="#a78bfa" />
                    <stop offset="100%" stopColor="#8b5cf6" />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#3f3f46" vertical={false} opacity={0.4} />
                <XAxis dataKey="label" stroke="#71717a" tick={{ fontSize: 10, fill: '#71717a' }} axisLine={false} tickLine={false} dy={10} />
                <YAxis stroke="#71717a" tick={{ fontSize: 10, fill: '#71717a' }} axisLine={false} tickLine={false} />
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: 'rgba(24, 24, 27, 0.95)', 
                    border: '1px solid rgba(63, 63, 70, 1)', 
                    borderRadius: '12px',
                    boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)',
                    color: '#fff',
                    padding: '8px 12px',
                    fontSize: '12px'
                  }}
                  itemStyle={{ color: '#a78bfa', fontWeight: 'bold' }}
                  cursor={{ stroke: '#3f3f46', strokeWidth: 1 }}
                />
                <Legend iconType="circle" />
                <Line 
                  type="monotone" 
                  dataKey="value" 
                  name="New Users" 
                  stroke="url(#userGradient)" 
                  strokeWidth={3} 
                  dot={{ r: 4, fill: '#18181b', stroke: '#8b5cf6', strokeWidth: 2 }} 
                  activeDot={{ r: 6, fill: '#8b5cf6', stroke: '#fff', strokeWidth: 2 }} 
                  animationDuration={1500}
                  animationEasing="ease-in-out"
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>

        {/* Revenue (7 days) */}
        <div className="bg-zinc-900/50 p-6 rounded-2xl border border-zinc-800 shadow-2xl backdrop-blur-sm transition-all duration-300 hover:border-zinc-700">
          <h3 className="text-white font-semibold mb-4 text-left flex items-center gap-2">
            <span className="w-2 h-2 rounded-full bg-emerald-500 animate-pulse"></span>
            Revenue (Last 7 Days)
          </h3>
          <div className="h-64 w-full">
            <ResponsiveContainer width="100%" height="100%">
              <LineChart data={chartsData.dailyRevenueLast7Days} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
                <defs>
                  <linearGradient id="revenueGradient" x1="0" y1="0" x2="1" y2="0">
                    <stop offset="0%" stopColor="#34d399" />
                    <stop offset="100%" stopColor="#10b981" />
                  </linearGradient>
                </defs>
                <CartesianGrid strokeDasharray="3 3" stroke="#3f3f46" vertical={false} opacity={0.4} />
                <XAxis dataKey="label" stroke="#71717a" tick={{ fontSize: 10, fill: '#71717a' }} axisLine={false} tickLine={false} dy={10} />
                <YAxis stroke="#71717a" tick={{ fontSize: 10, fill: '#71717a' }} axisLine={false} tickLine={false} />
                <Tooltip 
                  contentStyle={{ 
                    backgroundColor: 'rgba(24, 24, 27, 0.95)', 
                    border: '1px solid rgba(63, 63, 70, 1)', 
                    borderRadius: '12px',
                    boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)',
                    color: '#fff',
                    padding: '8px 12px',
                    fontSize: '12px'
                  }}
                  itemStyle={{ color: '#34d399', fontWeight: 'bold' }}
                  cursor={{ stroke: '#3f3f46', strokeWidth: 1 }}
                  formatter={(value: any) => `€${value.toFixed(2)}`}
                />
                <Legend iconType="circle" />
                <Line 
                  type="monotone" 
                  dataKey="value" 
                  name="Revenue (€)" 
                  stroke="url(#revenueGradient)" 
                  strokeWidth={3} 
                  dot={{ r: 4, fill: '#18181b', stroke: '#10b981', strokeWidth: 2 }} 
                  activeDot={{ r: 6, fill: '#10b981', stroke: '#fff', strokeWidth: 2 }} 
                  animationDuration={1500}
                  animationEasing="ease-in-out"
                />
              </LineChart>
            </ResponsiveContainer>
          </div>
        </div>
      </div>

      {/* Streams started (24 hours) */}
      <div className="bg-zinc-900/50 p-6 rounded-2xl border border-zinc-800 shadow-2xl backdrop-blur-sm transition-all duration-300 hover:border-zinc-700">
        <h3 className="text-white font-semibold mb-4 text-left flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-amber-500 animate-pulse"></span>
          Streams Started (Last 24 Hours)
        </h3>
        <div className="h-64 w-full">
          <ResponsiveContainer width="100%" height="100%">
            <LineChart data={chartsData.streamsLast24Hours} margin={{ top: 5, right: 20, bottom: 5, left: 0 }}>
              <defs>
                <linearGradient id="streamGradient" x1="0" y1="0" x2="1" y2="0">
                  <stop offset="0%" stopColor="#fbbf24" />
                  <stop offset="100%" stopColor="#f59e0b" />
                </linearGradient>
              </defs>
              <CartesianGrid strokeDasharray="3 3" stroke="#3f3f46" vertical={false} opacity={0.4} />
              <XAxis dataKey="label" stroke="#71717a" tick={{ fontSize: 10, fill: '#71717a' }} axisLine={false} tickLine={false} dy={10} />
              <YAxis stroke="#71717a" tick={{ fontSize: 10, fill: '#71717a' }} axisLine={false} tickLine={false} />
              <Tooltip 
                contentStyle={{ 
                  backgroundColor: 'rgba(24, 24, 27, 0.95)', 
                  border: '1px solid rgba(63, 63, 70, 1)', 
                  borderRadius: '12px',
                  boxShadow: '0 20px 25px -5px rgba(0, 0, 0, 0.3)',
                  color: '#fff',
                  padding: '8px 12px',
                  fontSize: '12px'
                }}
                itemStyle={{ color: '#fbbf24', fontWeight: 'bold' }}
                cursor={{ stroke: '#3f3f46', strokeWidth: 1 }}
              />
              <Legend iconType="circle" />
              <Line 
                type="monotone" 
                dataKey="value" 
                name="Streams Started" 
                stroke="url(#streamGradient)" 
                strokeWidth={3} 
                dot={{ r: 4, fill: '#18181b', stroke: '#f59e0b', strokeWidth: 2 }} 
                activeDot={{ r: 6, fill: '#f59e0b', stroke: '#fff', strokeWidth: 2 }} 
                animationDuration={1500}
                animationEasing="ease-in-out"
              />
            </LineChart>
          </ResponsiveContainer>
        </div>
      </div>
    </div>
  );
});

export default AdminPlatformCharts;
