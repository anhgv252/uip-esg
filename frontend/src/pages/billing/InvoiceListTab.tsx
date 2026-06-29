import { useState } from 'react';
import {
  Box,
  Button,
  Paper,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Chip,
  IconButton,
  Typography,
  Dialog,
  DialogTitle,
  DialogContent,
  DialogActions,
  TextField,
  MenuItem,
  CircularProgress,
  Alert,
  Pagination,
} from '@mui/material';
import {
  PictureAsPdf as PdfIcon,
  CheckCircle as CheckIcon,
  Add as AddIcon,
} from '@mui/icons-material';
import { useAuth } from '@/hooks/useAuth';
import { useInvoices, useGenerateInvoice, useMarkInvoicePaid } from '@/hooks/useBillingUsage';
import type { InvoiceStatus } from '@/types/billing';

const STATUS_COLORS: Record<InvoiceStatus, 'info' | 'secondary' | 'success' | 'warning'> = {
  GENERATED: 'info',
  SENT: 'secondary',
  PAID: 'success',
  DISPUTED: 'warning',
};

const STATUS_LABELS: Record<InvoiceStatus, string> = {
  GENERATED: 'Generated',
  SENT: 'Sent',
  PAID: 'Paid',
  DISPUTED: 'Disputed',
};

export default function InvoiceListTab() {
  const { user } = useAuth();
  const isAdmin = user?.role === 'ROLE_ADMIN' || user?.role === 'ROLE_TENANT_ADMIN';

  const [page, setPage] = useState(1);
  const [statusFilter, setStatusFilter] = useState<InvoiceStatus | ''>('');
  const [generateDialogOpen, setGenerateDialogOpen] = useState(false);
  const [selectedMonth, setSelectedMonth] = useState('');
  const [selectedYear, setSelectedYear] = useState(new Date().getFullYear());

  const { data: invoiceData, isLoading, error } = useInvoices(page, statusFilter || undefined);
  const generateMutation = useGenerateInvoice();
  const markPaidMutation = useMarkInvoicePaid();

  const handleGenerateInvoice = () => {
    if (!selectedMonth) return;
    const month = `${selectedYear}-${selectedMonth.padStart(2, '0')}`;
    generateMutation.mutate(
      { month, year: selectedYear },
      {
        onSuccess: () => {
          setGenerateDialogOpen(false);
          setSelectedMonth('');
        },
      }
    );
  };

  const handleMarkPaid = (invoiceId: string) => {
    if (confirm('Mark this invoice as paid?')) {
      markPaidMutation.mutate(invoiceId);
    }
  };

  const handleViewPdf = (pdfUrl?: string) => {
    if (pdfUrl) {
      window.open(pdfUrl, '_blank');
    }
  };

  return (
    <Box>
      {/* Header Actions */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <TextField
          select
          size="small"
          label="Filter by Status"
          value={statusFilter}
          onChange={(e) => setStatusFilter(e.target.value as InvoiceStatus | '')}
          sx={{ minWidth: 180 }}
        >
          <MenuItem value="">All Statuses</MenuItem>
          <MenuItem value="GENERATED">Generated</MenuItem>
          <MenuItem value="SENT">Sent</MenuItem>
          <MenuItem value="PAID">Paid</MenuItem>
          <MenuItem value="DISPUTED">Disputed</MenuItem>
        </TextField>

        {isAdmin && (
          <Button
            variant="contained"
            startIcon={<AddIcon />}
            onClick={() => setGenerateDialogOpen(true)}
          >
            Generate Invoice
          </Button>
        )}
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load invoices
        </Alert>
      )}

      {isLoading && (
        <Box display="flex" justifyContent="center" py={6}>
          <CircularProgress />
        </Box>
      )}

      {!isLoading && invoiceData && (
        <>
          <TableContainer component={Paper} variant="outlined">
            <Table>
              <TableHead>
                <TableRow>
                  <TableCell>Invoice #</TableCell>
                  <TableCell>Period</TableCell>
                  <TableCell align="right">Total (VND)</TableCell>
                  <TableCell>Status</TableCell>
                  <TableCell>Generated At</TableCell>
                  <TableCell align="right">Actions</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {invoiceData.invoices.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={6} align="center" sx={{ py: 4 }}>
                      <Typography color="text.secondary">No invoices found</Typography>
                    </TableCell>
                  </TableRow>
                ) : (
                  invoiceData.invoices.map((invoice) => (
                    <TableRow key={invoice.id} hover>
                      <TableCell>
                        <Typography variant="body2" fontWeight={600}>
                          {invoice.invoiceNumber}
                        </Typography>
                      </TableCell>
                      <TableCell>{invoice.periodMonth}</TableCell>
                      <TableCell align="right">
                        {invoice.totalAmountVnd.toLocaleString()}
                      </TableCell>
                      <TableCell>
                        <Chip
                          label={STATUS_LABELS[invoice.status]}
                          color={STATUS_COLORS[invoice.status]}
                          size="small"
                        />
                      </TableCell>
                      <TableCell>
                        {new Date(invoice.generatedAt).toLocaleDateString()}
                      </TableCell>
                      <TableCell align="right">
                        <IconButton
                          size="small"
                          onClick={() => handleViewPdf(invoice.pdfUrl)}
                          disabled={!invoice.pdfUrl}
                          title="View PDF"
                        >
                          <PdfIcon fontSize="small" />
                        </IconButton>
                        {isAdmin && invoice.status !== 'PAID' && (
                          <IconButton
                            size="small"
                            onClick={() => handleMarkPaid(invoice.id)}
                            disabled={markPaidMutation.isPending}
                            title="Mark as Paid"
                            sx={{ ml: 1 }}
                          >
                            <CheckIcon fontSize="small" />
                          </IconButton>
                        )}
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </TableContainer>

          {invoiceData.totalPages > 1 && (
            <Box display="flex" justifyContent="center" mt={3}>
              <Pagination
                count={invoiceData.totalPages}
                page={page}
                onChange={(_, value) => setPage(value)}
                color="primary"
              />
            </Box>
          )}
        </>
      )}

      {/* Generate Invoice Dialog */}
      <Dialog open={generateDialogOpen} onClose={() => setGenerateDialogOpen(false)} maxWidth="xs" fullWidth>
        <DialogTitle>Generate Invoice</DialogTitle>
        <DialogContent>
          <Box display="flex" flexDirection="column" gap={2} pt={1}>
            <TextField
              select
              fullWidth
              label="Month"
              value={selectedMonth}
              onChange={(e) => setSelectedMonth(e.target.value)}
            >
              {Array.from({ length: 12 }, (_, i) => (
                <MenuItem key={i + 1} value={String(i + 1)}>
                  {new Date(2024, i).toLocaleString('default', { month: 'long' })}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              fullWidth
              label="Year"
              type="number"
              value={selectedYear}
              onChange={(e) => setSelectedYear(Number(e.target.value))}
            />
          </Box>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setGenerateDialogOpen(false)}>Cancel</Button>
          <Button
            onClick={handleGenerateInvoice}
            variant="contained"
            disabled={!selectedMonth || generateMutation.isPending}
          >
            {generateMutation.isPending ? 'Generating...' : 'Generate'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}
