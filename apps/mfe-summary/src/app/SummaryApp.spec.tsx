import { render, screen, fireEvent } from '@testing-library/react';
import { QueryClientProvider, QueryClient } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { HealthSummaryApp } from './SummaryApp';

// Mock the shared-state hooks
vi.mock('@mono-repo/shared-state', () => ({
  useUserInfo: vi.fn(),
  useApiClient: vi.fn(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  })),
  // Health hooks used by child sections
  useImmunizations: vi.fn(() => ({
    data: [],
    isLoading: false,
    error: null,
  })),
  useAllergies: vi.fn(() => ({
    data: [],
    isLoading: false,
    error: null,
  })),
  useMedications: vi.fn(() => ({
    data: [],
    isLoading: false,
    error: null,
  })),
}));

// eslint-disable-next-line import/first
import { useImmunizations, useAllergies, useMedications } from '@mono-repo/shared-state';

const mockUseImmunizations = vi.mocked(useImmunizations);
const mockUseAllergies = vi.mocked(useAllergies);
const mockUseMedications = vi.mocked(useMedications);

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

describe('HealthSummaryApp', () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it('should render the Health Summary header', () => {
    render(
      <HealthSummaryApp memberId="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText('Health Summary')).toBeTruthy();
  });

  it('should render all three tabs', () => {
    render(
      <HealthSummaryApp memberId="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText('Immunizations')).toBeTruthy();
    expect(screen.getByText('Allergies')).toBeTruthy();
    expect(screen.getByText('Medications')).toBeTruthy();
  });

  it('should display operator info for proxy personas', () => {
    render(
      <HealthSummaryApp
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

  it('should not display operator info for individual personas', () => {
    render(
      <HealthSummaryApp memberId="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.queryByText(/Viewing as:/)).toBeNull();
  });

  it('should switch to Allergies tab when clicked', () => {
    render(
      <HealthSummaryApp memberId="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    const allergiesTab = screen.getByText('Allergies');
    fireEvent.click(allergiesTab);

    // Verify the tab is now active by checking the class
    expect(allergiesTab.className).toContain('activeTab');
  });

  it('should switch to Medications tab when clicked', () => {
    render(
      <HealthSummaryApp memberId="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    const medicationsTab = screen.getByText('Medications');
    fireEvent.click(medicationsTab);

    // Verify the tab is now active
    expect(medicationsTab.className).toContain('activeTab');
  });

  it('should call useImmunizations with correct memberId', () => {
    render(
      <HealthSummaryApp memberId="member-xyz" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(mockUseImmunizations).toHaveBeenCalledWith('member-xyz');
  });
});
