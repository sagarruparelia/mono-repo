import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useApiClient } from '../api/ApiClientContext';

/**
 * Query key factory for profile-related queries
 */
export const profileKeys = {
  all: ['profile'] as const,
  detail: (memberEid: string) => [...profileKeys.all, memberEid] as const,
};

/**
 * Profile data response type
 */
export interface ProfileData {
  memberEid: string;
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
export const useProfile = (memberEid: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: profileKeys.detail(memberEid),
    queryFn: () => api.get<ProfileData>(`/api/mfe/profile/${memberEid}`),
    enabled: !!memberEid && enabled,
  });
};

/**
 * Hook to update member profile
 */
export const useUpdateProfile = (memberEid: string) => {
  const api = useApiClient();
  const queryClient = useQueryClient();

  return useMutation({
    mutationFn: (payload: ProfileUpdatePayload) =>
      api.put<ProfileData>(`/api/mfe/profile/${memberEid}`, payload),
    onSuccess: (data) => {
      queryClient.setQueryData(profileKeys.detail(memberEid), data);
    },
  });
};
