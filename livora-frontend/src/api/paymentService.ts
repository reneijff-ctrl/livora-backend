import apiClient from './apiClient';
import { SubscriptionStatus } from '../auth/AuthContext';

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
  async createCheckoutSession(): Promise<CheckoutResponse> {
    const response = await apiClient.post<CheckoutResponse>('/api/payments/checkout');
    return response.data;
  },

  /**
   * Fetches the current user's subscription status and details.
   */
  async getMySubscription(): Promise<SubscriptionResponse> {
    const response = await apiClient.get<SubscriptionResponse>('/api/subscriptions/me');
    return response.data;
  },

  /**
   * Cancels the current user's active subscription.
   */
  async cancelSubscription(): Promise<void> {
    await apiClient.post('/api/subscriptions/cancel');
  },

  /**
   * Resumes a canceled subscription.
   */
  async resumeSubscription(): Promise<void> {
    await apiClient.post('/api/subscriptions/resume');
  },

  /**
   * Opens the customer billing portal.
   */
  async openBillingPortal(): Promise<void> {
    const response = await apiClient.get<{ url: string }>('/api/billing/portal');
    if (response.data.url) {
      window.location.href = response.data.url;
    }
  },

  /**
   * Fetches invoice history for the current user.
   */
  async fetchInvoices(): Promise<Invoice[]> {
    const response = await apiClient.get<Invoice[]>('/api/billing/invoices');
    return response.data;
  },
};

export default paymentService;
