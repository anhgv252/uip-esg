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
} from '@mui/material';
import DownloadIcon from '@mui/icons-material/Download';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { triggerReportGeneration, getReportStatus, downloadReport } from '../../api/esg';

const CURRENT_YEAR = new Date().getFullYear();
const QUARTERS = [1, 2, 3, 4];
const YEARS = [CURRENT_YEAR, CURRENT_YEAR - 1, CURRENT_YEAR - 2];

export function ReportGenerationPanel() {
  const [selectedYear, setSelectedYear] = useState(CURRENT_YEAR);
  const [selectedQuarter, setSelectedQuarter] = useState(Math.ceil((new Date().getMonth() + 1) / 3));
  const [activeReportId, setActiveReportId] = useState<string | null>(null);

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

  const handleDownload = async () => {
    if (!activeReportId) return;
    if (reportStatus?.downloadUrl) {
      // Use backend-provided download URL if available
      window.location.href = reportStatus.downloadUrl;
      return;
    }
    const blob = await downloadReport(activeReportId);
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `esg-report-q${selectedQuarter}-${selectedYear}.xlsx`;
    a.click();
    URL.revokeObjectURL(url);
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

        <Button
          variant="contained"
          onClick={() => triggerMutation.mutate()}
          disabled={isGenerating || triggerMutation.isPending}
        >
          Generate Report
        </Button>

        {isDone && (
          <Button
            variant="outlined"
            startIcon={<DownloadIcon />}
            onClick={handleDownload}
            color="success"
          >
            Download XLSX
          </Button>
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
          Report ready! Click <strong>Download XLSX</strong> to save.
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
    </Box>
  );
}

export default ReportGenerationPanel;
