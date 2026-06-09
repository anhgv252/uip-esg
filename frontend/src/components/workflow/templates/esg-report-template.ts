export const ESG_REPORT_TEMPLATE = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  id="EsgReportDefs" targetNamespace="http://uip.vn/bpmn/esg-report">
  <bpmn:process id="EsgReportProcess" name="Monthly ESG Report Generation" isExecutable="true">
    <bpmn:startEvent id="EsgStart" name="Month End Triggered">
      <bpmn:outgoing>flow_collect</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:serviceTask id="CollectMetrics" name="Collect ESG Metrics from ClickHouse">
      <bpmn:incoming>flow_collect</bpmn:incoming>
      <bpmn:outgoing>flow_calc</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="CalculateGri" name="Calculate GRI 302-1 / 305-4 KPIs">
      <bpmn:incoming>flow_calc</bpmn:incoming>
      <bpmn:outgoing>flow_validate</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:exclusiveGateway id="ValidateGate" name="Data Complete?">
      <bpmn:incoming>flow_validate</bpmn:incoming>
      <bpmn:outgoing>flow_complete</bpmn:outgoing>
      <bpmn:outgoing>flow_incomplete</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:userTask id="FillMissing" name="Analyst: Fill Missing Data">
      <bpmn:incoming>flow_incomplete</bpmn:incoming>
      <bpmn:outgoing>flow_refill</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:serviceTask id="GenerateReport" name="Generate PDF / GRI Report">
      <bpmn:incoming>flow_complete</bpmn:incoming>
      <bpmn:incoming>flow_refill</bpmn:incoming>
      <bpmn:outgoing>flow_review</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:userTask id="ReviewReport" name="ESG Officer: Review &amp; Approve">
      <bpmn:incoming>flow_review</bpmn:incoming>
      <bpmn:outgoing>flow_publish</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:serviceTask id="PublishReport" name="Publish to Stakeholders &amp; Archive">
      <bpmn:incoming>flow_publish</bpmn:incoming>
      <bpmn:outgoing>flow_end</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="EsgEnd" name="Report Published"><bpmn:incoming>flow_end</bpmn:incoming></bpmn:endEvent>
    <bpmn:sequenceFlow id="flow_collect" sourceRef="EsgStart" targetRef="CollectMetrics"/>
    <bpmn:sequenceFlow id="flow_calc" sourceRef="CollectMetrics" targetRef="CalculateGri"/>
    <bpmn:sequenceFlow id="flow_validate" sourceRef="CalculateGri" targetRef="ValidateGate"/>
    <bpmn:sequenceFlow id="flow_complete" name="Yes" sourceRef="ValidateGate" targetRef="GenerateReport"/>
    <bpmn:sequenceFlow id="flow_incomplete" name="No" sourceRef="ValidateGate" targetRef="FillMissing"/>
    <bpmn:sequenceFlow id="flow_refill" sourceRef="FillMissing" targetRef="GenerateReport"/>
    <bpmn:sequenceFlow id="flow_review" sourceRef="GenerateReport" targetRef="ReviewReport"/>
    <bpmn:sequenceFlow id="flow_publish" sourceRef="ReviewReport" targetRef="PublishReport"/>
    <bpmn:sequenceFlow id="flow_end" sourceRef="PublishReport" targetRef="EsgEnd"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="EsgDiagram">
    <bpmndi:BPMNPlane bpmnElement="EsgReportProcess">
      <bpmndi:BPMNShape id="EsgStart_di" bpmnElement="EsgStart"><dc:Bounds x="80" y="212" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="CollectMetrics_di" bpmnElement="CollectMetrics"><dc:Bounds x="180" y="190" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="CalculateGri_di" bpmnElement="CalculateGri"><dc:Bounds x="350" y="190" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ValidateGate_di" bpmnElement="ValidateGate" isMarkerVisible="true"><dc:Bounds x="520" y="205" width="50" height="50"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="FillMissing_di" bpmnElement="FillMissing"><dc:Bounds x="640" y="320" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="GenerateReport_di" bpmnElement="GenerateReport"><dc:Bounds x="640" y="90" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ReviewReport_di" bpmnElement="ReviewReport"><dc:Bounds x="810" y="90" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="PublishReport_di" bpmnElement="PublishReport"><dc:Bounds x="980" y="90" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="EsgEnd_di" bpmnElement="EsgEnd"><dc:Bounds x="1150" y="112" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow_collect_di" bpmnElement="flow_collect"><di:waypoint x="116" y="230"/><di:waypoint x="180" y="230"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_calc_di" bpmnElement="flow_calc"><di:waypoint x="280" y="230"/><di:waypoint x="350" y="230"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_validate_di" bpmnElement="flow_validate"><di:waypoint x="450" y="230"/><di:waypoint x="520" y="230"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_complete_di" bpmnElement="flow_complete"><di:waypoint x="545" y="205"/><di:waypoint x="545" y="130"/><di:waypoint x="640" y="130"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_incomplete_di" bpmnElement="flow_incomplete"><di:waypoint x="545" y="255"/><di:waypoint x="545" y="360"/><di:waypoint x="640" y="360"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_refill_di" bpmnElement="flow_refill"><di:waypoint x="690" y="320"/><di:waypoint x="690" y="170"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_review_di" bpmnElement="flow_review"><di:waypoint x="740" y="130"/><di:waypoint x="810" y="130"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_publish_di" bpmnElement="flow_publish"><di:waypoint x="910" y="130"/><di:waypoint x="980" y="130"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_end_di" bpmnElement="flow_end"><di:waypoint x="1080" y="130"/><di:waypoint x="1150" y="130"/></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
