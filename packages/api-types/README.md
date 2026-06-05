# @uip/api-types

Shared TypeScript types for UIP Smart City API, generated from OpenAPI spec.

## Purpose

This package contains TypeScript interfaces and types generated from `docs/api/openapi.json`.
It is shared between:
- `frontend/` (React web app)
- `applications/operator-mobile/` (React Native / Expo app)

## Usage

```typescript
import type { paths, components, operations } from '@uip/api-types'

// Example: Type for sensor reading endpoint response
type SensorReading = components['schemas']['SensorReadingDTO']

// Example: Type for GET /api/v1/sensors/{id} operation
type GetSensorOp = operations['getSensorById']
```

## Regenerate Types

From workspace root:

```bash
npm run gen-api-types
```

Or from this package:

```bash
cd packages/api-types
npm run gen-api-types
```

## Files

- `src/generated.ts` — Auto-generated from OpenAPI spec (DO NOT EDIT)
- `src/index.ts` — Re-exports + convenience helpers

## Version

Current: 0.1.0 (Sprint 9)
