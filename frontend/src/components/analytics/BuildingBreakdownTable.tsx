import { Card, CardContent, Typography, Table, TableBody, TableCell, TableHead, TableRow } from '@mui/material'
import type { EnergyAggregateResponse, EmissionsAggregateResponse } from '@/api/analytics'

interface Props {
  energy?: EnergyAggregateResponse
  emissions?: EmissionsAggregateResponse
  loading?: boolean
}

export function BuildingBreakdownTable({ energy, emissions, loading }: Props) {
  const energyMap = new Map(energy?.buildings.map((b) => [b.buildingId, b]) ?? [])
  const emissionsMap = new Map(emissions?.buildings.map((b) => [b.buildingId, b]) ?? [])
  const allIds = new Set([...energyMap.keys(), ...emissionsMap.keys()])

  return (
    <Card sx={{ height: '100%' }}>
      <CardContent>
        <Typography variant="subtitle1" fontWeight={600} gutterBottom>
          Building Breakdown
        </Typography>
        {loading ? (
          <Typography color="text.secondary">Loading...</Typography>
        ) : allIds.size === 0 ? (
          <Typography color="text.secondary">No data</Typography>
        ) : (
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>Building</TableCell>
                <TableCell align="right">Energy (kWh)</TableCell>
                <TableCell align="right">Peak (kW)</TableCell>
                <TableCell align="right">CO2 (kg)</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {Array.from(allIds).map((id) => {
                const e = energyMap.get(id)
                const em = emissionsMap.get(id)
                return (
                  <TableRow key={id}>
                    <TableCell>{id}</TableCell>
                    <TableCell align="right">{e?.totalKwh.toFixed(1) ?? '-'}</TableCell>
                    <TableCell align="right">{e?.peakDemandKw.toFixed(1) ?? '-'}</TableCell>
                    <TableCell align="right">{em?.totalCo2Kg.toFixed(1) ?? '-'}</TableCell>
                  </TableRow>
                )
              })}
            </TableBody>
          </Table>
        )}
      </CardContent>
    </Card>
  )
}
