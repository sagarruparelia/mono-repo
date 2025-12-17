# =============================================================================
# Frontend Dockerfile - React/Vite + Nginx with Brotli
# Multi-stage build with shell app + embeddable MFE bundles
# Enterprise production-ready, runs as non-root
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build Shell App + MFE Web Components
# -----------------------------------------------------------------------------
FROM node:24-alpine AS builder

# Build argument for shell app name (web-cl or web-hs)
ARG APP_NAME=web-cl

# Whether to include embeddable MFE bundles (for external embedding)
ARG INCLUDE_MFE_BUNDLES=true

WORKDIR /app

# Copy package files for dependency caching
COPY package*.json ./
COPY nx.json tsconfig.base.json ./

# Install dependencies
RUN npm ci --legacy-peer-deps

# Copy source files
COPY apps/ apps/
COPY libs/ libs/

# Build the shell app
RUN npx nx build ${APP_NAME} --configuration=production

# Create MFE output directory (ensures COPY works even if bundles not built)
RUN mkdir -p dist/mfe

# Build MFE web components for external embedding (if enabled)
RUN if [ "$INCLUDE_MFE_BUNDLES" = "true" ]; then \
      npx nx run mfe-profile:build-wc && \
      npx nx run mfe-summary:build-wc; \
    fi

# -----------------------------------------------------------------------------
# Stage 2: Runtime - Nginx with Brotli (unprivileged)
# Using fholzer/nginx-brotli for brotli compression support
# -----------------------------------------------------------------------------
# Using v1.26.2 for multi-arch support (amd64, arm64)
# Check https://hub.docker.com/r/fholzer/nginx-brotli/tags for latest versions
FROM fholzer/nginx-brotli:v1.26.2 AS runtime

# Build arguments
ARG APP_NAME=web-cl

# Labels for container metadata
LABEL org.opencontainers.image.title="Frontend - ${APP_NAME}" \
      org.opencontainers.image.description="React/Vite frontend with Brotli compression and MFE bundles" \
      org.opencontainers.image.vendor="mono-repo" \
      org.opencontainers.image.source="https://github.com/org/mono-repo"

# Install curl for health checks and ca-certificates for HTTPS
RUN apk add --no-cache curl ca-certificates && \
    rm -rf /var/cache/apk/*

# Remove default nginx configuration
RUN rm -rf /etc/nginx/conf.d/default.conf /usr/share/nginx/html/*

# Configure nginx to run as non-root (unprivileged)
# Using nginx user (uid 101) that comes with the base image
RUN mkdir -p /var/cache/nginx/client_temp \
             /var/cache/nginx/proxy_temp \
             /var/cache/nginx/fastcgi_temp \
             /var/cache/nginx/uwsgi_temp \
             /var/cache/nginx/scgi_temp \
             /usr/share/nginx/html/mfe && \
    chown -R nginx:nginx /var/cache/nginx /var/log/nginx /usr/share/nginx/html && \
    # Create and set permissions for pid file location
    touch /tmp/nginx.pid && \
    chown nginx:nginx /tmp/nginx.pid

# Copy nginx configuration
COPY --chown=nginx:nginx docker/nginx/nginx.conf /etc/nginx/nginx.conf

# Copy entrypoint script
COPY docker/nginx/docker-entrypoint.sh /docker-entrypoint.sh
RUN chmod +x /docker-entrypoint.sh

# Copy built shell app assets from builder
COPY --from=builder --chown=nginx:nginx /app/dist/apps/${APP_NAME} /usr/share/nginx/html

# Copy MFE bundles for external embedding
# These are served at /mfe/profile/bundle.js and /mfe/summary/bundle.js
COPY --from=builder --chown=nginx:nginx /app/dist/mfe/ /usr/share/nginx/html/mfe/

# Environment variable for CloudFront origin verification
# Set this in K8s deployment when CloudFront is configured
ENV CLOUDFRONT_ORIGIN_SECRET=""

# Switch to non-root user
USER nginx

# Expose port (unprivileged port > 1024)
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -sf http://localhost:8080/health || exit 1

# Use entrypoint for environment variable substitution
ENTRYPOINT ["/docker-entrypoint.sh"]

# Start nginx in foreground
CMD ["nginx", "-g", "daemon off;"]
