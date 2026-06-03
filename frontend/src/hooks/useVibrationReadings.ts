import { useQuery } from '@tanstack/react-query'
import { apiClient } from '@/api/client'

export interface VibrationReading {
  timestamp: string
  value: number
  sensorId: string
  sensorType: 'STRUCTURAL_VIBRATION' | 'STRUCTURAL_TILT' | 'STRUCTURAL_CRACK'
  unit: string
}

async function fetchVibrationReadings(
  buildingId: string,
  sensorType: string,
  range: string
): Promise<VibrationReading[]> {
  const res = await apiClient.get<VibrationReading[]>(
    `/buildings/${buildingId}/vibration/readings`,
    { params: { range, sensorType } }
  )
  return res.data
}

export function useVibrationReadings(
  buildingId: string | undefined,
  sensorType: 'STRUCTURAL_VIBRATION' | 'STRUCTURAL_TILT' | 'STRUCTURAL_CRACK',
  range = '24h'
) {
  return useQuery({
    queryKey: ['safety', 'vibration', buildingId, sensorType, range],
    queryFn: () => fetchVibrationReadings(buildingId!, sensorType, range),
    enabled: !!buildingId,
    staleTime: 60_000,
  })
}
