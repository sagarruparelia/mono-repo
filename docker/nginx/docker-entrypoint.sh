#!/bin/sh
set -e

# =============================================================================
# Docker Entrypoint for Nginx Frontend
# Handles environment variable substitution in nginx.conf
#
# This script is used for local Docker runs. In Kubernetes with CloudFront
# enabled, an init container performs the substitution instead.
# =============================================================================

NGINX_CONF="/etc/nginx/nginx.conf"

# If CloudFront origin secret is set, substitute it in the config
if [ -n "$CLOUDFRONT_ORIGIN_SECRET" ]; then
    echo "Configuring CloudFront origin verification..."
    sed -i "s/CLOUDFRONT_SECRET_PLACEHOLDER/$CLOUDFRONT_ORIGIN_SECRET/g" "$NGINX_CONF"
    echo "CloudFront origin verification configured."
fi

# Validate nginx configuration
echo "Validating nginx configuration..."
if ! nginx -t; then
    echo "ERROR: nginx configuration validation failed!"
    exit 1
fi

# Execute the main command (nginx)
echo "Starting nginx..."
exec "$@"
