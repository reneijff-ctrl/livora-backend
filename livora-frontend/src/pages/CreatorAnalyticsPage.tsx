import React, { useEffect, useState } from 'react';
import CreatorSidebar from '../components/CreatorSidebar';
import SEO from '../components/SEO';
import { getCreatorDashboard, CreatorDashboard } from '../api/dashboardApi';
import creatorEarningsService from '../api/creatorEarningsService';

interface DailyAnalytics {
    date: string;
    earnings: number;
    viewers: number;
    subscriptions: number;
    returningViewers: number;
    avgSessionDuration: number;
    messagesPerViewer: number;
}

const CreatorAnalyticsPage: React.FC = () => {
    const [dashboard, setDashboard] = useState<CreatorDashboard | null>(null);
    const [dailyAnalytics, setDailyAnalytics] = useState<DailyAnalytics[]>([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);

    useEffect(() => {
        const fetchData = async () => {
            try {
                setLoading(true);
                const [dashboardData, analyticsData] = await Promise.all([
                    getCreatorDashboard(),
                    creatorEarningsService.getDailyAnalytics(30),
                ]);
                setDashboard(dashboardData);
                setDailyAnalytics(Array.isArray(analyticsData) ? analyticsData : []);
            } catch (err: any) {
                setError(err?.message ?? 'Failed to load analytics');
            } finally {
                setLoading(false);
            }
        };
        fetchData();
    }, []);

    // Derived metrics
    const totalEarnings = dashboard?.totalEarnings ?? 0;
    const totalSubscribers = dashboard?.totalSubscribers ?? 0;
    const totalFollowers = dailyAnalytics.reduce((sum, d) => sum + (d.subscriptions ?? 0), 0);
    const totalViewers7d = dailyAnalytics.slice(-7).reduce((sum, d) => sum + (d.viewers ?? 0), 0);

    // Earnings trend percentage (last 15 days vs previous 15 days)
    const recentEarnings = dailyAnalytics.slice(-15).reduce((sum, d) => sum + (d.earnings ?? 0), 0);
    const previousEarnings = dailyAnalytics.slice(0, 15).reduce((sum, d) => sum + (d.earnings ?? 0), 0);
    const earningsTrend = previousEarnings > 0
        ? (((recentEarnings - previousEarnings) / previousEarnings) * 100).toFixed(1)
        : '0.0';

    // Weekly subscriber growth (split 30 days into 4 weeks)
    const weeklyGrowth = [0, 1, 2, 3].map(weekIndex => {
        const start = weekIndex * 7;
        const end = Math.min(start + 7, dailyAnalytics.length);
        const weekData = dailyAnalytics.slice(start, end);
        return {
            label: `Week ${weekIndex + 1}`,
            value: weekData.reduce((sum, d) => sum + (d.subscriptions ?? 0), 0),
        };
    });
    const maxWeeklyValue = Math.max(...weeklyGrowth.map(w => w.value), 1);

    // Engagement metrics
    const last7d = dailyAnalytics.slice(-7);
    const avgSessionSeconds = last7d.length > 0
        ? last7d.reduce((sum, d) => sum + (d.avgSessionDuration ?? 0), 0) / last7d.length
        : 0;
    const avgSessionMinutes = Math.floor(avgSessionSeconds / 60);
    const avgSessionRemSeconds = Math.floor(avgSessionSeconds % 60);
    const avgMessages = last7d.length > 0
        ? (last7d.reduce((sum, d) => sum + (d.messagesPerViewer ?? 0), 0) / last7d.length).toFixed(1)
        : '0.0';
    const returningRate = last7d.length > 0
        ? (() => {
            const totalV = last7d.reduce((s, d) => s + (d.viewers ?? 0), 0);
            const totalR = last7d.reduce((s, d) => s + (d.returningViewers ?? 0), 0);
            return totalV > 0 ? Math.round((totalR / totalV) * 100) : 0;
        })()
        : 0;

    // Chart bar heights
    const maxEarnings = Math.max(...dailyAnalytics.map(d => d.earnings ?? 0), 1);

    const metrics = [
        { title: 'Total Earnings', value: `€${totalEarnings.toLocaleString(undefined, { minimumFractionDigits: 2, maximumFractionDigits: 2 })}`, subValue: `${Number(earningsTrend) >= 0 ? '+' : ''}${earningsTrend}% vs prev 15d`, icon: '💰' },
        { title: 'Total Subscribers', value: totalSubscribers.toLocaleString(), subValue: `+${totalFollowers} this month`, icon: '💎' },
        { title: 'Post Views (7d)', value: totalViewers7d.toLocaleString(), subValue: 'Unique viewers', icon: '👁️' },
        { title: 'Content Count', value: (dashboard?.contentCount ?? 0).toLocaleString(), subValue: `${dashboard?.activeStreams ?? 0} active streams`, icon: '📺' },
    ];

    return (
        <div style={styles.layout}>
            <SEO 
                title="Creator Analytics" 
                description="Monitor your growth, earnings and audience engagement on Livora."
            />
            <CreatorSidebar />
            <div className="flex-1 p-4 md:p-8 overflow-y-auto text-zinc-100">
                <header className="mb-8">
                    <h1 className="text-3xl font-bold text-white">Creator Analytics</h1>
                    <p className="text-zinc-500 mt-2">Track your performance and audience growth over time.</p>
                </header>

                {loading && (
                    <div className="flex items-center justify-center h-64">
                        <div className="animate-spin rounded-full h-8 w-8 border-t-2 border-indigo-500"></div>
                    </div>
                )}

                {error && !loading && (
                    <div className="bg-red-500/10 border border-red-500/20 rounded-2xl p-6 text-red-400 text-center">
                        {error}
                    </div>
                )}

                {!loading && !error && (
                    <>
                        {/* Summary Metrics */}
                        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                            {metrics.map((metric, index) => (
                                <div key={index} className="bg-[#0F0F14] p-6 rounded-2xl border border-[#16161D] shadow-[0_20px_60px_rgba(0,0,0,0.6)] transition-all hover:scale-[1.02]">
                                    <div className="flex justify-between items-start mb-4">
                                        <span className="text-2xl">{metric.icon}</span>
                                        <h3 className="text-xs font-bold text-zinc-500 uppercase tracking-widest">{metric.title}</h3>
                                    </div>
                                    <p className="text-3xl font-bold text-white">{metric.value}</p>
                                    <p className="text-xs mt-2 text-indigo-400 font-semibold">{metric.subValue}</p>
                                </div>
                            ))}
                        </div>

                        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8 mb-8">
                            {/* Earnings Chart */}
                            <div className="bg-[#0F0F14] p-6 rounded-2xl border border-[#16161D] shadow-[0_20px_60px_rgba(0,0,0,0.6)]">
                                <div className="flex justify-between items-center mb-6">
                                    <h2 className="text-xl font-bold text-white">Earnings Trend (30 Days)</h2>
                                    <span className={`text-xs px-2 py-1 rounded-full font-bold border ${
                                        Number(earningsTrend) >= 0 
                                            ? 'bg-green-500/10 text-green-500 border-green-500/20' 
                                            : 'bg-red-500/10 text-red-500 border-red-500/20'
                                    }`}>
                                        {Number(earningsTrend) >= 0 ? '+' : ''}{earningsTrend}% {Number(earningsTrend) >= 0 ? '📈' : '📉'}
                                    </span>
                                </div>
                                <div className="h-64 w-full flex items-end justify-between px-2 pt-4">
                                    {dailyAnalytics.length > 0 ? dailyAnalytics.map((data, i) => (
                                        <div 
                                            key={i} 
                                            className="w-1.5 bg-indigo-600 rounded-t-sm hover:bg-indigo-500 transition-colors group relative"
                                            style={{ height: `${Math.max(((data.earnings ?? 0) / maxEarnings) * 100, 2)}%` }}
                                        >
                                            <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:block bg-[#08080A] text-white text-[10px] py-1 px-2 rounded border border-[#16161D] whitespace-nowrap z-10 shadow-2xl">
                                                {data.date}: €{(data.earnings ?? 0).toFixed(2)}
                                            </div>
                                        </div>
                                    )) : (
                                        <div className="flex items-center justify-center w-full h-full text-zinc-500 text-sm">
                                            No earnings data yet
                                        </div>
                                    )}
                                </div>
                                <div className="flex justify-between mt-6 pt-4 border-t border-[#16161D] text-[10px] text-zinc-500 font-bold uppercase tracking-widest">
                                    <span>30 days ago</span>
                                    <span>15 days ago</span>
                                    <span>Today</span>
                                </div>
                            </div>

                            {/* Subscriber Growth Chart */}
                            <div className="bg-[#0F0F14] p-6 rounded-2xl border border-[#16161D] shadow-[0_20px_60px_rgba(0,0,0,0.6)]">
                                <div className="flex justify-between items-center mb-6">
                                    <h2 className="text-xl font-bold text-white">Subscriber Growth</h2>
                                    <span className="text-xs px-2 py-1 bg-indigo-500/10 text-indigo-400 rounded-full font-bold border border-indigo-500/20">
                                        +{totalFollowers} this month
                                    </span>
                                </div>
                                <div className="h-64 flex flex-col justify-around">
                                    {weeklyGrowth.map((item, i) => (
                                        <div key={i} className="space-y-2">
                                            <div className="flex justify-between text-sm">
                                                <span className="text-zinc-400 font-medium">{item.label}</span>
                                                <span className="font-bold text-white">+{item.value}</span>
                                            </div>
                                            <div className="w-full bg-white/5 h-2.5 rounded-full overflow-hidden">
                                                <div 
                                                    className="h-full bg-indigo-600 rounded-full transition-all duration-500 ease-out" 
                                                    style={{ width: `${(item.value / maxWeeklyValue) * 100}%` }}
                                                ></div>
                                            </div>
                                        </div>
                                    ))}
                                    <div className="mt-4 p-4 bg-white/5 rounded-xl border border-[#16161D]">
                                        <p className="text-xs text-zinc-500 text-center italic font-medium">
                                            {totalFollowers > 0 
                                                ? `You've gained ${totalFollowers} subscribers this month.`
                                                : 'Start streaming to grow your subscriber base!'}
                                        </p>
                                    </div>
                                </div>
                            </div>
                        </div>

                        {/* Engagement Section */}
                        <div className="bg-[#0F0F14] p-8 rounded-2xl border border-[#16161D] shadow-[0_20px_60px_rgba(0,0,0,0.6)]">
                            <h2 className="text-xl font-bold mb-8 text-white">Audience Engagement (7 Days)</h2>
                            <div className="grid grid-cols-1 md:grid-cols-3 gap-12">
                                <EngagementMetric 
                                    label="Avg Watch Time" 
                                    value={`${avgSessionMinutes}m ${avgSessionRemSeconds}s`} 
                                    percentage={Math.min(Math.round((avgSessionSeconds / 1800) * 100), 100)} 
                                />
                                <EngagementMetric 
                                    label="Messages / Viewer" 
                                    value={avgMessages} 
                                    percentage={Math.min(Math.round(Number(avgMessages) * 10), 100)} 
                                />
                                <EngagementMetric 
                                    label="Retention Rate" 
                                    value={`${returningRate}%`} 
                                    percentage={returningRate} 
                                />
                            </div>
                        </div>
                    </>
                )}
            </div>
        </div>
    );
};

const EngagementMetric: React.FC<{ label: string, value: string, percentage: number }> = ({ label, value, percentage }) => (
    <div className="text-center">
        <div className="relative inline-flex items-center justify-center mb-4 group">
            <svg className="w-24 h-24 transform -rotate-90">
                <circle className="text-white/5" strokeWidth="6" stroke="currentColor" fill="transparent" r="40" cx="48" cy="48" />
                <circle className="text-indigo-600" strokeWidth="6" strokeDasharray={251.2} strokeDashoffset={251.2 - (251.2 * percentage) / 100} strokeLinecap="round" stroke="currentColor" fill="transparent" r="40" cx="48" cy="48" />
            </svg>
            <span className="absolute text-xl font-extrabold text-white group-hover:scale-110 transition-transform">{percentage}%</span>
        </div>
        <h4 className="text-xs font-bold text-zinc-500 uppercase tracking-widest">{label}</h4>
        <p className="text-xl font-bold text-white mt-1">{value}</p>
    </div>
);

const styles: { [key: string]: React.CSSProperties } = {
    layout: {
        display: 'flex',
        minHeight: 'calc(100vh - 64px)',
        backgroundColor: '#08080A',
        fontFamily: 'system-ui, -apple-system, sans-serif',
    }
};

export default CreatorAnalyticsPage;
