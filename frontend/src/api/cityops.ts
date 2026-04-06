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

export { getSensors, getCurrentAqi, getAlerts }
