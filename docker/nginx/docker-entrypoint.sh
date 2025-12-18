#!/bin/sh
set -e

# =============================================================================
# Docker Entrypoint for Nginx Frontend
# Handles environment variable substitution in nginx config files
#
# Environment Variables:
#   CLOUDFRONT_ORIGIN_SECRET - Secret for CloudFront origin verification
#   MFE_ALLOWED_ORIGINS      - Space-separated list of allowed MFE origins
#                              Example: "*.abc.com *.partner1.com localhost"
# =============================================================================

NGINX_CONF="/etc/nginx/nginx.conf"
MFE_ORIGINS_CONF="/etc/nginx/conf.d/mfe-allowed-origins.conf"

# CloudFront origin secret substitution
if [ -n "$CLOUDFRONT_ORIGIN_SECRET" ]; then
    echo "Configuring CloudFront origin verification..."
    sed -i "s/CLOUDFRONT_SECRET_PLACEHOLDER/$CLOUDFRONT_ORIGIN_SECRET/g" "$NGINX_CONF"
fi

# MFE allowed origins substitution (default to localhost if not set)
MFE_ALLOWED_ORIGINS="${MFE_ALLOWED_ORIGINS:-localhost 127.0.0.1}"
echo "Configuring MFE allowed origins: $MFE_ALLOWED_ORIGINS"
sed -i "s/\${MFE_ALLOWED_ORIGINS}/$MFE_ALLOWED_ORIGINS/g" "$MFE_ORIGINS_CONF"

# Validate nginx configuration
echo "Validating nginx configuration..."
if ! nginx -t; then
    echo "ERROR: nginx configuration validation failed!"
    exit 1
fi

# Execute the main command (nginx)
echo "Starting nginx..."
exec "$@"
