import { Box, Button, Card, CardContent, Typography } from '@mui/material'
import { useLocation, useNavigate, useParams } from 'react-router-dom'

type InvoiceState = {
  id?: string
  amount?: number
  meterType?: string
  issuedAt?: string
}

export default function MobileBillDetailPage() {
  const navigate = useNavigate()
  const { billId } = useParams()
  const location = useLocation()
  const invoice = (location.state as { invoice?: InvoiceState } | null)?.invoice

  return (
    <Box sx={{ p: 2 }}>
      <Button variant="text" onClick={() => navigate('/citizen/bills')}>Back to Bills</Button>

      <Card data-testid="bill-detail" sx={{ mt: 1.5 }}>
        <CardContent>
          <Typography variant="h6" fontWeight={600}>Bill Detail</Typography>
          <Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
            Bill ID: {invoice?.id ?? billId}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Type: {invoice?.meterType ?? 'N/A'}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            Issued: {invoice?.issuedAt ? new Date(invoice.issuedAt).toLocaleDateString('vi-VN') : 'N/A'}
          </Typography>

          <Typography data-testid="bill-amount" variant="h5" fontWeight={700} sx={{ mt: 2 }}>
            {Number(invoice?.amount ?? 0).toLocaleString('vi-VN')} VND
          </Typography>

          <Button variant="contained" sx={{ mt: 2 }}>
            Pay Online
          </Button>
        </CardContent>
      </Card>
    </Box>
  )
}
