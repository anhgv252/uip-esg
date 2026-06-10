/**
 * Building Safety Workflow Template
 *
 * Vibration anomaly detected → AI classifies severity → Operator review → Safety assessment → Action
 */
export const BUILDING_SAFETY_TEMPLATE = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  id="Definitions_BuildingSafety" targetNamespace="http://bpmn.io/schema/bpmn">
  <bpmn:process id="BuildingSafetyProcess" name="Building Safety Alert Workflow" isExecutable="true">
    <bpmn:startEvent id="Start_SensorTrigger" name="Vibration Anomaly Detected">
      <bpmn:outgoing>Flow1</bpmn:outgoing>
    </bpmn:startEvent>

    <bpmn:serviceTask id="Task_FetchSensorData" name="Fetch Sensor Data (Flink CEP)">
      <bpmn:incoming>Flow1</bpmn:incoming>
      <bpmn:outgoing>Flow2</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:serviceTask id="Task_AISeverityClassify" name="AI: Classify Severity (Welford)">
      <bpmn:incoming>Flow2</bpmn:incoming>
      <bpmn:outgoing>Flow3</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:exclusiveGateway id="Gateway_SeverityCheck" name="Severity Level?">
      <bpmn:incoming>Flow3</bpmn:incoming>
      <bpmn:outgoing>Flow4_Warning</bpmn:outgoing>
      <bpmn:outgoing>Flow4_Critical</bpmn:outgoing>
    </bpmn:exclusiveGateway>

    <bpmn:userTask id="Task_OperatorReview" name="Operator Review (P0 Alert)">
      <bpmn:incoming>Flow4_Critical</bpmn:incoming>
      <bpmn:outgoing>Flow5</bpmn:outgoing>
    </bpmn:userTask>

    <bpmn:serviceTask id="Task_SafetyAssessment" name="Safety Assessment (TCVN 9386)">
      <bpmn:incoming>Flow5</bpmn:incoming>
      <bpmn:outgoing>Flow6</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:exclusiveGateway id="Gateway_ActionDecision" name="Action Required?">
      <bpmn:incoming>Flow6</bpmn:incoming>
      <bpmn:outgoing>Flow7_Evacuate</bpmn:outgoing>
      <bpmn:outgoing>Flow7_Monitor</bpmn:outgoing>
    </bpmn:exclusiveGateway>

    <bpmn:serviceTask id="Task_EvacuationOrder" name="Issue Evacuation Order">
      <bpmn:incoming>Flow7_Evacuate</bpmn:incoming>
      <bpmn:outgoing>Flow8</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:serviceTask id="Task_ContinueMonitor" name="Continue Monitoring (24h)">
      <bpmn:incoming>Flow4_Warning</bpmn:incoming>
      <bpmn:incoming>Flow7_Monitor</bpmn:incoming>
      <bpmn:outgoing>Flow9</bpmn:outgoing>
    </bpmn:serviceTask>

    <bpmn:endEvent id="End_Resolved" name="Safety Alert Resolved">
      <bpmn:incoming>Flow8</bpmn:incoming>
      <bpmn:incoming>Flow9</bpmn:incoming>
    </bpmn:endEvent>

    <bpmn:sequenceFlow id="Flow1" sourceRef="Start_SensorTrigger" targetRef="Task_FetchSensorData" />
    <bpmn:sequenceFlow id="Flow2" sourceRef="Task_FetchSensorData" targetRef="Task_AISeverityClassify" />
    <bpmn:sequenceFlow id="Flow3" sourceRef="Task_AISeverityClassify" targetRef="Gateway_SeverityCheck" />
    <bpmn:sequenceFlow id="Flow4_Warning" name="Warning" sourceRef="Gateway_SeverityCheck" targetRef="Task_ContinueMonitor" />
    <bpmn:sequenceFlow id="Flow4_Critical" name="Critical" sourceRef="Gateway_SeverityCheck" targetRef="Task_OperatorReview" />
    <bpmn:sequenceFlow id="Flow5" sourceRef="Task_OperatorReview" targetRef="Task_SafetyAssessment" />
    <bpmn:sequenceFlow id="Flow6" sourceRef="Task_SafetyAssessment" targetRef="Gateway_ActionDecision" />
    <bpmn:sequenceFlow id="Flow7_Evacuate" name="Evacuate" sourceRef="Gateway_ActionDecision" targetRef="Task_EvacuationOrder" />
    <bpmn:sequenceFlow id="Flow7_Monitor" name="Monitor" sourceRef="Gateway_ActionDecision" targetRef="Task_ContinueMonitor" />
    <bpmn:sequenceFlow id="Flow8" sourceRef="Task_EvacuationOrder" targetRef="End_Resolved" />
    <bpmn:sequenceFlow id="Flow9" sourceRef="Task_ContinueMonitor" targetRef="End_Resolved" />
  </bpmn:process>

  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="BuildingSafetyProcess">
      <bpmndi:BPMNShape id="_BPMNShape_Start" bpmnElement="Start_SensorTrigger">
        <dc:Bounds x="179" y="259" width="36" height="36" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="_BPMNShape_Task1" bpmnElement="Task_FetchSensorData">
        <dc:Bounds x="270" y="237" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="_BPMNShape_Task2" bpmnElement="Task_AISeverityClassify">
        <dc:Bounds x="430" y="237" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="_BPMNShape_GW1" bpmnElement="Gateway_SeverityCheck" isMarkerVisible="true">
        <dc:Bounds x="595" y="252" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="_BPMNShape_Task3" bpmnElement="Task_OperatorReview">
        <dc:Bounds x="700" y="160" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="_BPMNShape_Task4" bpmnElement="Task_SafetyAssessment">
        <dc:Bounds x="870" y="160" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="_BPMNShape_GW2" bpmnElement="Gateway_ActionDecision" isMarkerVisible="true">
        <dc:Bounds x="1035" y="175" width="50" height="50" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="_BPMNShape_Task5" bpmnElement="Task_EvacuationOrder">
        <dc:Bounds x="1140" y="100" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="_BPMNShape_Task6" bpmnElement="Task_ContinueMonitor">
        <dc:Bounds x="700" y="340" width="100" height="80" />
      </bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="_BPMNShape_End" bpmnElement="End_Resolved">
        <dc:Bounds x="1312" y="262" width="36" height="36" />
      </bpmndi:BPMNShape>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
