# Sprint 10 — BPMN Workflow Designer UX Polish

**Task:** S10-TD-02  
**Date:** 2026-06-05  
**Agent:** UIP-frontend-engineer  

## Summary

Enhanced WorkflowModeler component with built-in toolbar, properties panel, custom node styles, and flexible height support.

## Changes Implemented

### 1. Built-in Toolbar
Added MUI toolbar above BPMN canvas with buttons:
- **Save** (💾) — triggers onSave callback, shows success snackbar
- **Fit View** (🔍) — zooms to fit viewport
- **Undo / Redo** (↶ ↷) — with keyboard shortcuts support
- **Export XML** (⬇) — downloads BPMN XML as `.bpmn` file

### 2. Properties Panel (Right Side)
- Width: 240px, collapsible
- Shows when BPMN element is selected
- Displays: Element ID (read-only), Name (editable), Type (read-only)
- Updates element via `modeler.get('modeling').updateProperties()`
- Listens to `selection.changed` events from bpmn-js eventBus

### 3. Custom Node Styles
Injected CSS overrides for smart city aesthetic:
- **Start events**: Green fill (#E8F5E9), border (#388E3C)
- **End events**: Red fill (#FFEBEE), border (#C62828)
- **Service tasks**: Blue fill (#E3F2FD), border (#1565C0)
- **User tasks**: Orange fill (#FFF3E0), border (#E65100)
- **Gateways**: Yellow fill (#FFFDE7), amber border (#F9A825)

### 4. Height Auto-fill Support
- Prop `height` now accepts `number | string`
- Default changed from `500` to `calc(100vh - 200px)` for better page fill
- Backward compatible — existing numeric values still work

## API Changes

### Updated Props Interface
```typescript
interface Props {
  initialXml?: string | null;
  onSave?: (xml: string) => void;
  height?: number | string;       // was: number
  showToolbar?: boolean;           // NEW — default true
  showPropertiesPanel?: boolean;   // NEW — default true
}
```

## Files Modified
- `/frontend/src/components/workflow/WorkflowModeler.tsx` — 141 lines added

## Quality Gates ✅

- [x] `npx tsc --noEmit` — **PASS** (0 errors)
- [x] `npm test -- src/test/workflow/BpmnViewer.test.tsx` — **PASS** (7/7 tests)

## Usage Example

```tsx
<WorkflowModeler
  initialXml={xml}
  onSave={handleSave}
  height="calc(100vh - 200px)"  // or 520 for fixed
  showToolbar={true}
  showPropertiesPanel={true}
/>
```

## Notes

- Toolbar integrated into component (no more DOM event `bpmn-save` needed, though still supported for backward compatibility)
- Properties panel auto-opens when element is selected
- Custom styles use `!important` to override bpmn-js defaults
- No changes needed to AiWorkflowPage.tsx — existing usage compatible
