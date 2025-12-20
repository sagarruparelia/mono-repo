import { useQuery } from '@tanstack/react-query';
import { useApiClient } from '../api/ApiClientContext';

/**
 * Query key factory for summary-related queries
 */
export const summaryKeys = {
  all: ['summary'] as const,
  detail: (memberEid: string) => [...summaryKeys.all, memberEid] as const,
};

/**
 * Summary data response type
 */
export interface SummaryData {
  memberEid: string;
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
export const useSummary = (memberEid: string, enabled = true) => {
  const api = useApiClient();

  return useQuery({
    queryKey: summaryKeys.detail(memberEid),
    queryFn: () => api.get<SummaryData>(`/api/mfe/summary/${memberEid}`),
    enabled: !!memberEid && enabled,
  });
};
