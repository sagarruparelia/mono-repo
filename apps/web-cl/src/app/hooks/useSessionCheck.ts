import { useQuery } from '@tanstack/react-query';
import { api, SessionCheckResponse } from '@mono-repo/shared-state';

/**
 * Hook for lightweight session validation
 * Used by AuthGuard and SessionExpiryWarning in web-cl only
 *
 * This hook is NOT in shared-state because:
 * - MFEs deployed to external partners don't need session checking
 * - External partners handle their own auth
 * - Only web-cl (HSID direct login) needs route guards
 */
export const useSessionCheck = (options?: { enabled?: boolean }) => {
  return useQuery({
    queryKey: ['session', 'check'],
    queryFn: () => api.get<SessionCheckResponse>('/api/auth/check'),
    staleTime: 30 * 1000,       // Fresh for 30 seconds
    refetchInterval: 60 * 1000, // Poll every minute
    retry: 1,                   // Single retry on failure
    ...options,
  });
};
