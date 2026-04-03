#!/bin/bash
# Tu dong them User Storage Provider (keycloak-provider) vao realm master neu chua co.
set +e

ADMIN_PASS="${KEYCLOAK_ADMIN_PASSWORD:-admin123}"
BASE="http://127.0.0.1:8080"

TOKEN_JSON=$(curl -fsS -X POST "$BASE/realms/master/protocol/openid-connect/token" \
  -H "Content-Type: application/x-www-form-urlencoded" \
  -d "client_id=admin-cli&grant_type=password&username=admin&password=${ADMIN_PASS}" 2>/dev/null)

TOKEN=$(echo "$TOKEN_JSON" | jq -r '.access_token // empty')
if [ -z "$TOKEN" ] || [ "$TOKEN" = "null" ]; then
  echo "WARN: Khong lay duoc admin token de cau hinh federation (Keycloak chua san sang hoac sai mat khau admin)." >&2
  exit 0
fi

COUNT=$(curl -fsS -H "Authorization: Bearer $TOKEN" \
  "$BASE/admin/realms/master/components?type=org.keycloak.storage.UserStorageProvider" 2>/dev/null \
  | jq '[.[] | select(.providerId=="keycloak-provider")] | length')

if [ "$COUNT" != "0" ]; then
  echo "INFO: User federation keycloak-provider da ton tai."
  exit 0
fi

REALM_ID=$(curl -fsS -H "Authorization: Bearer $TOKEN" "$BASE/admin/realms/master" | jq -r '.id // empty')
if [ -z "$REALM_ID" ] || [ "$REALM_ID" = "null" ]; then
  echo "WARN: Khong doc duoc realm id cua master." >&2
  exit 0
fi

BODY=$(jq -n \
  --arg pid "$REALM_ID" \
  '{name:"postgres-users", providerId:"keycloak-provider", providerType:"org.keycloak.storage.UserStorageProvider", parentId:$pid, config:{}}')

HTTP_CODE=$(curl -sS -o /tmp/kc_federation_resp.txt -w "%{http_code}" -X POST "$BASE/admin/realms/master/components" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$BODY")

if [ "$HTTP_CODE" = "201" ] || [ "$HTTP_CODE" = "204" ]; then
  echo "INFO: Da them User Federation keycloak-provider vao realm master."
  exit 0
fi

echo "WARN: Them federation that bai HTTP $HTTP_CODE — co the them thu cong trong Admin Console." >&2
cat /tmp/kc_federation_resp.txt >&2 || true
exit 0
