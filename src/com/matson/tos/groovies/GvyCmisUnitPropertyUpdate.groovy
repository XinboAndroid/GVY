/*
* Sr doer  Date      change
* A1 GR    10/08/10  Gems : Uptd message posting method to Incorporate
                     Message specific field updates(example : AVL)
                    Depending Class GvyCmisUtil
* A2 GR       02/17/12   TOS2.1 : Updt Field unitFlexString07 to UfvFlexString07
*/

public class GvyCmisUnitPropertyUpdate
{
    def cmisActionList = ''
    def locationStatus = ''
    def fieldname=''
    def currentValue=''
    def previousValue=''

    StringBuffer buffFieldName = new StringBuffer();
    /*
     * Method Processes the Updates fields in event UNIT_PROPERTY_UPDATE
     * 1] Read the event obj and stores the Updated Field Values
     * 2] Reads the field obj and process cmis msg for updates Fields
     */
    public String unitUpdateProcess(String xmlData,Object event,Object gvyBaseClass, boolean detnMsg)
    {
        def xmlGvyString = xmlData
        boolean postMsgEDT = false;
        try
        {
            //Read & Store Event Fields into an EventField Object
            def unit = event.getEntity()
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            cmisActionList = gvyBaseClass.getGroovyClassInstance("GvyCmisListAction");

            //Set location status field
            getlocationStatus(unit,gvyCmisUtil)

            Map mapEvntFld = gvyEventUtil.eventFieldChangedValues(event, gvyBaseClass)

            //Read Fields and stores values into StringBuffer Objects for processing
            readEventChangedFields(mapEvntFld)

            //Check For Detention Msg
            println("Detention MSG :::"+detnMsg)
            if(detnMsg)
            {
                cmisActionList.setActionList("FREE")
                cmisActionList.setActionList("EDT")
            }

            //Gets Field specific action & Post Message
            String evntFieldName = new String(buffFieldName)
            String [] fldNames = evntFieldName != null ? evntFieldName.split(' ') : null;
            for(aFldName in fldNames)
            {
                def fldAction = getChangedFieldAction(aFldName.trim())
                println("unit_property_update::Field Action ::"+fldAction)
                if(fldAction != null && fldAction.length() > 0 )
                {

                    postFieldSpecMsg(xmlGvyString,gvyBaseClass,gvyCmisUtil,fldAction,unit,event)
                }
            }

            //Post Cmis msg after appending the required action
            LinkedHashSet actionList = cmisActionList.getActionList();
            println("actionList :::::"+actionList.size())
            for(aAction in actionList)
            {
                println("UNIT_PROPERTY_UPDT_ACTION_MSG_POSTING ::"+aAction);
                //gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,aAction)
                println("currentValue1::"+currentValue);
                if(currentValue!="CSR_ACTION_REQUIRED"){
                    gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,aAction, unit, event,gvyEventUtil)
                }
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
        try
        {
            Iterator it = mapEvntField.keySet().iterator();
            while (it.hasNext())
            {
                def aField = it.next();
                def aEvntFieldObj = mapEvntField.get(aField)

                //Fetch Updated Field Values
                fieldname = aEvntFieldObj.getFieldName()
                previousValue = aEvntFieldObj.getpreviousValue()
                currentValue = aEvntFieldObj.getCurrentValue()

                //Append updated Field Name and Prev Value
                buffFieldName.append(fieldname+' ')

                println('fieldname ::'+fieldname+'  previousValue::'+previousValue+'   currentValue::'+currentValue)



            }//While Ends
        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method readEventChangedFields Ends


    /* Method post messages for Field Specific Cmis Action */
    public void postFieldSpecMsg(String xmlData,Object gvyBaseClass,Object gvyCmisUtil, String fieldAction, Object unit, Object event)
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
            boolean isUnitRollEvnt = "UNIT_ROLL".equals(event.getEvent().getEventTypeId()) ? true : false

            //def locationStatus = gvyCmisUtil.getFieldValues(xmlGvyString, "locationStatus=")
            def destPort = gvyCmisUtil.getFieldValues(xmlGvyString, "dPort=")
            def srv = gvyCmisUtil.getFieldValues(xmlGvyString, "srv=")

            def islandPort =  gvyCmisUtil.isNISPort(destPort)
            println("ISland POrt ::"+islandPort+"    locationStatus::"+locationStatus+"   svr ::"+srv)

            if(fieldAction.equals('RTG'))
            {
                //UNIT_REROUTE_PROCESSING
                def rerouteEvntFeed = gvyBaseClass.getGroovyClassInstance("GvyCmisEventFeedUnitReroute")
                rerouteEvntFeed.processUnitRerouteCmisFeed(xmlGvyString, gvyBaseClass, event, unit, cmisActionList, false)
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
                cmisActionList .setActionList("BDC")
            }
            else if(fieldAction.equals('INOB') && postObCarrierMsg)
            {
                //overwrite ACTION=AVL & Post Msg dont post for UNIT_ROLL
                if(!isUnitRollEvnt){
                    cmisActionList .setActionList("AVL")
                }
                //overwrite ACTION=BDC & Post Msg
                cmisActionList .setActionList("BDC")
            }
            else if(fieldAction.equals('NIT'))
            {   //NIT (Check if not on Barge and srv=MAT)
                if(islandPort && !locationStatus.equals('7') && srv.equals('MAT'))   {
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
        mapType.put('rfreqTempRequiredC','TMP')
        mapType.put('ufvFlexString07','TMP') //A2
        mapType.put('unitFreightKind','EDT')
        mapType.put('unitGoodsAndCtrWtKg','EDT')
        mapType.put('unitSealNbr1','EDT')
        mapType.put('gdsConsigneeAsString','NIT')
        mapType.put('gdsShipperAsString','EDT')
        mapType.put('gdsDestination','BDC')
        mapType.put('gdsCommodity','EDT')
        mapType.put('gdsBlNbr','BDC')
        mapType.put('eqEquipType','TYP')
        mapType.put('unitRemark','EDT')
        mapType.put('ufvFlexDate01','PMR')
        mapType.put('rtgPOL','BDC')
        //Commented out the Routing check in UNIT_PROPERTY_UPDATE
        //mapType.put('rtgPOD1','RTG')
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

    public void getlocationStatus(Object unit, Object gvyCmisUtil){
        try
        {
            def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''
            def lkpCarrierId = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")
            locationStatus = lkpLocType.equals('VESSEL') && gvyCmisUtil.getVesselClassType(lkpCarrierId).equals('BARGE') ? '7' : ''
        }catch(Exception e){
            e.printStackTrace()
        }
    }

}

