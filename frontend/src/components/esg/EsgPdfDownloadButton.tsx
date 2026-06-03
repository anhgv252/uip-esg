import { useState } from 'react'
import { Alert, Box, Button, CircularProgress, Tooltip } from '@mui/material'
import PictureAsPdfIcon from '@mui/icons-material/PictureAsPdf'
import { useMutation } from '@tanstack/react-query'
import { generatePdfReport } from '@/api/esg'
import { useScope } from '@/hooks/useScope'

interface EsgPdfDownloadButtonProps {
  year: number
  quarter: number
}

/**
 * "Generate PDF Report" button — visible only with esg:write scope (B1-5 / FE-5).
 * Click → POST /esg/reports/pdf → blob download → auto-save as PDF file.
 * Memory leak prevention: URL.revokeObjectURL() called after download.
 */
export function EsgPdfDownloadButton({ year, quarter }: EsgPdfDownloadButtonProps) {
  const canExport = useScope('esg:write')
  const [errorMsg, setErrorMsg] = useState<string | null>(null)

  const { mutate: download, isPending } = useMutation({
    mutationFn: () => generatePdfReport(year, quarter),
    onSuccess: (blob: Blob) => {
      const url = URL.createObjectURL(blob)
      const a = document.createElement('a')
      a.href = url
      a.download = `esg-report-Q${quarter}-${year}.pdf`
      document.body.appendChild(a)
      a.click()
      document.body.removeChild(a)
      URL.revokeObjectURL(url)  // prevent memory leak
      setErrorMsg(null)
    },
    onError: () => {
      setErrorMsg('Không thể tạo PDF. Vui lòng thử lại sau.')
    },
  })

  if (!canExport) return null

  return (
    <Box>
      <Tooltip title={`Tải xuống báo cáo ESG Q${quarter} ${year} (PDF)`}>
        <Button
          variant="outlined"
          size="small"
          startIcon={isPending ? <CircularProgress size={16} /> : <PictureAsPdfIcon />}
          onClick={() => download()}
          disabled={isPending}
          aria-label={`Tạo báo cáo PDF Q${quarter} ${year}`}
        >
          {isPending ? 'Đang tạo PDF…' : 'Tải PDF'}
        </Button>
      </Tooltip>
      {errorMsg && (
        <Alert severity="error" sx={{ mt: 1 }} onClose={() => setErrorMsg(null)}>
          {errorMsg}
        </Alert>
      )}
    </Box>
  )
}
