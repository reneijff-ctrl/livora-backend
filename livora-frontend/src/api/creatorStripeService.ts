import apiClient from './apiClient';

export enum StripeOnboardingStatus {
  NOT_STARTED = 'NOT_STARTED',
  PENDING = 'PENDING',
  VERIFIED = 'VERIFIED'
}

export interface StripeStatusResponse {
  stripeAccountId: string;
  onboardingStatus: StripeOnboardingStatus;
}

export interface OnboardingLinkResponse {
  onboardingUrl: string;
}

const creatorStripeService = {
  getStatus: async (): Promise<StripeStatusResponse> => {
    const token = localStorage.getItem("token");
    if (!token) return { stripeAccountId: "", onboardingStatus: StripeOnboardingStatus.NOT_STARTED };
    const response = await apiClient.get<StripeStatusResponse>('/creator/stripe/status');
    return response.data;
  },

  createOnboardingLink: async (): Promise<OnboardingLinkResponse> => {
    const response = await apiClient.post<OnboardingLinkResponse>('/creator/stripe/onboard');
    return response.data;
  }
};

export default creatorStripeService;
