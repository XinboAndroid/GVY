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

public class MatReportMAEGateActivityFormat extends GroovyInjectionBase
{
    /*private final String MAEemailTo = "1aktosdevteam@matson.com";//"Darlene.mckinley@maersk.com,Kristie.folk@maersk.com,liz.berry@maersk.com"; */
    private final String MAEemailTo = "1aktosdevteam@matson.com;Darlene.mckinley@maersk.com";
    //private final String MAEemailTo = "1aktosdevteam@matson.com";
    private String outBoundCarrierId = null
    def inj = null;
    String VesVoy = null;
    String printRpt = "N";
    String inGateData = "N";
    String outGateData = "N";
    String emailBody = null;

    public boolean execute(Map params)
    {
        try
        {
            println("MatGetUnitDetails")
            inj = new GroovyInjectionBase();
            HashMap reportDesignsmap = new HashMap();

            ArrayList MAEDataList = new ArrayList();

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
            println("inGateData " + inGateData +" outGateData " + outGateData);

            if (outGateData == "N" && inGateData == "N")
            {
                emailBody =  "\n \n" + "Note : No Ingate and Outgate activity recorded Today for MAERSK Gate";
            }
            else if (outGateData == "Y" && inGateData == "N")
            {
                emailBody =  "\n \n" + "Note : No Ingate activity recorded Today for MAERSK Gate";
            }
            else if (outGateData == "N" && inGateData == "Y")
            {
                emailBody =  "\n \n" + "Note : No Outgate activity recorded Today for MAERSK Gate";
            }
            else
            {
                emailBody = "\n \n" + "Note : Attached Matson Client Report for MAERSK Gate";
            }

            println(emailBody);
            //Set Report Parameter
            HashMap parameters = new HashMap();
            println("MAEDataList size is  ------- "+ MAEDataList.size())

            def reportRunner = inj.getGroovyClassInstance("ReportRunner");

            if (MAEDataList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsMae = new JRMapCollectionDataSource(MAEDataList);
                reportRunner.emailReport(dsMae, parameters, "MAERSK_GATE_ACTIVITY", MAEemailTo, "Matson_Client_Report - MAE" ,emailBody);
                println("MaerskDataList ------- Success")
            }

            else {
                JRDataSource dsMae = new JRMapCollectionDataSource(MAEDataList);
                reportRunner.emailReportWithoutAttachment(dsMae, parameters, "MAERSK_GATE_ACTIVITY", MAEemailTo, "Matson_Client_Report - MAE" ,emailBody);
                println("MaerskDataList size is  ------- No Data to print")
            }

        }catch(Exception e){
            e.printStackTrace()
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
                map.put("UnitInboundCarrierId", units.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"));//Inbound Vessel
                map.put("TranDestination",units.getFieldValue("unitGoods.gdsDestination"))//Destination
            }
            map.put("TranBlNbr",blNbr)
            map.put("TranType",tranType)
            map.put("TranCtrGrossWeight", cargoWeight);
            map.put("TranUnitFlexString02", formattedDate);
            map.put("TranUnitFlexString03", runTime);
            map.put("TranUnitFlexString04", lineId);
            map.put("TranUnitFlexString05", inGateData);//Ingate data flag
            map.put("TranUnitFlexString06", inGateData);//Outgate data flag

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
            String formattedDate = date.format('MM/dd/yyyy');
            List ufvGateUnits = null;

            String startDateHST = formattedDate +" "+"00:00:00 HST";//2012-07-19 00:00:00 HST";
            String endDateHST = formattedDate +" "+"23:59:59 HST";//"2012-07-19 23:59:59 HST";

            Date startDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(startDateHST);
            Date endDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(endDateHST);

            DomainQuery dq = QueryUtils.createDomainQuery("Unit");
            dq.addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_ID,unitId)).addDqPredicate(PredicateFactory.eq(InventoryField.UNIT_LINE_OPERATOR,lineOpGkey)).addDqPredicate(PredicateFactory.le(InventoryField.UNIT_TIME_LAST_STATE_CHANGE,endDate));

            def unit = null;

            //println("getUnit "+dq);
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+unitList.size());
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

            String formattedDate = date.format('MM/dd/yyyy');

            List ufvGateUnits = null;

            String startDateHST = formattedDate +" "+"00:00:00 HST";//2012-07-19 00:00:00 HST";
            String endDateHST = formattedDate +" "+"23:59:59 HST";//"2012-07-19 23:59:59 HST";

            Date startDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(startDateHST);
            println ("PDT Start date time " + startDate);
            Date endDate = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss z", Locale.ENGLISH).parse(endDateHST);
            println ("PDT End date time " + endDate);

            DomainQuery dq = QueryUtils.createDomainQuery("TruckTransaction").addDqPredicate(PredicateFactory.ge(RoadField.TRAN_CREATED, startDate)).addDqPredicate(PredicateFactory.in(RoadField.TRAN_LINE_ID,lineId));//.addDqPredicate(PredicateFactory.in(RoadField.TRAN_LINE_ID,"MAE","HLC","APL","ANL","HSD"));//.addDqPredicate(PredicateFactory.eq(RoadField.TRAN_CTR_NBR,"APZU4663320"));

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