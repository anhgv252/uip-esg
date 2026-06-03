import { CircleMarker, Popup } from 'react-leaflet'
import { Typography } from '@mui/material'
import { useNavigate } from 'react-router-dom'

export interface BuildingSafetyPoint {
  buildingId: string
  buildingName: string
  lat: number
  lng: number
  score: number              // 0-100
  status: 'SAFE' | 'WARNING' | 'CRITICAL' | 'OFFLINE'
  activeAlerts: number
}

// Safety status → map marker color (matches SafetyScoreGauge zones)
const STATUS_COLORS: Record<BuildingSafetyPoint['status'], string> = {
  SAFE:     '#22C55E',
  WARNING:  '#F59E0B',
  CRITICAL: '#EF4444',
  OFFLINE:  '#9CA3AF',
}

interface BuildingSafetyMapOverlayProps {
  buildings: BuildingSafetyPoint[]
}

/**
 * Leaflet map overlay showing building safety status as colored circle markers.
 * Color-coded by status: green=SAFE, amber=WARNING, red=CRITICAL, gray=OFFLINE.
 * Click navigates to /buildings/:id?tab=safety.
 *
 * Usage: place inside a <MapContainer> alongside other overlays.
 * Building lat/lng must be provided by the parent (not in buildings API yet — Sprint 7 scope).
 */
export function BuildingSafetyMapOverlay({ buildings }: BuildingSafetyMapOverlayProps) {
  const navigate = useNavigate()

  return (
    <>
      {buildings.map(building => {
        const color = STATUS_COLORS[building.status]
        const isCritical = building.status === 'CRITICAL'
        return (
          <CircleMarker
            key={building.buildingId}
            center={[building.lat, building.lng]}
            radius={isCritical ? 14 : 10}
            pathOptions={{
              color,
              fillColor: color,
              fillOpacity: 0.85,
              weight: isCritical ? 3 : 2,
            }}
            eventHandlers={{
              click: () => navigate(`/buildings/${building.buildingId}?tab=safety`),
            }}
          >
            <Popup>
              <div style={{ minWidth: 160 }}>
                <Typography variant="subtitle2" fontWeight={700}>
                  {building.buildingName}
                </Typography>
                <Typography
                  variant="body2"
                  sx={{ color, fontWeight: 600, mt: 0.5 }}
                >
                  {building.status} — Score: {building.score}
                </Typography>
                {building.activeAlerts > 0 && (
                  <Typography variant="caption" color="error">
                    {building.activeAlerts} cảnh báo đang mở
                  </Typography>
                )}
                {isCritical && (
                  <Typography
                    variant="caption"
                    display="block"
                    sx={{ mt: 0.5, color: '#EF4444', fontWeight: 600 }}
                  >
                    ⚠ Yêu cầu xem xét ngay
                  </Typography>
                )}
              </div>
            </Popup>
          </CircleMarker>
        )
      })}
    </>
  )
}
