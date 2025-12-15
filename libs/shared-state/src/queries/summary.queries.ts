import { useQuery } from '@tanstack/react-query';
import { api } from '../api/client';

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
 * Used by mfe-summary
 */
export const useSummary = (memberId: string, enabled = true) => {
  return useQuery({
    queryKey: summaryKeys.detail(memberId),
    queryFn: () => api.get<SummaryData>(`/api/mfe/summary/${memberId}`),
    enabled: !!memberId && enabled,
  });
};
