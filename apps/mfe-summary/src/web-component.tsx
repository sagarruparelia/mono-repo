import { createRoot, Root } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient, createApiClient, api } from '@mono-repo/shared-state';
import type { Persona } from '@mono-repo/shared-state';
import { SummaryApp } from './app/SummaryApp';

/**
 * MFE Summary Web Component
 *
 * Usage:
 * <mfe-summary
 *   member-id="123"
 *   persona="agent"
 *   operator-id="456"
 *   operator-name="Jane Smith"
 *   api-base="https://api.example.com"
 * />
 */
class MfeSummaryElement extends HTMLElement {
  private root: Root | null = null;
  private queryClient = createQueryClient();

  static get observedAttributes() {
    return ['member-id', 'persona', 'operator-id', 'operator-name', 'api-base'];
  }

  connectedCallback() {
    // Create shadow DOM for style isolation
    const shadow = this.attachShadow({ mode: 'open' });

    // Create container
    const container = document.createElement('div');
    shadow.appendChild(container);

    // Inject styles into shadow DOM
    const styleSheet = document.createElement('style');
    styleSheet.textContent = this.getStyles();
    shadow.appendChild(styleSheet);

    // Set API base if provided
    const apiBase = this.getAttribute('api-base');
    if (apiBase) {
      api.setBaseUrl(apiBase);
    }

    this.root = createRoot(container);
    this.render();
  }

  disconnectedCallback() {
    this.root?.unmount();
    this.queryClient.clear();
  }

  attributeChangedCallback() {
    // Re-render when attributes change
    this.render();
  }

  private render() {
    if (!this.root) return;

    const props = {
      memberId: this.getAttribute('member-id') || '',
      persona: (this.getAttribute('persona') || 'individual') as Persona,
      operatorId: this.getAttribute('operator-id') || undefined,
      operatorName: this.getAttribute('operator-name') || undefined,
      apiBase: this.getAttribute('api-base') || undefined,
    };

    this.root.render(
      <QueryClientProvider client={this.queryClient}>
        <SummaryApp {...props} />
      </QueryClientProvider>
    );
  }

  private getStyles(): string {
    // Inline critical styles for shadow DOM
    return `
      :host {
        display: block;
        font-family: system-ui, -apple-system, sans-serif;
      }
    `;
  }
}

// Register custom element
if (!customElements.get('mfe-summary')) {
  customElements.define('mfe-summary', MfeSummaryElement);
}

export { MfeSummaryElement };
