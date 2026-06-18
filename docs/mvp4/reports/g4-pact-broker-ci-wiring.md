# G4 — Pact Broker CI Wiring (to re-enable BackendProviderPactTest)

| Field | Value |
|---|---|
| **Status** | Guidance doc — `BackendProviderPactTest` is `@Disabled` until a Pact broker publishes `provider: backend` contracts |
| **Audience** | DevOps |
| **Blocks** | None — G4 currently passes on 42 REST Assured `@Tag("contract")` tests + consumer-side Pact tests. This is a CI hardening task, not a code defect. |

## Why the test is disabled

`backend/src/test/java/com/uip/backend/contract/BackendProviderPactTest.java` verifies backend **as a provider** — i.e. it consumes pact files where `provider: "backend"` and replays them against the backend API. Today:

- Backend's existing Pact tests (`AnalyticsServiceConsumerPactTest`, `NotificationServiceConsumerPactTest`) generate pacts where backend is the **consumer** (backend → analytics-service, backend → notification-service). These do not satisfy the provider test.
- No consumer (frontend web, mobile) publishes `provider: backend` contracts to a shared broker.
- Pact's `@TestTemplate` extension throws `NoPactsFoundException` at registration time (before any JUnit condition guard can run), so the test cannot be conditionally skipped — it must be `@Disabled` until pacts exist.

## Wiring steps (DevOps)

### 1. Stand up a Pact broker

Options (cheapest first):
- **PactFlow (managed, free tier)** — recommended for pilot. Sign up, create a broker, note the URL + read/write tokens.
- **Self-hosted** — `docker run -p 9292:9292 pactfoundation/pact-broker` (add to `infrastructure/docker-compose.*.yml`). Requires a Postgres for persistence.

### 2. Store broker credentials in CI secrets

In the CI provider (GitHub Actions, per `.github/workflows/`):
```
PACT_BROKER_URL        = https://<org>.pactflow.io
PACT_BROKER_TOKEN      = <read/write token>
```
These do not exist today (`grep PACT_BROKER .github/` returns empty).

### 3. Publish consumer pacts from CI

Add a step to the relevant build job (backend, frontend, mobile) after tests:
```bash
# Backend consumer pacts (backend → analytics/notification)
./gradlew pactPublish \
  -Ppactbroker.url=$PACT_BROKER_URL \
  -Ppactbroker.token=$PACT_BROKER_TOKEN
```
The `au.com.dius.pact.core` Gradle plugin supports `pactPublish` — add it to `backend/build.gradle` if not present.

Frontend/mobile consumers must likewise publish their `provider: backend` pacts. This is the gap — no frontend/mobile Pact tests exist today that target backend as provider.

### 4. Re-enable the provider test + verify in CI

Once pacts flow:
1. Remove `@Disabled` from `BackendProviderPactTest`.
2. Add a CI job that runs provider verification against a live backend (staging or a CI-only instance):
   ```bash
   ./gradlew test --tests "com.uip.backend.contract.BackendProviderPactTest" \
     -Ppactbroker.url=$PACT_BROKER_URL \
     -Ppactbroker.token=$PACT_BROKER_TOKEN \
     -Ppact.verifier.tags=prod
   ```
3. Wire `can-i-deploy` checks into `cd-staging.yml` / `cd-prod.yml` so a broken contract blocks deploy.

## Scope decision

This is **MVP5 work** unless a paying customer's contract requires provider verification sooner. For the pilot (5 buildings), the 42 REST Assured contract tests + consumer-side Pact tests give adequate coverage. Track in `mvp5-roadmap-draft.md` Theme A (K8s migration enables multi-service contracts).

## Reference

- Existing local flow (no broker): `scripts/pact-verify.sh` — copies backend-generated pacts into `analytics-service/src/test/resources/pacts/` and runs `AnalyticsServiceProviderPactTest`. This works for the one backend→analytics path; it does not scale to frontend/mobile consumers.
- `copyPactFiles` Gradle task (added 2026-06-15) mirrors `build/pacts/*.json` into the test classpath so the provider test can load them once CI flow is wired.
