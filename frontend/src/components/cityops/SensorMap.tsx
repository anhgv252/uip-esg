import { MapContainer, TileLayer, useMapEvents, Polyline, Tooltip } from 'react-leaflet'
import MarkerClusterGroup from 'react-leaflet-cluster'
import 'leaflet/dist/leaflet.css'
import 'react-leaflet-cluster/dist/assets/MarkerCluster.css'
import 'react-leaflet-cluster/dist/assets/MarkerCluster.Default.css'
import type { SensorWithAqi, CongestionSegment } from '@/api/cityops'
import SensorMarker from './SensorMarker'

// Fix Leaflet default marker icon path issue with Vite
import L from 'leaflet'
// MarkerCluster type: minimal interface for iconCreateFunction callback
interface MarkerCluster { getAllChildMarkers(): L.Marker[]; getChildCount(): number }
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
delete (L.Icon.Default.prototype as any)._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
})

const VIEWPORT_KEY = 'sensormap-viewport'
const HCMC_CENTER: [number, number] = [10.7769, 106.7009]

// AQI colors in descending severity order (worst first)
const AQI_COLOR_PRIORITY = ['#7e0023', '#8f3f97', '#ff0000', '#ff7e00', '#ffff00', '#00e400', '#1976d2', '#9e9e9e']

function getClusterIcon(cluster: MarkerCluster): L.DivIcon {
  const markers = cluster.getAllChildMarkers()
  let worstIdx = AQI_COLOR_PRIORITY.length - 1
  for (const m of markers) {
    const color = ((m.options as Record<string, unknown>).fillColor as string | undefined ?? '#9e9e9e').toLowerCase()
    const idx = AQI_COLOR_PRIORITY.indexOf(color)
    if (idx !== -1 && idx < worstIdx) worstIdx = idx
  }
  const color = AQI_COLOR_PRIORITY[worstIdx]
  const count = cluster.getChildCount()
  const textColor = color === '#ffff00' ? '#333' : '#fff'
  return L.divIcon({
    html: `<div style="background:${color};color:${textColor};border:2.5px solid rgba(255,255,255,0.85);border-radius:50%;width:38px;height:38px;display:flex;align-items:center;justify-content:center;font-size:13px;font-weight:700;box-shadow:0 2px 8px rgba(0,0,0,0.45)">${count}</div>`,
    className: '',
    iconSize: [38, 38],
    iconAnchor: [19, 19],
  })
}

function getSavedViewport(): { center: [number, number]; zoom: number } {
  try {
    const raw = sessionStorage.getItem(VIEWPORT_KEY)
    if (raw) {
      const { lat, lng, zoom } = JSON.parse(raw)
      return { center: [lat, lng], zoom }
    }
  } catch {
    // ignore
  }
  return { center: HCMC_CENTER, zoom: 12 }
}

function ViewportPersistor() {
  const map = useMapEvents({
    moveend: () => {
      const { lat, lng } = map.getCenter()
      sessionStorage.setItem(
        VIEWPORT_KEY,
        JSON.stringify({ lat, lng, zoom: map.getZoom() }),
      )
    },
  })
  return null
}

const CONGESTION_COLORS: Record<string, string> = {
  FREE: '#4caf50',
  MODERATE: '#ff9800',
  HEAVY: '#f44336',
  STANDSTILL: '#b71c1c',
}

function TrafficLayer({ segments }: { segments: CongestionSegment[] }) {
  return (
    <>
      {segments.map((seg) => (
        <Polyline
          key={seg.id}
          positions={seg.positions}
          pathOptions={{ color: CONGESTION_COLORS[seg.level] ?? '#9e9e9e', weight: 5, opacity: 0.8 }}
        >
          <Tooltip sticky>{seg.name}: {seg.level}</Tooltip>
        </Polyline>
      ))}
    </>
  )
}

interface SensorMapProps {
  sensors: SensorWithAqi[]
  districtFilter: string | null
  congestionSegments?: CongestionSegment[]
  showTraffic?: boolean
}

export default function SensorMap({ sensors, districtFilter, congestionSegments = [], showTraffic = false }: SensorMapProps) {
  const filtered = districtFilter
    ? sensors.filter((s) => s.district === districtFilter)
    : sensors

  const { center, zoom } = getSavedViewport()

  return (
    <MapContainer
      center={center}
      zoom={zoom}
      style={{ height: '100%', width: '100%', borderRadius: 8 }}
      scrollWheelZoom
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      <ViewportPersistor />
      {showTraffic && <TrafficLayer segments={congestionSegments} />}
      <MarkerClusterGroup chunkedLoading iconCreateFunction={getClusterIcon}>
        {filtered.map((sensor) => (
          <SensorMarker key={sensor.id} sensor={sensor} />
        ))}
      </MarkerClusterGroup>
    </MapContainer>
  )
}
