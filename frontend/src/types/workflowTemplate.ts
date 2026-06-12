export interface WorkflowTemplate {
  id: string;
  name: string;
  description: string;
  category: 'FLOOD' | 'AIR_QUALITY' | 'EQUIPMENT' | 'ESG' | 'COMPLAINT';
  icon: string; // MUI icon name as string
  params: TemplateParam[];
  bpmnKey: string; // maps to Camunda process definition key
  tags: string[];
  estimatedDurationMinutes: number;
}

export interface TemplateParam {
  key: string;
  label: string;
  type: 'string' | 'number' | 'boolean' | 'select';
  required: boolean;
  defaultValue?: string | number | boolean;
  options?: string[]; // for select type
  description?: string;
}
