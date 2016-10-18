/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */


import com.navis.argo.ContextHelper
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.Container
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.MailServerConfig
import com.navis.framework.portal.UserContext
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.config.ClientConfig
import com.sun.jersey.api.client.config.DefaultClientConfig
import org.apache.log4j.Logger
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper

import javax.mail.internet.MimeMessage
import javax.ws.rs.core.MediaType

/**
 * GEMSPreTripCheck.
 *
 * @author <a href="mailto:kramachandran@matson.com"> Keerthi Ramachandran</a>
 * @since 01/18/2016
 * @api none
 * Called from: Gate Configuration
 * <p>This code used to run an webservice call to GEMS to determine if the equipment is due for Pre-Trip inspection</p>
 * <p>The container ID is passed to GEMS and Boolean is required as reponse</p>
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
public class GEMSPreTripCheck extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {

    /**
     * Print document based on the configuration docTypeId parameter
     *
     * @param inOutDao
     */

    public static final String PRE_TRIP_REQUIRED = "<preTripDueFlag>Y</preTripDueFlag>"

    public void execute(TransactionAndVisitHolder inOutDao) {
        super.executeInternal(inOutDao);
        logMsg("execute Stared");
        TruckTransaction tran = inOutDao.getTran();
        UnitFacilityVisit unitFacilityVisit = tran.getTranUfv();
        Container container = tran.tranContainer;
        logMsg(container.toString());
        String equipmentId = tran.getTranCtrNbr();
        //if (unitFacilityVisit != null) equipmentId = unitFacilityVisit.getUnitId();
        logMsg("Equipment ID for pre-trip validation" + equipmentId);
        if (equipmentId == null) {
            logMsg("The Transaction Container can't be read. Something wrong with container read logic");
            /*RoadBizUtil.appendExceptionChainAsWarnings(
                    BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                            "Can't determine if the Equipment is due for Pretrip. Please Perform Manual Validation"));*/
            return;
        }
        Boolean preTripRequired = false;
        String owner = inOutDao.getTran().getTranContainer().getEquipmentOwnerId();
        /*
         * Pre-trip only for Matson Equipment
         */
        Boolean isMatsonEquipment = false;
        if (owner != null && ("MAT".equalsIgnoreCase(owner) || "MATU".equalsIgnoreCase(owner)))
            isMatsonEquipment = true;
        if (!isMatsonEquipment)
            return;

        /*
         * If the UCC code is not Equal to UN, prevent the User from completing the transaction
         */
        String ucc = inOutDao.getTran().getTranUnitFlexString15();
        Boolean overRideByUCC = false;
        logMsg("UCC from transaction " + ucc);
        /*if (ucc == null && inOutDao.getTran().getTranUfv() != null && inOutDao.getTran().getTranUfv().getUfvUnit() != null)
            ucc = inOutDao.getTran().getTranUfv().getUfvUnit().getUnitFlexString15();*/
        if (ucc != null && ("UN".equalsIgnoreCase(ucc) || "DL".equalsIgnoreCase(ucc) || "DN".equalsIgnoreCase(ucc) || "SF".equalsIgnoreCase(ucc) || ".".equalsIgnoreCase(ucc)))
            overRideByUCC = true;


        try {
            preTripRequired = isPreTripRequired(equipmentId); // call WS here with Equipment ID
        }
        catch (Exception exception) {
            //send email to dev team
            sendFailureMail(equipmentId, exception);
        }
        if (preTripRequired && !overRideByUCC) {
            RoadBizUtil.appendExceptionChain(
                    BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                            "The Unit " + equipmentId + " is due for PreTrip"));
        }
    }

    private logMsg(String inMsg) {
        log(this.getClass().getName() + " " + inMsg);
    }

    private Boolean isPreTripRequired(String inEquipmentID) {
        String equipmentId, checkDigit;
        if (inEquipmentID.length() > 10) equipmentId = inEquipmentID.substring(0, 10); else equipmentId = inEquipmentID;
        if (inEquipmentID.length() > 10) checkDigit = inEquipmentID.substring(10); else checkDigit = "X";

        GeneralReference genRef = GeneralReference.findUniqueEntryById("MATSON", "GEMSPRETRIP", "URL");
        log(genRef.getRefValue1());
        URL url = new URL(genRef.getRefValue1() + "equipment/" + equipmentId + "/checkdigit/" + checkDigit);
        logMsg(url.toString());
        DefaultClientConfig clientConfig1 = new DefaultClientConfig();
        clientConfig1.getProperties().put(ClientConfig.PROPERTY_CONNECT_TIMEOUT, Integer.parseInt(genRef.getRefValue2()));
        clientConfig1.getProperties().put(ClientConfig.PROPERTY_READ_TIMEOUT, Integer.parseInt(genRef.getRefValue3()));
        Client client = Client.create(clientConfig1);

        WebResource resource = client.resource(url.toString());
        ClientResponse response = (ClientResponse) resource.accept(MediaType.APPLICATION_XML).get(ClientResponse.class);
        if (response.getStatus() != 200) {
            logMsg("Request failed");
            logMsg(response.toString());
        } else {
            logMsg("Request Success");
            logMsg(response.toString());
        }
        String xmlResponse = response.getEntity(String.class);
        logMsg(xmlResponse);
        if (xmlResponse.contains(PRE_TRIP_REQUIRED))
            return Boolean.TRUE;
        else
            return Boolean.FALSE;
    }
/**
 * Send simple email message
 *
 * @param inTo TO email address
 * @param inFrom FROM email address
 * @param inSubject Text in the subject line
 * @param inBody Text in the body of the email
 * @return TRUE/FALSE     True if email has been sent or not
 */
    public Boolean sendEmail(String inTo, String inFrom, String inSubject, String inBody) {
        GroovyEmailSender sender = new GroovyEmailSender();
        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
        helper.setFrom(inFrom);
        helper.setReplyTo(inFrom);
        helper.setTo(inTo);
        helper.setSubject(inSubject);
        helper.setText(inBody, true);
        try {
            sender.send(msg);
        } catch (Exception inException) {
            LOGGER.error("GEMSPreTripCheck: Exception in email attempt: " + inException);
            return false;
        }
        return true;
    }

    private class GroovyEmailSender extends JavaMailSenderImpl {
        GroovyEmailSender() {
            setMailServerPropertiesFromUserContext();
        }
        /**
         * Sets the Host, Port, and Protocol from the config settings based on the UserContext from the email message.
         *
         * @param inEmailMessage
         */
        private void setMailServerPropertiesFromUserContext() {
            try {
                UserContext userContext = ContextHelper.getThreadUserContext();
                setHost(MailServerConfig.HOST.getSetting(userContext));
                setPort(Integer.parseInt(MailServerConfig.PORT.getSetting(userContext)));
                String protocol = MailServerConfig.PROTOCOL.getSetting(userContext);
                long timeout = MailServerConfig.TIMEOUT.getValue(userContext);
                Properties props = new Properties();
                props.setProperty("mail.pop3.timeout", String.valueOf(timeout));
                setProtocol(protocol);
                if ("smtps".equals(protocol)) {
                    setUsername(MailServerConfig.SMTPS_USER.getSetting(userContext));
                    setPassword(MailServerConfig.SMTPS_PASSWORD.getSetting(userContext));
                    props.put("mail.smtp.auth", "true");
                    props.put("mail.smtps.auth", "true");
                    props.put("mail.smtp.ssl.enable", "true");
                    props.put("mail.transport.protocol", "smtps");
                }
                setJavaMailProperties(props);
                LOGGER.info("Initialized SMTP Mail Server Configuration.");
            } catch (Throwable throwable) {
                String error = "Initializing the SMTP Mail Server configuration encountered the following error:";
                LOGGER.error(error, throwable);
                throw new MailSendException(error, throwable);
            }
        }
        private Logger LOGGER = Logger.getLogger(GroovyEmailSender.class);
    }

    public void sendFailureMail(String inEquipmentId, Throwable inThrowable) {
        GeneralReference genRef = GeneralReference.findUniqueEntryById("ENV", "ENVIRONMENT");
        String environment = genRef.getRefValue1();
        genRef = GeneralReference.findUniqueEntryById("MATSON", "EMAIL", "NOTIFICATION");
        String emailFrom = genRef.getRefValue1();
        String emailTo = genRef.getRefValue2();
        String emailSubject = environment + " - GEMS Equipment pretrip check failure " + inEquipmentId;
        String emailBody = inThrowable.message + "\n" + inThrowable.toString();
        sendEmail(emailTo, emailFrom, emailSubject, emailBody);
    }


}