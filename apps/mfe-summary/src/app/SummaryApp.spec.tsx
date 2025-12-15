import { render, screen } from '@testing-library/react';
import { QueryClientProvider, QueryClient } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { SummaryApp } from './SummaryApp';

// Mock the shared-state hooks
vi.mock('@mono-repo/shared-state', () => ({
  useSummary: vi.fn(),
}));

import { useSummary } from '@mono-repo/shared-state';

const mockUseSummary = vi.mocked(useSummary);

const createWrapper = () => {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: {
        retry: false,
      },
    },
  });

  return ({ children }: { children: React.ReactNode }) => (
    <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>
  );
};

describe('SummaryApp', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render loading state', () => {
    mockUseSummary.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useSummary>);

    render(
      <SummaryApp memberId="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText('Loading summary...')).toBeTruthy();
  });

  it('should render error state', () => {
    mockUseSummary.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Failed to fetch'),
    } as ReturnType<typeof useSummary>);

    render(
      <SummaryApp memberId="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText(/Failed to load summary/)).toBeTruthy();
  });

  it('should render empty state when no data', () => {
    mockUseSummary.mockReturnValue({
      data: null,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useSummary>);

    render(
      <SummaryApp memberId="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText('No summary data available')).toBeTruthy();
  });

  it('should render summary data', () => {
    mockUseSummary.mockReturnValue({
      data: {
        memberId: 'test-123',
        name: 'John Doe',
        status: 'Active',
        lastUpdated: '2024-01-15T00:00:00Z',
        metrics: [
          { key: 'visits', label: 'Total Visits', value: 42 },
          { key: 'balance', label: 'Balance', value: '$1,234' },
        ],
      },
      isLoading: false,
      error: null,
    } as ReturnType<typeof useSummary>);

    render(
      <SummaryApp memberId="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText('Member Summary')).toBeTruthy();
    expect(screen.getByText('John Doe')).toBeTruthy();
    expect(screen.getByText('Active')).toBeTruthy();
    expect(screen.getByText('Total Visits')).toBeTruthy();
    expect(screen.getByText('42')).toBeTruthy();
    expect(screen.getByText('Balance')).toBeTruthy();
    expect(screen.getByText('$1,234')).toBeTruthy();
  });

  it('should display operator info for proxy personas', () => {
    mockUseSummary.mockReturnValue({
      data: {
        memberId: 'test-123',
        name: 'John Doe',
        status: 'Active',
        lastUpdated: '2024-01-15T00:00:00Z',
        metrics: [],
      },
      isLoading: false,
      error: null,
    } as ReturnType<typeof useSummary>);

    render(
      <SummaryApp
        memberId="test-123"
        persona="agent"
        operatorId="op-456"
        operatorName="Jane Smith"
      />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText(/Viewing as: Jane Smith/)).toBeTruthy();
    expect(screen.getByText(/agent/)).toBeTruthy();
  });

  it('should not display operator info for HSID personas', () => {
    mockUseSummary.mockReturnValue({
      data: {
        memberId: 'test-123',
        name: 'John Doe',
        status: 'Active',
        lastUpdated: '2024-01-15T00:00:00Z',
        metrics: [],
      },
      isLoading: false,
      error: null,
    } as ReturnType<typeof useSummary>);

    render(
      <SummaryApp memberId="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.queryByText(/Viewing as:/)).toBeNull();
  });

  it('should call useSummary with correct memberId', () => {
    mockUseSummary.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useSummary>);

    render(
      <SummaryApp memberId="member-xyz" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(mockUseSummary).toHaveBeenCalledWith('member-xyz');
  });
});
