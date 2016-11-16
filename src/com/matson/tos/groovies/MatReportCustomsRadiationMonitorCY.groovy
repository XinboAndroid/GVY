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
import java.util.List;

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

public class MatReportCustomsRadiationMonitorCY extends GroovyInjectionBase
{
    private final String  emailFrom = '1aktosdevteam@matson.com';
    private final String emailTo = "1aktosdevteam@matson.com";
    //private final String emailTo = "1aktosdevteam@matson.com;1aktosdevteam@matson.com";

    def inj = null;

    public boolean execute(Map params)
    {
        try
        {
            inj = new GroovyInjectionBase();

            List unitList = null;
            ArrayList reportUnitList =  new ArrayList();
            unitList = getUnits();
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
                def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                reportRunner.emailCsvPDFReports(reportUnitList, parameters,"CUSTOMS RADIATION MONITOR FOR CY", emailTo, "CUSTOMS RADIATION MONITOR FOR CY" ,"Attached report for CUSTOMS RADIATION MONITOR FOR CY");
                println("reportUnitList ------- Success")
            }
            else {
                println("reportUnitList ------- No data to print")
            }

        }catch(Exception e){
            e.printStackTrace();
        }
        return true;
    }



//1.Maps unit Data to report file attribute
    public HashMap populateUnitData(Object unit)
    {
        HashMap map = null;
        try
        {
            String unitId =  unit.unitId;
            String equipType =  unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
            if ((unitId.startsWith("\$")) || (equipType.startsWith("C")))
            {
                return map;
            }
            else{

                def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState");
                transitState = transitState != null ? transitState.getKey() : '';

                transitState = transitState == "S40_YARD" ? "Yard" : "";

                //println("unitId ::"+unitId);

                map = new HashMap();

                map.put("UnitNbr", unit.getFieldValue("unitId"));
                map.put("InboundCarrierId", unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"));
                map.put("PositionSlot",unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));
                map.put("EquipmentType", unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId"));
                map.put("GoodsConsigneeName",unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName"));


            }
        }catch(Exception e){
            println("Error in the report");
            e.printStackTrace();
        }
        return map;
    }

    public List getUnits() {

        try {

            ArrayList units = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE,"S40_YARD"));
            //dq.addDqPredicate(PredicateFactory.in(UnitField.UNIT_IMPEDIMENT_ROAD,"RM"));

            println("DomainQuery :::: "+ dq);
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());

            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    if(unit.getFieldValue("unitVisitState").equals(
                            com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE) &&
                            (unit.getFieldValue("unitAppliedHoldOrPermName") != null && unit.getFieldValue("unitAppliedHoldOrPermName").contains("RM"))) {
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