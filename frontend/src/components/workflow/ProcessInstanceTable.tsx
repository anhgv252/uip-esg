import {
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
  CircularProgress,
  Tooltip,
  IconButton,
} from '@mui/material';
import OpenInNewIcon from '@mui/icons-material/OpenInNew';
import { format, formatDistanceToNow } from 'date-fns';
import type { ProcessInstance } from '@/api/workflow';

interface Props {
  instances: ProcessInstance[];
  totalElements: number;
  page: number;
  onPageChange: (page: number) => void;
  isLoading: boolean;
  onRowClick: (instance: ProcessInstance) => void;
}

const STATE_CHIP: Record<string, { label: string; color: 'success' | 'warning' | 'error' | 'default' }> = {
  ACTIVE: { label: 'Active', color: 'warning' },
  COMPLETED: { label: 'Completed', color: 'success' },
  EXTERNALLY_TERMINATED: { label: 'Terminated', color: 'error' },
};

function StateChip({ state }: { state: string }) {
  const cfg = STATE_CHIP[state] ?? { label: state, color: 'default' };
  return <Chip label={cfg.label} color={cfg.color} size="small" />;
}

export default function ProcessInstanceTable({
  instances,
  totalElements,
  page,
  onPageChange,
  isLoading,
  onRowClick,
}: Props) {
  return (
    <>
      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Instance ID</TableCell>
              <TableCell>Process</TableCell>
              <TableCell>Business Key</TableCell>
              <TableCell>State</TableCell>
              <TableCell>Started</TableCell>
              <TableCell align="center">Detail</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                  <CircularProgress size={24} />
                </TableCell>
              </TableRow>
            )}
            {!isLoading && instances.length === 0 && (
              <TableRow>
                <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                  <Typography color="text.secondary" variant="body2">
                    No instances found
                  </Typography>
                </TableCell>
              </TableRow>
            )}
            {instances.map((inst) => (
              <TableRow
                key={inst.id}
                hover
                sx={{ cursor: 'pointer' }}
                onClick={() => onRowClick(inst)}
              >
                <TableCell>
                  <Typography variant="caption" fontFamily="monospace" noWrap sx={{ maxWidth: 160, display: 'block' }}>
                    {inst.id}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2" noWrap sx={{ maxWidth: 200 }}>
                    {inst.processDefinitionKey}
                  </Typography>
                </TableCell>
                <TableCell>
                  <Typography variant="body2" color="text.secondary" noWrap sx={{ maxWidth: 180 }}>
                    {inst.businessKey ?? '—'}
                  </Typography>
                </TableCell>
                <TableCell>
                  <StateChip state={inst.state} />
                </TableCell>
                <TableCell>
                  <Tooltip title={format(new Date(inst.startTime), 'dd/MM/yyyy HH:mm:ss')}>
                    <Typography variant="caption" color="text.secondary">
                      {formatDistanceToNow(new Date(inst.startTime), { addSuffix: true })}
                    </Typography>
                  </Tooltip>
                </TableCell>
                <TableCell align="center" onClick={(e) => e.stopPropagation()}>
                  <Tooltip title="View variables">
                    <IconButton size="small" onClick={() => onRowClick(inst)}>
                      <OpenInNewIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>
      <TablePagination
        component="div"
        count={totalElements}
        page={page}
        onPageChange={(_, p) => onPageChange(p)}
        rowsPerPage={20}
        rowsPerPageOptions={[20]}
      />
    </>
  );
}
