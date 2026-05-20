import { Box, Typography, Chip, Table, TableBody, TableCell, TableHead, TableRow, Collapse, IconButton } from '@mui/material';
import { ExpandMore, ExpandLess } from '@mui/icons-material';
import { useState } from 'react';

interface ReportSummary {
  energyTotal?: number;
  carbonTotal?: number;
  energyIntensityKwhPerM2?: number;
  co2EmissionsPerM2?: number;
  dataQuality?: string;
  buildingBreakdown?: Record<string, number>;
  quarter: number;
  year: number;
}

interface EsgReportPreviewProps {
  summary: ReportSummary;
}

const qualityColor: Record<string, 'success' | 'warning' | 'error'> = {
  COMPLETE: 'success',
  PARTIAL: 'warning',
  ESTIMATED: 'error',
};

export function EsgReportPreview({ summary }: EsgReportPreviewProps) {
  const [expanded, setExpanded] = useState(false);

  const totalEnergy = summary.energyTotal ?? 0;
  const totalCarbon = summary.carbonTotal ?? 0;
  const breakdown = summary.buildingBreakdown ?? {};

  return (
    <Box sx={{ mt: 2 }}>
      <Typography variant="subtitle2" gutterBottom>
        Report Preview — Q{summary.quarter} {summary.year}
      </Typography>

      <Box sx={{ display: 'flex', gap: 2, flexWrap: 'wrap', mb: 2 }}>
        <Box>
          <Typography variant="caption" color="text.secondary">Energy Total</Typography>
          <Typography variant="body2" fontWeight={600}>{totalEnergy.toFixed(2)} kWh</Typography>
        </Box>
        <Box>
          <Typography variant="caption" color="text.secondary">Carbon Total</Typography>
          <Typography variant="body2" fontWeight={600}>{totalCarbon.toFixed(4)} tCO2e</Typography>
        </Box>
        {summary.energyIntensityKwhPerM2 != null && (
          <Box>
            <Typography variant="caption" color="text.secondary">Energy Intensity</Typography>
            <Typography variant="body2" fontWeight={600}>{summary.energyIntensityKwhPerM2.toFixed(2)} kWh/m2</Typography>
          </Box>
        )}
        {summary.co2EmissionsPerM2 != null && (
          <Box>
            <Typography variant="caption" color="text.secondary">CO2 Intensity</Typography>
            <Typography variant="body2" fontWeight={600}>{summary.co2EmissionsPerM2.toFixed(4)} tCO2e/m2</Typography>
          </Box>
        )}
        {summary.dataQuality && (
          <Box>
            <Typography variant="caption" color="text.secondary">Data Quality</Typography>
            <Chip
              label={summary.dataQuality}
              color={qualityColor[summary.dataQuality] ?? 'default'}
              size="small"
              sx={{ ml: 0.5 }}
            />
          </Box>
        )}
      </Box>

      {Object.keys(breakdown).length > 0 && (
        <>
          <Box sx={{ display: 'flex', alignItems: 'center', mb: 1 }}>
            <Typography variant="caption" color="text.secondary" sx={{ mr: 1 }}>
              Per-Building Breakdown ({Object.keys(breakdown).length} buildings)
            </Typography>
            <IconButton size="small" onClick={() => setExpanded(!expanded)} aria-label={expanded ? 'Collapse breakdown' : 'Expand breakdown'}>
              {expanded ? <ExpandLess fontSize="small" /> : <ExpandMore fontSize="small" />}
            </IconButton>
          </Box>
          <Collapse in={expanded}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Building</TableCell>
                  <TableCell align="right">kWh</TableCell>
                  <TableCell align="right">% of Total</TableCell>
                </TableRow>
              </TableHead>
              <TableBody>
                {Object.entries(breakdown)
                  .sort(([, a], [, b]) => b - a)
                  .map(([building, value]) => (
                    <TableRow key={building}>
                      <TableCell>{building}</TableCell>
                      <TableCell align="right">{value.toFixed(2)}</TableCell>
                      <TableCell align="right">
                        {totalEnergy > 0 ? ((value / totalEnergy) * 100).toFixed(1) : '—'}%
                      </TableCell>
                    </TableRow>
                  ))}
              </TableBody>
            </Table>
          </Collapse>
        </>
      )}
    </Box>
  );
}

export default EsgReportPreview;
