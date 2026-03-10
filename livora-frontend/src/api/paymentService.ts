import apiClient, { publicApiClient } from './apiClient';
import { SubscriptionStatus } from '../types';

/**
 * Interface for the checkout session response from the backend.
 */
export interface CheckoutResponse {
  redirectUrl: string;
}

/**
 * Interface for the subscription response.
 */
export interface SubscriptionResponse {
  status: SubscriptionStatus;
  currentPeriodEnd: string | null;
  cancelAtPeriodEnd: boolean;
  nextInvoiceDate: string | null;
  paymentMethodBrand: string | null;
  last4: string | null;
}

/**
 * Interface for the invoice data.
 */
export interface Invoice {
  id: string;
  amount: number;
  currency: string;
  status: string;
  date: string;
  pdfUrl: string;
}

/**
 * Interface for the subscription plan data.
 */
export interface SubscriptionPlan {
  id: string;
  name: string;
  price: string;
  currency: string;
  interval: string;
  features: string[];
  isPopular: boolean;
  stripePriceId?: string;
}

/**
 * Payment API service.
 * 
 * Provides methods for:
 * - Creating a Stripe Checkout session
 * - Retrieving the current user's subscription
 * - Managing subscriptions (cancel, resume)
 * - Customer billing portal
 * - Invoice history
 */
const paymentService = {
  /**
   * Initiates a Stripe checkout session for a premium subscription.
   * Returns the URL to redirect the user to Stripe Checkout.
   */
  async createCheckoutSession(planId?: string): Promise<CheckoutResponse> {
    const response = await apiClient.post<CheckoutResponse>('/payments/checkout', { planId });
    return response.data;
  },

  /**
   * Fetches the available subscription plans.
   */
  async getPlans(): Promise<SubscriptionPlan[]> {
    const response = await publicApiClient.get<SubscriptionPlan[]>('/subscriptions/plans');
    return response.data;
  },

  /**
   * Fetches the current user's subscription status and details.
   */
  async getMySubscription(): Promise<SubscriptionResponse> {
    const response = await apiClient.get<SubscriptionResponse>('/subscriptions/me');
    return response.data;
  },

  /**
   * Cancels the current user's active subscription.
   */
  async cancelSubscription(): Promise<void> {
    await apiClient.post('/subscriptions/cancel');
  },

  /**
   * Resumes a canceled subscription.
   */
  async resumeSubscription(): Promise<void> {
    await apiClient.post('/subscriptions/resume');
  },

  /**
   * Opens the customer billing portal.
   */
  async openBillingPortal(): Promise<void> {
    const response = await apiClient.get<{ url: string }>('/billing/portal');
    if (response.data.url) {
      window.location.href = response.data.url;
    }
  },

  /**
   * Fetches invoice history for the current user.
   */
  async fetchInvoices(): Promise<Invoice[]> {
    const response = await apiClient.get<Invoice[]>('/billing/invoices');
    return response.data;
  },
};

export default paymentService;
