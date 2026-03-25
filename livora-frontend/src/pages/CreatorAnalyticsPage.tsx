import React from 'react';
import CreatorSidebar from '../components/CreatorSidebar';
import SEO from '../components/SEO';

const CreatorAnalyticsPage: React.FC = () => {
    // Mock Data
    const metrics = [
        { title: 'Total Followers', value: '1,248', subValue: '+12% from last month', color: 'bg-[#0F0F14]', textColor: 'text-zinc-100', icon: '👥' },
        { title: 'Total Subscribers', value: '86', subValue: '+5 this week', color: 'bg-[#0F0F14]', textColor: 'text-zinc-100', icon: '💎' },
        { title: 'Monthly Earnings', value: '€420.50', subValue: '€12.50 today', color: 'bg-[#0F0F14]', textColor: 'text-zinc-100', icon: '💰' },
        { title: 'Post Views (7d)', value: '5,672', subValue: 'Top post: "Daily Vlog"', color: 'bg-[#0F0F14]', textColor: 'text-zinc-100', icon: '👁️' },
    ];

    const earningsGrowth = [
        { day: '1', amount: 12 }, { day: '2', amount: 15 }, { day: '3', amount: 8 }, { day: '4', amount: 20 },
        { day: '5', amount: 25 }, { day: '6', amount: 18 }, { day: '7', amount: 30 }, { day: '8', amount: 22 },
        { day: '9', amount: 28 }, { day: '10', amount: 35 }, { day: '11', amount: 40 }, { day: '12', amount: 32 },
        { day: '13', amount: 38 }, { day: '14', amount: 45 }, { day: '15', amount: 50 }, { day: '16', amount: 42 },
        { day: '17', amount: 48 }, { day: '18', amount: 55 }, { day: '19', amount: 60 }, { day: '20', amount: 52 },
        { day: '21', amount: 58 }, { day: '22', amount: 65 }, { day: '23', amount: 70 }, { day: '24', amount: 62 },
        { day: '25', amount: 68 }, { day: '26', amount: 75 }, { day: '27', amount: 80 }, { day: '28', amount: 72 },
        { day: '29', amount: 78 }, { day: '30', amount: 85 },
    ];

    const subscriberGrowth = [
        { label: 'Week 1', value: 12 },
        { label: 'Week 2', value: 18 },
        { label: 'Week 3', value: 25 },
        { label: 'Week 4', value: 31 },
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

                {/* Summary Metrics */}
                <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6 mb-8">
                    {metrics.map((metric, index) => (
                        <div key={index} className={`${metric.color} p-6 rounded-2xl border border-[#16161D] shadow-[0_20px_60px_rgba(0,0,0,0.6)] transition-all hover:scale-[1.02]`}>
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
                            <span className="text-xs px-2 py-1 bg-green-500/10 text-green-500 rounded-full font-bold border border-green-500/20">
                                +15.4% 📈
                            </span>
                        </div>
                        <div className="h-64 w-full flex items-end justify-between px-2 pt-4">
                            {earningsGrowth.map((data, i) => (
                                <div 
                                    key={i} 
                                    className="w-1.5 bg-indigo-600 rounded-t-sm hover:bg-indigo-500 transition-colors group relative"
                                    style={{ height: `${data.amount}%` }}
                                >
                                    <div className="absolute bottom-full left-1/2 -translate-x-1/2 mb-2 hidden group-hover:block bg-[#08080A] text-white text-[10px] py-1 px-2 rounded border border-[#16161D] whitespace-nowrap z-10 shadow-2xl">
                                        Day {data.day}: €{data.amount}
                                    </div>
                                </div>
                            ))}
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
                                Monthly Goal: 80%
                            </span>
                        </div>
                        <div className="h-64 flex flex-col justify-around">
                            {subscriberGrowth.map((item, i) => (
                                <div key={i} className="space-y-2">
                                    <div className="flex justify-between text-sm">
                                        <span className="text-zinc-400 font-medium">{item.label}</span>
                                        <span className="font-bold text-white">+{item.value}</span>
                                    </div>
                                    <div className="w-full bg-white/5 h-2.5 rounded-full overflow-hidden">
                                        <div 
                                            className="h-full bg-indigo-600 rounded-full transition-all duration-500 ease-out" 
                                            style={{ width: `${(item.value / 40) * 100}%` }}
                                        ></div>
                                    </div>
                                </div>
                            ))}
                            <div className="mt-4 p-4 bg-white/5 rounded-xl border border-[#16161D]">
                                <p className="text-xs text-zinc-500 text-center italic font-medium">
                                    "You've gained 86 subscribers this month. Your conversion rate is up 4.2%!"
                                </p>
                            </div>
                        </div>
                    </div>
                </div>

                {/* Engagement Section (Extra) */}
                <div className="bg-[#0F0F14] p-8 rounded-2xl border border-[#16161D] shadow-[0_20px_60px_rgba(0,0,0,0.6)]">
                    <h2 className="text-xl font-bold mb-8 text-white">Audience Engagement</h2>
                    <div className="grid grid-cols-1 md:grid-cols-3 gap-12">
                        <EngagementMetric label="Avg Watch Time" value="12m 45s" percentage={75} />
                        <EngagementMetric label="Post Interactions" value="842" percentage={62} />
                        <EngagementMetric label="Retention Rate" value="68%" percentage={68} />
                    </div>
                </div>
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
