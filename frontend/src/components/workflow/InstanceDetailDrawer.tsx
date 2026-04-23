import {
  Drawer,
  Box,
  Typography,
  IconButton,
  Divider,
  Chip,
  CircularProgress,
  Alert,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Paper,
  Accordion,
  AccordionSummary,
  AccordionDetails,
} from '@mui/material';
import CloseIcon from '@mui/icons-material/Close';
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import SmartToyIcon from '@mui/icons-material/SmartToy';
import { format } from 'date-fns';
import type { ProcessInstance } from '@/api/workflow';
import { useInstanceVariables } from '@/hooks/useWorkflowData';

interface Props {
  instance: ProcessInstance | null;
  onClose: () => void;
}

const AI_VARIABLE_KEYS = ['aiDecision', 'aiAnalysis', 'aiConfidence', 'alertLevel', 'aiReasoning', 'aiSeverity', 'aiRecommendedActions'];

function VariableValue({ value }: { value: unknown }) {
  if (value === null || value === undefined) return <Typography variant="body2" color="text.disabled">—</Typography>;
  if (typeof value === 'boolean') {
    return <Chip label={String(value)} size="small" color={value ? 'success' : 'default'} />;
  }
  if (typeof value === 'object') {
    return (
      <Typography variant="caption" fontFamily="monospace" component="pre" sx={{ m: 0, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>
        {JSON.stringify(value, null, 2)}
      </Typography>
    );
  }
  return <Typography variant="body2">{String(value)}</Typography>;
}

export default function InstanceDetailDrawer({ instance, onClose }: Props) {
  const { data: variables, isLoading, error } = useInstanceVariables(instance?.id ?? null);

  const aiVars = variables
    ? Object.entries(variables).filter(([k]) => AI_VARIABLE_KEYS.includes(k))
    : [];

  const otherVars = variables
    ? Object.entries(variables).filter(([k]) => !AI_VARIABLE_KEYS.includes(k))
    : [];

  return (
    <Drawer
      anchor="right"
      open={!!instance}
      onClose={onClose}
      PaperProps={{ sx: { width: { xs: '100%', sm: 480 } } }}
    >
      {instance && (
        <Box sx={{ p: 3, height: '100%', overflowY: 'auto' }}>
          {/* Header */}
          <Box display="flex" justifyContent="space-between" alignItems="flex-start" mb={2}>
            <Box>
              <Typography variant="h6" gutterBottom>
                Process Instance
              </Typography>
              <Typography variant="caption" fontFamily="monospace" color="text.secondary">
                {instance.id}
              </Typography>
            </Box>
            <IconButton onClick={onClose} size="small" aria-label="close">
              <CloseIcon />
            </IconButton>
          </Box>

          {/* Summary */}
          <Paper variant="outlined" sx={{ p: 2, mb: 2 }}>
            <Table size="small">
              <TableBody>
                {[
                  ['Process Key', instance.processDefinitionKey],
                  ['Business Key', instance.businessKey ?? '—'],
                  ['State', instance.state],
                  ['Started', format(new Date(instance.startTime), 'dd/MM/yyyy HH:mm:ss')],
                ].map(([label, value]) => (
                  <TableRow key={label}>
                    <TableCell sx={{ border: 0, pl: 0, py: 0.5, fontWeight: 600, color: 'text.secondary', width: '40%' }}>
                      {label}
                    </TableCell>
                    <TableCell sx={{ border: 0, py: 0.5 }}>
                      {label === 'State' ? (
                        <Chip
                          label={value}
                          size="small"
                          color={value === 'ACTIVE' ? 'warning' : value === 'COMPLETED' ? 'success' : 'error'}
                        />
                      ) : (
                        <Typography variant="body2">{value}</Typography>
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Paper>

          <Divider sx={{ mb: 2 }} />

          {isLoading && (
            <Box display="flex" justifyContent="center" py={4}>
              <CircularProgress size={28} />
            </Box>
          )}
          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Failed to load variables
            </Alert>
          )}

          {/* AI Decision section */}
          {aiVars.length > 0 && (
            <Accordion defaultExpanded elevation={0} variant="outlined" sx={{ mb: 2 }}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Box display="flex" alignItems="center" gap={1}>
                  <SmartToyIcon color="primary" fontSize="small" />
                  <Typography variant="subtitle2">AI Decision</Typography>
                </Box>
              </AccordionSummary>
              <AccordionDetails sx={{ pt: 0 }}>
                <Table size="small">
                  <TableBody>
                    {aiVars.map(([key, value]) => (
                      <TableRow key={key}>
                        <TableCell sx={{ border: 0, pl: 0, py: 0.75, fontWeight: 600, color: 'text.secondary', width: '40%', verticalAlign: 'top' }}>
                          {key}
                        </TableCell>
                        <TableCell sx={{ border: 0, py: 0.75, wordBreak: 'break-word' }}>
                          <VariableValue value={value} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </AccordionDetails>
            </Accordion>
          )}

          {/* All variables section */}
          {variables && (
            <Accordion elevation={0} variant="outlined" defaultExpanded={aiVars.length === 0}>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="subtitle2">
                  All Variables ({Object.keys(variables).length})
                </Typography>
              </AccordionSummary>
              <AccordionDetails sx={{ pt: 0 }}>
                {otherVars.length === 0 && aiVars.length > 0 && (
                  <Typography variant="body2" color="text.secondary">
                    No additional variables
                  </Typography>
                )}
                <Table size="small">
                  <TableBody>
                    {otherVars.map(([key, value]) => (
                      <TableRow key={key}>
                        <TableCell sx={{ border: 0, pl: 0, py: 0.75, fontWeight: 600, color: 'text.secondary', width: '40%', verticalAlign: 'top' }}>
                          {key}
                        </TableCell>
                        <TableCell sx={{ border: 0, py: 0.75, wordBreak: 'break-word' }}>
                          <VariableValue value={value} />
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </AccordionDetails>
            </Accordion>
          )}
        </Box>
      )}
    </Drawer>
  );
}
