/*
* Srno  Doer  Date       comment
* A1    GR    06/16/10   Pass Holds to acets removed Barge (locationstatus 7 check)
* A2    GR    06/18/10   Added Hold Escape list for ITN,DVI,AGN hold
* A3    GR    06/22/10   Overwrite Event & Recorder for Acets as AHLP & AHLR
* A4    GR    06/28/10   Send DVI hold upto acets (HPL/HLR)
*                        Handel Messages Condition for Departed married units.
* A5    GR    06/29/10   Clear Chassis Alert for HLR
* A6    GR    08/11/10   Remove Actual ves, voy and leg from generic default setting
* A7    GR    07/15/10   Remove Haz handling from Class
* A8    GR    07/19/10   Updt Cmis Posting Method
* A9    GR    09/08/10   Added flex01 variable as GEMS dosent need UN/NA
* A10   GR    10/04/10   Gems Change : Added HAZ events to Skip event list
* A12   GR    10/15/10   Reverted A8 change now to login Integration errors
* A13   GR    10/18/10   Gems change : Add UPU messages to escape list
* A14   GR    10/28/10   Gems : Post NV and NLT Execution Messages Directly to Gems
* A15   GR    11/03/10   Gems : INGATE_RELEASE to set CLS to locationStatus=1
* A16   GR    11/10/10   Gems : Double posting HLP joce identified isssue Fixed
* A17   GR    12/08/10   Gems : DVIR Check
* A18   GR    01/11/11   Gems : Suppress EDT for overDimension Update
* A19   GR    01/31/11   Gems : USA Hold/ Release Added
* A20   GR    12/06/11   Gems : Added YB_TRUCKER_ASSIGN and mapped trucker
* A21   GR    03/16/11   opened out HLP/HLR posting for Client Vessel
* A22   RI    02/27/14   Added method to capture Integration error
*/
import com.navis.services.business.event.Event
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.Unit
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.framework.persistence.HibernateApi

public class GvyCmisEvntFldChngProcessor
{
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
    public String processFieldChngCmisFeed(Object event,Object gvyBaseClass)
    {
        def xmlGvyString = ''
        try{
            //Get OBJECT
            Object unitObj = event.getEntity()
            def isUnitObj = false

            //Check For Unit Object to handle Reporting Tag Event Call
            if(unitObj instanceof Unit) {
                isUnitObj = true
            }

            //Gets EVENT ID
            Event gvyEventObj = event.getEvent()
            String eventType =  gvyEventObj.getEventTypeId()
            def evntNotes = gvyEventObj.getEventNote();
            evntNotes = evntNotes != null ? evntNotes : ''

            def gvyDataProcObj = gvyBaseClass.getGroovyClassInstance("GvyCmisDataProcessor");
            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil")
            def gvyTxtMsgFmt = gvyBaseClass.getGroovyClassInstance("GvyCmisTxtMsgFormatter");

            //MSG TYPE
            def eventTypeAttr = gvyTxtMsgFmt.doIt('msgType',eventType)

            //Getting UNIT_OBJ from EQUIP_OBJ
            if(unitObj instanceof EquipmentState){
                unitObj = gvyDataProcObj.getUnitFromEquipment(unitObj)
            }
            //Assigning If Equi Obj to Unit Obj
            def unit = unitObj

            //Print Evnt and Unit
            def unitId = unit.getFieldValue("unitId");
            println('EventType ::'+eventType+"   unitId:: "+unitId)

            // EQUIP CLASS
            def equiClass =unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            equiClass = equiClass != null ? equiClass.getKey() : ''

            //Crstatus,CargoNotes,Comments
            def CommentsAttr = ''
            def gvyCommentObj = gvyBaseClass.getGroovyClassInstance("GvyCmisCommentNotesField");
            CommentsAttr = gvyCommentObj.doIt(gvyEventObj,eventType,gvyTxtMsgFmt,unit)

            //Action Attr
            def actionAttr = ''
            def gvyActionObj = gvyBaseClass.getGroovyClassInstance("GvyCmisActionDetail");
            actionAttr = gvyActionObj.doIt(gvyTxtMsgFmt,gvyEventObj,gvyBaseClass,unit)

            //Unit Class,Nbr,Chassis Nbr,Accessory
            def unitFieldAttr = ''
            def gvyUnitDtlObj = gvyBaseClass.getGroovyClassInstance("GvyCmisUnitDetail");
            unitFieldAttr = gvyUnitDtlObj.doIt(equiClass,gvyTxtMsgFmt,unit, event)

            //DMG_CODE,STOW FLAG
            def flagFieldsAttr = flagFields(unit,eventType,gvyTxtMsgFmt)

            //RESTOW
            def restow = unit.getFieldValue("unitActiveUfv.ufvRestowType")
            restow = restow != null ? restow.getKey() : ''
            def restowAttr = gvyTxtMsgFmt.doIt('restow',restow)

            //CONSIGNEE
            def gvyConsigneeObj = gvyBaseClass.getGroovyClassInstance("GvyCmisShipmentDetail")
            def consignee=gvyConsigneeObj.getConsigneeValue(unit,eventType)
            if(consignee == null || consignee.equals('null') || consignee.trim().length() == 0){
                consignee = '%'
            }
            def consigneeAttr = gvyTxtMsgFmt.doIt('consignee',consignee)

            //MISC2 - A1(flex fielsds-cargo status field Change code)
            def misc2 = ''
            def gvyEditFlag = gvyBaseClass.getGroovyClassInstance("GvyCmisProcessEditFlag");
            misc2 =gvyEditFlag.processEditFlag(event, eventType, unitObj, gvyBaseClass )
            unitObj.setUnitFlexString11(misc2)
            def misc2Attr = gvyTxtMsgFmt.doIt('misc2',misc2)

            //Fields the Did not change
            def nonChngFieldAttr = nonChangingFields()

            def msgString = eventTypeAttr+' '+CommentsAttr+' '+actionAttr+' '+unitFieldAttr+' '+flagFieldsAttr+' '+' '+restowAttr+' '+consigneeAttr+' '+nonChngFieldAttr+' '+misc2Attr         //Creates Groovy Xml
            xmlGvyString = gvyTxtMsgFmt.createGroovyXml(msgString)

            //Field Manipulation
            def eventFeedObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventFeedManipulation");
            xmlGvyString = eventFeedObj.processCmisEventFeedManipulation(eventType,xmlGvyString, gvyBaseClass,event,unitObj, isUnitObj, equiClass)


            //Added Field Check - Remove EQUIP HOLD/RELEASE FIELD MANIPULATION
            //def equipHold = gvyBaseClass.getGroovyClassInstance("GvyCmisEquipmentHoldEvents")
            //xmlGvyString = equipHold.setEquipmentHoldFields(xmlGvyString,event,unitObj)
            boolean msgPostingFlag = true;

            if(eventType.equals("DVI_HOLD") || eventType.equals("DVI_RELEASE")){ // A4 Chassis Hold
                if(eventType.endsWith('_HOLD')){
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"chassisAlert=",'DVI')
                }else{
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"chassisAlert=",'null')
                }

                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"unitClass=",'CHASSIS')

                //GemsPROD issue Reading for CntrNbr 12/09/10
                def chassisNbr = gvyCmisUtil.getFieldValues(xmlGvyString, "chassisNumber=")
                def chassisCd = gvyCmisUtil.getFieldValues(xmlGvyString, "chassisCd=")

                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"ctrNo=",chassisNbr)
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"checkDigit=",chassisCd)
            }

            if (eventType.equals("UNIT_OVERDIMENSIONS_UPDATE")){
                msgPostingFlag = false;
                processOogFields(xmlGvyString,unit,gvyCmisUtil,event,gvyBaseClass)

            }else if (eventType.equals("ARD")){
                //BOOKING NUMBER
                def bookingNbr=unit.getFieldValue("unitGoods.gdsBlNbr")
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"bookingNumber=",bookingNbr)
            }
            else if (eventType.equals("TRUCKER_ASSIGN") || eventType.equals("RELEASE_CONTAINER") || eventType.equals("YB_TRUCKER_ASSIGN") || eventType.equals("PTL_RELEASE_TO") ){
                if(eventType.equals("RELEASE_CONTAINER")){
                    def relConsignee =gvyConsigneeObj.getConsigneeValue(unit,eventType)
                    relConsignee = relConsignee != null && relConsignee.trim().length() != 0 ? relConsignee : "null"
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"consignee=",relConsignee)
                }

                def truck = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
                truck = truck != null ? truck : "null"
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"truck=",truck)
                gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"EDT")
                msgPostingFlag = false;
            }
            else if(eventType.equals("PREMOUNT_REQUEST")){
                xmlGvyString = processPremountEvent(xmlGvyString,unit,gvyCmisUtil)
            }
            else if (eventType.equals("UNIT_OPERATOR_CHANGE")){
                //OWNER & OPERATOR(SRV)
                def gvyEquiObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEquiDetail");
                def srv = gvyEquiObj.getSrv(unit,gvyBaseClass)
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"srv=",srv)
            }else if (eventType.endsWith("_HOLD") || eventType.endsWith("_RELEASE")){
                if(!(eventType.startsWith("SHOP")|| eventType.startsWith("CG_DMG") || eventType.startsWith("CL") || eventType.startsWith("INGATE") || eventType.startsWith("LTV"))){
                    def acetsAction = eventType.endsWith("_HOLD") ? 'HLP' : 'HLR'
                    //A3 - Overwrite for Acets
                    if(evntNotes.contains('Acets HLP/HLR')){
                        acetsAction = eventType.endsWith("_HOLD") ? 'AHLP' : 'AHLR'  // A16
                        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"doer=","ACETS")
                        xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"lastDoer=","ACETS")
                    }
// A21 - Starts           def postAcetsMsg =  postHoldReleaseAcetsMsg(unit,gvyBaseClass,gvyCmisUtil)
                    //          if(postAcetsMsg){ //post acets messages
                    def acetsXml = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"action=",acetsAction)
                    acetsXml = gvyCmisUtil.eventSpecificFieldValue(acetsXml,"lastAction=",acetsAction)
                    //A14 - Post NV and NLT Execution Messages Directly to Gems
                    /* if(event.event.evntAppliedBy.contains('jms')){
                         def jmsTopicSender = gvyBaseClass.getGroovyClassInstance("JMSTopicSender")
                         acetsXml = gvyCmisUtil.customizeActionField(acetsXml,acetsAction)
                         jmsTopicSender.send(acetsXml);
                         msgPostingFlag = false;
                         println("HLR SENT TO GEMS="+acetsXml)
                     }else{ */
                    acetsXml = gvyCmisUtil.customizeActionField(acetsXml,acetsAction)
                    try{
                        gvyBaseClass.sendXml(acetsXml) //A12
                    }catch(Exception e){ //A10
                        String error = e;
                        //A22
                        if (error.contains("JMS") && acetsXml != null){
                            def eventId = this.getFieldValues(xmlGvyString, "msgType=");
                            println("Calling MatGetIntegrationError.createIntegrationError in "+eventId+" message");
                            String entity = "Unit";
                            def errUnitId = this.getFieldValues(xmlGvyString, "ctrNo=");
                            def errDesc = eventId+" Failed for "+errUnitId;
                            def inj = new GroovyInjectionBase();
                            inj.getGroovyClassInstance("MatGetIntegrationError").createIntegrationError(error,entity,errUnitId,eventId,errDesc,xmlGvyString);
                            HibernateApi.getInstance().flush();
                        }
                    }
                    // }//A14 Ends
                    //A21 - Ends        }
                }else if(eventType.startsWith("INGATE_RELEASE")){//A15
                    xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"locationStatus=","1")
                }
            }else if (eventType.equals("SET_EMPTY") || eventType.equals("SET_FULL")){
                msgPostingFlag = false;
                xmlGvyString = postFreightKindFld(xmlGvyString,unit,gvyCmisUtil,gvyEventUtil,gvyBaseClass)
            }else if (eventType.equals("UPU") ){
                xmlGvyString = getUpuFields(xmlGvyString,unit,gvyCmisUtil,gvyBaseClass,equiClass)
            }else if (eventType.equals("UPDATE_UNIT_NOTES") ){
                msgPostingFlag = false;
            }else if (eventType.equals("SHIPPER_REHANDLE")){
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dsc=","C")
            }else if(eventType.equals("SHIPPER_REHANDLE_CANCEL")){
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dsc=","null")
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dss=","null")
            }else if(eventType.equals("UNIT_YARD_MOVE") || eventType.equals("UNIT_POSITION_CORRECTION") || eventType.equals("UNIT_SHIFT_ON_CARRIER")){
                xmlGvyString = getLocationPositionFields(xmlGvyString,gvyBaseClass,unit,gvyCmisUtil)
                msgPostingFlag = false;
            }else if(eventType.equals("UNIT_CHECKDIGIT")){
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"lastAction=","FCD")
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"action=","FCD")
            }else if(eventType.equals("UNIT_DRAY_IN")){
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"lastAction=","EDT")
                xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"action=","EDT")
            } else if(eventType.equals("RESPOT")){
                def respot = unit.getFieldValue("unitActiveUfv.ufvFlexString10")
                println("RESPOT : "+ respot);
                xmlGvyString = xmlGvyString.replace("action='null'","action='EDT'")
                xmlGvyString = xmlGvyString.replace("lastAction='null'","lastAction='EDT'")
                xmlGvyString = xmlGvyString.replace("cell='%'", "cell='"+respot+"'")

                println(xmlGvyString);
            }

            //Skip Hold Default posting
            if (skipEventInList(eventType)){
                msgPostingFlag = false;
            }

            if(msgPostingFlag)
            {
                //println("xmlGvyString ::"+xmlGvyString)

                try{
                    gvyBaseClass.sendXml(xmlGvyString) //A12
                }catch(Exception e){ //A10
                    String error = e;
                    //A22
                    if (error.contains("JMS") && xmlGvyString != null){
                        def eventId = this.getFieldValues(xmlGvyString, "msgType=");
                        println("Calling MatGetIntegrationError.createIntegrationError in "+eventId+" message");
                        String entity = "Unit";
                        def errUnitId1 = this.getFieldValues(xmlGvyString, "ctrNo=");
                        def errDesc = eventId+" Failed for "+errUnitId1;
                        def inj = new GroovyInjectionBase();
                        inj.getGroovyClassInstance("MatGetIntegrationError").createIntegrationError(error,entity,errUnitId1,eventId,errDesc,xmlGvyString);
                        HibernateApi.getInstance().flush();
                    }
                }
                //A12
                //gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,action)
            }

        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }// processFieldChngCmisFeed

    public String nonChangingFields()
    {
        String appendedNonChngFields = "srv='%' dir='%' loc='%' truck='%' pmd='%' dPort='%' shipper='%' vesvoy='%' ds='%' dsc='%' temp='%' seal='%' cell='%' typeCode='%' owner='%' cWeight='%' retPort='%' hsf7='%' bookingNumber='%' arrDate='%' cneeCode='%' dischargePort='%' planDisp='%' locationStatus='%' locationCategory='%' locationRow='%' locationRun='%' locationTier='%' locationStallConfig='%' orientation='%' commodity='%' stowRestCode='%' tareWeight='%' tempMeasurementUnit='%' actualVessel='%' actualVoyage='%' leg='%' loadPort='%' hgt='%' strength='%' gateSeqNo='%' shipperPool='%' misc1='%' misc3='%'  hazImdg='%' hazUnNum='%' gateTruckId='%' gateTruckCmpyCd='%' gateTruckCmpyName='%' batNumber='%' turnTime='%' laneGateId='%' deptTruckCode='%' deptDport='%' deptVesVoy='%'  deptOutGatedDt='%' consigneePo='%' prevAvaildate='%' laneId='%' safetyExpiry='%' lastInspection='%' lastAnnual='%' mgp='%' chassisAlert='%' chassisNotes='%' chassisHold='%' overWideRight='%' overWideLeft='%' overHeight='%' overLongBack='%' overLongFront='%' hazF='%' odf='%'  chassisTareWeight='%' flex01='%' hazDesc='%' hazRegs='%' hazOpenCloseFlag='%' ";

        return appendedNonChngFields
    }

    public String flagFields(Object unit, String eventType, Object gvyTxtMsgFmt)
    {
        def fieldValueAttr = ''
        try
        {
            //DMG_CODE
            def damageCode = unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsDamageSeverity")
            damageCode = damageCode != null ? damageCode.getKey() : damageCode
            def dmgCodeFmt = damageCode.equals('MAJOR') ? 'H' : (damageCode.equals('MINOR') ? 'L' : '%')
            def damageCodeAttr = gvyTxtMsgFmt.doIt('damageCode',dmgCodeFmt)

            //STOW FLAG
            def stowFlag = eventType.equals('REVIEW_FOR_STOW') ? 'Y' : '%'
            def stowFlagAttr = gvyTxtMsgFmt.doIt('stowFlag',stowFlag)

            fieldValueAttr = damageCodeAttr+stowFlagAttr

        }catch(Exception e){
            e.printStackTrace()
        }

        return fieldValueAttr
    }//flag Fields Ends


    //OVER_DIMENSIONS ATTRIBUTES
    public void processOogFields(String xmlData, Object unit,Object gvyCmisUtil,Object event,Object gvyBaseClass)
    {
        def xmlGvyString = xmlData
        try
        {
            def overLongBack=gvyCmisUtil.convertCmToInch(unit.getFieldValue("unitOogBackCm"))
            overLongBack = overLongBack != null ? overLongBack : 0

            def overLongFront=gvyCmisUtil.convertCmToInch(unit.getFieldValue("unitOogFrontCm"))
            overLongFront = overLongFront != null ? overLongFront : 0

            def overWideLeft=gvyCmisUtil.convertCmToInch(unit.getFieldValue("unitOogLeftCm"))
            overWideLeft = overWideLeft != null ? overWideLeft : 0

            def overWideRight=gvyCmisUtil.convertCmToInch(unit.getFieldValue("unitOogRightCm"))
            overWideRight = overWideRight != null ? overWideRight : 0

            def overHeight=gvyCmisUtil.convertCmToInch(unit.getFieldValue("unitOogTopCm"))
            overHeight = overHeight != null ? overHeight : 0

            //ODF
            def odf = unit.getFieldValue("unitIsOog");
            odf = odf == true ? 'Y' : 'null'

            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"odf=",odf)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"overLongBack=",""+overLongBack)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"overLongFront=",""+overLongFront)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"overWideLeft=",""+overWideLeft)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"overWideRight=",""+overWideRight)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"overHeight=",""+overHeight)

            def GvyOvrDimObj =gvyBaseClass.getGroovyClassInstance('GvyCmisEventOverDimension')
            xmlGvyString = GvyOvrDimObj.getUnitDimensionUpdate(xmlGvyString,event,unit)
            gvyBaseClass.sendXml(xmlGvyString)
            try{
                gvyBaseClass.sendXml(xmlGvyString) //A12
            }catch(Exception e){ //A10
                String error = e;
                //A22
                if (error.contains("JMS") && xmlGvyString != null){
                    def eventId = this.getFieldValues(xmlGvyString, "msgType=");
                    println("Calling MatGetIntegrationError.createIntegrationError in "+eventId+" message");
                    String entity = "Unit";
                    def unitId = this.getFieldValues(xmlGvyString, "ctrNo=");
                    def errDesc = eventId+" Failed for "+unitId;
                    def inj = new GroovyInjectionBase();
                    inj.getGroovyClassInstance("MatGetIntegrationError").createIntegrationError(error,entity,unitId,eventId,errDesc,xmlGvyString);
                    HibernateApi.getInstance().flush();
                }
            }
            //A12 - gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,"EDT")

        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public String processPremountEvent(String xmlData,Object unit,Object gvyCmisUtil)
    {
        def xmlGvyString = xmlData
        try
        {
            def pmd = null;
            def chasType =unit.getFieldValue("unitActiveUfv.ufvFlexString02")
            if(chasType != null){
                pmd = chasType;
            }else{
                def _pmdDt =unit.getFieldValue("unitActiveUfv.ufvFlexDate01")
                def strpmd = _pmdDt != null ? (''+_pmdDt) : ''
                pmd =  strpmd.length() > 10 ? strpmd.substring(8,10) : strpmd
            }
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"pmd=",pmd)

        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }//Premount Ends

    public boolean postHoldReleaseAcetsMsg(Object unit,Object gvyBaseClass,Object gvyCmisUtil)
    {
        boolean postAcetsMsg = false
        try
        {
            //-- A1 Starts
            //def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            //lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''

            //def lkpCarrierId=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")
            //lkpCarrierId = lkpCarrierId != null ? lkpCarrierId : ''

            //def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
            //transitState = transitState != null ? transitState.getKey() : ''

            //def category=unit.getFieldValue("unitCategory")
            //category = category != null ? category.getKey() : ''

            //def dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            //dischPort = dischPort != null ? dischPort : ''

            def gvyEquiObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEquiDetail");
            def srv = gvyEquiObj.getSrv(unit,gvyBaseClass)
            //def gvyPositionObj = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionDetail");
            //def locationStatus = gvyPositionObj.getLocationStatus(lkpLocType,lkpCarrierId,transitState,gvyCmisUtil,category,dischPort)
            //-- A1 Ends
            if(srv.equals('MAT')){
                postAcetsMsg = true
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return  postAcetsMsg
    }

    //Method For Event SET_EMPTY / SET_FULL
    public String postFreightKindFld(String xmlData,Object unit,Object gvyCmisUtil,Object gvyEventUtil,Object gvyBaseClass)
    {
        def xmlGvyString = xmlData
        try
        {
            def freightkind=unit.getFieldValue("unitFreightKind")
            freightkind = freightkind != null ? freightkind.getKey() : ''

            def category=unit.getFieldValue("unitCategory")
            category = category != null ? category.getKey() : ''

            def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''

            def expGateBkgNbr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr")

            def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''

            def gdsBlNbr = unit.getFieldValue("unitGoods.gdsBlNbr")

            //ORIENTATION
            def orientation = freightkind.equals('MTY') ? 'E' : (freightkind.length() > 1 ? 'F' : '')
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"orientation=",orientation)

            //MISC3
            def gvyFlexObj = gvyBaseClass.getGroovyClassInstance("GvyCmisFlexFieldDetail")
            def misc3 = gvyFlexObj.getMisc3(unit, gvyEventUtil)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"misc3=",misc3)

            //LAST FREE DAY
            def lastfreeDay = unit.getFieldValue("unitActiveUfv.ufvCalculatedLastFreeDay");
            if (lastfreeDay != null && lastfreeDay.indexOf("no") == -1){
                lastfreeDay = gvyEventUtil.formatDate(lastfreeDay)
                lastfreeDay =  gvyEventUtil.convertToJulianDate(lastfreeDay)
            }else if (lastfreeDay != null && lastfreeDay.indexOf("no") != -1) {
                lastfreeDay = ''
            }
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"locationCategory=",lastfreeDay)

            //DIR
            def gvyRtgObj = gvyBaseClass.getGroovyClassInstance("GvyCmisRtgProcessDetail")
            def dir =  gvyRtgObj.getDir(category,transitState,freightkind,expGateBkgNbr,lkpLocType,gdsBlNbr)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dir=",dir)

            //DPORT
            def dischargePort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"dPort=",dischargePort)

        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }

    //Method gets Messages for UPU MSG TYPE
    public String getUpuFields(String xmlData, Object unit,Object gvyCmisUtil,Object gvyBaseClass,String equiClass)
    {
        def xmlGvyString = xmlData
        def equiType = ''
        try
        {
            //Type Code
            equiType=unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId")
            if(!(equiClass.equals('CHASSIS') || equiClass.equals('ACCESSORY'))){
                def eqHgt=unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypNominalHeight")
                eqHgt = eqHgt != null ? eqHgt.getKey() : ''
                def equiMaterial=unit.getFieldValue("unitPrimaryUe.ueEquipment.eqMaterial")
                equiMaterial = equiMaterial!= null ? equiMaterial.getKey() : ''
                def gvyEquiObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEquiDetail");
                equiType = gvyEquiObj.TypeCodeProcessing(equiType,eqHgt, equiMaterial)
            }

            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"typeCode=",equiType)
            //Line Operator
            def lineOperator=unit.getFieldValue("unitLineOperator.bzuId")
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"chassisHold=",lineOperator)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"locationRow=",lineOperator)

            //OWNER
            def equiOwner =unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId")
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"owner=",equiOwner)

        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }

    //Method Sets the LOC and cell for Events UNIT_YARD_MOVE,UNIT_POSITION_CORRECTION,UNIT_SHIFT_ON_CARRIER
    public String getLocationPositionFields(String xmlData, Object gvyBaseClass,Object unit, Object gvyCmisUtil)
    {
        def xmlGvyString = xmlData
        try
        {
            def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''

            def lkpSlot = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
            lkpSlot = lkpSlot!= null ? lkpSlot : ''
            def lkpSlotValue = lkpSlot.indexOf(".")== -1 ? lkpSlot : lkpSlot.substring(0,lkpSlot.indexOf("."));

            def lkpLocType = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''

            def lkpCarrierId=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")
            lkpCarrierId = lkpCarrierId != null ? lkpCarrierId : ''

            //LOC
            def gvyPositionObj = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionDetail");
            def loc = gvyPositionObj.getLoc(gvyCmisUtil,lkpCarrierId, lkpLocType,lkpSlotValue,transitState)
            loc = loc.length() == 0  ? "null" : loc

            //CELL
            def cell = lkpLocType.equals('VESSEL') ? lkpSlotValue : "null"

            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"loc=",loc)
            xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"cell=",cell)

        }catch(Exception e){
            e.printStackTrace();
        }
        return xmlGvyString
    }

    public boolean skipEventInList(String eventType){
        ArrayList list = new ArrayList();
        list.add('DVI_HOLD');
        list.add('DVI_RELEASE');
        list.add('AGN_HOLD');
        list.add('AGN_RELEASE');
        list.add('USA_HOLD'); //A8
        list.add('USA_RELEASE');
        list.add('ITN_HOLD');
        list.add('ITN_RELEASE');
        list.add('UNIT_HAZARDS_INSERT');
        list.add('UNIT_HAZARDS_UPDATE');
        list.add('UNIT_HAZARDS_DELETE');
        list.add('UPU'); //A13

        def skipBln = list.contains(eventType)
        return skipBln
    }

}