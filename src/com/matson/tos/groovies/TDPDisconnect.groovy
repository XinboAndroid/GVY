import com.navis.argo.ArgoField
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.Lane
import com.navis.external.framework.ui.AbstractTableViewCommand
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.road.business.model.GateLane
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.config.DefaultClientConfig
import org.w3c.dom.Element
import wslite.soap.SOAPResponse

import javax.ws.rs.core.MediaType

/**
 * @author Keerthi Ramachandran
 * @since 1/12/2016
 * <p>TDPDisconnect is ..</p>
 */
class TDPDisconnect extends AbstractTableViewCommand {
    public TDPDisconnect() {
    }

    public void execute(EntityId inEntityId, List<Serializable> inGkeys, Map<String, Object> inParams) {
        UserContext uc = FrameworkPresentationUtils.getUserContext();
        log("uc.briefDetails" + uc.briefDetails);
        log("entityName" + inEntityId.entityName);


        List<Lane> laneList;
        for (Serializable s : inGkeys) {
            log("inGkey= $s")
            Map inParam = new HashMap();
            Map outParam = new HashMap();
            inParam.put("laneGkey", s);
            inParam.put("lane", inEntityId);
            this.executeInTransaction("GvyDisconnectLanes", inParam, outParam );
        }


        return;
    }


    private logMsg(String inMsg) {
        log("TDPDisconnect: " + inMsg);
    }

/*
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
*/


}
