import { useCallback } from 'react'
import { Box, Button, FormControl, InputLabel, MenuItem, Select, Chip, OutlinedInput, Typography, TextField } from '@mui/material'
import { useSearchParams } from 'react-router-dom'
import { useBuildings } from '@/hooks/useBuildings'

const METRIC_TYPES = ['energy', 'emissions', 'aqi'] as const
type MetricType = (typeof METRIC_TYPES)[number]

const GROUP_BY_OPTIONS = ['hour', 'day', 'week', 'month'] as const
type GroupBy = (typeof GROUP_BY_OPTIONS)[number]

const MAX_BUILDINGS = 10

function getAllowedGroupBy(daysDiff: number): GroupBy[] {
  if (daysDiff <= 2) return ['hour', 'day']
  if (daysDiff > 90) return ['week', 'month']
  return ['hour', 'day', 'week', 'month']
}

function daysBetween(from: string, to: string): number {
  const d1 = new Date(from)
  const d2 = new Date(to)
  return Math.round((d2.getTime() - d1.getTime()) / (1000 * 60 * 60 * 24))
}

function today(): string {
  return new Date().toISOString().split('T')[0]
}

function thirtyDaysAgo(): string {
  const d = new Date()
  d.setDate(d.getDate() - 30)
  return d.toISOString().split('T')[0]
}

export function AnalyticsFilterPanel() {
  const [searchParams, setSearchParams] = useSearchParams()
  const { data: allBuildings = [] } = useBuildings()

  const buildingIds = searchParams.get('ids')?.split(',').filter(Boolean) ?? []
  const metric = (searchParams.get('metric') as MetricType) ?? 'energy'
  const groupBy = (searchParams.get('groupBy') as GroupBy) ?? 'day'
  const from = searchParams.get('from') ?? thirtyDaysAgo()
  const to = searchParams.get('to') ?? today()

  const daysDiff = daysBetween(from, to)
  const allowedGroupBy = getAllowedGroupBy(daysDiff)

  const update = useCallback(
    (updates: Record<string, string | undefined>) => {
      const next = new URLSearchParams(searchParams)
      for (const [key, value] of Object.entries(updates)) {
        if (value) next.set(key, value)
        else next.delete(key)
      }
      setSearchParams(next, { replace: true })
    },
    [searchParams, setSearchParams],
  )

  const handleReset = () => setSearchParams({}, { replace: true })

  return (
    <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 2, alignItems: 'center', mb: 2 }}>
      <FormControl size="small" sx={{ minWidth: 200 }}>
        <InputLabel>Buildings</InputLabel>
        <Select
          multiple
          value={buildingIds}
          onChange={(e) => {
            const val = e.target.value as string[]
            if (val.length <= MAX_BUILDINGS) {
              update({ ids: val.length > 0 ? val.join(',') : undefined })
            }
          }}
          input={<OutlinedInput label="Buildings" />}
          renderValue={(selected) => (
            <Box sx={{ display: 'flex', flexWrap: 'wrap', gap: 0.5 }}>
              {selected.map((val) => (
                <Chip key={val} label={val} size="small" />
              ))}
            </Box>
          )}
        >
          {allBuildings.map((b) => (
            <MenuItem key={b.buildingCode} value={b.buildingCode}>
              {b.buildingName} ({b.buildingCode})
            </MenuItem>
          ))}
        </Select>
        <Typography variant="caption" color="text.secondary" sx={{ mt: 0.5 }}>
          Max {MAX_BUILDINGS} buildings ({buildingIds.length}/{MAX_BUILDINGS})
        </Typography>
      </FormControl>

      <FormControl size="small" sx={{ minWidth: 120 }}>
        <InputLabel>Metric</InputLabel>
        <Select value={metric} label="Metric" onChange={(e) => update({ metric: e.target.value })}>
          {METRIC_TYPES.map((m) => (
            <MenuItem key={m} value={m}>{m.charAt(0).toUpperCase() + m.slice(1)}</MenuItem>
          ))}
        </Select>
      </FormControl>

      <FormControl size="small" sx={{ minWidth: 120 }}>
        <InputLabel>Group By</InputLabel>
        <Select value={groupBy} label="Group By" onChange={(e) => update({ groupBy: e.target.value })}>
          {allowedGroupBy.map((g) => (
            <MenuItem key={g} value={g}>{g.charAt(0).toUpperCase() + g.slice(1)}</MenuItem>
          ))}
        </Select>
      </FormControl>

      <TextField
        label="From"
        type="date"
        size="small"
        value={from}
        onChange={(e) => update({ from: e.target.value })}
        InputLabelProps={{ shrink: true }}
      />

      <TextField
        label="To"
        type="date"
        size="small"
        value={to}
        onChange={(e) => update({ to: e.target.value })}
        InputLabelProps={{ shrink: true }}
      />

      <Button variant="outlined" size="small" onClick={handleReset}>
        Reset
      </Button>
    </Box>
  )
}
