/**
 * Web Component wrapper for ProfileApp
 * Allows embedding in external sites via:
 *   <script src="https://web-cl.abc.com/mfe/profile/bundle.js"></script>
 *   <mfe-profile member-id="123" service-base-url="https://web-cl.abc.com"></mfe-profile>
 */
import { createRoot, Root } from 'react-dom/client';
import { QueryClientProvider } from '@tanstack/react-query';
import { createQueryClient, ApiClientProvider } from '@mono-repo/shared-state';
import { ProfileApp } from './app/ProfileApp';
import type { Persona } from '@mono-repo/shared-state';

// CSS will be injected into shadow DOM
import appStyles from './app/app.module.css?inline';

class MfeProfileElement extends HTMLElement {
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
    const memberId = this.getAttribute('member-id') || '';
    const persona = (this.getAttribute('persona') || 'individual') as Persona;
    const operatorId = this.getAttribute('operator-id') || undefined;
    const operatorName = this.getAttribute('operator-name') || undefined;
    const serviceBaseUrl = this.getAttribute('service-base-url') || '';

    // Validate required attributes
    if (!memberId) {
      console.error('mfe-profile: member-id attribute is required');
      return;
    }

    // Create container with styles
    if (!this.root) {
      // Inject styles into shadow DOM
      const styleSheet = document.createElement('style');
      styleSheet.textContent = appStyles;
      this.shadowRoot_.appendChild(styleSheet);

      // Create mount point
      const container = document.createElement('div');
      container.id = 'mfe-profile-root';
      this.shadowRoot_.appendChild(container);
      this.root = createRoot(container);
    }

    this.root.render(
      <QueryClientProvider client={this.queryClient}>
        <ApiClientProvider serviceBaseUrl={serviceBaseUrl}>
          <ProfileApp
            memberId={memberId}
            persona={persona}
            operatorId={operatorId}
            operatorName={operatorName}
          />
        </ApiClientProvider>
      </QueryClientProvider>
    );
  }
}

// Register custom element
if (!customElements.get('mfe-profile')) {
  customElements.define('mfe-profile', MfeProfileElement);
}

// Export for UMD/global access
export { MfeProfileElement };
