/*
* Srno   Doer  Date      Change
* A1     GR    06/21/10  Added Email Notification for JMS Posting Error
* A2     GR    07/09/10  Method to Customize Field on Actions
* A3     GR    07/12/10  Added Null Check before Overriding the Field value
* A4     GR    07/16/10  Dont catch errros submit to Intergation Errors
* A5     GR    09/08/10  Trimmed HoldId and HoldId mapping
* A6     GR    10/09/10  Gems : Add Method to Handle vesvoy fields(Cell & Barge)
* A7     GR    10/09/10  Gems : BDC-FREE For Gems
* A8     GR    11/01/10  Gems : AVL on OBcarrier updt to set value to NULL
* A9     GR    11/01/10  Gems : RHN set Actual ves,voy,leg
* A10    GR    11/30/10  Adding the JMS Error Email and Resubmitting the msg to integration Errors.
* A11    GR    12/09/10  Handel westcoastload & blank ports for AVL-leg computation
* A12    GR    12/09/10  AVL remove Obcarrier Field Check on AVL compute for all AVL's
* A13    GR    12/09/10  DONT post AVL for BARGE
* A14    GR    03/07/11  Method to Set carrier OutboudId
* A15    GR    03/10/11  Rectify unit State Method
* A16    GR    04/20/11  Added Method to Fetch Unit Groovy event Object
* A17    GR    06/08/11  Added Generic Code to set Vesvoy leg
* A18    GR    07/18/11  Added OBClassType in RHN Event
* 08/16/11 2.1 Updated Email Method
* A19    LC    09/12/12  Added condition for GEN_TRUCK, set Obcarrier to TRUCK
* A20    RI    02/27/14  Added method to capture Integration error
*/
import com.navis.vessel.business.operation.VesselClass
import com.navis.vessel.business.operation.VesselClassHbr
import com.navis.vessel.business.atoms.VesselTypeEnum

import com.navis.argo.business.api.ArgoRoadManager
import com.navis.argo.business.api.ITruckVisitDetails
import com.navis.framework.business.Roastery;
import com.navis.argo.business.api.*;
import com.navis.framework.business.*;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.InventoryField;
import com.navis.framework.persistence.*;
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.ContextHelper
import com.navis.argo.business.model.CarrierVisit;
import com.navis.inventory.business.api.RectifyParms;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;

import com.navis.services.business.rules.EventType
import com.navis.services.business.event.Event
import com.navis.services.business.api.EventManager
import com.navis.framework.business.Roastery;
import com.navis.services.business.event.GroovyEvent
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.framework.persistence.HibernateApi


public class GvyCmisUtil {

    private static final String emailTo = '1aktosdevteam@matson.com'
    private static final String emailfrom = '1aktosdevteam@matson.com'

    def inj = new GroovyInjectionBase();


///Method Returns the true/false based on validity of the units load port and dischararge port
//Method Takes Input unit and event
    boolean checkValidUnitSendEmail(Object event, Object unit)
    {
        def returnFlag = true;
        def dischargePort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
        def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId")
        def unitDetails = inj.getGroovyClassInstance("GvyCmisDataProcessor")
        def unitDtl = unitDetails.doIt(event)
        def gvyLoadObj = inj.getGroovyClassInstance("GvyCmisEventUnitLoad");
        String outMsg = gvyLoadObj.getLoadedEquipClassMsg(unitDtl,event,inj);
        dischargePort  = getActualFieldValues(outMsg, "dischargePort=")
        loadPort = getActualFieldValues(outMsg, "loadPort=")
        dischargePort = dischargePort != null ? dischargePort.trim().toLowerCase() : null;
        loadPort = loadPort != null ? loadPort.trim().toLowerCase() : null;

        println( " ############## dischargePort "+ dischargePort +  " ################# loadPort " + loadPort);

        if (loadPort =='null' || dischargePort =='null'){
            returnFlag =  false;
        } else if (loadPort.indexOf(dischargePort) > -1 || dischargePort.indexOf(loadPort) > -1 ){
            returnFlag = false;
        }else {
            println( " ##############  leg will be calculated correctly " );
            returnFlag = true ;
        }

        if(!returnFlag){


            def ibcarrier = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId");
            def emailSender = inj.getGroovyClassInstance("EmailSender")
            def emailFrom = '1aktosdevteam@matson.com'
            def emailTo = "1aktosdevteam@matson.com";
            println( " ##############  leg will not be calculated correctly the output message is " + outMsg );
            emailSender.custSendEmail(emailFrom,"WARNING : Unit loading error for ## " + ibcarrier  + " ## load port ## " + loadPort + "##  discharge port ## " + dischargePort + " ## this might calculate leg incorrectly " ,"WARNING : Unit loading  warning " + outMsg  );

        }

        println( " ##############  returnFlag "  + returnFlag);
        return returnFlag;
    }


    //Method Returns the Vessel Type [Ship/Barge]
    //Methof takes input last known Position
    private String getVesselClassTypeBackup(String vesselId)
    {
        def vesselType = ''
        try
        {
            def vesselClassId = vesselId != null && vesselId.length() >3 ? vesselId.substring(0,3) : null
            println("vesselClassId :: ibcarrier ::"+vesselClassId)
            if(vesselClassId != null)
            {
                VesselClass vesselClass = new VesselClass()
                vesselClass = vesselClass.findVesselClassById(vesselClassId)
                println("vesselClass :: ibcarrier ::"+vesselClass)
                VesselTypeEnum  vesselTypeEnum = vesselClass != null ? vesselClass.getVesclassVesselType() : null
                println("VesselTypeEnum ::"+VesselTypeEnum)
                vesselType = vesselTypeEnum != null ? vesselTypeEnum.getKey() : ''
            }
            //println('VESSEL TYPE :'+vesselType)
        }catch(Exception e){
            e.printStackTrace()
        }
        return vesselType
    }

    //Method Returns the Vessel Type [Ship/Barge]
    //Methof takes input last known Position
    private String getVesselClassType(String vesselId)
    {
        def vesselType = ''
        try
        {
            def vesselClassId = vesselId != null && vesselId.length() >3 ? vesselId.substring(0,3) : null
            println("vesselClassId :: ibcarrier ::"+vesselClassId)
            if(vesselClassId != null)
            {
                if( "STB".equalsIgnoreCase(vesselClassId)  || "STR".equalsIgnoreCase(vesselClassId)  ||
                        "BAB".equalsIgnoreCase(vesselClassId)){

                    vesselClassId="ILB";
                    println("setting vesselClassId as "+vesselClassId )
                }

                VesselClass vesselClass = new VesselClass()
                vesselClass = vesselClass.findVesselClassById(vesselClassId)
                println("vesselClass :: ibcarrier ::"+vesselClass)
                VesselTypeEnum  vesselTypeEnum = vesselClass != null ? vesselClass.getVesclassVesselType() : null
                println("VesselTypeEnum ::"+VesselTypeEnum)
                vesselType = vesselTypeEnum != null ? vesselTypeEnum.getKey() : ''
            }
            //println('VESSEL TYPE :'+vesselType)
        }catch(Exception e){
            e.printStackTrace()
        }
        return vesselType
    }


    //Method Returns the Vessel Type [Ship/Barge]
    //Method Takes Input Vessel Cd
    private String getVesselClassForVesCode(String vesselId)
    {
        try
        {
            def vesselClassId = vesselId != null ? vesselId : null
            if(vesselClassId != null){
                VesselClass vesselClass = new VesselClass()
                vesselClass = vesselClass.findVesselClassById(vesselClassId)
                if(vesselClass != null){
                    VesselTypeEnum  vesselTypeEnum = vesselClass.getVesclassVesselType()
                    def vesselType = vesselTypeEnum != null ? vesselTypeEnum.getKey() : ''
                    //println('VESSEL TYPE :'+vesselType)
                    return vesselType
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return ''
    }

    //Method Vessel Class Type that has no entry in the vesVisit
    public String getVesClassTypeWithNoVisitEntry(Object unit)
    {
        def vesselClass = ''
        try
        {
            def aibcarrierMode=unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCarrierMode")
            aibcarrierMode = aibcarrierMode!= null ? aibcarrierMode.getKey() : ''
            if(aibcarrierMode != null && aibcarrierMode.equals('VESSEL'))
            {
                def ibcarrier = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId");
                ibcarrier = ibcarrier != null && ibcarrier.length() > 3 ? ibcarrier.substring(0,3) : ''
                vesselClass = getVesselClassForVesCode(ibcarrier)
                println("noVesselVisitEntry :: ibcarrier ::"+ibcarrier+"   vesselClass ----"+vesselClass)
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return vesselClass
    } //Method VesClass


    public String eventSpecificFieldValue(String xmlGvyData,String field,String newFieldValue)
    {
        String newValue = null;
        String tempNewValue = newFieldValue != null ? newFieldValue : 'null' //A2
        String oldValue = null;
        String xmlGvyString = xmlGvyData;
        int fieldIndx = xmlGvyString.indexOf(field);


        try
        {
            if(fieldIndx != -1)
            {
                int equalsIndx = xmlGvyString.indexOf("=",fieldIndx);
                int nextspace = xmlGvyString.indexOf("'", equalsIndx+2);
                oldValue = xmlGvyString.substring(equalsIndx+2, nextspace);

                if(oldValue.equals("null") ){
                    newValue = tempNewValue;
                }
                else{
                    //CHECK FOR VALUE HERE
                    newValue = tempNewValue;
                }

                System.out.println("Field ::"+field+"  oldValue ::"+oldValue+"  newValue :::"+newValue);


                //Replace Escape Char in String
                newValue = replaceQuotes(newValue)
                String oldXmlValue = field+"'"+oldValue+"'";
                String newXmlValue = field+"'"+newValue+"'";
                xmlGvyString = xmlGvyString.replace(oldXmlValue, newXmlValue);


            }//IF Ends
        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString;
    }// Method eventSpecificFieldValue Ends

    public static String getFieldValues(String xmlGvyString, String field)
    {
        String fieldValue = ''
        try
        {
            def fieldIndx = xmlGvyString.indexOf(field);
            def equalsIndx = xmlGvyString.indexOf("=",fieldIndx);
            def nextspace = xmlGvyString.indexOf("'", equalsIndx+2);
            fieldValue  = xmlGvyString.substring(equalsIndx+2, nextspace);
        }catch(Exception e){
            e.printStackTrace()
        }
        return fieldValue;
    }

    public static String getActualFieldValues(String xmlGvyString, String field)
    {
        String fieldValue = null
        try
        {
            def fieldIndx = xmlGvyString.indexOf(field);
            def equalsIndx = xmlGvyString.indexOf("=",fieldIndx);
            def nextspace = xmlGvyString.indexOf("'", equalsIndx+2);
            fieldValue  = xmlGvyString.substring(equalsIndx+2, nextspace);
        }catch(Exception e){
            e.printStackTrace()
        }
        return fieldValue;
    }


    //Sets the specific Cmis action and Post the Message
    public void postMsgForAction(String xmlData,Object gvyBaseClass,String action)
    {
        def xmlGvyString = xmlData
        if(!('null').equals(action)){
            xmlGvyString = eventSpecificFieldValue(xmlGvyString,"lastAction=", action)
            xmlGvyString = eventSpecificFieldValue(xmlGvyString,"action=", action)
        }
        xmlGvyString = customizeActionField(xmlGvyString,action)
        try{
            gvyBaseClass.sendXml(xmlGvyString)
            println("gvyCmisUtil.postMsgForAction()"+xmlGvyString);
        }catch(Exception e){ //A10
            def emailSender = gvyBaseClass.getGroovyClassInstance("EmailSender")
            emailSender.custSendEmail(emailTo, e.getMessage());
            //Catching the Error will post the mail and Re-posting the Message will exception out not be caught and will get Logged into the Integration Errors.
            //gvyBaseClass.sendXml(xmlGvyString)
            String error = e;
            //A20
            if (error.contains("JMS") && xmlGvyString != null){
                emailSender = gvyBaseClass.getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailTo, "TOS : NODE2 JMS SERVICE DOWN", "Fix : Goto Administration -> System -> Cluster management -> Select Node2 -> Action -> Restart JMS Connections  \r\n\r\n");
                println("Calling MatGetIntegrationError.createIntegrationError");
                String entity = "Unit";
                def unitId = this.getFieldValues(xmlGvyString, "ctrNo=");
                def eventId = this.getFieldValues(xmlGvyString, "msgType=");
                def errDesc = eventId+" Failed for "+unitId;
                def inj = new GroovyInjectionBase();
                inj.getGroovyClassInstance("MatGetIntegrationError").createIntegrationError(error,entity,unitId,eventId,errDesc,xmlGvyString);
                HibernateApi.getInstance().flush();
            }

        }
    }


    //Sets the specific Cmis action and Post the Message
    public void postMsgForAction(String xmlData,Object gvyBaseClass,String action, Object unit, Object event,Object gvyEventUtil)
    {

        def tempXml = xmlData
        boolean dontPost = true;
        try{
            if('AVL'.equals(action)){
                def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
                def isInYard = 'S40_YARD'.equals(transitState.getKey())
                def isObCarUpdt = gvyEventUtil.wasFieldChanged(event,"ufvIntendedObCv");
                //A12
                def obVesClass = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                obVesClass = obVesClass != null ? obVesClass.getKey() : ''
                def obCarrierId = isObCarUpdt ? unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId") : unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")
                //A13
                if('BARGE'.equals(obCarrierId) || 'BARGE'.equals(obVesClass)){
                    dontPost = false;
                }
                if(isInYard && !'BARGE'.equals(obCarrierId) && ('BARGE'.equals(obVesClass) || 'CELL'.equals(obVesClass)) ){
                    tempXml = setVesvoyFields(unit, tempXml, obCarrierId, obVesClass) //A17
                    tempXml = eventSpecificFieldValue(tempXml,"locationStallConfig=","null")
                } //A12
                if(isObCarUpdt){tempXml = eventSpecificFieldValue(tempXml,"locationStallConfig=","null")} //A8
            }else if('RHN'.equals(action)){
                //A18
                def obVesClass = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
                obVesClass = obVesClass != null ? obVesClass.getKey() : ''

                def intdObCarrierId = unit.getFieldValue("unitActiveUfv.ufvIntendedObCv.cvId")
                def aobcarrierId = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")
                aobcarrierId = aobcarrierId != null ? aobcarrierId : intdObCarrierId
                tempXml = setVesvoyFields(unit, tempXml, aobcarrierId, obVesClass) //A17
            }
        }catch(Exception e){
            e.printStackTrace()
        }

        println("dontPost :: "+dontPost+"   action ----"+action);
        if(dontPost){
            postMsgForAction(tempXml,gvyBaseClass,action)
        }
    }

    //Method to Customized Fields based on Event Action
    //1. HLP & HLR for Holds and Releases
    public String customizeActionField(String xmlData,String action){
        String xmlGvyString = xmlData
        try{
            if('null'.equals(action)){
                return xmlGvyString;
            }

            if('HLP'.equals(action)){
                def msgType = getFieldValues(xmlGvyString, "msgType=")
                def holdId = msgType.substring(0,msgType.indexOf('_HOLD'))
                holdId = holdId.startsWith('CG_INSP') ? 'CG' : (holdId.startsWith('OUTGATE') ? 'RD' : holdId)
                xmlGvyString = eventSpecificFieldValue(xmlGvyString,"crStatus=", holdId.trim()) //A5
            }else if('HLR'.equals(action)){
                def msgType = getFieldValues(xmlGvyString, "msgType=")
                def holdId = msgType.substring(0,msgType.indexOf('_RELEASE'))
                holdId = holdId.startsWith('CG_INSP') ? 'CG' : (holdId.startsWith('OUTGATE') ? 'RD' : holdId)
                xmlGvyString = eventSpecificFieldValue(xmlGvyString,"crStatus=", holdId.trim())
            }else if('FREE'.equals(action)){ //A4
                xmlGvyString = eventSpecificFieldValue(xmlGvyString,"action=", 'BDC')
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }

    /*
     * Method returns the Vessel Service/Line operator
     */
    public String vesselServiceOperator(Object unit)
    {
        boolean unitOnBarge = false;
        def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
        def locType = lkpLocType != null ? lkpLocType.getKey() : ''

        if(!locType.equals('VESSEL')){
            return ''
        }

        def lkpCarrierId=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")

        def aibcarrierId=unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")
        def aobcarrierId=unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")

        def carrierOperatorIdOB = unit.getFieldValue("unitActiveUfv.ufvActualObCv.carrierOperatorId")
        carrierOperatorIdOB = carrierOperatorIdOB != null ? carrierOperatorIdOB : ''

        def carrierOperatorIdIB = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.carrierOperatorId")
        carrierOperatorIdIB  = carrierOperatorIdIB != null ? carrierOperatorIdIB : ''

        def vesServiceOperator = ''

        if(lkpCarrierId.equals(aibcarrierId)){
            vesServiceOperator = carrierOperatorIdIB
        }
        else if(lkpCarrierId.equals(aobcarrierId)){
            vesServiceOperator = carrierOperatorIdOB
        }

        println("UNIT OnVesselType :: lkpLocType ::"+locType+" LKPCarrId::"+lkpCarrierId+" vesServiceOperator:"+vesServiceOperator)

        return vesServiceOperator;
    }

    public String getVesselLineOperator(Object unit)
    {
        def vesselLine = null

        def category=unit.getFieldValue("unitCategory")
        category = category != null ? category.getKey() : ''

        def vesselLineOb = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdBizu.bzuId");
        def vesselLineIb = unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvCvd.vvdBizu.bzuId");
        if(vesselLineOb != null ||  vesselLineIb != null)
        {
            if (category.equals('EXPRT') || category.equals('THRGH'))
            {
                vesselLine = vesselLineOb
            }
            else if (category.equals('IMPRT'))
            {
                vesselLine = vesselLineIb
            }
        }
        println("vesselLineOb ::"+vesselLineOb+"     vesselLineIb::"+vesselLineIb+' vesselLine ::'+vesselLine  )
        return vesselLine
    }

    /*Method Validates Neighbor island port */
    public boolean isNISPort(String destPort)
    {
        def nisPort = false;
        try
        {
            if(destPort != null)
            {
                if(destPort.equals('KAH') || destPort.equals('HIL') || destPort.equals('KHI') || destPort.equals('NAW') ||
                        destPort.equals('LNI') || destPort.equals('MOL'))
                {
                    nisPort = true;
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return  nisPort
    }

    /*Method Validates Neighbor island port */
    public boolean isNISPortReroute(String destPort)
    {
        def nisPort = false;
        try
        {
            if(destPort != null)
            {
                if(destPort.equals('KAH') || destPort.equals('HIL') || destPort.equals('KHI') || destPort.equals('NAW') ||
                        destPort.equals('LNI') || destPort.equals('MOL') || destPort.equals('HON'))
                {
                    nisPort = true;
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return  nisPort
    }

    /*
     * Method checks last event processing time interval to call thread sleep on current processing thread
     */
    public boolean holdEventProcessing(Object event, String eventType, int sec)
    {
        def secInterval = sec * 1000
        def currEvntTime = event.getEvntAppliedDate()
        def currEvtTime = currEvntTime != null ? currEvntTime.getTime() : null

        def mstEvent = event.getMostRecentEvent(eventType)
        def mstEvntTime = mstEvent!= null ? mstEvent.getEvntAppliedDate() : null
        if(currEvtTime != null && mstEvntTime != null)
        {
            def mstEvtTime = mstEvntTime.getTime()
            def evntTimeDiff = currEvtTime - mstEvtTime
            if(evntTimeDiff < secInterval){
                return true
            }
            else{
                return false;
            }
        }else{
            return false;
        }
    }

    //convert Cm To Inch
    private Object convertCmToInch(Object cmValue)
    {
        def inchValue = null
        def api = new GroovyApi();
        if(cmValue != null && cmValue > 0){
            def inchVal = cmValue * 0.393700787;
            inchVal = new BigDecimal(inchVal).setScale(2, BigDecimal.ROUND_HALF_UP);
            inchValue = Math.round(inchVal)
        }
        return inchValue
    }

    public Object lookupFacility(Object id) {
        println("GvyUnitLookup.lookupFacility");

        try {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");

            dq.addDqPredicate(PredicateFactory.eq(InventoryField.UFV_UNIT,id ));
            def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if(list != null) {
                Iterator iter = list.iterator();
                while(iter.hasNext()) {
                    def visit = iter.next();
                    def testVisit = visit.getFieldValue("ufvTransitState")
                    println("visit.getFieldValue-ufvTransitState------"+testVisit); if(visit.getFieldValue("ufvTransitState").equals(com.navis.inventory.business.atoms.UfvTransitStateEnum.S70_DEPARTED)) {
                        return visit;
                    }
                }}
            return null;
        } catch (Exception e) {
            println("Exception in GvyUnitLookup "+e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    public  String replaceQuotes(Object message)
    {
        def msg = message.toString();
        def replaceAmp = msg.replaceAll('&', '&amp;');
        replaceAmp = replaceAmp.replaceAll('\'', '&apos;');
        replaceAmp = replaceAmp.replaceAll("<", "&lt;")
        replaceAmp =  replaceAmp.replaceAll(">", "&gt;")
        replaceAmp = replaceAmp.replaceAll("\"", "&quot;")
        return replaceAmp;
    }

    /* A6 AVL messages sets the Actual vessel, Voyage and leg value
     1. Post AVL Message if Discharge from barge
     2. When Obcarrier is updated
    */
    public String setVesvoyFields(Object unit, String xmlGvyString, String carrierId, String obVesselType)
    {


        def xmldata =  xmlGvyString
        try{
            def loadPort = unit.getFieldValue("unitRouting.rtgPOL.pointId")
            def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType").getKey() //A11
            if(lkpLocType.equals('YARD') && ('LAX'.equals(loadPort) || 'OAK'.equals(loadPort) || 'SEA'.equals(loadPort) || loadPort == null )){
                //todo what we need for Barge locations and Alaska Locations? (TAC)
                def routing = unit.getUnitRouting();
                routing.setRtgPOL(RoutingPoint.findRoutingPoint(ContextHelper.getThreadFacility().getFcyId()));
            }
            def dischargePort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            //def obVesselType = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCvd.vvdVessel.vesVesselClass.vesclassVesselType");
            def obVesselTypeKey = obVesselType

            if(carrierId == null || carrierId.startsWith('GEN') || carrierId.equals('BARGE')){
                return xmldata;
            }

            def actualVessel = null; def actualVoyage = null; def leg = null;
            if('BARGE'.equals(obVesselTypeKey)){
                actualVessel = carrierId.length() > 6 ? carrierId.substring(0,3) : 'null'
                actualVoyage = carrierId.length() > 6 ? carrierId.substring(3,6) : 'null'
                leg = carrierId.length() > 6 ? carrierId.substring(6) : 'null'
            }else{
                leg = loadPort+'_'+dischargePort
                actualVessel = carrierId.length() > 5 ? carrierId.substring(0,3) : 'null'
                actualVoyage = carrierId.length() > 5 ? carrierId.substring(3) : 'null'
            }


            xmldata = eventSpecificFieldValue(xmldata,"actualVessel=",actualVessel)
            xmldata = eventSpecificFieldValue(xmldata,"actualVoyage=",actualVoyage)
            xmldata = eventSpecificFieldValue(xmldata,"leg=",leg)
        }catch(Exception e){
            e.printStackTrace();
        }
        return xmldata
    }

    //A14
    public void setObCarrier(Object unit, String vesselId){
        try{
            def facility = ContextHelper.getThreadFacility();
            def visit;

            //A19
            if (vesselId.equals('GEN_TRUCK'))
            {
                def complex = ContextHelper.getThreadComplex();
                visit = CarrierVisit.findOrCreateGenericCv(complex, com.navis.argo.business.atoms.LocTypeEnum.TRUCK)

            } else {

                visit = CarrierVisit.findOrCreateVesselVisit(facility, vesselId)
            }

            unit.getUfvForFacilityNewest(facility).setUfvIntendedObCv(visit);
            unit.getUfvForFacilityNewest(facility).setUfvActualObCv(visit);

        }catch(Exception e){
            api.log("Exception in GvyCmisUtil.setObCarrier() " + e);
        }
    }

    public void rectifyState(Object aUfv, UfvTransitStateEnum tState, UnitVisitStateEnum vState)
    {
        try{
            RectifyParms rparms = new RectifyParms();
            rparms.setUfvTransitState(UfvTransitStateEnum.S70_DEPARTED);
            rparms.setUnitVisitState(UnitVisitStateEnum.DEPARTED)
            aUfv.rectify(rparms);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

/*
* Method Return Groovy EventType Obejct back for Specified Unit
* Input Unit Object and EventName
* Returns NULL if doesnt Find a match Else returns GroovyEvent Object for Event
* Created this for Special cases when we need to Pull out event inforamtion
  After the Fact/At a later time
*/
    public Object getUnitGroovyEventObject(Object unit, String eventName)
    {
        try{
            //def unit = event.getEntity()
            EventType eventType = EventType.findEventType(eventName);
            EventManager eventManager = (EventManager)Roastery.getBean("eventManager");
            List events = eventManager.getEventHistory(eventType, unit);
            Event deptUnitEvent = null
            if (events!= null && events.size() == 0){
                return null;
            } else {
                deptUnitEvent = (Event)events.get(0);
            }
            if(deptUnitEvent != null){
                GroovyEvent moveEvent = new GroovyEvent( deptUnitEvent , unit);
                //Check if we are getting this object
                return moveEvent
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    } //Method ends

/*Method Validates Alaskan Neighbor island port */
    public boolean isAlaskanNISPort(String destPort)
    {
        def nisPort = false;
        try
        {
            if(destPort != null)
            {
                if(destPort.equals('KQA') || destPort.equals('SDP') || destPort.equals('NNK') || destPort.equals('PML') ||
                        destPort.equals('QPO') /*|| destPort.equals('MOL')*/)
                {
                    nisPort = true;
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return  nisPort
    }//method ends

}
