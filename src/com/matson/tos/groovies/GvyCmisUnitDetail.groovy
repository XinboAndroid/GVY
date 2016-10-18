/*
*  Srno  Changer Date        Desc
*  A1    GR      03/17/2010  Removed Two Fields
*  A2    GR      04/07/2010  Added Facility Fields to the Out messages
*  A3    GR      12/09/10    Append CntrNbr and checkDigit attributes to bare chassis and Acry equipment as NULL
*  A4    GR      07/11/11    Added Check for OWN as Chassis Value
*  A5    GR      11/07/11    Added FLEX02 for OGA YBBARGE
*  A6    GR      12/02/11    Gems: flex02 valid valid on PDU and OGA else %
*  A7    GR      12/02/11    Gems: added YB trucker
*  A9    GR      12/05/11  renamed attribute flex02 to ybBarge
*  A10    GR     12/05/11  renamed attribute flex02 to ybBarge
*/
import com.navis.argo.ContextHelper
import com.navis.services.business.event.Event

public class GvyCmisUnitDetail {

    public String doIt(String equiClass, Object gvyTxtMsgFmt, Object unit, Object event)
    {

        println("In Class GvyCmisUnitDetail.doIt()")
        def unitFieldAttr = ''
        def ctrNoAttr = ''
        def checkDigitAttr = ''

        def unitClassAttr = gvyTxtMsgFmt.doIt('unitClass', equiClass)
        try
        {
            //CTRNO & CHECK DIGIT
            def ctrNo = 'null'
            def checkDigit = 'null'
            def unitIdVal=unit.getFieldValue("unitId")
            unitIdVal = unitIdVal != null ? unitIdVal : ""
            print('PRIMARY UNIT ID  :::::'+unitIdVal+"  equiClass :::::"+equiClass)
            if(equiClass.equals('CONTAINER') && unitIdVal.startsWith('MATU')){
                ctrNo = unitIdVal.substring(4,unitIdVal.length()-1)
                checkDigit = unitIdVal.substring(unitIdVal.length()-1)
            }
            else{
                ctrNo =unitIdVal.substring(0,unitIdVal.length()-1)
                checkDigit = unitIdVal.substring(unitIdVal.length()-1)
            }
            //Do not pass ctrNo and ctrCheckDigit for Primary unit chassis and accessory
            if(equiClass.equals('CONTAINER')){ //A3
                ctrNoAttr = gvyTxtMsgFmt.doIt('ctrNo',ctrNo)
                checkDigitAttr = gvyTxtMsgFmt.doIt('checkDigit',checkDigit)
            }else if(equiClass.equals('CHASSIS') ||  equiClass.equals('ACCESSORY')){
                ctrNoAttr = gvyTxtMsgFmt.doIt('ctrNo','null')
                checkDigitAttr = gvyTxtMsgFmt.doIt('checkDigit','null')
            }

            //CHASSIS NUMBER
            def chassisNum,chassisNumCd = "null"; //A15 -  If Value is OWN Stick Null
            def carriage = unit.getFieldValue("unitCarriageUe.ueEquipment.eqIdFull")
            if(!"OWN".equalsIgnoreCase(carriage)){
                def chassisId = carriage != null ? carriage.substring(0,carriage.length()-1) : carriage
                chassisNum = equiClass.equals('CHASSIS') ? ctrNo : chassisId
                //CHASSIS CHECK DIGIT
                def chassisCd = carriage != null ? carriage.substring(carriage.length()-1) : ''
                chassisNumCd = equiClass.equals('CHASSIS') ? checkDigit : chassisCd
            }

            def chassisNumAttr = gvyTxtMsgFmt.doIt('chassisNumber',chassisNum)
            def chassisCdAttr = gvyTxtMsgFmt.doIt('chassisCd',chassisNumCd)

            //ACCESSORY & ACCESSORY WEIGHT
            def accessory = ''
            def acryTareWeight = ''
            if(equiClass.equals('CONTAINER'))
            {
                accessory = unit.getUnitAcryEquipIds()
                def acryObj = unit.getUnitCtrAccessory()
                acryTareWeight = acryObj != null ? acryObj.getEqTareWeightKg() : null
                acryTareWeight = acryTareWeight != null ? Math.round(acryTareWeight * 2.20462262 ) : ''
            }
            else if (equiClass.equals('CHASSIS'))
            {
                accessory = unit.getUnitChsAcryId()
                def acryObj = unit.getUnitChsAccessory()
                acryTareWeight = acryObj != null ? acryObj.getEqTareWeightKg() : null
                acryTareWeight = acryTareWeight != null ? Math.round(acryTareWeight * 2.20462262 ) : ''
            }
            else if(equiClass.equals('ACCESSORY'))
            {
                accessory = unitIdVal
                acryTareWeight = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqTareWeightKg")
                acryTareWeight = acryTareWeight != null ? Math.round(acryTareWeight * 2.20462262 ) : ''
            }

            //Accessory & AcessoryWeight
            def accessoryAttr = gvyTxtMsgFmt.doIt('accessory',accessory)
            def mgWeightAttr = gvyTxtMsgFmt.doIt('mgWeight',acryTareWeight)

            //CATEGORY
            def category=unit.getFieldValue("unitCategory")
            category = category != null ? category.getKey() : ''
            def categoryAttr = gvyTxtMsgFmt.doIt('category',category)

            //Event Fields Changed Previous Value
            //def fieldsChanged = ''
            //def fieldsChangedAttr = gvyTxtMsgFmt.doIt('fieldsChanged',fieldsChanged)

            //Event Fields Changed
            //def fieldsPrevoiusValue = ''
            //def fieldsPrevoiusValueAttr = gvyTxtMsgFmt.doIt('fieldsPrevoiusValue',fieldsPrevoiusValue)

            //Facility
            //def inFacility = ContextHelper.getThreadFacility()
            //def fcyId = inFacility.fcyId
            //def facilityAttr = gvyTxtMsgFmt.doIt('facility',inFacility.fcyId)
            //Facility psethuraman - making generic code changes for all facilities
            def inFacility = ContextHelper.getThreadFacility()
            def fcyId = inFacility.fcyId
            Event gvyEventObj = event.getEvent();
            def eventFcyId = fcyId
            if (gvyEventObj != null) {
                eventFcyId = gvyEventObj.getEvntFacility() != null ? gvyEventObj.getEvntFacility().fcyId : fcyId
            }

            def facilityAttr = gvyTxtMsgFmt.doIt('facility',eventFcyId)

            //ADDED FOR YBBARGE VALUE ONLY ON OGA
            def ybBarge= '%';
            def ybBargeAttr = gvyTxtMsgFmt.doIt('ybBarge',ybBarge)

            //ADDED FOR YBBARGE VALUE ONLY ON OGA
            def flex02= '%';
            def flex02Attr = gvyTxtMsgFmt.doIt('flex02',flex02)

            //ADDED FOR YBTRUCKER
            def ybTrucker= unit.getFieldValue("unitFlexString14");
            def ybTruckerAttr = gvyTxtMsgFmt.doIt('ybTrucker',ybTrucker)

            unitFieldAttr = unitClassAttr+ctrNoAttr+checkDigitAttr+chassisNumAttr+chassisCdAttr+categoryAttr+accessoryAttr+mgWeightAttr+facilityAttr+ybBargeAttr+ybTruckerAttr+flex02Attr
            // println('unitFieldAttr : '+unitFieldAttr)
        }catch(Exception e){
            e.printStackTrace()
        }
        return unitFieldAttr;

    }

}//Class Ends