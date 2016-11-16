import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.framework.configuration.calendar.AppCalendarUtil;
import com.navis.framework.configuration.calendar.AppCalendarEventType;
import com.navis.argo.ContextHelper;


public class TestGvyPluginCal
{

    private static AppCalendarEventType[] exemptTypes = null;

    static {
        exemptTypes = new AppCalendarEventType[2];
        exemptTypes[0] = AppCalendarEventType.findOrCreateAppCalendarEventType("EXEMPT_DAY");
        exemptTypes[1] = AppCalendarEventType.findOrCreateAppCalendarEventType("GRATIS_DAY");
    }

    public String  testCalender()
    {
        try{
            def exemptCalendarEvents = com.navis.framework.configuration.calendar.AppCalendarUtil.getEvents(exemptTypes,ContextHelper.getThreadUserContext())

            java.text.DateFormat df = new java.text.SimpleDateFormat("MM/dd/yyyy hh:mm:ss");
            Date startDate = df.parse("12/11/2009 02:00:00");

            def zone = com.navis.argo.ContextHelper.getThreadUserTimezone()

            def addDays = 10

            println("startDate="+startDate+"  zone="+zone+"  addDays="+addDays)
            def daysOutput = com.navis.framework.configuration.calendar.AppCalendarUtil.getEndDate(startDate, zone, addDays , exemptCalendarEvents, exemptTypes);

            println(" daysOutput="+daysOutput);
            return daysOutput;
        }catch(Exception e){
            e.printStackTrace();
        }

    }


}//Class Ends



