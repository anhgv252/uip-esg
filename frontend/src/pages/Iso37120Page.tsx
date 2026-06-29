import { useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  TextField,
  MenuItem,
  Button,
  CircularProgress,
  Alert,
  Chip,
  Card,
  CardContent,
  Grid,
} from '@mui/material';
import {
  Download as DownloadIcon,
  CheckCircle as CheckIcon,
  Cancel as CancelIcon,
} from '@mui/icons-material';
import { useIso37120Report } from '@/hooks/useIso37120';
import type { Iso37120Category } from '@/types/iso37120';

const _CATEGORY_LABELS: Record<Iso37120Category, string> = {
  ECONOMY: 'Economy',
  EDUCATION: 'Education',
  ENERGY: 'Energy',
  ENVIRONMENT: 'Environment',
  FINANCE: 'Finance',
  GOVERNANCE: 'Governance',
  HEALTH: 'Health',
  SAFETY: 'Safety',
  SHELTER: 'Shelter',
};

const CATEGORY_COLORS: Record<Iso37120Category, string> = {
  ECONOMY: 'primary.main',
  EDUCATION: 'secondary.main',
  ENERGY: 'warning.main',
  ENVIRONMENT: 'success.main',
  FINANCE: 'info.main',
  GOVERNANCE: 'grey.600',
  HEALTH: 'error.main',
  SAFETY: 'error.light',
  SHELTER: 'success.light',
};

export default function Iso37120Page() {
  const currentYear = new Date().getFullYear();
  const [selectedYear, setSelectedYear] = useState(currentYear);

  const { data: report, isLoading, error } = useIso37120Report(selectedYear);

  const yearOptions = Array.from({ length: 5 }, (_, i) => ({
    value: currentYear - i,
    label: String(currentYear - i),
  }));

  const handleDownloadReport = () => {
    // Placeholder for CSV export
    alert('Download CSV functionality will be implemented in next sprint');
  };

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        ISO 37120 City Indicators
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Sustainable Cities and Communities — Indicators for City Services and Quality of Life
      </Typography>

      {/* Year Selector & Actions */}
      <Box display="flex" justifyContent="space-between" alignItems="center" mb={3}>
        <TextField
          select
          size="small"
          label="Year"
          value={selectedYear}
          onChange={(e) => setSelectedYear(Number(e.target.value))}
          sx={{ minWidth: 150 }}
        >
          {yearOptions.map((opt) => (
            <MenuItem key={opt.value} value={opt.value}>
              {opt.label}
            </MenuItem>
          ))}
        </TextField>

        <Button
          variant="outlined"
          startIcon={<DownloadIcon />}
          onClick={handleDownloadReport}
          disabled={!report}
        >
          Download Report
        </Button>
      </Box>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load ISO 37120 report
        </Alert>
      )}

      {isLoading && (
        <Box display="flex" justifyContent="center" py={6}>
          <CircularProgress />
        </Box>
      )}

      {!isLoading && report && (
        <>
          {/* Summary Stats */}
          <Paper variant="outlined" sx={{ p: 3, mb: 4 }}>
            <Box display="flex" justifyContent="space-between" alignItems="center">
              <Box>
                <Typography variant="h6" gutterBottom>
                  Report Summary — {report.year}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {report.calculatedIndicators} / {report.totalIndicators} indicators calculated
                </Typography>
              </Box>
              <Box textAlign="right">
                <Typography variant="body2" color="text.secondary">
                  Last updated
                </Typography>
                <Typography variant="body2" fontWeight={600}>
                  {new Date(report.lastUpdated).toLocaleDateString()}
                </Typography>
              </Box>
            </Box>
          </Paper>

          {/* Indicator Grid */}
          <Grid container spacing={2}>
            {report.indicators.map((indicator) => (
              <Grid item xs={12} sm={6} md={4} key={indicator.code}>
                {indicator.dataAvailable ? (
                  <Card
                    variant="outlined"
                    sx={{
                      height: '100%',
                      transition: 'box-shadow 0.2s',
                      '&:hover': { boxShadow: 2 },
                    }}
                  >
                    <CardContent>
                      <Box display="flex" justifyContent="space-between" alignItems="start" mb={2}>
                        <Chip
                          label={indicator.code}
                          size="small"
                          sx={{
                            bgcolor: CATEGORY_COLORS[indicator.category],
                            color: 'white',
                            fontWeight: 600,
                          }}
                        />
                        <CheckIcon fontSize="small" sx={{ color: 'success.main' }} />
                      </Box>
                      <Typography variant="body2" fontWeight={600} gutterBottom>
                        {indicator.name}
                      </Typography>
                      <Typography variant="h5" color="primary.main" gutterBottom>
                        {indicator.value} <Typography variant="caption">{indicator.unit}</Typography>
                      </Typography>
                      <Chip
                        label={indicator.dataSource}
                        size="small"
                        variant="outlined"
                        sx={{ mt: 1 }}
                      />
                    </CardContent>
                  </Card>
                ) : (
                  <Card
                    variant="outlined"
                    sx={{
                      height: '100%',
                      bgcolor: 'grey.50',
                      borderColor: 'grey.300',
                    }}
                  >
                    <CardContent>
                      <Box display="flex" justifyContent="space-between" alignItems="start" mb={2}>
                        <Chip
                          label={indicator.code}
                          size="small"
                          sx={{ bgcolor: 'grey.300', fontWeight: 600 }}
                        />
                        <CancelIcon fontSize="small" sx={{ color: 'grey.500' }} />
                      </Box>
                      <Typography variant="body2" fontWeight={600} color="text.secondary" gutterBottom>
                        {indicator.name}
                      </Typography>
                      <Typography variant="h6" color="text.secondary" gutterBottom>
                        No data
                      </Typography>
                      <Chip
                        label={indicator.dataSource}
                        size="small"
                        variant="outlined"
                        sx={{ mt: 1, borderColor: 'grey.400' }}
                      />
                    </CardContent>
                  </Card>
                )}
              </Grid>
            ))}
          </Grid>
        </>
      )}
    </Box>
  );
}
