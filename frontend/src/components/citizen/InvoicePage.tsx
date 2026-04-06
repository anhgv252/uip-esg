import { useState } from 'react'
import {
  Box, Typography, Drawer, IconButton, Grid, Chip, Divider,
  Table, TableBody, TableCell, TableContainer, TableHead, TableRow,
  Paper, MenuItem, TextField, CircularProgress, Alert,
} from '@mui/material'
import CloseIcon from '@mui/icons-material/Close'
import { format } from 'date-fns'
import { useInvoices, useInvoiceDetail } from '@/hooks/useCitizenData'

const STATUS_COLORS = {
  UNPAID: 'warning',
  PAID: 'success',
  OVERDUE: 'error',
} as const

function InvoiceDetailDrawer({ id, onClose }: { id: string | null; onClose: () => void }) {
  const { data, isLoading } = useInvoiceDetail(id)

  return (
    <Drawer anchor="right" open={!!id} onClose={onClose} PaperProps={{ sx: { width: 380 } }}>
      <Box sx={{ p: 3 }}>
        <Box display="flex" justifyContent="space-between" alignItems="center" mb={2}>
          <Typography variant="h6">Invoice Detail</Typography>
          <IconButton onClick={onClose}><CloseIcon /></IconButton>
        </Box>

        {isLoading && <CircularProgress />}
        {data && (
          <>
            <Box display="flex" gap={1} mb={2}>
              <Chip label={data.meterType} size="small" />
              <Chip
                label={data.status}
                size="small"
                color={STATUS_COLORS[data.status] ?? 'default'}
              />
            </Box>
            <Divider sx={{ mb: 2 }} />
            <Grid container spacing={1}>
              {([
                ['Period', `${String(data.billingMonth).padStart(2, '0')}/${data.billingYear}`],
                ['Units consumed', `${data.unitsConsumed ?? '—'}`],
                ['Unit price', `${data.unitPrice ?? '—'} VND`],
                ['Total amount', `${Number(data.amount).toLocaleString('vi-VN')} VND`],
                ['Issued', data.issuedAt ? format(new Date(data.issuedAt), 'dd/MM/yyyy') : '—'],
                ['Paid', data.paidAt ? format(new Date(data.paidAt), 'dd/MM/yyyy') : 'Not yet paid'],
              ] as [string, string][]).map(([label, value]) => (
                <Grid item xs={12} key={label}>
                  <Box display="flex" gap={1}>
                    <Typography variant="body2" color="text.secondary" sx={{ minWidth: 130 }}>{label}:</Typography>
                    <Typography variant="body2" fontWeight={label === 'Total amount' ? 600 : 400}>{value}</Typography>
                  </Box>
                </Grid>
              ))}
            </Grid>
          </>
        )}
      </Box>
    </Drawer>
  )
}

export default function InvoicePage() {
  const currentYear = new Date().getFullYear()
  const currentMonth = new Date().getMonth() + 1
  const [year, setYear] = useState(currentYear)
  const [month, setMonth] = useState(currentMonth)
  const [detailId, setDetailId] = useState<string | null>(null)

  const { data: invoicePage, isLoading, error } = useInvoices({ year, month })
  const invoices = invoicePage?.content ?? []

  return (
    <Box>
      <Typography variant="h6" fontWeight={600} gutterBottom>My Invoices</Typography>

      <Box display="flex" gap={2} mb={2}>
        <TextField select size="small" label="Year" value={year}
          onChange={(e) => setYear(Number(e.target.value))} sx={{ minWidth: 100 }}>
          {[currentYear, currentYear - 1].map((y) => <MenuItem key={y} value={y}>{y}</MenuItem>)}
        </TextField>
        <TextField select size="small" label="Month" value={month}
          onChange={(e) => setMonth(Number(e.target.value))} sx={{ minWidth: 120 }}>
          {Array.from({ length: 12 }, (_, i) => i + 1).map((m) => (
            <MenuItem key={m} value={m}>{String(m).padStart(2, '0')}</MenuItem>
          ))}
        </TextField>
      </Box>

      {error && <Alert severity="error" sx={{ mb: 2 }}>Failed to load invoices.</Alert>}

      <TableContainer component={Paper} variant="outlined">
        <Table size="small">
          <TableHead>
            <TableRow>
              <TableCell>Period</TableCell>
              <TableCell>Type</TableCell>
              <TableCell>Units</TableCell>
              <TableCell>Amount (VND)</TableCell>
              <TableCell>Status</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {isLoading && (
              <TableRow><TableCell colSpan={5} align="center"><CircularProgress size={20} /></TableCell></TableRow>
            )}
            {!isLoading && invoices.length === 0 && (
              <TableRow>
                <TableCell colSpan={5} align="center">
                  <Typography color="text.secondary" variant="body2">No invoices for this period</Typography>
                </TableCell>
              </TableRow>
            )}
            {invoices.map((inv) => (
              <TableRow
                key={inv.id} hover sx={{ cursor: 'pointer' }}
                onClick={() => setDetailId(inv.id)}
              >
                <TableCell>{String(inv.billingMonth).padStart(2, '0')}/{inv.billingYear}</TableCell>
                <TableCell><Chip label={inv.meterType} size="small" /></TableCell>
                <TableCell>{inv.unitsConsumed ?? '—'}</TableCell>
                <TableCell>{Number(inv.amount).toLocaleString('vi-VN')}</TableCell>
                <TableCell>
                  <Chip
                    label={inv.status} size="small" variant="outlined"
                    color={STATUS_COLORS[inv.status] ?? 'default'}
                  />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </TableContainer>

      <InvoiceDetailDrawer id={detailId} onClose={() => setDetailId(null)} />
    </Box>
  )
}
