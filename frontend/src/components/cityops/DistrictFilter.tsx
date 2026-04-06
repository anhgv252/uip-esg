import { FormControl, InputLabel, Select, MenuItem } from '@mui/material'
import type { SensorWithAqi } from '@/api/cityops'

const HCMC_DISTRICTS = [
  'Quận 1', 'Quận 3', 'Quận 4', 'Quận 5', 'Quận 6',
  'Quận 7', 'Quận 8', 'Quận 10', 'Quận 11', 'Quận 12',
  'Bình Thạnh', 'Gò Vấp', 'Phú Nhuận', 'Tân Bình',
  'Tân Phú', 'Bình Tân', 'Thủ Đức',
]

interface DistrictFilterProps {
  sensors: SensorWithAqi[]
  value: string | null
  onChange: (district: string | null) => void
}

export default function DistrictFilter({ sensors, value, onChange }: DistrictFilterProps) {
  // Collect districts that actually have sensors
  const activeDistricts = Array.from(new Set(sensors.map((s) => s.district))).sort()
  const available = HCMC_DISTRICTS.filter((d) => activeDistricts.includes(d))

  return (
    <FormControl size="small" sx={{ minWidth: 180 }}>
      <InputLabel>District</InputLabel>
      <Select
        value={value ?? ''}
        label="District"
        onChange={(e) => onChange(e.target.value === '' ? null : e.target.value)}
      >
        <MenuItem value="">
          <em>All districts</em>
        </MenuItem>
        {(available.length > 0 ? available : activeDistricts).map((d) => (
          <MenuItem key={d} value={d}>
            {d}
          </MenuItem>
        ))}
      </Select>
    </FormControl>
  )
}
