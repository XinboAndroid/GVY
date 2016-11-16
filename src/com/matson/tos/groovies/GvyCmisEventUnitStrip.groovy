/*
* srno  Doer  Date       Change
* A1    GR    01-30- 10  added action AMTX
* A2    GR    08/22/10   Added depndMtxActionForSn4Q() to post action DTD,HZD,OVD (SN4Q change)
* A3    GR    09/01/10   Loc Format (SN4Q change)
* A4    GR    01/11/10   NullPointer patch and HazF set to NULL for HZD
* A5    GR    02/11/10   Replaced api Exception and Commented out Detention call
* A6    GR    02/16/11   #011016 - OVD,HZD to be suppress as Gems Doesnt need
* A7    GR    04/05/11   #012223 - Suppress MTX msg for fructose event
* A8    GR    04/07/11   #012223 - Suppress EDT
* A9    GR    04/13/11   #Opened MTX that was commented as fix for A8
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.services.business.event.Event

public class GvyCmisEventUnitStrip
{
    def gvyCmisUtil = null;
    def gvyBaseClass = null;

    public void stripUnit(Object event)
    {
        def gvyStripXml = ''
        try
        {
            gvyBaseClass = new GroovyInjectionBase()
            gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
            Event gvyEventObj = event.getEvent()
            def doer = gvyEventObj.getEvntAppliedBy()
            def eventNotes = gvyEventObj.getEventNote()
            eventNotes = eventNotes != null ? eventNotes : ''
            def unit = event.getEntity()

            /* Set Avail Date */
            def availLookup = gvyBaseClass.getGroovyClassInstance("GvyAvailDate");
            boolean update = availLookup.setAvailDate(unit, event);
            if(eventNotes.startsWith('Gvy MTY'))
            {
                //A2 - Dependant MTX action for Sn4Q and Acets
                depndMtxActionForSn4Q(event,unit, gvyBaseClass)

                /* --- Call To get Strip values --- */
                gvyStripXml = processUnitStrip(unit, eventNotes,gvyEventObj)

                //Detention Msg Check - GR-11/02/10 I dont think this is called
                if(update){
                    //def gvyDentObj = gvyBaseClass.getGroovyClassInstance("GvyCmisDetentionMsgProcess");
                    // gvyDentObj.detentionProcess(gvyStripXml,event,gvyBaseClass)
                }

                //gvyBaseClass.sendXml(gvyStripXml) - Maybe this is the EDT A8
            }
            /* --- Check and Create Release ACETS Msg before Stripping the Unit -- */

            def notesObj = gvyBaseClass.getGroovyClassInstance("GvyCmisCommentNotesField")
            if(!eventNotes.startsWith('Gvy MTY'))
            {
                def holds = notesObj.getUnitActiveHolds(unit)
                if(holds != null && holds.length() > 1 && !doer.contains('ACETS')){
                    processHoldsReleaseStrip(event,holds,doer,unit)
                }

                //A2 - Dependant MTX action for Sn4Q and Acets
                depndMtxActionForSn4Q(event,unit, gvyBaseClass)

                /* --- Call To get Strip values --- */
                gvyStripXml = processUnitStrip(unit, eventNotes, gvyEventObj)

                //Detention Msg Check
                if(update){  // GR-11/02/10 I dont think this is called
                    // def gvyDentObj = gvyBaseClass.getGroovyClassInstance("GvyCmisDetentionMsgProcess");
                    // gvyDentObj.detentionProcess(gvyStripXml,event,gvyBaseClass)
                }
                gvyBaseClass.sendXml(gvyStripXml)  //A9 - Opened This For MTX
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public String processUnitStrip(Object unit, String eventNotes, Object gvyEventObj)
    {
        def groovyXml = ''
        try
        {
            //Calling Msg Formater class
            def gvyTxtMsgFmt = gvyBaseClass.getGroovyClassInstance("GvyCmisTxtMsgFormatter");
            //Unit
            def u = unit

            String eventType = "UNIT_STRIP"
            if(eventNotes.startsWith('Gvy MTY')){
                eventType = "FRUCTOSE_MTY"
            }
            //MSGTYPE
            def eventTypeAttr = gvyTxtMsgFmt.doIt('msgType',eventType)

            // EQUIP CLASS
            def equiClass = u.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            def equiClassKey = equiClass != null ? equiClass.getKey() : equiClass
            def unitClassAttr = gvyTxtMsgFmt.doIt('unitClass', equiClassKey)

            //CTRNO & CHECK DIGIT
            def ctrNo = ''
            def checkDigit = ''
            def unitIdVal=u.getFieldValue("unitId")
            print('unitId ::'+unitIdVal)
            if(equiClassKey.equals('CONTAINER') && unitIdVal.startsWith('MATU')){
                ctrNo = unitIdVal.substring(4,unitIdVal.length()-1)
                checkDigit = unitIdVal.substring(unitIdVal.length()-1)
            }else if(equiClass.equals('ACCESSORY')){
                ctrNo = unitIdVal
                checkDigit = ''
            }else{
                ctrNo =unitIdVal.substring(0,unitIdVal.length()-1)
                checkDigit = unitIdVal.substring(unitIdVal.length()-1)
            }
            def ctrNoAttr = gvyTxtMsgFmt.doIt('ctrNo',ctrNo)
            def checkDigitAttr = gvyTxtMsgFmt.doIt('checkDigit',checkDigit)

            //SRV
            def gvyEquiObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEquiDetail");
            def srv = gvyEquiObj.getSrv(unit,gvyBaseClass)
            def srvAttr = gvyTxtMsgFmt.doIt('srv',srv)

            //LOC
            def lkpSlot=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
            lkpSlot = lkpSlot!= null ? lkpSlot : ''
            def lkpSlotValue = lkpSlot.indexOf(".")== -1 ? lkpSlot : lkpSlot.substring(0,lkpSlot.indexOf("."));
            def lkpLocType=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            def lkpLocTypeKey = lkpLocType != null ? lkpLocType.getKey() : ''
            /*def loc = lkpLocTypeKey.equals('YARD') ? lkpSlotValue : ''
            //A4
            //def gvyPosition = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionDetail");
            //loc = gvyPosition.formatYardPosition(loc)
            if(loc == null || 'null'.equals(loc)){
                loc='%'
            } */ //A9
            def locAttr = gvyTxtMsgFmt.doIt('loc','%')

            //OWNER
            def equiOwner =u.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId")
            def ownerAttr = gvyTxtMsgFmt.doIt('owner',equiOwner)

            //CWEIGHT & TAREWEIGHT
            def equiTareKg= u.getFieldValue("unitPrimaryUe.ueEquipment.eqTareWeightKg")
            def equiTareLB = equiTareKg != null ? Math.round(equiTareKg * 2.20462262 ) : ''
            def cWeightAttr = gvyTxtMsgFmt.doIt('cWeight',equiTareLB)
            def tareWeightAttr = gvyTxtMsgFmt.doIt('tareWeight',equiTareLB)

            //RET_PORT
            def retport = u.getFieldValue("unitRouting.rtgReturnToLocation");
            def retportAttr = gvyTxtMsgFmt.doIt('retPort',retport)

            //STRENGTH CODE
            def equiStrengthCode=u.getFieldValue("unitPrimaryUe.ueEquipment.eqStrengthCode")
            def strengthAttr = gvyTxtMsgFmt.doIt('strength', equiStrengthCode)

            //HARDCODED VALUES
            def dirAttr =  gvyTxtMsgFmt.doIt('dir', 'MTY')
            def dPortAttr =  gvyTxtMsgFmt.doIt('dPort', 'OPT')
            def dischargePortAttr = gvyTxtMsgFmt.doIt('dischargePort', 'OPT')
            def locationRunAttr = gvyTxtMsgFmt.doIt('locationRun', 'NO')
            def orientationAttr = gvyTxtMsgFmt.doIt('orientation', 'E')

            //Location Status
            def _transitState=u.getFieldValue("unitActiveUfv.ufvTransitState")
            def transitStateKey = _transitState != null ? _transitState.getKey() : ''

            def _category=u.getFieldValue("unitCategory")
            def categoryKey = _category != null ? _category.getKey() : ''

            def dischPort = u.getFieldValue("unitRouting.rtgPOD1.pointId")

            def _drayStatus=u.getFieldValue("unitDrayStatus")
            def drayStatusKey = _drayStatus!= null ? _drayStatus.getKey() : _drayStatus

            def lkpCarrierId=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")
            def positionObj = gvyBaseClass.getGroovyClassInstance("GvyCmisPositionDetail");
            def locationType = positionObj.getLocationStatus(lkpLocTypeKey,lkpCarrierId,transitStateKey,gvyCmisUtil,categoryKey,dischPort)
            def locationStatusAttr = gvyTxtMsgFmt.doIt('locationStatus',locationType)

            //DAMAGE_CODE
            def damageCode = u.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsDamageSeverity")
            damageCode = damageCode != null ? damageCode.getKey() : damageCode
            def dmgCodeFmt = damageCode.equals('MAJOR') ? 'H' : (damageCode.equals('MINOR') ? 'L' : '')
            def damageCodeAttr = gvyTxtMsgFmt.doIt('damageCode',dmgCodeFmt)

            //LOCATION ROW
            def lineOperator=u.getFieldValue("unitLineOperator.bzuId")
            def locationRowAttr =  gvyTxtMsgFmt.doIt('locationRow',lineOperator)

            //ACTION INFORMATION
            def actionObj = gvyBaseClass.getGroovyClassInstance("GvyCmisActionDetail");
            def actionAttributes = actionObj.doIt(gvyTxtMsgFmt,gvyEventObj,gvyBaseClass,u)

            def commodity =  getCommodityAuto(unit);
            def commodityAttr =  gvyTxtMsgFmt.doIt('commodity',commodity)
            def doer = gvyCmisUtil.getFieldValues(actionAttributes,"doer=")

            if(doer != null && doer.contains('ACETS')){
                actionAttributes = gvyCmisUtil.eventSpecificFieldValue(actionAttributes,"lastAction=",'AULD')
                actionAttributes = gvyCmisUtil.eventSpecificFieldValue(actionAttributes,"action=",'AULD')
            }else{
                actionAttributes = actionAttributes.replace("lastAction='null'","lastAction='ULD'")
                actionAttributes = actionAttributes.replace("action='null'","action='ULD'")
            }


            //LEG
            def legAttr = gvyTxtMsgFmt.doIt('leg','null')

            //TYPE CODE / HEIGHT
            def typeCodeAttr = gvyTxtMsgFmt.doIt('typeCode','%')
            def hgtAttr = gvyTxtMsgFmt.doIt('hgt','%')

            //Passing List
            def stripAttributes = eventTypeAttr+unitClassAttr+ctrNoAttr+checkDigitAttr+srvAttr+locAttr+ownerAttr+cWeightAttr+tareWeightAttr+retportAttr+strengthAttr+dirAttr+dPortAttr+dischargePortAttr+locationRunAttr+orientationAttr+locationStatusAttr+damageCodeAttr+locationRowAttr+' '+actionAttributes+legAttr+typeCodeAttr+hgtAttr+commodityAttr

            groovyXml = gvyTxtMsgFmt.createGroovyXml(stripAttributes)

        }catch(Exception e){
            e.printStackTrace();
        }
        return groovyXml
    }//Method Ends

    public String getCommodityAuto(Object unit)
    {
        def commodity = ''
        try
        {
            //set commodity value
            def commodityId=unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")
            commodity = commodityId != null ? commodityId : ''
            if(commodity.equals('AUTO')){
                commodity='MTYAUT'
            }else if(commodity.equals('AUTOCON')){
                commodity='MTYCFS'
            }

        }catch(Exception e){
            e.printStackTrace();
        }
        return commodity
    }

    //Generate Release Records for ACETS before Stripping the unit
    public void processHoldsReleaseStrip(Object event, String holds,String evntDoer,Object unit)
    {
        try{
            def unitProcessor = gvyBaseClass.getGroovyClassInstance("GvyCmisDataProcessor")
            def appendObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue")
            def evntFldChngProcess = gvyBaseClass.getGroovyClassInstance("GvyCmisEvntFldChngProcessor")
            def unitXml = unitProcessor.doIt(event)
            def xmlGvyString = unitXml

            def unitHolds = holds != null ? holds : ''
            def holdsList = unitHolds.split(' ')
            def cmisAction = 'ALT';
            for(aHold in holdsList)
            {
                if(!evntDoer.contains('ACETS'))
                {
                    xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"msgType=",aHold+'_RELEASE',null,null,null)
                    xmlGvyString = stripHoldOfField(appendObj,xmlGvyString,"crStatus=",aHold)
                    xmlGvyString = stripHoldOfField(appendObj,xmlGvyString,"comments=",aHold)
                    xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"loc=","%",null,null,null) //A9

                    // To Pass back acets Messages
                    def postAcetsMsg = evntFldChngProcess.postHoldReleaseAcetsMsg(unit,gvyBaseClass,gvyCmisUtil)
                    if(postAcetsMsg){
                        gvyCmisUtil.postMsgForAction(xmlGvyString,gvyBaseClass,'HLR')
                    }

                    cmisAction = 'ALT'
                    xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"lastAction=",cmisAction,null,null,null)
                    xmlGvyString =  appendObj.addEventSpecificFldValue(xmlGvyString,"action=",cmisAction,null,null,null)
                    gvyBaseClass.sendXml(xmlGvyString)
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    //Strip of Hold from CrStatus and Comments
    public String stripHoldOfField(Object utilObj, String xmlGvyData, String field, String aHold)
    {
        def xmlGvyString = xmlGvyData
        try
        {
            def  fieldValue = utilObj.getFieldValues(xmlGvyString, field)
            def fieldNew = fieldValue.replace(aHold,'')
            fieldNew = fieldNew != null && fieldNew.trim().length() > 0  ? fieldNew.trim() : 'null'
            xmlGvyString = utilObj.addEventSpecificFldValue(xmlGvyString,field,fieldNew,null,null,null);
        }
        catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString
    }

    //Method Check only to Pass one UNIT_STRIP
    public boolean checkAcetsStrip(Object event)
    {
        try
        {
            def doer = event.event.evntAppliedBy
            def evntNotes = event.event.evntNote
            evntNotes = evntNotes != null ? evntNotes : ''
            if(doer.contains('ACETS') && evntNotes.contains('Stripped')){
                return false;
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return true;
    }

    /* A2 - Method post Strip MTX dependent actions on the SN4Q */
    public void depndMtxActionForSn4Q(Object event,Object unit, Object gvyBaseClass){
        try{
            def unitProcessor = gvyBaseClass.getGroovyClassInstance("GvyCmisDataProcessor")
            def unitXml = unitProcessor.doIt(event)

            def inComplex = unit.getFieldValue("unitComplex.cpxGkey")
            def equiGKey = unit.getFieldValue("unitPrimaryUe.ueEquipment.ueGkey")
            def inFacility = com.navis.argo.ContextHelper.getThreadFacility()

            def gateData = gvyBaseClass.getGroovyClassInstance("GvyUnitUtility")
            def retiredUfv = gateData.findRetiredUfvUnit(inFacility, equiGKey)
            //Previous misc value
            def retiredUnit = retiredUfv.ufvUnit
            def misc3 = retiredUnit.unitSealNbr4
            misc3 = misc3 != null ? misc3 : ''
            def dischPort = retiredUnit.unitRouting.rtgPOD1 != null ?  retiredUnit.unitRouting.rtgPOD1.pointId : ''  //A4
            //DTD Messages
            if(ContextHelper.getThreadFacility().getFcyId().equals(dischPort) && misc3.length() > 6){
                gvyCmisUtil.postMsgForAction(unitXml,gvyBaseClass,'DTD')
            }

            //HZD and OVD messages out here
            //-- A6 postMsgHzdOvd(unitXml,retiredUnit,gvyBaseClass,event)
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /*  public void postMsgHzdOvd(String xmlData,Object unit,Object gvyBaseClass,Object event)
      {
         def xmlGvyString = xmlData
         def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");
         def isHazardous=unit.getFieldValue("unitGoods.gdsIsHazardous")
         def outOfGauge  = unit.getFieldValue("unitIsOog");
         def msgType = gvyCmisUtil.getFieldValues(xmlGvyString,"msgType=")

         if(isHazardous)
         {
           HazardItem hazardIt = null; def imdg = null; def hzrdItemNbrType = null;  def mostHazNum=null;
           def hazDesc= null; def hazRegs = null;

           def gdsBase= unit.getUnitGoods()
           Hazards hazards = gdsBase.getGdsHazards();
           Iterator it = hazards.getHazardItemsIteratorOrderedBySeverity()
           while (it.hasNext()) {
             hazardIt = (HazardItem) it.next();
             imdg = hazardIt.hzrdiImdgClass.key
             hzrdItemNbrType = hazardIt.hzrdiNbrType.key
             mostHazNum = hazardIt.hzrdiUNnum
             hazDesc = hazardIt.getDescription()
             if(hazDesc != null){
               hazDesc = hazDesc.indexOf(' ') != -1 ? hazDesc.substring(hazDesc.indexOf(' ')+1) : null
             }
             hazRegs = hazDesc != null ? (hazDesc.contains('Liquid') ? 'DOT' : 'IMO') : ''

             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazImdg=",imdg)
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"flex01=",hzrdItemNbrType)
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazUnNum=",mostHazNum)
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazDesc=",hazDesc)
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazRegs=",hazRegs)
             xmlGvyString = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"hazF=","null")

            def xmlGvyHazStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"msgType=","UNIT_HAZARDS_DELETE")
            gvyCmisUtil.postMsgForAction(xmlGvyHazStr,gvyBaseClass,"HZD")

           }//While Ends

         }
         if(outOfGauge) //A6
         {
           def xmlGvyOvuStr = gvyCmisUtil.eventSpecificFieldValue(xmlGvyString,"msgType=","UNIT_OVERDIMENSIONS_DELETE")
           gvyCmisUtil.postMsgForAction(xmlGvyOvuStr,gvyBaseClass,"OVD")
         }
      }  */
}