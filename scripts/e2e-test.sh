#!/bin/bash
# =============================================================================
# E2E Test Script - Validates both ALB authentication flows
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

BFF_URL="${BFF_URL:-http://localhost:8080}"
FRONTEND_URL="${FRONTEND_URL:-http://localhost:3000}"

echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}E2E Test Suite - Dual ALB Authentication${NC}"
echo -e "${YELLOW}========================================${NC}"
echo ""

# Track test results
PASSED=0
FAILED=0

# Helper function to run a test
run_test() {
    local name="$1"
    local expected_status="$2"
    local actual_status="$3"

    if [ "$expected_status" = "$actual_status" ]; then
        echo -e "${GREEN}✓ PASS${NC}: $name"
        ((PASSED++))
    else
        echo -e "${RED}✗ FAIL${NC}: $name (expected: $expected_status, got: $actual_status)"
        ((FAILED++))
    fi
}

# Wait for services to be ready
echo -e "${YELLOW}Waiting for services...${NC}"
sleep 5

# =============================================================================
# Test 1: BFF Health Check
# =============================================================================
echo ""
echo -e "${YELLOW}--- BFF Health Checks ---${NC}"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BFF_URL/actuator/health/liveness" 2>/dev/null || echo "000")
run_test "BFF liveness endpoint" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BFF_URL/actuator/health/readiness" 2>/dev/null || echo "000")
run_test "BFF readiness endpoint" "200" "$STATUS"

# =============================================================================
# Test 2: Frontend Health Check + MFE Bundles
# =============================================================================
echo ""
echo -e "${YELLOW}--- Frontend Health Checks ---${NC}"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL/health" 2>/dev/null || echo "000")
run_test "Frontend health endpoint" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL/" 2>/dev/null || echo "000")
run_test "Frontend index page" "200" "$STATUS"

# MFE bundles
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL/mfe/profile/bundle.js" 2>/dev/null || echo "000")
run_test "MFE Profile bundle.js" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$FRONTEND_URL/mfe/summary/bundle.js" 2>/dev/null || echo "000")
run_test "MFE Summary bundle.js" "200" "$STATUS"

# MFE CORS headers
CORS_HEADER=$(curl -s -I "$FRONTEND_URL/mfe/profile/bundle.js" 2>/dev/null | grep -i "access-control-allow-origin" | tr -d '\r' || echo "")
if [[ "$CORS_HEADER" == *"*"* ]]; then
    echo -e "${GREEN}✓ PASS${NC}: MFE CORS headers present"
    ((PASSED++))
else
    echo -e "${RED}✗ FAIL${NC}: MFE CORS headers missing"
    ((FAILED++))
fi

# =============================================================================
# Test 3: Session-Based Auth Flow (api-ALB simulation)
# =============================================================================
echo ""
echo -e "${YELLOW}--- Session-Based Auth Flow (api-ALB) ---${NC}"

# Without session cookie - should get 401 on protected endpoints
STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BFF_URL/api/user/profile" 2>/dev/null || echo "000")
run_test "Protected endpoint without auth returns 4xx" "401" "$STATUS"

# =============================================================================
# Test 4: mTLS Header-Based Auth Flow (mtls-ALB simulation)
# =============================================================================
echo ""
echo -e "${YELLOW}--- mTLS Header-Based Auth Flow (mtls-ALB) ---${NC}"

# Valid external integration request with all required headers
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Client-Id: partner-123" \
    -H "X-User-Id: user-456" \
    -H "X-Persona: agent" \
    -H "X-IDP-Type: partner-idp" \
    "$BFF_URL/api/member/123" 2>/dev/null || echo "000")
# Should pass auth filter, may get 404 or other response (not 401/403)
if [ "$STATUS" != "401" ] && [ "$STATUS" != "403" ]; then
    echo -e "${GREEN}✓ PASS${NC}: Valid external request passes auth (status: $STATUS)"
    ((PASSED++))
else
    echo -e "${RED}✗ FAIL${NC}: Valid external request blocked (status: $STATUS)"
    ((FAILED++))
fi

# Missing persona header - should get 401
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Client-Id: partner-123" \
    -H "X-User-Id: user-456" \
    -H "X-IDP-Type: partner-idp" \
    "$BFF_URL/api/member/123" 2>/dev/null || echo "000")
run_test "External request without persona returns 401" "401" "$STATUS"

# Missing user-id header - should get 401
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Client-Id: partner-123" \
    -H "X-Persona: agent" \
    -H "X-IDP-Type: partner-idp" \
    "$BFF_URL/api/member/123" 2>/dev/null || echo "000")
run_test "External request without user-id returns 401" "401" "$STATUS"

# Invalid persona - should get 403
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Client-Id: partner-123" \
    -H "X-User-Id: user-456" \
    -H "X-Persona: invalid_persona" \
    -H "X-IDP-Type: partner-idp" \
    "$BFF_URL/api/member/123" 2>/dev/null || echo "000")
run_test "External request with invalid persona returns 403" "403" "$STATUS"

# Untrusted IDP type - should get 403
STATUS=$(curl -s -o /dev/null -w "%{http_code}" \
    -H "X-Client-Id: partner-123" \
    -H "X-User-Id: user-456" \
    -H "X-Persona: agent" \
    -H "X-IDP-Type: untrusted-idp" \
    "$BFF_URL/api/member/123" 2>/dev/null || echo "000")
run_test "External request with untrusted IDP returns 403" "403" "$STATUS"

# =============================================================================
# Test 5: Public Endpoints (no auth required)
# =============================================================================
echo ""
echo -e "${YELLOW}--- Public Endpoints ---${NC}"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BFF_URL/actuator/health" 2>/dev/null || echo "000")
run_test "Health endpoint is public" "200" "$STATUS"

STATUS=$(curl -s -o /dev/null -w "%{http_code}" "$BFF_URL/actuator/info" 2>/dev/null || echo "000")
run_test "Info endpoint is public" "200" "$STATUS"

# =============================================================================
# Summary
# =============================================================================
echo ""
echo -e "${YELLOW}========================================${NC}"
echo -e "${YELLOW}Test Summary${NC}"
echo -e "${YELLOW}========================================${NC}"
echo -e "Passed: ${GREEN}$PASSED${NC}"
echo -e "Failed: ${RED}$FAILED${NC}"
echo ""

if [ $FAILED -eq 0 ]; then
    echo -e "${GREEN}All tests passed!${NC}"
    exit 0
else
    echo -e "${RED}Some tests failed.${NC}"
    exit 1
fi
