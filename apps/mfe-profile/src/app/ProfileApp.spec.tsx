import { render, screen, fireEvent, waitFor } from '@testing-library/react';
import { QueryClientProvider, QueryClient } from '@tanstack/react-query';
import { describe, it, expect, vi, beforeEach } from 'vitest';
import { ProfileApp } from './ProfileApp';

// Mock the shared-state hooks
vi.mock('@mono-repo/shared-state', () => ({
  useProfile: vi.fn(),
  useUpdateProfile: vi.fn(),
  useUserInfo: vi.fn(),
  useApiClient: vi.fn(() => ({
    get: vi.fn(),
    post: vi.fn(),
    put: vi.fn(),
    delete: vi.fn(),
  })),
  // Document hooks used by DocumentsSection
  useDocuments: vi.fn(() => ({
    data: [],
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  })),
  useUploadDocument: vi.fn(() => ({
    mutateAsync: vi.fn(),
    isPending: false,
  })),
  useDeleteDocument: vi.fn(() => ({
    mutateAsync: vi.fn(),
    isPending: false,
  })),
  getDocumentDownloadUrl: () => '/api/documents/test',
  formatFileSize: (size: number) => `${size} bytes`,
}));

// eslint-disable-next-line import/first
import { useProfile, useUpdateProfile } from '@mono-repo/shared-state';

const mockUseProfile = vi.mocked(useProfile);
const mockUseUpdateProfile = vi.mocked(useUpdateProfile);

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

const mockProfileData = {
  memberEid: 'test-123',
  firstName: 'John',
  lastName: 'Doe',
  email: 'john@example.com',
  phone: '+1-555-0123',
  address: {
    street: '123 Main St',
    city: 'Anytown',
    state: 'CA',
    zip: '90210',
  },
};

describe('ProfileApp', () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockUseUpdateProfile.mockReturnValue({
      mutateAsync: vi.fn(),
      isPending: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof useUpdateProfile>);
  });

  it('should render loading state', () => {
    mockUseProfile.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useProfile>);

    render(
      <ProfileApp memberEid="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText('Loading profile...')).toBeTruthy();
  });

  it('should render error state', () => {
    mockUseProfile.mockReturnValue({
      data: undefined,
      isLoading: false,
      error: new Error('Failed to fetch'),
    } as ReturnType<typeof useProfile>);

    render(
      <ProfileApp memberEid="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText(/Failed to load profile/)).toBeTruthy();
  });

  it('should render empty state when no data', () => {
    mockUseProfile.mockReturnValue({
      data: null,
      isLoading: false,
      error: null,
    } as unknown as ReturnType<typeof useProfile>);

    render(
      <ProfileApp memberEid="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText('No profile data available')).toBeTruthy();
  });

  it('should render profile data', () => {
    mockUseProfile.mockReturnValue({
      data: mockProfileData,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useProfile>);

    render(
      <ProfileApp memberEid="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText('Member Profile')).toBeTruthy();
    expect(screen.getByText('John Doe')).toBeTruthy();
    expect(screen.getByText('john@example.com')).toBeTruthy();
    expect(screen.getByText('+1-555-0123')).toBeTruthy();
    expect(screen.getByText(/123 Main St/)).toBeTruthy();
  });

  it('should display avatar with initials', () => {
    mockUseProfile.mockReturnValue({
      data: mockProfileData,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useProfile>);

    render(
      <ProfileApp memberEid="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    // Avatar should show "JD" (first letter of first and last name)
    expect(screen.getByText('JD')).toBeTruthy();
  });

  it('should display operator info for proxy personas', () => {
    mockUseProfile.mockReturnValue({
      data: mockProfileData,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useProfile>);

    render(
      <ProfileApp
        memberEid="test-123"
        persona="agent"
        operatorId="op-456"
        operatorName="Jane Smith"
      />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByText(/Viewing as: Jane Smith/)).toBeTruthy();
    expect(screen.getByText(/agent/)).toBeTruthy();
  });

  it('should show edit button', () => {
    mockUseProfile.mockReturnValue({
      data: mockProfileData,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useProfile>);

    render(
      <ProfileApp memberEid="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(screen.getByRole('button', { name: 'Edit' })).toBeTruthy();
  });

  it('should enter edit mode when Edit button is clicked', () => {
    mockUseProfile.mockReturnValue({
      data: mockProfileData,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useProfile>);

    render(
      <ProfileApp memberEid="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));

    // Should show Save and Cancel buttons
    expect(screen.getByRole('button', { name: 'Save' })).toBeTruthy();
    expect(screen.getByRole('button', { name: 'Cancel' })).toBeTruthy();

    // Should show phone input
    expect(screen.getByDisplayValue('+1-555-0123')).toBeTruthy();
  });

  it('should cancel edit mode', () => {
    mockUseProfile.mockReturnValue({
      data: mockProfileData,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useProfile>);

    render(
      <ProfileApp memberEid="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    // Enter edit mode
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));

    // Cancel
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));

    // Should be back to view mode
    expect(screen.getByRole('button', { name: 'Edit' })).toBeTruthy();
    expect(screen.queryByRole('button', { name: 'Save' })).toBeNull();
  });

  it('should call mutateAsync on save', async () => {
    const mockMutateAsync = vi.fn().mockResolvedValue(mockProfileData);
    mockUseUpdateProfile.mockReturnValue({
      mutateAsync: mockMutateAsync,
      isPending: false,
      isError: false,
      error: null,
    } as unknown as ReturnType<typeof useUpdateProfile>);

    mockUseProfile.mockReturnValue({
      data: mockProfileData,
      isLoading: false,
      error: null,
    } as ReturnType<typeof useProfile>);

    render(
      <ProfileApp memberEid="test-123" persona="individual" />,
      { wrapper: createWrapper() }
    );

    // Enter edit mode
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));

    // Change phone number
    const phoneInput = screen.getByDisplayValue('+1-555-0123');
    fireEvent.change(phoneInput, { target: { value: '+1-555-9999' } });

    // Save
    fireEvent.click(screen.getByRole('button', { name: 'Save' }));

    await waitFor(() => {
      expect(mockMutateAsync).toHaveBeenCalledWith(
        expect.objectContaining({
          phone: '+1-555-9999',
        })
      );
    });
  });

  it('should call useProfile with correct memberEid', () => {
    mockUseProfile.mockReturnValue({
      data: undefined,
      isLoading: true,
      error: null,
    } as ReturnType<typeof useProfile>);

    render(
      <ProfileApp memberEid="member-xyz" persona="individual" />,
      { wrapper: createWrapper() }
    );

    expect(mockUseProfile).toHaveBeenCalledWith('member-xyz');
  });
});
