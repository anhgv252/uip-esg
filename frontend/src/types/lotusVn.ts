export type LotusLevel = 'PLATINUM' | 'GOLD' | 'SILVER' | 'CERTIFIED' | 'NOT_CERTIFIED';

export type LotusCategoryCode = 'EN' | 'WA' | 'IEQ' | 'MA' | 'ST';

export interface LotusCategory {
  code: LotusCategoryCode;
  name: string;
  score: number;
  maxScore: number;
  percentageScore: number;
}

export interface LotusIndicatorResult {
  code: string;
  name: string;
  actualValue: number | string;
  benchmark: number | string;
  score: number; // 0-4 stars
  dataSource: string;
  unit?: string;
}

export interface LotusVnReport {
  buildingId: string;
  buildingName: string;
  period: string; // YYYY-MM
  overallScore: number; // 0-100
  certificationLevel: LotusLevel;
  categories: LotusCategory[];
  indicators: LotusIndicatorResult[];
  lastUpdated: string;
}

export interface LotusBuilding {
  id: string;
  name: string;
  tenantId: string;
}
