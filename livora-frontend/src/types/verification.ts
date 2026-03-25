export enum VerificationStatus {
  PENDING = 'PENDING',
  UNDER_REVIEW = 'UNDER_REVIEW',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED',
  SUSPENDED = 'SUSPENDED'
}

export enum DocumentType {
  PASSPORT = 'PASSPORT',
  ID_CARD = 'ID_CARD',
  DRIVER_LICENSE = 'DRIVER_LICENSE'
}

export interface CreatorVerificationRequest {
  legalFirstName: string;
  legalLastName: string;
  dateOfBirth: string;
  country: string;
  documentType: DocumentType;
  idDocumentUrl: string;
  documentBackUrl?: string;
  selfieDocumentUrl: string;
}

export interface CreatorVerificationResponse {
  id: string;
  creatorId: number;
  legalFirstName: string;
  legalLastName: string;
  dateOfBirth: string;
  country: string;
  documentType: DocumentType;
  idDocumentUrl: string;
  documentBackUrl?: string;
  selfieDocumentUrl: string;
  status: VerificationStatus;
  rejectionReason?: string;
  createdAt: string;
  updatedAt: string;
}
