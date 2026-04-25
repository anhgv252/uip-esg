import type { Sensor, AqiResponse } from './environment'
import { getSensors, getCurrentAqi } from './environment'
import type { AlertEvent, AlertEventsPage } from './alerts'
import { getAlerts } from './alerts'

export type { Sensor, AqiResponse, AlertEvent, AlertEventsPage }

export interface SensorWithAqi extends Sensor {
  aqi?: number
  aqiCategory?: string
  aqiColor?: string
}

export type CongestionLevel = 'FREE' | 'MODERATE' | 'HEAVY' | 'STANDSTILL'

export interface CongestionSegment {
  id: string
  name: string
  level: CongestionLevel
  positions: Array<[number, number]>
}

/** Returns all active sensors enriched with current AQI data */
export async function getSensorsForMap(): Promise<SensorWithAqi[]> {
  const [sensors, aqiList] = await Promise.all([getSensors(), getCurrentAqi()])
  return sensors.map((s) => {
    const aqiEntry = aqiList.find((a) => a.sensorId === s.sensorCode)
    return {
      ...s,
      aqi: aqiEntry?.aqi,
      aqiCategory: aqiEntry?.category,
      aqiColor: aqiEntry?.color,
    }
  })
}

/** Returns recent 20 alerts for alert feed panel */
export const getRecentAlerts = () =>
  getAlerts({ size: 20, status: undefined })

const CONGESTION_MOCK: CongestionSegment[] = [
  { id: 'nguyen-hue', name: 'Nguyễn Huệ', level: 'MODERATE', positions: [[10.773, 106.702], [10.778, 106.702]] },
  { id: 'le-loi', name: 'Lê Lợi', level: 'HEAVY', positions: [[10.774, 106.699], [10.774, 106.705]] },
  { id: 'vo-van-kiet', name: 'Võ Văn Kiệt', level: 'FREE', positions: [[10.762, 106.688], [10.768, 106.725]] },
  { id: 'dien-bien-phu', name: 'Điện Biên Phủ', level: 'STANDSTILL', positions: [[10.782, 106.689], [10.782, 106.710]] },
  { id: 'nguyen-van-linh', name: 'Nguyễn Văn Linh', level: 'MODERATE', positions: [[10.732, 106.700], [10.732, 106.740]] },
  { id: 'ham-nghi', name: 'Hàm Nghi', level: 'HEAVY', positions: [[10.773, 106.698], [10.773, 106.703]] },
]

/** Returns current road congestion segments (mock data until traffic module API is live) */
export const getCongestionSegments = (): Promise<CongestionSegment[]> =>
  Promise.resolve(CONGESTION_MOCK)

export { getSensors, getCurrentAqi, getAlerts }
