import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';

/**
 * Query key factory for user-related queries
 */
export const userKeys = {
  all: ['user'] as const,
  profile: () => [...userKeys.all, 'profile'] as const,
  session: () => [...userKeys.all, 'session'] as const,
};

/**
 * User profile response type
 */
export interface UserProfile {
  sub: string;
  name: string;
  email: string;
  persona: string;
  dependents?: string[];
}

/**
 * Session info response type
 */
export interface SessionInfo {
  sessionId: string;
  userId: string;
  persona: string;
  expiresAt: number;
  createdAt: number;
}

/**
 * Hook to fetch user profile
 */
export const useUserProfile = () => {
  return useQuery({
    queryKey: userKeys.profile(),
    queryFn: () => api.get<UserProfile>('/api/user/profile'),
  });
};

/**
 * Hook to fetch current session info
 */
export const useSessionInfo = () => {
  return useQuery({
    queryKey: userKeys.session(),
    queryFn: () => api.get<SessionInfo>('/api/auth/session'),
    refetchInterval: 60 * 1000, // Refresh every minute to track expiry
  });
};

/**
 * Hook to logout
 */
export const useLogout = () => {
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: () => api.post<void>('/api/auth/logout'),
    onSuccess: () => {
      // Clear all cached queries
      queryClient.clear();
    },
  });
};
