import { useQuery } from '@tanstack/react-query';
import { getEnergyForecast } from '@/api/forecast';

export function useEnergyForecast(buildingId: string | undefined, horizonDays = 30) {
  return useQuery({
    queryKey: ['forecast', 'energy', buildingId, horizonDays],
    queryFn: () => getEnergyForecast(buildingId!, horizonDays),
    enabled: !!buildingId,
    staleTime: 15 * 60 * 1000, // 15 min — matches Python cache TTL
    retry: 1,
  });
}
