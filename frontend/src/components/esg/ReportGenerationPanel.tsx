import { useState } from 'react';
import {
  Box,
  Button,
  Typography,
  LinearProgress,
  Alert,
  Stack,
  Divider,
  FormControl,
  InputLabel,
  Select,
  MenuItem,
  Tooltip,
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { triggerReportGeneration, getReportStatus, downloadReport } from '../../api/esg';
import { useScope } from '../../hooks/useScope';

const CURRENT_YEAR = new Date().getFullYear();
const QUARTERS = [1, 2, 3, 4];
const YEARS = [CURRENT_YEAR, CURRENT_YEAR - 1, CURRENT_YEAR - 2, CURRENT_YEAR - 3];

export function ReportGenerationPanel() {
  const [selectedYear, setSelectedYear] = useState(CURRENT_YEAR);
  const [selectedQuarter, setSelectedQuarter] = useState(Math.ceil((new Date().getMonth() + 1) / 3));
  const [activeReportId, setActiveReportId] = useState<string | null>(null);
  const canWrite = useScope('esg:write');

  const qc = useQueryClient();

  const { data: reportStatus } = useQuery({
    queryKey: ['esg-report-status', activeReportId],
    queryFn: () => getReportStatus(activeReportId!),
    enabled: !!activeReportId,
    refetchInterval: (query) => {
      const status = query.state.data?.status;
      return status === 'GENERATING' || status === 'PENDING' ? 3000 : false;
    },
  });

  const triggerMutation = useMutation({
    mutationFn: () => triggerReportGeneration(selectedYear, selectedQuarter),
    onSuccess: (data) => {
      setActiveReportId(data.id);
      qc.invalidateQueries({ queryKey: ['esg-report-status'] });
    },
  });

  const handleDownload = async (format: 'xlsx' | 'pdf' = 'xlsx') => {
    if (!activeReportId) return;
    if (reportStatus?.downloadUrl) {
      window.location.href = `${reportStatus.downloadUrl}?format=${format}`;
      return;
    }
    const blob = await downloadReport(activeReportId, format);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `esg-report-q${selectedQuarter}-${selectedYear}.${format}`;
    a.click();
    setTimeout(() => URL.revokeObjectURL(url), 1000);
  };

  const isGenerating = reportStatus?.status === 'GENERATING' || reportStatus?.status === 'PENDING';
  const isDone = reportStatus?.status === 'DONE';
  const isFailed = reportStatus?.status === 'FAILED';

  return (
    <Box>
      <Typography variant="subtitle1" fontWeight={600} mb={1}>
        Generate ESG Report
      </Typography>
      <Divider sx={{ mb: 2 }} />

      <Stack direction="row" spacing={2} alignItems="center" flexWrap="wrap">
        <FormControl size="small" sx={{ minWidth: 100 }}>
          <InputLabel>Year</InputLabel>
          <Select
            value={selectedYear}
            label="Year"
            onChange={(e) => setSelectedYear(Number(e.target.value))}
            disabled={isGenerating || triggerMutation.isPending}
          >
            {YEARS.map((y) => (
              <MenuItem key={y} value={y}>{y}</MenuItem>
            ))}
          </Select>
        </FormControl>

        <FormControl size="small" sx={{ minWidth: 100 }}>
          <InputLabel>Quarter</InputLabel>
          <Select
            value={selectedQuarter}
            label="Quarter"
            onChange={(e) => setSelectedQuarter(Number(e.target.value))}
            disabled={isGenerating || triggerMutation.isPending}
          >
            {QUARTERS.map((q) => (
              <MenuItem key={q} value={q}>Q{q}</MenuItem>
            ))}
          </Select>
        </FormControl>

        <Tooltip title={!canWrite ? 'You need esg:write scope' : ''}>
          <span>
            <Button
              variant="contained"
              onClick={() => triggerMutation.mutate()}
              disabled={isGenerating || triggerMutation.isPending || !canWrite}
            >
              Generate Report
            </Button>
          </span>
        </Tooltip>

        {isDone && (
          <>
            <Button
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={() => handleDownload('xlsx')}
              color="success"
              aria-label="Download Excel report"
            >
              Download XLSX
            </Button>
            <Button
              variant="outlined"
              startIcon={<DownloadIcon />}
              onClick={() => handleDownload('pdf')}
              color="info"
              aria-label="Download PDF report"
            >
              Download PDF
            </Button>
          </>
        )}
      </Stack>

      {/* Progress */}
      {isGenerating && (
        <Box mt={2}>
          <Typography variant="body2" color="text.secondary" mb={0.5}>
            Generating Q{selectedQuarter} {selectedYear} report…
          </Typography>
          <LinearProgress />
        </Box>
      )}

      {isDone && (
        <Alert severity="success" sx={{ mt: 2 }}>
          Report ready! Click <strong>Download XLSX</strong> or <strong>Download PDF</strong> to save.
          {reportStatus?.generatedAt && (
            <Typography variant="caption" display="block">
              Completed: {new Date(reportStatus.generatedAt).toLocaleString()}
            </Typography>
          )}
        </Alert>
      )}

      {isFailed && (
        <Alert severity="error" sx={{ mt: 2 }}>
          Report generation failed. Please try again.
        </Alert>
      )}

      {triggerMutation.isError && (
        <Alert severity="error" sx={{ mt: 2 }}>
          Failed to start report generation. Please try again.
        </Alert>
      )}

      {!activeReportId && !triggerMutation.isPending && !triggerMutation.isError && (
        <Typography
          variant="body2"
          color="text.secondary"
          sx={{ mt: 2, fontStyle: 'italic' }}
          aria-label="Report generation hint"
        >
          Select period and click Generate to create an ESG report.
        </Typography>
      )}
    </Box>
  );
}

export default ReportGenerationPanel;
