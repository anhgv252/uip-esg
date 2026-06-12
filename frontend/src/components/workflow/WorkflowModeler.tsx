import { useEffect, useRef, useState, useCallback } from 'react';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import {
  Box,
  CircularProgress,
  Alert,
  Snackbar,
  IconButton,
  Toolbar,
  Tooltip,
  Paper,
  Typography,
  TextField,
  Divider,
  Stack,
  Menu,
  MenuItem,
  ListItemText,
  Chip,
  Button,
  Collapse,
  useTheme,
  useMediaQuery,
} from '@mui/material';
import SaveIcon from '@mui/icons-material/Save';
import ZoomInMapIcon from '@mui/icons-material/ZoomInMap';
import ZoomInIcon from '@mui/icons-material/ZoomIn';
import ZoomOutIcon from '@mui/icons-material/ZoomOut';
import UndoIcon from '@mui/icons-material/Undo';
import RedoIcon from '@mui/icons-material/Redo';
import DownloadIcon from '@mui/icons-material/Download';
import CloseIcon from '@mui/icons-material/Close';
import TemplateIcon from '@mui/icons-material/Wallpaper';
import DeleteIcon from '@mui/icons-material/Delete';
import ChevronRightIcon from '@mui/icons-material/ChevronRight';
import { WORKFLOW_TEMPLATES, TEMPLATE_CATEGORY_LABELS, type WorkflowTemplate } from './templates';

/** Snap-to-grid size in pixels */
const GRID_SNAP = 20;

interface Props {
  initialXml?: string | null;
  onSave?: (xml: string) => void;
  onLoadTemplate?: (xml: string) => void;
  height?: number | string;
  showToolbar?: boolean;
  showPropertiesPanel?: boolean;
}

const EMPTY_BPMN = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  id="Definitions_1" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="Process_1" isExecutable="true">
    <bpmn:startEvent id="StartEvent_1" name="Start" />
  </bpmn:process>
  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="Process_1">
      <bpmndi:BPMNShape id="_BPMNShape_StartEvent_1" bpmnElement="StartEvent_1">
        <dc:Bounds x="173" y="102" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`;

/**
 * BPMN Modeler component using bpmn-js.
 * Supports drag & drop, editing, save/export, collapsible properties panel,
 * node hover effects, and responsive layout (v3.1-04 UX polish).
 *
 * All toolbar buttons have aria-label for WCAG 2.1 AA (v3.1-17).
 */
export default function WorkflowModeler({
  initialXml,
  onSave,
  onLoadTemplate,
  height = 'calc(100vh - 200px)',
  showToolbar = true,
  showPropertiesPanel = true,
}: Props) {
  const theme = useTheme();
  const isMobile = useMediaQuery(theme.breakpoints.down('md'));
  const containerRef = useRef<HTMLDivElement>(null);
  const modelerRef = useRef<InstanceType<typeof BpmnModeler> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [snackbar, setSnackbar] = useState<string | null>(null);
  const [selectedElement, setSelectedElement] = useState<{ id: string; name: string; type: string } | null>(null);
  const [propertiesPanelOpen, setPropertiesPanelOpen] = useState(false);
  const [templateAnchorEl, setTemplateAnchorEl] = useState<HTMLElement | null>(null);
  const [zoomLevel, setZoomLevel] = useState(1);

  // Initialize modeler instance only once on mount
  useEffect(() => {
    if (!containerRef.current) return;
    const modeler = new BpmnModeler({
      container: containerRef.current,
      keyboard: { bindTo: document },
    });
    modelerRef.current = modeler;
    return () => {
      modeler.destroy();
      modelerRef.current = null;
    };
  }, []);

  // ── DnD drop handler: accept dragged palette nodes and create BPMN elements (M4-SS-03) ──
  useEffect(() => {
    const container = containerRef.current;
    if (!container) return;

    const handleDragOver = (e: DragEvent) => {
      if (e.dataTransfer?.types.includes('application/bpmn-node') || e.dataTransfer?.types.includes('text/plain')) {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'copy';
      }
    };

    const handleDrop = (e: DragEvent) => {
      e.preventDefault();
      if (!modelerRef.current) return;

      const nodeType = e.dataTransfer?.getData('application/bpmn-node') || e.dataTransfer?.getData('text/plain');
      if (!nodeType) return;

      // Convert page coordinates to BPMN canvas coordinates
      const canvas = modelerRef.current.get<{ getViewbox: () => { x: number; y: number; width: number; height: number }; zoom: () => number }>('canvas');
      const elementFactory = modelerRef.current.get<{ create: (type: string, options?: Record<string, unknown>) => unknown }>('elementFactory');
      const modeling = modelerRef.current.get<{ createShape: (args: Record<string, unknown>) => unknown; connect: (source: unknown, target: unknown) => void }>('modeling');
      const elementRegistry = modelerRef.current.get<{ getAll: () => Array<{ id: string; businessObject: { $type: string }; x: number; y: number; width: number; height: number }> }>('elementRegistry');

      const viewbox = canvas.getViewbox();
      const zoom = canvas.zoom();
      const clientRect = container.getBoundingClientRect();

      // Compute drop position in BPMN coordinates
      const rawX = (e.clientX - clientRect.left) / zoom + viewbox.x;
      const rawY = (e.clientY - clientRect.top) / zoom + viewbox.y;

      // Snap to grid
      const snappedX = Math.round(rawX / GRID_SNAP) * GRID_SNAP;
      const snappedY = Math.round(rawY / GRID_SNAP) * GRID_SNAP;

      // Determine BPMN element type and create it
      let bpmnType: string;
      let shapeOptions: Record<string, unknown> = {};

      if (nodeType === 'bpmn:ServiceTask:notification') {
        bpmnType = 'bpmn:ServiceTask';
        shapeOptions = { businessObject: { name: 'Notification' } };
      } else if (nodeType === 'ai:AiDecisionTask') {
        bpmnType = 'bpmn:ServiceTask';
        shapeOptions = { businessObject: { name: 'AI Decision' } };
      } else {
        bpmnType = nodeType;
      }

      try {
        const shape = elementFactory.create(bpmnType, shapeOptions);
        modeling.createShape({
          shape,
          position: { x: snappedX, y: snappedY },
          parent: undefined, // defaults to root process
        });

        // Auto-connect: find the closest preceding element and connect if possible
        const allElements = elementRegistry.getAll();
        const sourceCandidates = allElements.filter(
          (el) =>
            el.id !== (shape as { id: string }).id &&
            el.businessObject.$type !== 'bpmn:EndEvent' &&
            el.businessObject.$type !== 'bpmn:SequenceFlow',
        );

        if (sourceCandidates.length > 0) {
          // Find closest element by distance to the drop point
          const closest = sourceCandidates.reduce((prev, curr) => {
            const prevDist = Math.hypot(prev.x + (prev.width / 2) - snappedX, prev.y + (prev.height / 2) - snappedY);
            const currDist = Math.hypot(curr.x + (curr.width / 2) - snappedX, curr.y + (curr.height / 2) - snappedY);
            return currDist < prevDist ? curr : prev;
          });

          const dist = Math.hypot(closest.x + (closest.width / 2) - snappedX, closest.y + (closest.height / 2) - snappedY);
          // Only auto-connect if within reasonable distance (250px in BPMN coords)
          if (dist < 250 && bpmnType !== 'bpmn:StartEvent') {
            try {
              modeling.connect(closest, shape);
            } catch {
              // Connection not possible between these types — skip silently
            }
          }
        }

        setSnackbar(`Added ${nodeType.replace('bpmn:', '').replace('ai:', '')}`);
      } catch (err) {
        console.error('Failed to create BPMN element:', err);
        setSnackbar('Failed to add node');
      }
    };

    container.addEventListener('dragover', handleDragOver);
    container.addEventListener('drop', handleDrop);
    return () => {
      container.removeEventListener('dragover', handleDragOver);
      container.removeEventListener('drop', handleDrop);
    };
  }, []);

  // Load / reload XML whenever initialXml changes (or on first render)
  useEffect(() => {
    if (!modelerRef.current) return;
    const xml = initialXml || EMPTY_BPMN;
    modelerRef.current
      .importXML(xml)
      .then(() => {
        const canvas = modelerRef.current?.get<{ zoom: (level: string) => void }>('canvas');
        canvas?.zoom('fit-viewport');
      })
      .catch((err: Error) => {
        setError(err.message ?? 'Failed to load BPMN diagram');
      })
      .finally(() => setLoading(false));
  }, [initialXml]);

  // Listen for selection changes to populate properties panel
  useEffect(() => {
    if (!modelerRef.current) return;
    const eventBus = modelerRef.current.get<{ on: (event: string, callback: (e: { newSelection: unknown[] }) => void) => void }>('eventBus');
    const elementRegistry = modelerRef.current.get<{ get: (id: string) => { id: string; businessObject: { name?: string; $type: string } } }>('elementRegistry');

    const handler = (e: { newSelection: unknown[] }) => {
      if (e.newSelection.length === 1) {
        const element = e.newSelection[0] as { id: string };
        const elementData = elementRegistry.get(element.id);
        if (elementData) {
          setSelectedElement({
            id: elementData.id,
            name: elementData.businessObject.name ?? '',
            type: elementData.businessObject.$type.replace('bpmn:', ''),
          });
          setPropertiesPanelOpen(true);
        }
      } else {
        setSelectedElement(null);
      }
    };

    eventBus.on('selection.changed', handler);
    return () => {
      // bpmn-js doesn't provide off, so we just rely on cleanup
    };
  }, []);

  /** Save current BPMN XML and call onSave callback */
  const handleSave = useCallback(async () => {
    if (!modelerRef.current) return;
    try {
      const { xml } = await modelerRef.current.saveXML({ format: true });
      if (xml && onSave) {
        onSave(xml);
        setSnackbar('Workflow saved');
      }
    } catch (err) {
      setSnackbar('Failed to save workflow');
      console.error('Save XML error:', err);
    }
  }, [onSave]);

  /** Fit viewport */
  const handleFitView = useCallback(() => {
    if (!modelerRef.current) return;
    const canvas = modelerRef.current.get<{ zoom: (level: string) => void }>('canvas');
    canvas?.zoom('fit-viewport');
  }, []);

  /** Undo last action */
  const handleUndo = useCallback(() => {
    if (!modelerRef.current) return;
    const commandStack = modelerRef.current.get<{ canUndo: () => boolean; undo: () => void }>('commandStack');
    if (commandStack.canUndo()) commandStack.undo();
  }, []);

  /** Redo last undone action */
  const handleRedo = useCallback(() => {
    if (!modelerRef.current) return;
    const commandStack = modelerRef.current.get<{ canRedo: () => boolean; redo: () => void }>('commandStack');
    if (commandStack.canRedo()) commandStack.redo();
  }, []);

  /** Export BPMN XML as .bpmn file */
  const handleExportXml = useCallback(async () => {
    if (!modelerRef.current) return;
    try {
      const { xml } = await modelerRef.current.saveXML({ format: true });
      if (xml) {
        const blob = new Blob([xml], { type: 'application/xml' });
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = `workflow-${Date.now()}.bpmn`;
        a.click();
        URL.revokeObjectURL(url);
        setSnackbar('Workflow exported');
      }
    } catch (err) {
      setSnackbar('Failed to export workflow');
      console.error('Export XML error:', err);
    }
  }, []);

  /** Zoom in */
  const handleZoomIn = useCallback(() => {
    if (!modelerRef.current) return;
    const canvas = modelerRef.current.get<{ zoom: (level: number | string) => number }>('canvas');
    const newZoom = canvas.zoom(zoomLevel + 0.15);
    setZoomLevel(newZoom);
  }, [zoomLevel]);

  /** Zoom out */
  const handleZoomOut = useCallback(() => {
    if (!modelerRef.current) return;
    const canvas = modelerRef.current.get<{ zoom: (level: number | string) => number }>('canvas');
    const newZoom = canvas.zoom(Math.max(0.2, zoomLevel - 0.15));
    setZoomLevel(newZoom);
  }, [zoomLevel]);

  /** Load a workflow template */
  const handleLoadTemplate = useCallback((template: WorkflowTemplate) => {
    if (!modelerRef.current) return;
    modelerRef.current
      .importXML(template.xml)
      .then(() => {
        const canvas = modelerRef.current?.get<{ zoom: (level: string) => void }>('canvas');
        canvas?.zoom('fit-viewport');
        setSnackbar(`Template "${template.name}" loaded`);
        onLoadTemplate?.(template.xml);
      })
      .catch((err: Error) => setError(err.message ?? 'Failed to load template'))
      .finally(() => setTemplateAnchorEl(null));
  }, [onLoadTemplate]);

  /** Delete selected element */
  const handleDeleteSelected = useCallback(() => {
    if (!modelerRef.current || !selectedElement) return;
    const modeling = modelerRef.current.get<{ removeElements: (elements: unknown[]) => void }>('modeling');
    const elementRegistry = modelerRef.current.get<{ get: (id: string) => unknown }>('elementRegistry');
    const element = elementRegistry.get(selectedElement.id);
    if (element) {
      modeling.removeElements([element]);
      setSelectedElement(null);
      setPropertiesPanelOpen(false);
    }
  }, [selectedElement]);

  /** Update element name in properties panel */
  const handleUpdateElementName = useCallback((newName: string) => {
    if (!modelerRef.current || !selectedElement) return;
    const modeling = modelerRef.current.get<{ updateProperties: (element: unknown, props: { name: string }) => void }>('modeling');
    const elementRegistry = modelerRef.current.get<{ get: (id: string) => unknown }>('elementRegistry');
    const element = elementRegistry.get(selectedElement.id);
    if (element) {
      modeling.updateProperties(element, { name: newName });
      setSelectedElement({ ...selectedElement, name: newName });
    }
  }, [selectedElement]);

  /** Expose handleSave via DOM event for parent toolbar */
  useEffect(() => {
    const handler = () => handleSave();
    window.addEventListener('bpmn-save', handler);
    return () => window.removeEventListener('bpmn-save', handler);
  }, [handleSave]);

  if (error) {
    return (
      <Box display="flex" alignItems="center" justifyContent="center" style={{ height }} p={2}>
        <Alert severity="error">{error}</Alert>
      </Box>
    );
  }

  return (
    <>
      {/* Custom CSS for BPMN node styles with hover effects (v3.1-04) */}
      <style>
        {`
          /* Start Event - Green */
          .djs-element .djs-visual > circle[data-element-id*="StartEvent"],
          .djs-shape[data-element-id*="StartEvent"] .djs-visual > circle {
            fill: ${theme.palette.success.main}22 !important;
            stroke: ${theme.palette.success.main} !important;
            stroke-width: 2px !important;
            transition: fill 0.2s ease, stroke-width 0.2s ease;
          }

          /* End Event - Red */
          .djs-element .djs-visual > circle[data-element-id*="EndEvent"],
          .djs-shape[data-element-id*="EndEvent"] .djs-visual > circle {
            fill: ${theme.palette.error.main}22 !important;
            stroke: ${theme.palette.error.main} !important;
            stroke-width: 3px !important;
            transition: fill 0.2s ease, stroke-width 0.2s ease;
          }

          /* Service Task - Blue */
          .djs-shape[data-element-id*="ServiceTask"] .djs-visual > rect,
          .djs-element[data-element-id*="ServiceTask"] .djs-visual > rect {
            fill: ${theme.palette.primary.main}18 !important;
            stroke: ${theme.palette.primary.main} !important;
            stroke-width: 2px !important;
            transition: fill 0.2s ease, stroke-width 0.2s ease;
          }

          /* User Task - Orange */
          .djs-shape[data-element-id*="Task"] .djs-visual > rect:not([data-element-id*="ServiceTask"]),
          .djs-element[data-element-id*="Task"] .djs-visual > rect {
            fill: ${theme.palette.warning.main}18 !important;
            stroke: ${theme.palette.warning.main} !important;
            stroke-width: 2px !important;
            transition: fill 0.2s ease, stroke-width 0.2s ease;
          }

          /* Gateway - Yellow */
          .djs-shape[data-element-id*="Gateway"] .djs-visual > path,
          .djs-shape[data-element-id*="Gateway"] .djs-visual > polygon,
          .djs-element[data-element-id*="Gateway"] .djs-visual > path,
          .djs-element[data-element-id*="Gateway"] .djs-visual > polygon {
            fill: #FFFDE7 !important;
            stroke: ${theme.palette.warning.dark} !important;
            stroke-width: 2px !important;
            transition: fill 0.2s ease, stroke-width 0.2s ease;
          }

          /* Node hover effects (v3.1-04) */
          .djs-element:hover .djs-visual > rect,
          .djs-element:hover .djs-visual > circle {
            filter: brightness(0.95);
            stroke-width: 3px !important;
          }
          .djs-element.selected .djs-outline {
            stroke: ${theme.palette.primary.main} !important;
            stroke-width: 2px !important;
          }
        `}
      </style>

      <Box display="flex" flexDirection="column" style={{ height }} border="1px solid" borderColor="divider" borderRadius={1} overflow="hidden">
        {/* Toolbar (v3.1-04 UX polish + v3.1-17 aria-labels) */}
        {showToolbar && (
          <Toolbar
            variant="dense"
            sx={{
              minHeight: 48,
              bgcolor: 'background.paper',
              borderBottom: 1,
              borderColor: 'divider',
              gap: 0.5,
              flexWrap: { xs: 'wrap', md: 'nowrap' },
              overflow: 'auto',
            }}
          >
            {/* File group */}
            <Typography variant="caption" color="text.secondary" sx={{ ml: 0.5, mr: 0.5, fontWeight: 600 }} component="span">FILE</Typography>
            <Tooltip title="Save (Ctrl+S)">
              <IconButton size="small" onClick={handleSave} disabled={!onSave} aria-label="Save workflow">
                <SaveIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Export BPMN XML">
              <IconButton size="small" onClick={handleExportXml} aria-label="Export BPMN XML file">
                <DownloadIcon fontSize="small" />
              </IconButton>
            </Tooltip>

            <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />

            {/* Edit group */}
            <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5, fontWeight: 600 }} component="span">EDIT</Typography>
            <Tooltip title="Undo (Ctrl+Z)">
              <IconButton size="small" onClick={handleUndo} aria-label="Undo last action">
                <UndoIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Redo (Ctrl+Y)">
              <IconButton size="small" onClick={handleRedo} aria-label="Redo last action">
                <RedoIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Delete Selected (Del)">
              <span>
                <IconButton size="small" onClick={handleDeleteSelected} disabled={!selectedElement} aria-label="Delete selected element">
                  <DeleteIcon fontSize="small" />
                </IconButton>
              </span>
            </Tooltip>

            <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />

            {/* View group */}
            <Typography variant="caption" color="text.secondary" sx={{ mr: 0.5, fontWeight: 600 }} component="span">VIEW</Typography>
            <Tooltip title="Zoom In (+)">
              <IconButton size="small" onClick={handleZoomIn} aria-label="Zoom in">
                <ZoomInIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Chip label={`${Math.round(zoomLevel * 100)}%`} size="small" variant="outlined" sx={{ minWidth: 52, height: 24, fontSize: '0.7rem' }} aria-label={`Zoom level ${Math.round(zoomLevel * 100)} percent`} />
            <Tooltip title="Zoom Out (-)">
              <IconButton size="small" onClick={handleZoomOut} aria-label="Zoom out">
                <ZoomOutIcon fontSize="small" />
              </IconButton>
            </Tooltip>
            <Tooltip title="Fit to Screen">
              <IconButton size="small" onClick={handleFitView} aria-label="Fit diagram to screen">
                <ZoomInMapIcon fontSize="small" />
              </IconButton>
            </Tooltip>

            <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />

            {/* Templates */}
            <Button
              size="small"
              startIcon={<TemplateIcon fontSize="small" />}
              onClick={(e) => setTemplateAnchorEl(e.currentTarget)}
              sx={{ textTransform: 'none', fontSize: '0.75rem' }}
              aria-label="Load workflow template"
              aria-haspopup="true"
              aria-expanded={Boolean(templateAnchorEl)}
            >
              Templates
            </Button>
            <Menu
              anchorEl={templateAnchorEl}
              open={Boolean(templateAnchorEl)}
              onClose={() => setTemplateAnchorEl(null)}
              PaperProps={{ sx: { maxHeight: 360, width: 320 } }}
            >
              {Object.entries(TEMPLATE_CATEGORY_LABELS).map(([cat, label]) => {
                const templates = WORKFLOW_TEMPLATES.filter((t) => t.category === cat);
                if (templates.length === 0) return null;
                return [
                  <Typography key={`header-${cat}`} variant="caption" color="text.secondary" sx={{ px: 2, py: 0.5, fontWeight: 600, display: 'block' }}>
                    {label}
                  </Typography>,
                  ...templates.map((t) => (
                    <MenuItem key={t.id} onClick={() => handleLoadTemplate(t)} dense aria-label={`Load template: ${t.name}`}>
                      <ListItemText primary={t.name} secondary={t.description} secondaryTypographyProps={{ fontSize: '0.7rem', lineHeight: 1.2, noWrap: true }} />
                    </MenuItem>
                  )),
                  <Divider key={`divider-${cat}`} />,
                ];
              })}
            </Menu>

            {/* Properties panel toggle (v3.1-04 collapsible) */}
            {showPropertiesPanel && !isMobile && (
              <>
                <Divider orientation="vertical" flexItem sx={{ mx: 1 }} />
                <Tooltip title={propertiesPanelOpen ? 'Close Properties Panel' : 'Open Properties Panel'}>
                  <IconButton
                    size="small"
                    onClick={() => setPropertiesPanelOpen((prev) => !prev)}
                    aria-label={propertiesPanelOpen ? 'Close properties panel' : 'Open properties panel'}
                    sx={{
                      transform: propertiesPanelOpen ? 'rotate(0deg)' : 'rotate(180deg)',
                      transition: 'transform 0.2s ease',
                    }}
                  >
                    <ChevronRightIcon fontSize="small" />
                  </IconButton>
                </Tooltip>
              </>
            )}
          </Toolbar>
        )}

        {/* Main content: BPMN canvas + properties panel */}
        <Box display="flex" flex={1} position="relative" overflow="hidden">
          {/* Loading overlay */}
          {loading && (
            <Box
              position="absolute" top={0} left={0} right={0} bottom={0}
              display="flex" alignItems="center" justifyContent="center"
              bgcolor="rgba(255,255,255,0.7)" zIndex={2}
            >
              <CircularProgress size={32} />
            </Box>
          )}

          {/* BPMN canvas */}
          <Box flex={1} position="relative">
            <div
              ref={containerRef}
              data-testid="bpmn-modeler-container"
              style={{ width: '100%', height: '100%' }}
            />
          </Box>

          {/* Properties Panel — collapsible (v3.1-04) */}
          {showPropertiesPanel && !isMobile && (
            <Collapse orientation="horizontal" in={propertiesPanelOpen} collapsedSize={0}>
              <Paper
                elevation={2}
                sx={{
                  width: 240,
                  flexShrink: 0,
                  borderLeft: 1,
                  borderColor: 'divider',
                  display: 'flex',
                  flexDirection: 'column',
                  overflow: 'auto',
                }}
              >
                {/* Panel header */}
                <Box display="flex" alignItems="center" justifyContent="space-between" p={1.5} borderBottom={1} borderColor="divider">
                  <Typography variant="subtitle2">Properties</Typography>
                  <IconButton
                    size="small"
                    onClick={() => setPropertiesPanelOpen(false)}
                    aria-label="Close properties panel"
                  >
                    <CloseIcon fontSize="small" />
                  </IconButton>
                </Box>

                {/* Panel content */}
                {selectedElement ? (
                  <Stack spacing={2} p={2}>
                    <TextField
                      label="Element ID"
                      value={selectedElement.id}
                      size="small"
                      InputProps={{ readOnly: true }}
                      fullWidth
                      inputProps={{ 'aria-label': 'Element ID' }}
                    />
                    <TextField
                      label="Name"
                      value={selectedElement.name}
                      onChange={(e) => handleUpdateElementName(e.target.value)}
                      size="small"
                      fullWidth
                      placeholder="Enter element name"
                      inputProps={{ 'aria-label': 'Element name' }}
                    />
                    <TextField
                      label="Type"
                      value={selectedElement.type}
                      size="small"
                      InputProps={{ readOnly: true }}
                      fullWidth
                      inputProps={{ 'aria-label': 'Element type' }}
                    />
                  </Stack>
                ) : (
                  <Box p={2}>
                    <Typography variant="body2" color="text.secondary">
                      Click an element to view its properties
                    </Typography>
                  </Box>
                )}
              </Paper>
            </Collapse>
          )}

          {/* Mobile properties panel — bottom sheet */}
          {showPropertiesPanel && isMobile && selectedElement && (
            <Paper
              elevation={4}
              sx={{
                position: 'absolute',
                bottom: 0,
                left: 0,
                right: 0,
                maxHeight: '50%',
                overflow: 'auto',
                borderTop: 1,
                borderColor: 'divider',
                borderRadius: '8px 8px 0 0',
              }}
            >
              <Box display="flex" alignItems="center" justifyContent="space-between" p={1.5} borderBottom={1} borderColor="divider">
                <Typography variant="subtitle2">Properties</Typography>
                <IconButton
                  size="small"
                  onClick={() => setSelectedElement(null)}
                  aria-label="Close properties panel"
                >
                  <CloseIcon fontSize="small" />
                </IconButton>
              </Box>
              <Stack spacing={2} p={2}>
                <TextField label="Element ID" value={selectedElement.id} size="small" InputProps={{ readOnly: true }} fullWidth />
                <TextField
                  label="Name"
                  value={selectedElement.name}
                  onChange={(e) => handleUpdateElementName(e.target.value)}
                  size="small"
                  fullWidth
                  placeholder="Enter element name"
                />
                <TextField label="Type" value={selectedElement.type} size="small" InputProps={{ readOnly: true }} fullWidth />
              </Stack>
            </Paper>
          )}
        </Box>

        {/* Snackbar for notifications */}
        <Snackbar
          open={!!snackbar}
          autoHideDuration={2000}
          onClose={() => setSnackbar(null)}
          message={snackbar}
          anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
        />
      </Box>
    </>
  );
}
