/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/
import com.navis.argo.business.atoms.LaneTruckStatusEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.portal.context.ArgoUserContext
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.presentation.FrameworkPresentationUtils
import com.navis.road.business.model.GateLane
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import wslite.soap.SOAPClient
import wslite.soap.SOAPResponse

/**
 * This groovy will send a disconnect SOAP Request to TDP. It will aslo set GateLane status to Empty and will clear the lane from the Console
 *
 * Author: Anburaja
 * Date: 20-Jul-2015
 * SFDC-142304/CSDV-3055
 * Called from: Gate Configuration (Truck Visit Level)
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

public class MATGateDisconnectTDP extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {

    private logMsg(String inMsg) {
        log("MATGateDisconnectTDP : " + inMsg);
    }

    public void execute(TransactionAndVisitHolder inDao) {

        logMsg("Started");

        ArgoUserContext userContext = (ArgoUserContext) FrameworkPresentationUtils.getUserContext();
        com.navis.road.business.reference.Console console = (userContext ? (com.navis.road.business.reference.Console) HibernateApi.getInstance().load(com.navis.road.business.reference.Console.class,  userContext.getConsoleGkey()) : null)

        if (inDao == null) {
            log("inDao is null");
            return;
        }

        TruckVisitDetails tvd = inDao.getTv();
        if (tvd == null) {
            log("TruckVisitDetails is null");
            return;
        }

        GateLane gateLane = tvd.getTvdtlsExitLane();
        GateLane null_value;
        //Update GateLane, Console and send SOAP Request
        if (gateLane && console){
            gateLane.setLaneTruckStatus(LaneTruckStatusEnum.EMPTY);
            console.setHwLaneSelected(null_value);

            sendRestfulMsgToTDP("DISCONNECT", gateLane, console);
        }
    }

    /**
     * Build the SOAP request
     * @param inMsgType
     * @param inGateLane
     * @param inConsole
     * @return
     */
    private sendRestfulMsgToTDP(String inMsgType, GateLane inGateLane, com.navis.road.business.reference.Console inConsole){
        SOAPResponse response;
        String xmlMessage = (String.format(RESTFULL_CONNECT_MESSAGE.toString(), inMsgType, inGateLane.getLaneId(),inConsole.getHwconsoleIdExternal()));
        logMsg(xmlMessage);
        if (StringUtils.isBlank(xmlMessage))
            log(inMsgType + " : Message failed to build");
        else
            response = sendSOAPRequest(xmlMessage);
        //for now eat the SOAP Response
    }

    /**
     * Send SOAP Request to TDP
     * @param xmlMessage
     * @return
     */
    private SOAPResponse sendSOAPRequest(String xmlMessage) {
        try {
            GeneralReference genRef = GeneralReference.findUniqueEntryById("RESTFULLTDP", "URL");
            String wsUrl = (genRef ? genRef.getRefValue1() : null);
            SOAPClient client = new SOAPClient(wsUrl);
            SOAPResponse response = client.send(xmlMessage);
            log("Connect Response = "+response.getText());
            return response;
        } catch (Exception ex) {
            log("sendSOAPRequest message failed to sent due to " + ex.toString());
            return null;
        }
    }

    /**
     *  Build SOAP Request XML format
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