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
  LinearProgress,
  Chip,
  Table,
  TableBody,
  TableCell,
  TableContainer,
  TableHead,
  TableRow,
  Card,
  CardContent,
  Grid,
} from '@mui/material';
import {
  Refresh as RefreshIcon,
  Star as StarIcon,
  StarBorder as StarBorderIcon,
} from '@mui/icons-material';
import { useLotusScore, useLotusBuildings, useRefreshLotusScore } from '@/hooks/useLotusVn';
import type { LotusLevel, LotusCategoryCode } from '@/types/lotusVn';

const LEVEL_COLORS: Record<LotusLevel, string> = {
  PLATINUM: 'grey.700',
  GOLD: 'warning.main',
  SILVER: 'grey.400',
  CERTIFIED: 'success.main',
  NOT_CERTIFIED: 'error.main',
};

const LEVEL_LABELS: Record<LotusLevel, string> = {
  PLATINUM: 'Platinum',
  GOLD: 'Gold',
  SILVER: 'Silver',
  CERTIFIED: 'Certified',
  NOT_CERTIFIED: 'Not Certified',
};

const CATEGORY_NAMES: Record<LotusCategoryCode, string> = {
  EN: 'Energy',
  WA: 'Water',
  IEQ: 'Indoor Quality',
  MA: 'Materials',
  ST: 'Site & Transport',
};

function getCategoryColor(percentage: number): string {
  if (percentage >= 70) return 'success.main';
  if (percentage >= 40) return 'warning.main';
  return 'error.main';
}

function renderStars(score: number) {
  const maxStars = 4;
  return (
    <Box display="inline-flex" alignItems="center" gap={0.5}>
      {Array.from({ length: maxStars }, (_, i) =>
        i < score ? (
          <StarIcon key={i} fontSize="small" sx={{ color: 'warning.main' }} />
        ) : (
          <StarBorderIcon key={i} fontSize="small" sx={{ color: 'grey.400' }} />
        )
      )}
    </Box>
  );
}

export default function LotusVnPage() {
  const [selectedBuildingId, setSelectedBuildingId] = useState('');
  const [selectedPeriod, setSelectedPeriod] = useState('');

  const { data: buildings, isLoading: buildingsLoading } = useLotusBuildings();
  const { data: report, isLoading: reportLoading, error } = useLotusScore(selectedBuildingId, selectedPeriod);
  const refreshMutation = useRefreshLotusScore();

  const handleRefresh = () => {
    if (!selectedBuildingId) return;
    refreshMutation.mutate({ buildingId: selectedBuildingId, period: selectedPeriod || undefined });
  };

  const periodOptions = [
    { value: '', label: 'Latest' },
    { value: '2026-06', label: 'June 2026' },
    { value: '2026-05', label: 'May 2026' },
    { value: '2026-04', label: 'April 2026' },
  ];

  return (
    <Box>
      <Typography variant="h4" gutterBottom>
        LOTUS VN Certification
      </Typography>
      <Typography variant="body2" color="text.secondary" paragraph>
        Green Building Certification for Vietnam
      </Typography>

      {/* Selectors */}
      <Box display="flex" gap={2} mb={3} alignItems="center">
        <TextField
          select
          size="small"
          label="Select Building"
          value={selectedBuildingId}
          onChange={(e) => setSelectedBuildingId(e.target.value)}
          sx={{ minWidth: 250 }}
          disabled={buildingsLoading}
        >
          <MenuItem value="">
            <em>-- Choose a building --</em>
          </MenuItem>
          {buildings?.map((bldg) => (
            <MenuItem key={bldg.id} value={bldg.id}>
              {bldg.name}
            </MenuItem>
          ))}
        </TextField>

        <TextField
          select
          size="small"
          label="Period"
          value={selectedPeriod}
          onChange={(e) => setSelectedPeriod(e.target.value)}
          sx={{ minWidth: 180 }}
        >
          {periodOptions.map((opt) => (
            <MenuItem key={opt.value} value={opt.value}>
              {opt.label}
            </MenuItem>
          ))}
        </TextField>

        <Button
          variant="outlined"
          startIcon={<RefreshIcon />}
          onClick={handleRefresh}
          disabled={!selectedBuildingId || refreshMutation.isPending}
        >
          {refreshMutation.isPending ? 'Refreshing...' : 'Refresh Score'}
        </Button>
      </Box>

      {!selectedBuildingId && (
        <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
          <Typography color="text.secondary">Select a building to view LOTUS VN certification report</Typography>
        </Paper>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Failed to load LOTUS VN report
        </Alert>
      )}

      {reportLoading && (
        <Box display="flex" justifyContent="center" py={6}>
          <CircularProgress />
        </Box>
      )}

      {!reportLoading && report && selectedBuildingId && (
        <>
          {/* Overall Score & Level */}
          <Grid container spacing={3} mb={4}>
            <Grid item xs={12} md={6}>
              <Card variant="outlined">
                <CardContent sx={{ textAlign: 'center' }}>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    Overall Score
                  </Typography>
                  <Box
                    sx={{
                      width: 120,
                      height: 120,
                      borderRadius: '50%',
                      border: 4,
                      borderColor: 'primary.main',
                      display: 'flex',
                      alignItems: 'center',
                      justifyContent: 'center',
                      mx: 'auto',
                      my: 2,
                    }}
                  >
                    <Typography variant="h3" fontWeight={700} color="primary.main">
                      {report.overallScore}
                    </Typography>
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    Out of 100
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
            <Grid item xs={12} md={6}>
              <Card variant="outlined">
                <CardContent sx={{ textAlign: 'center' }}>
                  <Typography variant="body2" color="text.secondary" gutterBottom>
                    Certification Level
                  </Typography>
                  <Box my={2}>
                    <Chip
                      label={LEVEL_LABELS[report.certificationLevel]}
                      sx={{
                        fontSize: '1.2rem',
                        py: 3,
                        px: 2,
                        fontWeight: 700,
                        bgcolor: LEVEL_COLORS[report.certificationLevel],
                        color: 'white',
                      }}
                    />
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    Last updated: {new Date(report.lastUpdated).toLocaleString()}
                  </Typography>
                </CardContent>
              </Card>
            </Grid>
          </Grid>

          {/* Category Breakdown */}
          <Paper variant="outlined" sx={{ p: 3, mb: 4 }}>
            <Typography variant="h6" gutterBottom>
              Category Breakdown
            </Typography>
            <Typography variant="body2" color="text.secondary" paragraph>
              Score distribution across 5 main categories
            </Typography>
            {report.categories.map((cat) => (
              <Box key={cat.code} mb={2}>
                <Box display="flex" justifyContent="space-between" alignItems="center" mb={0.5}>
                  <Typography variant="body2" fontWeight={600}>
                    {CATEGORY_NAMES[cat.code]} ({cat.code})
                  </Typography>
                  <Typography variant="body2" color="text.secondary">
                    {cat.score}/{cat.maxScore} ({cat.percentageScore}%)
                  </Typography>
                </Box>
                <LinearProgress
                  variant="determinate"
                  value={cat.percentageScore}
                  sx={{
                    height: 8,
                    borderRadius: 1,
                    bgcolor: 'grey.200',
                    '& .MuiLinearProgress-bar': {
                      bgcolor: getCategoryColor(cat.percentageScore),
                    },
                  }}
                />
              </Box>
            ))}
          </Paper>

          {/* Indicator Table */}
          <Paper variant="outlined">
            <Box p={2} borderBottom="1px solid" borderColor="divider">
              <Typography variant="h6">Indicator Details</Typography>
              <Typography variant="body2" color="text.secondary">
                Individual performance metrics
              </Typography>
            </Box>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Code</TableCell>
                    <TableCell>Indicator Name</TableCell>
                    <TableCell align="right">Actual</TableCell>
                    <TableCell align="right">Benchmark</TableCell>
                    <TableCell align="center">Score</TableCell>
                    <TableCell>Data Source</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {report.indicators.map((ind) => (
                    <TableRow key={ind.code} hover>
                      <TableCell>
                        <Chip label={ind.code} size="small" variant="outlined" />
                      </TableCell>
                      <TableCell>
                        <Typography variant="body2">{ind.name}</Typography>
                      </TableCell>
                      <TableCell align="right">
                        {ind.actualValue} {ind.unit}
                      </TableCell>
                      <TableCell align="right">
                        {ind.benchmark} {ind.unit}
                      </TableCell>
                      <TableCell align="center">{renderStars(ind.score)}</TableCell>
                      <TableCell>
                        <Chip label={ind.dataSource} size="small" variant="filled" />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>
        </>
      )}
    </Box>
  );
}
