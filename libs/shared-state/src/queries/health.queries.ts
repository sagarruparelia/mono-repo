import { useQuery } from '@tanstack/react-query';
import { useApiClient } from '../api/ApiClientContext';

/**
 * Query key factory for health-related queries
 */
export const healthKeys = {
  all: ['health'] as const,
  immunizations: (memberId: string) => [...healthKeys.all, 'immunizations', memberId] as const,
  allergies: (memberId: string) => [...healthKeys.all, 'allergies', memberId] as const,
  medications: (memberId: string) => [...healthKeys.all, 'medications', memberId] as const,
};

/**
 * Immunization record type
 */
export interface Immunization {
  id: string;
  name: string;
  date: string;
  provider?: string;
  lotNumber?: string;
}

/**
 * Allergy record type
 */
export interface Allergy {
  id: string;
  allergen: string;
  reaction: string;
  severity: 'mild' | 'moderate' | 'severe';
  onsetDate?: string;
}

/**
 * Medication record type
 */
export interface Medication {
  id: string;
  name: string;
  dosage: string;
  frequency: string;
  prescribedDate: string;
  prescriber?: string;
}

/**
 * Hook to fetch immunization records for a member
 * Uses API client from context (supports web components with serviceBaseUrl)
 */
export const useImmunizations = (memberId: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: healthKeys.immunizations(memberId),
    queryFn: () => api.get<Immunization[]>(`/api/mfe/immunizations/${memberId}`),
    enabled: !!memberId && enabled,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

/**
 * Hook to fetch allergy records for a member
 * Uses API client from context (supports web components with serviceBaseUrl)
 */
export const useAllergies = (memberId: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: healthKeys.allergies(memberId),
    queryFn: () => api.get<Allergy[]>(`/api/mfe/allergies/${memberId}`),
    enabled: !!memberId && enabled,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

/**
 * Hook to fetch medication records for a member
 * Uses API client from context (supports web components with serviceBaseUrl)
 */
export const useMedications = (memberId: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: healthKeys.medications(memberId),
    queryFn: () => api.get<Medication[]>(`/api/mfe/medications/${memberId}`),
    enabled: !!memberId && enabled,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
