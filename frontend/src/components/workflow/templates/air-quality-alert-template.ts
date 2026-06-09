export const AIR_QUALITY_ALERT_TEMPLATE = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  id="AirQualityDefs" targetNamespace="http://uip.vn/bpmn/air-quality">
  <bpmn:process id="AirQualityProcess" name="Air Quality Alert Workflow" isExecutable="true">
    <bpmn:startEvent id="AqiStart" name="AQI Reading Received">
      <bpmn:outgoing>flow_aqi_classify</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="ClassifyAqi" name="AI: Classify AQI Level">
      <bpmn:incoming>flow_aqi_classify</bpmn:incoming>
      <bpmn:outgoing>flow_aqi_gate</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:exclusiveGateway id="AqiGateway" name="AQI Severity?">
      <bpmn:incoming>flow_aqi_gate</bpmn:incoming>
      <bpmn:outgoing>flow_good</bpmn:outgoing>
      <bpmn:outgoing>flow_moderate</bpmn:outgoing>
      <bpmn:outgoing>flow_hazardous</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:serviceTask id="LogGood" name="Log: Normal Reading">
      <bpmn:incoming>flow_good</bpmn:incoming>
      <bpmn:outgoing>flow_end_good</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="NotifyModerate" name="Push: Moderate Warning to Citizens">
      <bpmn:incoming>flow_moderate</bpmn:incoming>
      <bpmn:outgoing>flow_end_moderate</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="BroadcastHazardous" name="Broadcast: Hazardous Emergency Alert">
      <bpmn:incoming>flow_hazardous</bpmn:incoming>
      <bpmn:outgoing>flow_restrict</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:userTask id="EnforceRestrictions" name="Operator: Enforce Traffic Restrictions">
      <bpmn:incoming>flow_restrict</bpmn:incoming>
      <bpmn:outgoing>flow_end_hazardous</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:endEvent id="AqiEndGood" name="Logged"><bpmn:incoming>flow_end_good</bpmn:incoming></bpmn:endEvent>
    <bpmn:endEvent id="AqiEndModerate" name="Warning Sent"><bpmn:incoming>flow_end_moderate</bpmn:incoming></bpmn:endEvent>
    <bpmn:endEvent id="AqiEndHazardous" name="Restrictions Active"><bpmn:incoming>flow_end_hazardous</bpmn:incoming></bpmn:endEvent>
    <bpmn:sequenceFlow id="flow_aqi_classify" sourceRef="AqiStart" targetRef="ClassifyAqi"/>
    <bpmn:sequenceFlow id="flow_aqi_gate" sourceRef="ClassifyAqi" targetRef="AqiGateway"/>
    <bpmn:sequenceFlow id="flow_good" name="Good/Moderate" sourceRef="AqiGateway" targetRef="LogGood"/>
    <bpmn:sequenceFlow id="flow_moderate" name="Unhealthy" sourceRef="AqiGateway" targetRef="NotifyModerate"/>
    <bpmn:sequenceFlow id="flow_hazardous" name="Hazardous" sourceRef="AqiGateway" targetRef="BroadcastHazardous"/>
    <bpmn:sequenceFlow id="flow_restrict" sourceRef="BroadcastHazardous" targetRef="EnforceRestrictions"/>
    <bpmn:sequenceFlow id="flow_end_good" sourceRef="LogGood" targetRef="AqiEndGood"/>
    <bpmn:sequenceFlow id="flow_end_moderate" sourceRef="NotifyModerate" targetRef="AqiEndModerate"/>
    <bpmn:sequenceFlow id="flow_end_hazardous" sourceRef="EnforceRestrictions" targetRef="AqiEndHazardous"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="AqiDiagram">
    <bpmndi:BPMNPlane bpmnElement="AirQualityProcess">
      <bpmndi:BPMNShape id="AqiStart_di" bpmnElement="AqiStart"><dc:Bounds x="80" y="172" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ClassifyAqi_di" bpmnElement="ClassifyAqi"><dc:Bounds x="180" y="150" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="AqiGateway_di" bpmnElement="AqiGateway" isMarkerVisible="true"><dc:Bounds x="350" y="165" width="50" height="50"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="LogGood_di" bpmnElement="LogGood"><dc:Bounds x="470" y="60" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="NotifyModerate_di" bpmnElement="NotifyModerate"><dc:Bounds x="470" y="190" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="BroadcastHazardous_di" bpmnElement="BroadcastHazardous"><dc:Bounds x="470" y="320" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EnforceRestrictions_di" bpmnElement="EnforceRestrictions"><dc:Bounds x="640" y="320" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="AqiEndGood_di" bpmnElement="AqiEndGood"><dc:Bounds x="640" y="82" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="AqiEndModerate_di" bpmnElement="AqiEndModerate"><dc:Bounds x="640" y="212" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="AqiEndHazardous_di" bpmnElement="AqiEndHazardous"><dc:Bounds x="810" y="342" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow_aqi_classify_di" bpmnElement="flow_aqi_classify"><di:waypoint x="116" y="190"/><di:waypoint x="180" y="190"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_aqi_gate_di" bpmnElement="flow_aqi_gate"><di:waypoint x="280" y="190"/><di:waypoint x="350" y="190"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_good_di" bpmnElement="flow_good"><di:waypoint x="375" y="165"/><di:waypoint x="375" y="100"/><di:waypoint x="470" y="100"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_moderate_di" bpmnElement="flow_moderate"><di:waypoint x="400" y="190"/><di:waypoint x="470" y="230"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_hazardous_di" bpmnElement="flow_hazardous"><di:waypoint x="375" y="215"/><di:waypoint x="375" y="360"/><di:waypoint x="470" y="360"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_restrict_di" bpmnElement="flow_restrict"><di:waypoint x="570" y="360"/><di:waypoint x="640" y="360"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_end_good_di" bpmnElement="flow_end_good"><di:waypoint x="570" y="100"/><di:waypoint x="640" y="100"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_end_moderate_di" bpmnElement="flow_end_moderate"><di:waypoint x="570" y="230"/><di:waypoint x="640" y="230"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_end_hazardous_di" bpmnElement="flow_end_hazardous"><di:waypoint x="740" y="360"/><di:waypoint x="810" y="360"/></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
