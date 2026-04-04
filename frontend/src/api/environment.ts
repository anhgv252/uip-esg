import { apiClient } from './client';

export interface Sensor {
  id: string;
  sensorCode: string;
  name: string;
  district: string;
  lat: number;
  lng: number;
  type: string;
  status: 'ONLINE' | 'OFFLINE' | 'MAINTENANCE';
  lastSeenAt: string | null;
}

export interface SensorReading {
  sensorId: number;
  timestamp: string;
  pm25: number | null;
  pm10: number | null;
  no2: number | null;
  o3: number | null;
  so2: number | null;
  co: number | null;
  temperature: number | null;
  humidity: number | null;
  aqi: number | null;
}

export interface AqiResponse {
  sensorId: string;
  sensorName: string;
  district: string;
  aqi: number;
  category: string;
  color: string;
  dominantPollutant: string;
  pm25: number | null;
  pm10: number | null;
  no2: number | null;
  o3: number | null;
  calculatedAt: string;
}

export interface AqiHistory {
  sensorId: string;
  timestamp: string;
  aqi: number;
  category: string;
}

interface SensorApiResponse {
  id: string;
  sensorId: string;
  sensorName: string;
  sensorType: string;
  districtCode: string | null;
  latitude: number | null;
  longitude: number | null;
  status: 'ONLINE' | 'OFFLINE' | 'MAINTENANCE';
  lastSeenAt: string | number | null;
}

interface AqiApiResponse {
  sensorId: string;
  timestamp: string | number;
  aqiValue: number;
  category: string;
  color: string;
  districtCode: string | null;
  pm25: number | null;
  pm10: number | null;
  no2: number | null;
  o3: number | null;
  so2: number | null;
  co: number | null;
}

function normalizeInstant(value: string | number | null | undefined): string | null {
  if (value == null) return null;
  if (typeof value === 'string') return value;
  // Backend serializes Instant as epoch-seconds with fractional part.
  return new Date(value * 1000).toISOString();
}

function dominantPollutantOf(reading: AqiApiResponse): string {
  const pollutants = [
    { key: 'PM2.5', value: reading.pm25 },
    { key: 'PM10', value: reading.pm10 },
    { key: 'NO2', value: reading.no2 },
    { key: 'O3', value: reading.o3 },
    { key: 'SO2', value: reading.so2 },
    { key: 'CO', value: reading.co },
  ];
  return pollutants
    .filter((item) => item.value != null)
    .sort((left, right) => (right.value ?? 0) - (left.value ?? 0))[0]?.key ?? 'N/A';
}

function mapSensor(sensor: SensorApiResponse): Sensor {
  return {
    id: sensor.id,
    sensorCode: sensor.sensorId,
    name: sensor.sensorName,
    district: sensor.districtCode ?? 'Unknown',
    lat: sensor.latitude ?? 0,
    lng: sensor.longitude ?? 0,
    type: sensor.sensorType,
    status: sensor.status,
    lastSeenAt: normalizeInstant(sensor.lastSeenAt),
  };
}

function mapAqi(reading: AqiApiResponse): AqiResponse {
  return {
    sensorId: reading.sensorId,
    sensorName: reading.sensorId,
    district: reading.districtCode ?? 'Unknown',
    aqi: reading.aqiValue,
    category: reading.category,
    color: reading.color,
    dominantPollutant: dominantPollutantOf(reading),
    pm25: reading.pm25,
    pm10: reading.pm10,
    no2: reading.no2,
    o3: reading.o3,
    calculatedAt: normalizeInstant(reading.timestamp) ?? new Date().toISOString(),
  };
}

export const getSensors = () =>
  apiClient.get<SensorApiResponse[]>('/environment/sensors').then((r) => r.data.map(mapSensor));

export const getSensorReadings = (sensorId: number, from?: string, to?: string) =>
  apiClient
    .get<SensorReading[]>(`/environment/sensors/${sensorId}/readings`, {
      params: { from, to },
    })
    .then((r) => r.data);

export const getCurrentAqi = () =>
  apiClient.get<AqiApiResponse[]>('/environment/aqi/current').then((r) => r.data.map(mapAqi));

export const getAqiHistory = (district: string, period = '24h') =>
  apiClient
    .get<AqiApiResponse[]>('/environment/aqi/history', {
      params: { district, period },
    })
    .then((r) => r.data.map((item) => ({
      sensorId: item.sensorId,
      timestamp: normalizeInstant(item.timestamp) ?? new Date().toISOString(),
      aqi: item.aqiValue,
      category: item.category,
    })));
