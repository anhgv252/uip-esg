import { useState } from 'react';
import {
  Box,
  Typography,
  Paper,
  TextField,
  MenuItem,
  Grid,
  Table,
  TableHead,
  TableBody,
  TableRow,
  TableCell,
  TableContainer,
  CircularProgress,
  Alert,
  Chip,
  Card,
  CardContent,
  Tabs,
  Tab,
} from '@mui/material';
import {
  AttachMoney as MoneyIcon,
  Build as BuildingIcon,
  SmartToy as AiIcon,
  Notifications as AlertIcon,
  TrendingUp as RoiIcon,
  Receipt as InvoiceIcon,
} from '@mui/icons-material';
import { useBillingUsage, useBuildingRoi, type TenantUsage } from '@/hooks/useBillingUsage';
import BuildingRoiChart from './billing/BuildingRoiChart';
import InvoiceListTab from './billing/InvoiceListTab';

// BR-014: Billing cost formula
const BASE_RATE_PER_BUILDING = 2_000_000; // VND/month
const TOKEN_RATE = 0.05; // VND/token (charged on tokens above 100k base)
const BASE_TOKEN_ALLOWANCE = 100_000;

function calculateEstimatedCost(usage: TenantUsage): number {
  const baseCost = (usage.buildingCount || 1) * BASE_RATE_PER_BUILDING;
  const tokenOverage = Math.max(0, (usage.totalAiTokens || 0) - BASE_TOKEN_ALLOWANCE);
  return baseCost + tokenOverage * TOKEN_RATE;
}

function formatVND(amount: number): string {
  return new Intl.NumberFormat('vi-VN', { style: 'currency', currency: 'VND' }).format(amount);
}

interface UsageSummaryCardProps {
  title: string;
  value: string | number;
  subtitle?: string;
  icon: React.ReactNode;
  color: string;
}

function UsageSummaryCard({ title, value, subtitle, icon, color }: UsageSummaryCardProps) {
  return (
    <Card variant="outlined">
      <CardContent>
        <Box display="flex" alignItems="center" gap={1} mb={1}>
          <Box sx={{ color, display: 'flex' }}>{icon}</Box>
          <Typography variant="subtitle2" color="text.secondary">
            {title}
          </Typography>
        </Box>
        <Typography variant="h5" fontWeight={600}>
          {value}
        </Typography>
        {subtitle && (
          <Typography variant="caption" color="text.secondary">
            {subtitle}
          </Typography>
        )}
      </CardContent>
    </Card>
  );
}

export default function BillingPage() {
  const [selectedMonth, setSelectedMonth] = useState('2026-06');
  const [activeTab, setActiveTab] = useState(0);
  const [selectedBuildingForRoi, setSelectedBuildingForRoi] = useState('');

  const { data: usage, isLoading, error } = useBillingUsage(selectedMonth);
  const { data: roiData, isLoading: roiLoading, error: roiError } = useBuildingRoi(
    selectedBuildingForRoi,
    selectedMonth
  );

  const monthOptions = [
    { value: '2026-06', label: 'June 2026' },
    { value: '2026-05', label: 'May 2026' },
    { value: '2026-04', label: 'April 2026' },
  ];

  const estimatedCost = usage ? calculateEstimatedCost(usage) : 0;
  const baseCost = (usage?.buildingCount || 1) * BASE_RATE_PER_BUILDING;
  const tokenCost = estimatedCost - baseCost;

  return (
    <Box>
      {/* Tabs */}
      <Tabs value={activeTab} onChange={(_, newValue) => setActiveTab(newValue)} sx={{ mb: 3, borderBottom: 1, borderColor: 'divider' }}>
        <Tab label="Usage & Cost" />
        <Tab label="ROI Analysis" icon={<RoiIcon />} iconPosition="start" />
        <Tab label="Invoices" icon={<InvoiceIcon />} iconPosition="start" />
      </Tabs>

      {/* Tab Panel: Usage & Cost */}
      {activeTab === 0 && (
        <>
          {/* Period Selector */}
          <Box display="flex" gap={2} mb={3} alignItems="center">
            <TextField
              select
              size="small"
              label="Billing Period"
              value={selectedMonth}
              onChange={(e) => setSelectedMonth(e.target.value)}
              sx={{ minWidth: 200 }}
            >
              {monthOptions.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>
                  {opt.label}
                </MenuItem>
              ))}
            </TextField>
            {usage && (
              <Chip
                label={`${usage.buildingCount} building${usage.buildingCount !== 1 ? 's' : ''}`}
                size="small"
              />
            )}
          </Box>

          {error && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Failed to load billing data
            </Alert>
          )}

          {isLoading && (
            <Box display="flex" justifyContent="center" py={6}>
              <CircularProgress />
            </Box>
          )}

      {!isLoading && usage && (
        <>
          {/* Summary Cards */}
          <Grid container spacing={2} mb={4}>
            <Grid item xs={12} sm={6} md={3}>
              <UsageSummaryCard
                title="Estimated Cost"
                value={formatVND(estimatedCost)}
                subtitle={`Base: ${formatVND(baseCost)} + Tokens: ${formatVND(tokenCost)}`}
                icon={<MoneyIcon />}
                color="primary.main"
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <UsageSummaryCard
                title="Buildings"
                value={usage.buildingCount}
                subtitle={`${formatVND(BASE_RATE_PER_BUILDING)}/building/month`}
                icon={<BuildingIcon />}
                color="secondary.main"
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <UsageSummaryCard
                title="AI Tokens"
                value={usage.totalAiTokens.toLocaleString()}
                subtitle={`${usage.totalNlQueries} NL queries`}
                icon={<AiIcon />}
                color="success.main"
              />
            </Grid>
            <Grid item xs={12} sm={6} md={3}>
              <UsageSummaryCard
                title="Alert Rules"
                value={usage.totalAlertRules}
                subtitle="Active rules"
                icon={<AlertIcon />}
                color="warning.main"
              />
            </Grid>
          </Grid>

          {/* Building Breakdown Table */}
          <Paper variant="outlined">
            <Box p={2} borderBottom="1px solid" borderColor="divider">
              <Typography variant="h6">Building Breakdown</Typography>
              <Typography variant="body2" color="text.secondary">
                Usage detail per building for {monthOptions.find((m) => m.value === selectedMonth)?.label}
              </Typography>
            </Box>
            <TableContainer>
              <Table>
                <TableHead>
                  <TableRow>
                    <TableCell>Building</TableCell>
                    <TableCell align="right">AI Tokens</TableCell>
                    <TableCell align="right">NL Queries</TableCell>
                    <TableCell align="right">Alert Rules</TableCell>
                    <TableCell align="right">Cost Attribution</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {usage.buildingBreakdown.map((bldg) => {
                    const bldgBaseCost = BASE_RATE_PER_BUILDING;
                    const bldgTokenOverage = Math.max(0, bldg.totalAiTokens - BASE_TOKEN_ALLOWANCE / usage.buildingCount);
                    const bldgTokenCost = bldgTokenOverage * TOKEN_RATE;
                    const bldgTotalCost = bldgBaseCost + bldgTokenCost;

                    return (
                      <TableRow key={bldg.buildingId} hover>
                        <TableCell>
                          <Typography variant="body2" fontWeight={600}>
                            {bldg.buildingName}
                          </Typography>
                          <Typography variant="caption" color="text.secondary">
                            {bldg.buildingId}
                          </Typography>
                        </TableCell>
                        <TableCell align="right">{bldg.totalAiTokens.toLocaleString()}</TableCell>
                        <TableCell align="right">{bldg.totalNlQueries}</TableCell>
                        <TableCell align="right">{bldg.totalAlertRules}</TableCell>
                        <TableCell align="right">
                          <Typography variant="body2" fontWeight={600}>
                            {formatVND(bldgTotalCost)}
                          </Typography>
                          {bldgTokenCost > 0 && (
                            <Typography variant="caption" color="text.secondary">
                              (+{formatVND(bldgTokenCost)} tokens)
                            </Typography>
                          )}
                        </TableCell>
                      </TableRow>
                    );
                  })}
                </TableBody>
              </Table>
            </TableContainer>
          </Paper>

          {/* Billing Policy Note */}
          <Alert severity="info" sx={{ mt: 3 }}>
            <Typography variant="body2" fontWeight={600} gutterBottom>
              Billing Policy (BR-014):
            </Typography>
            <Typography variant="body2" component="ul" sx={{ pl: 2, mb: 0 }}>
              <li>Base rate: {formatVND(BASE_RATE_PER_BUILDING)}/building/month (includes {BASE_TOKEN_ALLOWANCE.toLocaleString()} AI tokens)</li>
              <li>Additional tokens: {TOKEN_RATE} VND/token above base allowance</li>
              <li>Usage tracked per building for cost attribution</li>
            </Typography>
          </Alert>
        </>
      )}

      {/* Tab Panel: ROI Analysis */}
      {activeTab === 1 && (
        <>
          <Box display="flex" gap={2} mb={3} alignItems="center">
            <TextField
              select
              size="small"
              label="Select Building"
              value={selectedBuildingForRoi}
              onChange={(e) => setSelectedBuildingForRoi(e.target.value)}
              sx={{ minWidth: 250 }}
            >
              <MenuItem value="">
                <em>-- Choose a building --</em>
              </MenuItem>
              {usage?.buildingBreakdown.map((bldg) => (
                <MenuItem key={bldg.buildingId} value={bldg.buildingId}>
                  {bldg.buildingName}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              select
              size="small"
              label="Month"
              value={selectedMonth}
              onChange={(e) => setSelectedMonth(e.target.value)}
              sx={{ minWidth: 200 }}
            >
              {monthOptions.map((opt) => (
                <MenuItem key={opt.value} value={opt.value}>
                  {opt.label}
                </MenuItem>
              ))}
            </TextField>
          </Box>

          {!selectedBuildingForRoi && (
            <Paper variant="outlined" sx={{ p: 4, textAlign: 'center' }}>
              <Typography color="text.secondary">Select a building to view ROI analysis</Typography>
            </Paper>
          )}

          {roiError && (
            <Alert severity="error" sx={{ mb: 2 }}>
              Failed to load ROI data
            </Alert>
          )}

          {roiLoading && (
            <Box display="flex" justifyContent="center" py={6}>
              <CircularProgress />
            </Box>
          )}

          {!roiLoading && roiData && selectedBuildingForRoi && (
            <BuildingRoiChart roi={roiData} />
          )}
        </>
      )}

      {/* Tab Panel: Invoices */}
      {activeTab === 2 && <InvoiceListTab />}
    </Box>
  );
}
