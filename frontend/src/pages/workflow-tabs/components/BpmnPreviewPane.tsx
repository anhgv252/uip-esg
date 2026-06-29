import {
  Box,
  Paper,
  Typography,
  Chip,
  Button,
  TextField,
  Stack,
  Divider,
  Alert,
  Collapse,
  List,
  ListItem,
  ListItemIcon,
  ListItemText,
  CircularProgress,
} from '@mui/material';
import {
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  FlashOn as SimulateIcon,
  CheckCircleOutline as OkIcon,
  Warning as WarningIcon,
  SkipNext as SkipIcon,
  Error as ErrorIcon,
} from '@mui/icons-material';
import { useState } from 'react';
import type { WorkflowReview } from '@/hooks/useOperatorReview';
import type { SimulationResult } from '@/types/nlWorkflow';

interface BpmnPreviewPaneProps {
  review: WorkflowReview | null;
  simulationResult?: SimulationResult | null;
  onApprove: () => void;
  onReject: (reason: string) => void;
  onSimulate?: () => void;
  isPending: boolean;
  isSimulating?: boolean;
}

const RISK_LEVELS = {
  flood_response: 'HIGH',
  emergency_evacuation: 'HIGH',
  aqi_alert: 'MEDIUM',
  citizen_notification: 'MEDIUM',
  energy_optimization: 'LOW',
  esg_report: 'LOW',
} as const;

const RISK_COLORS = {
  HIGH: 'error',
  MEDIUM: 'warning',
  LOW: 'success',
} as const;

const STEP_STATUS_ICONS = {
  OK: <OkIcon color="success" fontSize="small" />,
  WARNING: <WarningIcon color="warning" fontSize="small" />,
  SKIPPED: <SkipIcon color="disabled" fontSize="small" />,
  ERROR: <ErrorIcon color="error" fontSize="small" />,
} as const;

function getConfidenceColor(confidence: number): 'success' | 'warning' | 'error' {
  if (confidence >= 0.9) return 'success';
  if (confidence >= 0.7) return 'warning';
  return 'error';
}

export default function BpmnPreviewPane({
  review,
  simulationResult,
  onApprove,
  onReject,
  onSimulate,
  isPending,
  isSimulating = false,
}: BpmnPreviewPaneProps) {
  const [rejectReason, setRejectReason] = useState('');
  const [showRejectForm, setShowRejectForm] = useState(false);
  const [showSimulation, setShowSimulation] = useState(true);

  if (!review) {
    return (
      <Paper
        variant="outlined"
        sx={{ p: 3, display: 'flex', alignItems: 'center', justifyContent: 'center', height: 400 }}
      >
        <Typography color="text.secondary" variant="body2">
          Select a workflow review to preview
        </Typography>
      </Paper>
    );
  }

  const riskLevel = RISK_LEVELS[review.intent as keyof typeof RISK_LEVELS] ?? 'MEDIUM';
  const riskColor = RISK_COLORS[riskLevel];

  const handleRejectSubmit = () => {
    if (rejectReason.trim()) {
      onReject(rejectReason);
      setRejectReason('');
      setShowRejectForm(false);
    }
  };

  return (
    <Paper variant="outlined" sx={{ p: 2, height: '100%' }}>
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
        <Typography variant="h6">Workflow Review</Typography>
        <Chip
          label={`Risk: ${riskLevel}`}
          color={riskColor}
          size="small"
        />
      </Box>

      {/* BR-010 Safety Warning */}
      <Alert severity="info" sx={{ mb: 2 }}>
        <strong>BR-010:</strong> All AI-generated workflows require operator approval before execution.
      </Alert>

      {/* Extracted Entities */}
      <Box mb={2}>
        <Typography variant="subtitle2" gutterBottom>
          Extracted Entities
        </Typography>
        <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
          {Object.entries(review.entities).map(([key, value]) => (
            <Chip
              key={key}
              label={`${key}: ${String(value)}`}
              size="small"
              variant="outlined"
            />
          ))}
        </Stack>
      </Box>

      <Divider sx={{ my: 2 }} />

      {/* BPMN XML Viewer */}
      <Box mb={2}>
        <Typography variant="subtitle2" gutterBottom>
          BPMN XML (Read-only)
        </Typography>
        <Paper
          variant="outlined"
          sx={{
            p: 1.5,
            maxHeight: 200,
            overflow: 'auto',
            backgroundColor: 'grey.50',
            fontFamily: 'monospace',
            fontSize: '0.75rem',
            whiteSpace: 'pre-wrap',
            wordBreak: 'break-all',
          }}
        >
          {review.bpmnXml}
        </Paper>
      </Box>

      <Divider sx={{ my: 2 }} />

      {/* Simulation Results Panel */}
      {simulationResult && (
        <>
          <Box mb={2}>
            <Box display="flex" justifyContent="space-between" alignItems="center" mb={1}>
              <Typography variant="subtitle2">Simulation Results</Typography>
              <Chip
                label={simulationResult.success ? 'Success' : 'Failed'}
                color={simulationResult.success ? 'success' : 'error'}
                size="small"
              />
            </Box>

            {simulationResult.warnings.length > 0 && (
              <Alert severity="warning" sx={{ mb: 2 }}>
                <Typography variant="subtitle2" gutterBottom>
                  Warnings ({simulationResult.warnings.length})
                </Typography>
                <ul style={{ margin: 0, paddingLeft: 20 }}>
                  {simulationResult.warnings.map((w, i) => (
                    <li key={i}>
                      <Typography variant="body2">{w}</Typography>
                    </li>
                  ))}
                </ul>
              </Alert>
            )}

            <Collapse in={showSimulation}>
              <List dense>
                {simulationResult.steps.map((step, idx) => (
                  <ListItem key={idx}>
                    <ListItemIcon sx={{ minWidth: 36 }}>
                      {STEP_STATUS_ICONS[step.status]}
                    </ListItemIcon>
                    <ListItemText
                      primary={step.elementName}
                      secondary={step.message}
                      primaryTypographyProps={{ variant: 'body2' }}
                      secondaryTypographyProps={{ variant: 'caption' }}
                    />
                  </ListItem>
                ))}
              </List>
            </Collapse>

            <Typography variant="caption" color="text.secondary">
              Simulated in {simulationResult.durationMs}ms
            </Typography>
          </Box>
          <Divider sx={{ my: 2 }} />
        </>
      )}

      {/* Action Buttons */}
      {isPending && (
        <>
          {!showRejectForm ? (
            <Stack direction="row" spacing={2}>
              {onSimulate && (
                <Button
                  variant="outlined"
                  color="primary"
                  startIcon={isSimulating ? <CircularProgress size={16} /> : <SimulateIcon />}
                  onClick={onSimulate}
                  disabled={isSimulating}
                >
                  {isSimulating ? 'Simulating...' : 'Simulate'}
                </Button>
              )}
              <Button
                variant="contained"
                color="success"
                startIcon={<ApproveIcon />}
                onClick={onApprove}
                fullWidth
              >
                Approve & Deploy
              </Button>
              <Button
                variant="outlined"
                color="error"
                startIcon={<RejectIcon />}
                onClick={() => setShowRejectForm(true)}
                fullWidth
              >
                Reject
              </Button>
            </Stack>
          ) : (
            <Box>
              <TextField
                fullWidth
                multiline
                rows={3}
                label="Rejection Reason"
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                placeholder="Explain why this workflow is rejected..."
                sx={{ mb: 2 }}
              />
              <Stack direction="row" spacing={2}>
                <Button
                  variant="contained"
                  color="error"
                  onClick={handleRejectSubmit}
                  disabled={!rejectReason.trim() || isPending}
                  fullWidth
                >
                  Confirm Reject
                </Button>
                <Button
                  variant="outlined"
                  onClick={() => {
                    setShowRejectForm(false);
                    setRejectReason('');
                  }}
                  disabled={isPending}
                  fullWidth
                >
                  Cancel
                </Button>
              </Stack>
            </Box>
          )}
        </>
      )}

      {review.status !== 'PENDING' && (
        <Alert severity={review.status === 'APPROVED' ? 'success' : 'error'}>
          Workflow {review.status.toLowerCase()}
        </Alert>
      )}
    </Paper>
  );
}
