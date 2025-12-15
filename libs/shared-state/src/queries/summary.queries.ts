import { useQuery } from '@tanstack/react-query';
import { useApiClient } from '../api/ApiClientContext';

/**
 * Query key factory for summary-related queries
 */
export const summaryKeys = {
  all: ['summary'] as const,
  detail: (memberId: string) => [...summaryKeys.all, memberId] as const,
};

/**
 * Summary data response type
 */
export interface SummaryData {
  memberId: string;
  name: string;
  status: string;
  lastUpdated: string;
  metrics: {
    key: string;
    label: string;
    value: string | number;
  }[];
}

/**
 * Hook to fetch member summary
 * Uses API client from context (supports isolated web components)
 */
export const useSummary = (memberId: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: summaryKeys.detail(memberId),
    queryFn: () => api.get<SummaryData>(`/api/mfe/summary/${memberId}`),
    enabled: !!memberId && enabled,
  });
};
