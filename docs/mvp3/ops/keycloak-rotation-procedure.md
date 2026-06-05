# Keycloak Client Secret Rotation Procedure

**Version:** 1.0
**Last Updated:** 2026-06-05
**Scope:** uip-api client secret rotation on staging/production

---

## Prerequisites

- Keycloak Admin Console access (`admin` user)
- Backend deployment access (`kubectl` or Docker Compose)
- Valid admin JWT token for testing
- Notify team before rotation (Slack: #uip-ops)

---

## Step-by-Step Procedure

### Step 1: Pre-Rotation Backup

```bash
# Export current Keycloak realm configuration
/opt/keycloak/bin/kc.sh export \
  --realm uip \
  --file /backups/keycloak/uip-realm-pre-rotation-$(date +%Y%m%d%H%M).json

# Verify backup exists
ls -la /backups/keycloak/uip-realm-pre-rotation-*.json
```

### Step 2: Record Current Secret

```bash
# Get current backend client secret (for rollback)
kubectl get secret uip-api-secrets \
  -o jsonpath='{.data.KEYCLOAK_CLIENT_SECRET}' | base64 -d
# Save this value for rollback
```

### Step 3: Generate New Secret in Keycloak

1. Open Keycloak Admin Console: `https://auth.uip.local/admin`
2. Select realm: **uip**
3. Navigate to: **Clients** → **uip-api**
4. Go to: **Credentials** tab
5. Click: **Regenerate Client Secret**
6. **Copy the new secret immediately** (it's shown only once)

### Step 4: Update Backend Configuration

```bash
# Option A: Kubernetes Secret
kubectl create secret generic uip-api-secrets \
  --from-literal=KEYCLOAK_CLIENT_SECRET=<new-secret> \
  --dry-run=client -o yaml | kubectl apply -f -

# Option B: Docker Compose env file
# Edit .env file:
# KEYCLOAK_CLIENT_SECRET=<new-secret>
```

### Step 5: Restart Backend Services

```bash
# Kubernetes
kubectl rollout restart deployment/uip-api
kubectl rollout status deployment/uip-api --timeout=120s

# Docker Compose
cd /opt/uip/infra
docker compose restart uip-api
docker compose logs -f uip-api --tail=50
```

### Step 6: Verify New Secret Works

```bash
# Test login with valid credentials
curl -s -X POST https://api.uip.local/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@hcm-uip.vn","password":"<test-password>"}' | jq .

# Expected: 200 + {"accessToken":"eyJ...", "refreshToken":"eyJ..."}

# If 401 → secret mismatch, proceed to Rollback
```

### Step 7: Verify Old Secret is Rejected

```bash
# Attempt login using old secret (should fail)
# This is verified by Keycloak rejecting the old credential
# No direct API test needed — Keycloak enforces this
```

---

## Rollback Procedure

If rotation fails (login returns 401 after update):

```bash
# Step 1: Restore previous secret in backend
kubectl create secret generic uip-api-secrets \
  --from-literal=KEYCLOAK_CLIENT_SECRET=<old-secret> \
  --dry-run=client -o yaml | kubectl apply -f -

# Step 2: Restart backend
kubectl rollout restart deployment/uip-api
kubectl rollout status deployment/uip-api

# Step 3: Verify login works with old secret
curl -s -X POST https://api.uip.local/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin@hcm-uip.vn","password":"<test-password>"}'
# Expected: 200

# Step 4: If old secret also fails, re-import realm backup
/opt/keycloak/bin/kc.sh import \
  --file /backups/keycloak/uip-realm-pre-rotation-<timestamp>.json

# Step 5: Restart Keycloak
kubectl rollout restart deployment/keycloak
```

---

## Rotation Schedule

| Environment | Frequency | Window |
|-------------|-----------|--------|
| Staging | Monthly | Any time with 1h notice |
| Production | Quarterly | Maintenance window (Sun 02:00-04:00 SGT) |
| Emergency | As needed | With PO approval |

---

*Document: Keycloak Rotation Procedure v1.0 | Created 2026-06-05*
