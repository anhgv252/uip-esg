import { useState, useEffect, useRef } from 'react';
import { useForm, Controller } from 'react-hook-form';
import { useNavigate } from 'react-router-dom';
import {
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  Stepper,
  Step,
  StepLabel,
  Button,
  Box,
  Typography,
  TextField,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  FormControlLabel,
  Switch,
  FormHelperText,
  Grid,
  Card,
  CardContent,
  CircularProgress,
  Table,
  TableBody,
  TableCell,
  TableRow,
  Chip,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import type { WorkflowTemplate, TemplateParam } from '@/types/workflowTemplate';
import TemplateGallery from '@/components/workflow/TemplateGallery';

// ── Types ─────────────────────────────────────────────────────────────────────

export interface WorkflowWizardProps {
  open: boolean;
  onClose: () => void;
  onDeploy: (template: WorkflowTemplate, variables: Record<string, unknown>) => Promise<void>;
  isPending?: boolean;
}

type FormValues = Record<string, string | number | boolean>;

// State machine: idle → confirming → success | error
//                error → idle (try again)
type DeployStatus = 'idle' | 'confirming' | 'success' | 'error';

const STEPS = ['Choose Template', 'Customize', 'Review & Deploy'];

// ── Step 2 form field ─────────────────────────────────────────────────────────

interface ParamFieldProps {
  param: TemplateParam;
  control: ReturnType<typeof useForm<FormValues>>['control'];
  errors: ReturnType<typeof useForm<FormValues>>['formState']['errors'];
}

function ParamField({ param, control, errors }: ParamFieldProps) {
  const errorMsg = errors[param.key]?.message as string | undefined;

  if (param.type === 'boolean') {
    return (
      <FormControl fullWidth>
        <Controller
          name={param.key}
          control={control}
          render={({ field }) => (
            <FormControlLabel
              label={param.label}
              control={
                <Switch
                  checked={Boolean(field.value)}
                  onChange={(e) => field.onChange(e.target.checked)}
                  inputProps={{ 'aria-label': param.label }}
                />
              }
            />
          )}
        />
        {param.description && (
          <FormHelperText>{param.description}</FormHelperText>
        )}
      </FormControl>
    );
  }

  if (param.type === 'select') {
    const labelId = `wizard-select-label-${param.key}`;
    return (
      <FormControl fullWidth error={!!errorMsg} required={param.required}>
        <InputLabel id={labelId}>{param.label}</InputLabel>
        <Controller
          name={param.key}
          control={control}
          rules={{
            required: param.required ? `${param.label} is required` : undefined,
          }}
          render={({ field }) => (
            <Select
              labelId={labelId}
              label={param.label}
              value={field.value ?? ''}
              onChange={(e) => field.onChange(e.target.value)}
              inputProps={{ 'aria-label': param.label }}
            >
              {param.options?.map((opt) => (
                <MenuItem key={opt} value={opt}>
                  {opt}
                </MenuItem>
              ))}
            </Select>
          )}
        />
        {(errorMsg ?? param.description) && (
          <FormHelperText>{errorMsg ?? param.description}</FormHelperText>
        )}
      </FormControl>
    );
  }

  // string | number
  return (
    <Controller
      name={param.key}
      control={control}
      rules={{
        validate: (v) => {
          if (param.required && (v === '' || v === undefined || v === null)) {
            return `${param.label} is required`;
          }
          return true;
        },
      }}
      render={({ field }) => (
        <TextField
          fullWidth
          label={param.label}
          type={param.type === 'number' ? 'number' : 'text'}
          value={field.value ?? ''}
          onChange={(e) => {
            const raw = e.target.value;
            // Preserve empty string so required validation can catch it
            field.onChange(param.type === 'number' && raw !== '' ? Number(raw) : raw);
          }}
          error={!!errorMsg}
          helperText={errorMsg ?? param.description}
          required={param.required}
          inputProps={{ 'aria-label': param.label }}
        />
      )}
    />
  );
}

// ── Step 3 review ─────────────────────────────────────────────────────────────

function ReviewStep({
  template,
  values,
}: {
  template: WorkflowTemplate;
  values: FormValues;
}) {
  return (
    <Box>
      <Box display="flex" alignItems="center" gap={1} mb={2}>
        <CheckCircleOutlineIcon color="success" />
        <Typography variant="subtitle1" fontWeight={700}>
          Ready to deploy
        </Typography>
      </Box>

      <Card variant="outlined" sx={{ mb: 2 }}>
        <CardContent>
          <Typography variant="subtitle2" gutterBottom>
            {template.name}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {template.description}
          </Typography>
          <Box mt={1}>
            <Chip label={template.category} size="small" sx={{ mr: 0.5 }} />
            <Chip
              label={`BPMN: ${template.bpmnKey}`}
              size="small"
              variant="outlined"
            />
          </Box>
        </CardContent>
      </Card>

      <Typography variant="subtitle2" gutterBottom>
        Variables
      </Typography>
      <Table size="small" aria-label="Review variables table">
        <TableBody>
          {template.params.map((param) => (
            <TableRow key={param.key}>
              <TableCell sx={{ fontWeight: 600, width: '40%' }}>
                {param.label}
              </TableCell>
              <TableCell>
                {values[param.key] !== undefined && values[param.key] !== ''
                  ? String(values[param.key])
                  : <Typography component="span" variant="body2" color="text.disabled">—</Typography>
                }
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}

// ── Wizard ────────────────────────────────────────────────────────────────────

export default function WorkflowWizard({
  open,
  onClose,
  onDeploy,
  isPending = false,
}: WorkflowWizardProps) {
  const navigate = useNavigate();
  const [activeStep, setActiveStep] = useState(0);
  const [selectedTemplate, setSelectedTemplate] =
    useState<WorkflowTemplate | null>(null);
  const [deployStatus, setDeployStatus] = useState<DeployStatus>('idle');
  const [deployError, setDeployError] = useState<string | null>(null);

  const contentRef = useRef<HTMLDivElement>(null);

  const {
    control,
    reset,
    trigger,
    getValues,
    formState: { errors },
  } = useForm<FormValues>({ mode: 'onTouched' });

  // Reset all state when dialog opens/closes
  useEffect(() => {
    if (!open) {
      setActiveStep(0);
      setSelectedTemplate(null);
      setDeployStatus('idle');
      setDeployError(null);
      reset({});
    }
  }, [open, reset]);

  // Focus first interactive element on step/state change (WCAG 2.1 AA focus management)
  useEffect(() => {
    if (!open) return;
    const timer = setTimeout(() => {
      const el = contentRef.current?.querySelector<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), [role="combobox"]:not([aria-disabled="true"])',
      );
      el?.focus();
    }, 0);
    return () => clearTimeout(timer);
  }, [activeStep, open, deployStatus]);

  // Reset form when a new template is selected
  const handleSelectTemplate = (template: WorkflowTemplate) => {
    setSelectedTemplate(template);
    const defaults: FormValues = {};
    for (const param of template.params) {
      if (param.defaultValue !== undefined) {
        defaults[param.key] = param.defaultValue as string | number | boolean;
      } else if (param.type === 'boolean') {
        defaults[param.key] = false;
      } else {
        defaults[param.key] = '';
      }
    }
    reset(defaults);
    setActiveStep(1);
  };

  const handleBack = () => {
    setActiveStep((s) => Math.max(0, s - 1));
  };

  const handleNextFromStep2 = async () => {
    const valid = await trigger();
    if (valid) {
      setActiveStep(2);
    }
  };

  // Transitions wizard into inline confirmation state
  const handleDeploy = () => {
    if (!selectedTemplate) return;
    setDeployStatus('confirming');
  };

  // Returns to review step from confirmation without deploying
  const handleCancelConfirm = () => {
    setDeployStatus('idle');
  };

  // Called after user confirms deployment
  const handleConfirmDeploy = async () => {
    if (!selectedTemplate) return;
    const values = getValues();
    // Coerce empty strings to undefined, keep booleans/numbers as-is
    const variables: Record<string, unknown> = {};
    for (const param of selectedTemplate.params) {
      const raw = values[param.key];
      if (raw !== undefined && raw !== '') {
        variables[param.key] = raw;
      }
    }
    try {
      await onDeploy(selectedTemplate, variables);
      setDeployStatus('success');
    } catch (err) {
      setDeployStatus('error');
      setDeployError(err instanceof Error ? err.message : 'Unknown error');
    }
  };

  const handleTryAgain = () => {
    setDeployStatus('idle');
    setDeployError(null);
    // activeStep stays at 2 (review step)
  };

  const handleViewInstances = () => {
    navigate('/ai-workflow');
    onClose();
  };

  return (
    <Dialog open={open} onClose={onClose} fullWidth maxWidth="md">
      <DialogTitle>New Workflow</DialogTitle>

      <DialogContent dividers>
        <Box ref={contentRef}>
          {/* ── Confirmation state (inline — no second Dialog to avoid aria-hidden conflicts) ── */}
          {deployStatus === 'confirming' && selectedTemplate && (
            <Box py={2} aria-labelledby="confirm-deploy-heading">
              <Typography variant="h6" id="confirm-deploy-heading" gutterBottom>
                Confirm Deployment
              </Typography>
              <Typography>
                Are you sure? This will deploy{' '}
                <strong>{selectedTemplate.name}</strong> workflow to production.
              </Typography>
            </Box>
          )}

          {/* ── Success state ─────────────────────────────────────── */}
          {deployStatus === 'success' && selectedTemplate && (
            <Box textAlign="center" py={4}>
              <CheckCircleIcon
                color="success"
                sx={{ fontSize: 64, mb: 2 }}
                aria-hidden="true"
              />
              <Typography variant="h6" gutterBottom>
                Workflow deployed successfully!
              </Typography>
              <Typography color="text.secondary" gutterBottom>
                {selectedTemplate.name}
              </Typography>
              <Box mt={3} display="flex" gap={2} justifyContent="center" flexWrap="wrap">
                <Button
                  variant="outlined"
                  onClick={handleViewInstances}
                  aria-label="View in Process Instances"
                >
                  View in Process Instances
                </Button>
                <Button
                  variant="contained"
                  onClick={onClose}
                  aria-label="Close wizard after success"
                >
                  Close
                </Button>
              </Box>
            </Box>
          )}

          {/* ── Error state ───────────────────────────────────────── */}
          {deployStatus === 'error' && (
            <Box py={2}>
              <Typography color="error" role="alert" gutterBottom>
                Deployment failed: {deployError}
              </Typography>
              <Box mt={2} display="flex" gap={2}>
                <Button
                  variant="outlined"
                  onClick={handleTryAgain}
                  aria-label="Try deploy again"
                >
                  Try Again
                </Button>
                <Button onClick={onClose} aria-label="Cancel wizard">
                  Cancel
                </Button>
              </Box>
            </Box>
          )}

          {/* ── Normal wizard steps ───────────────────────────────── */}
          {deployStatus === 'idle' && (
            <>
              <Stepper activeStep={activeStep} sx={{ mb: 3 }}>
                {STEPS.map((label) => (
                  <Step key={label}>
                    <StepLabel>{label}</StepLabel>
                  </Step>
                ))}
              </Stepper>

              {/* Step 1 — Choose Template */}
              {activeStep === 0 && (
                <TemplateGallery onSelectTemplate={handleSelectTemplate} />
              )}

              {/* Step 2 — Customize */}
              {activeStep === 1 && selectedTemplate && (
                <Box>
                  <Card variant="outlined" sx={{ mb: 2, bgcolor: 'action.hover' }}>
                    <CardContent sx={{ py: 1.5, '&:last-child': { pb: 1.5 } }}>
                      <Typography variant="subtitle2">
                        Selected: <strong>{selectedTemplate.name}</strong>
                      </Typography>
                    </CardContent>
                  </Card>

                  <Grid container spacing={2}>
                    {selectedTemplate.params.map((param) => (
                      <Grid
                        item
                        xs={12}
                        md={6}
                        key={param.key}
                      >
                        <ParamField
                          param={param}
                          control={control}
                          errors={errors}
                        />
                      </Grid>
                    ))}
                  </Grid>
                </Box>
              )}

              {/* Step 3 — Review & Deploy */}
              {activeStep === 2 && selectedTemplate && (
                <ReviewStep template={selectedTemplate} values={getValues()} />
              )}
            </>
          )}
        </Box>
      </DialogContent>

      <DialogActions sx={{ px: 3, py: 2 }}>
        {/* ── Idle wizard actions ─────────────────────────────────── */}
        {deployStatus === 'idle' && (
          <>
            <Button onClick={onClose} aria-label="Cancel wizard">
              Cancel
            </Button>

            {activeStep > 0 && (
              <Button onClick={handleBack} aria-label="Go back to previous step">
                Back
              </Button>
            )}

            {activeStep === 1 && (
              <Button
                variant="contained"
                onClick={() => void handleNextFromStep2()}
                aria-label="Continue to review step"
              >
                Next
              </Button>
            )}

            {activeStep === 2 && (
              <Button
                variant="contained"
                color="success"
                onClick={handleDeploy}
                disabled={isPending}
                startIcon={isPending ? <CircularProgress size={16} /> : undefined}
                aria-label="Deploy workflow"
              >
                {isPending ? 'Deploying…' : 'Deploy'}
              </Button>
            )}
          </>
        )}

        {/* ── Confirmation actions ─────────────────────────────────── */}
        {deployStatus === 'confirming' && (
          <>
            <Button onClick={handleCancelConfirm} aria-label="Cancel deployment">
              Cancel
            </Button>
            <Button
              variant="contained"
              color="success"
              onClick={() => void handleConfirmDeploy()}
              disabled={isPending}
              startIcon={isPending ? <CircularProgress size={16} /> : undefined}
              aria-label="Confirm deploy"
            >
              {isPending ? 'Deploying…' : 'Confirm Deploy'}
            </Button>
          </>
        )}
      </DialogActions>
    </Dialog>
  );
}
