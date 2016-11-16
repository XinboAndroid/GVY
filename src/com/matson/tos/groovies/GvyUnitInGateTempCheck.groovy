/*
* A1  01/11/10  GR   Temp Discrepancy Check for Import Cntr.
* A2  01/15/10  GR   Added EmailSender Groovy Code
* A3  01/18/10  GR   Report Text Formattter Code
* A4  04/26/10  GR   NumberFormatException check
* 08/16/11 2.1 Updated Email Method
* A5  02/17/12  GR  TOS2.1 : Updt Field unitFlexString07 to UfvFlexString07
* A6  9/25/12   LC  Added check for booking temp = null, set to AMB
* A7  9/28/12   LC  Fix AMB ingate temp converting to F
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.api.GroovyApi;
import com.navis.inventory.business.units.ReeferRecord
import com.navis.framework.util.DateUtil;
import com.navis.argo.ContextHelper;

import java.util.Set
import java.text.DateFormat;
import java.text.SimpleDateFormat;

public class GvyUnitInGateTempCheck{

    public static String emailfrom = "1aktosdevteam@matson.com"
    public static String emailTo = "1aktosdevteam@matson.com"
    public static String subject = "Booked Temp. Discrepancy"
    public static String IS_AMB = "AMB";

    public void doIt(Object u, Object event)
    {
        def gvyBaseClass = new GroovyInjectionBase()
        try
        {

            def unit = event.getEntity();
            def unitId = unit.getFieldValue("unitId");
            def bookingNumber = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr");
            println("Calling GvyUnitInGateTempCheck for :::" + unitId);
            boolean isIngateAmb = false;
            boolean isExpBookAmb = false;
            //Added check for SIT
            if(bookingNumber == null){
                return
            }

            def IngateSetPoint = getSetPointTmp(unit);
            //A7
            def ingateReqTemp;
            if(IngateSetPoint == null) {
                isIngateAmb = true;
                ingateReqTemp = IS_AMB;
            }  else {
                ingateReqTemp = getValue(IngateSetPoint);
            }

            def expGateBkgReqTemp = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqoiTempRequired");
            //A6
            def expBkgReqTemp;
            if(expGateBkgReqTemp == null) {
                isExpBookAmb = true;
                expBkgReqTemp = IS_AMB;
            } else {
                expBkgReqTemp = getValue(expGateBkgReqTemp);
            }
            def expBkgPort = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoDestination");

            //both booking and ingate temps are AMB
            if(isExpBookAmb && isIngateAmb) {
                return;
            }

            def ingateReqTempF;
            def expBkgReqTempF;
            if(isIngateAmb) {
                ingateReqTempF = ingateReqTemp;
            } else {
                ingateReqTempF = celsiusToFahrenheit(ingateReqTemp);
            }

            if(isExpBookAmb)  {
                expBkgReqTempF = expBkgReqTemp;
            } else {
                expBkgReqTempF = celsiusToFahrenheit(expBkgReqTemp);
            }
            println("ingateReqTempF :::: expBkgReqTempF ::" + ingateReqTempF  +"::"+ expBkgReqTempF);
            //first checks if booking OR ingate is AMB, then it'll send an email
            //then checks if both have valid temp, then compares the values, sends an email if different
            String body;
            def gvyEmailSender;
            GroovyApi gvyApi = new GroovyApi();
            if((isIngateAmb && !isExpBookAmb) || (!isIngateAmb && isExpBookAmb))   {
                gvyApi.logWarn("Sending email to notify booking temp discrepancy ");
                body = generateReport(unitId, bookingNumber, ingateReqTempF, expBkgReqTempF, expBkgPort, "Gate")
                gvyEmailSender = gvyBaseClass.getGroovyClassInstance("EmailSender");
                gvyEmailSender.custSendEmail(emailTo, subject, body)
            } else {
                int comparisonVal = ingateReqTemp.compareTo(expBkgReqTemp);
                if(comparisonVal != 0) {
                    gvyApi.logWarn("Sending email to notify booking temp discrepancy ");
                    body = generateReport(unitId, bookingNumber, ingateReqTempF, expBkgReqTempF, expBkgPort, "Gate")
                    gvyEmailSender = gvyBaseClass.getGroovyClassInstance("EmailSender");
                    gvyEmailSender.custSendEmail(emailTo, subject, body)
                }
            }
        }catch(Exception e){
            GroovyApi.log("Exception in GvyUnitInGateTempCheck(): " + e);
        }
    }

    //A1 - Temp Discrepancy for Import Units
    public void ImportCntrBkgDiscrepancy(Object event){
        def gvyBaseClass = new GroovyInjectionBase()
        try
        {
            def unit = event.getEntity();
            def unitId = unit.getFieldValue("unitId")
            def bookingNumber = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr");
            //Added check for SIT
            if(bookingNumber == null){
                return
            }
            // Impt unit temp Setting value
            def IngateSetPoint = unit.getFieldValue("unitActiveUfv.ufvFlexString07"); //A5
            def ingateReqTemp = IngateSetPoint != null && IngateSetPoint.trim().length() > 0 ? getValue(IngateSetPoint.replace('F','')) : 0.0
            ingateReqTemp = ingateReqTemp == null ? null : (ingateReqTemp != 0.0 ? fahrenheitToCelsius(ingateReqTemp) : 0.0)

            // Impt unit temp Req value
            def imptGateBkgReqTemp = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqoiTempRequired");
            def imptBkgReqTemp = imptGateBkgReqTemp == null ? null : (imptGateBkgReqTemp != null ? getValue(imptGateBkgReqTemp) : 0.0)

            def imptBkgPort = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqoDestination");

            //Check for Reefer Empty
            if(ingateReqTemp == 0.0 &&  imptBkgReqTemp == 0.0){
                return;
            }

            int comparisonVal = 0;
            if((ingateReqTemp == null && imptBkgReqTemp != null) || (imptBkgReqTemp == null && ingateReqTemp != null)){
                comparisonVal = -1
            }else{
                comparisonVal = ingateReqTemp.compareTo(imptBkgReqTemp);
            }

            GroovyApi gvyApi = new GroovyApi();
            if(comparisonVal != 0)
            {
                gvyApi.logWarn("Sending email to notify booking temp discrepancy ");
                def ingateReqTempF = ingateReqTemp != null ? celsiusToFahrenheit(ingateReqTemp) : "--"
                def imptBkgReqTempF = imptBkgReqTemp != null ? celsiusToFahrenheit(imptBkgReqTemp) : "--"
                String body = generateReport(unitId, bookingNumber, ingateReqTempF, imptBkgReqTempF, imptBkgPort, "Review For Stow")
                def gvyEmailSender = gvyBaseClass.getGroovyClassInstance("EmailSender");
                gvyEmailSender.custSendEmail(emailTo, subject, body)
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }


    public String getTempDecimalValue(Object tempValue)
    {
        def reqTemp = ''+tempValue
        try
        {
            if(reqTemp!= null && reqTemp.trim().length() > 5)
            {
                def indx = reqTemp.indexOf(".");
                reqTemp = reqTemp.substring(0,indx+3);
            }

        }catch(Exception e){
            e.printStackTrace()
            throw e
        }
        return reqTemp
    }

    public Object getValue(Object tempvalue)
    {
        if(tempvalue != null)
        {
            double setPoint = new BigDecimal(""+tempvalue).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
            Double setPointTemp = new Double(setPoint)
            return setPointTemp
        }
        return null
    }

    public Object getSetPointTmp(Object u)
    {
        def setPoint = null
        try
        {
            ReeferRecord latestRecord = null;
            Set reefRecordSet= u.getUnitReeferRecordSet()
            if (reefRecordSet != null && reefRecordSet.size() > 0) {
                Object [] reefRecords  = reefRecordSet.toArray();
                Arrays.sort(reefRecords);
                latestRecord = (ReeferRecord)reefRecords[reefRecords.length - 1];
                setPoint = latestRecord.getRfrecSetPointTmp()
            }
        }catch(Exception e){
            e.printStackTrace()
            throw e
        }
        return setPoint
    }

    public double fahrenheitToCelsius(Double fahrenheit)
    {
        double fahr = (fahrenheit - 32) * 5/9;
        double result = new BigDecimal(""+fahr).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
        return result;
    }

    public double celsiusToFahrenheit(Double celsius)
    {
        double fahr = (celsius * 9/5) + 32;
        double result = new BigDecimal(""+fahr).setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
        return result;
    }

    public String generateReport(String unitId, String bookingNumber, Object ingateReqTemp, Object expBkgReqTemp, String expBkgPort, String noticeFrom) {
        def timezone = ContextHelper.getThreadUserTimezone();
        def eventTime = DateUtil.convertDateToLocalTime(new Date(), timezone);
        StringBuffer header = new StringBuffer();
        header.append("    Date:"+eventTime);
        header.append("\n\n\n                       MATSON TERMINALS - CONTAINER YARD     ");
        header.append("\n\n\n    From    :"+noticeFrom);
        header.append("\n    Subject : Booked Temperature Discrepancy");
        header.append("\n\n\n    Container:"+unitId+"   Booking # : "+bookingNumber+"   Gate Temp: "+ingateReqTemp+"   Book Temp: "+expBkgReqTemp+"   Port: "+expBkgPort);
        header.append("\n\n\n\n");
        return header.toString();
    }


}