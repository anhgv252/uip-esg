import { Circle, Popup } from 'react-leaflet';
import { Typography } from '@mui/material';

interface FloodAlertPoint {
  id: string;
  sensorId: string;
  latitude: number;
  longitude: number;
  severity: string;
  sensorType: string;
  value: number;
  threshold: number;
  location?: string | null;
}

interface Props {
  alerts: FloodAlertPoint[];
}

const SEVERITY_COLORS: Record<string, string> = {
  CRITICAL: '#c62828',
  HIGH: '#f57c00',
  WARNING: '#1976d2',
};

const SEVERITY_RADII: Record<string, number> = {
  CRITICAL: 500,
  HIGH: 350,
  WARNING: 200,
};

/**
 * Leaflet map overlay showing flood risk zones as color-coded circles.
 * Uses react-leaflet CircleMarker for lightweight rendering.
 */
export default function FloodRiskMapOverlay({ alerts }: Props) {
  if (!alerts || alerts.length === 0) return null;

  return (
    <>
      {alerts.map((alert) => {
        const color = SEVERITY_COLORS[alert.severity] ?? '#1976d2';
        const radius = SEVERITY_RADII[alert.severity] ?? 200;

        return (
          <Circle
            key={alert.id}
            center={[alert.latitude, alert.longitude]}
            radius={radius}
            pathOptions={{
              color,
              fillColor: color,
              fillOpacity: 0.3,
              weight: 2,
            }}
          >
            <Popup>
              <div>
                <Typography variant="subtitle2" fontWeight={700}>
                  {alert.sensorType.replace('_', ' ')}
                </Typography>
                <Typography variant="body2">
                  Value: {alert.value.toFixed(1)} | Threshold: {alert.threshold.toFixed(1)}
                </Typography>
                {alert.location && (
                  <Typography variant="caption">{alert.location}</Typography>
                )}
              </div>
            </Popup>
          </Circle>
        );
      })}
    </>
  );
}
