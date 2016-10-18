/*
* A1  GR   12/19/2010  TT#10125 Current time setto VST atdDate and atdTime
* A2  GR   07/21/2011  Set YB barge Leg correctly for VST action
* A3  GR   08/15/2011  Pulling out SingletonService class call
* A4  LC   03/26/2013  Add doCmisDataRefresh method to separate event
* A5  LC   06/18/2013  Modify call to processCmisEventFeedManipulationForRefresh in doCmisDataRefresh
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.event.Event
import com.navis.argo.business.reference.Equipment
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
import com.navis.framework.util.DateUtil;


public class GvyCmisDataProcessor {

    public String doIt(Object event)
    {
        println("In Class GvyCmisDataProcessor.doIt()")
        def gvyBaseClass = ''
        def groovyfinalXml  = ''
        try
        {
            //Calling Msg Formater class
            gvyBaseClass = new GroovyInjectionBase();
            def gvyTxtMsgFmt = gvyBaseClass.getGroovyClassInstance("GvyCmisTxtMsgFormatter");

            //Get OBJECT
            Object unitObj = event.getEntity()
            def isUnitObj = false

            //Gets EVENT ID
            def eventTypeAttr = ''
            Event gvyEventObj = event.getEvent()
            String eventType =  gvyEventObj.getEventTypeId()
            def doer = gvyEventObj.getEvntAppliedBy()
            def acetsMsg = getAcetsMsgType(doer)
            if(acetsMsg != null){
                eventTypeAttr = gvyTxtMsgFmt.doIt('msgType',acetsMsg)
            }else{
                eventTypeAttr = gvyTxtMsgFmt.doIt('msgType',eventType)
            }
            println('EventType ::'+eventType+' ACETS DOER ::'+acetsMsg)

            //Check For Unit Object to handle Reporting Tag Event Call
            if(unitObj instanceof Unit) {
                isUnitObj = true
            }

            //Getting UNIT_OBJ from EQUIP_OBJ
            if(unitObj instanceof EquipmentState){
                unitObj = getUnitFromEquipment(unitObj)
            }

            // EQUIP CLASS
            def equiClass =unitObj.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            def equiClassKey = equiClass != null ? equiClass.getKey() : ''

            //ACTION SPECIFIC CLASS CALLS
            def unitDetails = gvyBaseClass.getGroovyClassInstance("GvyCmisUnitDetail");
            def unitDetailsAttr = unitDetails.doIt(equiClassKey,gvyTxtMsgFmt,unitObj, event);

            def unitEquip = gvyBaseClass.getGroovyClassInstance("GvyCmisEquiDetail");
            def equipDetailAttr = unitEquip.doIt(gvyTxtMsgFmt,unitObj, event, isUnitObj, gvyBaseClass,eventType);

            def unitPhyStatus = gvyBaseClass.getGroovyClassInstance("GvyCmisPhysicalStatusDetail");
            def phyStatusAttr =unitPhyStatus.doIt(eventType,gvyTxtMsgFmt,unitObj);

            def unitShipmentDetails = gvyBaseClass.getGroovyClassInstance("GvyCmisShipmentDetail");
            def shipmentDetailsAttr =unitShipmentDetails.doIt(gvyTxtMsgFmt,unitObj,eventType,gvyBaseClass,event, isUnitObj );

            def unitRtgProcess = gvyBaseClass.getGroovyClassInstance("GvyCmisRtgProcessDetail");
            def rtgProcessAttr =unitRtgProcess.doIt(eventType,gvyTxtMsgFmt,unitObj,gvyBaseClass);

            def unitRouting = gvyBaseClass.getGroovyClassInstance("GvyCmisRoutingDetail");
            def routingAttr =unitRouting.doIt(gvyTxtMsgFmt,unitObj,eventType);

            def unitposition = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionDetail");
            def positionAttr =unitposition.doIt(eventType,gvyTxtMsgFmt,unitObj,eventType,event);

            def unitpositionProc = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionProcessDetail");
            def positionProcAttr =unitpositionProc.doIt(unitObj,gvyTxtMsgFmt,eventType,event);

            def flexFields = gvyBaseClass.getGroovyClassInstance("GvyCmisFlexFieldDetail");
            def unitFlexFieldsAttr =flexFields.doIt(gvyTxtMsgFmt,unitObj, event, eventType, isUnitObj, gvyBaseClass);

            def actionDetail = gvyBaseClass.getGroovyClassInstance("GvyCmisActionDetail");
            def actionDetailAttr =actionDetail.doIt(gvyTxtMsgFmt, gvyEventObj, gvyBaseClass,unitObj);

            def commentNotes = gvyBaseClass.getGroovyClassInstance("GvyCmisCommentNotesField");
            def commentNotesAttr = commentNotes.doIt(gvyEventObj,eventType,gvyTxtMsgFmt,unitObj);

            def unitChassisObj = gvyBaseClass.getGroovyClassInstance("GvyCmisChassisAttributes");
            def unitChassisAttr = unitChassisObj.getChassisAttributes(gvyTxtMsgFmt,unitObj,equiClassKey,eventType);

            def unitGateObj = gvyBaseClass.getGroovyClassInstance("GvyCmisGateData");
            def unitGateAttr = unitGateObj.gateAttributes(unitObj, gvyTxtMsgFmt, gvyBaseClass,eventType );


            def msgString = eventTypeAttr+' '+unitDetailsAttr+' '+equipDetailAttr+' '+phyStatusAttr+' '+shipmentDetailsAttr+' '+rtgProcessAttr+' '+routingAttr+' '+positionAttr+' '+positionProcAttr+' '+unitFlexFieldsAttr+' '+actionDetailAttr+' '+commentNotesAttr+' '+unitChassisAttr+' '+unitGateAttr

            //Creates Groovy Xml
            def groovyXml = gvyTxtMsgFmt.createGroovyXml(msgString)
            //def groovyfinalXml =  getEventSpecificValues(eventType,groovyXml, gvyBaseClass,event,unitObj, isUnitObj, equiClassKey)

            def eventFeedObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventFeedManipulation");
            groovyfinalXml = eventFeedObj.processCmisEventFeedManipulation(eventType,groovyXml, gvyBaseClass,event,unitObj, isUnitObj, equiClassKey)

            println("GVY XML : "+groovyfinalXml)

        }catch(Exception e){
            e.printStackTrace()
            //def gvyExceptionObj = gvyBaseClass.getGroovyClassInstance("GvyCmisExceptionProcess");
            //gvyExceptionObj.processException(e)
        }

        return groovyfinalXml

    }

//A4
    public String doCmisDataRefresh(Object event)
    {
        println("In Class GvyCmisDataProcessor.doCmisDataRefresh()")
        def gvyBaseClass = ''
        def groovyfinalXml  = ''
        try
        {
            //Calling Msg Formater class
            gvyBaseClass = new GroovyInjectionBase();
            def gvyTxtMsgFmt = gvyBaseClass.getGroovyClassInstance("GvyCmisTxtMsgFormatter");

            //Get OBJECT
            Object unitObj = event.getEntity()
            def isUnitObj = false

            //Gets EVENT ID
            def eventTypeAttr = ''
            Event gvyEventObj = event.getEvent()
            String eventType =  gvyEventObj.getEventTypeId()
            def doer = gvyEventObj.getEvntAppliedBy()
            def acetsMsg = getAcetsMsgType(doer)
            if(acetsMsg != null){
                eventTypeAttr = gvyTxtMsgFmt.doIt('msgType',acetsMsg)
            }else{
                eventTypeAttr = gvyTxtMsgFmt.doIt('msgType',eventType)
            }
            println('EventType ::'+eventType+' ACETS DOER ::'+acetsMsg)

            //Check For Unit Object to handle Reporting Tag Event Call
            if(unitObj instanceof Unit) {
                isUnitObj = true
            }

            //Getting UNIT_OBJ from EQUIP_OBJ
            if(unitObj instanceof EquipmentState){
                unitObj = getUnitFromEquipment(unitObj)
            }

            // EQUIP CLASS
            def equiClass =unitObj.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            def equiClassKey = equiClass != null ? equiClass.getKey() : ''

            //ACTION SPECIFIC CLASS CALLS
            def unitDetails = gvyBaseClass.getGroovyClassInstance("GvyCmisUnitDetail");
            def unitDetailsAttr = unitDetails.doIt(equiClassKey,gvyTxtMsgFmt,unitObj, event);

            def unitEquip = gvyBaseClass.getGroovyClassInstance("GvyCmisEquiDetail");
            def equipDetailAttr = unitEquip.doIt(gvyTxtMsgFmt,unitObj, event, isUnitObj, gvyBaseClass,eventType);

            def unitPhyStatus = gvyBaseClass.getGroovyClassInstance("GvyCmisPhysicalStatusDetail");
            def phyStatusAttr =unitPhyStatus.doIt(eventType,gvyTxtMsgFmt,unitObj);

            def unitShipmentDetails = gvyBaseClass.getGroovyClassInstance("GvyCmisShipmentDetail");
            def shipmentDetailsAttr =unitShipmentDetails.doIt(gvyTxtMsgFmt,unitObj,eventType,gvyBaseClass,event, isUnitObj );

            def unitRtgProcess = gvyBaseClass.getGroovyClassInstance("GvyCmisRtgProcessDetail");
            def rtgProcessAttr =unitRtgProcess.doIt(eventType,gvyTxtMsgFmt,unitObj,gvyBaseClass);

            def unitRouting = gvyBaseClass.getGroovyClassInstance("GvyCmisRoutingDetail");
            def routingAttr =unitRouting.doIt(gvyTxtMsgFmt,unitObj,eventType);

            def unitposition = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionDetail");
            def positionAttr =unitposition.doIt(eventType,gvyTxtMsgFmt,unitObj,eventType,event);

            def unitpositionProc = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionProcessDetail");
            def positionProcAttr =unitpositionProc.doCmisDataRefresh(unitObj,gvyTxtMsgFmt,eventType,event);

            def flexFields = gvyBaseClass.getGroovyClassInstance("GvyCmisFlexFieldDetail");
            def unitFlexFieldsAttr =flexFields.doIt(gvyTxtMsgFmt,unitObj, event, eventType, isUnitObj, gvyBaseClass);

            def actionDetail = gvyBaseClass.getGroovyClassInstance("GvyCmisActionDetail");
            def actionDetailAttr =actionDetail.doIt(gvyTxtMsgFmt, gvyEventObj, gvyBaseClass,unitObj);

            def commentNotes = gvyBaseClass.getGroovyClassInstance("GvyCmisCommentNotesField");
            def commentNotesAttr = commentNotes.doIt(gvyEventObj,eventType,gvyTxtMsgFmt,unitObj);

            def unitChassisObj = gvyBaseClass.getGroovyClassInstance("GvyCmisChassisAttributes");
            def unitChassisAttr = unitChassisObj.getChassisAttributes(gvyTxtMsgFmt,unitObj,equiClassKey,eventType);

            def unitGateObj = gvyBaseClass.getGroovyClassInstance("GvyCmisGateData");
            def unitGateAttr = unitGateObj.gateAttributes(unitObj, gvyTxtMsgFmt, gvyBaseClass,eventType );


            def msgString = eventTypeAttr+' '+unitDetailsAttr+' '+equipDetailAttr+' '+phyStatusAttr+' '+shipmentDetailsAttr+' '+rtgProcessAttr+' '+routingAttr+' '+positionAttr+' '+positionProcAttr+' '+unitFlexFieldsAttr+' '+actionDetailAttr+' '+commentNotesAttr+' '+unitChassisAttr+' '+unitGateAttr

            //Creates Groovy Xml
            def groovyXml = gvyTxtMsgFmt.createGroovyXml(msgString)
            //def groovyfinalXml =  getEventSpecificValues(eventType,groovyXml, gvyBaseClass,event,unitObj, isUnitObj, equiClassKey)

            def eventFeedObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventFeedManipulation");
            groovyfinalXml = eventFeedObj.processCmisEventFeedManipulationForRefresh(eventType,groovyXml, gvyBaseClass,event,unitObj, isUnitObj, equiClassKey)

            // println("GVY XML : "+groovyfinalXml)

        }catch(Exception e){
            gvyBaseClass.log("Exception in GvyCmisDataProcessor.doCmisDataRefresh()" + e);
        }

        return groovyfinalXml

    }

    public Object getUnitFromEquipment(Object EquipObj)
    {
        def unit = ''
        try
        {
            def equiId = EquipObj.getFieldValue("eqsEquipment.eqIdFull")
            def injBase = new GroovyInjectionBase();
            def complex = ContextHelper.getThreadComplex();
            def unitFinder = injBase.getUnitFinder();
            def eq = Equipment.loadEquipment( equiId);
            unit = unitFinder.findAttachedUnit(complex, eq);

        }catch(Exception e){
            e.printStackTrace()
        }

        return unit
    }



    public String doTheVessel(Object event, String action)
    {
        println("In Class GvyCmisDataProcessor.doTheVessel()")

        //Calling Msg Formater class
        def groovyXml = ''
        try
        {
            def gvyBaseClass = new GroovyInjectionBase()
            def gvyTxtMsgFmt = gvyBaseClass.getGroovyClassInstance("GvyCmisTxtMsgFormatter");
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            def gvyVesselLookup = gvyBaseClass.getGroovyClassInstance("GvyVesselLookup");

            //Get OBJECT
            def visit = event.getEntity();
            def zone =  visit.getCvdCv().getCvComplex() .getTimeZone();

            //Gets EVENT ID
            Event gvyEventObj = event.getEvent();
            String eventType =  gvyEventObj.getEventTypeId();

            //EVENT TYPE
            def eventTypeAttr = gvyTxtMsgFmt.doIt("msgType",eventType);

            def unitClassAttr = gvyTxtMsgFmt.doIt("unitClass","VESSELVISIT");

            def visitId = visit.getFieldValue("cvdCv.cvId");
            def vistAttr = gvyTxtMsgFmt.doIt("visit",visitId);

            def facility = visit.getFieldValue("cvdCv.cvFacility.fcyId")
            def facilityAttr = gvyTxtMsgFmt.doIt("facility",facility);

            def nextFacility = visit.getFieldValue("cvdCv.cvNextFacility.fcyId")
            def nextFacilityAttr = gvyTxtMsgFmt.doIt("nextFacility",nextFacility);


            def line = event.getPropertyAsString("VesselLineOperator")
            def lineAttr = gvyTxtMsgFmt.doIt("line",line);

            def vessel = event.getPropertyAsString("VesselId")
            def vesselAttr = gvyTxtMsgFmt.doIt("vesselVes",vessel);

            def ibVoyage = visit.getFieldValue("vvdIbVygNbr")
            def ibVoyageAttr = gvyTxtMsgFmt.doIt("ibVoyage",ibVoyage);

            def obVoyage = visit.getFieldValue("vvdObVygNbr")
            def obVoyageAttr = gvyTxtMsgFmt.doIt("obVoyage",obVoyage);

            def phase = visit.getFieldValue("cvdCv.cvVisitPhase").key;
            if(phase != null && phase.length() > 2) {
                phase = phase.substring(2);
            }
            def phaseAttr = gvyTxtMsgFmt.doIt("phase",phase);

            def etaDate = visit.getFieldValue("cvdETA")
            def etaDateAttr = gvyTxtMsgFmt.doIt("etaDate",gvyEventUtil.formatDate(etaDate,zone));
            def etaTimeAttr = gvyTxtMsgFmt.doIt("etaTime",gvyEventUtil.formatTime(etaDate,zone));

            def etdDate = visit.getFieldValue("cvdETD");
            def etdDateAttr = gvyTxtMsgFmt.doIt("etdDate",gvyEventUtil.formatDate(etdDate,zone));
            def etdTimeAttr = gvyTxtMsgFmt.doIt("etdTime",gvyEventUtil.formatTime(etdDate,zone));

            def ataDate = visit.getFieldValue("cvdCv.cvATA")
            def ataDateAttr = gvyTxtMsgFmt.doIt("ataDate",gvyEventUtil.formatDate(ataDate,zone));
            def ataTimeAttr = gvyTxtMsgFmt.doIt("ataTime",gvyEventUtil.formatTime(ataDate,zone));

            def atdDate = visit.getFieldValue("cvdCv.cvATD")
            def aDate  = event.getEvent().getEvntAppliedDate(); //a1

            def atdDateAttr = gvyTxtMsgFmt.doIt("atdDate",gvyEventUtil.formatDate(aDate,zone)); //a1
            def atdTimeAttr = gvyTxtMsgFmt.doIt("atdTime",gvyEventUtil.formatTime(aDate,zone)); //a1

            // Action VST or EDT
            def actionAttr = gvyTxtMsgFmt.doIt("action", action);


            def aDateAttr = gvyTxtMsgFmt.doIt("aDate",gvyEventUtil.formatDate(aDate,zone));
            def aTimeAttr = gvyTxtMsgFmt.doIt("aTime",gvyEventUtil.formatTime(aDate,zone));

            //DOER
            def doer = event.getEvent().getEvntAppliedBy()
            def doerAttr = gvyTxtMsgFmt.doIt('doer',doer)

            def legAttr = gvyTxtMsgFmt.doIt("leg",getLeg(vessel,visit,gvyVesselLookup));

            def msgString = eventTypeAttr+' '+unitClassAttr+' '+vistAttr+' '+facilityAttr+' '+nextFacilityAttr+' '+lineAttr+' '+vesselAttr+' '+ibVoyageAttr+' '+obVoyageAttr+' '+phaseAttr+' '+etaDateAttr+' '+etaTimeAttr+' '+etdDateAttr+' '+etdTimeAttr+' '+ataDateAttr+' '+ataTimeAttr+' '+atdDateAttr+' '+atdTimeAttr+' '+actionAttr+' '+aDateAttr+' '+aTimeAttr+' '+doerAttr+' '+legAttr;

            //Creates Groovy Xml
            groovyXml = gvyTxtMsgFmt.createGroovyXml(msgString);

            //def groovyfinalXml =  getEventSpecificValues(eventType,groovyXml, gvyBaseClass,event,unitObj, isUnitObj, equiClassKey)

            println("GVY XML : "+groovyXml)

        }catch(Exception e){
            e.printStackTrace()
        }

        return groovyXml;

    }

    public String getLeg(Object vessel, Object visit, Object gvyVesselLookup) { //A2
        def service = visit.cvdItinerary.itinId;
        println("Service : "+service);
        // Dummy value for the service leg calculation, need to revisit.
        if("ALASKA-STD".equals(service) || "ANK-KDK-TAC-STD".equals(service)) { //D031803
            return "S";
        } else if("DUT-KQA-DUT-STD".equals(service) ) {
            def fcyId = visit.getFieldValue("cvdCv.cvFacility.fcyId")
            def nextFacility = visit.getFieldValue("cvdCv.cvNextFacility.fcyId")
            def nextFcyId = nextFacility != null ? nextFacility : null;
            println("Facility : "+fcyId);
            println("Next Fcy Id : "+nextFcyId);
            if("DUT".equals(fcyId) && nextFcyId!= null && "KQA".equals(nextFcyId)) {
                return "N";
            } else if("KQA".equals(fcyId) && nextFcyId!= null && "DUT".equals(nextFcyId)) {
                return "S";
            } else if("DUT".equals(fcyId) && nextFcyId==null) {
                return "S";
            }
        }
        return "S";//D031803

    }

    public String getAcetsMsgType(String doer)
    {
        String msgType = null;
        try
        {
            String[] doerArr = doer.split(":");
            if(doerArr != null && doerArr.length ==3 && doerArr[0].startsWith("snx")){
                if(doerArr[1].equals("ACETS")){
                    msgType = doerArr[2];
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return msgType;
    }

    //A5
    public String doItNewVess(Object event, Object unit)
    {
        println("In Class GvyCmisDataProcessor.doIt()")
        def gvyBaseClass = ''
        def groovyfinalXml  = ''
        try
        {
            //Calling Msg Formater class
            gvyBaseClass = new GroovyInjectionBase();
            def gvyTxtMsgFmt = gvyBaseClass.getGroovyClassInstance("GvyCmisTxtMsgFormatter");

            //Get OBJECT
            Object unitObj = unit;
            def isUnitObj = false

            //Gets EVENT ID
            def eventTypeAttr = ''
            Event gvyEventObj = event.getEvent()
            String eventType =  gvyEventObj.getEventTypeId()
            def doer = gvyEventObj.getEvntAppliedBy()
            def acetsMsg = getAcetsMsgType(doer)
            if(acetsMsg != null){
                eventTypeAttr = gvyTxtMsgFmt.doIt('msgType',acetsMsg)
            }else{
                eventTypeAttr = gvyTxtMsgFmt.doIt('msgType',eventType)
            }
            println('EventType ::'+eventType+' ACETS DOER ::'+acetsMsg)

            //Check For Unit Object to handle Reporting Tag Event Call
            if(unitObj instanceof Unit) {
                isUnitObj = true
            }

            //Getting UNIT_OBJ from EQUIP_OBJ
            if(unitObj instanceof EquipmentState){
                unitObj = getUnitFromEquipment(unitObj)
            }

            // EQUIP CLASS
            def equiClass =unitObj.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            def equiClassKey = equiClass != null ? equiClass.getKey() : ''

            //ACTION SPECIFIC CLASS CALLS
            def unitDetails = gvyBaseClass.getGroovyClassInstance("GvyCmisUnitDetail");
            def unitDetailsAttr = unitDetails.doIt(equiClassKey,gvyTxtMsgFmt,unitObj, event);

            def unitEquip = gvyBaseClass.getGroovyClassInstance("GvyCmisEquiDetail");
            def equipDetailAttr = unitEquip.doIt(gvyTxtMsgFmt,unitObj, event, isUnitObj, gvyBaseClass,eventType);

            def unitPhyStatus = gvyBaseClass.getGroovyClassInstance("GvyCmisPhysicalStatusDetail");
            def phyStatusAttr =unitPhyStatus.doIt(eventType,gvyTxtMsgFmt,unitObj);

            def unitShipmentDetails = gvyBaseClass.getGroovyClassInstance("GvyCmisShipmentDetailNewVes");
            def shipmentDetailsAttr =unitShipmentDetails.doIt(gvyTxtMsgFmt,unitObj,eventType,gvyBaseClass,event, isUnitObj );

            def unitRtgProcess = gvyBaseClass.getGroovyClassInstance("GvyCmisRtgProcessDetail");
            def rtgProcessAttr =unitRtgProcess.doIt(eventType,gvyTxtMsgFmt,unitObj,gvyBaseClass);

            def unitRouting = gvyBaseClass.getGroovyClassInstance("GvyCmisRoutingDetail");
            def routingAttr =unitRouting.doIt(gvyTxtMsgFmt,unitObj,eventType);

            def unitposition = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionDetailNewVess");
            def positionAttr =unitposition.doIt(eventType,gvyTxtMsgFmt,unitObj,eventType,event);

            def unitpositionProc = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionProcessDetailNewVes");
            def positionProcAttr =unitpositionProc.doIt(unitObj,gvyTxtMsgFmt,eventType,event);

            def flexFields = gvyBaseClass.getGroovyClassInstance("GvyCmisFlexFieldDetail");
            def unitFlexFieldsAttr =flexFields.doIt(gvyTxtMsgFmt,unitObj, event, eventType, isUnitObj, gvyBaseClass);

            def actionDetail = gvyBaseClass.getGroovyClassInstance("GvyCmisActionDetailForNewVes");
            def actionDetailAttr =actionDetail.doIt(gvyTxtMsgFmt, gvyEventObj, gvyBaseClass,unitObj);

            def commentNotes = gvyBaseClass.getGroovyClassInstance("GvyCmisCommentNotesField");
            def commentNotesAttr = commentNotes.doIt(gvyEventObj,eventType,gvyTxtMsgFmt,unitObj);

            def unitChassisObj = gvyBaseClass.getGroovyClassInstance("GvyCmisChassisAttributes");
            def unitChassisAttr = unitChassisObj.getChassisAttributes(gvyTxtMsgFmt,unitObj,equiClassKey,eventType);

            def unitGateObj = gvyBaseClass.getGroovyClassInstance("GvyCmisGateData");
            def unitGateAttr = unitGateObj.gateAttributes(unitObj, gvyTxtMsgFmt, gvyBaseClass,eventType );


            def msgString = eventTypeAttr+' '+unitDetailsAttr+' '+equipDetailAttr+' '+phyStatusAttr+' '+shipmentDetailsAttr+' '+rtgProcessAttr+' '+routingAttr+' '+positionAttr+' '+positionProcAttr+' '+unitFlexFieldsAttr+' '+actionDetailAttr+' '+commentNotesAttr+' '+unitChassisAttr+' '+unitGateAttr

            //Creates Groovy Xml
            def groovyXml = gvyTxtMsgFmt.createGroovyXml(msgString)
            //def groovyfinalXml =  getEventSpecificValues(eventType,groovyXml, gvyBaseClass,event,unitObj, isUnitObj, equiClassKey)

            def eventFeedObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventFeedManipulation");
            groovyfinalXml = eventFeedObj.processCmisEventFeedManipulation(eventType,groovyXml, gvyBaseClass,event,unitObj, isUnitObj, equiClassKey)

            // println("GVY XML : "+groovyfinalXml)

        }catch(Exception e){
            e.printStackTrace()
            //def gvyExceptionObj = gvyBaseClass.getGroovyClassInstance("GvyCmisExceptionProcess");
            //gvyExceptionObj.processException(e)
        }

        return groovyfinalXml

    }


}//Class Ends