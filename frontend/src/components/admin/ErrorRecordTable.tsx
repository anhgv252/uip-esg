import {
  Box,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  TablePagination,
  Paper,
  Chip,
  Typography,
  Skeleton,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Stack,
  Tooltip,
  IconButton,
} from '@mui/material';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutline';
import ReplayIcon from '@mui/icons-material/Replay';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { ErrorRecord, resolveError, reingestError } from '../../api/errors';
import { formatDistanceToNow } from 'date-fns';

interface ErrorRecordTableProps {
  records: ErrorRecord[];
  total: number;
  loading?: boolean;
  page: number;
  rowsPerPage: number;
  onPageChange: (page: number) => void;
  onRowsPerPageChange: (rows: number) => void;
  statusFilter: string;
  onStatusFilterChange: (status: string) => void;
  moduleFilter: string;
  onModuleFilterChange: (module: string) => void;
  modules: string[];
}

const STATUS_COLORS: Record<string, 'warning' | 'success' | 'info'> = {
  UNRESOLVED: 'warning',
  RESOLVED: 'success',
  REINGESTED: 'info',
};

export function ErrorRecordTable({
  records,
  total,
  loading,
  page,
  rowsPerPage,
  onPageChange,
  onRowsPerPageChange,
  statusFilter,
  onStatusFilterChange,
  moduleFilter,
  onModuleFilterChange,
  modules,
}: ErrorRecordTableProps) {
  const qc = useQueryClient();

  const resolveMutation = useMutation({
    mutationFn: (id: number) => resolveError(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['error-records'] }),
  });

  const reingestMutation = useMutation({
    mutationFn: (id: number) => reingestError(id),
    onSuccess: () => qc.invalidateQueries({ queryKey: ['error-records'] }),
  });

  return (
    <Box>
      {/* Filters */}
      <Stack direction="row" spacing={2} mb={2}>
        <FormControl size="small" sx={{ minWidth: 120 }}>
          <InputLabel>Status</InputLabel>
          <Select
            value={statusFilter}
            label="Status"
            onChange={(e) => onStatusFilterChange(e.target.value)}
          >
            <MenuItem value="">All</MenuItem>
            <MenuItem value="UNRESOLVED">Unresolved</MenuItem>
            <MenuItem value="RESOLVED">Resolved</MenuItem>
            <MenuItem value="REINGESTED">Reingested</MenuItem>
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 140 }}>
          <InputLabel>Module</InputLabel>
          <Select
            value={moduleFilter}
            label="Module"
            onChange={(e) => onModuleFilterChange(e.target.value)}
          >
            <MenuItem value="">All</MenuItem>
            {modules.map((m) => (
              <MenuItem key={m} value={m}>{m}</MenuItem>
            ))}
          </Select>
        </FormControl>
      </Stack>

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell><strong>ID</strong></TableCell>
              <TableCell><strong>Module</strong></TableCell>
              <TableCell><strong>Topic / Offset</strong></TableCell>
              <TableCell><strong>Error Type</strong></TableCell>
              <TableCell><strong>Status</strong></TableCell>
              <TableCell><strong>Occurred</strong></TableCell>
              <TableCell align="center"><strong>Actions</strong></TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {loading
              ? [...Array(5)].map((_, i) => (
                  <TableRow key={i}>
                    {[...Array(7)].map((__, j) => (
                      <TableCell key={j}><Skeleton /></TableCell>
                    ))}
                  </TableRow>
                ))
              : records.map((rec) => (
                  <TableRow key={rec.id} hover>
                    <TableCell>{rec.id}</TableCell>
                    <TableCell>
                      <Typography variant="body2" fontWeight={500}>{rec.sourceModule}</Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption">{rec.kafkaTopic}</Typography>
                      <br />
                      <Typography variant="caption" color="text.secondary">offset: {rec.kafkaOffset}</Typography>
                    </TableCell>
                    <TableCell>
                      <Tooltip title={rec.errorMessage}>
                        <Typography variant="body2" noWrap sx={{ maxWidth: 180 }}>
                          {rec.errorType}
                        </Typography>
                      </Tooltip>
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={rec.status}
                        color={STATUS_COLORS[rec.status] ?? 'default'}
                        size="small"
                        variant="outlined"
                      />
                    </TableCell>
                    <TableCell>
                      <Typography variant="caption" color="text.secondary">
                        {formatDistanceToNow(new Date(rec.occurredAt), { addSuffix: true })}
                      </Typography>
                    </TableCell>
                    <TableCell align="center">
                      <Stack direction="row" justifyContent="center">
                        {rec.status === 'UNRESOLVED' && (
                          <>
                            <Tooltip title="Mark Resolved">
                              <IconButton
                                size="small"
                                color="success"
                                onClick={() => resolveMutation.mutate(rec.id)}
                                disabled={resolveMutation.isPending}
                              >
                                <CheckCircleOutlineIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                            <Tooltip title="Reingest to Kafka">
                              <IconButton
                                size="small"
                                color="info"
                                onClick={() => reingestMutation.mutate(rec.id)}
                                disabled={reingestMutation.isPending}
                              >
                                <ReplayIcon fontSize="small" />
                              </IconButton>
                            </Tooltip>
                          </>
                        )}
                      </Stack>
                    </TableCell>
                  </TableRow>
                ))}
            {!loading && !records.length && (
              <TableRow>
                <TableCell colSpan={7} align="center">
                  <Typography color="text.secondary" py={2}>No error records found</Typography>
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </TableContainer>

      <TablePagination
        component="div"
        count={total}
        page={page}
        rowsPerPage={rowsPerPage}
        onPageChange={(_, p) => onPageChange(p)}
        onRowsPerPageChange={(e) => onRowsPerPageChange(Number(e.target.value))}
        rowsPerPageOptions={[10, 25, 50]}
      />
    </Box>
  );
}

export default ErrorRecordTable;
