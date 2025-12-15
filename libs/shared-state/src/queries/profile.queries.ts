import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useApiClient } from '../api/ApiClientContext';

/**
 * Query key factory for profile-related queries
 */
export const profileKeys = {
  all: ['profile'] as const,
  detail: (memberId: string) => [...profileKeys.all, memberId] as const,
};

/**
 * Profile data response type
 */
export interface ProfileData {
  memberId: string;
  firstName: string;
  lastName: string;
  email: string;
  phone?: string;
  address?: {
    street: string;
    city: string;
    state: string;
    zip: string;
  };
  preferences?: Record<string, unknown>;
}

/**
 * Profile update payload
 */
export interface ProfileUpdatePayload {
  phone?: string;
  address?: ProfileData['address'];
  preferences?: Record<string, unknown>;
}

/**
 * Hook to fetch member profile
 * Uses API client from context (supports isolated web components)
 */
export const useProfile = (memberId: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: profileKeys.detail(memberId),
    queryFn: () => api.get<ProfileData>(`/api/mfe/profile/${memberId}`),
    enabled: !!memberId && enabled,
  });
};

/**
 * Hook to update member profile
 */
export const useUpdateProfile = (memberId: string) => {
  const api = useApiClient();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ProfileUpdatePayload) =>
      api.put<ProfileData>(`/api/mfe/profile/${memberId}`, payload),
    onSuccess: (data) => {
      queryClient.setQueryData(profileKeys.detail(memberId), data);
    },
  });
};
