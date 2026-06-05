/**
 * @uip/api-types
 * 
 * Shared TypeScript types generated from OpenAPI spec.
 * Used by frontend and operator-mobile apps.
 */

// Re-export specific types to avoid duplicate 'resolve' operation identifiers
// (backend has /alerts/{id}/resolve and /incidents/{id}/resolve with same operationId)
export type {
  paths,
  components,
  webhooks,
  $defs
} from './generated'
