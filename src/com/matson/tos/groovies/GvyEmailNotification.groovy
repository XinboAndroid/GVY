/*
*
* 08/16/11 2.1 Updated Email Method
*/
import com.navis.inventory.business.units.UnitEquipment
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.Chassis
import com.navis.argo.business.atoms.LocTypeEnum;


public class GvyEmailNotification
{
    private String dviEmailto = '1aktosdevteam@matson.com'
    private static final String eol = "\r\n";
    private static final String tab = "\t";

    public void notifyDviHold(Object event, Object api){
        def unit = event.getEntity();
        def eventId = event.event.eventTypeId
        def CntrNbr = null;    def chasId = null;
        try{
            chasId = unit.getFieldValue("unitCarriageUe.ueEquipment.eqIdFull")
            def primaryClass = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")

            if(EquipClassEnum.CONTAINER.equals(primaryClass)){
                CntrNbr = unit.unitId
            }else if (EquipClassEnum.CHASSIS.equals(primaryClass)){
                chasId = unit.unitId
                CntrNbr = "NA"
            }

            if(chasId == null){
                return;
            }

            Chassis chs = Chassis.findChassis(chasId);
            if('DVI'.equals(chs.eqLicenseNbr)){
                def blnbr=unit.getFieldValue("unitGoods.gdsBlNbr")
                if(!LocTypeEnum.YARD.equals(unit.unitActiveUfv.ufvLastKnownPosition.posLocType)){
                    return;
                }
                def lkpSlot=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
                lkpSlot = lkpSlot != null ? lkpSlot : 'Y-SI'

                def aobcarrierId=unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId")
                def aibcarrierId=unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId")
                def dischargePort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
                dischargePort = dischargePort != null ? dischargePort : ''
                def category=unit.getFieldValue("unitCategory").getKey()
                String strDate = ContextHelper.formatTimestamp(new Date(), ContextHelper.getThreadUserTimezone())

                StringBuffer buf = new StringBuffer();
                buf.append(eol);
                buf.append("            INYARD CHASSIS WITH DVI HOLD NOTICE     Date:"+strDate+eol);
                buf.append("             Matson Navigation Honolulu, Hawaii                "+eol);
                buf.append("-------------------------------------------------------------------------");
                buf.append(eol);
                buf.append(eol);
                buf.append("Chassis ID         : "+chasId);
                buf.append(eol);
                buf.append("Container Number   : "+CntrNbr);
                buf.append(eol);
                buf.append("Category           : "+category);
                buf.append(eol);buf.append(eol)
                buf.append("Yard Location      : "+lkpSlot);
                buf.append(eol);
                buf.append("Carrier Inbound    : "+aibcarrierId);
                buf.append(eol);
                buf.append("Carrier Outbound   : "+aobcarrierId);
                buf.append(eol);
                buf.append("Discharge Port     : "+dischargePort);
                buf.append(eol);
                buf.append("N4 Event           : "+eventId);
                buf.append(eol); buf.append(eol); buf.append(eol);
                buf.append(eol); buf.append(eol); buf.append(eol);

                //--  String body = getEmailContent(chasId,CntrNbr,category,lkpSlot,aibcarrierId,aobcarrierId,dischargePort,strDate)
                def emailSender = api.getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(dviEmailto, 'INYARD CHASSIS WITH DVI HOLD: '+chasId , buf.toString())
                //--HTML CODE emailSender.sendMail(emailfrom,emailTo, 'INYARD CHASSIS WITH DVI HOLD: '+chasId , body)
            }
        }catch(Exception e){
            e.printStackTrace();
        }

    }



    public String getEmailContent(String chasId,String CntrNbr,String category,String lkpSlot, String aibcarrierId,String aobcarrierId,String dischargePort,String strDate)
    {
        String emailContent = "<html><head>"+
                "<meta http-equiv='Content-Language' content='en-us'>"+
                "<meta http-equiv='Content-Type' content='text/html; charset=windows-1252'>"+
                "<title>New Page 1</title></head><body>"+
                "<table border='0' width='67%' id='table1'>"+
                "<tr><td height='79'><table border='0' width='100%' id='table4'>"+
                "<tr><td><p align='center'>INYARD CHASSIS WITH DVI HOLD NOTICE</td>"+
                "<td width='136'><font SIZE='2'>Date :"+strDate+"</font></td>"+
                "</tr><tr><td><p align='center'>Matson Navigation Honolulu, Hawaii </td>"+
                "<td width='136'>&nbsp;</td>"+
                "</tr></table></td></tr><tr>"+
                "<td><table border='0' width='100%' id='table3'>"+
                "<tr nowrap>"+
                "<td width='85'><b><span LANG='EN'><font SIZE='2'>Chassis Id</font></span></b></td>"+
                "<td width='95'><b><span LANG='EN'><font SIZE='2'>Container Nbr</font></span></b></td>"+
                "<td width='61'><b><font size='2'>Category</font></b></td>"+
                "<td width='65'><b><font size='2'>Yard Loc</font></b></td>"+
                "<td width='85'><b><font size='2'>IB Carrier</font></b></td>"+
                "<td width='100'><b><font size='2'>OB Carrier</font></b></td>"+
                "<td><b><font size='2'>POD</font></b></td>"+
                "</tr></table></td></tr>"+
                "<tr><td><table border='0' width='100%' id='table2'><tr nowrap>"+
                "<td width='84'><font SIZE='2'>"+chasId+"</font></td>"+
                "<td width='97'><font SIZE='2'>"+CntrNbr+"</font></td>"+
                "<td width='61'><font SIZE='2'>"+category+"</font></td>"+
                "<td width='65'><font SIZE='2'>"+lkpSlot+"</font></td>"+
                "<td width='85'><font SIZE='2'>"+aibcarrierId+"</font></td>"+
                "<td width='100'><font SIZE='2'>"+aobcarrierId+"</font></td>"+
                "<td><font SIZE='2'>"+dischargePort+"</font></td>"+
                "</tr></table></td></tr></table></body></html>";
        return emailContent;
    }

}