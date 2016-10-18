import com.navis.argo.ArgoField;
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.LaneTruckStatusEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.portal.context.ArgoUserContext
import com.navis.framework.business.Roastery
import com.navis.framework.business.atoms.LifeCycleStateEnum
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.road.RoadBizMetafield
import com.navis.road.RoadEntity
import com.navis.road.RoadField
import com.navis.road.business.model.Gate
import com.navis.road.business.model.GateConfigStage
import com.navis.road.business.model.GateLane
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.config.DefaultClientConfig
import org.w3c.dom.Element
import wslite.soap.SOAPClient
import wslite.soap.SOAPResponse

import javax.ws.rs.core.MediaType

/**
 * Description: N4 will need to send the Connect/DisConnect Web Service API to TDP when the clerk selects the lane to work.
 * Gate Clerk can manually pick a lane or let N4 choose the lane (by pressing the custom action button)
 *
 * Note: Do NOT change the groovy name since this name is been used by N4 internally for the Custom Action button in gate Screen
 *
 * Author: Anburaja
 * Date: 20-Feb-2015
 * JIRA: SFDC-142304/CSDV-3055
 * Called From: Gate Configuration (custom action button-labeled as Pick Lane)
 *
 * Peter Seiler 06-Aug-2015: Change the check for the longest wait for the gate lane to also consider seconds.
 *
 * Sample SOAP Request for CONNECT and DISCONNECT message:

 <soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:now="nowsol">
 <soapenv:Header/>
 <soapenv:Body>
 <now:realtime_update>
 <now:ConnectionType>CONNECT</now:ConnectionType>
 <now:GateLaneId>Lane 1</now:GateLaneId>
 <now:ExtConsoleId>CONS1</now:ExtConsoleId>
 </now:realtime_update>
 </soapenv:Body>
 </soapenv:Envelope>

 **/

class GateCustomActionGroovyImpl extends GroovyApi {

    private logMsg(String inMsg) {
        log("GateCustomActionGroovyImpl: " + inMsg);
    }

    public Map<MetafieldId, Object> execute(String inStageId, Long inLaneGkey, Long inTvdtlsGkey, Long inTranGkey, FieldChanges inChanges) {
        String tvExitLane = null;
        //String tvExitLaneID = null;
        GateLane gateLane;
        Map<MetafieldId, Object> returnMap = new HashMap<MetafieldId, Object>();

        ArgoUserContext userContext = (ArgoUserContext) FrameworkPresentationUtils.getUserContext();
        logMsg("userContext = " + userContext);
        logMsg("userContext.getConsoleGkey() = " + userContext.getConsoleGkey());
        com.navis.road.business.reference.Console console = (userContext ? (com.navis.road.business.reference.Console) HibernateApi.getInstance().load(com.navis.road.business.reference.Console.class, userContext.getConsoleGkey()) : null)


        logMsg("console = " + console);
        if (inChanges.hasFieldChange(RoadField.TVDTLS_EXIT_LANE)) {
            tvExitLane = inChanges.getFieldChange(RoadField.TVDTLS_EXIT_LANE).getNewValue();
            gateLane = (tvExitLane ? (GateLane) HibernateApi.getInstance().load(GateLane.class, tvExitLane.toLong()) : null);
        }

        logMsg("before: gateLane = " + gateLane);
        //when no lane is selected, pick the lane that is waiting the longest
        gateLane = (!tvExitLane) ? findWaitingGateLane(inStageId) : gateLane;
        logMsg("after: gateLane = " + gateLane);
        returnMap.put(RoadField.TVDTLS_EXIT_LANE, (gateLane ? gateLane.getPrimaryKey() : null));
        returnMap.put(RoadBizMetafield.RELOAD_TRUCK_VISIT, true);

        //update console with the selected lane, update gate lane status
        // First send Disconnect Message as clean up message and then update the console and then send the Final Connnect Message
        if (gateLane && console) {
            log("Lane Selected : " + console.getHwLaneSelected());
            //send Disconnect message only when the console is occupied by some lane
            if (!"--".equalsIgnoreCase(console.getHwLaneSelected().toString()))
                sendRestfulDisConnectMsgToTDP(gateLane, console);

            updateGateLaneAndConsole(gateLane, console);
            sendRestfulConnectMsgToTDP(gateLane, console);
        } else {
            //do not show in Gate screen as popup message
            log("No GateLane is in Waiting Status (in Lane Monitor) \n or selected Console not Selected");
        }
        return returnMap;
    }

    /**
     * send message to TDP for Connect
     * @param inMsgType
     * @param inGateLane
     * @param inConsole
     * @return
     */
    private sendRestfulConnectMsgToTDP(GateLane inGateLane, com.navis.road.business.reference.Console inConsole) {
        SOAPResponse response;
        response = sendConnectRequest(inGateLane.getLaneId(), inConsole.getHwconsoleIdExternal());
        //todo what to with SOAP response???
    }
    /**
     * send message to TDP for Connect
     * @param inGateLane
     * @param inConsole
     * @return
     */
    private sendRestfulDisConnectMsgToTDP(GateLane inGateLane, com.navis.road.business.reference.Console inConsole) {
        SOAPResponse response;
        response = sendDisConnectRequest(inGateLane.getLaneId(), inConsole.getHwconsoleIdExternal());
        //todo what to with SOAP response???
    }

    /**
     * Send Restful Connect Message
     * @param inLaneId
     * @param inConsoleId
     * @return
     */
    private Element sendConnectRequest(String inLaneId, String inConsoleId) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("MATSON", "RESTFULLTDP", "URL");
        logMsg(genRef.getRefValue1());
        URL url = new URL(genRef.getRefValue1() + "laneId=" + inLaneId.substring(5) + "&clerkId=" + inConsoleId);
        logMsg(url.toString());
        DefaultClientConfig clientConfig1 = new DefaultClientConfig();
        Client client = Client.create(clientConfig1);
        WebResource resource = client.resource(url.toString());
        ClientResponse response = (ClientResponse) resource.accept(MediaType.TEXT_XML).get(ClientResponse.class);
        if (response.getStatus() != 200) {
            logMsg("Request failed");
            logMsg(response.toString());
        } else {
            logMsg("Request Success");
            logMsg(response.toString());
        }
        return null;
    }
    /**
     * Send restful Disconnect Request
     * @param inLaneId
     * @param inConsoleId
     * @return
     */
    private Element sendDisConnectRequest(String inLaneId, String inConsoleId) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("MATSON", "RESTFULLTDP", "URL");
        logMsg(genRef.getRefValue1());
        URL url = new URL(genRef.getRefValue2() + "laneId=" + inLaneId.substring(5) + "&clerkId=" + inConsoleId);
        logMsg(url.toString());
        DefaultClientConfig clientConfig1 = new DefaultClientConfig();
        Client client = Client.create(clientConfig1);
        WebResource resource = client.resource(url.toString());
        ClientResponse response = (ClientResponse) resource.accept(MediaType.TEXT_XML).get(ClientResponse.class);
        if (response.getStatus() != 200) {
            logMsg("Request failed");
            logMsg(response.toString());
        } else {
            logMsg("Request Success");
            logMsg(response.toString());
        }
        return null;
    }

    /**
     * Build SOAP Request XML Format String
     * @param xmlMessage
     * @return
     */
    private SOAPResponse sendSOAPRequest(String xmlMessage) {
        try {
            GeneralReference genRef = GeneralReference.findUniqueEntryById("RESTFULLTDP", "URL");
            String wsUrl = (genRef ? genRef.getRefValue1() : null);
            SOAPClient client = new SOAPClient(wsUrl);
            SOAPResponse response = client.send(xmlMessage);
            log("Connect Response = " + response.getText());
            return response;
        } catch (Exception ex) {
            log("sendSOAPRequest message failed to sent due to " + ex.toString());
            return null;
        }
    }

    /**
     * Update GateLane and Console entities
     * @param inGateLane
     * @param inConsole
     * @return
     */
    private static updateGateLaneAndConsole(GateLane inGateLane, com.navis.road.business.reference.Console inConsole) {
        inGateLane.setLaneTruckStatus(LaneTruckStatusEnum.PROCESSING);
        //log("Lane status : " + inGateLane.getLaneTruckStatus().toString());
        inConsole.setHwLaneSelected(inGateLane);
    }

    /**
     * Pick the longest waiting Gate Lane
     * @param inStageId
     * @return
     */
    private static GateLane findWaitingGateLane(inStageId) {

        GateLane selectedGateLane = null;
        GateLane reservedGateLane = null;
        DomainQuery query = QueryUtils.createDomainQuery(RoadEntity.GATE_CONFIG_STAGE)
                .addDqPredicate(PredicateFactory.eq(RoadField.STAGE_ID, inStageId));
        GateConfigStage gcs = (GateConfigStage) Roastery.getHibernateApi().getUniqueEntityByDomainQuery(query);

        if (gcs) {
            List<Gate> allGates = Gate.findAllGatesForFacilityAndGateConfig(ContextHelper.getThreadFacility(), gcs.getStageGateConfig());
            for (Gate eachGate : allGates) {
                Set<GateLane> gateLanes = eachGate.getGateLanes();
                gateLanes.each {
                    gateLane ->

                        /* if the lane has been waiting longer that the selected lane substitute the new lane */

                        if (LifeCycleStateEnum.ACTIVE.equals(gateLane.getLifeCycleState()) && LaneTruckStatusEnum.WAITING.equals(gateLane.getLaneTruckStatus())) {
                            if (selectedGateLane == null) {
                                selectedGateLane = gateLane;
                            } else {
                                if (selectedGateLane.getLaneInLaneTime() > gateLane.getLaneInLaneTime()) {
                                    selectedGateLane = gateLane;
                                }
                            }
                        }
                }
            }
        }
        if (selectedGateLane != null) {
            //logMsg("selectAndReserveExchangeLaneByRange: lane selected = " + selectedGateLane.getLaneId());
            // if we found a suitable lane attempt to reserve it exclusively for this visit
            reservedGateLane = lockEmptyLane(selectedGateLane);

            if (reservedGateLane == null) {
                // we had a valid lane but we couldn't reserve it - something changed since we originally found it
                // so do a refesh to see the new values otherwise we'll pick it again
                Roastery.getHibernateApi().refresh(selectedGateLane);
            }
        }

        return reservedGateLane;
    }


    public static GateLane lockEmptyLane(GateLane inLane) {

        DomainQuery dq = QueryUtils.createDomainQuery("GateLane").addDqPredicate(PredicateFactory.eq(ArgoField.LANE_GKEY, inLane.getLaneGkey()))
                .addDqPredicate(PredicateFactory.eq(ArgoField.LANE_TRUCK_STATUS, LaneTruckStatusEnum.WAITING ));

        dq.setSelectForUpdate(true);
        return (GateLane) Roastery.getHibernateApi().getUniqueEntityByDomainQuery(dq);
    }

    /**
     * Build SOAP Request XML format
     */
    private static final StringBuilder RESTFULL_CONNECT_MESSAGE = new StringBuilder().append(
            "<?xml version='1.0' encoding='UTF-8'?>\n").append(
            "<soapenv:Envelope xmlns:soapenv=\"http://schemas.xmlsoap.org/soap/envelope/\" xmlns:now=\"nowsol\">\n").append(
            "   <soapenv:Header/>\n").append(
            "   <soapenv:Body>\n").append(
            "      <now:realtime_update>\n").append(
            "         <now:ConnectionType>%s</now:ConnectionType>\n").append(
            "         <now:GateLaneId>%s</now:GateLaneId>\n").append(
            "         <now:ExtConsoleId>%s</now:ExtConsoleId>\n").append(
            "      </now:realtime_update>\n").append(
            "   </soapenv:Body>\n").append(
            "</soapenv:Envelope>\n");
}