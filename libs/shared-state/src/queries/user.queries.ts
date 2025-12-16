import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { api } from '../api/client';
import { useApiClient } from '../api/ApiClientContext';
import { usePersona } from '../stores/auth.store';

/**
 * Query key factory for user-related queries
 */
export const userKeys = {
  all: ['user'] as const,
  profile: () => [...userKeys.all, 'profile'] as const,
  session: () => [...userKeys.all, 'session'] as const,
  info: () => [...userKeys.all, 'info'] as const,
  dependentsMetadata: () => [...userKeys.all, 'dependents-metadata'] as const,
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
 * User info response type - common data needed across dashboard and MFEs
 */
export interface UserInfo {
  userId: string;
  name: string;
  email: string;
  persona: string;
  memberId?: string;
  preferences?: {
    theme?: string;
    language?: string;
    notifications?: boolean;
  };
}

/**
 * Dependent metadata response type
 */
export interface DependentMetadata {
  id: string;
  name: string;
  dateOfBirth?: string;
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

/**
 * Hook to fetch user info
 * Uses API client from context (supports web components with serviceBaseUrl)
 * Data is cached by React Query - subsequent calls won't make duplicate API requests
 */
export const useUserInfo = (enabled = true) => {
  const apiClient = useApiClient();

  return useQuery({
    queryKey: userKeys.info(),
    queryFn: () => apiClient.get<UserInfo>('/api/user-info'),
    enabled,
  });
};

/**
 * Hook to fetch dependent metadata for parent persona
 * Uses API client from context (supports web components with serviceBaseUrl)
 * Only enabled for parent persona - returns empty for other personas
 */
export const useDependentsMetadata = (enabled = true) => {
  const apiClient = useApiClient();
  const persona = usePersona();

  return useQuery({
    queryKey: userKeys.dependentsMetadata(),
    queryFn: () => apiClient.get<DependentMetadata[]>('/api/auth/dependents'),
    enabled: enabled && persona === 'parent',
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
