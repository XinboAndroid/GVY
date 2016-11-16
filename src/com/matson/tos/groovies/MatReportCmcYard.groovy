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

/*
* Author : Raghu Iyer
* Date Written : 04/19/2013
* Description: This groovy is used to generate the CMC yard report
*/

public class MatReportCmcYard extends GroovyInjectionBase
{
    private final String  emailFrom = '1aktosdevteam@matson.com';
    //private final String emailTo = "riyer@matson.com";
    private final String emailTo = "1aktosdevteam@matson.com";

    def inj = null;

    public boolean execute(Map params)
    {
        try
        {
            inj = new GroovyInjectionBase();

            List unitList = null;
            ArrayList reportUnitList =  new ArrayList();
            unitList = getUnits()
            println("unitList.size()"+unitList.size());
            Iterator unitIterator = unitList.iterator();
            while(unitIterator.hasNext())
            {
                def unit = unitIterator.next();
                HashMap unitsDataMap = populateUnitData(unit);
                if(unitsDataMap != null) {
                    reportUnitList.add(unitsDataMap);
                }
            }

            println("reportUnitList -------------------- :"+ (reportUnitList != null ? reportUnitList.size() : "EMPTY"))
            //Set Report Parameter
            if (reportUnitList.size() > 0) {
                HashMap parameters = new HashMap();

                //Create and Mail Report
                JRDataSource ds = new JRMapCollectionDataSource(reportUnitList);

                HashMap reportDesignsmap = new HashMap();
                reportDesignsmap.put("CMC YARD REPORT",ds);

                def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                reportRunner.emailReports(reportDesignsmap,parameters, emailTo, "CMC Yard Report" ,"Attached report for CMC Yard");
                println("reportUnitList ------- Success")
            }
            else {
                println("reportUnitList ------- No data to print")
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

//1.Maps unit Data to report file attribute
    public HashMap populateUnitData(Object unit)
    {
        HashMap map = null;
        try
        {
            String equipType =  unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
            String unitId =  unit.unitId;
            if ((unitId.startsWith("\$")) || (equipType.startsWith("C")))
            {
                return map;
            }
            else{
                Double tempRequiredC=null;
                if (unit.getUnitGoods().getGdsReeferRqmnts() != null)
                {
                    try
                    {
                        tempRequiredC = unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempRequiredC();
                        tempRequiredC = celsiusToFahrenheit(tempRequiredC);
                    }
                    catch (e)
                    {
                        null;
                    }

                }

                def category = unit.getFieldValue("unitCategory");
                category = category != null ? category.getKey() : '';
                def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState");
                transitState = transitState != null ? transitState.getKey() : '';

                transitState = transitState == "S20_INBOUND" ? "InBound" : "Yard";

                def haz = unit.getFieldValue("unitGoods.gdsIsHazardous") ? "Y" : "N";
                //println("Haz ::::::::::::::::::::::::::"+haz);

                map = new HashMap();

                map.put("UnitNbr", unit.getFieldValue("unitId"));
                map.put("Destination", unit.getFieldValue("unitGoods.gdsDestination"));
                map.put("InboundCarrierId", unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"));
                map.put("OutboundCarrierId",unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId"));
                map.put("Category",category);
                map.put("PositionSlot",unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));
                map.put("DateOfLastHandling",unit.getFieldValue("unitActiveUfv.ufvTimeOfLastMove"));
                map.put("GoodsConsigneeName",unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName"));
                map.put("UnitRemark",unit.getFieldValue("unitRemark"));
                map.put("UnitHoldsAndPermissions",unit.getFieldValue("unitAppliedHoldOrPermName"));
                map.put("TempRequiredInF",tempRequiredC);
                map.put("TransitState",transitState);
                map.put("GrossWeight",unit.getFieldValue("unitGoodsAndCtrWtKgLong"));
                map.put("UnitFlexString01",unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId"));
                map.put("UnitFlexString02",haz);

            }
        }catch(Exception e){
            println("Error in the report")
            e.printStackTrace();
        }
        return map;
    }

    public List getUnits() {

        try {

            ArrayList units = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            //dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE,"S40_YARD","S20_INBOUND"))
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE,"S40_YARD"))
            dq.addDqPredicate(PredicateFactory.ne(UnitField.UFV_CATEGORY,"STRGE"))
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_GDS_DESTINATION,"HON","HIL","KAH","KHI","NAW","LNI","MOL","MIX"))
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_FREIGHT_KIND,"FCL"));
            //dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_UNIT_ID,"$ARM-HIL"));

            println("DomainQuery :::: "+ dq);
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());

            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    if(unit.getFieldValue("unitVisitState").equals(
                            com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE)) {
                        units.add(unit);
                    }
                }
            }
            println("unitsSize" + units.size);
            return units;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }
}