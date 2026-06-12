/**
 * OfflineStatusBanner — displays offline status, pending mutation count,
 * and sync progress. Placed in MobileLayout.
 */
import { Box, Chip, IconButton, Typography, Collapse, Tooltip, CircularProgress } from '@mui/material';
import SyncIcon from '@mui/icons-material/Sync';
import CloudOffIcon from '@mui/icons-material/CloudOff';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import ErrorIcon from '@mui/icons-material/Error';
import type { OfflineQueueStatus } from '@/hooks/useOfflineQueue';

interface OfflineStatusBannerProps {
  status: OfflineQueueStatus & { syncNow: () => Promise<void> };
}

export function OfflineStatusBanner({ status }: OfflineStatusBannerProps) {
  const { isOnline, pendingCount, isSyncing, lastSyncError, syncNow } = status;

  return (
    <Collapse in={!isOnline || pendingCount > 0}>
      <Box
        display="flex"
        alignItems="center"
        gap={1}
        px={2}
        py={0.5}
        sx={{
          bgcolor: isOnline ? 'action.hover' : 'warning.main',
          color: isOnline ? 'text.primary' : 'warning.contrastText',
          minHeight: 36,
        }}
        role="status"
        aria-live="polite"
        aria-label={isOnline ? 'Online status' : 'Offline status'}
      >
        {!isOnline ? (
          <CloudOffIcon fontSize="small" />
        ) : pendingCount > 0 ? (
          <SyncIcon fontSize="small" />
        ) : (
          <CheckCircleIcon fontSize="small" />
        )}

        <Typography variant="caption" fontWeight={600} flex={1}>
          {!isOnline
            ? `Offline — ${pendingCount} pending change${pendingCount !== 1 ? 's' : ''} queued`
            : isSyncing
              ? 'Syncing pending changes...'
              : lastSyncError
                ? `Sync error: ${lastSyncError}`
                : pendingCount > 0
                  ? `${pendingCount} pending change${pendingCount !== 1 ? 's' : ''} to sync`
                  : ''}
        </Typography>

        {pendingCount > 0 && isOnline && !isSyncing && (
          <Tooltip title="Sync now">
            <IconButton
              size="small"
              onClick={() => void syncNow()}
              aria-label="Sync pending changes now"
              sx={{ color: 'inherit' }}
            >
              <SyncIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        )}

        {isSyncing && (
          <CircularProgress size={16} sx={{ color: 'inherit' }} />
        )}

        {lastSyncError && (
          <Tooltip title={lastSyncError}>
            <ErrorIcon fontSize="small" sx={{ color: 'error.main' }} />
          </Tooltip>
        )}

        {pendingCount > 0 && (
          <Chip
            label={pendingCount}
            size="small"
            sx={{
              height: 20,
              fontSize: '0.65rem',
              fontWeight: 700,
              bgcolor: isOnline ? 'primary.main' : 'warning.dark',
              color: '#fff',
            }}
          />
        )}
      </Box>
    </Collapse>
  );
}
