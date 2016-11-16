import com.navis.argo.ContextHelper;
import com.navis.argo.business.api.GroovyApi;
import com.navis.framework.util.DateUtil;
import com.navis.inventory.business.units.Unit;
import java.math.BigDecimal;

/**
 * Created with IntelliJ IDEA.
 * User: lcrouch
 * Date: 9/3/13
 * Time: 10:10 AM
 * Description: Email notification for Review for Stow Temp Discrepancy
 */
public class ReviewForStowTempDiscrepancy extends GroovyApi{

    public static String EMAIL_FROM = "1aktosdevteam@matson.com";
    public static String EMAIL_TO = "1aktosdevteam@matson.com";
    public static String SUBJECT = "Review For Stow Temp. Discrepancy";

    public void execute(Unit unit, Object event) {
        Double tempRequiredC;
        double tempRequiredF;

        String tempSettingStr;
        Double tempSettingC;
        double tempSettingF;


        try {

            String ctrNbr = unit.getUnitId();
            String dest = unit.getFieldValue("unitGoods.gdsDestination");
            String blNbr = unit.getFieldValue("unitGoods.gdsBlNbr");

            if (unit != null && unit.getUnitGoods() != null && unit.getUnitGoods().getGdsReeferRqmnts() != null) {
                tempRequiredC = unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempRequiredC();
                tempRequiredF = celsiusToFahrenheit(tempRequiredC);
            }


            tempSettingStr = unit.getFieldValue("unitActiveUfv.ufvFlexString07");
            tempSettingF = tempSettingStr != null && tempSettingStr.trim().length() > 0 ? getValue(tempSettingStr.replace('F','')) : 0.0;
            tempSettingC = tempSettingF == null ? null : (tempSettingF != 0.0 ? fahrenheitToCelsius(tempSettingF) : 0.0)

            println("ReviewForStowTempDiscrepancy:: tempRequiredF:: "+tempRequiredF);
            println("ReviewForStowTempDiscrepancy:: tempSettingF:: "+tempSettingF);

            if (tempRequiredC != null && tempSettingC != null && (tempRequiredC.compareTo(tempSettingC)!= 0)) {
                println("ReviewForStowTempDiscrepancy:: Sending email to notify discrepancy in temperature");
                String tempReq = tempRequiredF != null ? tempRequiredF : "--";
                String tempSet = tempSettingF != null ? tempSettingF : "--";
                String body = generateReport(ctrNbr, blNbr, tempReq, tempSet, dest, "Review For Stow");
                def gvyEmailSender = getGroovyClassInstance("EmailSender");
                gvyEmailSender.custSendEmail(EMAIL_TO, SUBJECT, body);
            }



        } catch (Exception e) {
            log("Exception in ReviewForStowTempDiscrepancy(): " + e);
        }
    }

    public Object getValue(Object tempvalue)
    {
        if(tempvalue != null)
        {
            double setDecimalPoint = new BigDecimal(""+tempvalue).setScale(2, BigDecimal.ROUND_HALF_UP).doubleValue();
            Double setDecimalPointTemp = new Double(setDecimalPoint);
            return setDecimalPointTemp;
        }
        return null;
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

    public String generateReport(String ctrNbr, String blNbr, Object tempReq, Object tempSet, String dest, String noticeFrom) {
        def timezone = ContextHelper.getThreadUserTimezone();
        def eventTime = DateUtil.convertDateToLocalTime(new Date(), timezone);
        StringBuffer header = new StringBuffer();
        header.append("    Date:"+eventTime);
        header.append("\n\n\n                       MATSON TERMINALS - CONTAINER YARD     ");
        header.append("\n\n\n    From    : "+noticeFrom);
        header.append("\n    Subject : Review For Stow Temperature Discrepancy");
        header.append("\n\n\n    Container: "+ctrNbr+"   Booking # : "+blNbr+"   Temp Required: "+tempReq+"   Temp Setting: "+tempSet+"   Port: "+dest);
        header.append("\n\n\n\n");
        return header.toString();
    }
}
