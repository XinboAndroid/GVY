/**
 * Srno  date       doer    change
 * A1   03/19/2014  KR      Send NIZ_COMPLETE message to Gems
 * A2   03/21/2014	RI      Send NIZ_COMPLETE message to MNS and send notifications
 *
 */
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.services.business.event.Event;
import com.navis.services.business.event.GroovyEvent;
import com.navis.argo.business.atoms.UnitCategoryEnum;
import com.navis.framework.util.DateUtil;
import com.navis.argo.business.atoms.DrayStatusEnum
import com.navis.services.business.event.GroovyEvent
import com.navis.road.business.model.TruckingCompany
import com.navis.inventory.business.units.UnitFacilityVisit

import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.argo.business.model.CarrierVisit
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery
import com.navis.argo.ArgoField;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.argo.ContextHelper;
import org.apache.log4j.Logger

public class GvyNizComplete extends GroovyInjectionBase {
    public void sendNizComplete(Object event, Object api) {
        LOGGER.warn("BEGIN : GvyNizComplete.sendNizComplete()")
        def emailSender = api.getGroovyClassInstance("EmailSender")
        LOGGER.warn("Waiting....")
        Thread.sleep(5000)
        def unit = event.getEntity();
        def visitId = null;   def vesClass = null;
        def fcy = null; def ufv = null; def isYBbarge = false;  def nextFacility = null;
        def availDate = null;
        try {
            fcy = ContextHelper.getThreadFacility();
            ufv = unit.getUfvForFacilityCompletedOnly(fcy); //If Departed get facility ufv
            if(ufv == null){
                ufv = unit.unitActiveUfv; //Assign ufv from Facility Active unit
            }
            if(event.event.eventTypeId.equals("NIS_CODING_COMPLETE_LH")){
                visitId = ufv.getFieldValue("ufvActualIbCv.cvId")
                vesClass = ufv.getFieldValue("ufvActualIbCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                vesClass = vesClass != null ? vesClass.getKey() : ''
            }else{
                visitId = ufv.getFieldValue("ufvActualObCv.cvId")
                vesClass = ufv.getFieldValue("ufvActualObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                vesClass = vesClass != null ? vesClass.getKey() : ''
                nextFacility = ufv.getFieldValue("ufvActualObCv.cvNextFacility.fcyId")

                //YB Code
                def visitIntId = ufv.getFieldValue("ufvIntendedObCv.cvId")
                if(visitIntId != null && visitIntId.startsWith('YB')){
                    isYBbarge = true;
                    nextFacility = ufv.getFieldValue("ufvIntendedObCv.cvNextFacility.fcyId");
                    def aobcarrierMode=ufv.getFieldValue("ufvActualObCv.cvCarrierMode")
                    aobcarrierMode = aobcarrierMode != null ? aobcarrierMode.getKey() : ''
                    def intVesClass = ufv.getFieldValue("ufvIntendedObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                    intVesClass = intVesClass != null ? intVesClass.getKey() : ''
                    //if('TRUCK'.equals(aobcarrierMode)){ // Always use OB Intended for YB barges
                    visitId = visitIntId;
                    vesClass = intVesClass;
                    //}
                }
            }//Else Ends
            def port = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
            port = 'KHI'.equals(port) ? 'HIL' : port
            GroovyInjectionBase gvybase = new GroovyInjectionBase()
            def evtAppliedDt = event.event.getEvntAppliedDate()
            def zone = com.navis.argo.ContextHelper.getThreadUserTimezone()
            def gvyEventUtil =  gvybase.getGroovyClassInstance("GvyEventUtil");
            def aDate  = gvyEventUtil.formatDate(evtAppliedDt,zone)
            def aTime = gvyEventUtil.formatTime(evtAppliedDt,zone)
            String dateFormat = "yyyy-MM-dd'T'HH:mm:ss";
            String mnsDate = gvyEventUtil.formatDateTime(evtAppliedDt, zone, dateFormat)

            String gemsMsg = "<GroovyMsg vesvoy='"+visitId+"' action='NIZ' aTime='"+aTime+"' doer='TOS' aDate='"+aDate+"' msgType='NIZ_COMPLETE'  dPort='"+port+"' />"
            String mnsMsg = "<new-vessel source-system='TOS' vvd='"+visitId+"' port-code='"+port+"' date-time='"+mnsDate+"' status='COMPLETE' />";

            //3. Post Direct to MNS
            def jmsQueueSender = api.getGroovyClassInstance("JMSQueueSender")
            LOGGER.warn( "Posting to jms/queue/oceanevent/newvess/ni/inbound");
            jmsQueueSender.setMnsQueue("jms/queue/oceanevent/newvess/ni/inbound");
            jmsQueueSender.send(mnsMsg);

            //Post Direct to Gems
            jmsQueueSender.setMnsQueue("n4.gems.eq.events");
            jmsQueueSender.send(gemsMsg);

            //Success Email
            //def sub = "Trucker coding sucessfully completed for "+visitId;
            //def portToEmail = getEmialId(nextFacility);
            // emailSender.custSendEmail(portToEmail, sub, sub);

            LOGGER.warn("GemsNIZMsg="+gemsMsg);
            LOGGER.warn("MnsNIZMsg="+mnsMsg);
        }
        catch(Exception ex) {
            ex.printStackTrace();
            log(ex.getMessage());
        }
        LOGGER.warn("END : GvyNizComplete.sendNizComplete()")
    }

    public String getEmialId(String port){

        String emailId = getReferenceValue("TRCK_CODING_"+port, "TRCK_CODING_"+port, null, null, 1)  //A16
        if(emailId == null){ return "1tosdevteamhon@gmail.com" }
        return emailId
    }

    private static final Logger LOGGER = Logger.getLogger(GvyNizComplete.class);

}
