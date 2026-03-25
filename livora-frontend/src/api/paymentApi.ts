import apiClient from './apiClient';

export const createTipCheckout = async (creatorId: number, amount: number) => {
  try {
    const response = await apiClient.post('/payments/tip', { creatorId, amount });
    return response.data;
  } catch (error) {
    console.error('Payment checkout error:', error);
    throw error;
  }
};

export const verifyPayment = async (sessionId: string) => {
  try {
    const response = await apiClient.get(`/payments/verify?session_id=${sessionId}`);
    return response.data;
  } catch (error) {
    console.error('Payment verification error:', error);
    throw error;
  }
};
