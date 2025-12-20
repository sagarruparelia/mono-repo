/**
 * Web Component wrapper for HealthSummaryApp
 * Allows embedding in external sites via:
 *   <script src="https://web-cl.abc.com/mfe/summary/bundle.js"></script>
 *   <mfe-health-summary member-id="123" service-base-url="https://web-cl.abc.com"></mfe-health-summary>
 *
 * Legacy element name (mfe-summary) is still supported for backward compatibility
 */
import { createRoot, Root } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient, ApiClientProvider } from '@mono-repo/shared-state';
import { HealthSummaryApp } from './app/SummaryApp';
import type { Persona } from '@mono-repo/shared-state';

// CSS will be injected into shadow DOM
import appStyles from './app/app.module.css?inline';
import sectionStyles from './app/components/sections.module.css?inline';

class MfeHealthSummaryElement extends HTMLElement {
  private root: Root | null = null;
  private shadowRoot_: ShadowRoot;
  private queryClient = createQueryClient();

  static get observedAttributes() {
    return ['member-id', 'persona', 'operator-id', 'operator-name', 'service-base-url'];
  }

  constructor() {
    super();
    // Use shadow DOM for style isolation
    this.shadowRoot_ = this.attachShadow({ mode: 'open' });
  }

  connectedCallback() {
    this.render();
  }

  disconnectedCallback() {
    if (this.root) {
      this.root.unmount();
      this.root = null;
    }
  }

  attributeChangedCallback() {
    this.render();
  }

  private render() {
    // HTML attribute remains 'member-id' for backward compatibility, but maps to memberEid internally
    const memberEid = this.getAttribute('member-id') || '';
    const persona = (this.getAttribute('persona') || 'individual') as Persona;
    const operatorId = this.getAttribute('operator-id') || undefined;
    const operatorName = this.getAttribute('operator-name') || undefined;
    const serviceBaseUrl = this.getAttribute('service-base-url') || '';

    // Validate required attributes
    if (!memberEid) {
      console.error('mfe-health-summary: member-id attribute is required');
      return;
    }

    // Create container with styles
    if (!this.root) {
      // Inject styles into shadow DOM
      const styleSheet = document.createElement('style');
      styleSheet.textContent = appStyles + '\n' + sectionStyles;
      this.shadowRoot_.appendChild(styleSheet);

      // Create mount point
      const container = document.createElement('div');
      container.id = 'mfe-health-summary-root';
      this.shadowRoot_.appendChild(container);
      this.root = createRoot(container);
    }

    this.root.render(
      <QueryClientProvider client={this.queryClient}>
        <ApiClientProvider serviceBaseUrl={serviceBaseUrl}>
          <HealthSummaryApp
            memberEid={memberEid}
            persona={persona}
            operatorId={operatorId}
            operatorName={operatorName}
          />
        </ApiClientProvider>
      </QueryClientProvider>
    );
  }
}

// Alias class for legacy element name support (custom elements require unique classes)
class MfeSummaryElement extends MfeHealthSummaryElement {}

// Register custom elements
if (!customElements.get('mfe-health-summary')) {
  customElements.define('mfe-health-summary', MfeHealthSummaryElement);
}

// Register legacy element name for backward compatibility
if (!customElements.get('mfe-summary')) {
  customElements.define('mfe-summary', MfeSummaryElement);
}

// Export for UMD/global access
export { MfeHealthSummaryElement };
