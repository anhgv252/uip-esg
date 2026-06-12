import { useState } from 'react';
import {
  Box,
  Collapse,
  IconButton,
  TextField,
  Button,
  Typography,
  Stack,
} from '@mui/material';
import ThumbUpIcon from '@mui/icons-material/ThumbUp';
import ThumbDownIcon from '@mui/icons-material/ThumbDown';

interface AlertFeedbackButtonProps {
  /** ID of the alert to provide feedback on. */
  alertId: string;
  /** Called when the operator submits their feedback. */
  onSubmit: (correct: boolean, comment?: string) => void;
}

/**
 * Operator feedback widget — "Quyết định AI này có đúng không?"
 * Shows thumbs up / thumbs down, with an optional comment field.
 */
export default function AlertFeedbackButton({ alertId: _alertId, onSubmit }: AlertFeedbackButtonProps) {
  const [selected, setSelected] = useState<boolean | null>(null);
  const [comment, setComment] = useState('');
  const [submitted, setSubmitted] = useState(false);

  const handleThumb = (correct: boolean) => {
    setSelected((prev) => (prev === correct ? null : correct));
  };

  const handleSubmit = () => {
    if (selected === null) return;
    onSubmit(selected, comment.trim() || undefined);
    setSubmitted(true);
  };

  if (submitted) {
    return (
      <Box
        sx={{
          p: 1.5,
          borderRadius: 1,
          bgcolor: 'action.hover',
          display: 'flex',
          alignItems: 'center',
          gap: 1,
        }}
      >
        {selected ? (
          <ThumbUpIcon fontSize="small" color="success" />
        ) : (
          <ThumbDownIcon fontSize="small" color="error" />
        )}
        <Typography variant="body2" color="text.secondary">
          Cảm ơn phản hồi của bạn.
        </Typography>
      </Box>
    );
  }

  return (
    <Box>
      <Typography variant="caption" color="text.secondary" display="block" mb={0.75}>
        Quyết định AI này có đúng không?
      </Typography>

      <Stack direction="row" spacing={1} alignItems="center">
        <IconButton
          size="small"
          onClick={() => handleThumb(true)}
          aria-label="Quyết định AI đúng"
          sx={{
            color: selected === true ? 'success.main' : 'text.disabled',
            border: 1,
            borderColor: selected === true ? 'success.main' : 'divider',
            borderRadius: 1,
            transition: 'color 0.15s, border-color 0.15s',
          }}
        >
          <ThumbUpIcon fontSize="small" />
        </IconButton>

        <IconButton
          size="small"
          onClick={() => handleThumb(false)}
          aria-label="Quyết định AI không đúng"
          sx={{
            color: selected === false ? 'error.main' : 'text.disabled',
            border: 1,
            borderColor: selected === false ? 'error.main' : 'divider',
            borderRadius: 1,
            transition: 'color 0.15s, border-color 0.15s',
          }}
        >
          <ThumbDownIcon fontSize="small" />
        </IconButton>
      </Stack>

      <Collapse in={selected !== null} unmountOnExit>
        <Box mt={1.5} display="flex" flexDirection="column" gap={1}>
          <TextField
            size="small"
            fullWidth
            multiline
            rows={2}
            label="Nhận xét (không bắt buộc)"
            value={comment}
            onChange={(e) => setComment(e.target.value)}
            inputProps={{ 'aria-label': 'Nhận xét phản hồi' }}
          />
          <Button
            variant="outlined"
            size="small"
            onClick={handleSubmit}
            disabled={selected === null}
            sx={{ alignSelf: 'flex-end' }}
          >
            Gửi phản hồi
          </Button>
        </Box>
      </Collapse>
    </Box>
  );
}
