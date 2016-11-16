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



public class MatReportMtyGateConsists extends GroovyInjectionBase
{
    private final String emailTo = "1aktosdevteam@matson.com;";
    //private final String emailTo = "1aktosdevteam@matson.com";
    def inj = null;
    String tranType = null;


    public boolean execute(Map params)
    {
        try
        {
            inj = new GroovyInjectionBase();

            ArrayList trkTransList = new ArrayList();
            List trkTrans = getGateTrans()
            if(trkTrans == null){
                println("------------- NO REC ------------------")
                return;
            }
            //println("No of rows =-------------" + trkTrans)
            Iterator iter = trkTrans.iterator();
            while(iter.hasNext()) {
                //println("Inside loop")
                def aTrans = iter.next();
                HashMap trkTransDataMap = populateUnitData(aTrans)
                if(trkTransDataMap != null) {
                    trkTransList.add(trkTransDataMap);
                }
            }
            println("trkTransList -------------------- :"+ (trkTransList != null ? trkTransList.size() : "EMPTY"))
            //Set Report Parameter
            HashMap parameters = new HashMap();

            //Create and Mail Report
            JRDataSource ds = new JRMapCollectionDataSource(trkTransList);
            def reportRunner = inj.getGroovyClassInstance("ReportRunner");
            reportRunner.emailExcelReport(ds, parameters, "MTYGATECONSISTS", emailTo, "6 Weeks MTY Gate Containers" ,"Attached report for MTY Gate Consists 6 weeks average");
            println("trkTransList ------- Success")
        }catch(Exception e){
            e.printStackTrace()
        }
    }


    //1.Maps unit Data to report file attribute
    public HashMap populateUnitData(Object truckTrans)
    {
        HashMap map = null;
        try
        {


            Integer day = 0
            String frightKind = null;
            if (null != truckTrans.getTranCtrFreightKind())
            {
                frightKind = truckTrans.getTranCtrFreightKind().getKey()
            }
            day =  truckTrans.tranCreated.getDay();

            map = new HashMap();

            //map.put("TranCtrNbr", truckTrans.tranCtrNbr +",");
            //map.put("TranCreated", truckTrans.tranCreated);
            //map.put("TranCtrOwnerId", ","+truckTrans.tranLineId+",");
            //map.put("TranCtrTypeId", truckTrans.tranCtrTypeId+",");
            //map.put("TranTypeShort",truckTrans.getTranSubType().getKey()+",");
            //map.put("StageId", ","+truckTrans.tranStageId+",");
            //map.put("TranStatus", truckTrans.getTranStatus().getKey());

//println("truckTrans.getTranSubType() " + truckTrans.getTranSubType() + truckTrans.getTranStatus() + truckTrans.getTranCtrFreightKind() + frightKind)
            tranType = truckTrans.getTranSubType().getKey();

            if (tranType=="DM") {
                tranType = "OG";
            }
            else if (tranType=="RM" || tranType=="DC") {
                tranType = "IG";
            }

            map.put("TranCtrNbr", truckTrans.tranCtrNbr);
            map.put("TranCreated", truckTrans.tranCreated);
            map.put("TranCtrOwnerId",truckTrans.tranLineId);
            map.put("TranCtrTypeId", truckTrans.tranCtrTypeId);
            map.put("TranTypeShort",tranType);
            map.put("TranType", truckTrans.getTranSubType().getKey());
            map.put("StageId",truckTrans.tranStageId);
            map.put("TranStatus", truckTrans.getTranStatus().getKey());
            map.put("TranSeqNbr",day);
            map.put("TranCtrFreightKind",frightKind);//truckTrans.getTranCtrFreightKind().getKey());


        }catch(Exception e){
            e.printStackTrace();
        }
        return map;
    }

    public List getGateTrans()
    {
        /* //Date Manipulation - Minus 3 days from curent day
             Date startDate = new Date()
             String startDateT = '2012-09-24'
             startDate = new Date().parse('yyyy-MM-dd', startDateT)
             println("StartDate ===>" + startDate);
             String trimDate = '2012-09-24'
             //String trimDate = new Date().format('yyyy-MM-dd')
             Date endDate = new Date().parse('yyyy-MM-dd', trimDate)
             //int days = new Date().getDay();
             int days = 1;
             days = days - 1;
             days = days + 42 * -1;
             println("day "+ days);
             Calendar c = Calendar.getInstance();
             c.setTime(startDate);
             c.add(Calendar.DATE,days);
             startDate.setTime( c.getTime().getTime() ); */


        Date startDate = new Date()
        println("StartDate ===>" + startDate);
        String trimDate = new Date().format('yyyy-MM-dd')
        Date endDate = new Date().parse('yyyy-MM-dd', trimDate)
        int days = new Date().getDay();
        days = days - 1;
        days = days + 42 * -1;
        println("day "+ days);
        Calendar c = Calendar.getInstance();
        c.setTime(startDate);
        c.add(Calendar.DATE,days);
        startDate.setTime( c.getTime().getTime() );

        trimDate = startDate.format('yyyy-MM-dd')
        startDate = startDate.parse('yyyy-MM-dd', trimDate)

        println("trimDate "+ trimDate);
        println("startDate "+ startDate);
        println("endDate "+ endDate);
        println("Day "+ days);


        List gateTranUnits = null;
        try
        {
            Long facilityGkey = ContextHelper.getThreadFacility().getFcyGkey();
            //println("YB OBCarrId ::"+visit.getCvdCv()+" YBGKEY :"+cvGkey)
            println("ContextHelper.getThreadFacility().getFcyGkey() : "+ContextHelper.getThreadFacility().getFcyGkey())

            DomainQuery dq = QueryUtils.createDomainQuery("TruckTransaction").addDqPredicate(PredicateFactory.ge(RoadField.TRAN_CREATED, startDate)).addDqPredicate(PredicateFactory.lt(RoadField.TRAN_CREATED, endDate)).addDqPredicate(PredicateFactory.eq(RoadField.TRAN_LINE_ID, "MAT"))
                    .addDqPredicate(PredicateFactory.in(RoadField.TRAN_SUB_TYPE, "RM","DM","DC")).addDqPredicate(PredicateFactory.eq(RoadField.TRAN_FACILITY, facilityGkey))
                    .addDqPredicate(PredicateFactory.in(RoadField.TRAN_STATUS, "COMPLETE"));
            println("dq---------------"+dq);
            gateTranUnits  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("Query executed");
            println("gateTranUnits ::"+gateTranUnits != null ? gateTranUnits.size() : 0)
        }catch(Exception e){
            e.printStackTrace()
        }
        return gateTranUnits;
    }

}
