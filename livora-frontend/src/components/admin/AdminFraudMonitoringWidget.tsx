import React from 'react';
import { FraudDashboardMetrics } from '../../types';
import { safeRender } from '../../utils/safeRender';
import MetricCard from './MetricCard';

interface AdminFraudMonitoringWidgetProps {
  metrics?: FraudDashboardMetrics;
}

const AdminFraudMonitoringWidget: React.FC<AdminFraudMonitoringWidgetProps> = React.memo(({ metrics }) => {
  const { 
    unresolvedSignals = 0, 
    criticalSignals = 0, 
    highSignals = 0, 
    enforcementLast24h = 0,
    newAccountTippingHigh = 0,
    newAccountTippingMedium = 0,
    newAccountTipCluster = 0,
    rapidTipRepeats = 0
  } = metrics || {};

  return (
    <div className="bg-gradient-to-br from-zinc-900 to-zinc-950 border border-zinc-800/60 rounded-2xl overflow-hidden shadow-2xl mt-8">
      <div className="px-6 py-5 border-b border-zinc-800/60 flex justify-between items-center bg-zinc-900/50">
        <h3 className="text-white font-semibold text-lg flex items-center gap-2">
          <svg className="w-5 h-5 text-red-400" fill="none" stroke="currentColor" viewBox="0 0 24 24">
            <path strokeLinecap="round" strokeLinejoin="round" strokeWidth="2" d="M12 15v2m0 0v2m0-2h2m-2 0H10m10-5V7a2 2 0 00-2-2H6a2 2 0 00-2 2v3m16 0V9a2 2 0 00-2-2H6a2 2 0 00-2 2v3M4 11h16m-10 8h4a2 2 0 002-2v-3a2 2 0 00-2-2h-4a2 2 0 00-2 2v3a2 2 0 002 2z" />
          </svg>
          Fraud Monitoring
        </h3>
        {unresolvedSignals > 0 && (
          <div className="flex items-center gap-2 bg-red-500/10 border border-red-500/20 rounded-full px-3 py-1">
            <div className="w-1.5 h-1.5 bg-red-500 rounded-full animate-pulse shadow-[0_0_8px_rgba(239,68,68,0.6)]"></div>
            <span className="text-red-500 text-[10px] font-bold uppercase tracking-wider">
              {safeRender(unresolvedSignals)} Action Required
            </span>
          </div>
        )}
      </div>

      <div className="p-6">
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4 mb-8">
          <MetricCard 
            title="Unresolved Fraud Alerts" 
            value={unresolvedSignals} 
            type={unresolvedSignals > 0 ? 'warning' : 'neutral'} 
            alert={unresolvedSignals > 5}
            to="/admin/fraud/signals?resolved=false"
          />
          <MetricCard 
            title="Critical Signals" 
            value={criticalSignals} 
            type={criticalSignals > 0 ? 'danger' : 'neutral'} 
            alert={criticalSignals > 0}
            to="/admin/fraud/signals?riskLevel=CRITICAL"
          />
          <MetricCard 
            title="High Risk Signals" 
            value={highSignals} 
            type={highSignals > 0 ? 'warning' : 'neutral'} 
            to="/admin/fraud/signals?riskLevel=HIGH"
          />
          <MetricCard 
            title="Enforcement Actions (24h)" 
            value={enforcementLast24h} 
            type="neutral" 
            to="/admin/fraud/events"
          />
        </div>

        <div className="space-y-4">
          <h4 className="text-zinc-400 text-[10px] font-bold uppercase tracking-[0.2em]">Fraud Signals (Last Hour)</h4>
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3">
            <SignalRow title="NEW_ACCOUNT_TIPPING_HIGH" value={newAccountTippingHigh} risk="HIGH" />
            <SignalRow title="NEW_ACCOUNT_TIP_CLUSTER" value={newAccountTipCluster} risk="HIGH" />
            <SignalRow title="RAPID_TIP_REPEATS" value={rapidTipRepeats} risk="MEDIUM" />
            <SignalRow title="NEW_ACCOUNT_TIPPING_MEDIUM" value={newAccountTippingMedium} risk="MEDIUM" />
          </div>
        </div>
      </div>
    </div>
  );
});

const SignalRow = ({ title, value, risk }: { title: string; value: number; risk: 'HIGH' | 'MEDIUM' | 'NORMAL' }) => {
  const color = risk === 'HIGH' ? 'text-red-500' : risk === 'MEDIUM' ? 'text-orange-500' : 'text-green-500';
  const dot = risk === 'HIGH' ? '🔴' : risk === 'MEDIUM' ? '🟠' : '🟢';
  
  return (
    <div className="flex items-center justify-between p-3.5 bg-zinc-900/40 border border-zinc-800/40 rounded-xl hover:bg-zinc-800/60 transition-all duration-200 group">
      <span className="text-zinc-500 text-[11px] font-bold tracking-widest group-hover:text-zinc-300 transition-colors">{title}</span>
      <div className="flex items-center gap-5">
        <span className={`text-[10px] font-black ${color} flex items-center gap-1.5 uppercase`}>
          <span>{dot}</span> {risk} RISK
        </span>
        <span className="text-white font-mono font-bold text-lg min-w-[20px] text-right">{value}</span>
      </div>
    </div>
  );
};

export default AdminFraudMonitoringWidget;
