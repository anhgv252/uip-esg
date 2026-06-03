import {
  Box,
  Chip,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from '@mui/material'
import { format } from 'date-fns'
import type { AlertEvent } from '@/api/alerts'

interface SafetyAlertHistoryProps {
  alerts: AlertEvent[]
  loading?: boolean
}

const SEVERITY_COLORS: Record<string, 'error' | 'warning' | 'default'> = {
  CRITICAL: 'error',
  HIGH: 'warning',
  MEDIUM: 'default',
  LOW: 'default',
}

const STATUS_LABELS: Record<string, string> = {
  OPEN: 'Mở',
  ACKNOWLEDGED: 'Đã xác nhận',
  ESCALATED: 'Đã leo thang',
  RESOLVED: 'Đã giải quyết',
}

const MEASURE_LABELS: Record<string, string> = {
  VIBRATION: 'Rung động',
  TILT: 'Nghiêng',
  CRACK: 'Vết nứt',
}

function SkeletonRows() {
  return (
    <>
      {[0, 1, 2].map(i => (
        <TableRow key={i}>
          <TableCell><Skeleton variant="text" width={120} /></TableCell>
          <TableCell><Skeleton variant="rounded" width={70} height={24} /></TableCell>
          <TableCell><Skeleton variant="text" width={80} /></TableCell>
          <TableCell><Skeleton variant="text" width={60} /></TableCell>
          <TableCell><Skeleton variant="rounded" width={80} height={24} /></TableCell>
        </TableRow>
      ))}
    </>
  )
}

export function SafetyAlertHistory({ alerts, loading = false }: SafetyAlertHistoryProps) {
  return (
    <Box>
      <Typography variant="subtitle2" fontWeight={600} mb={1}>
        Lịch sử cảnh báo kết cấu
      </Typography>

      {!loading && alerts.length === 0 ? (
        <Typography variant="body2" color="text.secondary" sx={{ py: 2, textAlign: 'center' }}>
          Không có cảnh báo kết cấu nào gần đây
        </Typography>
      ) : (
        <Box sx={{ overflowX: 'auto' }}>
          <Table size="small" aria-label="Lịch sử cảnh báo kết cấu">
            <TableHead>
              <TableRow>
                <TableCell>Thời gian</TableCell>
                <TableCell>Mức độ</TableCell>
                <TableCell>Loại</TableCell>
                <TableCell>Giá trị</TableCell>
                <TableCell>Trạng thái</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {loading ? (
                <SkeletonRows />
              ) : (
                alerts.map(alert => (
                  <TableRow key={alert.id} hover>
                    <TableCell sx={{ whiteSpace: 'nowrap' }}>
                      {format(new Date(alert.detectedAt), 'dd/MM HH:mm')}
                    </TableCell>
                    <TableCell>
                      <Chip
                        label={alert.severity}
                        color={SEVERITY_COLORS[alert.severity] ?? 'default'}
                        size="small"
                      />
                    </TableCell>
                    <TableCell>
                      {MEASURE_LABELS[alert.measureType] ?? alert.measureType}
                    </TableCell>
                    <TableCell>{alert.value}</TableCell>
                    <TableCell>
                      <Typography variant="caption">
                        {STATUS_LABELS[alert.status] ?? alert.status}
                      </Typography>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>
        </Box>
      )}
    </Box>
  )
}
