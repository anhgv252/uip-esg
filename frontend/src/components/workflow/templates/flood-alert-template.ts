export const FLOOD_ALERT_TEMPLATE = `<?xml version="1.0" encoding="UTF-8"?>
<bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                  xmlns:di="http://www.omg.org/spec/DD/20100524/DI"
                  id="FloodAlertDefs" targetNamespace="http://uip.vn/bpmn/flood-alert">
  <bpmn:process id="FloodAlertProcess" name="Flood Alert Workflow" isExecutable="true">
    <bpmn:startEvent id="FloodStart" name="Water Level Sensor Triggered">
      <bpmn:outgoing>flow_start_check</bpmn:outgoing>
    </bpmn:startEvent>
    <bpmn:exclusiveGateway id="CheckLevel" name="Level &gt; Threshold?">
      <bpmn:incoming>flow_start_check</bpmn:incoming>
      <bpmn:outgoing>flow_alert</bpmn:outgoing>
      <bpmn:outgoing>flow_monitor</bpmn:outgoing>
    </bpmn:exclusiveGateway>
    <bpmn:serviceTask id="SendAlert" name="Send Emergency Alert">
      <bpmn:incoming>flow_alert</bpmn:incoming>
      <bpmn:outgoing>flow_alert_notify</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:serviceTask id="NotifyAuthorities" name="Notify City Authorities">
      <bpmn:incoming>flow_alert_notify</bpmn:incoming>
      <bpmn:outgoing>flow_activate</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:userTask id="ActivateResponse" name="Operator: Activate Flood Response">
      <bpmn:incoming>flow_activate</bpmn:incoming>
      <bpmn:outgoing>flow_end_alert</bpmn:outgoing>
    </bpmn:userTask>
    <bpmn:serviceTask id="UpdateMonitor" name="Update Monitoring Dashboard">
      <bpmn:incoming>flow_monitor</bpmn:incoming>
      <bpmn:outgoing>flow_end_monitor</bpmn:outgoing>
    </bpmn:serviceTask>
    <bpmn:endEvent id="FloodEndAlert" name="Response Activated">
      <bpmn:incoming>flow_end_alert</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:endEvent id="FloodEndMonitor" name="Monitoring Updated">
      <bpmn:incoming>flow_end_monitor</bpmn:incoming>
    </bpmn:endEvent>
    <bpmn:sequenceFlow id="flow_start_check" sourceRef="FloodStart" targetRef="CheckLevel"/>
    <bpmn:sequenceFlow id="flow_alert" name="Yes" sourceRef="CheckLevel" targetRef="SendAlert"/>
    <bpmn:sequenceFlow id="flow_monitor" name="No" sourceRef="CheckLevel" targetRef="UpdateMonitor"/>
    <bpmn:sequenceFlow id="flow_alert_notify" sourceRef="SendAlert" targetRef="NotifyAuthorities"/>
    <bpmn:sequenceFlow id="flow_activate" sourceRef="NotifyAuthorities" targetRef="ActivateResponse"/>
    <bpmn:sequenceFlow id="flow_end_alert" sourceRef="ActivateResponse" targetRef="FloodEndAlert"/>
    <bpmn:sequenceFlow id="flow_end_monitor" sourceRef="UpdateMonitor" targetRef="FloodEndMonitor"/>
  </bpmn:process>
  <bpmndi:BPMNDiagram id="FloodDiagram">
    <bpmndi:BPMNPlane bpmnElement="FloodAlertProcess">
      <bpmndi:BPMNShape id="FloodStart_di" bpmnElement="FloodStart"><dc:Bounds x="80" y="172" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="CheckLevel_di" bpmnElement="CheckLevel" isMarkerVisible="true"><dc:Bounds x="185" y="165" width="50" height="50"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="SendAlert_di" bpmnElement="SendAlert"><dc:Bounds x="310" y="80" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="NotifyAuthorities_di" bpmnElement="NotifyAuthorities"><dc:Bounds x="470" y="80" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="ActivateResponse_di" bpmnElement="ActivateResponse"><dc:Bounds x="630" y="80" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="UpdateMonitor_di" bpmnElement="UpdateMonitor"><dc:Bounds x="310" y="230" width="100" height="80"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="FloodEndAlert_di" bpmnElement="FloodEndAlert"><dc:Bounds x="790" y="102" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNShape id="FloodEndMonitor_di" bpmnElement="FloodEndMonitor"><dc:Bounds x="470" y="252" width="36" height="36"/></bpmndi:BPMNShape>
      <bpmndi:BPMNEdge id="flow_start_check_di" bpmnElement="flow_start_check"><di:waypoint x="116" y="190"/><di:waypoint x="185" y="190"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_alert_di" bpmnElement="flow_alert"><di:waypoint x="210" y="165"/><di:waypoint x="210" y="120"/><di:waypoint x="310" y="120"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_monitor_di" bpmnElement="flow_monitor"><di:waypoint x="210" y="215"/><di:waypoint x="210" y="270"/><di:waypoint x="310" y="270"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_alert_notify_di" bpmnElement="flow_alert_notify"><di:waypoint x="410" y="120"/><di:waypoint x="470" y="120"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_activate_di" bpmnElement="flow_activate"><di:waypoint x="570" y="120"/><di:waypoint x="630" y="120"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_end_alert_di" bpmnElement="flow_end_alert"><di:waypoint x="730" y="120"/><di:waypoint x="790" y="120"/></bpmndi:BPMNEdge>
      <bpmndi:BPMNEdge id="flow_end_monitor_di" bpmnElement="flow_end_monitor"><di:waypoint x="410" y="270"/><di:waypoint x="470" y="270"/></bpmndi:BPMNEdge>
    </bpmndi:BPMNPlane>
  </bpmndi:BPMNDiagram>
</bpmn:definitions>`
