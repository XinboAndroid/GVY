import com.navis.framework.persistence.*;
import com.navis.framework.business.Roastery;
import com.navis.services.business.api.EventManager;
import com.navis.services.business.event.Event;
import com.navis.services.business.rules.EventType;
import com.navis.argo.business.atoms.EventEnum;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.services.business.event.EventFieldChange;
import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum;
import com.navis.argo.business.model.CarrierVisit;
import com.navis.vessel.business.schedule.VesselVisitDetails
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import com.navis.argo.business.reports.DigitalAsset;
import com.navis.argo.ContextHelper
import com.navis.services.business.event.GroovyEvent
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.Calendar;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import com.navis.framework.util.scope.ScopeCoordinates;
import com.navis.framework.portal.context.UserContextUtils;
import com.navis.framework.portal.UserContext;

public class CVSReportTest extends GroovyInjectionBase
{
    private Date startDateTimeHst = null;
    private Date endDateTimeHst = null;
    GroovyInjectionBase inj = null
    def gvyRptEventUtil = null
    // private final String emailTo = "1aktosdevteam@matson.com";

    //private final String emailTo = "jtattershall@matson.com;1CVSHO_HON@matson.com;atokairin@matson.com;jchagami@matson.com;1TOSDevTeamHON@matson.com;sysreports@matson.com";

    private final String emailTo = "riyer@matson.com";
    HashSet deptVesSet = new HashSet();
    HashMap workingVesMap = new HashMap();

    public void setDeptVessel(String deptVessel){
        deptVesSet.add(deptVessel)
    }

    public void setWorkingVessel(String VesId, String VesValue){
        workingVesMap.put(VesId,VesValue)
    }

    public String execute(Map inParameter)
    {
        try
        {
            inj = new GroovyInjectionBase();
            def userContext = com.navis.argo.ContextHelper.getThreadUserId()
            com.navis.argo.ContextHelper.setThreadExternalUser("admin");

            def gvyCmisUtil  = inj.getGroovyClassInstance("GvyCmisUtil")
            gvyRptEventUtil  = inj.getGroovyClassInstance("GvyReportEventUtil")

            getDateCriteria();
            EventType[] evntTypeArr =  getEventTypeArray();
            EventManager em = (EventManager)Roastery.getBean("eventManager");
            List eventList = gvyRptEventUtil.getEventsByCreatedDate(startDateTimeHst,endDateTimeHst,evntTypeArr)
            println("eventList ::"+(eventList != null ? eventList.size() : 0))
            ArrayList listUnits = new ArrayList()

            for(aEvent in eventList)
            {
                Date evntDate = aEvent.getEvntCreated()
                String eventId = aEvent.getEvntEventType().getEvnttypeId();
                String unitNbr = aEvent.getEvntAppliedToNaturalKey()

                Set changes = aEvent.getFieldChanges()
                Iterator iterator = changes.iterator();
                String updtFieldValue = ""
                String vesselVisitId = ""

                while(iterator.hasNext())
                {
                    EventFieldChange  fieldChange = (EventFieldChange)iterator.next();
                    String fieldName = fieldChange.getMetafieldId()
                    MetafieldId mfId = MetafieldIdFactory.valueOf(fieldName);
                    if(eventId.equals("UNIT_DISCH")){
                        updtFieldValue = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcPrevVal());
                    }else if(eventId.equals("UNIT_LOAD")){
                        updtFieldValue = ArgoUtils.getPropertyValueAsUiString(mfId, fieldChange.getEvntfcNewVal());
                    }

                    vesselVisitId = getVesselId(updtFieldValue, gvyCmisUtil)
                    //Gets WorK Shift & Rearranges Dates
                    evntDate = getFirstPeriodWorkDate(evntDate)
                    evntDate = gvyRptEventUtil.formatDateToTimeZone(evntDate, "HST")

                    //Checks If Vessel Entry should be included in the Report
                    if(vesselVisitId == null){
                        continue;
                    }

                    HashMap unitData = populateCSVData(vesselVisitId,evntDate,unitNbr)
                    listUnits.add(unitData)

                }//While Ends
            }//For Ends

            //Sorting By Field
            def gvyRptUtil = inj.getGroovyClassInstance("ReportFieldSortUtil")
            if(listUnits != null && listUnits.size() > 0){
                //Sort By Vesvoy && EventCreated Time
                listUnits =  gvyRptUtil.processFieldSort(listUnits,"vesselId,createdDate")
            }

            //Create IReport Design
            processReportDesign(listUnits)

            println("deptVesSet for CVS Hourly Status Report :"+deptVesSet+" WorkingVesSet :"+workingVesMap)

        }catch(Exception e){
            e.printStackTrace();
        }

    }//Method Ends


    public EventType[] getEventTypeArray()
    {
        EventType[] evntTypeArr = null;
        try
        {
            EventType evntDisch = EventType.resolveIEventType(EventEnum.UNIT_DISCH);
            EventType evntLoad = EventType.resolveIEventType(EventEnum.UNIT_LOAD);
            evntTypeArr = [evntDisch,evntLoad]
        }catch(Exception e){
            e.printStackTrace();
        }
        return evntTypeArr
    }//Method Ends



    public HashMap populateCSVData(String vesselId,Date createdDate,String unitNbr)
    {
        HashMap map = new HashMap()
        try
        {
            map.put("vesselId", vesselId);
            map.put("createdDate", createdDate);
            map.put("unitNbr", unitNbr);

        }catch(Exception e){
            e.printStackTrace()
        }
        return map
    }


    public String getVesselId(String updtFieldValue, Object gvyCmisUtil)
    {
        String vesselId = null;
        try{
            String [] vesselPosition = updtFieldValue != null ? updtFieldValue.split("-") : ""
            vesselId = vesselPosition.length > 0 ? vesselPosition[1] : ""

            //Check Vessel Id from Buffered Id Values
            if(deptVesSet != null && deptVesSet.contains(vesselId)){
                return null;
            }else if(workingVesMap.get(vesselId) != null){
                //  println("workingVesMap.get(vesselId) ::"+workingVesMap.get(vesselId))
                return workingVesMap.get(vesselId)
            }/*else{
         def entry = null;
         workingVesMap.each
         {
            println("it.key ---::"+it.key+"   it.key.contains(vesselId) ::"+it.key.contains(vesselId))
            String aVesKey = it.key
            if(aVesKey.contains(vesselId)){
              entry = it.value;
              return entry
            }
          }
      }*/

            //Get Vess
            String vesClassType = gvyCmisUtil.getVesselClassType(vesselId)

            //Gets CarrierVisit
            def cv = CarrierVisit.findVesselVisit( getFacility(), vesselId);
            if(cv == null){
                return vesselId
            }

            VesselVisitDetails vvd = VesselVisitDetails.resolveVvdFromCv(cv);
            String vvdIbVygNbr =  vvd.vvdIbVygNbr
            String vvdObVygNbr =  vvd.vvdObVygNbr
            Date actualTimeofDept = cv.cvATD
            //println("actTimeofDept ------::"+actualTimeofDept)

            CarrierVisitPhaseEnum visitPhase = cv.getCvVisitPhase();

            if ( (CarrierVisitPhaseEnum.DEPARTED.equals(visitPhase) || CarrierVisitPhaseEnum.CLOSED.equals(visitPhase)
                    || CarrierVisitPhaseEnum.ARCHIVED.equals(visitPhase) || CarrierVisitPhaseEnum.WORKING.equals(visitPhase))
                    && actualTimeofDept != null &&  gvyRptEventUtil.timeDiffInHrs(endDateTimeHst, actualTimeofDept) > 12 )
            {
                setDeptVessel(vesselId); //Set VesselId to Departed Buffer
                return null;
            }else if (CarrierVisitPhaseEnum.INBOUND.equals(visitPhase) || CarrierVisitPhaseEnum.CANCELED.equals(visitPhase) ){
                return null;
            }

            if(vesClassType.equals("CELL")){
                setWorkingVessel(vesselId,vesselId)
            }
            if(vesClassType.equals("BARGE"))
            {
                // println("vvdIbVygNbr --::"+vvdIbVygNbr+" vvdObVygNbr --::"+vvdObVygNbr+" visitPhase --::"+visitPhase+" actualTimeofDept --::"+actualTimeofDept)
                def bargeVesId = vesselId
                def vesselCode = vesselId != null && vesselId.length() >3 ? vesselId.substring(0,3) : null
                vesselId = vesselCode+vvdIbVygNbr+"/"+vvdObVygNbr
                def bargeVesValue = vesselId

                //Set Working vessel
                setWorkingVessel(bargeVesId,bargeVesValue)
            }

        }catch(Exception e){
            e.printStackTrace()
        }
        return vesselId
    }

    //Get Report Search Date Criteria
    public void getDateCriteria()
    {
        try
        {
            Calendar calendarHst = Calendar.getInstance();
            endDateTimeHst = calendarHst.getTime() //formatDateToTimeZone(, "HST");

            calendarHst.add(Calendar.DATE, -3);
            startDateTimeHst = calendarHst.getTime() //formatDateToTimeZone(, "HST");

            println("startDateTimeHst::" +startDateTimeHst+"   endDateTimeHst::"+endDateTimeHst);
        }catch(Exception e){
            e.printStackTrace()
        }
    }//Method Ends


    //Method Substracts Date to a Day Earlier on the Morning Shift 0hrs to 6hrs
    public Date getFirstPeriodWorkDate(Date evntDate)
    {
        // println(" getFirstPeriodWorkDate evntDate --- ::"+evntDate)

        Date shiftDate = evntDate;
        try
        {
            Calendar cal=Calendar.getInstance();
            cal.setTimeZone(TimeZone.getTimeZone("HST"));
            cal.setTime(evntDate);
            if(cal.get(cal.HOUR_OF_DAY) >= 0 && cal.get(cal.HOUR_OF_DAY) < 7){
                cal.add(Calendar.DATE, -1);
                shiftDate = cal.getTime()
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return shiftDate
    }


    public void processReportDesign(ArrayList unitList)
    {
        println("unitList Count="+unitList.size())
        try{
            //Set Report Parameter
            HashMap parameters = new HashMap();
            String strDate = ContextHelper.formatTimestamp(new Date(), ContextHelper.getThreadUserTimezone())

            println("Report Time ::"+new Date()+"    TimeZone:"+ContextHelper.getThreadUserTimezone()+"  strDate ::"+strDate)

            parameters.put("Date",strDate);
            // parameters.put("MATSONLOGO",DigitalAsset.findImage("MATSONLOGO"));

            //Create and Mail Report
            JRDataSource ds = new JRMapCollectionDataSource(unitList);

            def reportRunner = inj.getGroovyClassInstance("ReportRunner");
            println("ds="+ds+" parameters="+parameters+" emailTo="+emailTo)
            reportRunner.emailReport(ds, parameters, "CVS_HOURLY_STATUS", emailTo, "CVS Hourly Status Report", "CVS Hourly Status Report");

        }catch(Exception e){
            e.printStackTrace();
        }
    }

}//Class Ends