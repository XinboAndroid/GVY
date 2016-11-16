public class GvyCmisChassisAttributes{

    //Method gets the chassis attributes
    public String getChassisAttributes(Object gvyTxtMsgFmt, Object unit, String unitClass,String eventType)
    {
        def chassisAttr= ''
        try
        {
            //Saftey Expiry
            def safetyExpiry = '%'
            def safetyExpAttr = gvyTxtMsgFmt.doIt('safetyExpiry',safetyExpiry)

            //Last Inspection
            def lastInspection = '%'
            def lastInspectAttr = gvyTxtMsgFmt.doIt('lastInspection',lastInspection)

            //Last Annual
            def lastAnnual = '%'
            def lastAnnualAttr = gvyTxtMsgFmt.doIt('lastAnnual',lastAnnual)

            //Chassis Alert
            def chassisAlert = '%'
            def chassisAlertAttr = gvyTxtMsgFmt.doIt('chassisAlert',chassisAlert)

            //MGP
            def mgp = ''
            def mgpAttr = gvyTxtMsgFmt.doIt('mgp',mgp)

            //CHASSIS HOLD
            def chassisHold =  unit.getFieldValue("unitLineOperator.bzuId")
            def chassisHoldAttr = gvyTxtMsgFmt.doIt('chassisHold',chassisHold)

            //CHASSIS NOTES
            def chassisNotes = '%'
            def chassisNotesAttr = gvyTxtMsgFmt.doIt('chassisNotes',chassisNotes)

            //Chassis Tare weight
            def chassisweight = ''
            def chassTareWeightAttr = ''
            if(unitClass.equals('CHASSIS')){
                chassisweight = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqTareWeightKg")
            }else{
                chassisweight = unit.getFieldValue("unitCarriageUe.ueEquipment.eqTareWeightKg")
            }
            def chassisTareWeight = chassisweight != null ? Math.round(chassisweight * 2.20462262 ) : ''
            if(eventType.equals('UNIT_DISMOUNT')) {
                chassTareWeightAttr = gvyTxtMsgFmt.doIt('chassTareWeight','%')
            }else{
                chassTareWeightAttr = gvyTxtMsgFmt.doIt('chassTareWeight',chassisTareWeight)
            }


            chassisAttr = safetyExpAttr+lastInspectAttr+lastAnnualAttr+chassisAlertAttr+mgpAttr+chassisHoldAttr+chassisNotesAttr+chassTareWeightAttr

            //println('chassisAttr ::'+chassisAttr)
        }catch(Exception e){
            e.printStackTrace()
        }

        return  chassisAttr;

    }


}