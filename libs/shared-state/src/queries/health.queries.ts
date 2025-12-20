import { useQuery } from '@tanstack/react-query';
import { useApiClient } from '../api/ApiClientContext';

/**
 * Query key factory for health-related queries
 */
export const healthKeys = {
  all: ['health'] as const,
  immunizations: (memberEid: string) => [...healthKeys.all, 'immunizations', memberEid] as const,
  allergies: (memberEid: string) => [...healthKeys.all, 'allergies', memberEid] as const,
  medications: (memberEid: string) => [...healthKeys.all, 'medications', memberEid] as const,
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
export const useImmunizations = (memberEid: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: healthKeys.immunizations(memberEid),
    queryFn: () => api.get<Immunization[]>(`/api/mfe/immunizations/${memberEid}`),
    enabled: !!memberEid && enabled,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

/**
 * Hook to fetch allergy records for a member
 * Uses API client from context (supports web components with serviceBaseUrl)
 */
export const useAllergies = (memberEid: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: healthKeys.allergies(memberEid),
    queryFn: () => api.get<Allergy[]>(`/api/mfe/allergies/${memberEid}`),
    enabled: !!memberEid && enabled,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};

/**
 * Hook to fetch medication records for a member
 * Uses API client from context (supports web components with serviceBaseUrl)
 */
export const useMedications = (memberEid: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: healthKeys.medications(memberEid),
    queryFn: () => api.get<Medication[]>(`/api/mfe/medications/${memberEid}`),
    enabled: !!memberEid && enabled,
    staleTime: 5 * 60 * 1000, // 5 minutes
  });
};
