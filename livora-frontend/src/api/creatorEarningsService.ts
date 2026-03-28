import apiClient from './apiClient';

const API_BASE_URL = '/creator/earnings';

export enum EarningSource {
    SUBSCRIPTION = 'SUBSCRIPTION',
    TIP = 'TIP',
    PPV = 'PPV',
    HIGHLIGHTED_CHAT = 'HIGHLIGHTED_CHAT',
    CHAT = 'CHAT',
    PRIVATE_SHOW = 'PRIVATE_SHOW',
    CHARGEBACK = 'CHARGEBACK'
}

export interface CreatorEarnings {
    todayTokens: number;
    todayRevenue: number;
    totalTokens: number;
    totalRevenue: number;
    pendingPayout: number;
    lastUpdated: string;
}

export interface PeriodStats {
    totalEarnings: number;
    totalTokens: number;
    revenueBySource: Record<EarningSource, number>;
    tokensBySource: Record<EarningSource, number>;
}

export interface CreatorEarningsReport {
    daily: PeriodStats;
    weekly: PeriodStats;
    monthly: PeriodStats;
}

export interface SummaryStats {
    count: number;
    sum: number;
}

export interface LockedBy {
    payoutHold: SummaryStats;
    fraudHold: SummaryStats;
    payoutRequested: SummaryStats;
    manualAdminLock: SummaryStats;
}

export interface EarningsBreakdown {
    totalEarnings: SummaryStats;
    availableEarnings: SummaryStats;
    lockedEarnings: SummaryStats;
    lockedBy: LockedBy;
}

export interface CreatorEarningEntry {
    id: string;
    grossAmount: number;
    platformFee: number;
    netAmount: number;
    currency: string;
    sourceType: EarningSource;
    createdAt: string;
    locked: boolean;
    supporterName: string;
}

export interface CreatorEarningsOverview {
    totalEarnings: number;
    availableBalance: number;
    pendingBalance: number;
    lastEarnings: CreatorEarningEntry[];
}

const creatorEarningsService = {
    getEarningsOverview: async (): Promise<CreatorEarningsOverview> => {
        const token = localStorage.getItem("token");
        if (!token) return { totalEarnings: 0, availableBalance: 0, pendingBalance: 0, lastEarnings: [] };
        const response = await apiClient.get<CreatorEarningsOverview>(API_BASE_URL);
        return response.data;
    },

    getEarnings: async (): Promise<CreatorEarnings> => {
        const token = localStorage.getItem("token");
        if (!token) return { todayTokens: 0, todayRevenue: 0, totalTokens: 0, totalRevenue: 0, pendingPayout: 0, lastUpdated: new Date().toISOString() };
        const response = await apiClient.get<CreatorEarnings>(`${API_BASE_URL}/legacy`);
        return response.data;
    },

    getEarningsBreakdown: async (): Promise<EarningsBreakdown> => {
        const token = localStorage.getItem("token");
        if (!token) return {
            totalEarnings: { count: 0, sum: 0 },
            availableEarnings: { count: 0, sum: 0 },
            lockedEarnings: { count: 0, sum: 0 },
            lockedBy: {
                payoutHold: { count: 0, sum: 0 },
                fraudHold: { count: 0, sum: 0 },
                payoutRequested: { count: 0, sum: 0 },
                manualAdminLock: { count: 0, sum: 0 }
            }
        };
        const response = await apiClient.get<EarningsBreakdown>(`${API_BASE_URL}/breakdown`);
        return response.data;
    },

    getEarningsReport: async (): Promise<CreatorEarningsReport> => {
        const token = localStorage.getItem("token");
        if (!token) {
            const emptyStats = { totalEarnings: 0, totalTokens: 0, revenueBySource: {} as any, tokensBySource: {} as any };
            return { daily: emptyStats, weekly: emptyStats, monthly: emptyStats };
        }
        const response = await apiClient.get<CreatorEarningsReport>(`${API_BASE_URL}/report`);
        return response.data;
    },

    getRecentTransactions: async (): Promise<CreatorEarningEntry[]> => {
        const token = localStorage.getItem("token");
        if (!token) return [];
        const response = await apiClient.get<CreatorEarningEntry[]>(`${API_BASE_URL}/transactions`);
        return response.data;
    },

    getDailyAnalytics: async (days: number = 30): Promise<any[]> => {
        const token = localStorage.getItem("token");
        if (!token) return [];
        const to = new Date().toISOString().split('T')[0];
        const fromDate = new Date();
        fromDate.setDate(fromDate.getDate() - days);
        const from = fromDate.toISOString().split('T')[0];
        const response = await apiClient.get('/creator/analytics', {
            params: { from, to }
        });
        return Array.isArray(response.data) ? response.data : [];
    }
};

export default creatorEarningsService;
