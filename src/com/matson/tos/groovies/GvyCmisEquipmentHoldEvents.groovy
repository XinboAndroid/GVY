/*
* Sr doer  Date      change
* A1 GR    11/01/10  SN4Q : Changes DIR to Category=Storage as dir was not getting computed
* A2 GR    03/14/10  Commented out setting locationStatus=6 coz MNS is getting jammed
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.event.Event
import com.navis.argo.business.atoms.UnitCategoryEnum

public class GvyCmisEquipmentHoldEvents{

    def locationStatus =''
    def gvyCmisUtil = null;

    public String setEquipmentHoldFields(String xmlGvyData, Object event, Object unit)
    {

        def xmlGvyString = xmlGvyData
        GroovyInjectionBase gvyBaseClass = new GroovyInjectionBase()
        def gvyCmisTxtFmt =  gvyBaseClass.getGroovyClassInstance("GvyCmisTxtMsgFormatter");

        try{
            Event gvyEventObj = event.getEvent()
            def eventNotes = gvyEventObj.getEventNote()
            eventNotes = eventNotes != null ? gvyCmisTxtFmt.replaceQuotes(eventNotes) : null
            String eventType =  gvyEventObj.getEventTypeId()
            def u = unit

            //Sets the locationStatus class var
            getLocationStatus(unit)

            def _freightkind=u.getFieldValue("unitFreightKind")
            def freightkind = _freightkind != null ? _freightkind.getKey() : ''

            def holdsList = u.getFieldValue("unitAppliedHoldOrPermName")
            holdsList = holdsList != null ? holdsList : ''


            def appendObj = gvyBaseClass.getGroovyClassInstance("GvyEventSpecificFldValue")
            gvyCmisUtil =  gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");

            def holdFlag = true
            //CHECK ON EVENT TYPE
            if(eventType.equals("CL_HOLD")){
                xmlGvyString = getClaimHold(freightkind, xmlGvyString, eventNotes, appendObj, unit)
                getClaim(xmlGvyString,gvyBaseClass,unit)
                holdFlag = false
            }
            else if(eventType.equals("CG_DMG_HOLD")){
                xmlGvyString = getCgDmgHold(xmlGvyString, appendObj, gvyBaseClass, eventType, unit)
                holdFlag = false
            }
            else if(eventType.equals("CL_RELEASE")){
                getClaimRel(xmlGvyString,gvyBaseClass,unit,appendObj)
                holdFlag = false
            }
            else if(eventType.equals("CG_DMG_RELEASE")){
                getClaimRel(xmlGvyString,gvyBaseClass,unit,appendObj)
                holdFlag = false
            }
            else if(eventType.equals("SHOP_HOLD")){
                xmlGvyString = getShopHold(xmlGvyString,eventType,appendObj,eventNotes)
                holdFlag = false
            }
            else if(eventType.equals('SHOP_RELEASE')){
                xmlGvyString  = getShopRelease(xmlGvyString,appendObj,unit)
                holdFlag = false
            }
            else if(eventType.equals('LTV_HOLD')){
                xmlGvyString  = appendObj.addEventSpecificFldValue(xmlGvyString,"stowFlag=","C",null,null,null)
                holdFlag = false
            }
            else if(eventType.equals('LTV_RELEASE')){
                xmlGvyString  = appendObj.addEventSpecificFldValue(xmlGvyString,"stowFlag=","null",null,null,null)
                holdFlag = false
            }
            else if(eventType.equals('INGATE_HOLD')){
                xmlGvyString  = appendObj.addEventSpecificFldValue(xmlGvyString,"damageCode=","Z",null,null,null)
                holdFlag = false
            }
            else if(eventType.equals('INGATE_RELEASE')){
                xmlGvyString  = appendObj.addEventSpecificFldValue(xmlGvyString,"damageCode=","null",null,null,null)
                holdFlag = false
            }


            //Process fields for CL,CG_DMG and SHOP HOLD
            if(holdFlag)
            {
                def claimsHold = holdsList!= null ? holdsList.indexOf("CL") : -1
                def cgmgHold = holdsList!= null ? holdsList.indexOf("CG_DMG") : -1
                def shopHold = holdsList!= null ? holdsList.indexOf("SHOP") : -1
                def ltvHold = holdsList!= null ? holdsList.indexOf("LTV") : -1
                def ingateHold = holdsList!= null ? holdsList.indexOf("INGATE") : -1

                if(claimsHold != -1){
                    xmlGvyString = getClaimHold(freightkind, xmlGvyString, eventNotes, appendObj, unit)
                }
                if(cgmgHold != -1){
                    xmlGvyString = getCgDmgHold(xmlGvyString, appendObj, gvyBaseClass, eventType, unit)
                }
                if(shopHold != -1){
                    xmlGvyString = getShopHold(xmlGvyString,eventType,appendObj,eventNotes)
                }
                if (ltvHold != -1){
                    if (holdsList != "SHOW LTV"){
                        xmlGvyString  = appendObj.addEventSpecificFldValue(xmlGvyString,"stowFlag=","C",null,null,null)
                    }
                }
                if(ingateHold != -1){
                    xmlGvyString  = appendObj.addEventSpecificFldValue(xmlGvyString,"damageCode=","Z",null,null,null)
                }

            }

        }catch(Exception e){
            e.printStackTrace();
        }


        return xmlGvyString
    }

    //CL_HOLD
    public String getClaimHold(String freightkind, String xmlGvyData, String eventNotes, Object appendObj,Object unit)
    {
        def xmlGvyString = xmlGvyData
        //def locationStatus = appendObj.getFieldValues(xmlGvyString, "locationStatus=");

        def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
        transitState = transitState != null ? transitState.getKey() : ''
        if(!transitState.equals('S40_YARD')){
            xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"consignee=","CLAIMS-MTY",null,null,null)
        }
        def equiType=unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId")
        equiType = equiType != null ? equiType : ''
        if (locationStatus.equals("1") && equiType.startsWith('R'))
        {
            //A2  xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"locationStatus=","6",null,null,null)
        }
        return xmlGvyString
    }

    //CG_DMG_HOLD
    public String getCgDmgHold(String xmlGvyData, Object appendObj, Object gvyBaseClass, String  eventType, Object unit)
    {
        def xmlGvyString = xmlGvyData
        try
        {
            //def locationStatus = appendObj.getFieldValues(xmlGvyString, "locationStatus=");
            def dir = appendObj.getFieldValues(xmlGvyString, "dir=");
            def cargoNotes = appendObj.getFieldValues(xmlGvyString, "cargoNotes=");
            def consignee = appendObj.getFieldValues(xmlGvyString, "consignee=");
            def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
            transitState = transitState != null ? transitState.getKey() : ''
            def category=unit.getFieldValue("unitCategory")

            if(consignee.equals('%') && !transitState.equals('S40_YARD')){
                xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"consignee=","CLAIMS-MTY ",null,null,null)
            }else if(!transitState.equals('S40_YARD')){
                xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"consignee=","CLAIMS-MTY ","L",null,null)
            }
            def cargoNotesValue = appendObj.getFieldValues(xmlGvyString, "cargoNotes=");
            if(!cargoNotesValue.equals('null')){
                xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"cargoNotes=","CG/EQUIP HOLD-","L",null,null)
            }
            else{
                xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"cargoNotes=","CG/EQUIP HOLD",null,null,null)
            }

            //if(locationStatus.equals("1") && dir.equals("MTY")){
            if(locationStatus.equals("1") && UnitCategoryEnum.STORAGE.equals(category)){ //A1
                //A2 xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"locationStatus=","6",null,null,null)
                if(eventType.equals("CG_DMG_HOLD")){
                    postAcetHoldsMsg(xmlGvyString,gvyBaseClass)
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }

        return xmlGvyString
    }

    //SHOP_HOLD
    public String getShopHold(String xmlGvyData,String eventType,Object appendObj, String eventnotes)
    {
        def xmlGvyString = xmlGvyData
        try
        {
            //def  locationStatus = appendObj.getFieldValues(xmlGvyString, "locationStatus=");
            def action = appendObj.getFieldValues(xmlGvyString, "action=");
            def lastaction = appendObj.getFieldValues(xmlGvyString, "lastAction=");

            def cargoNotes = appendObj.getFieldValues(xmlGvyString, "cargoNotes=");
            if(!cargoNotes.equals("null")){
                cargoNotes = eventnotes!= null ? 'TO F&amp;M-'+eventnotes+'.'+cargoNotes : 'TO F&amp;M '+cargoNotes
                xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"cargoNotes=",cargoNotes,null,null,null)
            }else{
                cargoNotes = eventnotes!= null ? 'TO F&amp;M-'+eventnotes+'.' : 'TO F&amp;M '
                xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"cargoNotes=",cargoNotes,null,null,null)
            }

            if(locationStatus.equals("1"))
            {
                //A2 -- xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"locationStatus=","6",null,null,null)
                def actionOld = "action='"+action+"'";
                def lastActionOld = "lastAction='"+lastaction+"'"

                if(eventType.equals('SHOP_HOLD')){
                    xmlGvyString = xmlGvyString.replace(actionOld,"action='TFM'");
                    xmlGvyString = xmlGvyString.replace(lastActionOld,"lastAction='TFM'");
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }

        return xmlGvyString;
    }

    //SHOP_RELEASE
    public String getShopRelease(String xmlGvyData, Object appendObj, Object unit)
    {
        def xmlGvyString = xmlGvyData
        try
        {
            //def  locationStatus = appendObj.getFieldValues(xmlGvyString, "locationStatus=");
            def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''
            def locationStatus = lkpLocType.equals('YARD') ? '1' : '0'

            def cargoNotes = appendObj.getFieldValues(xmlGvyString, "cargoNotes=");
            if(cargoNotes != null )
            {
                cargoNotes = cargoNotes.replace("TO F&amp;M ", "")
                cargoNotes = cargoNotes.replace("TO F&amp;M-", "")
/*        if(cargoNotes.indexOf("TO F&M-") != -1)
       {
          if(cargoNotes.indexOf(".") !=-1){
               cargoNotes = cargoNotes.substring(cargoNotes.indexOf(".")+1)
          }
        }
*/
                xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"cargoNotes=",cargoNotes,null,null,null)
            }

            if(locationStatus.equals("1"))
            {
                xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"locationStatus=","1",null,null,null)
                xmlGvyString = xmlGvyString.replace("lastAction='null'","lastAction='FFM'")
                xmlGvyString = xmlGvyString.replace("action='null'","action='FFM'")
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return xmlGvyString;
    }


    //CL_RELEASE and CG_DMG_RELEASE
    public void getClaim(String xmlGvyData, Object gvyBaseClass, Object unit)
    {
        def xmlGvyString = xmlGvyData
        def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
        def lkpLocTypeKy = lkpLocType != null ? lkpLocType.getKey() : ''
        if(lkpLocTypeKy.equals('YARD')){
            postAcetHoldsMsg(xmlGvyString,gvyBaseClass)
        }
    }

    public void getClaimRel(String xmlGvyData, Object gvyBaseClass, Object unit,Object appendObj)
    {
        def xmlGvyString = xmlGvyData
        def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
        def lkpLocTypeKy = lkpLocType != null ? lkpLocType.getKey() : ''
        if(lkpLocTypeKy.equals('YARD')){
            xmlGvyString = appendObj.addEventSpecificFldValue(xmlGvyString,"locationStatus=","1",null,null,null)
            postAcetHoldsMsg(xmlGvyString,gvyBaseClass)
        }
    }

    //Post Acets Msg for CL/CG Hold -- A1
    public void postAcetHoldsMsg(String xmlGvyData, Object gvyBaseClass){
        def xmlGvyString = xmlGvyData
        gvyCmisUtil = gvyCmisUtil == null ? gvyBaseClass.getGroovyClassInstance("GvyCmisUtil") : gvyCmisUtil;
        gvyCmisUtil.postMsgForAction(xmlGvyData,gvyBaseClass,"CLS")
    }

    public void getLocationStatus(Object unit){
        def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
        lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''
        locationStatus = lkpLocType.equals('YARD') ? '1' : ''
    }

}