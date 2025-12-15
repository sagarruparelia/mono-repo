/**
 * Vite configuration for building mfe-profile as a web component bundle
 *
 * This builds a standalone IIFE bundle that can be loaded via script tag:
 *   <script src="https://web-cl.abc.com/mfe/profile/bundle.js"></script>
 *   <mfe-profile member-id="123" service-base-url="https://web-cl.abc.com"></mfe-profile>
 */
import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react-swc';
import { nxViteTsPaths } from '@nx/vite/plugins/nx-tsconfig-paths.plugin';

export default defineConfig({
  root: import.meta.dirname,
  cacheDir: '../../node_modules/.vite/apps/mfe-profile-wc',
  plugins: [react(), nxViteTsPaths()],

  build: {
    outDir: '../../dist/mfe/profile',
    emptyOutDir: true,

    // Build as library (IIFE for script tag loading)
    lib: {
      entry: 'src/web-component.tsx',
      name: 'MfeProfile',
      fileName: () => 'bundle.js',
      formats: ['iife'],
    },

    rollupOptions: {
      // Don't externalize - bundle everything for standalone use
      external: [],
      output: {
        // Ensure styles are inlined
        inlineDynamicImports: true,
        // Global variable name for IIFE
        name: 'MfeProfile',
      },
    },

    // Inline CSS into JS bundle (critical for shadow DOM)
    cssCodeSplit: false,

    // Optimize for production
    minify: 'terser',
    terserOptions: {
      compress: {
        drop_console: true,
        drop_debugger: true,
      },
    },

    // Generate source maps for debugging
    sourcemap: true,

    // Report bundle size
    reportCompressedSize: true,
  },

  // CSS handling for shadow DOM injection
  css: {
    modules: {
      // Preserve class names for debugging
      generateScopedName: '[name]__[local]__[hash:base64:5]',
    },
  },

  define: {
    // Ensure React is in production mode
    'process.env.NODE_ENV': JSON.stringify('production'),
  },
});
