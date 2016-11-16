/*
* SrNo Doer  Date       Change
* A1   GR    04/30/10   Add Code for Consignee Detention Change
* A2   GR    05/19/10   Add change to Lookup the Event object only Once
* A3   GR    05/19/10   DTD Msg With Previous Value Information
* A4   GR    05/27/10   Store mis3 in sealNbr4 and lastfree day in sealNbr3
* A5   GR    07/13/10   Corrected Previous Freight kind value.
* A6   GR    09/02/10   DAS Fix : Update Destination and Discharge port Seperately
* A7   GR    10/27/10   Gemes SIT : FREE and EDT posting Twice added UNIT_ROLL to Method
* A8   GR    01/27/11   Commodity Change to post detention msg
* A9   LC    10/15/2013 Block UNIT_STORAGE_UPDATE DTD and DTA msgs on the NIS_CODING_COMPLETE_BARGE event
* A10  RI	 01/15/2014 Added code to send previous Dport as Dport in DTD to Detention messages while UNIT_REROUTE
*/


import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.Shipper
import com.navis.argo.business.reference.RoutingPoint;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.ArgoRefField;
import com.navis.framework.persistence.HibernateApi;
import com.navis.inventory.InventoryField;

import java.text.SimpleDateFormat;
import java.text.DateFormat;
import java.util.Calendar;

public class GvyCmisDetentionMsgProcess
{
    def cmisActnList = '';   def misc3 = '';   def dir = '';   def dischPort = '';
    def locationStatus = '';  def detnMsg = false;  boolean availDateChng = false;  boolean detentionDateChng = false;
    def availDate = null;  def detentionDate = null;  def lastfreeDay=null; def unit=null;

    //Lookup Event only Once and Set Values
    boolean freightkindChng = false; boolean consigneeChng = false; boolean updtdischPort = false;
    boolean lastFreeDateChng = false; boolean lineOperatorChng = false; boolean categoryChng = false;
    boolean commodityChng = false;
    // A10 to store previous Dport for DTD messages
    def previousDest = null;

    def prevAvailDate = null; def prevDetentionDate = null;  def prevLastFreeDate = null;
    def prevDischPort = ''; def prevFreightKind = '';
    def gvyEventUtil = null; def gvyCmisUtil = null;  def commodity = null;

    Map _mapPrevField = null;

    //Initialize class for Global use
    public void init(Object gvyBaseClass){
        gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
        gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
        cmisActnList = gvyBaseClass.getGroovyClassInstance("GvyCmisListAction");
    }

    public boolean detentionProcess(String xmlData,Object event, Object gvyBaseClass)
    {
        def xmlGvyString = xmlData
        unit = event.getEntity()
        def gvyEventObj = event.getEvent()
        String eventType =  gvyEventObj.getEventTypeId()
        try
        {

            def equiClass =unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            def equiClassKey = equiClass != null ? equiClass.getKey() : equiClass
            if(equiClassKey.equals('CONTAINER'))
            {
                init(gvyBaseClass);

                availDate= unit.getFieldValue("unitActiveUfv.ufvFlexDate02")
                detentionDate = unit.getFieldValue("unitActiveUfv.ufvFlexDate03")
                lastfreeDay = unit.getFieldValue("unitActiveUfv.ufvCalculatedLastFreeDay")
                lastfreeDay = lastfreeDay != null && lastfreeDay.indexOf("no") != -1 ? null : lastfreeDay
                lastfreeDay =  lastfreeDay != null ? lastfreeDay.replace('!','') : lastfreeDay
                //A8
                commodity=unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")
                commodity = commodity != null ? commodity : ''

                setEvntFieldChngBuiltEvnt(event, gvyBaseClass)

                //println("prevAvailDate ::"+prevAvailDate+"  prevDetentionDate:"+prevDetentionDate+"  prevLastFreeDate:"+prevLastFreeDate)
                //println("AvailDate ::"+availDate+"  DetentionDate:"+detentionDate+"  LastFreeDate:"+lastfreeDay)
                //println('detentionProcess : freightkindChng :'+freightkindChng+'  consigneeChng:'+consigneeChng+'   updtdischPort:'+updtdischPort+'   availDateChng:'+availDateChng+'   detentionDateChng:'+detentionDateChng+'  lastFreeDateChng:'+lastFreeDateChng)
                misc3 = gvyCmisUtil.getFieldValues(xmlGvyString, "misc3=")
                dir = gvyCmisUtil.getFieldValues(xmlGvyString, "dir=")
                dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
                locationStatus = getlocationStatus(unit,gvyCmisUtil)

                //A9
                boolean isNvBarge = false;
                def evntType = gvyEventUtil.getPrevEvent("NIS_DETENTION",unit);
                if(evntType!=null && evntType.contains('NIS_DETENTION')){
                    isNvBarge = true;
                    //println("GvyCmisDetentionMsgProcess.detentionProcess()::"+unit+" Event Type = "+evntType+" isNvBarge=true");
                }


                if(updtdischPort)
                {
                    detentionDischPortMsg(gvyCmisUtil,gvyEventUtil,event,gvyBaseClass,unit)
                }
                else if(freightkindChng)
                {
                    detentionFreightMsg(gvyCmisUtil,gvyEventUtil,event,unit,freightkindChng)
                }
                else if(consigneeChng)
                {
                    detentionConsigneeMsg(gvyCmisUtil,gvyEventUtil,event,unit)
                }
                else if(categoryChng)
                {
                    detentionForCategory(unit)
                }
                else if(lineOperatorChng)
                {
                    cmisActnList.setActionList("DTD")
                    cmisActnList.setActionList("DTA")
                    detnMsg = true
                }
                else if(commodityChng && (_mapPrevField.get('commodity') != null && _mapPrevField.get('commodity').contains('XMAS')) ||
                        commodity.contains('XMAS')) //A8
                {
                    cmisActnList.setActionList("DTD")
                    cmisActnList.setActionList("DTA")
                    detnMsg = true
                }


                // Check Unit Property Update
                if((availDateChng || detentionDateChng) && eventType.equals('UNIT_PROPERTY_UPDATE'))
                {
                    if(prevAvailDate == null){
                        cmisActnList.setActionList("DTA")
                        detnMsg = true
                    }
                    else{
                        cmisActnList.setActionList("DTD")
                        cmisActnList.setActionList("DTA")
                        detnMsg = true
                    }
                }//All other Events
                else if((availDateChng || detentionDateChng || lastFreeDateChng) &&  eventType.equals('UNIT_STORAGE_UPDATE'))
                {
                    if(availDateChng || detentionDateChng)
                    {
                        if(prevAvailDate == null && prevDetentionDate == null){
                            if(!isNvBarge) { //A9
                                cmisActnList.setActionList("DTA")
                            }
                            detnMsg = true
                        }else{
                            if(!isNvBarge) { //A9
                                cmisActnList.setActionList("DTD")
                                cmisActnList.setActionList("DTA")
                            }
                            detnMsg = true
                        }
                    }//Avail date loop ends
                    else if(lastFreeDateChng)
                    {
                        if(prevLastFreeDate == null){
                            cmisActnList.setActionList("EDT")
                            detnMsg = true;
                        }
                    }//lastFreeDate loop ends
                }//Outer Else if

                //DetentionAcetsMessage Check Do not Append EDT & FREE
                if(!detentionAcetsMsgFilter(gvyEventObj)){
                    appendMsgOnEvent(eventType)
                }

                //Post Detention MSG Action except DTA and DTD
                LinkedHashSet actionList = cmisActnList.getActionList();

                for(aAction in actionList)
                {
                    //println(unit.getFieldValue("unitId")+"-> aAction:::"+aAction);
                    if('DTD'.equals(aAction)){
                        null;
                    }else{
                        println(":::::::::::::Posting "+aAction+" Message:::::::::::::::");
                        gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,aAction)
                    }
                }
                Thread.sleep(2000);
                //Post DTD message afrer 2 sec delay
                for(aAction in actionList)
                {
                    //println(unit.getFieldValue("unitId")+"-> aAction:::"+aAction);
                    if('DTD'.equals(aAction)){
                        //A10 getting Previous Dport for DTD Start

                        if (event != null) {
                            def pointId = event.getPreviousPropertyAsString("PODRef");
                            def point = findRoutingPoint(pointId);
                            if (point != null) {
                                previousDest = point.pointId;
                            }
                        }
                        //A10 getting Previous Dport for DTD End
                        String reFmtDtdXml = reformatDTDwithPrevValues(xmlGvyString)
                        gvyCmisUtil.postMsgForAction(reFmtDtdXml,gvyBaseClass,aAction);
                        println(":::::::::::::Posting DTD Message:::::::::::::::");
                    }else{
                        null;
                    }
                }

                /*//Post Detention MSG Action
                 LinkedHashSet actionList = cmisActnList.getActionList();
                 for(aAction in actionList)
                 {
                   //println(unit.getFieldValue("unitId")+"-> aAction:::"+aAction);
                   if('DTD'.equals(aAction)){
                    //A10 getting Previous Dport for DTD Start
                          if (event != null) {
                             def pointId = event.getPreviousPropertyAsString("PODRef");
                             def point = findRoutingPoint(pointId);
                             if (point != null) {
                                 previousDest = point.pointId;
                             }
                        }
                    //A10 getting Previous Dport for DTD End
                      String reFmtDtdXml = reformatDTDwithPrevValues(xmlGvyString)
                     gvyCmisUtil.postMsgForAction(reFmtDtdXml,gvyBaseClass,aAction)
                   }else{
                    gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,aAction)
                   }
                 }*/
            }//IF Container Check Ends
        }catch(Exception e){
            e.printStackTrace()
        }

        return detnMsg
    }

/*
* Method set the Detention Cmis action for Destination change
*/
    public void detentionDischPortMsg(Object gvyCmisUtil,Object gvyEventUtil,Object event,Object gvyBaseClass,Object unit)
    {
        try
        {
            //latest Code Updt Starts
            prevDischPort = prevDischPort != null ? prevDischPort : ''
            def gvyDomQueryObj = gvyBaseClass.getGroovyClassInstance("GvyCmisDomainQueryUtil")
            def prevDischPortVal = gvyDomQueryObj.lookupRtgPOD(prevDischPort)

            def freightKind = unit.getFieldValue("unitFreightKind")
            freightKind = freightKind != null ? freightKind.getKey() : ''
            //latest Code Updt Ends

            cmisActnList.setActionList("DTD")
            detnMsg = true
            if(misc3.length() > 6  ){
                cmisActnList.setActionList("DTA")
            }

            /*def prevPortNis = gvyCmisUtil.isNISPort(prevDischPortVal)
            def updtPortNis = gvyCmisUtil.isNISPort(dischPort)
            //println('prevDischPortVal::'+prevDischPortVal+'  updtPortNis ::'+updtPortNis+'   Detention NIS - prevPortNis: '+prevPortNis+'      updtPortNis:'+updtPortNis)
            if(freightKind.equals('FCL') && ((prevDischPortVal.equals(ContextHelper.getThreadFacility().getFcyId()) )
                    || ( locationStatus.equals('7'))))
            {
                cmisActnList.setActionList("DTD")
                detnMsg = true
             }
             else
            {
                 cmisActnList.setActionList("DTD")
               if(misc3.length() > 6 || updtPortNis ){
                 cmisActnList.setActionList("DTA")
               }
               detnMsg = true
            }*///else Ends
            // }//If ends
        }catch(Exception e){
            e.printStackTrace()
        }
    }

/*
* Method set the Detention Cmis action for FreightKind and consignee change
*/
    public void detentionFreightMsg(Object gvyCmisUtil,Object gvyEventUtil, Object event,Object unit, boolean freightkindChng)
    {
        try
        {
            def currFreightKind = unit.getFieldValue("unitFreightKind")
            currFreightKind = currFreightKind != null ? currFreightKind.getKey() : ''
            //prevFreightKind = currFreightKind -//A5
            if(freightkindChng)
            {
                prevFreightKind = prevFreightKind != null ? prevFreightKind : ''
            }

            // def dischPortNis = gvyCmisUtil.isNISPort(dischPort)
            if(dischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && prevFreightKind.equals('FCL'))
            {
                cmisActnList.setActionList("DTD")
                detnMsg = true
            }
            else if (dischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && currFreightKind.equals('FCL') && misc3.length() > 6)
            {
                cmisActnList.setActionList("DTA")
                detnMsg = true
            }
            else if ( locationStatus.equals('7') && prevFreightKind.equals('FCL'))
            {
                cmisActnList.setActionList("DTD")
                detnMsg = true
            }
            else if( locationStatus.equals('7') && currFreightKind.equals('FCL'))
            {
                cmisActnList.setActionList("DTA")
                detnMsg = true
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

//A1
    public void detentionConsigneeMsg(Object gvyCmisUtil,Object gvyEventUtil, Object event,Object unit)
    {
        try
        {
            def currFreightKind = unit.getFieldValue("unitFreightKind")
            currFreightKind = currFreightKind != null ? currFreightKind.getKey() : ''
            //- A5 prevFreightKind = currFreightKind
            prevFreightKind = prevFreightKind != null ? prevFreightKind : ''

            //def dischPortNis = gvyCmisUtil.isNISPort(dischPort)
            if(dischPort.equals(ContextHelper.getThreadFacility().getFcyId()) && currFreightKind.equals('FCL'))
            {
                cmisActnList.setActionList("DTD")
                if(misc3.length() > 6){
                    cmisActnList.setActionList("DTA")
                }
                detnMsg = true
            }
            else if( locationStatus.equals('7') && currFreightKind.equals('FCL'))
            {
                cmisActnList.setActionList("DTD")
                cmisActnList.setActionList("DTA")
                detnMsg = true
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }


    public void appendMsgOnEvent(String eventType)
    {
        //A7
        if(!( eventType.equals('UNIT_PROPERTY_UPDATE') || eventType.equals('UNIT_REROUTE') || eventType.equals('UNIT_ROLL') ) )
        {
            //Passing Detention Msg
            if(detnMsg){
                cmisActnList.setActionList("FREE")
                cmisActnList.setActionList("EDT")
            }
        }
    }//Method Ends

    public boolean detentionAcetsMsgFilter(Object gvyEventObj)
    {
        try
        {
            //DOER
            def doer = gvyEventObj.getEvntAppliedBy();
            def eventNotes = gvyEventObj.getEventNote()
            eventNotes = eventNotes != null ? eventNotes : ''
            CharSequence acetsMsg = "ACETS";
            boolean acetsRecorder = doer.contains(acetsMsg);
            boolean evntNotes = eventNotes.contains(acetsMsg);

            if(acetsRecorder || evntNotes){
                return true
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return false
    }

    public void detentionForCategory(Object unit)
    {
        try
        {
            def category = unit.getFieldValue("unitCategory")
            category = category != null ? category.getKey() : ''

            if(category.equals('EXPRT')){
                cmisActnList.setActionList("DTD")
                cmisActnList.setActionList("DTA")
                detnMsg = true
            }else if (category.equals('IMPRT')){
                cmisActnList.setActionList("DTA")
                detnMsg = true
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }//Method Ends

    public void getlocationStatus(Object unit,Object gvyCmisUtil){
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

    public void setEvntFieldChngBuiltEvnt(Object event,Object gvyBaseClass)
    {
        //Reads and Maps Event Updated Field value
        Map mapEvntField = gvyEventUtil.eventFieldChangedValues(event, gvyBaseClass)
        boolean areBothPortsUpdt = (mapEvntField.get("rtgPOD1") != null && mapEvntField.get("gdsDestination") != null) //A6
        _mapPrevField = new HashMap();

        String fieldArr = ['unitFreightKind','gdsConsigneeAsString','gdsShipperAsString','rtgPOD1','ufvFlexDate02','ufvFlexDate03','ufvLastFreeDay','unitLineOperator','unitCategory','gdsDestination','gdsCommodity']
        try
        {
            Iterator it = mapEvntField.keySet().iterator();
            while (it.hasNext())
            {
                def aField = it.next();
                if(!fieldArr.contains(aField)){ continue; }

                //Fetch Event Updated Field : current and Previous value
                def aEvntFieldObj = mapEvntField.get(aField)
                def fieldname = aEvntFieldObj.getFieldName()
                def previousValue = aEvntFieldObj.getpreviousValue()
                previousValue = previousValue != null ? previousValue : ''
                def currentValue = aEvntFieldObj.getCurrentValue()
                currentValue = currentValue != null ? currentValue : ''

                //println('aField :'+aField+'  fieldname ::'+fieldname+'  previousValue::'+previousValue+'   currentValue::'+currentValue)

                if(!currentValue.equals(previousValue))
                {
                    if(aField.equals("rtgPOD1")){
                        updtdischPort = true
                        prevDischPort = previousValue
                        _mapPrevField.put('dischargePort',previousValue)
                        if(!areBothPortsUpdt){
                            //println("----------------------- 12 ----------------------------")
                            //_mapPrevField.put('dPort',previousValue)
                        } //A6
                        //println("----------------------- 12.1 --aField="+aField+"     previousValue="+previousValue)
                    }else if(aField.equals("gdsDestination")){
                        if(!areBothPortsUpdt){
                            //println("----------------------- 13 ----------------------------")
                            //_mapPrevField.put('dischargePort',previousValue)
                        } //A6
                        _mapPrevField.put('dPort',previousValue)
                        //println("----------------------- 13.1 --aField="+aField+"     previousValue="+previousValue)
                    }
                    else if(aField.equals("gdsConsigneeAsString")){
                        consigneeChng = true
                        _mapPrevField.put('consignee',previousValue)

                    }else if(aField.equals("unitFreightKind")){
                        freightkindChng = true
                        prevFreightKind = previousValue
                        _mapPrevField.put('orientation',previousValue)

                    }else if(aField.equals("unitCategory")){
                        categoryChng = true
                        _mapPrevField.put('category',previousValue)

                    }else if(aField.equals("unitLineOperator")){
                        lineOperatorChng = true
                        _mapPrevField.put('locationRow',previousValue)

                    }else if(aField.equals("gdsShipperAsString")){
                        _mapPrevField.put('shipper',previousValue)

                    }else if(aField.equals("ufvFlexDate02")){
                        availDateChng = true;
                        //_mapPrevField.put('misc3',previousValue)
                        prevAvailDate = previousValue

                    }else if(aField.equals("ufvFlexDate03")){
                        detentionDateChng = true
                        // _mapPrevField.put('misc3',previousValue)
                        prevDetentionDate = previousValue

                    }else if(aField.equals("ufvLastFreeDay")){
                        lastFreeDateChng = true;
                        def shipmentDetails = gvyBaseClass.getGroovyClassInstance("GvyCmisShipmentDetail")
                        def temp = unit.getFieldValue("unitSealNbr4")
                        temp = temp != null ? (temp.length() == 0 ? null : temp) : temp
                        previousValue = shipmentDetails.getlastFreeDate(temp, lastfreeDay, gvyBaseClass)
                        _mapPrevField.put('locationCategory',previousValue)
                        prevLastFreeDate= previousValue
                    }
                    else if(aField.equals("gdsCommodity")){ //A8
                        commodityChng = true
                        //def gvyCmisEventSIT =  gvyBaseClass.getGroovyClassInstance("GvyCmisEventSIT");
                        //def preCommodity  = gvyCmisEventSIT.lookupCommodity(previousValue)
                        _mapPrevField.put('commodity',previousValue)
                    }
                }//Inner If
            }//While Ends
        }catch(Exception e){
            e.printStackTrace()
        }
    }


    //Method Reformats the messages with previous values
    public String reformatDTDwithPrevValues(String xmlGvyString)
    {
        String reformattedXmlStr = xmlGvyString
        try
        {
            if(_mapPrevField == null) { return; }
            Iterator it = _mapPrevField.keySet().iterator();
            while (it.hasNext()){
                def aKey = it.next();
                def prevalue = _mapPrevField.get(aKey)
                if('consignee'.equals(aKey)){

                    def shipper =  Shipper.findShipperByName(prevalue)
                    def consigneeId = shipper != null ? shipper.bzuId : ""
                    reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr,"cneeCode=",consigneeId)
                    //Add code to Overwrite Shipper & Consignee Id
                }else if ('shipper'.equals(aKey)){
                    def shipper =  Shipper.findShipperByName(prevalue)
                    def shipperId = shipper != null ? shipper.bzuId : ""
                    reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr,"shipperId=",shipperId)
                }else if('orientation'.equals(aKey)){
                    prevalue = 'MTY'.equals(prevalue) ? 'E' : (prevalue.length() > 1 ? 'F' : '')
                }
                reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr,aKey+"=",prevalue)
            }
            //A10 Set DTD dPort value as previous Dport value

            def aDate = gvyCmisUtil.getFieldValues(reformattedXmlStr, "aDate=");
            def aTime = gvyCmisUtil.getFieldValues(reformattedXmlStr, "aTime=");
            def lastADate = gvyCmisUtil.getFieldValues(reformattedXmlStr, "lastADate=");
            def lastATime = gvyCmisUtil.getFieldValues(reformattedXmlStr, "lastATime=");

            println("Time for DTD Details  >>>>>>>>>>>:"+aDate+"::"+aTime+"::"+lastADate+"::"+lastATime);

            def oldATime = "aTime='"+aTime+"'";
            def oldLastATime = "lastATime='"+lastATime+"'";
            println("<<<<<<<<<aTime>>>>>>>>>>"+ aTime);
            SimpleDateFormat df = new SimpleDateFormat("HH:mm:ss");
            Date d = df.parse(aTime);
            Calendar cal = Calendar.getInstance();
            cal.setTime(d);
            cal.add(Calendar.SECOND, 2);
            String aTimeNew = df.format(cal.getTime());
            println("<<<<<<<<<newTime>>>>>>>>>>"+ aTimeNew);
            reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr,"aTime=",aTimeNew)
            reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr,"lastATime=",aTimeNew)

            def newDport = gvyCmisUtil.getFieldValues(xmlGvyString, "dPort=")
            if (previousDest == null){
                previousDest = newDport;
            }
            println("<<<<<<<<<<<<Old and New Dports for DTD >>>>>>>>>>>>>"+previousDest + "<<<>>>>"+newDport);
            reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr,"dPort=",previousDest)

            //Set DTD misc3 value
            def prevMisc3 = getPreviousMisc3(reformattedXmlStr);
            reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr,"misc3=",prevMisc3)

            //Set last Free Date
            def prevlastFreeDay = getPrevLastFreeDay(reformattedXmlStr)
            reformattedXmlStr = gvyCmisUtil.eventSpecificFieldValue(reformattedXmlStr,"locationCategory=",prevlastFreeDay)

        }catch(Exception e){
            e.printStackTrace();
        }
        return reformattedXmlStr
    }

    public String getPreviousMisc3(String xmlGvyString)
    {
        def currMisc3 = gvyCmisUtil.getFieldValues(xmlGvyString, "misc3=")
        def pervMis3 = unit.getFieldValue("unitSealNbr4")
        try{
            if(currMisc3 == null || !currMisc3.equals(pervMis3)){
                unit.setUnitSealNbr4(currMisc3)
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return pervMis3
    }


    public String getPrevLastFreeDay(String xmlGvyString){
        def currFreeDay = gvyCmisUtil.getFieldValues(xmlGvyString, "locationCategory=")
        def pervLastFreeDay = unit.getFieldValue("unitSealNbr3")
        try{
            if(currFreeDay == null || !currFreeDay.equals(pervLastFreeDay)){
                unit.setUnitSealNbr3(currFreeDay)
            }
        }catch(Exception e){
            e.printStackTrace()
        }

        return pervLastFreeDay
    }

    // A10 Adding Domine query to get Routing Point
    public RoutingPoint findRoutingPoint(String inPointId) {
        DomainQuery dq = QueryUtils.createDomainQuery("RoutingPoint").addDqPredicate(PredicateFactory.eq(ArgoRefField.POINT_GKEY, inPointId));
        return (RoutingPoint) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }
}//Class Ends