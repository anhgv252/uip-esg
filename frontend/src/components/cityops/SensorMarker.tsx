import { CircleMarker, Popup } from 'react-leaflet'
import type { SensorWithAqi } from '@/api/cityops'

const AQI_COLORS: Record<string, string> = {
  Good: '#00E400',
  Moderate: '#FFFF00',
  'Unhealthy for Sensitive Groups': '#FF7E00',
  Unhealthy: '#FF0000',
  'Very Unhealthy': '#8F3F97',
  Hazardous: '#7E0023',
}

function getMarkerColor(sensor: SensorWithAqi): string {
  if (sensor.status !== 'ONLINE') return '#9E9E9E'
  if (sensor.aqiCategory && AQI_COLORS[sensor.aqiCategory]) {
    return AQI_COLORS[sensor.aqiCategory]
  }
  if (sensor.aqi !== undefined) {
    if (sensor.aqi <= 50) return AQI_COLORS.Good
    if (sensor.aqi <= 100) return AQI_COLORS.Moderate
    if (sensor.aqi <= 150) return AQI_COLORS['Unhealthy for Sensitive Groups']
    if (sensor.aqi <= 200) return AQI_COLORS.Unhealthy
    if (sensor.aqi <= 300) return AQI_COLORS['Very Unhealthy']
    return AQI_COLORS.Hazardous
  }
  return '#1976D2'
}

interface SensorMarkerProps {
  sensor: SensorWithAqi
}

export default function SensorMarker({ sensor }: SensorMarkerProps) {
  const color = getMarkerColor(sensor)

  return (
    <CircleMarker
      center={[sensor.lat, sensor.lng]}
      radius={sensor.status === 'ONLINE' ? 10 : 7}
      pathOptions={{
        color,
        fillColor: color,
        fillOpacity: 0.85,
        weight: 2,
      }}
    >
      <Popup>
        <div style={{ minWidth: 180 }}>
          <strong>{sensor.name}</strong>
          <br />
          <span style={{ color: '#666' }}>{sensor.district}</span>
          <br />
          <span
            style={{
              display: 'inline-block',
              marginTop: 4,
              padding: '2px 8px',
              borderRadius: 4,
              background: sensor.status === 'ONLINE' ? '#e8f5e9' : '#fafafa',
              color: sensor.status === 'ONLINE' ? '#2e7d32' : '#666',
              fontSize: 12,
            }}
          >
            {sensor.status}
          </span>
          {sensor.aqi !== undefined && (
            <>
              <br />
              <span style={{ marginTop: 4, display: 'inline-block', fontSize: 13 }}>
                AQI:{' '}
                <strong style={{ color }}>{sensor.aqi}</strong>
                {sensor.aqiCategory && (
                  <span style={{ marginLeft: 4, color: '#666' }}>
                    ({sensor.aqiCategory})
                  </span>
                )}
              </span>
            </>
          )}
        </div>
      </Popup>
    </CircleMarker>
  )
}
