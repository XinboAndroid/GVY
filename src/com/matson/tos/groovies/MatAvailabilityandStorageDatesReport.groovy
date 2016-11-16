import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.CalendarTypeEnum
import com.navis.argo.business.calendar.ArgoCalendar
import com.navis.argo.business.calendar.ArgoCalendarEventType
import com.navis.argo.business.calendar.ArgoCalendarUtil
import com.navis.services.business.event.GroovyEvent
import net.sf.jasperreports.engine.JRDataSource
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource
import org.apache.log4j.Logger

import java.text.SimpleDateFormat
/*

*/

public class MatAvailabilityandStorageDatesReport extends GroovyApi{


    public void execute (GroovyEvent event, Object api){

        def visit = event.getEntity();
        def facility = visit.getFieldValue("cvdCv.cvFacility.fcyId")
        def cls=visit.getCarrierVesselClassType().getKey();
        def nxtFcy=visit.getFieldValue("cvdCv.cvNextFacility.fcyId")
        def ves=visit.getFieldValue("cvdCv.cvId")
        Date timeATA = visit.getCvdCv().getCvATA();
        Date actualTimeOfDeparture = visit.getCvdCv().getCvATD();
        Date timeETA = visit.getCvdCv().getCvCvd().getCvdETA();
        Date estTimeOfDeparture = visit.getCvdCv().getCvCvd().getCvdETD();
        def beginDate = visit.getFieldValue("cvdCv.cvCvd.cvdTimeFirstAvailability");
        TimeZone zone = ContextHelper.getThreadFacility().getTimeZone();



        LOGGER.warn ("Facility: " + facility);
        LOGGER.warn ("Class: " + cls);
        LOGGER.warn ("Next Facility: " + nxtFcy);
        LOGGER.warn ("Vessel: " + ves);
        LOGGER.warn ("TimeATA: " + timeATA);
        LOGGER.warn ("TimeATD: " + actualTimeOfDeparture);
        LOGGER.warn ("beginDate: " + beginDate);
        LOGGER.warn ("TimeETA: " + timeETA);
        LOGGER.warn ("TimeETD: " + estTimeOfDeparture);

        //Date availTwoDays = addDays(beginDate, 2);
        Date availTwoDays = addBusinessDate(beginDate, zone, 2);
        //Date availTwoDays = formatDate(availTwoDays1);


        LOGGER.warn ("availTwoDays: " + availTwoDays);

        Date availThreeDays = addBusinessDate(beginDate, zone, 3);
        Date availFourDays = addBusinessDate(beginDate, zone, 4);
        Date availFiveDays = addBusinessDate(beginDate, zone, 5);
        Date availSixDays = addBusinessDate(beginDate, zone, 6);
        Date availSevenDays = addBusinessDate(beginDate, zone, 7);
        Date availEightDays = addDays(beginDate, 7);
        Date availTenDays = addDays(beginDate, 9);
        Date availTenBusiDays = addBusinessDate(beginDate, zone, 10);
        Date availFourteenDays = addDays(beginDate, 13);
        Date availFifteenDays = addDays(beginDate, 14);
        Date availFifteenBusiDays = addBusinessDate(beginDate, zone, 15);
        Date availThirtyDays = addBusinessDate(beginDate, zone, 30);

        LOGGER.warn ("availTwoDays: " + availTwoDays);

        //Set Report Parameter

        LOGGER.warn ("Map setup: ");
        def inj = new GroovyInjectionBase();
        HashMap parameters = new HashMap();
        //String strDate = ContextHelper.formatTimestamp(event.getEvent().getEventTime(), timezone)
        //parameters.put("Date",strDate);
        def emailTo = "1aktosdevteam@matson.com";
        LOGGER.warn ("Email setup: ");
        //Create and Mail Report
        //JRDataSource ds = new JRMapCollectionDataSource(unitRptList);
        //def reportRunner = inj.getGroovyClassInstance("ReportRunner");
        def reportDesignName = "AVAILABILITY AND STORAGE";
        def displayType = null;
        ArrayList rptList = new ArrayList();

        parameters.put("vesVoy", ves);
        parameters.put("destination", facility);
        parameters.put("depDate1", estTimeOfDeparture);

        parameters.put("arrivalDate", timeETA);
        parameters.put("availableDate", beginDate);

        parameters.put("twoDays", availTwoDays);
        parameters.put("threeDays", availThreeDays);
        parameters.put("fourDays", availFourDays);
        parameters.put("fiveDays", availFiveDays);
        parameters.put("sixDays", availSixDays);
        parameters.put("sevenDays", availSevenDays);

        parameters.put("eightCalenderDays", availEightDays);
        parameters.put("tenCalenderDays", availTenDays);
        parameters.put("tenDays", availTenBusiDays);
        parameters.put("fourteenCalenderDays", availFourteenDays);
        parameters.put("fifteenCalenderDays", availFifteenDays);
        parameters.put("fifteenDays", availFifteenBusiDays);
        parameters.put("thirtyDays", availThirtyDays);
        HashMap dummyMap = new HashMap();
        dummyMap.put("UnitId", "MATU000000");
        rptList.add(dummyMap);
        LOGGER.warn ("Mail format: ");

        //Create and Mail Report
        JRDataSource ds = new JRMapCollectionDataSource(rptList);

        LOGGER.warn ("send mail: ");
        LOGGER.warn ("parameters: " + parameters);
        def reportRun = inj.getGroovyClassInstance("ReportRunner");
        reportRun.emailReport(ds,parameters, reportDesignName,emailTo, "Availability and Storage Dates : " + ves  ,"Attached report for AVSD");

    }

    public Date addBusinessDate(Date startDate, TimeZone zone, int addDays) {
        int altdays = addDays; //A5
        ArgoCalendarEventType[] exemptTypes = null;
        exemptTypes = new ArgoCalendarEventType[1];
        exemptTypes[0] = ArgoCalendarEventType.findOrCreateArgoCalendarEventType("EXEMPT_DAY");
        ///exemptTypes[1] = ArgoCalendarEventType.findOrCreateArgoCalendarEventType("GRATIS_DAY");

        //println("addBusinessDate : altdays="+altdays+"   addDays="+addDays)
        //def exemptCalendarEvents = AppCalendarUtil.getEvents(exemptTypes, ContextHelper.getThreadUserContext());
        CalendarTypeEnum calendarTypeEnum = CalendarTypeEnum.getEnum("STORAGE");
        ArgoCalendar argoCal = ArgoCalendar.findDefaultCalendar(calendarTypeEnum);
        def exemptCalendarEvents = ArgoCalendarUtil.getEvents(exemptTypes, argoCal);
        Date endDate = ArgoCalendarUtil.getEndDate(startDate, zone, altdays, exemptCalendarEvents, exemptTypes);
        if (endDate != null) {
            Calendar calendar = Calendar.getInstance(zone);
            calendar.setTimeInMillis(endDate.getTime());
            calendar.add(Calendar.DAY_OF_YEAR, -1);
            endDate = calendar.getTime();
        }
        return endDate;
    }

    public static Date addDays(Date baseDate, int daysToAdd) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(baseDate);
        calendar.add(Calendar.DAY_OF_YEAR, daysToAdd);
        return calendar.getTime();
    }

    public Date formatDate(Date inDate) {
        LOGGER.warn ("in formatDate: ");
        SimpleDateFormat dateFormat = new SimpleDateFormat("MM/DD/YYYY");
        LOGGER.warn ("return formatDate: ");
        Date formattedDate = dateFormat.format(inDate);
        return formattedDate;
    }

    private static final Logger LOGGER = Logger.getLogger(MatAvailabilityandStorageDatesReport.class);
}