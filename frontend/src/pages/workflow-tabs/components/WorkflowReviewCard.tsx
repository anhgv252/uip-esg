import { Card, CardContent, Typography, Chip, Box, Button, Stack } from '@mui/material';
import {
  CheckCircle as ApproveIcon,
  Cancel as RejectIcon,
  AccessTime as TimeIcon,
} from '@mui/icons-material';
import type { WorkflowReview } from '@/hooks/useOperatorReview';

interface WorkflowReviewCardProps {
  review: WorkflowReview;
  isSelected: boolean;
  onSelect: () => void;
  onApprove: () => void;
  onReject: () => void;
}

const INTENT_LABELS: Record<string, string> = {
  flood_response: 'Flood Response',
  aqi_alert: 'AQI Alert',
  emergency_evacuation: 'Emergency Evacuation',
  citizen_notification: 'Citizen Notification',
  energy_optimization: 'Energy Optimization',
  esg_report: 'ESG Report',
};

const STATUS_COLORS = {
  PENDING: 'warning',
  APPROVED: 'success',
  REJECTED: 'error',
  EXECUTED: 'info',
} as const;

export default function WorkflowReviewCard({
  review,
  isSelected,
  onSelect,
  onApprove,
  onReject,
}: WorkflowReviewCardProps) {
  const confidenceColor =
    review.confidence >= 0.85 ? 'success' : review.confidence >= 0.7 ? 'warning' : 'error';

  const timeAgo = new Date(review.createdAt).toLocaleString('en-US', {
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  });

  return (
    <Card
      variant={isSelected ? 'elevation' : 'outlined'}
      elevation={isSelected ? 4 : 0}
      sx={{
        cursor: 'pointer',
        borderColor: isSelected ? 'primary.main' : undefined,
        borderWidth: isSelected ? 2 : 1,
        mb: 1.5,
        '&:hover': {
          backgroundColor: 'action.hover',
        },
      }}
      onClick={onSelect}
    >
      <CardContent>
        <Box display="flex" justifyContent="space-between" alignItems="start" mb={1}>
          <Chip
            label={INTENT_LABELS[review.intent] ?? review.intent}
            size="small"
            color="primary"
            variant="outlined"
          />
          <Chip
            label={review.status}
            size="small"
            color={STATUS_COLORS[review.status]}
          />
        </Box>

        <Typography variant="body2" fontWeight={600} gutterBottom>
          {review.zone}
        </Typography>

        <Box display="flex" alignItems="center" gap={0.5} mb={1}>
          <TimeIcon fontSize="small" sx={{ color: 'text.secondary', fontSize: 16 }} />
          <Typography variant="caption" color="text.secondary">
            {timeAgo} • by {review.submittedBy}
          </Typography>
        </Box>

        <Box display="flex" alignItems="center" gap={1} mb={1.5}>
          <Typography variant="caption" color="text.secondary">
            Confidence:
          </Typography>
          <Chip
            label={`${(review.confidence * 100).toFixed(0)}%`}
            size="small"
            color={confidenceColor}
            sx={{ height: 20, fontSize: '0.7rem' }}
          />
        </Box>

        {review.status === 'PENDING' && (
          <Stack direction="row" spacing={1}>
            <Button
              size="small"
              variant="contained"
              color="success"
              startIcon={<ApproveIcon />}
              onClick={(e) => {
                e.stopPropagation();
                onApprove();
              }}
              sx={{ flex: 1 }}
            >
              Approve
            </Button>
            <Button
              size="small"
              variant="outlined"
              color="error"
              startIcon={<RejectIcon />}
              onClick={(e) => {
                e.stopPropagation();
                onReject();
              }}
              sx={{ flex: 1 }}
            >
              Reject
            </Button>
          </Stack>
        )}
      </CardContent>
    </Card>
  );
}
