import React, { useEffect, useState, useRef } from 'react';
import creatorEarningsService, { 
    CreatorEarningEntry
} from '../api/creatorEarningsService';
import creatorStripeService, { StripeOnboardingStatus } from '../api/creatorStripeService';
import creatorService from '../api/creatorService';
import { CreatorEarningsDashboard as EarningsData } from '../types';
import CreatorSidebar from '../components/CreatorSidebar';
import EmptyState from '../components/EmptyState';
import { useAuth } from '../auth/useAuth';
import { useWs } from '../ws/WsContext';
import SEO from '../components/SEO';
import { useLocation } from 'react-router-dom';
import apiClient from '../api/apiClient';
import { safeRender } from '@/utils/safeRender';

const MIN_PAYOUT_AMOUNT = 50.00;

const CreatorEarningsDashboard: React.FC = () => {
    const { user, authLoading } = useAuth();
    const { subscribe, connected } = useWs();
    const location = useLocation();
    const [earnings, setEarnings] = useState<EarningsData | null>(null);
    const [initialTotalEarnings, setInitialTotalEarnings] = useState<number | null>(null);
    const [recentTransactions, setRecentTransactions] = useState<CreatorEarningEntry[]>([]);
    const [primaryAction, setPrimaryAction] = useState<any | null>(null);
    const [secondaryActions, setSecondaryActions] = useState<string[]>([]);
    const [performanceInsight, setPerformanceInsight] = useState<string | null>(null);
    const [incomeInsight, setIncomeInsight] = useState<string | null>(null);
    const [revenueConcentrationInsight, setRevenueConcentrationInsight] = useState<string | null>(null);
    const [stabilityInsight, setStabilityInsight] = useState<string | null>(null);
    const [bestDayInsight, setBestDayInsight] = useState<{ day: string, lift: number } | null>(null);
    const [riskInsight, setRiskInsight] = useState<{ top1: number, top3: number, riskLevel: string, uniqueSupporters: number } | null>(null);
    const [whaleActivity, setWhaleActivity] = useState<any[]>([]);
    const [monetizationLadder, setMonetizationLadder] = useState<any | null>(null);
    const [pricingStrategyInsight, setPricingStrategyInsight] = useState<{
        strategy: string,
        action: string,
        impact: string,
        color: string,
        projection?: {
            revenue: number,
            riskTier: string,
            currentRiskTier: string,
            delta: number,
            absoluteIncrease: number,
            isRiskImproved: boolean
        },
        confidence?: {
            tier: string,
            color: string,
            explanation: string
        }
    } | null>(null);
    const [tipFloorInsight, setTipFloorInsight] = useState<{
        currentAvgTip: number,
        suggestedTipFloor: number,
        revenueUpside: number,
        riskLevel: string,
        recommendation: string,
        color: string,
        isBlocked?: boolean
    } | null>(null);
    const [adaptiveTipInsight, setAdaptiveTipInsight] = useState<{
        status: 'READY' | 'NOT_READY' | 'COOLDOWN' | 'CAUTION',
        badgeColor: string,
        reason?: string,
        suggestedAction?: string,
        expectedLift?: string,
        volumeRisk?: string,
        remainingMinutes?: number,
        totalCooldownMinutes?: number,
        riskScore?: number,
        momentum?: number
    } | null>(null);
    const [uniqueSupporters, setUniqueSupporters] = useState(0);
    const [liveTipCount, setLiveTipCount] = useState(0);
    const [isPulsing, setIsPulsing] = useState(false);
    const prevBalanceRef = useRef<number | undefined>(undefined);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState<string | null>(null);
    const [remainingSeconds, setRemainingSeconds] = useState<number | null>(null);
    const [stripeStatus, setStripeStatus] = useState<StripeOnboardingStatus>(StripeOnboardingStatus.NOT_STARTED);
    const [onboardingLoading, setOnboardingLoading] = useState(false);

    const isPayoutEligible = (earnings?.availableBalance || 0) >= MIN_PAYOUT_AMOUNT;

    // Frontend Insights Calculations
    const avgEarnings = recentTransactions.length > 0 
        ? recentTransactions.reduce((acc, tx) => acc + tx.netAmount, 0) / recentTransactions.length 
        : 0;
    const pendingCount = recentTransactions.filter(tx => tx.locked).length;
    const lockedRatio = recentTransactions.length > 0 
        ? (pendingCount / recentTransactions.length) * 100 
        : 0;

    const sessionDelta = initialTotalEarnings !== null && earnings 
        ? Math.max(0, earnings.totalEarnings - initialTotalEarnings) 
        : 0;

    const fetchEarningsData = async () => {
        try {
            setLoading(true);
            
            // 1. CORE DATA (strict)
            const [earningsResponse, transactionsResponse] = await Promise.all([
                creatorService.getMyEarnings(),
                creatorEarningsService.getRecentTransactions()
            ]);

            setEarnings(earningsResponse);
            setRecentTransactions(transactionsResponse);
            
            if (initialTotalEarnings === null) {
                setInitialTotalEarnings(earningsResponse.totalEarnings);
            }

            // 2. OPTIONAL ANALYTICS (resilient)
            let analyticsResponse = null;
            try {
                analyticsResponse = await creatorEarningsService.getDailyAnalytics(30);
            } catch (err) {
                console.warn('Failed to fetch daily analytics (optional):', err);
            }

            let reportResponse = null;
            try {
                reportResponse = await creatorEarningsService.getEarningsReport();
            } catch (err) {
                console.warn('Failed to fetch earnings report (optional):', err);
            }
            
            if (analyticsResponse && analyticsResponse.length >= 5) {
                const dailyTotals = analyticsResponse.map((d: any) => d.earnings || 0);
                const avg = dailyTotals.reduce((a: number, b: number) => a + b, 0) / dailyTotals.length;
                
                if (avg > 0) {
                    const variance = dailyTotals.reduce((sum: number, val: number) => sum + Math.pow(val - avg, 2), 0) / dailyTotals.length;
                    const stdDev = Math.sqrt(variance);
                    const cv = stdDev / avg;
                    
                    let status = 'Volatile';
                    if (cv < 0.3) status = 'Stable';
                    else if (cv < 0.6) status = 'Moderate';
                    
                    setStabilityInsight(status);
                }
            } else {
                setStabilityInsight(null);
            }
            
            // Calculate Supporter-based Insights
            if (transactionsResponse && transactionsResponse.length > 0) {
                const totalRevenue = transactionsResponse.reduce((acc, tx) => acc + tx.netAmount, 0);
                const supporterMap: Record<string, { total: number, last14: number, prev14: number }> = {};
                const now = new Date();
                const fourteenDaysAgo = new Date(now.getTime() - 14 * 24 * 60 * 60 * 1000);
                const twentyEightDaysAgo = new Date(now.getTime() - 28 * 24 * 60 * 60 * 1000);
                
                transactionsResponse.forEach(tx => {
                    const name = tx.supporterName || 'Anonymous';
                    if (!supporterMap[name]) {
                        supporterMap[name] = { total: 0, last14: 0, prev14: 0 };
                    }
                    supporterMap[name].total += tx.netAmount;
                    
                    const txDate = new Date(tx.createdAt);
                    if (txDate >= fourteenDaysAgo) {
                        supporterMap[name].last14 += tx.netAmount;
                    } else if (txDate >= twentyEightDaysAgo) {
                        supporterMap[name].prev14 += tx.netAmount;
                    }
                });
                
                const supporters = Object.entries(supporterMap)
                    .map(([name, data]) => ({ name, ...data, share: data.total / (totalRevenue || 1) }))
                    .sort((a, b) => b.total - a.total);
                
                setUniqueSupporters(supporters.length);
                
                if (totalRevenue > 0) {
                    // Concentration Insight
                    const sortedTxs = [...transactionsResponse].sort((a, b) => b.netAmount - a.netAmount);
                    const topCount = Math.ceil(sortedTxs.length * 0.2);
                    const topRevenueConcentration = sortedTxs.slice(0, topCount).reduce((acc, tx) => acc + tx.netAmount, 0);
                    const percentageConcentration = (topRevenueConcentration / totalRevenue) * 100;
                    if (percentageConcentration > 60) {
                        setRevenueConcentrationInsight(`Your top supporters drive ${percentageConcentration.toFixed(0)}% of your revenue.`);
                    } else {
                        setRevenueConcentrationInsight(null);
                    }

                    // Dependency Risk (Refined to use actual supporter totals)
                    let currentRiskLevel = "HEALTHY";
                    if (transactionsResponse.length >= 5) {
                        const top1Share = supporters[0].share;
                        const top3Share = supporters.slice(0, 3).reduce((acc, s) => acc + s.share, 0);
                        
                        if (top1Share > 0.8) currentRiskLevel = "CRITICAL";
                        else if (top3Share > 0.8) currentRiskLevel = "HIGH";
                        else if (top1Share > 0.4) currentRiskLevel = "MODERATE";
                        
                        setRiskInsight({
                            top1: top1Share * 100,
                            top3: top3Share * 100,
                            riskLevel: currentRiskLevel,
                            uniqueSupporters: supporters.length
                        });
                    }

                    // Whale Detection
                    const whales = supporters.filter(s => s.share > 0.2);
                    const whaleData = whales.map(w => {
                        let status = "STABLE";
                        if (w.last14 === 0) status = "INACTIVE";
                        else if (w.prev14 > 0) {
                            const change = (w.last14 - w.prev14) / w.prev14;
                            if (change < -0.4) status = "DECLINING";
                            else if (change > 0.3) status = "GROWING";
                        } else if (w.last14 > 0) {
                            status = "GROWING"; 
                        }
                        
                        return {
                            name: w.name.charAt(0) + "***",
                            share: w.share * 100,
                            status
                        };
                    });
                    setWhaleActivity(whaleData);

                    // Monetization Ladder
                    if (supporters.length >= 3) {
                        const totalViewers = analyticsResponse ? analyticsResponse.reduce((acc: any, d: any) => acc + (d.viewers || 0), 0) : 0;
                        const nonSpenderCount = Math.max(0, totalViewers - supporters.length);
                        
                        const tiers = [
                            { label: 'Non-spenders', count: nonSpenderCount, color: '#3f3f46' },
                            { label: 'Low (€1-50)', count: 0, color: '#6366f1' },
                            { label: 'Mid (€50-250)', count: 0, color: '#a855f7' },
                            { label: 'High (€250+)', count: 0, color: '#10b981' }
                        ];

                        supporters.forEach(s => {
                            if (s.total <= 50) tiers[1].count++;
                            else if (s.total <= 250) tiers[2].count++;
                            else tiers[3].count++;
                        });

                        const totalBase = tiers.reduce((acc, t) => acc + t.count, 0);
                        const tiersWithPct = tiers.map(t => ({
                            ...t,
                            percentage: totalBase > 0 ? (t.count / totalBase) * 100 : 0
                        }));

                        const untappedTier = tiersWithPct.slice(0, 3).reduce((prev, curr) => (curr.count > prev.count) ? curr : prev);

                        setMonetizationLadder({
                            tiers: tiersWithPct,
                            untapped: untappedTier.label
                        });
                    } else {
                        setMonetizationLadder(null);
                    }

                    // Pricing Intelligence
                    const avgPerSale = transactionsResponse.length > 0 ? totalRevenue / transactionsResponse.length : 0;
                    const top1Share = supporters[0].share;
                    const uniqueSpenders = supporters.length;

                    let strategy = 'STABLE';
                    let action = 'Maintain current pricing strategy and supporter engagement.';
                    let impact = 'LOW';
                    let color = '#71717A';

                    if (top1Share > 0.6) {
                        strategy = 'DIVERSIFY';
                        action = 'Expand audience reach to reduce dependency on top supporters.';
                        impact = 'HIGH';
                        color = '#f59e0b';
                    } else if (uniqueSpenders > 5 && avgPerSale < 15) {
                        strategy = 'PREMIUM_UPSELL';
                        action = 'Introduce higher-tier rewards to increase average transaction value.';
                        impact = 'MEDIUM';
                        color = '#6366f1';
                    } else if (uniqueSpenders < 5 && avgPerSale > 50) {
                        strategy = 'PRICE_OPTIMIZATION';
                        action = 'Review pricing tiers to attract a broader range of supporters.';
                        impact = 'HIGH';
                        color = '#10b981';
                    }

                    // Projection Simulation
                    let projection = undefined;
                    if (reportResponse && reportResponse.monthly) {
                        const currentRevenue = (reportResponse.monthly.totalEarnings || 0) + (reportResponse.monthly.totalTokens || 0) * 0.01;
                        if (currentRevenue > 0) {
                            const absoluteIncrease = avgPerSale * 2;
                            const projectedRevenue = currentRevenue + absoluteIncrease;
                            
                            // projectedTopShare calculation based on prompt formulas
                            const topSupporterRevenue = supporters[0]?.total || 0;
                            const projectedTopShare = (topSupporterRevenue / projectedRevenue) * 100;

                            let projectedRiskTier = "STABLE";
                            if (projectedTopShare > 70) projectedRiskTier = "CRITICAL";
                            else if (projectedTopShare >= 40) projectedRiskTier = "MODERATE";

                            const revenueDeltaPercent = Math.min(300, ((projectedRevenue - currentRevenue) / currentRevenue) * 100);

                            // Calculate risk improvement
                            const initialRiskScore = currentRiskLevel === 'CRITICAL' ? 3 : currentRiskLevel === 'HIGH' ? 2 : currentRiskLevel === 'MODERATE' ? 1 : 0;
                            const projectedRiskScore = projectedRiskTier === 'CRITICAL' ? 3 : projectedRiskTier === 'MODERATE' ? 1 : 0;

                            projection = {
                                revenue: projectedRevenue,
                                riskTier: projectedRiskTier,
                                currentRiskTier: currentRiskLevel,
                                delta: revenueDeltaPercent,
                                absoluteIncrease: absoluteIncrease,
                                isRiskImproved: projectedRiskScore < initialRiskScore
                            };
                        }
                    }

                    // Confidence Score Calculation
                    let confidenceTier = 'LOW';
                    let confidenceColor = '#f59e0b';
                    let confidenceExplanation = 'Small sample size. Forecast accuracy may be limited.';

                    if (uniqueSpenders > 7) {
                        confidenceTier = 'HIGH';
                        confidenceColor = '#10b981';
                        confidenceExplanation = 'Strong statistical data. High forecast accuracy.';
                    } else if (uniqueSpenders >= 3) {
                        confidenceTier = 'MEDIUM';
                        confidenceColor = '#6366f1';
                        confidenceExplanation = 'Growing supporter base. Moderate projection reliability.';
                    }

                    const confidence = {
                        tier: confidenceTier,
                        color: confidenceColor,
                        explanation: confidenceExplanation
                    };

                    setPricingStrategyInsight({ strategy, action, impact, color, projection, confidence });

                    // Adaptive Tip Engine & Tip Floor Optimization
                    const currentTipFloor = 1.00;
                    try {
                        const engineResponse = await apiClient.get('/creator/analytics/adaptive-tip');
                        const data = engineResponse.data;

                        const suggestedTipFloor = data.status === 'READY' || data.status === 'CAUTION' 
                            ? data.suggestedFloor 
                            : currentTipFloor;

                        setTipFloorInsight({
                            currentAvgTip: avgPerSale,
                            suggestedTipFloor,
                            revenueUpside: suggestedTipFloor - currentTipFloor,
                            riskLevel: data.status === 'NOT_READY' ? 'STRATEGIC HOLD' : 'LOW',
                            recommendation: data.status === 'READY' || data.status === 'CAUTION' 
                                ? `Optimized growth path. Recommended floor: €${data.suggestedFloor}.` 
                                : 'Revenue concentration too high. Focus on diversification before raising tip floor.',
                            color: data.status === 'READY' ? '#10b981' : (data.status === 'CAUTION' ? '#f59e0b' : '#71717A'),
                            isBlocked: data.status === 'NOT_READY'
                        });

                        const adaptiveColor = 
                            data.status === 'READY' ? '#10b981' :
                            (data.status === 'CAUTION' || data.status === 'COOLDOWN') ? '#f59e0b' : '#71717A';

                        setAdaptiveTipInsight({
                            status: data.status,
                            badgeColor: adaptiveColor,
                            reason: data.reason,
                            riskScore: data.riskScore,
                            momentum: data.momentum,
                            suggestedAction: (data.status === 'READY' || data.status === 'CAUTION') ? 'Execute +€2 test increase for high-value segments.' : undefined,
                            expectedLift: (data.status === 'READY' || data.status === 'CAUTION') ? '8–15%' : undefined,
                            volumeRisk: data.status === 'READY' ? 'LOW' : (data.status === 'CAUTION' ? 'MEDIUM' : undefined),
                            remainingMinutes: data.status === 'COOLDOWN' ? Math.ceil(data.cooldownRemainingSeconds / 60) : undefined,
                            totalCooldownMinutes: 30
                        });

                        if (data.status === 'COOLDOWN') {
                            setRemainingSeconds(data.cooldownRemainingSeconds);
                        } else {
                            setRemainingSeconds(null);
                        }
                    } catch (err) {
                        console.warn('Failed to fetch adaptive tip engine data:', err);
                        setAdaptiveTipInsight(null);
                        setTipFloorInsight(null);
                    }
                } else {
                    setRevenueConcentrationInsight(null);
                    setRiskInsight(null);
                    setWhaleActivity([]);
                    setPricingStrategyInsight(null);
                    setTipFloorInsight(null);
                    setAdaptiveTipInsight(null);
                }
            } else {
                setUniqueSupporters(0);
                setRevenueConcentrationInsight(null);
                setRiskInsight(null);
                setWhaleActivity([]);
                setPricingStrategyInsight(null);
                setTipFloorInsight(null);
            }
            
            // Calculate Performance Insight
            if (analyticsResponse && analyticsResponse.length > 0) {
                const dailyTotals = analyticsResponse.map((d: any) => d.earnings || 0);
                const validDailyTotals = dailyTotals.filter((e: number) => e > 0);
                
                if (validDailyTotals.length > 0) {
                    const totalSum = dailyTotals.reduce((a: number, b: number) => a + b, 0);
                    const avgDaily = totalSum / analyticsResponse.length;
                    
                    const bestDay = analyticsResponse.reduce((prev: any, current: any) => 
                        ((prev.earnings || 0) > (current.earnings || 0)) ? prev : current
                    );
                    
                    if (bestDay && bestDay.earnings > 0 && avgDaily > 0) {
                        const dayName = new Date(bestDay.date).toLocaleDateString('en-US', { weekday: 'long' });
                        const diff = ((bestDay.earnings - avgDaily) / avgDaily) * 100;
                        setPerformanceInsight(`You perform best on ${dayName} (+${diff.toFixed(0)}% vs average).`);
                    }
                }
            }

            // Calculate Best Performance Window (Recent Transactions)
            if (transactionsResponse && transactionsResponse.length > 10) {
                const daySums: Record<number, number> = {};
                
                transactionsResponse.forEach((tx: any) => {
                    const date = new Date(tx.createdAt);
                    const day = date.getDay();
                    daySums[day] = (daySums[day] || 0) + tx.netAmount;
                });
                
                const activeDaysTotals = Object.values(daySums).filter(t => t > 0);
                const activeDaysCount = activeDaysTotals.length;
                
                if (activeDaysCount >= 3) {
                    const baseline = activeDaysTotals.reduce((a, b) => a + b, 0) / activeDaysCount;
                    
                    let maxDayTotal = 0;
                    let bestDayIndex = -1;
                    
                    Object.entries(daySums).forEach(([day, total]) => {
                        if (total > maxDayTotal) {
                            maxDayTotal = total;
                            bestDayIndex = parseInt(day);
                        }
                    });
                    
                    if (bestDayIndex !== -1 && baseline > 0) {
                        const lift = Math.min(200, Math.round(((maxDayTotal - baseline) / baseline) * 100));
                        const dayNames = ['Sunday', 'Monday', 'Tuesday', 'Wednesday', 'Thursday', 'Friday', 'Saturday'];
                        setBestDayInsight({
                            day: dayNames[bestDayIndex],
                            lift: lift
                        });
                    } else {
                        setBestDayInsight(null);
                    }
                } else {
                    setBestDayInsight(null);
                }
            } else {
                setBestDayInsight(null);
            }

            // Generate Prioritized Growth Actions
            if (analyticsResponse && analyticsResponse.length >= 5) {
                const candidates: any[] = [];
                
                // 1. Earnings Trend
                const mid = Math.floor(analyticsResponse.length / 2);
                const firstHalf = analyticsResponse.slice(0, mid).reduce((acc: number, d: any) => acc + (d.earnings || 0), 0);
                const secondHalf = analyticsResponse.slice(mid).reduce((acc: number, d: any) => acc + (d.earnings || 0), 0);
                const earningsTrend = firstHalf > 0 ? (secondHalf - firstHalf) / firstHalf : 0;

                if (earningsTrend < -0.1) {
                    candidates.push({ 
                        metric: 'Revenue Growth', 
                        current: `${(earningsTrend * 100).toFixed(1)}%`,
                        target: '0.0%',
                        progress: Math.max(0, Math.min(100, (1 + earningsTrend) * 100)),
                        message: `Revenue trend: ${(earningsTrend * 100).toFixed(1)}%. Deploy high-engagement interactive content.`, 
                        priority: 2 
                    });
                } else if (earningsTrend > 0.1) {
                    candidates.push({ 
                        metric: 'Revenue Growth', 
                        current: `+${(earningsTrend * 100).toFixed(1)}%`,
                        target: '+15.0%',
                        progress: Math.min(100, (earningsTrend / 0.15) * 100),
                        message: `Revenue trend: +${(earningsTrend * 100).toFixed(1)}%. Increase broadcast density during peak hours.`, 
                        priority: 5 
                    });
                }
                
                // 2. Subscriber Growth
                const totalSubs = analyticsResponse.reduce((acc: number, d: any) => acc + (d.subscriptions || 0), 0);
                if (totalSubs < 3) {
                    candidates.push({ 
                        metric: 'Monthly Subscriptions',
                        current: totalSubs,
                        target: 3,
                        progress: (totalSubs / 3) * 100,
                        message: "Subscription conversion below threshold. Implement limited-time viewer incentives.", 
                        priority: 3 
                    });
                } else if (totalSubs > 10) {
                    candidates.push({ 
                        metric: 'Monthly Subscriptions',
                        current: totalSubs,
                        target: 20,
                        progress: (totalSubs / 20) * 100,
                        message: `Subscriber growth: +${totalSubs}. Deploy multi-tier exclusive loyalty perks.`, 
                        priority: 6 
                    });
                }
                
                // 3. Retention Rate
                const totalViewers = analyticsResponse.reduce((acc: number, d: any) => acc + (d.viewers || 0), 0);
                const totalReturning = analyticsResponse.reduce((acc: number, d: any) => acc + (d.returningViewers || 0), 0);
                const retention = totalViewers > 0 ? (totalReturning / totalViewers) : 0;
                
                if (retention < 0.25) {
                    candidates.push({ 
                        metric: 'Viewer Retention',
                        current: `${(retention * 100).toFixed(1)}%`,
                        target: '25.0%',
                        progress: (retention / 0.25) * 100,
                        message: `Retention rate: ${(retention * 100).toFixed(1)}%. Stabilize weekly broadcast schedule.`, 
                        priority: 1 
                    });
                } else if (retention > 0.5) {
                    candidates.push({ 
                        metric: 'Viewer Retention',
                        current: `${(retention * 100).toFixed(1)}%`,
                        target: '60.0%',
                        progress: (retention / 0.6) * 100,
                        message: `Retention rate: ${(retention * 100).toFixed(1)}%. Execute supporter appreciation protocols.`, 
                        priority: 4 
                    });
                }
                
                // Sort by priority (1 is highest)
                candidates.sort((a, b) => a.priority - b.priority);

                if (candidates.length > 0) {
                    setPrimaryAction(candidates[0]);
                    setSecondaryActions(candidates.slice(1, 3).map(c => c.message));
                } else {
                    setPrimaryAction({
                        metric: 'Performance Stability',
                        current: 'Optimal',
                        target: 'Maintain',
                        progress: 100,
                        message: "Performance metrics stable. Maintain baseline content delivery frequency."
                    });
                    setSecondaryActions([]);
                }
            } else {
                setPrimaryAction(null);
                setSecondaryActions([]);
            }

            // Calculate Income Stream Insight
            if (reportResponse && reportResponse.monthly && reportResponse.monthly.revenueBySource) {
                const sources = Object.entries(reportResponse.monthly.revenueBySource);
                if (sources.length > 0) {
                    const totalRevenue = sources.reduce((acc, [_, val]) => acc + (val as number), 0);
                    if (totalRevenue > 0) {
                        const [bestSource, bestValue] = sources.reduce((prev, curr) => (curr[1] as number) > (prev[1] as number) ? curr : prev);
                        const percentage = ((bestValue as number) / totalRevenue) * 100;
                        const sourceLabel = (bestSource || "").toLowerCase().replace('_', ' ').replace(/^\w/, c => c.toUpperCase());
                        setIncomeInsight(`Your strongest income stream is ${sourceLabel} (${percentage.toFixed(0)}% of total).`);
                    }
                }
            }

            setError(null);
        } catch (err) {
            console.error('Failed to fetch core earnings data:', err);
            setError('Failed to load core earnings data. Please try again later.');
        } finally {
            setLoading(false);
        }
    };

    const activateTest = () => {
        sessionStorage.setItem("adaptive_tip_engine_last_activation", Date.now().toString());
        fetchEarningsData();
    };

    const fetchStripeStatus = async () => {
        try {
            const data = await creatorStripeService.getStatus();
            setStripeStatus(data.onboardingStatus);
        } catch (err) {
            console.error('Failed to fetch Stripe status:', err);
        }
    };

    useEffect(() => {
        if (!user || authLoading) return;
        fetchEarningsData();
        fetchStripeStatus();
    }, [user, authLoading, location.search]);

    useEffect(() => {
        if (prevBalanceRef.current !== undefined && 
            earnings?.availableBalance !== undefined && 
            earnings.availableBalance > prevBalanceRef.current) {
            setIsPulsing(true);
            const timer = setTimeout(() => setIsPulsing(false), 800);
            return () => clearTimeout(timer);
        }
        prevBalanceRef.current = earnings?.availableBalance;
    }, [earnings?.availableBalance]);

    const prevRemainingSecondsRef = useRef<number | null>(null);

    useEffect(() => {
        if (remainingSeconds === null) return;

        const interval = setInterval(() => {
            setRemainingSeconds(prev => {
                if (!prev || prev <= 1) {
                    clearInterval(interval);
                    sessionStorage.removeItem("adaptive_tip_engine_last_activation");
                    return null;
                }
                return prev - 1;
            });
        }, 1000);

        return () => clearInterval(interval);
    }, [remainingSeconds]);

    // Re-evaluate eligibility automatically when cooldown ends
    useEffect(() => {
        if (prevRemainingSecondsRef.current !== null && remainingSeconds === null) {
            fetchEarningsData();
        }
        prevRemainingSecondsRef.current = remainingSeconds;
    }, [remainingSeconds]);

    useEffect(() => {
        if (!user?.id || authLoading) return;

        const earningsTopic = `/exchange/amq.topic/creator.${user.id}.earnings`;
        const tipsQueue = '/user/queue/tips';
        
        const unsubEarnings = subscribe(earningsTopic, (message) => {
            try {
                const data = JSON.parse(message.body);
                const aggregated = data.currentAggregatedEarnings;
                
                if (aggregated) {
                    setEarnings(prev => {
                        if (!prev) return prev;
                        
                        // Calculate delta in total net revenue to reflect in availableBalance immediately
                        const delta = (aggregated.totalRevenue || 0) - prev.totalEarnings;
                        
                        return {
                            ...prev,
                            totalEarnings: aggregated.totalRevenue,
                            // If totalEarnings increased, we add the delta to availableBalance for real-time feedback
                            availableBalance: prev.availableBalance + (delta > 0 ? delta : 0),
                            // Note: pendingBalance is not directly updated from pendingPayout 
                            // due to slight semantic differences, but fetchEarningsData will sync it.
                        };
                    });
                    
                    // Trigger a full refresh to sync transactions and ensure all balances are 100% accurate
                    fetchEarningsData();
                }
            } catch (err) {
                console.error('Error handling earnings websocket update:', err);
            }
        });

        const unsubTips = subscribe(tipsQueue, () => {
            try {
                setLiveTipCount(prev => prev + 1);
            } catch (err) {
                console.error('Error handling tips websocket update:', err);
            }
        });

        return () => {
            if (typeof unsubEarnings === 'function') unsubEarnings();
            if (typeof unsubTips === 'function') unsubTips();
        };
    }, [user?.id, authLoading, subscribe, connected]);

    const handleConnectStripe = async () => {
        try {
            setOnboardingLoading(true);
            const { onboardingUrl } = await creatorStripeService.createOnboardingLink();
            window.location.href = onboardingUrl;
        } catch (err) {
            console.error('Failed to start Stripe onboarding:', err);
            setError('Failed to start Stripe onboarding. Please try again.');
            setOnboardingLoading(false);
        }
    };

    if (authLoading) return null;

    const progressColor = primaryAction 
        ? (primaryAction.progress < 50 ? '#f59e0b' : (primaryAction.progress <= 85 ? '#6366f1' : '#10b981'))
        : '#6366f1';

    const formatTime = (seconds: number) => {
        const m = Math.floor(seconds / 60);
        const s = seconds % 60;
        return `${m}:${s.toString().padStart(2, "0")}`;
    };

    return (
        <div style={styles.layout}>
            <SEO 
                title="Revenue Dashboard" 
                description="Manage your revenue, balances, and payout channels."
            />
            <CreatorSidebar />
            <main style={styles.main}>
                {/* 1. FINANCIAL HEADER */}
                <header style={styles.header}>
                    <div>
                        <h1 style={styles.title}>Revenue</h1>
                        <p style={styles.subtitle}>Track your business liquidity and history</p>
                    </div>
                    <button onClick={() => { fetchEarningsData(); fetchStripeStatus(); }} style={styles.refreshButton} disabled={loading}>
                        {loading ? 'Updating...' : 'Refresh'}
                    </button>
                </header>

                {error && <div style={styles.errorBanner}>{error}</div>}

                {/* 2. LIQUIDITY HERO (Available Balance) */}
                <section style={styles.heroSection}>
                    <div style={styles.heroMain}>
                        <div style={styles.heroLabelRow}>
                            <span style={styles.heroLabel}>Available for Transfer</span>
                            <span style={styles.shieldIcon}>🛡️ SECURE</span>
                        </div>
                        <h2 
                            style={styles.heroAmount} 
                            className={isPulsing ? 'balance-pulse-glow' : ''}
                        >
                            €{(earnings?.availableBalance || 0).toFixed(2)}
                        </h2>
                        {sessionDelta > 0 && (
                            <div style={styles.sessionDeltaContainer}>
                                <span style={styles.sessionIcon}>📈</span>
                                +€{sessionDelta.toFixed(2)} this session
                            </div>
                        )}
                        <div style={styles.heroActionRow}>
                            <button 
                                style={isPayoutEligible ? styles.transferButton : styles.transferButtonDisabled}
                                disabled={!isPayoutEligible}
                            >
                                {isPayoutEligible ? 'Transfer to Bank' : `Next Payout at €${MIN_PAYOUT_AMOUNT.toFixed(2)}`}
                            </button>
                        </div>
                    </div>
                    <div style={styles.heroStats}>
                        <div style={styles.heroMiniStat}>
                            <span style={styles.miniLabel}>Total Lifetime</span>
                            <span style={styles.miniValue}>€{(earnings?.totalEarnings || 0).toFixed(2)}</span>
                        </div>
                        <div style={styles.heroMiniStat}>
                            <span style={styles.miniLabel}>Processing (7d)</span>
                            <span style={styles.miniValueAmber}>€{(earnings?.pendingBalance || 0).toFixed(2)}</span>
                        </div>
                    </div>
                </section>

                {/* PRIORITIZED GROWTH ACTIONS */}
                {primaryAction && (
                    <section style={styles.actionSystem}>
                        <div style={styles.primaryActionCard}>
                            <div style={styles.actionHeader}>
                                <span style={styles.primaryLabel}>Primary Growth Focus</span>
                                <span style={{
                                    ...styles.priorityBadge, 
                                    color: progressColor, 
                                    backgroundColor: `${progressColor}15`,
                                    borderColor: `${progressColor}30`
                                }}>ACTIVE TARGET</span>
                            </div>
                            
                            <div style={styles.targetContent}>
                                <span style={styles.targetLabel}>{safeRender(primaryAction.metric.toUpperCase())}</span>
                                <div style={styles.targetComparison}>
                                    <span style={styles.targetValue}>{safeRender(primaryAction.current)}</span>
                                    <span style={styles.targetArrow}>→</span>
                                    <span style={{...styles.targetValue, color: progressColor}}>{safeRender(primaryAction.target)}</span>
                                </div>
                            </div>

                            <div style={styles.progressContainer}>
                                <div style={styles.progressBar}>
                                    <div style={{
                                        ...styles.progressFill, 
                                        width: `${primaryAction.progress}%`,
                                        backgroundColor: progressColor,
                                        boxShadow: `0 0 12px ${progressColor}40`
                                    }} />
                                </div>
                                <span style={{...styles.progressText, color: progressColor}}>{safeRender(primaryAction.progress.toFixed(0))}% to performance threshold</span>
                                <span style={styles.gapSubtext}>Performance gap: {safeRender(Math.max(0, 100 - primaryAction.progress).toFixed(0))}% remaining</span>
                            </div>
                        </div>
                        
                        {secondaryActions.length > 0 && (
                            <div style={styles.secondaryActionsGrid}>
                                {secondaryActions.map((action, i) => (
                                    <div key={i} style={styles.secondaryActionCard}>
                                        <span style={styles.secondaryIcon}>⚖️</span>
                                        <p style={styles.secondaryText}>{safeRender(action)}</p>
                                    </div>
                                ))}
                            </div>
                        )}
                    </section>
                )}

                {/* BEST PERFORMANCE WINDOW */}
                {bestDayInsight && (
                    <div style={styles.bestWindowCard}>
                        <span style={styles.bestWindowHeader}>BEST PERFORMING DAY</span>
                        <h3 style={styles.bestWindowDay}>{safeRender(bestDayInsight.day)}</h3>
                        <span style={styles.bestWindowLift}>
                            +{safeRender(bestDayInsight.lift)}% vs weekly average
                        </span>
                    </div>
                )}

                {/* REVENUE DEPENDENCY RISK */}
                {riskInsight && (
                    <div style={styles.riskCard}>
                        <div style={styles.riskHeader}>
                            <span style={styles.riskTitle}>Revenue Stability</span>
                            <span style={styles.riskBadge(riskInsight.riskLevel)}>
                                {safeRender(riskInsight.riskLevel)} RISK
                            </span>
                        </div>
                        <div style={styles.riskMetrics}>
                            <div style={styles.riskMetric}>
                                <span style={styles.riskLabel}>Top supporter</span>
                                <span style={styles.riskValue}>{safeRender(riskInsight.top1.toFixed(1))}%</span>
                            </div>
                            {riskInsight.uniqueSupporters === 1 ? (
                                <div style={{ ...styles.riskMetric, borderLeft: '1px solid rgba(255,255,255,0.1)', paddingLeft: '1.5rem', justifyContent: 'center' }}>
                                    <span style={{ ...styles.riskLabel, color: '#f87171' }}>Single-Supporter Revenue Model</span>
                                </div>
                            ) : (
                                <div style={styles.riskMetric}>
                                    <span style={styles.riskLabel}>Top 3 supporters</span>
                                    <span style={styles.riskValue}>{safeRender(riskInsight.top3.toFixed(1))}%</span>
                                </div>
                            )}
                        </div>
                    </div>
                )}

                {/* DIVERSIFICATION TARGET */}
                {riskInsight && riskInsight.top1 > 60 && (
                    <div style={styles.targetCard}>
                        <div style={styles.targetHeader}>
                            <span style={styles.diversificationTargetLabel}>DIVERSIFICATION TARGET</span>
                            <span style={styles.riskTierLabel(riskInsight.riskLevel)}>{safeRender(riskInsight.riskLevel)} RISK TIER</span>
                        </div>
                        <div style={styles.targetMetricRow}>
                            <div style={styles.targetStat}>
                                <span style={styles.statLabel}>Unique paying supporters</span>
                                <div style={styles.statValueRow}>
                                    <span style={styles.statValue}>{safeRender(riskInsight.uniqueSupporters)}</span>
                                    <span style={styles.statSeparator}>/</span>
                                    <span style={styles.statTarget}>3</span>
                                </div>
                            </div>
                            <div style={styles.targetProgressContainer}>
                                <div style={styles.targetProgressHeader}>
                                    <span style={styles.targetProgressText}>
                                        {safeRender(Math.min(100, (riskInsight.uniqueSupporters / 3) * 100).toFixed(0))}% to target
                                    </span>
                                </div>
                                <div style={styles.targetProgressTrack}>
                                    <div style={styles.targetProgressBar(Math.min(100, (riskInsight.uniqueSupporters / 3) * 100))} />
                                </div>
                            </div>
                        </div>
                    </div>
                )}

                {/* PRICING INTELLIGENCE */}
                {pricingStrategyInsight && (
                    <div style={{ ...styles.pricingCard, borderLeft: `4px solid ${pricingStrategyInsight.color}` }}>
                        <div style={styles.pricingHeader}>
                            <span style={styles.pricingTargetLabel}>PRICING INTELLIGENCE</span>
                            <span style={styles.strategyBadge(pricingStrategyInsight.color)}>
                                {safeRender((pricingStrategyInsight.strategy || "").replace('_', ' '))}
                            </span>
                        </div>
                        <p style={styles.pricingAction}>{safeRender(pricingStrategyInsight.action)}</p>
                        <div style={styles.pricingFooter}>
                            <span style={styles.impactLabel}>EXPECTED IMPACT:</span>
                            <span style={styles.impactValue(pricingStrategyInsight.impact)}>{safeRender(pricingStrategyInsight.impact)}</span>
                        </div>

                        {pricingStrategyInsight.projection && (
                            <div style={{
                                ...styles.projectionBlock,
                                borderLeft: pricingStrategyInsight.projection.isRiskImproved ? '3px solid #10b981' : 'none',
                                paddingLeft: pricingStrategyInsight.projection.isRiskImproved ? '1rem' : '0',
                                transition: 'all 0.3s ease'
                            }}>
                                <span style={styles.projectionTitle}>Revenue Projection</span>
                                <div style={styles.projectionGrid}>
                                    <div style={styles.projectionItem}>
                                        <span style={styles.projectionLabel}>Projected Monthly Revenue</span>
                                        <span style={styles.projectionValue}>€{safeRender((pricingStrategyInsight.projection.revenue || 0).toFixed(2))}</span>
                                    </div>
                                    <div style={styles.projectionItem}>
                                        <span style={styles.projectionLabel}>Revenue Increase</span>
                                        <div style={{ display: 'flex', flexDirection: 'column' }}>
                                            <span style={styles.projectionValue}>+€{safeRender((pricingStrategyInsight.projection.absoluteIncrease || 0).toFixed(2))}</span>
                                            <span style={{ ...styles.projectionDelta, fontSize: '0.75rem', marginTop: '2px' }}>
                                                +{safeRender((pricingStrategyInsight.projection.delta || 0).toFixed(1))}% {pricingStrategyInsight.projection.delta >= 300 ? '(capped)' : ''}
                                            </span>
                                        </div>
                                    </div>
                                    <div style={styles.projectionItem}>
                                        <span style={styles.projectionLabel}>Risk Comparison</span>
                                        <div style={{ display: 'flex', alignItems: 'center', gap: '8px', marginTop: '4px' }}>
                                            <span style={{ 
                                                fontSize: '0.75rem', 
                                                fontWeight: '700',
                                                color: pricingStrategyInsight.projection.currentRiskTier === 'CRITICAL' ? '#ef4444' : 
                                                       pricingStrategyInsight.projection.currentRiskTier === 'HIGH' ? '#f59e0b' : 
                                                       pricingStrategyInsight.projection.currentRiskTier === 'MODERATE' ? '#6366f1' : '#10b981'
                                            }}>
                                                {safeRender(pricingStrategyInsight.projection.currentRiskTier)}
                                            </span>
                                            <span style={{ color: '#3f3f46', fontSize: '0.8rem' }}>→</span>
                                            <span style={{ 
                                                fontSize: '0.85rem', 
                                                fontWeight: '800',
                                                color: pricingStrategyInsight.projection.riskTier === 'CRITICAL' ? '#ef4444' : 
                                                       pricingStrategyInsight.projection.riskTier === 'MODERATE' ? '#6366f1' : '#10b981'
                                            }}>
                                                {safeRender(pricingStrategyInsight.projection.riskTier)}
                                            </span>
                                        </div>
                                    </div>
                                </div>
                                {!pricingStrategyInsight.projection.isRiskImproved && (
                                    <div style={{ marginTop: '1rem', fontSize: '0.75rem', color: '#71717a', fontStyle: 'italic' }}>
                                        Diversification insufficient to reduce dependency risk.
                                    </div>
                                )}
                            </div>
                        )}

                        {pricingStrategyInsight.confidence && (
                            <div style={styles.confidenceSection}>
                                <div style={styles.confidenceHeader}>
                                    <span style={styles.confidenceLabel}>Projection Confidence</span>
                                    <span style={{
                                        ...styles.confidenceBadge,
                                        color: pricingStrategyInsight.confidence.color,
                                        backgroundColor: `${pricingStrategyInsight.confidence.color}15`,
                                        borderColor: `${pricingStrategyInsight.confidence.color}30`
                                    }}>
                                        {safeRender(pricingStrategyInsight.confidence.tier)}
                                    </span>
                                </div>
                                <p style={styles.confidenceExplanation}>
                                    {safeRender(pricingStrategyInsight.confidence.explanation)}
                                </p>
                            </div>
                        )}
                    </div>
                )}

                {/* TIP FLOOR OPTIMIZATION */}
                {tipFloorInsight && (
                    <div style={{ ...styles.tipFloorCard, borderLeft: `4px solid ${tipFloorInsight.color}` }}>
                        <div style={styles.tipFloorHeader}>
                            <span style={styles.tipFloorTitle}>Tip Floor Optimization</span>
                            <span style={styles.strategyBadge(tipFloorInsight.color)}>
                                {safeRender(tipFloorInsight.riskLevel === 'STRATEGIC HOLD' ? 'STRATEGIC HOLD' : `${tipFloorInsight.riskLevel} RISK`)}
                            </span>
                        </div>
                        <div style={styles.tipFloorMetrics}>
                            <div style={styles.tipFloorMetric}>
                                <span style={styles.tipFloorLabel}>Current Avg Tip</span>
                                <span style={styles.tipFloorValue}>€{safeRender(tipFloorInsight.currentAvgTip.toFixed(2))}</span>
                            </div>
                            <div style={styles.tipFloorMetric}>
                                <span style={styles.tipFloorLabel}>Suggested Floor</span>
                                <span style={styles.tipFloorValue}>
                                    {tipFloorInsight.isBlocked ? 'Adjustment blocked' : `€${safeRender(tipFloorInsight.suggestedTipFloor.toFixed(2))}`}
                                </span>
                            </div>
                            <div style={styles.tipFloorMetric}>
                                <span style={styles.tipFloorLabel}>Revenue Upside</span>
                                <span style={styles.tipFloorUpside}>
                                    {tipFloorInsight.isBlocked ? 'N/A' : (tipFloorInsight.revenueUpside > 0 ? `+€${safeRender(tipFloorInsight.revenueUpside.toFixed(2))}` : '€0.00')}
                                </span>
                            </div>
                        </div>
                        <p style={{ ...styles.tipFloorRecommendation, borderLeftColor: tipFloorInsight.color }}>
                            {safeRender(tipFloorInsight.recommendation)}
                        </p>
                    </div>
                )}

                {/* ADAPTIVE TIP ENGINE */}
                {adaptiveTipInsight && (
                    <div 
                        style={adaptiveTipInsight.status === 'COOLDOWN' ? {
                            borderRadius: '24px',
                            padding: '1.5rem',
                            marginBottom: '1.5rem',
                        } : (adaptiveTipInsight.status === 'CAUTION' ? {
                            ...styles.adaptiveCard,
                            borderColor: '#f59e0b'
                        } : styles.adaptiveCard)}
                        className={adaptiveTipInsight.status === 'COOLDOWN' ? 'cooldown-card' : ''}
                    >
                        <div style={styles.adaptiveHeader}>
                            <span style={styles.adaptiveTitle}>ADAPTIVE TIP ENGINE</span>
                            <span style={{ 
                                ...styles.statusBadge, 
                                backgroundColor: adaptiveTipInsight.status === 'READY'
                                    ? 'rgba(16, 185, 129, 0.1)' 
                                    : (adaptiveTipInsight.status === 'COOLDOWN' || adaptiveTipInsight.status === 'CAUTION')
                                        ? 'rgba(245, 158, 11, 0.1)'
                                        : 'rgba(113, 113, 122, 0.1)',
                                color: adaptiveTipInsight.badgeColor,
                                borderColor: adaptiveTipInsight.badgeColor
                            }}>
                                {safeRender(adaptiveTipInsight.status === 'CAUTION' ? 'Elevated Risk' : (adaptiveTipInsight.status || "").replace('_', ' '))}
                            </span>
                        </div>
                        
                        { (adaptiveTipInsight.status === 'READY' || adaptiveTipInsight.status === 'CAUTION') ? (
                            <div style={styles.adaptiveReadyContent}>
                                {adaptiveTipInsight.status === 'CAUTION' && (
                                    <div style={{ display: 'flex', gap: '1rem', color: '#f59e0b', fontSize: '0.65rem', fontWeight: 800, textTransform: 'uppercase', marginBottom: '-0.5rem' }}>
                                        <span>Risk Score: {safeRender(adaptiveTipInsight.riskScore)}</span>
                                        <span>Momentum: {safeRender((adaptiveTipInsight.momentum || 0) >= 0 ? '↑' : '↓')}</span>
                                    </div>
                                )}
                                <div style={styles.adaptiveActionRow}>
                                    <span style={styles.adaptiveActionLabel}>Suggested Test:</span>
                                    <span style={styles.adaptiveActionValue}>{safeRender(adaptiveTipInsight.suggestedAction)}</span>
                                </div>
                                <div style={styles.adaptiveMetricGrid}>
                                    <div style={styles.adaptiveMetric}>
                                        <span style={styles.adaptiveMetricLabel}>Expected Lift</span>
                                        <span style={styles.adaptiveMetricValue}>{safeRender(adaptiveTipInsight.expectedLift)}</span>
                                    </div>
                                    <div style={styles.adaptiveMetric}>
                                        <span style={styles.adaptiveMetricLabel}>Volume Risk</span>
                                        <span style={styles.adaptiveMetricValue}>{safeRender(adaptiveTipInsight.volumeRisk)}</span>
                                    </div>
                                </div>
                                <button 
                                    onClick={activateTest}
                                    style={styles.adaptiveTestButton}
                                >
                                    Start 15 min test
                                </button>
                            </div>
                        ) : adaptiveTipInsight.status === 'COOLDOWN' ? (() => {
                            const totalSeconds = (adaptiveTipInsight.totalCooldownMinutes || 30) * 60;
                            const currentSeconds = remainingSeconds !== null ? remainingSeconds : (adaptiveTipInsight.remainingMinutes || 0) * 60;
                            const progressPercent = ((totalSeconds - currentSeconds) / totalSeconds) * 100;
                            
                            const radius = 52;
                            const circumference = 2 * Math.PI * radius;
                            const dashOffset = (1 - progressPercent / 100) * circumference;
                            
                            return (
                                <div style={styles.adaptiveCooldownContent}>
                                    <div style={styles.cooldownVisualContainer}>
                                        <div className="cooldown-visual">
                                            <svg width="120" height="120" viewBox="0 0 120 120">
                                                <defs>
                                                    <linearGradient id="cooldownGradient" x1="0%" y1="0%" x2="100%" y2="100%">
                                                        <stop offset="0%" stopColor="#f59e0b" />
                                                        <stop offset="100%" stopColor="#ef4444" />
                                                    </linearGradient>
                                                </defs>
                                                <circle
                                                    cx="60"
                                                    cy="60"
                                                    r={radius}
                                                    fill="transparent"
                                                    stroke="rgba(255, 255, 255, 0.05)"
                                                    strokeWidth="8"
                                                />
                                                <circle
                                                    cx="60"
                                                    cy="60"
                                                    r={radius}
                                                    fill="transparent"
                                                    stroke="url(#cooldownGradient)"
                                                    strokeWidth="8"
                                                    strokeDasharray={circumference}
                                                    strokeDashoffset={dashOffset}
                                                    strokeLinecap="round"
                                                    transform="rotate(-90 60 60)"
                                                    style={{ transition: 'stroke-dashoffset 1s linear' }}
                                                />
                                            </svg>
                                            <div className="cooldown-time">
                                                {formatTime(currentSeconds)}
                                            </div>
                                        </div>
                                        <p style={{ ...styles.adaptiveReasonText, margin: 0, textAlign: 'center' }}>
                                            <span style={{ ...styles.reasonLabel, color: '#f59e0b' }}>Cooldown active.</span> Re-evaluates shortly.
                                        </p>
                                    </div>
                                </div>
                            );
                        })() : (
                            <div style={styles.adaptiveNotReadyContent}>
                                <p style={styles.adaptiveReasonText}>
                                    <span style={styles.reasonLabel}>Condition required:</span> {adaptiveTipInsight.reason}
                                </p>
                            </div>
                        )}
                    </div>
                )}

                {/* WHALE ACTIVITY MONITOR */}
                {whaleActivity.length > 0 && (
                    <div style={styles.whaleCard}>
                        <span style={styles.whaleTitle}>Whale Activity Monitor</span>
                        <div style={styles.whaleList}>
                            {whaleActivity.map((whale, idx) => {
                                const recommendation = 
                                    whale.status === 'GROWING' ? "Consider loyalty reward or exclusive stream invite." :
                                    whale.status === 'STABLE' ? "Maintain consistent engagement and recognition." :
                                    whale.status === 'DECLINING' ? "Schedule direct interaction or exclusive offer within 7 days." :
                                    "Send personalized message or comeback incentive immediately.";

                                return (
                                    <div key={idx} style={styles.whaleRow}>
                                        <div style={styles.whaleMainRow}>
                                            <div style={styles.whaleInfo}>
                                                <span style={styles.whaleName}>{whale.name}</span>
                                                <span style={styles.whaleShare}>{whale.share.toFixed(1)}% revenue share</span>
                                            </div>
                                            <span style={styles.whaleStatusBadge(whale.status)}>
                                                {whale.status}
                                            </span>
                                        </div>
                                        <div style={styles.whaleRecommendation}>
                                            <span style={styles.recommendationLabel}>ACTION:</span>
                                            {recommendation}
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    </div>
                )}

                {/* MONETIZATION LADDER */}
                {recentTransactions.length > 0 && (
                    <div style={styles.ladderCard}>
                        <span style={styles.ladderTitle}>Monetization Ladder</span>
                        {uniqueSupporters >= 3 && monetizationLadder ? (
                            <>
                                <div style={styles.ladderBars}>
                                    {monetizationLadder.tiers.map((tier: any, i: number) => (
                                        <div key={i} style={styles.ladderBarRow}>
                                            <div style={styles.ladderBarLabel}>
                                                <span>{tier.label}</span>
                                                <span>{tier.count} users ({tier.percentage.toFixed(1)}%)</span>
                                            </div>
                                            <div style={styles.ladderBarTrack}>
                                                <div style={styles.ladderBarFill(tier.percentage, tier.color)} />
                                            </div>
                                        </div>
                                    ))}
                                </div>
                                <div style={styles.untappedBadge}>
                                    <span style={{ fontWeight: '800', marginRight: '0.5rem', fontSize: '0.65rem', color: '#71717A' }}>UNTAPPED:</span>
                                    Largest growth opportunity in <span style={{ color: '#fff', fontWeight: '700' }}>{monetizationLadder.untapped}</span> segment.
                                </div>
                            </>
                        ) : (
                            <div style={styles.placeholderContent}>
                                <p style={styles.placeholderMessage}>Insufficient supporter base for segmentation analysis.</p>
                                <p style={styles.placeholderSubtext}>Acquire at least 3 unique spenders to unlock tier distribution insights.</p>
                            </div>
                        )}
                    </div>
                )}

                {/* 3. PERFORMANCE INSIGHT */}
                {performanceInsight && (
                    <div style={styles.performanceInsight}>
                        <span style={styles.insightIcon}>💡</span>
                        {safeRender(performanceInsight)}
                    </div>
                )}

                {/* 3. CONNECTION STATUS BAR */}
                <div style={styles.connectionBar}>
                    <div style={styles.connectionInfo}>
                        <span style={styles.stripeIcon}>S</span>
                        <div>
                            <span style={styles.connectionTitle}>Stripe Connect Channel</span>
                            <p style={styles.connectionStatus}>
                                {stripeStatus === StripeOnboardingStatus.VERIFIED ? 'Verified & Active' : (stripeStatus === StripeOnboardingStatus.PENDING ? 'Onboarding in Progress' : 'Not Configured')}
                            </p>
                        </div>
                    </div>
                    {stripeStatus !== StripeOnboardingStatus.VERIFIED && (
                        <button 
                            onClick={handleConnectStripe} 
                            style={styles.setupButton}
                            disabled={onboardingLoading}
                        >
                            {onboardingLoading ? 'Loading...' : 'Complete Setup'}
                        </button>
                    )}
                </div>

                {/* 4. INSIGHTS STRIP */}
                <div style={styles.insightsStrip}>
                    <div style={styles.insightItem}>
                        <span style={styles.insightLabel}>Avg. per Sale</span>
                        <span style={styles.insightValue}>€{safeRender(avgEarnings.toFixed(2))}</span>
                    </div>
                    <div style={styles.insightItem}>
                        <span style={styles.insightLabel}>Held Entries</span>
                        <span style={styles.insightValue}>{safeRender(pendingCount)}</span>
                    </div>
                    <div style={styles.insightItem}>
                        <span style={styles.insightLabel}>Locked Ratio</span>
                        <span style={styles.insightValue}>{safeRender(lockedRatio.toFixed(1))}%</span>
                    </div>
                    <div style={styles.insightItem}>
                        <span style={styles.insightLabel}>Live Tips</span>
                        <span style={styles.insightValue}>{safeRender(liveTipCount)}</span>
                    </div>
                    {stabilityInsight && (
                        <div style={styles.insightItem}>
                            <span style={styles.insightLabel}>Stability</span>
                            <span style={styles.insightValue}>{safeRender(stabilityInsight)}</span>
                        </div>
                    )}
                    <div style={styles.insightItem}>
                        <span style={styles.insightLabel}>Recent Ledger</span>
                        <span style={styles.insightValue}>{safeRender(recentTransactions.length)} tx</span>
                    </div>
                    {revenueConcentrationInsight && (
                        <div style={{ ...styles.insightItem, flex: '2', minWidth: '240px' }}>
                            <span style={styles.insightLabel}>Concentration</span>
                            <span style={{ ...styles.insightValue, fontSize: '0.9rem', lineHeight: '1.4' }}>
                                {safeRender(revenueConcentrationInsight)}
                            </span>
                        </div>
                    )}
                    {incomeInsight && (
                        <div style={{ ...styles.insightItem, flex: '2', minWidth: '240px' }}>
                            <span style={styles.insightLabel}>Income Insight</span>
                            <span style={{ ...styles.insightValue, fontSize: '0.9rem', lineHeight: '1.4' }}>
                                {safeRender(incomeInsight)}
                            </span>
                        </div>
                    )}
                </div>

                {/* 5. MODERN LEDGER LIST */}
                <section style={styles.ledgerContainer}>
                    <div style={styles.ledgerHeader}>
                        <h3 style={styles.ledgerTitle}>Transaction Ledger</h3>
                    </div>
                    <div style={styles.ledgerList}>
                        {loading && recentTransactions.length === 0 ? (
                            <div style={styles.loadingState}>Analyzing ledger...</div>
                        ) : recentTransactions && recentTransactions.length > 0 ? (
                            recentTransactions.map((tx) => (
                                <div key={tx.id} style={styles.ledgerRow}>
                                    <div style={styles.ledgerIconCol}>
                                        <div style={styles.sourceIcon(tx.sourceType)}>
                                            {safeRender(tx.sourceType ? tx.sourceType[0] : '?')}
                                        </div>
                                    </div>
                                    <div style={styles.ledgerMainCol}>
                                        <span style={styles.ledgerSource}>{safeRender((tx.sourceType || "").replace('_', ' '))}</span>
                                        <span style={styles.ledgerDate}>
                                            {safeRender(tx.createdAt ? new Date(tx.createdAt).toLocaleDateString() : 'N/A')} • {safeRender(tx.createdAt ? new Date(tx.createdAt).toLocaleTimeString([], {hour: '2-digit', minute:'2-digit'}) : '')}
                                        </span>
                                    </div>
                                    <div style={styles.ledgerAmountCol}>
                                        <span style={styles.ledgerAmount}>+€{safeRender((tx.netAmount || 0).toFixed(2))}</span>
                                        <span style={tx.locked ? styles.ledgerStatusHeld : styles.ledgerStatusOk}>
                                            {safeRender(tx.locked ? 'Held' : 'Settled')}
                                        </span>
                                    </div>
                                </div>
                            ))
                        ) : (
                            <div style={{ padding: '4rem 0' }}>
                                <EmptyState 
                                    message="No financial activity detected in this period."
                                    icon="📉"
                                    showLogo={false}
                                />
                            </div>
                        )}
                    </div>
                </section>
            </main>
        </div>
    );
};

const styles: { [key: string]: any } = {
    layout: {
        display: 'flex',
        minHeight: 'calc(100vh - 64px)',
        backgroundColor: '#050505',
        color: '#F4F4F5',
        fontFamily: 'system-ui, -apple-system, sans-serif',
    },
    main: {
        flex: 1,
        padding: '2.5rem',
        maxWidth: '1000px',
        margin: '0 auto',
        overflowY: 'auto',
    },
    header: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-end',
        marginBottom: '2.5rem',
    },
    title: {
        fontSize: '2.25rem',
        fontWeight: '800',
        margin: 0,
        letterSpacing: '-0.03em',
    },
    subtitle: {
        fontSize: '0.875rem',
        color: '#71717A',
        margin: '0.25rem 0 0 0',
        fontWeight: '500',
    },
    refreshButton: {
        padding: '0.5rem 1rem',
        backgroundColor: 'rgba(255, 255, 255, 0.03)',
        border: '1px solid rgba(255, 255, 255, 0.08)',
        borderRadius: '10px',
        fontSize: '0.75rem',
        fontWeight: '700',
        color: '#71717A',
        cursor: 'pointer',
        transition: 'all 0.2s ease',
        textTransform: 'uppercase',
        letterSpacing: '0.05em',
    },
    heroSection: {
        backgroundColor: '#0C0C0E',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        padding: '2rem',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        flexWrap: 'wrap',
        gap: '2rem',
        marginBottom: '1.5rem',
        boxShadow: '0 20px 50px rgba(0,0,0,0.3)',
    },
    heroMain: {
        flex: 1,
        minWidth: '280px',
    },
    heroLabelRow: {
        display: 'flex',
        alignItems: 'center',
        gap: '0.75rem',
        marginBottom: '0.5rem',
    },
    heroLabel: {
        fontSize: '0.75rem',
        fontWeight: '700',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.05em',
    },
    shieldIcon: {
        fontSize: '10px',
        fontWeight: '900',
        color: '#10b981',
        backgroundColor: 'rgba(16, 185, 129, 0.1)',
        padding: '2px 6px',
        borderRadius: '4px',
    },
    heroAmount: {
        fontSize: '3.5rem',
        fontWeight: '800',
        margin: 0,
        letterSpacing: '-0.04em',
        fontVariantNumeric: 'tabular-nums',
    },
    sessionDeltaContainer: {
        display: 'flex',
        alignItems: 'center',
        gap: '0.4rem',
        marginTop: '0.5rem',
        color: '#10b981',
        fontWeight: '700',
        fontSize: '0.9rem',
    },
    sessionIcon: {
        fontSize: '1.1rem',
    },
    performanceInsight: {
        backgroundColor: 'rgba(99, 102, 241, 0.05)',
        border: '1px solid rgba(99, 102, 241, 0.1)',
        borderRadius: '16px',
        padding: '0.75rem 1.25rem',
        marginBottom: '1.5rem',
        fontSize: '0.875rem',
        color: '#A1A1AA',
        display: 'flex',
        alignItems: 'center',
        gap: '0.75rem',
        fontWeight: '500',
    },
    insightIcon: {
        fontSize: '1.1rem',
    },
    actionSystem: {
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
        marginBottom: '1.5rem',
    },
    primaryActionCard: {
        background: 'linear-gradient(90deg, rgba(99, 102, 241, 0.08) 0%, rgba(99, 102, 241, 0.02) 100%)',
        borderRadius: '24px',
        border: '1px solid rgba(99, 102, 241, 0.15)',
        padding: '1.75rem',
        boxShadow: '0 10px 30px rgba(99, 102, 241, 0.08), 0 10px 30px rgba(0,0,0,0.4)',
        width: '100%',
    },
    actionHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '1.25rem',
    },
    primaryLabel: {
        fontSize: '0.86rem',
        fontWeight: '800',
        color: '#818cf8',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    priorityBadge: {
        fontSize: '0.65rem',
        fontWeight: '900',
        color: '#ef4444',
        backgroundColor: 'rgba(239, 68, 68, 0.1)',
        padding: '3px 8px',
        borderRadius: '6px',
        border: '1px solid rgba(239, 68, 68, 0.2)',
    },
    targetContent: {
        display: 'flex',
        flexDirection: 'column',
        gap: '0.5rem',
        marginBottom: '1.5rem',
    },
    targetComparison: {
        display: 'flex',
        alignItems: 'baseline',
        gap: '0.75rem',
    },
    targetLabel: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    targetValue: {
        fontSize: '1.5rem',
        fontWeight: '800',
        color: '#fff',
        letterSpacing: '-0.02em',
    },
    targetArrow: {
        fontSize: '1.25rem',
        color: '#3F3F46',
        fontWeight: '600',
    },
    progressContainer: {
        display: 'flex',
        flexDirection: 'column',
        gap: '0.5rem',
    },
    progressBar: {
        height: '6px',
        backgroundColor: 'rgba(255, 255, 255, 0.05)',
        borderRadius: '3px',
        overflow: 'hidden',
    },
    progressFill: {
        height: '100%',
        backgroundColor: '#6366f1',
        borderRadius: '3px',
        transition: 'all 1s ease-out',
    },
    progressText: {
        fontSize: '0.75rem',
        fontWeight: '600',
        color: '#818cf8',
    },
    gapSubtext: {
        fontSize: '0.7rem',
        color: '#71717A',
        marginTop: '0.15rem',
        fontWeight: '500',
        opacity: 0.8,
    },
    bestWindowCard: {
        backgroundColor: 'rgba(255, 255, 255, 0.02)',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        padding: '1.5rem',
        marginBottom: '1.5rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.4rem',
    },
    bestWindowHeader: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    bestWindowDay: {
        fontSize: '1.5rem',
        fontWeight: '800',
        color: '#fff',
        margin: 0,
    },
    bestWindowLift: {
        fontSize: '0.875rem',
        fontWeight: '600',
        color: '#10b981',
    },
    riskCard: {
        backgroundColor: 'rgba(255, 255, 255, 0.02)',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        padding: '1.5rem',
        marginBottom: '1.5rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '1.25rem',
    },
    riskHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    riskTitle: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    riskBadge: (level: string) => ({
        fontSize: '0.65rem',
        fontWeight: '900',
        padding: '3px 8px',
        borderRadius: '6px',
        border: '1px solid',
        backgroundColor: level === 'CRITICAL' ? 'rgba(239, 68, 68, 0.1)' :
                         level === 'HIGH' ? 'rgba(245, 158, 11, 0.1)' :
                         level === 'MODERATE' ? 'rgba(99, 102, 241, 0.1)' :
                         'rgba(16, 185, 129, 0.1)',
        color: level === 'CRITICAL' ? '#ef4444' :
               level === 'HIGH' ? '#f59e0b' :
               level === 'MODERATE' ? '#6366f1' :
               '#10b981',
        borderColor: level === 'CRITICAL' ? 'rgba(239, 68, 68, 0.2)' :
                     level === 'HIGH' ? 'rgba(245, 158, 11, 0.2)' :
                     level === 'MODERATE' ? 'rgba(99, 102, 241, 0.2)' :
                     'rgba(16, 185, 129, 0.2)',
    }),
    riskMetrics: {
        display: 'flex',
        gap: '2rem',
    },
    riskMetric: {
        display: 'flex',
        flexDirection: 'column',
        gap: '0.25rem',
    },
    whaleCard: {
        backgroundColor: 'rgba(255, 255, 255, 0.02)',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        padding: '1.5rem',
        marginBottom: '1.5rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '1.25rem',
    },
    whaleTitle: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    whaleList: {
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
    },
    whaleRow: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'stretch',
        gap: '0.75rem',
        padding: '1rem',
        backgroundColor: 'rgba(255, 255, 255, 0.03)',
        borderRadius: '16px',
    },
    whaleMainRow: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    whaleRecommendation: {
        fontSize: '0.75rem',
        color: '#A1A1AA',
        borderTop: '1px solid rgba(255, 255, 255, 0.05)',
        paddingTop: '0.75rem',
        lineHeight: '1.4',
    },
    recommendationLabel: {
        color: '#71717A',
        fontWeight: '800',
        marginRight: '0.5rem',
        fontSize: '0.65rem',
    },
    whaleInfo: {
        display: 'flex',
        flexDirection: 'column',
        gap: '0.2rem',
    },
    whaleName: {
        fontSize: '0.9rem',
        fontWeight: '700',
        color: '#fff',
    },
    whaleShare: {
        fontSize: '0.75rem',
        color: '#71717A',
    },
    whaleStatusBadge: (status: string) => ({
        fontSize: '0.6rem',
        fontWeight: '900',
        padding: '3px 8px',
        borderRadius: '6px',
        backgroundColor: status === 'GROWING' ? 'rgba(16, 185, 129, 0.1)' :
                         status === 'STABLE' ? 'rgba(99, 102, 241, 0.1)' :
                         status === 'DECLINING' ? 'rgba(245, 158, 11, 0.1)' :
                         'rgba(239, 68, 68, 0.1)',
        color: status === 'GROWING' ? '#10b981' :
               status === 'STABLE' ? '#6366f1' :
               status === 'DECLINING' ? '#f59e0b' :
               '#ef4444',
        border: `1px solid ${status === 'GROWING' ? 'rgba(16, 185, 129, 0.2)' :
                             status === 'STABLE' ? 'rgba(99, 102, 241, 0.2)' :
                             status === 'DECLINING' ? 'rgba(245, 158, 11, 0.2)' :
                             'rgba(239, 68, 68, 0.2)'}`,
    }),
    ladderCard: {
        backgroundColor: 'rgba(255, 255, 255, 0.02)',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        padding: '1.5rem',
        marginBottom: '1.5rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '1.25rem',
    },
    ladderTitle: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    ladderBars: {
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
    },
    placeholderContent: {
        display: 'flex',
        flexDirection: 'column',
        gap: '0.5rem',
        padding: '1rem 0',
    },
    placeholderMessage: {
        fontSize: '0.9rem',
        fontWeight: '600',
        color: '#A1A1AA',
        margin: 0,
    },
    placeholderSubtext: {
        fontSize: '0.75rem',
        color: '#71717A',
        margin: 0,
        lineHeight: '1.4',
    },
    ladderBarRow: {
        display: 'flex',
        flexDirection: 'column',
        gap: '0.4rem',
    },
    ladderBarLabel: {
        display: 'flex',
        justifyContent: 'space-between',
        fontSize: '0.75rem',
        fontWeight: '600',
        color: '#A1A1AA',
    },
    ladderBarTrack: {
        height: '8px',
        backgroundColor: 'rgba(255, 255, 255, 0.05)',
        borderRadius: '4px',
        overflow: 'hidden',
    },
    ladderBarFill: (width: number, color: string) => ({
        height: '100%',
        width: `${width}%`,
        backgroundColor: color,
        borderRadius: '4px',
        transition: 'width 1s ease-out',
    }),
    untappedBadge: {
        marginTop: '0.5rem',
        padding: '0.75rem 1rem',
        backgroundColor: 'rgba(99, 102, 241, 0.05)',
        borderRadius: '12px',
        fontSize: '0.8rem',
        color: '#818cf8',
        border: '1px solid rgba(99, 102, 241, 0.1)',
        fontWeight: '500',
    },
    riskLabel: {
        fontSize: '0.65rem',
        color: '#71717A',
        fontWeight: '600',
        textTransform: 'uppercase',
    },
    riskValue: {
        fontSize: '1.25rem',
        fontWeight: '800',
        color: '#fff',
    },
    secondaryActionsGrid: {
        display: 'grid',
        gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))',
        gap: '1rem',
    },
    secondaryActionCard: {
        backgroundColor: 'rgba(255, 255, 255, 0.02)',
        borderRadius: '16px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        padding: '1rem 1.25rem',
        display: 'flex',
        alignItems: 'center',
        gap: '0.75rem',
    },
    secondaryIcon: {
        fontSize: '1rem',
        opacity: 0.7,
    },
    secondaryText: {
        fontSize: '0.875rem',
        color: '#A1A1AA',
        lineHeight: '1.4',
        margin: 0,
    },
    heroActionRow: {
        marginTop: '1.5rem',
    },
    transferButton: {
        padding: '0.75rem 1.5rem',
        backgroundColor: '#6366f1',
        color: '#fff',
        border: 'none',
        borderRadius: '12px',
        fontSize: '0.9rem',
        fontWeight: '700',
        cursor: 'pointer',
        boxShadow: '0 8px 20px rgba(99, 102, 241, 0.25)',
    },
    transferButtonDisabled: {
        padding: '0.75rem 1.5rem',
        backgroundColor: 'rgba(255, 255, 255, 0.03)',
        color: '#3F3F46',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        borderRadius: '12px',
        fontSize: '0.875rem',
        fontWeight: '600',
        cursor: 'not-allowed',
    },
    heroStats: {
        display: 'flex',
        flexDirection: 'column',
        gap: '1.25rem',
        minWidth: '180px',
    },
    heroMiniStat: {
        display: 'flex',
        flexDirection: 'column',
    },
    miniLabel: {
        fontSize: '0.7rem',
        color: '#52525B',
        fontWeight: '700',
        textTransform: 'uppercase',
        marginBottom: '0.25rem',
    },
    miniValue: {
        fontSize: '1.25rem',
        fontWeight: '700',
        fontVariantNumeric: 'tabular-nums',
    },
    miniValueAmber: {
        fontSize: '1.25rem',
        fontWeight: '700',
        color: '#f59e0b',
        fontVariantNumeric: 'tabular-nums',
    },
    connectionBar: {
        backgroundColor: '#0C0C0E',
        borderRadius: '16px',
        padding: '1rem 1.25rem',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '1.5rem',
    },
    connectionInfo: {
        display: 'flex',
        alignItems: 'center',
        gap: '1rem',
    },
    stripeIcon: {
        width: '32px',
        height: '32px',
        backgroundColor: '#635BFF',
        borderRadius: '8px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontWeight: '900',
        fontSize: '1.25rem',
    },
    connectionTitle: {
        fontSize: '0.875rem',
        fontWeight: '700',
        display: 'block',
    },
    connectionStatus: {
        fontSize: '0.75rem',
        color: '#71717A',
        margin: 0,
    },
    setupButton: {
        padding: '0.5rem 1rem',
        backgroundColor: 'rgba(255, 255, 255, 0.05)',
        border: '1px solid rgba(255, 255, 255, 0.1)',
        borderRadius: '8px',
        color: '#fff',
        fontSize: '0.75rem',
        fontWeight: '700',
        cursor: 'pointer',
    },
    insightsStrip: {
        display: 'flex',
        gap: '1.5rem',
        marginBottom: '2rem',
        padding: '0 0.5rem',
    },
    insightItem: {
        display: 'flex',
        flexDirection: 'column',
        gap: '2px',
    },
    insightLabel: {
        fontSize: '0.65rem',
        color: '#52525B',
        fontWeight: '700',
        textTransform: 'uppercase',
    },
    insightValue: {
        fontSize: '0.875rem',
        fontWeight: '600',
        color: '#D4D4D8',
    },
    ledgerContainer: {
        backgroundColor: '#0C0C0E',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        overflow: 'hidden',
    },
    ledgerHeader: {
        padding: '1.5rem',
        borderBottom: '1px solid rgba(255, 255, 255, 0.05)',
    },
    ledgerTitle: {
        margin: 0,
        fontSize: '1rem',
        fontWeight: '700',
    },
    ledgerList: {
        display: 'flex',
        flexDirection: 'column',
    },
    ledgerRow: {
        display: 'flex',
        alignItems: 'center',
        padding: '1.25rem 1.5rem',
        borderBottom: '1px solid rgba(255, 255, 255, 0.02)',
        transition: 'background-color 0.2s ease',
        cursor: 'default',
    },
    ledgerIconCol: {
        marginRight: '1rem',
    },
    sourceIcon: (source: string) => ({
        width: '40px',
        height: '40px',
        backgroundColor: 'rgba(255, 255, 255, 0.03)',
        borderRadius: '10px',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        fontSize: '1.1rem',
        fontWeight: '700',
        color: source === 'SUBSCRIPTION' ? '#6366f1' : '#a855f7',
        border: '1px solid rgba(255, 255, 255, 0.05)',
    }),
    ledgerMainCol: {
        flex: 1,
    },
    ledgerSource: {
        fontSize: '0.9375rem',
        fontWeight: '600',
        display: 'block',
        textTransform: 'capitalize',
    },
    ledgerDate: {
        fontSize: '0.75rem',
        color: '#52525B',
        display: 'block',
        marginTop: '2px',
    },
    ledgerAmountCol: {
        textAlign: 'right',
    },
    ledgerAmount: {
        fontSize: '1rem',
        fontWeight: '700',
        display: 'block',
        color: '#F4F4F5',
        fontVariantNumeric: 'tabular-nums',
    },
    ledgerStatusOk: {
        fontSize: '10px',
        fontWeight: '700',
        color: '#10b981',
        textTransform: 'uppercase',
    },
    ledgerStatusHeld: {
        fontSize: '10px',
        fontWeight: '700',
        color: '#f59e0b',
        textTransform: 'uppercase',
    },
    loadingState: {
        padding: '3rem',
        textAlign: 'center',
        color: '#52525B',
        fontSize: '0.875rem',
        fontStyle: 'italic',
    },
    errorBanner: {
        padding: '1rem',
        backgroundColor: 'rgba(239, 68, 68, 0.1)',
        color: '#f87171',
        borderRadius: '12px',
        marginBottom: '2rem',
        border: '1px solid rgba(239, 68, 68, 0.2)',
        fontSize: '0.875rem',
        fontWeight: '600',
    },
    targetCard: {
        backgroundColor: 'rgba(255, 255, 255, 0.02)',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        padding: '1.5rem',
        marginBottom: '1.5rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '1.25rem',
    },
    targetHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    diversificationTargetLabel: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    riskTierLabel: (level: string) => ({
        fontSize: '0.65rem',
        fontWeight: '700',
        color: level === 'CRITICAL' ? '#f87171' : level === 'HIGH' ? '#fbbf24' : level === 'MODERATE' ? '#6366f1' : '#10b981',
        backgroundColor: level === 'CRITICAL' ? 'rgba(239, 68, 68, 0.1)' : level === 'HIGH' ? 'rgba(245, 158, 11, 0.1)' : level === 'MODERATE' ? 'rgba(99, 102, 241, 0.1)' : 'rgba(16, 185, 129, 0.1)',
        padding: '2px 8px',
        borderRadius: '4px',
        textTransform: 'uppercase',
    }),
    targetMetricRow: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'flex-end',
        gap: '2rem',
    },
    targetStat: {
        flex: 1,
    },
    statLabel: {
        display: 'block',
        fontSize: '0.75rem',
        color: '#A1A1AA',
        marginBottom: '0.5rem',
    },
    statValueRow: {
        display: 'flex',
        alignItems: 'baseline',
        gap: '0.25rem',
    },
    statValue: {
        fontSize: '1.5rem',
        fontWeight: '800',
        color: '#fff',
    },
    statSeparator: {
        fontSize: '1rem',
        color: '#3F3F46',
        fontWeight: '600',
    },
    statTarget: {
        fontSize: '1.1rem',
        color: '#71717A',
        fontWeight: '600',
    },
    targetProgressContainer: {
        flex: 1.5,
        display: 'flex',
        flexDirection: 'column',
        gap: '0.5rem',
    },
    targetProgressHeader: {
        display: 'flex',
        justifyContent: 'flex-end',
    },
    targetProgressText: {
        fontSize: '0.75rem',
        fontWeight: '700',
        color: '#10b981',
    },
    targetProgressTrack: {
        height: '6px',
        backgroundColor: 'rgba(255, 255, 255, 0.05)',
        borderRadius: '3px',
        overflow: 'hidden',
    },
    targetProgressBar: (progress: number) => ({
        width: `${progress}%`,
        height: '100%',
        backgroundColor: '#10b981',
        borderRadius: '3px',
        transition: 'width 0.8s ease-out',
        boxShadow: '0 0 8px rgba(16, 185, 129, 0.3)',
    }),
    pricingCard: {
        backgroundColor: 'rgba(255, 255, 255, 0.02)',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        padding: '1.5rem',
        marginBottom: '1.5rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
    },
    pricingHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    pricingTargetLabel: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    strategyBadge: (color: string) => ({
        fontSize: '0.65rem',
        fontWeight: '700',
        color: color,
        backgroundColor: `${color}25`,
        padding: '2px 8px',
        borderRadius: '4px',
        textTransform: 'uppercase',
    }),
    pricingAction: {
        fontSize: '0.9375rem',
        color: '#D4D4D8',
        margin: 0,
        lineHeight: '1.5',
    },
    pricingFooter: {
        display: 'flex',
        alignItems: 'center',
        gap: '0.5rem',
        marginTop: '0.5rem',
    },
    impactLabel: {
        fontSize: '0.65rem',
        fontWeight: '700',
        color: '#52525B',
    },
    impactValue: (impact: string) => ({
        fontSize: '0.65rem',
        fontWeight: '800',
        color: impact === 'HIGH' ? '#f87171' : impact === 'MEDIUM' ? '#6366f1' : '#10b981',
    }),
    projectionBlock: {
        marginTop: '1rem',
        paddingTop: '1rem',
        borderTop: '1px solid rgba(255, 255, 255, 0.05)',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.75rem',
    },
    projectionTitle: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    projectionGrid: {
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: '1rem',
    },
    projectionItem: {
        display: 'flex',
        flexDirection: 'column',
        gap: '0.25rem',
    },
    projectionLabel: {
        fontSize: '0.65rem',
        color: '#71717A',
        fontWeight: '600',
    },
    projectionValue: {
        fontSize: '1rem',
        fontWeight: '700',
        color: '#fff',
    },
    projectionDelta: {
        fontSize: '0.75rem',
        fontWeight: '600',
        color: '#10b981',
    },
    confidenceSection: {
        marginTop: '1rem',
        paddingTop: '1rem',
        borderTop: '1px solid rgba(255, 255, 255, 0.05)',
        display: 'flex',
        flexDirection: 'column',
        gap: '0.5rem',
    },
    confidenceHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    confidenceLabel: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    confidenceBadge: {
        fontSize: '0.65rem',
        fontWeight: '900',
        padding: '2px 8px',
        borderRadius: '6px',
        border: '1px solid',
        textTransform: 'uppercase',
    },
    confidenceExplanation: {
        fontSize: '0.75rem',
        color: '#71717A',
        margin: 0,
        lineHeight: '1.4',
        fontStyle: 'italic',
    },
    tipFloorCard: {
        backgroundColor: 'rgba(255, 255, 255, 0.02)',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        padding: '1.5rem',
        marginBottom: '1.5rem',
        display: 'flex',
        flexDirection: 'column',
        gap: '1rem',
    },
    tipFloorHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
    },
    tipFloorTitle: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    tipFloorMetrics: {
        display: 'grid',
        gridTemplateColumns: 'repeat(3, 1fr)',
        gap: '1.5rem',
        marginTop: '0.5rem',
    },
    tipFloorMetric: {
        display: 'flex',
        flexDirection: 'column',
        gap: '0.25rem',
    },
    tipFloorLabel: {
        fontSize: '0.65rem',
        color: '#71717A',
        fontWeight: '600',
    },
    tipFloorValue: {
        fontSize: '1.125rem',
        fontWeight: '700',
        color: '#fff',
    },
    tipFloorUpside: {
        fontSize: '0.875rem',
        fontWeight: '700',
        color: '#10b981',
    },
    tipFloorRecommendation: {
        fontSize: '0.875rem',
        color: '#D4D4D8',
        marginTop: '0.5rem',
        lineHeight: '1.4',
        padding: '0.75rem',
        backgroundColor: 'rgba(255, 255, 255, 0.03)',
        borderRadius: '12px',
        borderLeft: '3px solid',
        margin: 0,
    },
    adaptiveCard: {
        backgroundColor: 'rgba(255, 255, 255, 0.02)',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
        padding: '1.5rem',
        marginBottom: '1.5rem',
    },
    adaptiveHeader: {
        display: 'flex',
        justifyContent: 'space-between',
        alignItems: 'center',
        marginBottom: '1.25rem',
    },
    adaptiveTitle: {
        fontSize: '0.65rem',
        fontWeight: '800',
        color: '#71717A',
        textTransform: 'uppercase',
        letterSpacing: '0.1em',
    },
    adaptiveReadyContent: {
        display: 'flex',
        flexDirection: 'column',
        gap: '1.25rem',
    },
    adaptiveActionRow: {
        padding: '1rem',
        backgroundColor: 'rgba(16, 185, 129, 0.05)',
        borderRadius: '12px',
        borderLeft: '3px solid #10b981',
    },
    adaptiveActionLabel: {
        display: 'block',
        fontSize: '0.65rem',
        color: '#10b981',
        fontWeight: '700',
        textTransform: 'uppercase',
        marginBottom: '0.25rem',
    },
    adaptiveActionValue: {
        fontSize: '0.875rem',
        color: '#D4D4D8',
        lineHeight: '1.4',
    },
    adaptiveMetricGrid: {
        display: 'grid',
        gridTemplateColumns: '1fr 1fr',
        gap: '1.5rem',
    },
    adaptiveMetric: {
        display: 'flex',
        flexDirection: 'column',
        gap: '0.25rem',
    },
    adaptiveMetricLabel: {
        fontSize: '0.65rem',
        color: '#71717A',
        fontWeight: '600',
        textTransform: 'uppercase',
    },
    adaptiveMetricValue: {
        fontSize: '1.125rem',
        fontWeight: '700',
        color: '#fff',
    },
    adaptiveNotReadyContent: {
        padding: '1rem',
        backgroundColor: 'rgba(255, 255, 255, 0.03)',
        borderRadius: '12px',
        borderLeft: '3px solid #71717A',
    },
    adaptiveReasonText: {
        fontSize: '0.875rem',
        color: '#A1A1AA',
        margin: 0,
        lineHeight: '1.4',
    },
    reasonLabel: {
        fontWeight: '700',
        color: '#71717A',
        textTransform: 'uppercase',
        fontSize: '0.7rem',
        marginRight: '0.4rem',
    },
    adaptiveTestButton: {
        backgroundColor: '#10b981',
        color: '#fff',
        border: 'none',
        borderRadius: '12px',
        padding: '0.875rem 1.5rem',
        fontSize: '0.875rem',
        fontWeight: '700',
        cursor: 'pointer',
        marginTop: '0.5rem',
        transition: 'all 0.2s ease',
        textAlign: 'center' as const,
    },
    adaptiveCooldownContent: {
        padding: '1.5rem',
        backgroundColor: 'rgba(255, 255, 255, 0.02)',
        borderRadius: '24px',
        border: '1px solid rgba(255, 255, 255, 0.05)',
    },
    cooldownVisualContainer: {
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        gap: '1.25rem',
        padding: '1rem 0',
    },
};

export default CreatorEarningsDashboard;
