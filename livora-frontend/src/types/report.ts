export enum ReportReason {
  UNDERAGE = 'UNDERAGE',
  COPYRIGHT = 'COPYRIGHT',
  HARASSMENT = 'HARASSMENT',
  VIOLENCE = 'VIOLENCE',
  NON_CONSENSUAL = 'NON_CONSENSUAL',
  SPAM = 'SPAM',
  OTHER = 'OTHER'
}

export enum ReportStatus {
  PENDING = 'PENDING',
  REVIEWED = 'REVIEWED',
  RESOLVED = 'RESOLVED',
  REJECTED = 'REJECTED'
}

export enum ReportTargetType {
  STREAM = 'STREAM',
  USER = 'USER',
  CHAT_MESSAGE = 'CHAT_MESSAGE'
}

export interface Report {
  id: string;
  reporterUserId?: number;
  reportedUserId: number;
  streamId?: string;
  reason: ReportReason;
  description?: string;
  status: ReportStatus;
  createdAt: string;
  updatedAt: string;
}

export interface CreateReportRequest {
  reportedUserId: number;
  streamId?: string;
  reason: ReportReason;
  description?: string;
}

export interface UpdateReportRequest {
  status: ReportStatus;
}
