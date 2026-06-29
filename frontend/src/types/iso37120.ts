export type Iso37120Category = 
  | 'ECONOMY'
  | 'EDUCATION'
  | 'ENERGY'
  | 'ENVIRONMENT'
  | 'FINANCE'
  | 'GOVERNANCE'
  | 'HEALTH'
  | 'SAFETY'
  | 'SHELTER';

export interface Iso37120Indicator {
  code: string;
  name: string;
  value?: number | string;
  unit: string;
  dataAvailable: boolean;
  dataSource: string;
  category: Iso37120Category;
  lastUpdated?: string;
}

export interface Iso37120Report {
  year: number;
  indicators: Iso37120Indicator[];
  totalIndicators: number;
  calculatedIndicators: number;
  lastUpdated: string;
}
