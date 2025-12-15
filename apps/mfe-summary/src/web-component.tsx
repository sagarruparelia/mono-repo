import { createRoot, Root } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient, ApiClientProvider } from '@mono-repo/shared-state';
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
 *   service-base-url="/api/proxy"
 * />
 *
 * Note: API calls default to same-origin. Use service-base-url when the
 * consumer app needs to route MFE traffic through a specific proxy endpoint.
 */
class MfeSummaryElement extends HTMLElement {
  private root: Root | null = null;
  private queryClient = createQueryClient();

  static get observedAttributes() {
    return ['member-id', 'persona', 'operator-id', 'operator-name', 'service-base-url'];
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
    };

    // service-base-url provides isolated API routing per web component instance
    const serviceBaseUrl = this.getAttribute('service-base-url') || undefined;

    this.root.render(
      <QueryClientProvider client={this.queryClient}>
        <ApiClientProvider serviceBaseUrl={serviceBaseUrl}>
          <SummaryApp {...props} />
        </ApiClientProvider>
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
