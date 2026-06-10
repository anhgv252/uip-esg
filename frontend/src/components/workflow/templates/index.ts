import { FLOOD_ALERT_TEMPLATE } from './flood-alert-template'
import { AIR_QUALITY_ALERT_TEMPLATE } from './air-quality-alert-template'
import { ESG_REPORT_TEMPLATE } from './esg-report-template'
import { BUILDING_SAFETY_TEMPLATE } from './building-safety-template'

export interface WorkflowTemplate {
  id: string
  name: string
  description: string
  category: 'emergency' | 'environmental' | 'reporting' | 'safety'
  xml: string
}

export const WORKFLOW_TEMPLATES: WorkflowTemplate[] = [
  {
    id: 'flood-alert',
    name: 'Flood Alert Response',
    description: 'Water level sensor triggers emergency alert → authority notification → operator response activation',
    category: 'emergency',
    xml: FLOOD_ALERT_TEMPLATE,
  },
  {
    id: 'air-quality-alert',
    name: 'Air Quality Alert',
    description: 'AI classifies AQI severity → citizen push notifications or hazardous emergency broadcast',
    category: 'environmental',
    xml: AIR_QUALITY_ALERT_TEMPLATE,
  },
  {
    id: 'building-safety',
    name: 'Building Safety Alert',
    description: 'Vibration anomaly → AI severity classify (Welford) → Operator review → TCVN 9386 assessment → Action/Evacuation',
    category: 'safety',
    xml: BUILDING_SAFETY_TEMPLATE,
  },
  {
    id: 'esg-monthly-report',
    name: 'Monthly ESG Report',
    description: 'Auto-collect GRI 302-1/305-4 metrics → validate → generate PDF → officer review → publish',
    category: 'reporting',
    xml: ESG_REPORT_TEMPLATE,
  },
]

/** Category labels for UI display */
export const TEMPLATE_CATEGORY_LABELS: Record<WorkflowTemplate['category'], string> = {
  emergency: '🚨 Khẩn cấp',
  environmental: '🌍 Môi trường',
  safety: '🏗️ An toàn công trình',
  reporting: '📊 Báo cáo',
}

export { FLOOD_ALERT_TEMPLATE, AIR_QUALITY_ALERT_TEMPLATE, ESG_REPORT_TEMPLATE, BUILDING_SAFETY_TEMPLATE }
