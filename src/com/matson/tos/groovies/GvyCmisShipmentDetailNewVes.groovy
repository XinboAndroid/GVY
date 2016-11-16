/*
* Srno  Change Date        Desc
* A1    GR     01/29/2010  If No Avail Date the Dont pass last Free Date
* A2    GR     03/17/2010  Added ShipperId Field for DAS
* A3    GR     05/19/10    Added method for lastfreeDay to reuse in Detention Code
* A4    GR     07/13/10    Add to Fields HazDesc,HazRegs and Nbr
*/
import com.navis.inventory.business.imdg.ImdgClass
import com.navis.inventory.business.imdg.HazardItem;
import com.navis.inventory.business.imdg.Hazards;
import com.navis.inventory.business.units.GoodsBase;
import com.navis.argo.business.reference.ScopedBizUnit;

public class GvyCmisShipmentDetailNewVes {

    public String doIt(Object gvyTxtMsgFmt, Object unit, String eventMsg, Object gvyBaseClass, Object event, Object isUnitObj)
    {
        println("In Class GvyCmisShipmentDetail.doIt()")


        def shipmentFieldAttr = ''
        try
        {
            //BOOKING NUMBER
            def bookingNbr = getBookingNumber(unit, eventMsg)
            def bookingNbrAttr = gvyTxtMsgFmt.doIt('bookingNumber',bookingNbr)

            //ARRDATE
            def arrDate = ''
            def arrDateAttr = gvyTxtMsgFmt.doIt('arrDate',arrDate)

            //CONSIGNEE
            def consignee=getConsigneeValue(unit,eventMsg)
            def consigneeAttr = gvyTxtMsgFmt.doIt('consignee',consignee)

            //SHIPPER
            def shipper = ''
            if(eventMsg.equals('COMMUNITY_SERVICE_ASSIGN')){
                shipper='COMMUNITY SERVICE'
            }else{
                shipper=unit.getFieldValue("unitGoods.gdsShipperBzu.bzuName")
                shipper = shipper != null ? shipper : unit.getFieldValue("unitGoods.gdsShipperAsString")
            }
            def shipperAttr = gvyTxtMsgFmt.doIt('shipper',shipper)

            //SHIPPER ID
            def shipperId=unit.getFieldValue("unitGoods.gdsShipperBzu.bzuId")
            def shipperIdAttr = gvyTxtMsgFmt.doIt('shipperId',shipperId)

            //CNEE CODE
            def consigneeId=unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId");
            def cneeCodeAttr = gvyTxtMsgFmt.doIt('cneeCode',consigneeId)

            //HAZF
            def isHazardous=unit.getFieldValue("unitGoods.gdsIsHazardous")
            isHazardous = isHazardous == true ? 'Y' : ''
            def hazfAttr = gvyTxtMsgFmt.doIt('hazF',isHazardous)

            //HAZ IMDG
            def hazItem = getMostHazItem(unit)
            def mostHazItem =  hazItem != null ? hazItem.hzrdiImdgClass.key : null
            mostHazItem = mostHazItem != null && mostHazItem.equals('X') ? 'HAZ' : mostHazItem
            def hazImdgAttr = gvyTxtMsgFmt.doIt('hazImdg',mostHazItem)

            //HAZ NBR Type
            def hzrdItemNbrType = hazItem != null ? hazItem.hzrdiNbrType.key : null
            def hzrdItemNbrTypeAttr = gvyTxtMsgFmt.doIt('flex01',hzrdItemNbrType)

            //HAZ UNNUM
            def mostHazNum = hazItem != null ? hazItem.hzrdiUNnum : ''
            def hazUnNumAttr = gvyTxtMsgFmt.doIt('hazUnNum',mostHazNum)

            //HazDesc
            def hazardItemDesc = hazItem != null ? hazItem.getDescription() : null
            def hazardItemDescAttr = gvyTxtMsgFmt.doIt('hazDesc',hazardItemDesc)
            if(hazardItemDesc != null){
                hazardItemDesc = hazardItemDesc.indexOf(' ') != -1 ? hazardItemDesc.substring(hazardItemDesc.indexOf(' ')+1) : null
            }
            //HazReg
            def hazardItemRegs = hazardItemDesc != null ? (hazardItemDesc.contains('Liquid') ? 'DOT' : 'IMO') : ''
            def hazardItemRegsAttr = gvyTxtMsgFmt.doIt('hazRegs',hazardItemRegs)


            //LOCATION CATEGORY - LAST FREE DATE - YY DOY
            def lastfreeDay = unit.getFieldValue("unitActiveUfv.ufvCalculatedLastFreeDay");
            def availDate=unit.getFieldValue("unitActiveUfv.ufvFlexDate02") //A1
            lastfreeDay = getlastFreeDate(availDate, lastfreeDay, gvyBaseClass)
            def locationCategoryAttr = gvyTxtMsgFmt.doIt('locationCategory',lastfreeDay)

            //CONSIGNEE PO
            def consigneePo=unit.getFieldValue("unitFlexString01")
            def consigneePoAttr = gvyTxtMsgFmt.doIt('consigneePo',consigneePo)

            //RESTOW
            def restow = unit.getFieldValue("unitActiveUfv.ufvRestowType")
            restow = restow != null ? restow.getKey() : ''
            def restowAttr = gvyTxtMsgFmt.doIt('restow',restow)

            shipmentFieldAttr = bookingNbrAttr+consigneeAttr+shipperAttr+cneeCodeAttr+hazfAttr+hazImdgAttr+hazUnNumAttr+locationCategoryAttr+arrDateAttr+consigneePoAttr+restowAttr+shipperIdAttr+hazardItemDescAttr+hazardItemRegsAttr
        }catch(Exception e){
            e.printStackTrace()
        }
        //println('shipmentFieldAttr : '+shipmentFieldAttr)

        return shipmentFieldAttr;

    }

    public String getlastFreeDate(Object availDate, String lastfreeDay, Object gvyBaseClass){

        if(availDate == null){
            lastfreeDay = ''
        }
        else if (lastfreeDay != null && lastfreeDay.indexOf("no") == -1)
        {
            def gvyUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            lastfreeDay = formatDate(lastfreeDay)
            lastfreeDay =  gvyUtil.convertToJulianDate(lastfreeDay)
        }else if (lastfreeDay != null && lastfreeDay.indexOf("no") != -1) {
            lastfreeDay = ''
        }
        return lastfreeDay
    }

    //Retrieves the Consignee Value
    public String getConsigneeValue(Object unit, String eventMsg)
    {
        def fmtConsigneeVal = ''
        try
        {
            def consignee=unit.getFieldValue("unitGoods.gdsConsigneeBzunit.bzuName")
            consignee  = consignee != null ? consignee : unit.getFieldValue("unitGoods.gdsConsigneeAsString")
            consignee = consignee != null ? consignee : ""
            def releaseToParty = unit.getFieldValue("unitFlexString02")
//  releaseToParty  =  releaseToParty != null ? (releaseToParty.length() >10 ? //releaseToParty.substring(0,10) : releaseToParty) : ""
            releaseToParty  =  releaseToParty != null ? releaseToParty : ""

            if(eventMsg.equals('COMMUNITY_SERVICE_ASSIGN')){
                consignee= releaseToParty
            }
            else if(consignee.startsWith("PACIFIC TRANSPORTATION LINES") && releaseToParty.length() > 0){
                consignee= releaseToParty.length() > 0 ? "PAC TRAN LINES%"+releaseToParty :
                        "PAC TRAN LINES"
            }
            else{
                consignee= releaseToParty.length() > 0 ? consignee+"%"+releaseToParty : consignee
            }
            fmtConsigneeVal = formatConsigneeSize(consignee);
        }catch(Exception e){
            e.printStackTrace();
        }
        return fmtConsigneeVal;

    }

    private String formatConsigneeSize(String _consignee)
    {
        String consigneeVal = null;
        try
        {
            String consignee = _consignee;
            if(consignee != null && consignee.length() > 35)
            {
                int conLength = consignee.length();
                int perIndex = consignee.indexOf("%");
                int stripIndex = conLength - 35;
                if(perIndex > 0 && perIndex - stripIndex > 0){
                    consigneeVal = consignee.substring(0, perIndex - stripIndex)+consignee.substring(perIndex);
                }else{
                    consigneeVal =  consignee.substring(0,35);
                }
            }else{
                consigneeVal =  consignee;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return consigneeVal;
    }

    //Method formats date to yyyy-MM-dd
    public static String formatDate(String str_date)
    {
        java.text.DateFormat formatter = null ; Date date = null;  String finalDate = null;
        try
        {
            formatter = new java.text.SimpleDateFormat("yyyy-MMM-dd");
            date = (Date)formatter.parse(str_date);
            def reqformat = new java.text.SimpleDateFormat("yyyy-MM-dd");
            finalDate = reqformat.format(date);
        }catch(Exception e){
            e.printStackTrace();
        }
        return finalDate;
    }


    public String getBookingNumber(Object unit, String eventMsg){
        def bookingNbr = ''
        try{
            //BOOKING NUMBER
            bookingNbr = unit.getFieldValue("unitGoods.gdsBlNbr")
            bookingNbr = bookingNbr != null ? bookingNbr : ''
            def equiOperator=unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOperator.bzuId")
            equiOperator = equiOperator != null ? equiOperator : ''
            if((eventMsg.equals("UNIT_IN_GATE") || eventMsg.equals("UNIT_RECEIVE")) && equiOperator.equals('MAT')){
                bookingNbr = bookingNbr.length() > 7 ? bookingNbr.substring(0,7) : bookingNbr
            }
        }
        catch(Exception e){
            e.printStackTrace()
        }
        return bookingNbr
    }


    public HazardItem getMostHazItem(Object unit)
    {
        HazardItem hazardIt = null;
        try
        {
            Hazards hazards = unit.getUnitGoods().getGdsHazards();
            int count = 0;
            if (hazards != null)
            {
                Iterator iterator = hazards.getHazardItemsIteratorOrderedBySeverity();
                for (aHazItem in iterator) {
                    hazardIt = (HazardItem)aHazItem;
                    return hazardIt;
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return hazardIt
    }


}//Class Ends