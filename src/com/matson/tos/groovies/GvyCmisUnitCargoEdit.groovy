/*
* A1  GR   12/21/09 Added trim to destination  and POD value
* A2  GR   01/28/10 Removed FREE Msg for Detention SIT
* A3 GR    10/08/10  Gems : Uptd message posting method to Incorporate
                     Message specific field updates(example : AVL)
			         Depending Class GvyCmisUtil
* A4  GR   11/09/10  Issue: Updating POD thats not on Obcarrier itinerary
                     Fix: for NIS Update the OBcarrier to BARGE and then update the POD
* A5 LC    10/18/12  Issue: Destination changed for SIT_ASSIGN, SIT_UNASSIGN, UNIT_PROPERTY_UPDATE
*                    and group id is 'YB', the group id remains = 'YB'
*                    Fix: when destination changes and group id = 'YB', YB_UNASSIGN event is invoked
*/

import com.navis.argo.business.reference.RoutingPoint;
import com.navis.argo.business.model.CarrierVisit;
import com.navis.argo.ContextHelper;
import com.navis.services.business.event.GroovyEvent;

public class GvyCmisUnitCargoEdit
{
    def cmisActionList = ''
    def locationStatus = ''
    def gvyEventUtil = null
    def isPodUpdated = false
    def _previousPod = ''
    def gvyCmisUtil = null

    StringBuffer buffFieldName = new StringBuffer();
    /*
     * Method Processes the Updates fields in event UNIT_PROPERTY_UPDATE
     * 1] Read the event obj and stores the Updated Field Values
     * 2] Reads the field obj and process cmis msg for updates Fields
     */
    public String unitUpdateProcess(String xmlData,Object event,Object gvyBaseClass, boolean detnMsg,String previousDischPort)
    {
        def xmlGvyString = xmlData
        boolean postMsgEDT = false;
        try
        {
            //Read & Store Event Fields into an EventField Object
            def unit = event.getEntity()
            gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            cmisActionList = gvyBaseClass.getGroovyClassInstance("GvyCmisListAction");

            //Set location status field
            locationStatus = getlocationStatus(unit,gvyCmisUtil)

            Map mapEvntFld = gvyEventUtil.eventFieldChangedValues(event, gvyBaseClass)

            //Read Fields and stores values into StringBuffer Objects for processing
            readEventChangedFields(mapEvntFld)

            //Check For Detention Msg
            println("Detention MSG :::"+detnMsg)
            if(detnMsg)
            {
                def eventId = event.event.eventTypeId
                if(!(eventId.equals("SIT_ASSIGN") || eventId.equals("SIT_UNASSIGN"))){
                    cmisActionList.setActionList("FREE")
                }
                cmisActionList.setActionList("EDT")
            }

            //Gets Field specific action & Post Message
            String evntFieldName = new String(buffFieldName)
            String [] fldNames = evntFieldName != null ? evntFieldName.split(' ') : null;
            for(aFldName in fldNames)
            {
                def fldAction = getChangedFieldAction(aFldName.trim())
                //println("unit_property_update::Field Action ::"+fldAction)
                if(fldAction != null && fldAction.length() > 0 )
                {
                    postFieldSpecMsg(xmlGvyString,gvyBaseClass,gvyCmisUtil,fldAction,unit,event,previousDischPort)
                }
            }

            if(event.event.eventTypeId.equals('REVIEW_FOR_STOW')){
                cmisActionList.setActionList("RFS")
            }

            //Post Cmis msg after appending the required action
            LinkedHashSet actionList = cmisActionList.getActionList();
            println("actionList :::::"+actionList.size())
            for(aAction in actionList)
            {
                println("UNIT_PROPERTY_UPDT_ACTION_MSG_POSTING ::"+aAction);
                // gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,aAction)
                gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,aAction, unit, event,gvyEventUtil) //A3
            }

        }catch(Exception e){
            e.printStackTrace()
        }
        return ''
    }//Method unitUpdateProcess Ends

    /*
    * Method reads the Updated field values from the object
    * and stores the values in a string buffer for processing
    */
    public void readEventChangedFields(Object mapEvntField)
    {
        def processFlag = false;
        def isappended = false;
        try
        {
            Iterator it = mapEvntField.keySet().iterator();
            while (it.hasNext())
            {
                def aField = it.next();
                def aEvntFieldObj = mapEvntField.get(aField)

                //Fetch Updated Field Values
                def fieldname = aEvntFieldObj.getFieldName()
                def previousValue = aEvntFieldObj.getpreviousValue()
                previousValue = previousValue != null ? previousValue : ''
                def currentValue = aEvntFieldObj.getCurrentValue()
                currentValue = currentValue != null ? currentValue : ''

                /* Append updated Field Name and Prev Value.If previous value does not equal to
               Current value then append as non build in events register fld in event history iwth no change */
                if(isPodUpdated && !isappended){
                    buffFieldName.append('rtgPOD1 ')
                    isappended = true
                }
                else if(!previousValue.equals(currentValue)){
                    buffFieldName.append(fieldname+' ')
                }
                //println('fieldname ::'+fieldname+'  previousValue::'+previousValue+'   currentValue::'+currentValue)

            }//While Ends
        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method readEventChangedFields Ends


    /* Method post messages for Field Specific Cmis Action */
    public void postFieldSpecMsg(String xmlData,Object gvyBaseClass,Object gvyCmisUtil, String fieldAction, Object unit, Object event,String previousDischPort)
    {
        boolean msgPosted = false;
        def xmlGvyString = xmlData
        try
        {
            //OB Carrier msg flag
            def intdObCarrierId=unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
            intdObCarrierId = intdObCarrierId != null ? intdObCarrierId : ''
            boolean postObCarrierMsg =  (intdObCarrierId.equals('GEN_TRUCK') || intdObCarrierId.equals('GEN_VESSEL')
                    || intdObCarrierId.equals('GEN_CARRIER')) ? false : true

            //def locationStatus = gvyCmisUtil.getFieldValues(xmlGvyString, "locationStatus=")
            def destPort = gvyCmisUtil.getFieldValues(xmlGvyString, "dPort=")
            def srv = gvyCmisUtil.getFieldValues(xmlGvyString, "srv=")

            //def islandPort =  gvyCmisUtil.isNISPort(destPort)
            //println("ISland POrt ::"+islandPort+"    locationStatus::"+locationStatus+"   svr ::"+srv)

            if(fieldAction.equals('RTG'))
            {
                def rerouteEvntFeed = gvyBaseClass.getGroovyClassInstance("GvyCmisCargoEditUnitReroute")
                rerouteEvntFeed.processUnitRerouteCmisFeed(xmlGvyString, gvyBaseClass, event, unit, cmisActionList, false, previousDischPort)
            }
            //Pass action BDC & NIB Msg Type
            else if(fieldAction.equals('BDC'))
            {
                //Generating NIB & NIE on cmis side
                /* if(locationStatus.equals('7')) {
                     cmisActionList .setActionList("NIB")
                 }
                else{
                     cmisActionList .setActionList("BDC")
                 }*/
                cmisActionList.setActionList("BDC")
            }
            else if(fieldAction.equals('INOB') && postObCarrierMsg)
            {
                //overwrite ACTION=AVL & Post Msg
                cmisActionList .setActionList("AVL")
                //overwrite ACTION=BDC & Post Msg
                cmisActionList .setActionList("BDC")
            }
            else if(fieldAction.equals('NIT'))
            {   //NIT (Check if not on Barge and srv=MAT)
                if(/*islandPort &&*/ !locationStatus.equals('7') && srv.equals('MAT'))   {
                    //overwrite ACTION=NIT & Post Msg
                    cmisActionList .setActionList("NIT")
                }
                //EDT
                cmisActionList .setActionList("EDT")
            }
            //Check to pass other MSG types(TYP,TMP,PMR,PO#)
            else {
                //overwrite the ACTION Attr & Post Msg
                cmisActionList .setActionList(fieldAction)
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }//Method postUnitUpdateMsg Ends

    /* Method Maps the Updated Field to the relevant Cmis Action */
    public Object getChangedFieldAction(String fieldName)
    {
        def chgFieldAction = ''

        Map mapType = new HashMap()
        mapType.put('rtgPOD1','RTG')
        mapType.put('gdsDestination','BDC')
        mapType.put('gdsConsigneeBzu','NIT')
        mapType.put('gdsShipperBzu','EDT')
        mapType.put('gdsBlNbr','BDC')
        mapType.put('unitSealNbr1','EDT')
        mapType.put('gdsCommodity','EDT')
        mapType.put('unitRemark','EDT')
        mapType.put('unitFlexString02','EDT')
        mapType.put('unitGoodsAndCtrWtKg','EDT')
        mapType.put('gdsConsigneeAsString','NIT')
        mapType.put('rfreqTempRequiredC','TMP')
        //mapType.put('unitFlexString07','TMP')
        mapType.put('unitFreightKind','EDT')
        mapType.put('unitGoodsAndCtrWtKg','EDT')
        mapType.put('gdsShipperAsString','EDT')
        mapType.put('eqEquipType','TYP')
        mapType.put('ufvFlexDate01','PMR')
        mapType.put('rtgPOL','BDC')
        mapType.put('unitFlexString01','PO#')
        mapType.put('ufvIntendedObCv','INOB')
        mapType.put('unitLineOperator','EDT')
        mapType.put('ufvFlexDate02','EDT')
        mapType.put('ufvFlexDate03','EDT')
        mapType.put('unitFlexString04','EDT')
        mapType.put('unitFlexString05','EDT')

        chgFieldAction = mapType.get(fieldName) != null ? mapType.get(fieldName) : null

        return chgFieldAction
    }//Method getChangedFieldAction Ends


    public String AssignTrucker(String xmlData,Object gvyCmisUtil,Object unit )
    {
        def xmlGvyString = xmlData
        //overwrite ACTION=EDT
        def truck = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
        truck = truck != null ? truck : "null"
        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"truck=",truck)

        return  xmlGvyString
    }

    public String getlocationStatus(Object unit, Object gvyCmisUtil){
        try
        {
            def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''
            def lkpCarrierId = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")
            def locStatus = lkpLocType.equals('VESSEL') && gvyCmisUtil.getVesselClassType(lkpCarrierId).equals('BARGE') ? '7' : ''
            return locStatus
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    /*
     * Method Updates
     * 1.Event Field Change History List
     * 2.Updates POD by Looking up the Destination value
     * 3.Looks up and Updates the OB carrier if POD is GUM
    */
/*    public String UpdateCargoEditFields(Object event,Object unit, Object gvyBaseClass,Object gvyEventUtil)
    {
       def prevDischPort = null;
       try{
        //UNIT_REROUTE_PROCESSING
       def destination = unit.getFieldValue("unitGoods.gdsDestination")
       prevDischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
       def podLookup = gvyBaseClass.getGroovyClassInstance("GvyRefDataLookup");
       def pod = podLookup.lookupPod(destination);
       def podRgtPoint = RoutingPoint.findRoutingPoint(pod);
       if(podRgtPoint != null){
         unit.getUnitRouting().setRtgPOD1(podRgtPoint);
        }
       println("UpdateCargoEditFields - destination:"+destination+"  prevDischPort:"+prevDischPort+"  pod:"+pod+"podRgtPoint :"+podRgtPoint)

      }catch(Exception e){
         e.printStackTrace()
      }
      return  prevDischPort
    }
*/
    /*
      * Method Updates
      * 1.Event Field Change History List
      * 2.Updates POD by Looking up the Destination value
      */
    public void autoRollPod(Object event,Object unit, Object gvyBaseClass,Object gvyEventUtil)
    {
        def prevDischPort = null;
        def destination = null;
        def podRgtPoint = null;
        boolean doAutoRoll = false;
        gvyCmisUtil = gvyCmisUtil == null ? gvyBaseClass.getGroovyClassInstance("GvyCmisUtil") : gvyCmisUtil
        def doer = event.event.evntAppliedBy
        boolean gdsDestinationFlag;
        try
        {
            //A5;
            gdsDestinationFlag = checkFieldChanged(event, gvyBaseClass, "gdsDestination");
            if (!gdsDestinationFlag){
                println("Destination Did not Change")
                return
            }
            if(doer.contains('jms') || doer.contains('ACETS')){
                println("jms or Acets message")
                return
            }
            //A1
            destination = unit.getFieldValue("unitGoods.gdsDestination");
            destination = destination != null ? destination.trim() : destination
            prevDischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            prevDischPort = prevDischPort != null ? prevDischPort.trim() : prevDischPort

            //def isNISport = gvyCmisUtil.isNISPort(destination);
            if((/*isNISport ||*/ ContextHelper.getThreadFacility().getFcyId().equals(destination)) && !destination.equals(prevDischPort)){
                doAutoRoll = true;
                podRgtPoint = RoutingPoint.findRoutingPoint(destination)
                CarrierVisit obCarrierVisit = unit.unitActiveUfv.ufvActualObCv
                //A4 - validate if POD update is on itinerary
                if(/*isNISport &&*/ obCarrierVisit != null &&
                        !(obCarrierVisit.cvId.startsWith('GEN') && obCarrierVisit.cvId.startsWith('BARGE'))){
                    def isPortRotation = obCarrierVisit.isPointInItinerary(podRgtPoint)
                    if(!isPortRotation){
                        setObCarrierAsBarge(unit)
                    } //Set ObCarrier as barge if portRotation dosent match
                }
            }else if(!destination.equals(prevDischPort)){
                def podLookup = gvyBaseClass.getGroovyClassInstance("GvyRefDataLookup");
                def pod = podLookup.lookupPod(destination);
                podRgtPoint = RoutingPoint.findRoutingPoint(pod);

                if(podRgtPoint != null && 'GUM'.equals(pod) || 'SHA'.equals(pod) || 'NGB'.equals(pod) || 'XMN'.equals(pod)){
                    doAutoRoll = true;
                }
            }//else ends
            else if(gvyEventUtil.wasFieldChanged(event, "rtgPOD1")){
                def currDischPort = prevDischPort
                def previousDischPort =  gvyEventUtil.getPreviousPropertyAsString(event, "rtgPOD1");
                def gvyDomQueryObj = gvyBaseClass.getGroovyClassInstance("GvyCmisDomainQueryUtil")
                previousDischPort = gvyDomQueryObj.lookupRtgPOD(previousDischPort)
                previousDischPort = previousDischPort != null ? previousDischPort : ""
                if(!previousDischPort.equals(currDischPort)){
                    isPodUpdated = true;
                    prevDischPort = previousDischPort
                }
                println("previousDischPort :"+previousDischPort+" currDischPort"+currDischPort)
            }


            if(doAutoRoll){
                unit.getUnitRouting().setRtgPOD1(podRgtPoint);
                isPodUpdated = true;
            }
            setPreviousPod(prevDischPort)

            println("AutoRollPOD- destination:"+destination+"  prevDischPort:"+prevDischPort+"podRgtPoint :"+podRgtPoint+" doAutoRoll :"+doAutoRoll+" isPodUpdated:"+isPodUpdated)

            //A5
            def sendEvent = new GroovyEvent(null,unit);
            if (gdsDestinationFlag){
                if(unit!=null && unit.getFieldValue("unitRouting.rtgGroup.grpId") in ['YB']) {
                    sendEvent.postNewEvent( "YB_UNASSIGN","YB Barge Routing");
                }
            }

        }catch(Exception e){
            gvyBaseClass.log("Exception in GvyCmisUnitCargoEdit.autoRollPod()" + e);
        }
    }//Auto Roll pod

    public boolean isPodUpdated(){
        return isPodUpdated;
    }

    public void setPreviousPod(String previousPod){
        _previousPod = previousPod;
    }

    public String getPreviousPod(){
        return _previousPod;
    }

    /* Set barge Value before Updating the POD if Itinary Doent match */
    public setObCarrierAsBarge(Object unit){
        try{
            def facility = ContextHelper.getThreadFacility();
            def visit = CarrierVisit.findOrCreateVesselVisit(facility, "BARGE")
            unit.getUfvForFacilityNewest(facility).setUfvIntendedObCv(visit);
            unit.getUfvForFacilityNewest(facility).setUfvActualObCv(visit);
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public boolean checkFieldChanged(Object event, Object gvyBaseClass, String checkFieldName){
        boolean changeFlag;
        gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
        try{
            Map mapEvntField = gvyEventUtil.eventFieldChangedValues(event, gvyBaseClass);
            Iterator it = mapEvntField.keySet().iterator();
            while (it.hasNext())
            {
                def aField = it.next();
                def aEvntFieldObj = mapEvntField.get(aField);
                //Fetch Updated Field Values
                def fieldname = aEvntFieldObj.getFieldName();
                def previousValue = aEvntFieldObj.getpreviousValue();
                previousValue = previousValue != null ? previousValue : '';
                def currentValue = aEvntFieldObj.getCurrentValue();
                currentValue = currentValue != null ? currentValue : '';

                if(fieldname.equals(checkFieldName)) {
                    changeFlag = previousValue.equals(currentValue) ? false : true;
                    println(checkFieldName+" changed::"+changeFlag+"  previousValue::"+previousValue+"   currentValue::"+currentValue);
                    return changeFlag;
                }
            }//While Ends
        } catch(Exception e){
            gvyBaseClass.log("Exception in GvyCmisUnitCargoEdit.checkFieldChanged()" + e);
        }
        return changeFlag;
    }



}