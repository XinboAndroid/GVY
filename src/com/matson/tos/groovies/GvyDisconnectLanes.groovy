package com.matson.tos;

import com.navis.argo.ArgoField
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.LaneTruckStatusEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.model.Lane
import com.navis.argo.portal.context.ArgoUserContext
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
import com.navis.external.framework.persistence.AbstractExtensionPersistenceCallback

/**
 * Created by psethuraman on 01/12/2016.

 * api.getGroovyClassInstance("GvyDisconnectLanes").findLanes(LaneGkey);
 */


class GvyDisconnectLanes extends AbstractExtensionPersistenceCallback {

    void execute(Map inParams, Map inOutResults) {
        List<Lane> laneList = findLanes(inParams.get("laneGkey"));
        log("working lane size is : "+laneList.size());
        for (Lane lane : laneList) {
            log("LaneId" + lane.getLaneId())

            ArgoUserContext userContext = (ArgoUserContext) FrameworkPresentationUtils.getUserContext();
            com.navis.road.business.reference.Console console = (userContext ? (com.navis.road.business.reference.Console) HibernateApi.getInstance().load(com.navis.road.business.reference.Console.class, userContext.getConsoleGkey()) : null)
            Lane null_value;
            if (lane && console) {
                lane.setLaneTruckStatus(LaneTruckStatusEnum.EMPTY);
                console.setHwLaneSelected(null_value);
                sendDisConnectRequest(lane.getLaneId(), console);
            }
        }
    }

    public String execute(Map inParameters) {
        return execute();
    }

    public List<Lane> findLanes(Long s) {
        Map inParam = new HashMap();
        Map outParam = new HashMap();
        DomainQuery query = QueryUtils.createDomainQuery("Lane").
                addDqPredicate(PredicateFactory.eq(ArgoField.LANE_GKEY, s));
        List<Lane> laneList  = HibernateApi.getInstance().findEntitiesByDomainQuery(query)
        return laneList
    }

    private logMsg(String inMsg) {
        log("GvyDiscnnectLanes: " + inMsg);
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
    private Element sendDisConnectRequest(String inLaneId, com.navis.road.business.reference.Console inConsole) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("MATSON", "RESTFULLTDP", "URL");
        logMsg(genRef.getRefValue2());
        String inConsoleId = inConsole != null ? inConsole.getHwconsoleIdExternal() : "Clerk";
        logMsg("Clerk Id : "+inConsoleId);
        URL url = new URL(genRef.getRefValue2() + "laneId=" + inLaneId.substring(5) + "&clerkId=" + inConsoleId+"&printTicketCount=0");
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
}



