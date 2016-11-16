import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.event.Event
import com.navis.framework.persistence.*;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;

import com.navis.road.RoadField;
import com.navis.framework.portal.Ordering;
import com.navis.framework.persistence.HibernateApi;
import com.navis.road.business.atoms.TranSubTypeEnum;
import com.navis.argo.business.atoms.FreightKindEnum;
import com.navis.road.business.model.TruckTransaction;
import java.text.SimpleDateFormat;

import com.navis.security.business.user.BaseUser
import com.navis.road.business.model.TruckingCompany

import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import com.navis.argo.business.reports.DigitalAsset;
import java.text.SimpleDateFormat
import java.text.DateFormat

import com.navis.argo.ContextHelper
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.road.business.model.TruckVisitStats
import com.navis.road.business.model.TruckVisitDetails

public class GvyReportTruckerTurnTime
{

    HashMap mapTrckTrans = null;
    ArrayList unitList = new ArrayList();
    private final String emailTo = '1aktosdevteam@matson.com';
    def inj = new GroovyInjectionBase();

    public void processLookup(String fromDate,String toDate, String truckingCompany, String consignee)
    {
        try
        {
            List tvdLst = getTruckVisitByCriteria(fromDate,toDate,truckingCompany,consignee)
            int tvdSize = tvdLst != null ? tvdLst.size() : 0
            println("truckVisitDetailSize ::"+tvdSize)
            //List and map each trucker
            for(atvd in tvdLst){
                getTruckTransStats(atvd)
            }

            println("unitList :"+ (unitList != null ? unitList.size() : "EMPTY"))

            //Sorting By Field
            def gvyRptUtil = inj.getGroovyClassInstance("ReportFieldSortUtil")
            if(unitList != null && unitList.size() > 0){
                unitList =  gvyRptUtil.processFieldSort(unitList,"truckingCompanyId")
            }

            //Set Report Parameter
            HashMap parameters = new HashMap();
            String strDate =  ContextHelper.formatTimestamp(new Date(),ContextHelper.getThreadUserTimezone())
            parameters.put("Date",strDate);
            // parameters.put("MATSONLOGO",DigitalAsset.findImage("MATSONLOGO"));

            //Create and Mail Report
            JRDataSource ds = new JRMapCollectionDataSource(unitList);
            def reportRunner = inj.getGroovyClassInstance("ReportRunner");
            reportRunner.emailReport(ds, parameters, "TURNTIME_BY_TRUCKER", emailTo, "Turn Time By Trucker", "Turn Time By Trucker, Consignee and Date");

        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public List getTruckVisitByCriteria(String fromDate,String toDate, String truckingCompanys, String consignee){
        def results  = null;
        try{
            //def consigneeArr =  consignee != null ? consignee.split(',') : null;

            SimpleDateFormat dateTimeFormat = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
            String startTime = fromDate +" 00:00:00"
            String endTime = toDate +" 23:59:00"
            Date startDateTime = dateTimeFormat.parse(startTime)
            Date endDateTime = dateTimeFormat.parse(endTime)

            println("startTime : "+startTime+" endTime:"+endTime+"  startDateTime:"+startDateTime+"   endDateTime:"+endDateTime)

            //2.Lookup TruckVisit
            DomainQuery dq = QueryUtils.createDomainQuery("TruckVisitDetails").addDqPredicate(PredicateFactory.eq(RoadField.TVDTLS_GATE, 3701109)).addDqPredicate(PredicateFactory.eq(RoadField.TVDTLS_STATUS, 'COMPLETE'))
            //DomainQuery dq = QueryUtils.createDomainQuery("TruckVisitDetails").addDqPredicate(PredicateFactory.eq(RoadField.TVDTLS_STATUS, 'COMPLETE'))

            //Criteria Date, Trucker, Consignee
            if(startDateTime != null && endDateTime != null){
                dq.addDqPredicate(PredicateFactory.between(RoadField.TVDTLS_CREATED, startDateTime, endDateTime))
            }
            if(truckingCompanys != null){
                def trcukCmpyGkeyLst = getTruckCmpyGkey(truckingCompanys)
                dq.addDqPredicate(PredicateFactory.in(RoadField.TVDTLS_TRK_COMPANY, trcukCmpyGkeyLst));
            }

            dq.addDqPredicate(PredicateFactory.like(RoadField.TRAN_CONSIGNEE, "0099835200"));
//Need to Uncomment
/*      if(consigneeArr != null){
           if(consigneeArr.size() == 1){
             dq.addDqPredicate(PredicateFactory.like(RoadField.TRAN_CONSIGNEE, consigneeArr.get(0)));
           }else{
             dq.addDqPredicate(PredicateFactory.in(RoadField.TRAN_CONSIGNEE, consigneeArr));
          }
      }
*/
            //dq.addDqPredicate(Ordering.desc(RoadField.TVDTLS_TRK_COMPANY))
            //dq.addDqOrdering(Ordering.desc(RoadField.TVDTLS_CREATED))

            println("Domain Query ::"+ dq)
            results = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("result size ::"+results != null ? results.size() : 0);

        }catch(Exception e){
            e.printStackTrace()
        }
        return results
    }

    public Integer[] getTruckCmpyGkey(String truckingCompanys){

        try{
            ArrayList truckGkeyLst = new ArrayList()
            def trckCompanyArr =truckingCompanys != null ? truckingCompanys.split(',') : null;
            for(aTruckCmpyId  in trckCompanyArr){
                def truckingCmpy = TruckingCompany.findTruckingCompany(aTruckCmpyId)
                def gkeyVal = truckingCmpy.getBzuGkey()
                println("Trucking Company Gkey ::"+gkeyVal)
                truckGkeyLst.add(gkeyVal)
            }
            println("Trucking Company arraylist ::"+truckGkeyLst)
            return truckGkeyLst.toArray()

        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public Object getTruckTransStats(Object truckVisitDetail)
    {
        try{
            //1.Truck Id
            def tvdTruckId = truckVisitDetail != null ? truckVisitDetail.tvdtlsTruckId : "NO ID";
            def gvyEventUtil = inj.getGroovyClassInstance("GvyEventUtil");

            def turnTime = getTruckerTurnTime(truckVisitDetail)

            //Instantiates one Row Entry
            mapTrckTrans = new HashMap();

            //IGT Variables
            def transType = null; def gateAction = null; def atime = null;
            def aDate = null; def truckCmpyId = null;
            def cntrNbr = null;

            //OGT Variables
            def transTypeOgt = null; def gateActionOgt = null; def atimeOgt = null;
            def aDateOgt = null; def cntrNbrOgt = null;

            //Truck Transaction Per Truck Visit
            def listTruckTrans = truckVisitDetail.getTvdtlsTruckTrans()
            for(aTruckTrans in listTruckTrans)
            {
                truckCmpyId = aTruckTrans.getTranTrkcId()

                FreightKindEnum freightKind = aTruckTrans.getTranCtrFreightKind();

                //Get Transaction Type IGT/OGT
                TranSubTypeEnum tranType = aTruckTrans.getTranSubType();
                if(TranSubTypeEnum.RE.equals(tranType) || TranSubTypeEnum.RI.equals(tranType) || TranSubTypeEnum.RM.equals(tranType))
                {
                    //Action,TrckCmpyId,CntrNbr,transactionType,date,time
                    gateAction = "IGATE";
                    cntrNbr = aTruckTrans.getTranCtrNbr()
                    transType = FreightKindEnum.MTY.equals(freightKind) ? "EMPTY" : "LOAD"
                    atime = gvyEventUtil.formatTime(aTruckTrans.getTranCreated(), ContextHelper.getThreadUserTimezone());
                    aDate = gvyEventUtil.formatDate(aTruckTrans.getTranCreated(), ContextHelper.getThreadUserTimezone());

                }else if(TranSubTypeEnum.DE.equals(tranType) || TranSubTypeEnum.DI.equals(tranType) || TranSubTypeEnum.DM.equals(tranType)){
                    gateActionOgt = "OGATE";
                    cntrNbrOgt = aTruckTrans.getTranCtrNbr()
                    transTypeOgt = FreightKindEnum.MTY.equals(freightKind) ? "EMPTY" : "LOAD"
                    atimeOgt = gvyEventUtil.formatTime(aTruckTrans.getTranChanged(), ContextHelper.getThreadUserTimezone());
                    aDateOgt = gvyEventUtil.formatDate(aTruckTrans.getTranChanged(), ContextHelper.getThreadUserTimezone());	        }

            }//For Ends
            boolean iGate = false;  boolean oGate = false;
            //Add hasmap values
            mapTrckTrans.put("truckingCompanyId", truckCmpyId)

            if(gateAction.equals("IGATE") && cntrNbr != null){
                iGate = true
                mapTrckTrans.put("truckId", tvdTruckId)
                mapTrckTrans.put("transaction", transType)
                mapTrckTrans.put("action", gateAction)
                mapTrckTrans.put("unitNbr", cntrNbr)
                mapTrckTrans.put("truckAtime", atime)
                mapTrckTrans.put("truckADate", aDate)
            }

            if(gateActionOgt.equals("OGATE") && cntrNbrOgt != null){
                oGate = true
                mapTrckTrans.put("unitNbrOgt",cntrNbrOgt)
                mapTrckTrans.put("transactionOgt",transTypeOgt)
                mapTrckTrans.put("truckIdOgt",tvdTruckId)
                mapTrckTrans.put("actionOgt",gateActionOgt)
                mapTrckTrans.put("aDateOgt",aDateOgt)
                mapTrckTrans.put("aTimeOgt",atimeOgt)
            }

            if((iGate && oGate)|| (iGate && cntrNbr != null)){
                mapTrckTrans.put("turntime", turnTime)
            }else if(oGate && cntrNbrOgt != null){
                mapTrckTrans.put("turntimeOgt", turnTime)
            }

            println("tvdTruckId: "+tvdTruckId+" truckCmpyId :"+truckCmpyId+"  cntrNbr:"+cntrNbr+" transType ::"+transType+" gateAction ::"+gateAction+"  gateActionOgt:"+gateActionOgt+"  cntrNbrOgt:"+cntrNbrOgt+"   transTypeOgt:"+transTypeOgt+"  atimeOgt:"+atimeOgt+" aDateOgt:"+aDateOgt+" iGate:"+iGate+"  oGate:"+iGate+"  turntime: "+turnTime )
            if(iGate && truckCmpyId != null || oGate && truckCmpyId != null){
                unitList.add(mapTrckTrans)
            }

        }catch(Exception e){
            e.printStackTrace();
        }
    }


    public Long getTruckerTurnTime(TruckVisitDetails truckVisitDetail)
    {
        Long turnTimeInMin = 0;
        try{
            TruckVisitStats truckVisitStats = new TruckVisitStats()
            TruckVisitStats unitTvstats = truckVisitStats.findOrCreateTruckVisitStats(truckVisitDetail)
            Long turnTimeInMillis = unitTvstats.getTvstatTurnTime()
            turnTimeInMin = turnTimeInMillis == null ? 0 : ( turnTimeInMillis < 60000 ? 0 : turnTimeInMillis/60000)
            //println("unitTvstats ::"+unitTvstats+" turnTimeInMillis ::"+turnTimeInMillis+"  turnTimeInMin::"+turnTimeInMin)

        }catch(Exception e){
            e.printStackTrace()
        }
        return turnTimeInMin
    }

}//Class Ends