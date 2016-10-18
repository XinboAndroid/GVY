/*
 *  Sr  Chg Date        Desc
 *  A1  GR  11/24/09    Set POL,OPL to facility if blank
 *  A2  GR  06/23/2010  Pass Chassis Info to NOW - 06/23/2010
 *  A3  GR  07/18/10    Updt Post Messages method
 *  A4  GR  09/14/10    Node2 Change - Updated method with Return Type
 *  A5  GR  09/23/10    TT#009202 - Suppress LTV for MG and Chassis if Primary unit=Container
						Commented Code that is not used
 *  A6  GR  12/13/10    Set locationTier for Pier2 Loads
 *  A7  GR  03/07/11    Added Null condition check
 *  A8  GR  04/05/12	   Set leg=E for OPT port of Discharge
 *  A9  SS  06/21/13    Update the POL with current facility
 */

import com.navis.argo.ContextHelper
import com.navis.services.business.event.Event
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.atoms.EquipClassEnum

public class GvyCmisEventUnitLoad
{

    public String getLoadedEquipClassMsg(String xmlData,Object event, Object gvyBaseClass) {
        def xmlGvyString = xmlData
        def xmlGvyChassis = ''
        def xmlGvyAcsry = ''
        def gvyCmisUtil = null

        def unit = event.getEntity()
        def gvyEvntObj = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
        gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");

        def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId")
        def dischargePort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")



        /* A5 Change
      def chassisNbr = gvyCmisUtil.getFieldValues(xmlGvyString, "chassisNumber=")
      def accessoryNbr = gvyCmisUtil.getFieldValues(xmlGvyString, "accessory=")
      def unitClass = gvyCmisUtil.getFieldValues(xmlGvyString, "unitClass=")
      */

        //A6,A7
        String prevPos = gvyEvntObj.getPreviousPropertyAsString(event, 'posName')
        prevPos = prevPos == null ? "" : prevPos
        if (prevPos.contains('P2A') || prevPos.contains('P29') || prevPos.contains('29Z')) {
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationTier=", "T2")
        }

        //Dport
        def dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")

        //Override the POL to facility
        def facilityId = unit.getFieldValue("unitActiveUfv.ufvFacility.fcyId")
        println("POL facility id " + facilityId)
        facilityId = facilityId != null ? facilityId : ''
        Event gvyEventObj = event.getEvent();
        def eventFcyId = facilityId
        if (gvyEventObj != null) {
            eventFcyId = gvyEventObj.getEvntFacility() != null ? gvyEventObj.getEvntFacility().fcyId : facilityId
        }
        println("XML string before POL set : " + xmlGvyString)
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "loadPort=", eventFcyId)
        println("XML string after POL set : " + xmlGvyString)
        //A9 Added By Solomon - Start
        if (eventFcyId != null) {
            def routing = unit.getUnitRouting();
            routing.setRtgPOL(RoutingPoint.findRoutingPoint(eventFcyId));
        }
        // Added By Solomon - End

        //Override misc1 value for Long haul vessel to NIS
        def lkpLocType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
        def aobcarrierId = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")
        lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''
        if (lkpLocType.equals('VESSEL') & gvyCmisUtil.isNISPort(dischPort)) {
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "misc1=", aobcarrierId)
        }
        println("aobcarrierId before Akutan load " + aobcarrierId)
        println("unit.getFieldValue(unitDeclaredIbCv.cvId) == " + unit.getFieldValue("unitDeclaredIbCv.cvId"))
        // compare eventFcyId with facilityId - to find vessvoy for barge load
        println("eventFcyId : " + eventFcyId +"facilityId  :"+facilityId)

        if ( (eventFcyId == "KQA"  || eventFcyId == "SDP"  || eventFcyId == "BBA"  || eventFcyId == "PML") && facilityId == "DUT" ) {
            def declaredOBVesType = null;
            println("inside eventFcyId : " + eventFcyId +"facilityId  :"+facilityId)
            //def ObCarrier = unit.getFieldValue("unitDeclaredIbCv.cvId")
            if (aobcarrierId != null) {
                declaredOBVesType = gvyCmisUtil.getVesselClassType(aobcarrierId);
            }
            declaredOBVesType = declaredOBVesType != null ? declaredOBVesType : '';
            // for empties without booking, IB visit should be populated
            Boolean isEmpty = false;
            def freightKind = unit.getFieldValue("unitFreightKind");
            freightKind = freightKind != null ? freightKind.getKey() : ''
            String declaredIBCV = unit.getFieldValue("unitDeclaredIbCv.cvId");
            println("freightKind : " + freightKind +" declaredIBCV: "+declaredIBCV +" declaredOBVesType: "+declaredOBVesType)

            if (freightKind != null && ( 'MTY'.equals(freightKind)  || 'FCL'.equals(freightKind))    && (''.equals(declaredOBVesType) || declaredOBVesType==null)) {
                declaredOBVesType = gvyCmisUtil.getVesselClassType(declaredIBCV);
            }


            println("inside declaredOBVesType : " + declaredOBVesType)

            if ("BARGE".equals(declaredOBVesType)) {
                println("inside 1 declaredOBVesType : " + declaredOBVesType)
                def declaredIBInVoyNbrForKQA = unit.getFieldValue("unitDeclaredIbCv.cvCvd.vvdIbVygNbr")
                println("declaredIBInVoyNbrForKQA : " + declaredIBInVoyNbrForKQA)
                if (declaredIBInVoyNbrForKQA != null) {
                    def actualVessel = declaredIBCV.length() > 5 ? declaredIBCV.substring(0,3) : 'null'
                    def actualVoyage = declaredIBInVoyNbrForKQA != null ? declaredIBInVoyNbrForKQA :declaredIBCV.length() > 5 ? declaredIBCV.substring(3,6) : 'null'

                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"actualVessel=",actualVessel)
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"actualVoyage=",actualVoyage)


                }
                aobcarrierId = unit.getFieldValue("unitDeclaredIbCv.cvId")



                println("facilityId: " +facilityId +" dischargePort "+dischargePort +"loadPort "+loadPort)

                if(  (loadPort!=null && loadPort!="") &&  (dischargePort!=null && dischargePort!="" ) ) {

                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"leg=",loadPort+'_'+dischargePort)
                }

            }
            else {
                aobcarrierId = unit.getFieldValue("unitDeclaredIbCv.cvId")
            }

        }else{
            println("event.event.eventTypeId: " + event.event.eventTypeId)

            if("UNIT_LOAD".equals(event.event.eventTypeId) && aobcarrierId!=null && aobcarrierId.length()>5){

                println("aobcarrierId: " + aobcarrierId)

                def actualVessel = aobcarrierId.length() > 5 ? aobcarrierId.substring(0,3) : 'null'
                def actualVoyage = aobcarrierId.length() > 5 ? aobcarrierId.substring(3,6) : 'null'

                // def actualVoyage = aobcarrierId != null ? aobcarrierId :declaredIBCV.length() > 5 ? declaredIBCV.substring(3,6) : 'null'

                println("actualVessel: " + actualVessel +" actualVoyage :"+actualVoyage)

                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"actualVessel=",actualVessel)
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"actualVoyage=",actualVoyage)
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"leg=",loadPort+'_'+dischargePort)


            }
        }
        println("aobcarrierId after Akutan load "+ aobcarrierId)

        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"vesvoy=",aobcarrierId)
        //println("xmlGvyString after "+ xmlGvyString)

        if("OPT".equals(dischPort)){
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"leg=","E");
        }

        //Added conditions in Generic Code
        xmlGvyString = unitLoadToNIS(unit,xmlGvyString,gvyCmisUtil,dischPort)
        //Formatting the Time Attrubites for Event Time Stamp Ordering
        java.util.Date date = new java.util.Date()
        //java.util.Date date = event.getEvent().getEventTime()

        def zone =  unit.getUnitComplex().getTimeZone();
        def fmtDate =  gvyEvntObj.formatDate(date, zone)
        def fmtTime = gvyEvntObj.formatTime(date,zone)
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"lastADate=",fmtDate)
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"lastATime=",fmtTime)
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"aDate=",fmtDate)
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"aTime=",fmtTime)


        try
        {

            def unitId = unit.getFieldValue("unitId")

            /*  A5 - Change
                 if(unitClass.equals("CONTAINER"))
                 {
                  if(!chassisNbr.equals('null'))
                   {
                      def chassisId = chassisNbr != null ? chassisNbr.substring(0,chassisNbr.length()-1) : 'null'
                      def chassisCd =  chassisNbr != null ? chassisNbr.substring(chassisNbr.length()-1) : 'null'

                      //Pass Record for Loaded Chassis
                      xmlGvyChassis = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"unitClass=","CHASSIS")
                      xmlGvyChassis = gvyCmisUtil.eventSpecificFieldValue(xmlGvyChassis,"chassisNumber=",chassisId)
                      xmlGvyChassis = gvyCmisUtil.eventSpecificFieldValue(xmlGvyChassis,"chassisCd=",chassisCd)

                      // compare eventFcyId with facilityId - to find vessvoy for barge load


                      if ((eventFcyId == "KQA"  || eventFcyId == "SDP"  || eventFcyId == "BBA"  || eventFcyId == "PML") && facilityId == "DUT" ) {

                         aobcarrierId = unit.getFieldValue("unitDeclaredIbCv.cvId")
                      }
                      xmlGvyChassis = gvyCmisUtil.eventSpecificFieldValue(xmlGvyChassis,"vesvoy=",aobcarrierId)
                      //Posting Chassis Msg
                      //gvyBaseClass.sendXml(xmlGvyChassis)
                      //A5- gvyCmisUtil.postMsgForAction(xmlGvyChassis,gvyBaseClass,"null")
                    }
                    if (!accessoryNbr.equals('null'))
                    {
                      //Pass Record for Loaded Acry
                      xmlGvyAcsry = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"unitClass=","ACCESSORY")
                      xmlGvyAcsry = gvyCmisUtil.eventSpecificFieldValue(xmlGvyAcsry,"accessory=",accessoryNbr)
                      xmlGvyAcsry = gvyCmisUtil.eventSpecificFieldValue(xmlGvyAcsry,"mgWeight=","%")
                      //Posting Accessory Msg
                      //gvyBaseClass.sendXml(xmlGvyAcsry)
                      //A5 - gvyCmisUtil.postMsgForAction(xmlGvyAcsry,gvyBaseClass,"null")
                    }

                   //Posting Container Msg
                   //gvyBaseClass.sendXml(xmlGvyString)
                   //A4 gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"null")
                   return xmlGvyString

                 }//If for Container Ends
                 else if(unitClass.equals("CHASSIS"))
                 {
                    if (!accessoryNbr.equals('null'))
                    {
                      //Pass Record for Loaded Acry
                      xmlGvyAcsry = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"unitClass=","ACCESSORY")
                      xmlGvyAcsry = gvyCmisUtil.eventSpecificFieldValue(xmlGvyAcsry,"accessory=",accessoryNbr)
                      xmlGvyAcsry = gvyCmisUtil.eventSpecificFieldValue(xmlGvyAcsry,"mgWeight=","%")
                      //Posting Accessory Msg
                      //gvyBaseClass.sendXml(xmlGvyAcsry)
                       //A5 - gvyCmisUtil.postMsgForAction(xmlGvyAcsry,gvyBaseClass,"null")
                    }
                   //Posting Chassis Msg
                   gvyBaseClass.sendXml(xmlGvyString)
                   //gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"null")

                 }// If for Chassis ends
                 else if(unitClass.equals("ACCESSORY"))
                 {
                     //Posting Chassis Msg
                     //gvyBaseClass.sendXml(xmlGvyString)
                     gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"null")
                 }//Else if for Accessory Ends
            */
            return xmlGvyString

        }catch(Exception e){
            e.printStackTrace()
        }

    }//getLoadedEquipClassMsg Ends

    /*
     //Method Checks if the Chassi is Attached to a RORO unit
      public boolean IschasAttachToRoRoUnit(Object event,Object api)
     {
       try
       {
         def equip = event.getEntity()
         def gvyFinderObj = api.getGroovyClassInstance("GvyCmisUnitFinderUtil");
         def unit = gvyFinderObj.findAttachedUnit(equip)
         if(unit != null)
         {
          //Adding Check for Event Ordering on UNIT_LOAD RORO for Chassis
          def lookup = api.getGroovyClassInstance("GvyVesselLookup");
          def position =  lookup.setDeckPositionType(unit);
          println('-----Position----:'+position+'--------unit.subsidiaryEquipment----'+unit.subsidiaryEquipment)

          if("RO".equals(position) && unit.subsidiaryEquipment != null) {
           println('INSIDE THE UNIT_PROPERTY_UPDATE DECK POISTION : FLASE')
           return false
          }
         }

       }catch(Exception e){
                  e.printStackTrace()
       }
       return true
     }

    */

    /*
    * Change for UNIT_LOAD LS,LOC,Truck
    * Added Conditions in Generic Code
    */
    public String unitLoadToNIS(Object unit,String xmlData,Object gvyCmisUtil,String dischPort)
    {
        def xmlGvyString = xmlData
        try{
            def aobcarrierVesId= unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesId")
            def islandPort = gvyCmisUtil.isNISPort(dischPort)
            if(islandPort){
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "7")
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "loc=", "NIS")
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "truck=", "9"+aobcarrierVesId)
            }
            //Being Set in Generic Rules
            /*  else if (!islandPort){
               xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "locationStatus=", "2")
               xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString, "loc=", "null")
               }*/
        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }

    // Auto Popluate POL and OPL to facilityId if Blank
    public void setPolIfBlank(Object unit){
        try{
            def pol =  unit.unitRouting.rtgPOL;
            def opl =  unit.unitRouting.rtgOPL;
            if(pol == null || opl == null){
                def fcyId = ContextHelper.getThreadFacility().getFcyId()
                def routing = unit.getUnitRouting();
                pol = pol == null ? routing.setRtgPOL(RoutingPoint.findRoutingPoint(fcyId)) : pol;
                opl = opl == null ? routing.setRtgOPL(RoutingPoint.findRoutingPoint(fcyId)) : opl;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void passBareChassisToNow(Object unit, Object api, Object event){
        def primaryClass = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
        def aobcarrierId = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")
        if(EquipClassEnum.CHASSIS.equals(primaryClass) && aobcarrierId.startsWith('ALE')){
            def gvyNow = api.getGroovyClassInstance("NowChassisTrackingBuilder")
            gvyNow.nowMessagesProcessor(event, api)
        }
    }

}