import { useEffect, useRef, useState } from 'react';
import BpmnJS from 'bpmn-js';
import { Box, CircularProgress, Alert, Typography } from '@mui/material';

interface Props {
  xml: string | null | undefined;
  height?: number;
}

export default function BpmnViewer({ xml, height = 400 }: Props) {
  const containerRef = useRef<HTMLDivElement>(null);
  const viewerRef = useRef<InstanceType<typeof BpmnJS> | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);

  useEffect(() => {
    if (!containerRef.current) return;

    viewerRef.current = new BpmnJS({ container: containerRef.current });

    return () => {
      viewerRef.current?.destroy();
      viewerRef.current = null;
    };
  }, []);

  useEffect(() => {
    if (!viewerRef.current || !xml) return;

    setLoading(true);
    setError(null);

    viewerRef.current
      .importXML(xml)
      .then(() => {
        viewerRef.current?.get<{ fit: () => void }>('canvas').fit?.();
      })
      .catch((err: Error) => {
        setError(err.message ?? 'Failed to render BPMN diagram');
      })
      .finally(() => setLoading(false));
  }, [xml]);

  if (!xml) {
    return (
      <Box
        data-testid="bpmn-viewer-root"
        display="flex"
        alignItems="center"
        justifyContent="center"
        style={{ height }}
        bgcolor="background.default"
        borderRadius={1}
        border="1px solid"
        borderColor="divider"
      >
        <Typography color="text.secondary" variant="body2">
          Select a process definition to view the diagram
        </Typography>
      </Box>
    );
  }

  return (
    <Box data-testid="bpmn-viewer-root" position="relative" style={{ height }} border="1px solid" borderColor="divider" borderRadius={1} overflow="hidden">
      {loading && (
        <Box
          position="absolute" top={0} left={0} right={0} bottom={0}
          display="flex" alignItems="center" justifyContent="center"
          bgcolor="rgba(255,255,255,0.7)" zIndex={1}
        >
          <CircularProgress size={32} />
        </Box>
      )}
      {error && (
        <Alert severity="error" sx={{ position: 'absolute', top: 8, left: 8, right: 8, zIndex: 1 }}>
          {error}
        </Alert>
      )}
      <div ref={containerRef} style={{ width: '100%', height: '100%' }} />
    </Box>
  );
}
