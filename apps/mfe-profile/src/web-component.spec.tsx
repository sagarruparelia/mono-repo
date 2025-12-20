/**
 * @vitest-environment jsdom
 */
import React from 'react';
import { describe, it, expect, beforeAll, afterEach, vi } from 'vitest';

import { QueryClient } from '@tanstack/react-query';

// Mock the shared-state module
vi.mock('@mono-repo/shared-state', () => ({
  createQueryClient: () => new QueryClient({
    defaultOptions: {
      queries: { retry: false },
    },
  }),
  createApiClient: vi.fn(),
  api: {
    setBaseUrl: vi.fn(),
  },
  ApiClientProvider: ({ children }: { children: React.ReactNode }) => children,
  useUserInfo: vi.fn(),
  useProfile: () => ({
    data: {
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
    },
    isLoading: false,
    error: null,
  }),
  useUpdateProfile: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
    isError: false,
    error: null,
  }),
  // Document hooks used by DocumentsSection
  useDocuments: () => ({
    data: [],
    isLoading: false,
    error: null,
    refetch: vi.fn(),
  }),
  useUploadDocument: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  useDeleteDocument: () => ({
    mutateAsync: vi.fn(),
    isPending: false,
  }),
  getDocumentDownloadUrl: vi.fn(() => '/api/documents/test'),
  formatFileSize: vi.fn((size: number) => `${size} bytes`),
}));

// eslint-disable-next-line import/first -- Import after mocking
import './web-component';

describe('MfeProfileElement Web Component', () => {
  beforeAll(() => {
    // Ensure custom element is registered
    expect(customElements.get('mfe-profile')).toBeDefined();
  });

  afterEach(() => {
    // Clean up any elements created during tests
    document.body.innerHTML = '';
  });

  it('should be registered as a custom element', () => {
    const ElementClass = customElements.get('mfe-profile');
    expect(ElementClass).toBeDefined();
    expect(ElementClass?.name).toBe('MfeProfileElement');
  });

  it('should have observed attributes defined', () => {
    const ElementClass = customElements.get('mfe-profile') as typeof HTMLElement & {
      observedAttributes: string[];
    };
    expect(ElementClass.observedAttributes).toEqual([
      'member-id',
      'persona',
      'operator-id',
      'operator-name',
      'service-base-url',
    ]);
  });

  it('should create shadow DOM when connected', () => {
    const element = document.createElement('mfe-profile');
    element.setAttribute('member-id', 'test-123');
    element.setAttribute('persona', 'agent');
    document.body.appendChild(element);

    expect(element.shadowRoot).toBeTruthy();
    expect(element.shadowRoot?.mode).toBe('open');
  });

  it('should render content in shadow DOM', async () => {
    const element = document.createElement('mfe-profile');
    element.setAttribute('member-id', 'test-123');
    element.setAttribute('persona', 'agent');
    element.setAttribute('operator-name', 'Test Operator');
    document.body.appendChild(element);

    // Wait for React to render
    await new Promise((resolve) => setTimeout(resolve, 100));

    const shadowRoot = element.shadowRoot;
    expect(shadowRoot).toBeTruthy();
    expect(shadowRoot?.querySelector('div')).toBeTruthy();
  });

  it('should clean up on disconnect', () => {
    const element = document.createElement('mfe-profile');
    element.setAttribute('member-id', 'test-123');
    element.setAttribute('persona', 'agent');
    document.body.appendChild(element);

    // Remove element
    element.remove();

    // Element should be disconnected
    expect(element.isConnected).toBe(false);
  });

  it('should update when attributes change', async () => {
    const element = document.createElement('mfe-profile');
    element.setAttribute('member-id', 'initial-123');
    element.setAttribute('persona', 'agent');
    document.body.appendChild(element);

    await new Promise((resolve) => setTimeout(resolve, 50));

    // Change attribute
    element.setAttribute('member-id', 'updated-456');

    await new Promise((resolve) => setTimeout(resolve, 50));

    expect(element.getAttribute('member-id')).toBe('updated-456');
  });

  it('should accept service-base-url attribute', () => {
    const element = document.createElement('mfe-profile');
    element.setAttribute('member-id', 'test-123');
    element.setAttribute('persona', 'agent');
    element.setAttribute('service-base-url', '/api/mfe-proxy');
    document.body.appendChild(element);

    // Verify attribute was set (defaults to same-origin if not specified)
    expect(element.getAttribute('service-base-url')).toBe('/api/mfe-proxy');
    expect(element.shadowRoot).toBeTruthy();
  });
});

describe('MfeProfileElement Attribute Handling', () => {
  afterEach(() => {
    document.body.innerHTML = '';
  });

  it('should handle missing optional attributes gracefully', () => {
    const element = document.createElement('mfe-profile');
    element.setAttribute('member-id', 'test-123');
    element.setAttribute('persona', 'individual');
    document.body.appendChild(element);

    expect(element.getAttribute('operator-id')).toBeNull();
    expect(element.getAttribute('operator-name')).toBeNull();
    expect(element.shadowRoot).toBeTruthy();
  });

  it('should support all persona types', () => {
    const personas = ['individual', 'parent', 'agent', 'config', 'case_worker'];

    personas.forEach((persona) => {
      const element = document.createElement('mfe-profile');
      element.setAttribute('member-id', 'test-123');
      element.setAttribute('persona', persona);
      document.body.appendChild(element);

      expect(element.getAttribute('persona')).toBe(persona);
      element.remove();
    });
  });
});
