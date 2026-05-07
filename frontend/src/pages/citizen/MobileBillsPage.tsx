import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import {
  Box, Card, CardContent, Typography, Chip, Collapse, IconButton, Skeleton,
  Button,
} from '@mui/material'
import ExpandMoreIcon from '@mui/icons-material/ExpandMore'
import RefreshIcon from '@mui/icons-material/Refresh'
import { useInvoices } from '@/hooks/useCitizenData'

const STATUS_CONFIG: Record<string, { label: string; color: 'success' | 'warning' | 'error' }> = {
  PAID: { label: 'Paid', color: 'success' },
  UNPAID: { label: 'Pending', color: 'warning' },
  OVERDUE: { label: 'Overdue', color: 'error' },
}

type InvoiceItem = ReturnType<typeof useInvoices>['data'] extends infer P
  ? P extends { content: (infer I)[] }
    ? I
    : never
  : never

export default function MobileBillsPage() {
  const now = new Date()
  const [month, setMonth] = useState(now.getMonth() + 1)
  const [year, setYear] = useState(now.getFullYear())
  const { data: invoicePage, isLoading, refetch, isFetching } = useInvoices({ month, year })
  const { data: allInvoicePage } = useInvoices()

  const monthInvoices = invoicePage?.content ?? []
  const fallbackInvoices = allInvoicePage?.content ?? []
  const invoices = monthInvoices.length > 0 ? monthInvoices : fallbackInvoices

  const prevMonth = () => {
    if (month === 1) { setMonth(12); setYear(year - 1) }
    else setMonth(month - 1)
  }

  const nextMonth = () => {
    const now2 = new Date()
    if (year === now2.getFullYear() && month >= now2.getMonth() + 1) return
    if (month === 12) { setMonth(1); setYear(year + 1) }
    else setMonth(month + 1)
  }

  const monthLabel = new Date(year, month - 1).toLocaleDateString('en-US', { month: 'long', year: 'numeric' })

  return (
    <Box sx={{ p: 2 }}>
      {/* Header with month navigation */}
      <Box display="flex" alignItems="center" justifyContent="space-between" mb={2}>
        <Button size="small" onClick={prevMonth}>&lt; Prev</Button>
        <Typography variant="h6" fontWeight={600}>{monthLabel}</Typography>
        <Button size="small" onClick={nextMonth} disabled={year === new Date().getFullYear() && month >= new Date().getMonth() + 1}>Next &gt;</Button>
      </Box>

      <Box display="flex" justifyContent="flex-end" mb={1}>
        <Button size="small" startIcon={<RefreshIcon />} onClick={() => refetch()} disabled={isFetching}>
          Refresh
        </Button>
      </Box>

      {isLoading ? (
        Array.from({ length: 3 }).map((_, i) => (
          <Card key={i} sx={{ mb: 1.5 }}>
            <CardContent>
              <Skeleton height={24} width="60%" />
              <Skeleton height={20} width="40%" />
            </CardContent>
          </Card>
        ))
      ) : invoices.length === 0 ? (
        <Card data-testid="bill-card" sx={{ mb: 1.5 }}>
          <CardContent>
            <Typography variant="body2" color="text.secondary" textAlign="center">
              No bills available.
            </Typography>
          </CardContent>
        </Card>
      ) : (
        invoices.map((inv) => <BillCard key={inv.id} invoice={inv} />)
      )}
    </Box>
  )
}

function BillCard({ invoice }: { invoice: InvoiceItem }) {
  const navigate = useNavigate()
  const [expanded, setExpanded] = useState(false)
  const statusCfg = STATUS_CONFIG[invoice.status] ?? STATUS_CONFIG.UNPAID

  return (
    <Card
      data-testid="bill-card"
      sx={{ mb: 1.5, cursor: 'pointer' }}
      onClick={() => navigate(`/citizen/bills/${invoice.id}`, { state: { invoice } })}
    >
      <CardContent sx={{ '&:last-child': { pb: 1.5 } }}>
        <Box display="flex" justifyContent="space-between" alignItems="center">
          <Box>
            <Typography variant="subtitle2" fontWeight={600}>
              {invoice.meterType === 'ELECTRICITY' ? 'Electricity' : 'Water'} Bill
            </Typography>
            <Typography variant="caption" color="text.secondary">
              {new Date(invoice.issuedAt).toLocaleDateString('vi-VN')}
            </Typography>
          </Box>
          <Box display="flex" alignItems="center" gap={1}>
            <Chip label={statusCfg.label} color={statusCfg.color} size="small" />
            <IconButton
              size="small"
              onClick={(e) => {
                e.stopPropagation()
                setExpanded(!expanded)
              }}
            >
              <ExpandMoreIcon sx={{ transform: expanded ? 'rotate(180deg)' : 'none', transition: '0.2s' }} />
            </IconButton>
          </Box>
        </Box>
        <Typography variant="h6" fontWeight={700} sx={{ mt: 1 }}>
          {Number(invoice.amount).toLocaleString('vi-VN')} VND
        </Typography>
        <Collapse in={expanded} timeout="auto">
          <Box mt={1} pt={1} borderTop="1px solid" borderColor="divider">
            <Typography variant="body2">Units consumed: {invoice.unitsConsumed ?? 'N/A'}</Typography>
            <Typography variant="body2">Unit price: {invoice.unitPrice ? `${invoice.unitPrice} VND` : 'N/A'}</Typography>
            {invoice.paidAt && (
              <Typography variant="body2" color="success.main">
                Paid: {new Date(invoice.paidAt).toLocaleDateString('vi-VN')}
              </Typography>
            )}
          </Box>
        </Collapse>
      </CardContent>
    </Card>
  )
}
