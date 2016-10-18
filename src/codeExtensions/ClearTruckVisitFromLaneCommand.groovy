package codeExtensions

import com.navis.framework.portal.BizResponse
import com.navis.framework.portal.UserContext
import com.navis.framework.presentation.command.AbstractBaseCommand
import com.navis.framework.presentation.ui.table.TableControllerHelper
import com.navis.road.RoadPropertyKeys
import com.navis.road.presentation.RoadDelegate
import org.jetbrains.annotations.Nullable

/**
 * @author Keerthi Ramachandran
 * @since 1/12/2016
 * <p>ClearTruckVisitFromLaneCommand is ..</p> 
 */
import java.io.Serializable;
import com.navis.external.framework.ui.AbstractTableViewCommand;
import com.navis.external.framework.ui.EUIExtensionHelper;
import com.navis.framework.metafields.entity.EntityId;
import com.navis.framework.portal.BizRequest;
import com.navis.framework.portal.CrudDelegate;
import com.navis.framework.presentation.util.FrameworkUserActions;
import com.navis.framework.ulc.server.context.UlcRequestContextFactory;
import com.navis.framework.util.message.MessageLevel;
import com.navis.road.RoadApiMappingConsts;
import com.navis.road.RoadField;
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

class ClearTruckVisitFromLaneCommand extends AbstractBaseCommand {
    public ClearTruckVisitFromLaneCommand() {
    }

    public Object execute(@Nullable Map<String, Object> inArguments) {
        UserContext uc = FrameworkPresentationUtils.getUserContext();
        for (String s : inArguments.keySet()) {
            log("$s=" + params.get(s))
        }
        BizResponse response = RoadDelegate.clearGateLaneTv(uc, this.getSourceAsArray());
        FrameworkPresentationUtils.displayMessagesIfNeeded(response, MessageLevel.SEVERE, RoadPropertyKeys.GATE__CLEAR_TV_FAILED, RoadPropertyKeys.GATE__CLEAR_TV_FAILED);
        TableControllerHelper.refreshTableFromCommand(this);
        return null;
    }
   /* void execute(EntityId entityId, List<Serializable> gkeys, Map<String, Object> params) {

        log("gkeys=$gkeys")
        log("entityId=$entityId")
        for (String s : params.keySet()) {
            log("$s=" + params.get(s))
        }
        super.execute(entityId,gkeys,params);
    }*/
    private logMsg(String inMsg) {
        log("ClearTruckVisitFromLaneCommand: " + inMsg);
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
        return selectedGateLane;
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
