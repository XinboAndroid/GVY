/*
*  A1   GR & Raghu Iyer  04/13/2012     Created Initial version for MTY gate consist reports
*                                       This report will fetch the data for all MAT container
*                                       which are created in past 6 weeks and group the data
*                                       based on equipment type for each day(Mon to Sun)
*/
import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.argo.business.model.Facility;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.event.Event
import com.navis.argo.business.reference.Equipment
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import com.navis.argo.business.reports.DigitalAsset;
import java.text.SimpleDateFormat
import java.text.DateFormat

import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.argo.business.model.CarrierVisit

import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery
import com.navis.argo.ArgoField;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.road.business.model.*;
import com.navis.road.RoadField;
import java.util.Calendar;

import org.springframework.core.io.ByteArrayResource;
import com.navis.argo.business.reports.DigitalAsset;
import java.util.HashMap;
import java.util.Date;



public class GvyPier2GateHourly extends GroovyInjectionBase
{
    //private final String emailTo = "1aktosdevteam@matson.com";
    private final String emailTo = "1aktosdevteam@matson.com";
    private String outBoundCarrierId = null
    def inj = null;
    String creater = null;
    HashMap classMap = new HashMap();

    String startTime = null;
    String startDateHST = null;
    String endDateHST = null;
    String formattedDate = null;
    Date date = new Date();

    String tranType = null;
    String printPier2Rpt = "N";
    String subject = "Attached report for Pier2 Gate hourly transactions";
    String Pier2GateSubject = null;


    public boolean execute(Map params)
    {
        try
        {
            println ("Here : execute")
            //readGateHourSpreadsheet()
            println ("Here : After excel read")
            inj = new GroovyInjectionBase();

            println ("Print Report")
            ArrayList Pier2GateList = new ArrayList();
            HashMap reportDesignsmap = new HashMap();

            List trkTrans = getGateTrans()
            if(trkTrans == null){
                println("------------- NO REC ------------------")
                return;
            }

            Iterator iter = trkTrans.iterator();
            while(iter.hasNext()) {
                def aTrans = iter.next();
                HashMap Pier2GateDataMap = populatePier2gateData(aTrans)
                if(Pier2GateDataMap != null) {
                    Pier2GateList.add(Pier2GateDataMap);
                }
            }
            println("Pier2GateList -------------------- :"+ (Pier2GateList != null ? Pier2GateList.size() : "EMPTY"))

            //Set Report Parameter
            HashMap parameters = new HashMap();
            println ("printPier2Rpt " + printPier2Rpt);

            if (printPier2Rpt == "Y")
            {
                //Create and Mail Report
                JRDataSource ds = new JRMapCollectionDataSource(SIGateList);
                reportDesignsmap.put("PIER2 GATE HOURLY",ds);
            }
            else
            {
                Pier2GateSubject  = "Note : No Pier2 Gate transaction found for this hour"
                println("Pier2 Report Status ------- Nothing to report for this Hour")
            }

            if (printPier2Rpt == "Y")
            {

                subject = subject + "\n \n" + Pier2GateSubject;
                println ("subject" + subject)
                def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                reportRunner.emailReports(reportDesignsmap, parameters,emailTo, "Pier2 Gate Hourly Activity Report" ,subject);
                println("Report Status ------- Success")
            }
            else
            {
                println("Report Status ------- Nothing to report for this Hour")
            }


        }catch(Exception e){
            e.printStackTrace()
        }
    }

//Maps the Pier2 gate data for the report
    public HashMap populatePier2gateData(Object truckTrans)
    {
        HashMap map = null;
        try
        {

            creater = truckTrans.tranCreator.toUpperCase();

            if ((creater=='PIER2') && 	(truckTrans.tranCtrTypeId != null))
            {
                if (truckTrans.tranCtrTypeId.substring(0,1) != "C")
                {

                    Date tranDate = truckTrans.tranCreated;

                    DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                    formatter.setTimeZone(TimeZone.getTimeZone("HST"));

                    String Hour =formatter.format(tranDate).substring(11,13);
                    String currHour = formatter.format(date).substring(11,13);
                    String currMin = formatter.format(date).substring(14,16);
                    String reportHour = "00:00 to "+currHour+":"+currMin
                    String currHourDisplay = currHour+":00 to "+currHour+":59"
                    String FreightKind = null;

                    if (Hour == currHour )
                    {
                        printPier2Rpt = "Y";
                    }

                    tranType = truckTrans.getTranSubType().getKey();

                    if (tranType=="DE" || tranType=="DI") {
                        tranType = "OUTGATE LOAD";
                    }
                    else if (tranType=="RE" || tranType=="RI") {
                        tranType = "INGATE LOAD";
                    }
                    else if (tranType=="DM") {
                        tranType = "OUTGATE EMPTY";
                    }
                    else if (tranType=="RM" || tranType=="DC") {
                        tranType = "INGATE EMPTY";
                    }
                    Hour = Hour+":00 to "+Hour+"59";

                    map = new HashMap();

                    map.put("TranUnitId", truckTrans.tranCtrNbr);
                    map.put("TranCreated", truckTrans.tranCreated);
                    map.put("TranCtrFreightKind",FreightKind);
                    map.put("StageId", truckTrans.tranStageId);
                    map.put("TranCtrTypeId", truckTrans.tranCtrTypeId);
                    map.put("TranUnitFlexString01", Hour);
                    map.put("TranUnitFlexString02", formatter.format(tranDate).substring(11,13));
                    map.put("TranUnitFlexString03",currHourDisplay);
                    map.put("TranUnitFlexString04",currHour);
                    map.put("TranUnitFlexString05",reportHour);
                    map.put("TranUnitFlexString06",tranType);
                    map.put("TranUnitFlexString07",currHour+":"+currMin);
                    map.put("TranUnitFlexString08",creater);
                    map.put("TranUnitFlexString09",truckTrans.tranLineId);
                    map.put("TranUnitFlexString10",truckTrans.getTranStatus().getKey());

                }
            }

        }catch(Exception e){
            println("Error in the report")
            e.printStackTrace();
        }
        return map;
    }

    public List getGateTrans()
    {

        println(" Inside DB Query " + date)

        formattedDate = date.format('MM/dd/yyyy');

        startDateHST = formattedDate +" "+"00:00:00 HST";//2012-07-19 00:00:00 HST";
        endDateHST = formattedDate +" "+"23:59:59 HST";//"2012-07-19 23:59:59 HST";

        Date startDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(startDateHST);
        println ("PDT Start date time " + startDate);
        Date endDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(endDateHST);
        println ("PDT End date time " + endDate);

        List ufvYbUnits = null;
        try
        {
            Long facilityGkey = ContextHelper.getThreadFacility().getFcyGkey();
            println("ContextHelper.getThreadFacility().getFcyGkey() : "+ContextHelper.getThreadFacility().getFcyGkey())

            DomainQuery dq = QueryUtils.createDomainQuery("TruckTransaction").addDqPredicate(PredicateFactory.ge(RoadField.TRAN_CREATED, startDate)).addDqPredicate(PredicateFactory.le(RoadField.TRAN_CREATED, endDate)).addDqPredicate(PredicateFactory.eq(RoadField.TRAN_FACILITY, facilityGkey));
            println("dq---------------"+dq);
            ufvYbUnits  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("Query executed");
            println("unitUfvYB ::"+ufvYbUnits != null ? ufvYbUnits.size() : 0)
        }catch(Exception e){
            e.printStackTrace()
        }
        return ufvYbUnits;
    }

}
