-- S4-10: Workflow Trigger Configuration Engine
-- Stores trigger configs for data-driven workflow triggering (replaces hardcoded Java trigger classes)

CREATE SCHEMA IF NOT EXISTS workflow;

CREATE TABLE workflow.trigger_config (
    id                    SERIAL PRIMARY KEY,
    scenario_key          VARCHAR(100) NOT NULL UNIQUE,
    process_key           VARCHAR(100) NOT NULL,
    display_name          VARCHAR(255) NOT NULL,
    description           TEXT,

    trigger_type          VARCHAR(20)  NOT NULL,  -- KAFKA | SCHEDULED | REST

    kafka_topic           VARCHAR(255),
    kafka_consumer_group  VARCHAR(255),

    filter_conditions     JSONB,
    variable_mapping      JSONB NOT NULL,

    schedule_cron         VARCHAR(100),
    schedule_query_bean   VARCHAR(255),

    prompt_template_path  VARCHAR(255),
    ai_confidence_threshold DECIMAL(3,2) DEFAULT 0.85,

    deduplication_key     VARCHAR(100),

    enabled               BOOLEAN DEFAULT true,
    created_at            TIMESTAMP DEFAULT NOW(),
    updated_at            TIMESTAMP DEFAULT NOW(),
    updated_by            VARCHAR(100)
);

-- Seed data: 7 existing AI scenarios

-- AI-C01: AQI Citizen Alert (Kafka trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, kafka_topic, kafka_consumer_group, filter_conditions, variable_mapping, prompt_template_path, deduplication_key, enabled)
VALUES (
  'aiC01_aqiCitizenAlert', 'aiC01_aqiCitizenAlert', 'Cảnh báo AQI cho cư dân',
  'KAFKA', 'UIP.flink.alert.detected.v1', 'uip-workflow-generic',
  '[{"field":"module","op":"EQ","value":"ENVIRONMENT"},{"field":"measureType","op":"EQ","value":"AQI"},{"field":"value","op":"GT","value":150.0}]'::jsonb,
  '{"sensorId":{"source":"payload.sensorId","default":"UNKNOWN"},"aqiValue":{"source":"payload.value"},"districtCode":{"source":"payload.districtCode","default":"UNKNOWN"},"measuredAt":{"source":"payload.detectedAt","default":"NOW()"},"scenarioKey":{"static":"aiC01_aqiCitizenAlert"}}'::jsonb,
  'prompts/aiC01_aqiCitizenAlert.txt', 'sensorId', true
);

-- AI-C03: Flood Emergency Evacuation (Kafka trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, kafka_topic, kafka_consumer_group, filter_conditions, variable_mapping, prompt_template_path, enabled)
VALUES (
  'aiC03_floodEmergencyEvacuation', 'aiC03_floodEmergencyEvacuation', 'Cảnh báo khẩn cấp & sơ tán lũ',
  'KAFKA', 'UIP.flink.alert.detected.v1', 'uip-workflow-generic',
  '[{"field":"measureType","op":"EQ","value":"WATER_LEVEL"},{"field":"value","op":"GT","value":3.5}]'::jsonb,
  '{"waterLevel":{"source":"payload.value"},"sensorLocation":{"source":"payload.sensorId","default":"UNKNOWN"},"warningZones":{"source":"payload.districtCode","default":"UNKNOWN"},"detectedAt":{"source":"payload.detectedAt","default":"NOW()"},"scenarioKey":{"static":"aiC03_floodEmergencyEvacuation"}}'::jsonb,
  'prompts/aiC03_floodEmergencyEvacuation.txt', true
);

-- AI-M01: Flood Response Coordination (Kafka trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, kafka_topic, kafka_consumer_group, filter_conditions, variable_mapping, prompt_template_path, enabled)
VALUES (
  'aiM01_floodResponseCoordination', 'aiM01_floodResponseCoordination', 'Phối hợp phản ứng lũ',
  'KAFKA', 'UIP.flink.alert.detected.v1', 'uip-workflow-generic',
  '[{"field":"measureType","op":"IN","value":["WATER_LEVEL"]},{"field":"value","op":"GT","value":3.5}]'::jsonb,
  '{"scenarioKey":{"static":"aiM01_floodResponseCoordination"},"alertId":{"source":"payload.alertId","default":"UUID()"},"waterLevel":{"source":"payload.value"},"location":{"source":"payload.sensorId","default":"UNKNOWN"},"affectedZones":{"source":"payload.districtCode","default":"UNKNOWN"}}'::jsonb,
  'prompts/aiM01_floodResponseCoordination.txt', true
);

-- AI-M02: AQI Traffic Control (Kafka trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, kafka_topic, kafka_consumer_group, filter_conditions, variable_mapping, prompt_template_path, enabled)
VALUES (
  'aiM02_aqiTrafficControl', 'aiM02_aqiTrafficControl', 'Kiểm soát giao thông khi AQI cao',
  'KAFKA', 'UIP.flink.alert.detected.v1', 'uip-workflow-generic',
  '[{"field":"module","op":"EQ","value":"ENVIRONMENT"},{"field":"measureType","op":"EQ","value":"AQI"},{"field":"value","op":"GT","value":150.0}]'::jsonb,
  '{"scenarioKey":{"static":"aiM02_aqiTrafficControl"},"sensorId":{"source":"payload.sensorId","default":"UNKNOWN"},"aqiValue":{"source":"payload.value"},"pollutants":{"source":"payload.measureType","default":"AQI"},"affectedDistricts":{"source":"payload.districtCode","default":"UNKNOWN"}}'::jsonb,
  'prompts/aiM02_aqiTrafficControl.txt', true
);

-- AI-C02: Citizen Service Request (REST trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, variable_mapping, prompt_template_path, enabled)
VALUES (
  'aiC02_citizenServiceRequest', 'aiC02_citizenServiceRequest', 'Xử lý yêu cầu dịch vụ',
  'REST',
  '{"scenarioKey":{"static":"aiC02_citizenServiceRequest"},"citizenId":{"source":"payload.citizenId"},"requestId":{"source":"payload.requestId","default":"UUID()"},"requestType":{"source":"payload.requestType"},"description":{"source":"payload.description"},"district":{"source":"payload.district","default":"UNKNOWN"}}'::jsonb,
  'prompts/aiC02_citizenServiceRequest.txt', true
);

-- AI-M03: Utility Incident (Scheduled trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, schedule_cron, schedule_query_bean, variable_mapping, prompt_template_path, deduplication_key, enabled)
VALUES (
  'aiM03_utilityIncidentCoordination', 'aiM03_utilityIncidentCoordination', 'Phối hợp sự cố tiện ích',
  'SCHEDULED', '0 */2 * * *', 'esgService.detectUtilityAnomalies',
  '{"scenarioKey":{"static":"aiM03_utilityIncidentCoordination"},"metricType":{"source":"anomaly.metricType"},"anomalyValue":{"source":"anomaly.currentValue"},"buildingId":{"source":"anomaly.buildingId"},"detectedAt":{"source":"anomaly.detectedAt","default":"NOW()"}}'::jsonb,
  'prompts/aiM03_utilityIncidentCoordination.txt', 'buildingId', true
);

-- AI-M04: ESG Anomaly (Scheduled trigger)
INSERT INTO workflow.trigger_config
  (scenario_key, process_key, display_name, trigger_type, schedule_cron, schedule_query_bean, variable_mapping, prompt_template_path, deduplication_key, enabled)
VALUES (
  'aiM04_esgAnomalyInvestigation', 'aiM04_esgAnomalyInvestigation', 'Điều tra bất thường ESG',
  'SCHEDULED', '0 */2 * * *', 'esgService.detectEsgAnomalies',
  '{"scenarioKey":{"static":"aiM04_esgAnomalyInvestigation"},"metricType":{"source":"anomaly.metricType"},"currentValue":{"source":"anomaly.currentValue"},"historicalAvg":{"source":"anomaly.historicalAvg"},"period":{"source":"anomaly.period"}}'::jsonb,
  'prompts/aiM04_esgAnomalyInvestigation.txt', 'metricType', true
);
