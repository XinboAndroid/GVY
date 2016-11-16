/*
* Srno  date       doer  change
* A1    12/117/11  GR    Added Method for YB-SIT
* A2    01/10/12   GR    UNIT_REROUTE YB assing and unassign fior NIS MNC
* A3    01/11/12   GR    Added Function ybUnAssignTrucker for SIT
* A4    01/11/12   GR    Rolling Trucker on UNIT RECIEVE setDesignatedTrucker
* A5    01/12/12   GR    Handled NullPointer Exception
* A6    01/12/12   GR    check the YB_UNASSIGN on YB to YB moves
* A7    01/12/12   GR    Imported Trucking Cmpy
* A8    01/12/12   GR    Updt Truck field
* A9    02/02/12   GR    YB NIS - ybNisDetentionProc
* A10   02/02/12   GR    YB NIS - ybNisTrucker
* A11   02/02/12   GR    NIZ action posting
* A12   02/17/12   GR    Updt Field ufvFlexString07 to unitFlexString07
* A13   03/07/12   GR    Updt Email Method
* A14   03/12/12   GR    Unit list cehck before sending out email
* A15   03/12/12   GR    HIL KHI port change
* A16   03/21/12   GR    Commented out the Departed Check for ALL vesvoys
* A17   03/21/12   GR    Added Additional Date Parameter
* A18   03/22/12   GR    Auto Set Truckers
* A19   03/22/12   GR    Fixed NIT for Long haul
* A20   03/22/12   GR    Query change for POD (HIL,KHI)
* A21   06/15/12   KM    Don't send "Ready for Trucker Coding" notification for YB barges
* A22   09/12/12   LC    Added yb_unassign check for DEST field change and yb_assign check for Obcarrier field change
* A23   09/24/2014 RP    Posting trucker update to GEMS when NIS_TRUCKER_ASSIGN event
* A24   07/09/15   KR    Alaska Ports
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.reference.Commodity
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.business.api.UnitField
import com.navis.road.business.model.TruckingCompany
import com.navis.services.business.event.GroovyEvent

public class GvyCmisEventSIT extends GroovyInjectionBase {

    def ACTION = "action='null'"
    def LAST_ACTION = "lastAction='null'"
    def errorEmail = "1aktosdevteam@matson.com";
    def supportEmail = "1aktosdevteam@matson.com";
    def gvyEventUtil = null;

    public String lookupCommodity(String id) {
        DomainQuery dq = QueryUtils.createDomainQuery("Commodity").addDqPredicate(PredicateFactory.eq(com.navis.argo.ArgoRefField.CMDY_GKEY, id));
        Commodity c = (Commodity) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
        if (c == null) {
            return "";
        }
        return c.getCmdyId();

    }

    //SIT_ASSIGN
    public String processSitAssign(String xmlGvyData, Object event, Object unit, String commodity, String drayStatus) {
        def xmlGvyString = xmlGvyData
        try {
            def destination = unit.getFieldValue("unitGoods.gdsDestination")
            destination = destination != null ? destination : ''

            GroovyInjectionBase gvybase = new GroovyInjectionBase()
            def appendObj = gvybase.getGroovyClassInstance("GvyEventSpecificFldValue");
            gvyEventUtil = gvybase.getGroovyClassInstance("GvyEventUtil");
            def cmdyId = gvyEventUtil.getPreviousPropertyAsString(event, 'gdsCommodity')
            def preCommodity = lookupCommodity(cmdyId)

            def preDrayStatus = gvyEventUtil.getPreviousPropertyAsString(event, 'unitDrayStatus')
            preDrayStatus = preDrayStatus != null ? preDrayStatus : ''

            //ACTION
            if (preCommodity.equals(commodity) && preDrayStatus.equals(drayStatus)) {

                xmlGvyString = xmlGvyString.replace(LAST_ACTION, "lastAction='EDT'");
                xmlGvyString = xmlGvyString.replace(ACTION, "action='EDT'")
            }

/*       //MISC1
def misc1 = appendObj.getFieldValues(xmlGvyString, "misc1=");

if(!misc1.equals('null')){
  xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"commodity=",misc1,null,null,null)
}else{
  xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"commodity=","SIT",null,null,null)
}  */
            //DESTINATION
            if (!destination.equals(ContextHelper.getThreadFacility().getFcyId())) {
                xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString, "misc3=", "null", null, null, null)
            }

        } catch (Exception e) {
            e.printStackTrace()
        }
        return xmlGvyString;
    }

    //MAP ACTION SIT_UNASSIGN
    public String processSitUnAssign(String xmlGvyData, Object appendObj) {
        def xmlGvyString = xmlGvyData
        xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString, "dsc=", null, null, null, null)

        return xmlGvyString;
    }

    //A1
    /**public void setYbDrayStaus(Object unit){try{def cmdyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
     def group= unit.getFieldValue("unitRouting.rtgGroup.grpId");
     if("YB".equals(group) && 'SAT'.equals(cmdyId)){unit.setFieldValue("unitDrayStatus",DrayStatusEnum.TRANSFER);}else if("YB".equals(group) && 'SIT'.equals(cmdyId)){unit.setFieldValue("unitDrayStatus",DrayStatusEnum.OFFSITE);}}catch(Exception e){e.printStackTrace();}}*/
//Method Ends

    /*public void ybSitProc(Object api, Object gvyCmisUtil,Object unit, String xmlGvyReceive)
    {
        try{
            if(unit.unitRouting.rtgGroup != null && 'YB'.equals(unit.unitRouting.rtgGroup.grpId)){
                setYbDrayStaus(unit)
                setDesignatedTrucker(unit)
                def xmlGvyStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyReceive,"ybBarge=",unit.unitActiveUfv.ufvIntendedObCv.cvId)
                xmlGvyStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyReceive,"flex02=",unit.unitActiveUfv.ufvIntendedObCv.cvId)
                xmlGvyStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyStr,"commodity=","YB")
                xmlGvyStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyStr,"truck=",unit.getFieldValue("unitFlexString14"))
                gvyCmisUtil.postMsgForAction(xmlGvyStr,api,'PDU')
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }*///Method Ends

    public String ybSatProc(Object gvyCmisUtil, Object unit, String xmlGvyReceive) {
        def xmlGvyStr = xmlGvyReceive;
        try {
            def cmdyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
            if (unit.unitRouting.rtgGroup != null && 'YB'.equals(unit.unitRouting.rtgGroup.grpId) && 'SAT'.equals(cmdyId)) {
                xmlGvyStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyReceive, "commodity=", "SAT")
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return xmlGvyStr
    }//Method Ends

    public void ybTruckerUnassign(Object unit) {
        try {
            if (unit.unitRouting.rtgGroup != null && 'YB'.equals(unit.unitRouting.rtgGroup.grpId)) {
                def trckCmpy = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
                def ybTrucker = unit.getUnitFlexString14();
                if (trckCmpy != null && trckCmpy.equals(ybTrucker)) {
                    unit.getUnitRouting().setRtgTruckingCompany(null);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }//Method Ends

    //A2
    public void NISBargeAssign(Object event, Object unit, Object api) {
        try {
            def gvyEventUtil = api.getGroovyClassInstance("GvyEventUtil");
            def doer = event.event.evntAppliedBy;
            com.navis.argo.ContextHelper.setThreadExternalUser(doer);
            def sendEvent = new GroovyEvent(null, unit);
            String groupId = unit.getFieldValue("unitRouting.rtgGroup.grpId");
            def intCarrId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId");
            intCarrId = intCarrId != null ? intCarrId : "";
            String yb_flag = "YB";

            /* YB_UNASSIGN triggered by UNIT_REROUTE based on Dest change
            */
            api.logWarn("NISBargeAssign::GroupId->" + groupId);
            if (yb_flag.equalsIgnoreCase(groupId) && ((gvyEventUtil.wasFieldChanged(event, "ufvIntendedObCv") && !intCarrId.startsWith('YB')) || gvyEventUtil.wasFieldChanged(event, "gdsDestination"))) {
                sendEvent.postNewEvent("YB_UNASSIGN", "YB Barge Routing");
                api.logWarn("NISBargeAssign::YB_UNASSIGN");
            }

            /* YB_ASSIGN triggered by UNIT_REROUTE based on Outbound Carrier Intended change
            */
            api.logWarn("NISBargeAssign::unitActiveUfv.ufvIntendedObCv.cvId->" + intCarrId);
            if (intCarrId.startsWith('YB') && gvyEventUtil.wasFieldChanged(event, "ufvIntendedObCv")) {
                sendEvent.postNewEvent("YB_ASSIGN", "YB Barge Routing");
                api.logWarn("NISBargeAssign::YB_ASSIGN");
            }

        } catch (Exception e) {
            api.log("Exception in GvyCmisEventSIT.NISBargeAssign()" + e);
        }
    }//Method Ends


    public void ybUnAssignTrucker(Object unit) {
        try {
            def designatedTrucker = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
            def ybTrucker = unit.getFieldValue("unitFlexString14")
            def cmdyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");

            if ('SIT'.equals(cmdyId) && designatedTrucker != null && !designatedTrucker.equals(ybTrucker)) {
                unit.setUnitFlexString14(null);
            } else if ('SIT'.equals(cmdyId) && designatedTrucker != null && designatedTrucker.equals(ybTrucker)) {
                unit.getUnitRouting().setRtgTruckingCompany(null);
                unit.setUnitFlexString14(null);
            } else {
                unit.getUnitRouting().setRtgTruckingCompany(null);
                unit.setUnitFlexString14(null);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }//Method Ends

    //A6 - Setting Designated Trucker for YB
    public void setDesignatedTrucker(Object unit) {
        try {
            def ybTrucker = unit.getFieldValue("unitFlexString14");
            if (ybTrucker != null) {
                def trkc = TruckingCompany.findTruckingCompany(ybTrucker)
                if (trkc != null) {
                    unit.getUnitRouting().setRtgTruckingCompany(trkc);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public void ybNisTruckerProc(Object unit, Object gvyEventUtil, Object event) {
        try {
            def truck = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
            unit.setFieldValue("unitFlexString07", truck); //A12

            def prevTruck = gvyEventUtil.getPreviousPropertyAsString(event, "rtgTruckingCompany")
            if (prevTruck != null) {
                def trkc = TruckingCompany.load(prevTruck);
                if (trkc != null) {
                    unit.getUnitRouting().setRtgTruckingCompany(trkc);
                }
            } else {
                unit.getUnitRouting().setRtgTruckingCompany(null);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }//Method Ends

    public String setNisTruck(Object gvyCmisUtil, Object unit, String xmlGvyReceive) {
        def xmlGvyStr = xmlGvyReceive;
        try {
            xmlGvyStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyStr, "truck=", unit.getFieldValue("unitFlexString07"))
        } catch (Exception e) {
            e.printStackTrace();
        }
        return xmlGvyStr
    }//Method Ends


    private void NIZCompleteProc(Object event, Object api, Object beginDelivery, Object nextPortDelivery) {
        //Gems - <GroovyMsg msgType='NIZ_COMPLETE'  action='NIZ'   aDate='08/26/2011' aTime='08:43:58' doer='tos' vesvoy='ALE134B'  dPort='HIL'/>
        //MNS - <new-vessel source-system="TOS" vessel-voyage="ALE019" port-code="KHI" status="COMPLETE"/>
        def emailSender = api.getGroovyClassInstance("EmailSender")
        def unit = event.getEntity();
        def visitId = null; def vesClass = null;
        def fcy = null; def ufv = null; def isYBbarge = false; def nextFacility = null;
        def availDate = null;

        try {
            fcy = ContextHelper.getThreadFacility();
            ufv = unit.getUfvForFacilityCompletedOnly(fcy); //If Departed get facility ufv
            if (ufv == null) {
                ufv = unit.unitActiveUfv; //Assign ufv from Facility Active unit
            }

            if (event.event.eventTypeId.equals("NIS_CODING_COMPLETE_LH")) {
                visitId = ufv.getFieldValue("ufvActualIbCv.cvId")
                vesClass = ufv.getFieldValue("ufvActualIbCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                vesClass = vesClass != null ? vesClass.getKey() : ''
            } else {
                visitId = ufv.getFieldValue("ufvActualObCv.cvId")
                vesClass = ufv.getFieldValue("ufvActualObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                vesClass = vesClass != null ? vesClass.getKey() : ''
                nextFacility = ufv.getFieldValue("ufvActualObCv.cvNextFacility.fcyId")

                //YB Code
                def visitIntId = ufv.getFieldValue("ufvIntendedObCv.cvId")
                if (visitIntId != null && visitIntId.startsWith('YB')) {
                    isYBbarge = true;
                    nextFacility = ufv.getFieldValue("ufvIntendedObCv.cvNextFacility.fcyId");
                    def aobcarrierMode = ufv.getFieldValue("ufvActualObCv.cvCarrierMode")
                    aobcarrierMode = aobcarrierMode != null ? aobcarrierMode.getKey() : ''
                    def intVesClass = ufv.getFieldValue("ufvIntendedObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                    intVesClass = intVesClass != null ? intVesClass.getKey() : ''
                    if ('TRUCK'.equals(aobcarrierMode)) {
                        visitId = visitIntId;
                        vesClass = intVesClass;
                    }
                }
            }//Else Ends

            def port = unit.getFieldValue("unitRouting.rtgPOD1.pointId");
            port = 'KHI'.equals(port) ? 'HIL' : port
            nextFacility = nextFacility != null ? nextFacility : port
            def portToEmail = getEmialId(nextFacility);
            //List out units to create BDC for
            def unitList = null; def cvGkey = null;
            if (visitId != null && visitId.startsWith('YB')) {
                cvGkey = ufv.getFieldValue("ufvIntendedObCv.cvdGkey");
            } else {
                cvGkey = ufv.getFieldValue("ufvActualObCv.cvdGkey");
            }
            if (!event.event.eventTypeId.equals("NIS_CODING_COMPLETE_LH")) {
                unitList = getUnitforObCarrier(visitId, isYBbarge);
                //Stop proc if no units to process against
                if (unitList == null || unitList.size() == 0) {
                    def sub = "Trucker coding for VesVisit " + visitId + " STOPPED unit List is ZERO";
                    def body = "Please ROUTING OBCarrier Selection for " + visitId + " then re-execute Trucker coding complete process";
                    emailSender.custSendEmail(portToEmail, sub, body);
                    return;
                }
            }

            if ('CELL'.equals(vesClass)) {
                def ctrNo = null; def checkDigit = null;
                def lhUnits = getUnitforIbCarrier(visitId, nextFacility, "AUTO") //A19 Starts
                //println("LHUNIT --"+(lhUnits != null ? lhUnits.size() : "ZERO"))
                if (lhUnits == null || lhUnits.size() == 0) {
                    def sub = "Trucker coding for VesVisit " + visitId + " STOPPED unit List is ZERO";
                    def body = "Long haul Carrier Returned Zero Records for " + visitId + " Please check entry and re-execute Trucker coding complete process";
                    emailSender.custSendEmail(portToEmail, sub, body);
                    return;
                }

                def iter = lhUnits.iterator();
                while (iter.hasNext()) {
                    def aUnit = iter.next();
                    def currUfv = getCurrentFaciltyUnitFromUfv(aUnit)
                    def currUnit = currUfv.getUfvUnit()
                    def cmdyId = currUnit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                    //N4 TO CMIS data processing
                    if (!'AUTO'.equals(cmdyId)) {
                        def groovyEvent = new GroovyEvent(null, currUnit);
                        groovyEvent.postNewEvent("NIS_TRUCKER_POST_MSG", "Automated NIT created");
                    }
                }
                //Set Vesvoy DIR
                visitId = visitId + "W";
            }//A19 Ends
            else if ('BARGE'.equals(vesClass)) {
                def iter = unitList.iterator();
                def availLookup = api.getGroovyClassInstance("GvyAvailDate");
                while (iter.hasNext()) {
                    def aUnit = iter.next();
                    //1. Set Avail Date
                    def currUfv = getCurrentFaciltyUnitFromUfv(aUnit)
                    def currUnit = currUfv.getUfvUnit()
                    def nisPort = currUnit.getFieldValue("unitRouting.rtgPOD1.pointId");
                    if ('KHI'.equals(nisPort)) {
                        availDate = nextPortDelivery != null ? nextPortDelivery : beginDelivery
                    } else {
                        availDate = beginDelivery
                    }
                    availLookup.detentionForNisBarge(event, currUfv, availDate);

                    def cmdyId = currUnit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                    if (!'AUTO'.equals(cmdyId)) {
                        def groovyEvent = new GroovyEvent(null, currUnit);
                        groovyEvent.postNewEvent("NIS_DETENTION", "Automated BDC created");
                    }
                }
            }
            def evtAppliedDt = event.event.getEvntAppliedDate()
            def zone = com.navis.argo.ContextHelper.getThreadUserTimezone()
            gvyEventUtil = api.getGroovyClassInstance("GvyEventUtil");
            def aDate = gvyEventUtil.formatDate(evtAppliedDt, zone)
            def aTime = gvyEventUtil.formatTime(evtAppliedDt, zone)
            String dateFormat = "yyyy-MM-dd'T'HH:mm:ss";
            String mnsDate = gvyEventUtil.formatDateTime(evtAppliedDt, zone, dateFormat)

            String gemsMsg = "<GroovyMsg vesvoy='" + visitId + "' action='NIZ' aTime='" + aTime + "' doer='TOS' aDate='" + aDate + "' msgType='NIZ_COMPLETE'  dPort='" + port + "' />"
            //2. Create MNS Data mapping
            String mnsMsg = "<new-vessel source-system='TOS' vvd='" + visitId + "' port-code='" + port + "' date-time='" + mnsDate + "' status='COMPLETE' />";

            //3. Post Direct to MNS
            def jmsQueueSender = api.getGroovyClassInstance("JMSQueueSender")
            println("Posting to jms/queue/oceanevent/newvess/ni/inbound");
            jmsQueueSender.setMnsQueue("jms/queue/oceanevent/newvess/ni/inbound");
            jmsQueueSender.send(mnsMsg);

            //Post Direct to Gems
            jmsQueueSender.setMnsQueue("n4.gems.eq.events");
            jmsQueueSender.send(gemsMsg);

            //Success Email
            def sub = "Trucker coding sucessfully completed for " + visitId;
            emailSender.custSendEmail(portToEmail, sub, sub);

            println("GemsNIZMsg=" + gemsMsg);
            println("MnsNIZMsg=" + mnsMsg);


        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void NIZCompleteProcBarge(Object event, Object api, Object beginDelivery, Object nextPortDelivery) {
        println("BEGIN : GvyCmisEventSIT.NIZCompleteProcBarge()")
        //Gems - <GroovyMsg msgType='NIZ_COMPLETE'  action='NIZ'   aDate='08/26/2011' aTime='08:43:58' doer='tos' vesvoy='ALE134B'  dPort='HIL'/>
        //MNS - <new-vessel source-system="TOS" vessel-voyage="ALE019" port-code="KHI" status="COMPLETE"/>
        def emailSender = api.getGroovyClassInstance("EmailSender")
        def unit = event.getEntity();
        def visitId = null; def vesClass = null;
        def fcy = null; def ufv = null; def isYBbarge = false; def nextFacility = null;
        def availDate = null;

        try {
            fcy = ContextHelper.getThreadFacility();
            ufv = unit.getUfvForFacilityCompletedOnly(fcy); //If Departed get facility ufv
            if (ufv == null) {
                ufv = unit.unitActiveUfv; //Assign ufv from Facility Active unit
            }

            if (event.event.eventTypeId.equals("NIS_CODING_COMPLETE_LH")) {
                visitId = ufv.getFieldValue("ufvActualIbCv.cvId")
                vesClass = ufv.getFieldValue("ufvActualIbCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                vesClass = vesClass != null ? vesClass.getKey() : ''
            } else {
                visitId = ufv.getFieldValue("ufvActualObCv.cvId")
                vesClass = ufv.getFieldValue("ufvActualObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                vesClass = vesClass != null ? vesClass.getKey() : ''
                nextFacility = ufv.getFieldValue("ufvActualObCv.cvNextFacility.fcyId")

                //YB Code
                def visitIntId = ufv.getFieldValue("ufvIntendedObCv.cvId")
                if (visitIntId != null && visitIntId.startsWith('YB')) {
                    isYBbarge = true;
                    nextFacility = ufv.getFieldValue("ufvIntendedObCv.cvNextFacility.fcyId");
                    def aobcarrierMode = ufv.getFieldValue("ufvActualObCv.cvCarrierMode")
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
            nextFacility = nextFacility != null ? nextFacility : port
            def portToEmail = getEmialId(nextFacility);
            //List out units to create BDC for
            def unitList = null; def cvGkey = null;
            if (visitId != null && visitId.startsWith('YB')) {
                cvGkey = ufv.getFieldValue("ufvIntendedObCv.cvdGkey");
            } else {
                cvGkey = ufv.getFieldValue("ufvActualObCv.cvdGkey");
            }
            if (!event.event.eventTypeId.equals("NIS_CODING_COMPLETE_LH")) {
                unitList = getUnitforObCarrier(visitId, isYBbarge);
                //Stop proc if no units to process against
                if (unitList == null || unitList.size() == 0) {
                    def sub = "Trucker coding for VesVisit " + visitId + " STOPPED unit List is ZERO";
                    def body = "Please ROUTING OBCarrier Selection for " + visitId + " then re-execute Trucker coding complete process";
                    emailSender.custSendEmail(portToEmail, sub, body);
                    return;
                }
            }

            if ('CELL'.equals(vesClass)) {
                def ctrNo = null; def checkDigit = null;
                def lhUnits = getUnitforIbCarrier(visitId, nextFacility, "AUTO") //A19 Starts
                //println("LHUNIT --"+(lhUnits != null ? lhUnits.size() : "ZERO"))
                if (lhUnits == null || lhUnits.size() == 0) {
                    def sub = "Trucker coding for VesVisit " + visitId + " STOPPED unit List is ZERO";
                    def body = "Long haul Carrier Returned Zero Records for " + visitId + " Please check entry and re-execute Trucker coding complete process";
                    emailSender.custSendEmail(portToEmail, sub, body);
                    return;
                }

                def iter = lhUnits.iterator();
                while (iter.hasNext()) {
                    def aUnit = iter.next();
                    def currUfv = getCurrentFaciltyUnitFromUfv(aUnit)
                    def currUnit = currUfv.getUfvUnit()
                    def cmdyId = currUnit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                    //N4 TO CMIS data processing
                    if (!'AUTO'.equals(cmdyId)) {
                        def groovyEvent = new GroovyEvent(null, currUnit);
                        groovyEvent.postNewEvent("NIS_TRUCKER_POST_MSG", "Automated NIT created");
                    }
                }
                //Set Vesvoy DIR
                visitId = visitId + "W";
            }//A19 Ends
            else if ('BARGE'.equals(vesClass)) {
                def iter = unitList.iterator();
                def availLookup = api.getGroovyClassInstance("GvyAvailDate");
                while (iter.hasNext()) {
                    def aUnit = iter.next();
                    //1. Set Avail Date
                    def currUfv = getCurrentFaciltyUnitFromUfv(aUnit)
                    def currUnit = currUfv.getUfvUnit()
                    def nisPort = currUnit.getFieldValue("unitRouting.rtgPOD1.pointId");
                    if ('KHI'.equals(nisPort)) {
                        availDate = nextPortDelivery != null ? nextPortDelivery : beginDelivery
                    } else {
                        availDate = beginDelivery
                    }
                    availLookup.detentionForNisBarge(event, currUfv, availDate);

                    def cmdyId = currUnit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                    if (!'AUTO'.equals(cmdyId)) {
                        def groovyEvent = new GroovyEvent(null, currUnit);
                        groovyEvent.postNewEvent("NIS_DETENTION", "Automated BDC created");
                    }
                }
            }

            //Success Email
            def sub = "Trucker coding sucessfully completed for " + visitId;
            emailSender.custSendEmail(portToEmail, sub, sub);

        } catch (Exception e) {
            e.printStackTrace();
        }
        println("END : GvyCmisEventSIT.NIZCompleteProcBarge()")
    }

    public List getUnitforObCarrier(Object cvId, boolean ybBarge) {
        List cvUnits = null;
        try {
            // NON HON POD
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            //dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.DEPARTED));

            if (ybBarge) {
                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_INTENDED_OB_ID, cvId));
            } else {
                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID, cvId));
            }
            cvUnits = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cvUnits;
    }

    //A19
    public List getUnitforIbCarrier(Object cvId, String pod, String cmdy) {

        List cvUnits = null;
        try {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");

            if ('KHI'.equals(pod)) {
                def ports = [pod, "HIL"]
                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, cvId)).addDqPredicate(PredicateFactory.in(UnitField.UFV_POD_ID, ports))
            } else if ('HIL'.equals(pod)) {
                def ports = [pod, "KHI"]
                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, cvId)).addDqPredicate(PredicateFactory.in(UnitField.UFV_POD_ID, ports))
            } else {
                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, cvId)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_POD_ID, pod))
            }
            cvUnits = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return cvUnits;
    }


    public Date setVesBeginDate(Object unit, Object ufv, Object api) {
        try {

            def begindelivery = unit.getFieldValue("unitActiveUfv.ufvFlexDate05");
            def vvd = null; def vvPhase = ""; def vesId = null; def nextFacility = null;
            def emailAddr = "1aktosdevteam@matson.com";
            def visitIntId = ufv.getFieldValue("ufvIntendedObCv.cvId")


            try {
                if (visitIntId != null && visitIntId.startsWith('YB')) {
                    vvd = ufv.ufvIntendedObCv.cvCvd
                    vvPhase = ufv.getFieldValue("ufvIntendedObCv.cvVisitPhase")
                    vesId = ufv.getFieldValue("ufvIntendedObCv.cvId");
                    nextFacility = ufv.getFieldValue("ufvIntendedObCv.cvNextFacility.fcyId");
                } else {
                    vvd = ufv.ufvActualObCv.cvCvd
                    vvPhase = ufv.getFieldValue("ufvActualObCv.cvVisitPhase");
                    vesId = vesId = ufv.getFieldValue("ufvActualObCv.cvId");
                    nextFacility = ufv.getFieldValue("ufvActualObCv.cvNextFacility.fcyId");
                }
                emailAddr = getEmialId(nextFacility)

            } catch (Exception e) {
                try {
                    vvd = ufv.ufvActualObCv.cvCvd
                } catch (Exception ex) {
                    vvd = null;
                }
            }//outer catch ends

/*	if( !(CarrierVisitPhaseEnum.DEPARTED.equals(vvPhase) || CarrierVisitPhaseEnum.CLOSED.equals(vvPhase))){
       def emailSender = api.getGroovyClassInstance("EmailSender")
       def sub = "PROC STOPPED : Vesvoy="+vesId+" NOT DEPARTED YET Please depart the vessel and re-execute NIS coding";
   	   emailSender.custSendEmail(emailAddr, sub, sub);
	   return false;
    }*/
            if (vvd == null) {
                def emailSender = api.getGroovyClassInstance("EmailSender")
                def sub = "PROC STOPPED :  OBCarrierId=" + vesId + " is not a vessel";
                emailSender.custSendEmail(emailAddr, sub, sub + " For YB barge the OBIntended should be Set" +
                        "and For Matson Barge OBActual One of the values is missing");
                return false;
            }

            vvd.setCvdTimeFirstAvailability(begindelivery);
            def activeUfv = unit.unitActiveUfv
            activeUfv.setFieldValue("ufvFlexDate05", null);
            activeUfv.setFieldValue("ufvFlexDate06", null);

            return begindelivery;

        } catch (Exception exc) {
            exc.printStackTrace();
        }
    }//Method ends


    public Object getCurrentFaciltyUnitFromUfv(Object aUfv) {
        def unit = null; def ufv = null;
        try {
            unit = aUfv.getUfvUnit();
            def fcy = com.navis.argo.ContextHelper.getThreadFacility();
            ufv = unit.getUfvForFacilityCompletedOnly(fcy); //If Departed get facility ufv
            if (ufv == null) {
                ufv = unit.unitActiveUfv; //Assign ufv from Facility Active unit
            }
        } catch (Exception e) {
            e.printStackTrace()
            return null;
        }
        return ufv
    }

    public String getDetentionDatesforCurrentFcyUfv(Object unit, String xml, Object api, gvyCmisUtil) {
        def xmlGvyString = xml
        try {
            def fcy = com.navis.argo.ContextHelper.getThreadFacility();
            def ufv = unit.getUfvForFacilityCompletedOnly(fcy); //If Departed get facility ufv
            gvyEventUtil = gvyEventUtil == null ? getGroovyClassInstance("GvyEventUtil") : gvyEventUtil

            def availDate = ufv.getFieldValue("ufvFlexDate02")
            def availDateStr = availDate != null ? gvyEventUtil.dateFormat(availDate, 'MM/dd/yyyy') : "null";
            def detnDate = ufv.getFieldValue("ufvFlexDate03")
            def detnDateStr = detnDate != null ? gvyEventUtil.dateFormat(detnDate, 'MM/dd/yyyy') : "null";
            def gvyCmisActionDetail = getGroovyClassInstance("GvyCmisActionDetail");
            def lastfreeDayStr = ufv.getFieldValue("ufvCalculatedLastFreeDay");
            Date lastfreeDate = gvyCmisActionDetail.getlastFreeDate(availDate, lastfreeDayStr, api)
            def lastFreeStgDt = lastfreeDate != null ? gvyEventUtil.dateFormat(lastfreeDate, 'MM/dd/yyyy') : lastfreeDate
            lastFreeStgDt = lastFreeStgDt != null ? "" + lastFreeStgDt : "null";

            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "availDt=", availDateStr);
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "dtnDueDt=", detnDateStr);
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "lastFreeStgDt=", lastFreeStgDt);
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "action=", "BDC");
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "lastAction=", "FREE");

        } catch (Exception e) {
            e.printStackTrace();
        }
        return xmlGvyString;
    }

    public void truckerCodingNotification(Object visit, String visitId) {
        def portToEmail = "1aktosdevteam@matson.com"
        try {
            if (!"BARGE".equals(visit.vvdVessel.vesVesselClass.vesclassVesselType.name)) {
                return;
            }
            def phase = visit.vvdVisitPhase
            boolean isYb = visitId.startsWith('YB') ? true : false
            def unitList = getUnitforObCarrier(visitId, isYb)
            if (!isYb && CarrierVisitPhaseEnum.DEPARTED.equals(phase) && unitList != null && unitList.size() > 0) {
                //A21
                def carrierId = visit.cvdCv;
                def emailSender = getGroovyClassInstance("EmailSender");
                def sub = " Vessel ready for TRUCKER CODING=" + carrierId;
                def nextFacility = visit.getFieldValue("cvdCv.cvNextFacility.fcyId")
                portToEmail = getEmialId(nextFacility);
                emailSender.custSendEmail(portToEmail, sub, sub);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    public String getEmialId(String port) {

        String emailId = getReferenceValue("TRCK_CODING_" + port, "TRCK_CODING_" + port, null, null, 1)  //A16
        if (emailId == null) {
            return "1aktosdevteam@matson.com"
        }
        return emailId
    }

    //A18
    public String setTruckerFromNotes(Object unit) {
        try {
            String notes = unit.getFieldValue("unitRemark");
            if (notes != null && notes.startsWith("N/P")) {
                String truck = notes.substring(notes.indexOf("/") + 2, notes.indexOf(","));
                truck = truck != null ? truck.trim() : null;
                unit.setFieldValue("unitFlexString07", truck); //A12
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void postNITtoGEMS(Object event) {
        println("Testing start event to GEMS for NIS_TRUCKER_ASSIGN");
        def unit = event.entity;
        def groovyEvent = new GroovyEvent(null, unit);
        groovyEvent.postNewEvent("NIS_TRUCKER_POST_MSG", "Automated NIT created");
        println("Testing end event to GEMS for NIS_TRUCKER_ASSIGN");
    }

}//Class Ends