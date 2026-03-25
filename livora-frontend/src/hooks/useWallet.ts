import { useCallback } from 'react';
import { useAuth } from '../auth/useAuth';

/**
 * useWallet hook provides access to the user's token balance and common wallet operations.
 * It automatically uses the AuthContext for centralized state management.
 */
export const useWallet = () => {
  const { tokenBalance, refreshTokenBalance } = useAuth();

  const refreshBalance = useCallback(async () => {
    return await refreshTokenBalance();
  }, [refreshTokenBalance]);

  /**
   * Checks if the user has enough tokens for a given amount.
   * @param amount The required token amount.
   * @returns true if the balance is sufficient, false otherwise.
   */
  const hasSufficientBalance = (amount: number): boolean => {
    return tokenBalance >= amount;
  };

  /**
   * Logic to handle common wallet-related warnings or UI feedback can be added here.
   */

  return {
    balance: tokenBalance,
    refreshBalance,
    hasSufficientBalance
  };
};

export default useWallet;
