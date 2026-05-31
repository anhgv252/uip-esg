import { useState, useEffect } from 'react';
import {
  Box,
  TextField,
  Slider,
  Select,
  MenuItem,
  FormControl,
  InputLabel,
  Typography,
  Paper,
  Stack,
  Chip,
} from '@mui/material';
import SmartToyIcon from '@mui/icons-material/SmartToy';

const AI_MODELS = [
  { value: 'claude-sonnet-4-6', label: 'Claude Sonnet 4.6' },
  { value: 'claude-haiku-4-5', label: 'Claude Haiku 4.5' },
];

interface AiNodeConfig {
  prompt: string;
  confidenceThreshold: number;
  model: string;
}

interface Props {
  /** Currently selected AI node ID (null if no AI node selected) */
  selectedNodeId?: string | null;
  /** Current config values */
  config?: AiNodeConfig;
  /** Callback when config changes */
  onConfigChange?: (config: AiNodeConfig) => void;
}

/**
 * Configuration panel for AI Decision nodes in the BPMN workflow.
 * Shows prompt, confidence threshold slider, and model selector.
 */
export default function AiNodeConfigPanel({ selectedNodeId, config, onConfigChange }: Props) {
  const [prompt, setPrompt] = useState(config?.prompt ?? '');
  const [confidence, setConfidence] = useState(config?.confidenceThreshold ?? 0.85);
  const [model, setModel] = useState(config?.model ?? 'claude-sonnet-4-6');

  // Sync external config changes
  useEffect(() => {
    if (config) {
      setPrompt(config.prompt);
      setConfidence(config.confidenceThreshold);
      setModel(config.model);
    }
  }, [config]);

  const handleChange = (updates: Partial<AiNodeConfig>) => {
    const newConfig: AiNodeConfig = {
      prompt: updates.prompt ?? prompt,
      confidenceThreshold: updates.confidenceThreshold ?? confidence,
      model: updates.model ?? model,
    };
    onConfigChange?.(newConfig);
  };

  if (!selectedNodeId) {
    return (
      <Paper variant="outlined" sx={{ p: 2, width: 280 }}>
        <Box display="flex" alignItems="center" gap={1} mb={1}>
          <SmartToyIcon color="disabled" fontSize="small" />
          <Typography variant="caption" color="text.secondary">
            Select an AI Decision node to configure
          </Typography>
        </Box>
      </Paper>
    );
  }

  return (
    <Paper variant="outlined" sx={{ p: 2, width: 280 }}>
      <Box display="flex" alignItems="center" gap={1} mb={2}>
        <SmartToyIcon color="primary" fontSize="small" />
        <Typography variant="subtitle2" fontWeight={700}>
          AI Decision Config
        </Typography>
        <Chip label="AI" size="small" color="secondary" variant="outlined" />
      </Box>

      <Stack spacing={2}>
        {/* AI Prompt */}
        <TextField
          label="AI Prompt"
          multiline
          rows={3}
          value={prompt}
          onChange={(e) => {
            setPrompt(e.target.value);
            handleChange({ prompt: e.target.value });
          }}
          placeholder="Analyze sensor data and recommend action..."
          fullWidth
          size="small"
          inputProps={{ style: { fontSize: '0.85rem' } }}
        />

        {/* Confidence Threshold */}
        <Box>
          <Typography variant="caption" color="text.secondary" gutterBottom display="block">
            Confidence Threshold
          </Typography>
          <Slider
            value={confidence}
            onChange={(_, v) => {
              const val = v as number;
              setConfidence(val);
              handleChange({ confidenceThreshold: val });
            }}
            min={0}
            max={1}
            step={0.05}
            marks={[
              { value: 0.6, label: '0.6' },
              { value: 0.85, label: '0.85' },
              { value: 1.0, label: '1.0' },
            ]}
            valueLabelDisplay="auto"
            valueLabelFormat={(v) => `${Math.round(v * 100)}%`}
            size="small"
            aria-label="Confidence threshold"
          />
          <Box display="flex" justifyContent="space-between">
            <Chip label="Operator Queue" size="small" variant="outlined" color="warning" sx={{ fontSize: '0.65rem' }} />
            <Chip label="Auto-Execute" size="small" variant="outlined" color="success" sx={{ fontSize: '0.65rem' }} />
          </Box>
        </Box>

        {/* AI Model */}
        <FormControl fullWidth size="small">
          <InputLabel>AI Model</InputLabel>
          <Select
            value={model}
            label="AI Model"
            onChange={(e) => {
              setModel(e.target.value);
              handleChange({ model: e.target.value });
            }}
          >
            {AI_MODELS.map((m) => (
              <MenuItem key={m.value} value={m.value}>
                {m.label}
              </MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>
    </Paper>
  );
}
