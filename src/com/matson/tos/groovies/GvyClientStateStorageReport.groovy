import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.CalendarTypeEnum
import com.navis.argo.business.atoms.EquipRfrTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.calendar.ArgoCalendar
import com.navis.argo.business.calendar.ArgoCalendarEventType
import com.navis.argo.business.calendar.ArgoCalendarUtil
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.LineOperator
import com.navis.argo.business.reports.DigitalAsset
import com.navis.framework.email.DefaultAttachment
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.vessel.VesselField
import net.sf.jasperreports.engine.JRDataSource
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource
import org.apache.commons.lang.time.DateUtils
import org.springframework.core.io.ByteArrayResource

import java.text.NumberFormat
import java.text.ParseException
import java.text.SimpleDateFormat

/**
 * Created with IntelliJ IDEA.
 * User: lcrouch
 * Date: 4/26/13
 * Time: 12:26 PM
 * To change this template use File | Settings | File Templates.
 */
public class GvyClientStateStorageReport extends GroovyInjectionBase {
    GroovyApi gvyBaseClass = new GroovyApi();
    private static ArgoCalendarEventType[] exemptTypes = null;
    ArrayList<StorageRate> StgRateTable = new ArrayList<StorageRate>();
    private final String  emailFrom = '1aktosdevteam@matson.com'
    private final String emailTo = "1aktosdevteam@matson.com";
    //private final String emailTo = "riyer@matson.com";
    def inj = null;
    private final String designName = "CLIENT STATE STORAGE"

    static {
        exemptTypes = new ArgoCalendarEventType[2];
        exemptTypes[0] = ArgoCalendarEventType.findOrCreateArgoCalendarEventType("EXEMPT_DAY");
        exemptTypes[1] = ArgoCalendarEventType.findOrCreateArgoCalendarEventType("GRATIS_DAY");
    }



    public void execute(Map inParameters) {

        println("Client State Storage Report::starting...");

        StgRateTable = readStateStorageMediaAsset();
        ArrayList<StorageReport> ClientList = findAllClientUnits();

        //for (StorageRate rate : StgRateTable) {
        //System.out.println(rate.getFormattedDate(rate.getStartDate()) + " " + rate.getStgPort() + " " + Integer.toString(rate.getLowRange()) + " " + Integer.toString(rate.getHighRange()) + " " + rate.getFormattedRate() + " " + rate.getCategory());
        //}

        println("Client State Storage Report::ending...");
    }

    /*
    * Method finds all NON-MAT Container and Add the cntrs to a list
    * Returns a List of containers
    */
    public ArrayList findAllClientUnits()
    {
        ArrayList<StorageReport> clientReport = new ArrayList<StorageReport>();
        NumberFormat currency = NumberFormat.getCurrencyInstance();
        ArrayList reportUnitList =  new ArrayList();
        ArrayList<String> lineOperatorList= new ArrayList<String>();
        inj = new GroovyInjectionBase();
        ArrayList<DefaultAttachment> reportsAttachment = null;
        def messageText = "No Containers for Client State Storage report";

        try{

            //Gets today's date and calculates the weekending period
            String date_start;
            String date_end;
            //SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");
            SimpleDateFormat dt = new SimpleDateFormat("yyyy-MM-dd");
            SimpleDateFormat sdf = new SimpleDateFormat("MM-dd-yyyy");
            SimpleDateFormat simpleDateFormat2 = new SimpleDateFormat("EEE");

            Date today = new Date();
            String dateNow = simpleDateFormat2.format(today.getTime());
            System.out.println("Now the date is :=>  " + dateNow);
            long endDay = 0;
            long startDay;
            ArrayList<String> DOW = new ArrayList<String>();
            DOW.add(0,"SUN");
            DOW.add(1,"MON");
            DOW.add(2,"TUE");
            DOW.add(3,"WED");
            DOW.add(4,"THU");
            DOW.add(5,"FRI");
            DOW.add(6,"SAT");
            for (String d: DOW)
            {
                if(d.toString().equalsIgnoreCase(dateNow)) {
                    System.out.println(DOW.indexOf(d) + " " + dateNow.toUpperCase());
                    endDay = getDayOfWeek(DOW.indexOf(d));
                }

            }
            startDay = endDay - ((24*6) * 60 * 60 * 1000);
            date_start = dt.format(startDay);
            date_end = dt.format(endDay);

            //***************REMOVE---FOR TESTING ONLY*********************************
            //date_start = "2014-02-21 00:00:00.0";
            //date_end = "2014-02-28 11:59:59.9";
            //***************REMOVE---FOR TESTING ONLY*********************************

            println("start period:"+dt.parse(date_start));
            println("end period:"+dt.parse(date_end));
            Date weekStarting = dt.parse(date_start);
            Date weekEnding = dt.parse(date_end);


            //start query for report
            Long lineOpGkey = LineOperator.findLineOperatorById("MAT").bzuGkey;
            ArrayList clientVessels = lookupVessel();

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_CATEGORY, UnitCategoryEnum.IMPORT));
            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S70_DEPARTED));
            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.DEPARTED));
            dq = dq.addDqPredicate(PredicateFactory.ge(UnitField.UFV_TIME_OUT, weekStarting));
            dq = dq.addDqPredicate(PredicateFactory.le(UnitField.UFV_TIME_OUT, weekEnding));
            //dq = dq.addDqPredicate(PredicateFactory.le(UnitField.UFV_UNIT_ID, "SUDU7986373"));
            dq = dq.addDqPredicate(PredicateFactory.ne(UnitField.UFV_LINE_OPERATOR_GKEY, lineOpGkey))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.THROUGH))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_LINE_OPERATOR_GKEY));  //A17
            println("dq ===="+dq);
            List vesVistUnits  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("dq ===="+dq+" unitsList ===="+(vesVistUnits != null ? vesVistUnits.size() : "NO RESULT"));

            //Gets all the unique lineOperators returned from the query and client vessel id
            if(vesVistUnits != null) {
                Iterator iter = vesVistUnits.iterator();
                while(iter.hasNext()) {
                    UnitFacilityVisit ufv = iter.next();
                    ufv.ufvUnit.getUnitId();
                    String lineOp = ufv.ufvUnit.getFieldValue("unitLineOperator.bzuId");
                    println("::::::::ufv.getUfvActualIbCv():::::::::"+ufv.getUfvActualIbCv().toString().length()+"::"+ufv.ufvUnit.getUnitId());
                    String vesselId = null;
                    if (ufv.getUfvActualIbCv().toString().length() >= 3){
                        vesselId = ufv.getUfvActualIbCv().toString().substring(0,3);
                    }
                    println("::::::::::::::here:::::::::::::::::::::::" + lineOp + "::"+vesselId +"::"+ufv.ufvUnit.getUnitId());
                    if (!lineOperatorList.contains(lineOp) && clientVessels.contains(vesselId))  {
                        lineOperatorList.add(lineOp);
                    }
                }
            }
            reportsAttachment = new ArrayList<DefaultAttachment>();
            if(lineOperatorList.size() > 0) {
                for(String op : lineOperatorList) {
                    println("START LINE OPERATOR: "+op);
                    if(vesVistUnits != null)  {
                        Iterator iter = vesVistUnits.iterator();

                        println("-----------------------------------------------------------------------------------------------------");
                        println("CtrNo.        VesVoy     AvailDate      FreeTimeEnding      OutGated    CtrSize   DaysStored   AmtDue")
                        println("------------------------------------------------------------------------------------------------------");

                        while(iter.hasNext()) {

                            UnitFacilityVisit ufv = iter.next();
                            String vesselId = null;
                            if (ufv.getUfvActualIbCv().toString().length() >= 3){
                                vesselId = ufv.getUfvActualIbCv().toString().substring(0,3);
                            }
                            if(clientVessels.contains(vesselId)) {
                                String unitId =  ufv.ufvUnit.getUnitId();
                                println("unitId::"+unitId);
                                HashMap map = null;

                                //gets the inbound vessel visit voyage
                                String carrierVisitId = ufv.getUfvActualIbCv().toString();
                                CarrierVisit vesselVisit = CarrierVisit.findVesselVisit(ContextHelper.getThreadFacility(), carrierVisitId);
                                String vesVoy = carrierVisitId.substring(0,3) + vesselVisit.getCarrierIbVoyNbrOrTrainId();
                                String lineOp = ufv.ufvUnit.getFieldValue("unitLineOperator.bzuId");
                                def freightKind = ufv.ufvUnit.getUnitFreightKind().getKey();
                                boolean isReefer = !EquipRfrTypeEnum.NON_RFR.equals(ufv.ufvUnit.getUnitPrimaryUe().getUeEquipment().getEqEquipType().getEqtypRfrType()) ? true : false;
                                Date availDate = ufv.ufvFlexDate02;
                                println("availDate::"+availDate);
                                if(!availDate.equals(null)){
                                    def zone = ufv.ufvUnit.getUnitComplex().getTimeZone();
                                    println("zone::"+zone);
                                    Date freeTimeEnding = calcStorageDate(freightKind, isReefer, availDate, zone);
                                    println("freeTimeEnding::"+freeTimeEnding);
                                    Date outgateDate = null;  //accounts for units that have not outgate yet
                                    if(!ufv.getUfvTimeEcOut().equals(null)){
                                        outgateDate = ufv.getUfvTimeEcOut();
                                    }
                                    println("outgateDate::"+outgateDate);
                                    if(isReefer && freeTimeEnding==null) {
                                        freeTimeEnding = outgateDate;
                                        println("Reefer changing freeTimeEnding::"+freeTimeEnding);
                                    }
                                    int containerSize = ufv.ufvUnit.getPrimaryEq().getEqEquipType().getEqtypLengthMm();
                                    containerSize = (int) Math.round((containerSize/25.4) / 12);

                                    //int daysOver = Math.round((outgateDate.getTime() - freeTimeEnding.getTime())/ (24 * 60 * 60 * 1000)) ;
                                    int timeDiff;
                                    if(outgateDate.equals(null) || freeTimeEnding.equals(null)) {
                                        timeDiff = 0;
                                    } else {
                                        timeDiff =  (int) (outgateDate.getTime() - freeTimeEnding.getTime());
                                    }

                                    int daysOver = 0;
                                    if(timeDiff > 0) {
                                        daysOver = (int) (timeDiff)/ (24 * 60 * 60 * 1000);
                                    }
                                    //check that there is storage detention and line operator is for the same report
                                    println("daysOver and op ::"+daysOver+"::"+op);
                                    if(daysOver != 0 && lineOp.equalsIgnoreCase(op)) {
                                        //calculates the free time ending date and stores it to the ufv

                                        map = new HashMap();


                                        //ufv.setFieldValue("ufvLastFreeDay", freeTimeEnding);
                                        StorageReport storageReport = stateStorageCalc(ufv,daysOver,freeTimeEnding,containerSize);

                                        storageReport.setContainerNo(unitId);
                                        storageReport.setVesVoy(vesVoy);
                                        storageReport.setAvailDate(availDate);
                                        storageReport.setFreeTimeEnding(freeTimeEnding);
                                        storageReport.setOutgateDate(outgateDate);
                                        storageReport.setContainerSize(Integer.toString(containerSize));
                                        storageReport.setDaysStored(daysOver);


                                        //CONTAINER STATE STORAGE
                                        map.put("weekEnding",sdf.format(weekEnding));
                                        map.put("stgType",storageReport.getStgType());
                                        map.put("category",storageReport.getCategory());
                                        map.put("LineOperator",lineOp);
                                        map.put("UnitNbr", storageReport.getContainerNo());
                                        map.put("InboundCarrierId", storageReport.getVesVoy());
                                        map.put("UnitFlexString01", storageReport.getFormattedDate(storageReport.getAvailDate()));
                                        map.put("UnitFlexString02", storageReport.getFormattedDate(storageReport.getFreeTimeEnding()));
                                        map.put("UnitFlexString03", storageReport.getFormattedDate(storageReport.getOutgateDate()));
                                        map.put("UnitFlexString04", storageReport.getContainerSize());
                                        map.put("UnitFlexString05", storageReport.getDaysStored());
                                        map.put("UnitFlexString06", storageReport.getAmountDue());

                                        if(map != null) {
                                            reportUnitList.add(map);
                                        }

                                        println("StgType: "+storageReport.getStgType());
                                        println("Category: "+storageReport.getCategory());
                                        println(storageReport.getContainerNo()+"   "+storageReport.getVesVoy()+"     "+storageReport.getFormattedDate(storageReport.getAvailDate())+
                                                "        "+storageReport.getFormattedDate(storageReport.getFreeTimeEnding())+"          "+storageReport.getFormattedDate(storageReport.getOutgateDate())+
                                                "    "+storageReport.getContainerSize()+"          "+storageReport.getDaysStored()+"        "+storageReport.getAmountDue());
                                        println("------------------------------------------------------------------------------------------------------");


                                    }
                                } else {
                                    println("AvailDate is null!");
                                }
                            }
                        }

                        //CONTAINER STAT STORAGE

                        if (reportUnitList.size() > 0) {
                            messageText = "Attached report(s) for Client State Storage";
                            HashMap parameters = new HashMap();

                            /* //Create and Mail Report
                             JRDataSource ds = new JRMapCollectionDataSource(reportUnitList);

                             //HashMap reportDesignsmap = new HashMap();
                             //reportDesignsmap.put("CLIENT STATE STORAGE",ds);

                             //def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                             //reportRunner.emailReports(reportDesignsmap,parameters, emailTo, "Client State Storage" ,"Attached report for Client State Storage");
                             println("reportUnitList ------- Success")*/

                            // Above code commented on 7/30/2013,
                            // Collect all the reports here for each line operator and send them in only one email at the end
                            JRDataSource dataSource = new JRMapCollectionDataSource(reportUnitList)
                            String attachFileName = op + "-" + designName
                            def reportRunner = inj.getGroovyClassInstance("ReportRunner")
                            DefaultAttachment attachment = reportRunner.generateReportAttachment(designName, dataSource, parameters, attachFileName)
                            if(attachment!=null)
                                reportsAttachment.add(attachment)
                            else
                                println("Attachment null")
                        }
                        else {
                            println("reportUnitList ------- No data to print")
                        }

                    }
                    //clear report list for next line operator
                    reportUnitList.clear();
                    println("END LINE OPERATOR: "+op);
                }
            }
            // Send all the report attachments

            inj.getGroovyClassInstance("ReportRunner").emailReportAttachments(reportsAttachment, emailFrom, emailTo, "Client State Storage Report", messageText)


        }catch(Exception e){
            e.printStackTrace();
        }
        return clientReport;
    }

    public static long getDayOfWeek(int d) {

        Date today = new Date();
        long day;

        switch (d) {
            case 0:
                day =  (today.getTime() - ((1*24) * 60 * 60 * 1000));
                break;
            case 1:
                day =  (today.getTime() - (((2*24) * 60 * 60 * 1000)));
                break;
            case 2:
                day =  (today.getTime() - (((24*3) * 60 * 60 * 1000)));
                break;
            case 3:
                day =  (today.getTime() - (((24*4) * 60 * 60 * 1000)));
                break;
            case 4:
                day = (today.getTime() - (((24*5) * 60 * 60 * 1000)));
                break;
            case 5:
                day = (today.getTime() - (((24*6) * 60 * 60 * 1000)));
                break;
            case 6:
                day =  (today.getTime());
                break;
            default:
                day = (today.getTime());
                break;

        }
        return day;

    }

    public StorageReport stateStorageCalc(UnitFacilityVisit ufv, int daysOver, Date freeTimeEnding, int containerSize) {
        String dport =  ufv.ufvUnit.getUnitRouting().getRtgPOD1().getPointId();
        String equiOperator = ufv.ufvUnit.getUnitLineOperator().getBzuId();
        double storage = 0.0;
        StorageReport storageReport = new StorageReport();


        Date calcDate = DateUtils.addDays(freeTimeEnding,1);

        int temp_count = 1;
        String stgPort;
        StorageRate clientRate = new StorageRate();

        if(dport.equalsIgnoreCase(ContextHelper.getThreadFacility().getFcyId())) {
            stgPort = dport;
        } else {
            stgPort = "NIS";
        }

        while(temp_count <= daysOver) {
            clientRate = getRateInfoState(calcDate, stgPort, temp_count);

            calcDate = DateUtils.addDays(calcDate,1);
            temp_count = temp_count + 1;
            storage = storage + (clientRate.getRate() * containerSize);
        }
        storageReport.setCategory(clientRate.getCategory());
        storageReport.setStgType(clientRate.getStgType());
        storageReport.setAmountDue(storage);



        return storageReport;



    }

    public StorageRate getRateInfoState(Date calcDate, String stgPort, int temp_count) {
        TreeMap<Date, String> map = new TreeMap<Date, String>();
        StorageRate clientRate = new StorageRate();
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yy");
        String calcDateStr = simpleDateFormat.format(calcDate);
        Date matchDate = new Date();
        //println("calcDateStr = " + calcDateStr);
        try {
            matchDate = simpleDateFormat.parse(calcDateStr);
        } catch (ParseException e) {
            e.printStackTrace();
        }

        for (StorageRate rate : StgRateTable) {
            //println("Formatted Start Date " + rate.getFormattedDate(rate.getStartDate()));
            map.put(rate.getStartDate(),rate.getFormattedDate(rate.getStartDate()));

        }

        String effDate = getClosestPrevious(map,matchDate);
        //System.out.println(getClosestPrevious(map,matchDate));

        for (StorageRate rate : StgRateTable) {
            if(rate.getFormattedDate(rate.getStartDate()).equals(effDate) && rate.getStgPort().equals(stgPort)){
                if(( rate.getLowRange() <= temp_count) && (temp_count <= rate.getHighRange())) {
                    clientRate.setRate(rate.getRate());
                    clientRate.setCategory(rate.getCategory());
                    clientRate.setStgPort(rate.getStgPort());
                    clientRate.setHighRange(rate.getHighRange());
                    clientRate.setLowRange(rate.getLowRange());
                    clientRate.setStgType(rate.getCategory());
                    /*
                    println("Start Date: " + clientRate.getFormattedDate(rate.getStartDate())
                    + "  Stg Port: " + clientRate.getStgPort()
                    + "  LowRange: " + clientRate.getLowRange()
                    + "  HighRange: " + clientRate.getHighRange()
                    + "  Rate: " + clientRate.getFormattedRate()
                    + "  Category: " + clientRate.getCategory()
                    + "  Stg Type: " + clientRate.getStgType());
                    */
                    break;
                }
            }
        }

        return clientRate;

    }

    private static String getClosestPrevious(TreeMap<Date, String> map, Date date) {
        return map.get(map.headMap(date, true).lastKey());
    }


    public java.util.Date calcStorageDate(String freightKind, boolean isReefer, java.util.Date availDate, TimeZone zone) {
        if(isReefer)
            return null;

        if(freightKind.equals("MTY"))
            return addBusinessDate(availDate, zone, 7);

        return addBusinessDate(availDate, zone, 5);
    }

    public Date addBusinessDate(Date startDate, TimeZone zone, int addDays) {
        int altdays = addDays; //A5
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


    public ArrayList readStateStorageMediaAsset()
    {

        try{
            ByteArrayResource byteResource  = new ByteArrayResource(DigitalAsset.findImage("STATE_STORAGE"));
            InputStream inputStream = byteResource.getInputStream();
            DataInputStream dStream = new DataInputStream(inputStream);
            BufferedReader br = new BufferedReader(new InputStreamReader(dStream));
            int count = 0;
            String line;

            while ((line = br.readLine()) != null) {
                if(count != 0) {
                    StgRateTable = processLine(line,count);
                }
                count++;
            }

        }catch(Exception e){
            e.printStackTrace();
        }
        return StgRateTable;
    }

    //process each line of code from the media asset file
    public ArrayList processLine(String msg, int lineNum)
    {

        try{
            String[] loadFile = msg.split(",");
            if(lineNum == 0) {

                String StartDate = loadFile[0];
                String StgPort = loadFile[1];
                String LowRange = loadFile[2];
                String HighRange = loadFile[3];
                String Rate = loadFile[4];
                String Category = loadFile[5];

            } else {

                String StartDate = loadFile[0];
                String StgPort = loadFile[1];
                String LowRange = loadFile[2];
                String HighRange = loadFile[3];
                String Rate = loadFile[4];
                String Category = loadFile[5];
                StorageRate stgRate = new StorageRate();

                stgRate.setStartDate(StartDate);
                stgRate.setStgPort(StgPort);
                stgRate.setLowRange(Integer.parseInt(LowRange));
                stgRate.setHighRange(Integer.parseInt(HighRange));
                stgRate.setRate(Double.parseDouble(Rate));
                stgRate.setCategory(Category);
                //println("Start Date::" + stgRate.getFormattedDate(stgRate.getStartDate()) + " StgPort::" + stgRate.getStgPort() + " LowRange::" + Integer.toString(stgRate.getLowRange()) + " HighRange::" + Integer.toString(stgRate.getHighRange()) + " StgRate::" + stgRate.getFormattedRate() + " Category::" + stgRate.getCategory());


                StgRateTable.add(stgRate);

            }

        }catch(Exception e){
            e.printStackTrace();
        }
        return StgRateTable;
    }

    public ArrayList lookupVessel() {
        ArrayList vessels = new ArrayList();

        try {
            //search for all vessels except for Matson (303726)
            DomainQuery dq = QueryUtils.createDomainQuery("Vessel");
            dq.addDqPredicate(PredicateFactory.ne(VesselField.VES_OWNER,"303726" ));
            def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

            if(list != null) {
                Iterator iter = list.iterator();
                while(iter.hasNext()) {
                    def item = iter.next();
                    //println("vessel id::"+item.getFieldValue("vesId"));
                    //println("vessel owner::"+item.getFieldValue("vesOwner"));
                    vessels.add(item.getFieldValue("vesId"));
                }
            }

        } catch (Exception e) {
            println("Exception in Vessel lookup  "+e.getMessage());
            e.printStackTrace();
        }

        return vessels;
    }


}

/*
StorageRate class to hold all the rate information
 */
class StorageRate {
    private Date startDate;
    private String stgPort;
    private int lowRange;
    private int highRange;
    private double rate;
    private String category;
    private String stgType;

    public StorageRate(){
        stgPort = "";
        stgType = "";
        lowRange = 0;
        highRange = 0;
        rate = 0;
        category = "";
    }

    public void setStartDate(String date) {
        Date startDate = setFormatDate(date);
        this.startDate =  startDate;

    }

    public Date setFormatDate(String date) {
        //println("Date " + date);
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yy");
        Date startDate;
        try {
            startDate = simpleDateFormat.parse(date);
            //println("Formatted " + startDate);
        } catch (ParseException e1) {
            e1.printStackTrace();
        }
        return startDate;
    }

    public Date getStartDate(){
        return startDate;
    }

    public String getFormattedDate(Date date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yy");

        String startDate = simpleDateFormat.format(date);

        return startDate;
    }

    public void setStgPort(String stgPort) {
        this.stgPort = stgPort;
    }

    public String getStgPort() {
        return stgPort;
    }

    public void setLowRange(int lowRange) {
        this.lowRange = lowRange;
    }

    public int getLowRange() {
        return lowRange;
    }


    public void setHighRange(int highRange) {
        this.highRange = highRange;
    }

    public int getHighRange() {
        return highRange;
    }


    public void setRate(double rate) {
        this.rate = rate;
    }

    public double getRate() {
        return rate;
    }

    public String getFormattedRate() {
        NumberFormat currency = NumberFormat.getCurrencyInstance();
        return currency.format(rate);
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCategory() {
        return category;
    }

    public void setStgType(String stgType) {
        if(stgType.equals("00-05") || stgType.equals("06-10")) {
            this.stgType = "Storage";
        }
        if(stgType.equals("11-15") || stgType.equals("16+")) {
            this.stgType = "Demmurage";
        }
    }

    public String getStgType() {
        return stgType;
    }

}

class StorageReport {
    private String containerNo;
    private String vesVoy;
    private String stgType;
    private String category;
    private Date availDate;
    private Date freeTimeEnding;
    private Date outgateDate;
    private String containerSize;
    private int daysStored;
    private double amountDue;


    public StorageReport(){
        containerNo = "";
        vesVoy = "";
        stgType = "";
        category = "";
        availDate = new Date();
        freeTimeEnding = new Date();
        outgateDate = new Date();
        containerSize = "";
        daysStored = 0;
        amountDue = 0.0;
    }

    public void setContainerNo(String containerNo) {
        this.containerNo = containerNo;
    }

    public String getContainerNo() {
        return containerNo;
    }

    public void setVesVoy(String vesVoy) {
        this.vesVoy = vesVoy;
    }

    public String getVesVoy() {
        return vesVoy;
    }

    public void setStgType(String stgType) {
        this.stgType = stgType;
    }

    public String getStgType() {
        return stgType;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getCategory() {
        return category;
    }

    public void setAvailDate(Date availDate) {
        this.availDate = availDate;
    }

    public Date getAvailDate() {
        return availDate;
    }

    public String getFormattedDate(Date date) {
        SimpleDateFormat simpleDateFormat = new SimpleDateFormat("dd-MMM-yy");

        String startDate = simpleDateFormat.format(date);

        return startDate;
    }

    public void setFreeTimeEnding(Date freeTimeEnding) {
        this.freeTimeEnding = freeTimeEnding;
    }

    public Date getFreeTimeEnding() {
        return freeTimeEnding;
    }

    public void setOutgateDate(Date outgateDate) {
        this.outgateDate = outgateDate;
    }

    public Date getOutgateDate(){
        return outgateDate;
    }

    public void setContainerSize(String containerSize) {
        this.containerSize = containerSize;
    }

    public String getContainerSize() {
        return containerSize;
    }

    public void setDaysStored(int daysStored) {
        this.daysStored = daysStored;
    }

    public int getDaysStored(){
        return daysStored;
    }

    public void setAmountDue(double amountDue)  {
        this.amountDue = amountDue;
    }

    public double getAmountDue() {
        return amountDue;
    }

    public String getFormattedAmtDue() {
        NumberFormat currency = NumberFormat.getCurrencyInstance();
        return currency.format(amountDue);
    }
}