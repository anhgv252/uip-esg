import { MapContainer, TileLayer } from 'react-leaflet'
import 'leaflet/dist/leaflet.css'
import type { SensorWithAqi } from '@/api/cityops'
import SensorMarker from './SensorMarker'

// Fix Leaflet default marker icon path issue with Vite
import L from 'leaflet'
import markerIcon2x from 'leaflet/dist/images/marker-icon-2x.png'
import markerIcon from 'leaflet/dist/images/marker-icon.png'
import markerShadow from 'leaflet/dist/images/marker-shadow.png'
delete (L.Icon.Default.prototype as any)._getIconUrl
L.Icon.Default.mergeOptions({
  iconRetinaUrl: markerIcon2x,
  iconUrl: markerIcon,
  shadowUrl: markerShadow,
})

// HCMC center
const HCMC_CENTER: [number, number] = [10.7769, 106.7009]

interface SensorMapProps {
  sensors: SensorWithAqi[]
  districtFilter: string | null
}

export default function SensorMap({ sensors, districtFilter }: SensorMapProps) {
  const filtered = districtFilter
    ? sensors.filter((s) => s.district === districtFilter)
    : sensors

  return (
    <MapContainer
      center={HCMC_CENTER}
      zoom={12}
      style={{ height: '100%', width: '100%', borderRadius: 8 }}
      scrollWheelZoom
    >
      <TileLayer
        attribution='&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>'
        url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
      />
      {filtered.map((sensor) => (
        <SensorMarker key={sensor.id} sensor={sensor} />
      ))}
    </MapContainer>
  )
}
