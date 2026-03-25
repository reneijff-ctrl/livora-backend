import { UserStatus, Role } from './index';

export enum FraudRiskLevel {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
  CRITICAL = 'CRITICAL'
}

export interface UserAdminResponse {
  id: number;
  email: string;
  role: Role;
  status: UserStatus;
  fraudRiskLevel: FraudRiskLevel;
  payoutsEnabled: boolean;
  shadowbanned: boolean;
  createdAt: string;
}

export interface FailedLogin {
  email: string | null;
  timestamp: string;
  ipAddress: string | null;
  userAgent: string | null;
}

export interface PaymentAnomaly {
  paymentId: string;
  userEmail: string;
  amount: number;
  currency: string;
  riskLevel: FraudRiskLevel;
  createdAt: string;
}

export interface ChargebackHistory {
  userEmail: string;
  amount: number;
  currency: string;
  reason: string;
  status: string;
  createdAt: string;
  fraudRisk: FraudRiskLevel;
}

export interface FraudSignal {
  id: string;
  userId: number;
  riskLevel: string;
  type: string;
  reason: string;
  source: string;
  resolved: boolean;
  createdAt: string;
}

export interface FraudEvent {
  id: string;
  userId: string;
  eventType: string;
  reason: string;
  createdAt: string;
}

export interface RiskScore {
  userId: string;
  score: number;
  breakdown: string;
  lastEvaluatedAt: string;
}
