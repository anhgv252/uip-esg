import { useEffect, useRef, useState, useCallback } from 'react';
import BpmnModeler from 'bpmn-js/lib/Modeler';
import { Box, CircularProgress, Alert, Snackbar } from '@mui/material';

interface Props {
  initialXml?: string | null;
  onSave?: (xml: string) => void;
  readOnly?: boolean;
  height?: number;
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
 * Supports drag & drop, editing, and save/export of BPMN XML.
 */
export default function WorkflowModeler({ initialXml, onSave, height = 500 }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const modelerRef = useRef<InstanceType<typeof BpmnModeler> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [snackbar, setSnackbar] = useState<string | null>(null);

  // Initialize modeler
  useEffect(() => {
    if (!containerRef.current) return;

    const modeler = new BpmnModeler({
      container: containerRef.current,
      keyboard: { bindTo: document },
    });

    modelerRef.current = modeler;

    const xml = initialXml || EMPTY_BPMN;
    modeler
      .importXML(xml)
      .then(() => {
        const canvas = modeler.get<{ zoom: (level: string) => void }>('canvas');
        canvas.zoom('fit-viewport');
      })
      .catch((err: Error) => {
        setError(err.message ?? 'Failed to load BPMN diagram');
      })
      .finally(() => setLoading(false));

    return () => {
      modeler.destroy();
      modelerRef.current = null;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  // Reload XML when initialXml changes
  useEffect(() => {
    if (!modelerRef.current || !initialXml) return;
    modelerRef.current
      .importXML(initialXml)
      .catch((err: Error) => setError(err.message));
  }, [initialXml]);

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
    <Box position="relative" style={{ height }} border="1px solid" borderColor="divider" borderRadius={1} overflow="hidden">
      {loading && (
        <Box
          position="absolute" top={0} left={0} right={0} bottom={0}
          display="flex" alignItems="center" justifyContent="center"
          bgcolor="rgba(255,255,255,0.7)" zIndex={2}
        >
          <CircularProgress size={32} />
        </Box>
      )}
      <div
        ref={containerRef}
        data-testid="bpmn-modeler-container"
        style={{ width: '100%', height: '100%' }}
      />
      <Snackbar
        open={!!snackbar}
        autoHideDuration={2000}
        onClose={() => setSnackbar(null)}
        message={snackbar}
        anchorOrigin={{ vertical: 'bottom', horizontal: 'center' }}
      />
    </Box>
  );
}
