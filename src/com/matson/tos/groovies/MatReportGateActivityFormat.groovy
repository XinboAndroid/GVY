import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.EquipmentState
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
import java.util.Calendar;

import com.navis.argo.business.model.Facility;
import com.navis.services.business.event.Event;
import com.navis.argo.business.reference.Equipment
import com.navis.services.business.rules.EventType;
import com.navis.services.business.api.EventManager;
import com.navis.inventory.InventoryField;
import com.navis.services.business.event.EventFieldChange;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.argo.business.reference.Shipper;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.services.business.event.GroovyEvent;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import com.navis.road.RoadField;
import com.navis.argo.business.reference.LineOperator;

/*
* Author : Raghu Iyer
* Date Written : 09/19/2012
* Description: This groovy is used to generate the Stowplan/Manifest Discrepancies after Stowplan and Manifest process
*/

public class MatReportGateActivityFormat extends GroovyInjectionBase
{
    private final String MAEemailTo = "1aktosdevteam@matson.com";
    private final String APLemailTo = "1aktosdevteam@matson.com;info.hon@nortonlilly.com;Joshua_sykes@apl.com";
    private final String ANLemailTo = "1aktosdevteam@matson.com;usa.rbautista@cma-cgm.com;usa.sduggan@cma-cgm.com;info.hon@nortonlilly.com;Joshua_sykes@apl.com";
    private final String HLCemailTo = "1aktosdevteam@matson.com;alloy.brownlow@hlag.com;tpa.ed@hlag.com;info.hon@nortonlilly.com;Joshua_sykes@apl.com";
    private final String HSDemailTo = "1aktosdevteam@matson.com;mary.moyer@us.hamburgsud.com;paul.voorhees@us.hamburgsud.com;tony.anselmo@us.hamburgsud.com;info.hon@nortonlilly.com;Joshua_sykes@apl.com";

    /*private final String MAEemailTo = "1aktosdevteam@matson.com";//"Darlene.mckinley@maersk.com,Kristie.folk@maersk.com,liz.berry@maersk.com";
    private final String APLemailTo = "1aktosdevteam@matson.com";//"info.hon@nortonlilly.com,Joshua_sykes@apl.com";
    private final String ANLemailTo = "1aktosdevteam@matson.com";//"usa.rbautista@cma-cgm.com,usa.sduggan@cma-cgm.com,info.hon@nortonlilly.com,Joshua_sykes@apl.com";
    private final String HLCemailTo = "1aktosdevteam@matson.com";//"alloy.brownlow@hlag.com,tpa.ed@hlag.com,info.hon@nortonlilly.com,Joshua_sykes@apl.com";
    private final String HSDemailTo = "1aktosdevteam@matson.com";//"mary.moyer@us.hamburgsud.com,paul.voorhees@us.hamburgsud.com,tony.anselmo@us.hamburgsud.com,info.hon@nortonlilly.com,Joshua_sykes@apl.com";
*/

//1TOSDevRpt@matson.com
    private String outBoundCarrierId = null
    def inj = null;
    String VesVoy = null;
    String printRpt = "N";
    String inGateData = "N";
    String outGateData = "N";
    String MAEemailBody = null;
    String APLemailBody = null;
    String ANLemailBody = null;
    String HLCemailBody = null;
    String HSDemailBody = null;
    String emailBody = null;

    public boolean execute(Map params)
    {
        try
        {
            println("MatGetUnitDetails")
            inj = new GroovyInjectionBase();
            HashMap reportDesignsmap = new HashMap();

            ArrayList MAEDataList = new ArrayList();
            ArrayList APLDataList = new ArrayList();
            ArrayList ANLDataList = new ArrayList();
            ArrayList HLCDataList = new ArrayList();
            ArrayList HSDDataList = new ArrayList();

            List trkTrans = getUnitTran("MAE");
            if(trkTrans == null){
                println("------------- NO REC ------------------")
                return;
            }

            Iterator iter = trkTrans.iterator();
            while(iter.hasNext()) {
                def aTrans = iter.next();
                HashMap gateActivityDataMap = populateReportData(aTrans)
                if(gateActivityDataMap != null) {
                    MAEDataList.add(gateActivityDataMap);
                }
            }

            MAEemailBody = getEmailBody(inGateData,outGateData,"MAE");
            inGateData = "N";
            outGateData = "N";
            println(MAEemailBody);

            trkTrans = getUnitTran("APL");
            if(trkTrans == null){
                println("------------- NO REC ------------------")
                return;
            }

            iter = trkTrans.iterator();
            while(iter.hasNext()) {
                def aTrans = iter.next();
                HashMap gateActivityDataMap = populateReportData(aTrans)
                if(gateActivityDataMap != null) {
                    APLDataList.add(gateActivityDataMap);
                }
            }

            APLemailBody = getEmailBody(inGateData,outGateData,"APL");
            inGateData = "N";
            outGateData = "N";
            println(APLemailBody);

            trkTrans = getUnitTran("ANL");
            if(trkTrans == null){
                println("------------- NO REC ------------------")
                return;
            }

            iter = trkTrans.iterator();
            while(iter.hasNext()) {
                def aTrans = iter.next();
                HashMap gateActivityDataMap = populateReportData(aTrans)
                if(gateActivityDataMap != null) {
                    ANLDataList.add(gateActivityDataMap);
                }
            }

            ANLemailBody = getEmailBody(inGateData,outGateData,"ANL");
            inGateData = "N";
            outGateData = "N";
            println(ANLemailBody);

            trkTrans = getUnitTran("HLC");
            if(trkTrans == null){
                println("------------- NO REC ------------------")
                return;
            }

            iter = trkTrans.iterator();
            while(iter.hasNext()) {
                def aTrans = iter.next();
                HashMap gateActivityDataMap = populateReportData(aTrans)
                if(gateActivityDataMap != null) {
                    HLCDataList.add(gateActivityDataMap);
                }
            }

            HLCemailBody = getEmailBody(inGateData,outGateData,"HLC");
            inGateData = "N";
            outGateData = "N";
            println(HLCemailBody);

            trkTrans = getUnitTran("HSD");
            if(trkTrans == null){
                println("------------- NO REC ------------------")
                return;
            }

            iter = trkTrans.iterator();
            while(iter.hasNext()) {
                def aTrans = iter.next();
                HashMap gateActivityDataMap = populateReportData(aTrans)
                if(gateActivityDataMap != null) {
                    HSDDataList.add(gateActivityDataMap);
                }
            }

            HSDemailBody = getEmailBody(inGateData,outGateData,"HSD");
            println(HSDemailBody);

            //Set Report Parameter
            HashMap parameters = new HashMap();
            println("MAEDataList size is  ------- "+ MAEDataList.size())
            println("APLDataList size is  ------- "+ APLDataList.size())
            println("ANLDataList size is  ------- "+ ANLDataList.size())
            println("HLCDataList size is  ------- "+ HLCDataList.size())
            println("HSDDataList size is  ------- "+ HSDDataList.size())

            def reportRunner = inj.getGroovyClassInstance("ReportRunner");

            if (MAEDataList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsMae = new JRMapCollectionDataSource(MAEDataList);
                reportRunner.emailReport(dsMae, parameters, "MAERSK_GATE_ACTIVITY", MAEemailTo, "Matson_Client_Report - MAE" ,MAEemailBody);
            }
            else
            {
                JRDataSource dsMae = new JRMapCollectionDataSource(MAEDataList);
                reportRunner.emailReportWithoutAttachment(dsMae, parameters, "MAERSK_GATE_ACTIVITY", MAEemailTo, "Matson_Client_Report - MAE" ,MAEemailBody);
                println("Maresk Gate Report  ------- No Data to print")
            }

            if (APLDataList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsApl = new JRMapCollectionDataSource(APLDataList);
                reportRunner.emailReport(dsApl, parameters, "APL_GATE_ACTIVITY", APLemailTo, "Matson_Client_Report - APL" ,APLemailBody);
            }
            else
            {
                JRDataSource dsApl = new JRMapCollectionDataSource(APLDataList);
                reportRunner.emailReportWithoutAttachment(dsApl, parameters, "APL_GATE_ACTIVITY", APLemailTo, "Matson_Client_Report - APL" ,APLemailBody);
                println("APL Gate Report  ------- No Data to print")
            }

            if (ANLDataList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsAnl = new JRMapCollectionDataSource(ANLDataList);
                reportRunner.emailReport(dsAnl, parameters, "ANL_GATE_ACTIVITY", ANLemailTo, "Matson_Client_Report - ANL" ,ANLemailBody);
            }
            else
            {
                JRDataSource dsAnl = new JRMapCollectionDataSource(ANLDataList);
                reportRunner.emailReportWithoutAttachment(dsAnl, parameters, "ANL_GATE_ACTIVITY", ANLemailTo, "Matson_Client_Report - ANL" ,ANLemailBody);
                println("ANL Gate Report  ------- No Data to print")
            }

            if (HLCDataList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsHlc = new JRMapCollectionDataSource(HLCDataList);
                reportRunner.emailReport(dsHlc, parameters, "HAPAG_LLOYD_GATE_ACTIVITY", HLCemailTo, "Matson_Client_Report - HLC" ,HLCemailBody);
            }
            else
            {
                JRDataSource dsHlc = new JRMapCollectionDataSource(HLCDataList);
                reportRunner.emailReportWithoutAttachment(dsHlc, parameters, "HAPAG_LLOYD_GATE_ACTIVITY", HLCemailTo, "Matson_Client_Report - HLC" ,HLCemailBody);
                println("Hapag Lloyd Sud Gate Report  ------- No Data to print")
            }

            if (HSDDataList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsHsd = new JRMapCollectionDataSource(HSDDataList);
                reportRunner.emailReport(dsHsd, parameters, "HAMBURG_SUD_GATE_ACTIVITY", HSDemailTo, "Matson_Client_Report - HSD" ,HSDemailBody);
            }
            else
            {
                JRDataSource dsHsd = new JRMapCollectionDataSource(HSDDataList);
                reportRunner.emailReportWithoutAttachment(dsHsd, parameters, "HAMBURG_SUD_GATE_ACTIVITY", HSDemailTo, "Matson_Client_Report - HSD" ,HSDemailBody);
                println("Hapag Lloyd Sud Gate Report  ------- No Data to print")
            }


        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public String getEmailBody (String inGateFlag, String outGateFlag, String lineId)
    {
        try
        {
            String lineOptr = null;
            String emailBody = null;
            if (lineId == "MAE")
            {
                lineOptr = "MAERSK Gate"
            }
            else if (lineId == "ANL")
            {
                lineOptr = "ANL Gate"
            }
            else if (lineId == "HLC")
            {
                lineOptr = "HAPAG LLOYD Gate"
            }
            else if (lineId == "APL")
            {
                lineOptr = "APL Gate"
            }
            else if (lineId == "HSD")
            {
                lineOptr = "HAMBURG SUD Gate"
            }

            if (outGateFlag == "N" && inGateFlag == "N")
            {
                emailBody =  "\n \n" + "Note : No Ingate and Outgate activity recorded Yesterday for " + lineOptr;
            }
            else if (outGateFlag == "Y" && inGateFlag == "N")
            {
                emailBody =  "\n \n" + "Note : No Ingate activity recorded Yesterday for " + lineOptr;
            }
            else if (outGateFlag == "N" && inGateFlag == "Y")
            {
                emailBody =  "\n \n" + "Note : No Outgate activity recorded Yesterday for " + lineOptr;
            }
            else
            {
                emailBody = "\n \n" + "Note : Attached Matson Client Report for " + lineOptr;
            }
            return emailBody;
        }catch(Exception e){
            println("Error in the report")
            e.printStackTrace();
        }
    }

//Maps the SI gate data for the report
    public HashMap populateReportData(Object gateTran)
    {
        HashMap map = null;
        Object units = null;
        String lineId = null;
        String frightKind = "MTY";
        Double  cargoWeight = null;

        try
        {

            DateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            formatter.setTimeZone(TimeZone.getTimeZone("HST"));

            Date date = new Date();
            String runTime = formatter.format(date).substring(11,19);
            //String runTime = date.format('h:mm a');

            int a = -1;
            Calendar c = Calendar.getInstance();

            c.setTime(date);
            c.add(Calendar.DATE,a);
            date.setTime( c.getTime().getTime() );

            String formattedDate = date.format('MM/dd/yyyy');

            units = getUnit (gateTran.tranCtrNbr,gateTran.tranLineId);

            String tranType = gateTran.getTranSubType().getKey();
            String blNbr = null;

            if (tranType=="DE" || tranType=="DI" || tranType=="DM") {
                tranType = "OUT Gate Containers";
                outGateData = "Y";
            }
            else if (tranType=="RE" || tranType=="RI" || tranType=="RM" || tranType=="DC") {
                tranType = "IN Gate Containers";
                inGateData = "Y";
            }

            if (gateTran.tranCtrFreightKind.getKey() == "FCL")
            {
                if (units != null)
                {
                    blNbr = units.getFieldValue("unitGoods.gdsBlNbr")
                }
                frightKind  = "LOAD";
                if (gateTran.tranCtrTareWeight <= 0)
                {
                    //Convert KG to Lbs
                    cargoWeight = gateTran.tranCtrGrossWeight * 2.20462;
                }

            }
            else
            {
                if (units != null)
                {
                    blNbr = units.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr")
                }
            }

            if (gateTran.tranLineId == "MAE")
            {
                lineId = "MAERSK GATE ACTIVITY"
            }
            else if (gateTran.tranLineId == "ANL")
            {
                lineId = "ANL GATE ACTIVITY"
            }
            else if (gateTran.tranLineId == "HLC")
            {
                lineId = "HAPAG LLOYD GATE REPORT"
            }
            else if (gateTran.tranLineId == "APL")
            {
                lineId = "APL GATE REPORT"
            }
            else if (gateTran.tranLineId == "HSD")
            {
                lineId = "HAMBURG SUD GATE REPORT"
            }

            Date tranDate = gateTran.tranCreated;

            map = new HashMap();

            map.put("TranLineId", gateTran.tranLineId);
            map.put("TranCtrNbr", gateTran.tranCtrNbr);
            map.put("TranCtrTypeId", gateTran.tranCtrTypeId);
            map.put("TranCtrFreightKind",frightKind)
            map.put("TruckingCompanyId", gateTran.tranTrkcId);
            map.put("TranUnitFlexString01", formatter.format(tranDate).substring(11,19));
            if (units != null)
            {
                map.put("UnitInboundCarrierId",units.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"));//Inbound Vessel
                map.put("TranDestination",units.getFieldValue("unitGoods.gdsDestination"))//Destination
            }
            map.put("TranBlNbr",blNbr)
            map.put("TranType",tranType)
            map.put("TranCtrGrossWeight", cargoWeight);
            map.put("TranUnitFlexString02", formattedDate);
            map.put("TranUnitFlexString03", runTime);
            map.put("TranUnitFlexString04", lineId);

        }catch(Exception e){
            println("Error in the report")
            e.printStackTrace();
        }
        return map;
    }

    public Object getUnit(String unitId, String lineId)
    {

        try {
            println("Inside getUnit");

            Long lineOpGkey = LineOperator.findLineOperatorById(lineId).bzuGkey

            Date date = new Date();
            int a = -1;
            Calendar c = Calendar.getInstance();

            c.setTime(date);
            c.add(Calendar.DATE,a);
            date.setTime( c.getTime().getTime() );

            String formattedDate = date.format('MM/dd/yyyy');

            List ufvGateUnits = null;

            String startDateHST = formattedDate +" "+"00:00:00 HST";//2012-07-19 00:00:00 HST";
            String endDateHST = formattedDate +" "+"23:59:59 HST";//"2012-07-19 23:59:59 HST";

            Date startDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(startDateHST);
            Date endDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(endDateHST);

            DomainQuery dq = QueryUtils.createDomainQuery("Unit");
            dq.addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_ID,unitId))
                    .addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_LINE_OPERATOR,lineOpGkey))
                    .addDqPredicate(PredicateFactory.le(InventoryField.UNIT_TIME_LAST_STATE_CHANGE,endDate));

            def unit = null;

            //println("getUnit "+dq);
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            //println("After unitList"+unitList.size());
            if (unitList.size() > 0)
            {
                Iterator iterUnitList = unitList.iterator()
                while (iterUnitList.hasNext())
                {
                    unit = iterUnitList.next();
                }
            }

            return unit;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

    public List getUnitTran(String lineId)
    {

        try {
            println("Inside getUnit");

            Date date = new Date();
            int a = -1;
            Calendar c = Calendar.getInstance();

            c.setTime(date);
            c.add(Calendar.DATE,a);
            date.setTime( c.getTime().getTime() );

            String formattedDate = date.format('MM/dd/yyyy');

            List ufvGateUnits = null;

            String startDateHST = formattedDate +" "+"00:00:00 HST";//2012-07-19 00:00:00 HST";
            String endDateHST = formattedDate +" "+"23:59:59 HST";//"2012-07-19 23:59:59 HST";

            Date startDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(startDateHST);
            println ("PDT Start date time " + startDate);
            Date endDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(endDateHST);
            println ("PDT End date time " + endDate);

            DomainQuery dq = QueryUtils.createDomainQuery("TruckTransaction").addDqPredicate(PredicateFactory.ge(RoadField.TRAN_CREATED, startDate)).addDqPredicate(PredicateFactory.in(RoadField.TRAN_LINE_ID,lineId)).addDqPredicate(PredicateFactory.le(RoadField.TRAN_CREATED, endDate));//.addDqPredicate(PredicateFactory.in(RoadField.TRAN_LINE_ID,"MAE","HLC","APL","ANL","HSD"));//.addDqPredicate(PredicateFactory.eq(RoadField.TRAN_CTR_NBR,"APZU4663320"));
            //	.addDqPredicate(PredicateFactory.le(RoadField.TRAN_CREATED, endDate)).addDqPredicate(PredicateFactory.eq(RoadField.TRAN_LINE_ID,"APL"));//.addDqPredicate(PredicateFactory.in(RoadField.TRAN_LINE_ID,"MAE","HLC","APL","ANL","HSD"));//.addDqPredicate(PredicateFactory.eq(RoadField.TRAN_CTR_NBR,"APZU4663320"));
            //DomainQuery dq = QueryUtils.createDomainQuery("TruckTransaction").addDqPredicate(PredicateFactory.eq(RoadField.TRAN_CTR_NBR, "MRKU2808305")).addDqPredicate(PredicateFactory.eq(RoadField.TRAN_LINE_ID, "MAE"));.addDqPredicate(PredicateFactory.eq(RoadField.TRAN_LINE_ID, "HLC"));//

            println("dq---------------"+dq);
            ufvGateUnits  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("Query executed");
            println("ufvGateUnits ::"+ufvGateUnits != null ? ufvGateUnits.size() : 0)

            return ufvGateUnits;

        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }
}