import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EquipRfrTypeEnum
import com.navis.argo.business.reference.Container
import com.navis.framework.metafields.Metafield
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.util.internationalization.ITranslationContext
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.internationalization.TranslationUtils
import com.navis.framework.util.message.MessageLevel
import com.navis.framework.util.unit.TemperatureUnit
import com.navis.framework.util.unit.UnitUtils
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.argo.business.api.GroovyApi
import com.navis.inventory.business.units.Unit;
import com.navis.framework.util.DateUtil;

import com.navis.services.business.event.Event;
import com.navis.services.business.rules.EventType;
import com.navis.services.business.api.EventManager;
import com.navis.services.business.event.EventFieldChange;
import com.navis.framework.portal.FieldChanges


/*
* Author : Raghu Iyer
* Date Written : 11/04/2013
* Description: This groovy is used to generate alert email for temperature discrepancy while UNIT_DRAY_IN (Ingate)
*/

public class MatReportTempDiscrepancy extends GroovyInjectionBase
{

    public static String EMAIL_FROM = "1aktosdevteam@matson.com"
    public static String EMAIL_TO = "1aktosdevteam@matson.com"
    //public static String EMAIL_TO = "1aktosdevteam@matson.com"
    public static String SUBJECT = "Dray-In Temp. Discrepancy"

    private String outBoundCarrierId = null
    def inj = new GroovyInjectionBase();

    public void getEventChanges(Object event)
    {
        try
        {
            println("Calling getEventChangesTest");
            def gvyBaseClass = new GroovyInjectionBase()
            Set set = event.getEvent().getEvntFieldChanges();
            Iterator iter = set.iterator();
            EventFieldChange efc;
            while ( iter.hasNext()) {
                efc = (EventFieldChange)iter.next();
                println("get feild change Value :"+efc.getMetafieldId() + "-->"+ efc.getPrevVal() + "-->"+ efc.getNewVal())
                if ("rfreqTempRequiredC".equalsIgnoreCase(efc.getMetafieldId()))
                {
                    def unit = event.entity;
                    println("<<<<unit>>>>>"+unit.unitId);

                    String ctrNbr = unit.unitId;
                    String blNbr = unit.getFieldValue("unitGoods.gdsBlNbr");
                    String dest = unit.getFieldValue("unitGoods.gdsDestination");

                    Double prevReeferTempC = Double.parseDouble(efc.getPrevVal());
                    Double newReeferTempC = Double.parseDouble(efc.getNewVal());
                    Double prevReeferTempF = celsiusToFahrenheit(prevReeferTempC);
                    println("<<<prevReeferTempF>>>>>"+prevReeferTempF);
                    Double newReeferTempF = celsiusToFahrenheit(newReeferTempC);
                    println("<<<newReeferTempF>>>>>"+newReeferTempF);

                    if (newReeferTempF != prevReeferTempF){
                        String body = generateReport(ctrNbr, blNbr, newReeferTempF, prevReeferTempF, dest, "Gate");
                        def gvyEmailSender = gvyBaseClass.getGroovyClassInstance("EmailSender");
                        gvyEmailSender.custSendEmail(EMAIL_TO, SUBJECT, body)
                    }
                    else {
                        println("No discrepancy in the SIT and GATE Temperature for Dray-In Unit :::"+ctrNbr +" SIT Temp ::"+prevReeferTempF +" Gate Temp ::"+newReeferTempF);
                    }
                }
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public double celsiusToFahrenheit(Double celsius)
    {
        double fahr = (celsius * 9/5) + 32;
        double result = new BigDecimal(""+fahr).setScale(1, BigDecimal.ROUND_HALF_UP).doubleValue();
        return result;
    }

    public String generateReport(String ctrNbr, String blNbr, Double gateTemp, Double sitTemp, String dest, String noticeFrom)
    {
        def timezone = ContextHelper.getThreadUserTimezone();
        def eventTime = DateUtil.convertDateToLocalTime(new Date(), timezone);
        StringBuffer header = new StringBuffer();
        header.append("    Date:"+eventTime);
        header.append("\n\n\n                       MATSON TERMINALS - CONTAINER YARD     ");
        header.append("\n\n\n    From    : "+noticeFrom);
        header.append("\n    Subject : Dray-In Temperature Discrepancy");
        header.append("\n\n\n    Container: "+ctrNbr+"   Booking # : "+blNbr+"   Gate Temp: "+Double.toString(gateTemp)+"   SIT Temp: "+Double.toString(sitTemp)+"   Port: "+dest);
        header.append("\n\n\n\n");

        return header.toString();
    }
}