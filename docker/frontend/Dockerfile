# =============================================================================
# Frontend Dockerfile - React/Vite + Nginx
# Multi-stage build with parameterized app name
# =============================================================================

# -----------------------------------------------------------------------------
# Stage 1: Build
# -----------------------------------------------------------------------------
FROM node:22-alpine AS builder

# Build argument for app name (web-cl, web-hs, mfe-profile, mfe-summary)
ARG APP_NAME=web-cl

WORKDIR /app

# Copy package files for dependency caching
COPY package*.json ./
COPY nx.json tsconfig.base.json ./

# Install dependencies
RUN npm ci --legacy-peer-deps

# Copy source files
COPY apps/ apps/
COPY libs/ libs/

# Build the specific app
RUN npx nx build ${APP_NAME} --configuration=production

# -----------------------------------------------------------------------------
# Stage 2: Runtime - Nginx
# -----------------------------------------------------------------------------
FROM nginx:1.27-alpine AS runtime

# Build argument for app name
ARG APP_NAME=web-cl

# Labels for container metadata
LABEL org.opencontainers.image.title="Frontend - ${APP_NAME}" \
      org.opencontainers.image.description="React/Vite frontend served by Nginx" \
      org.opencontainers.image.vendor="mono-repo" \
      org.opencontainers.image.source="https://github.com/org/mono-repo"

# Install curl for health checks
RUN apk add --no-cache curl && \
    rm -rf /var/cache/apk/*

# Remove default nginx configuration
RUN rm -rf /etc/nginx/conf.d/default.conf /usr/share/nginx/html/*

# Create non-root user for nginx
RUN addgroup -g 1000 -S nginx-app && \
    adduser -u 1000 -S -G nginx-app nginx-app && \
    # Create required directories with proper permissions
    mkdir -p /var/cache/nginx/client_temp \
             /var/cache/nginx/proxy_temp \
             /var/cache/nginx/fastcgi_temp \
             /var/cache/nginx/uwsgi_temp \
             /var/cache/nginx/scgi_temp \
             /var/run && \
    chown -R nginx-app:nginx-app /var/cache/nginx /var/run /var/log/nginx

# Copy nginx configuration
COPY docker/nginx/nginx.conf /etc/nginx/nginx.conf

# Copy built assets from builder
COPY --from=builder /app/dist/apps/${APP_NAME} /usr/share/nginx/html

# Set ownership of static files
RUN chown -R nginx-app:nginx-app /usr/share/nginx/html

# Switch to non-root user
USER nginx-app

# Expose port
EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD curl -sf http://localhost:8080/health || exit 1

# Start nginx in foreground
CMD ["nginx", "-g", "daemon off;"]
