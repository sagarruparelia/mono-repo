import 'react';

declare module 'react' {
  namespace JSX {
    interface IntrinsicElements {
      'mfe-summary': React.DetailedHTMLProps<
        React.HTMLAttributes<HTMLElement> & {
          'member-id'?: string;
          persona?: string;
          'operator-id'?: string;
          'operator-name'?: string;
          'api-base'?: string;
        },
        HTMLElement
      >;
      'mfe-profile': React.DetailedHTMLProps<
        React.HTMLAttributes<HTMLElement> & {
          'member-id'?: string;
          persona?: string;
          'operator-id'?: string;
          'operator-name'?: string;
          'api-base'?: string;
        },
        HTMLElement
      >;
    }
  }
}
