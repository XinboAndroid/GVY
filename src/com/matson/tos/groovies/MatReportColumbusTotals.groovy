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
* Date Written : 02/01/2013
* Description: This groovy is used to generate the Columbus Totals by Owner report
*/

public class MatReportColumbusTotals extends GroovyInjectionBase
{
    private final String  emailFrom = '1aktosdevteam@matson.com'
    //private final String emailTo = "1aktosdevteam@matson.com";
    private final String emailTo = "1aktosdevteam@matson.com";

    private String outBoundCarrierId = null
    def inj = null;
    String VesVoy = null;


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
                reportDesignsmap.put("COLUMBUS TOTALS",ds);

                def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                reportRunner.emailReports(reportDesignsmap,parameters, emailTo, VesVoy+" Columbus Totals by Owner" ,"Attached report for Columbus Totals by Owner");
                println("reportUnitList ------- Success")
            }
            else {
                println("reportUnitList ------- No data to print")
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public boolean generateColumbusReport(List unitList, String vesVoy)
    {
        try
        {
            inj = new GroovyInjectionBase();

            ArrayList reportUnitList =  new ArrayList();
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
                reportDesignsmap.put("COLUMBUS TOTALS",ds);

                def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                reportRunner.emailReports(reportDesignsmap,parameters, emailTo, vesVoy+" Columbus Totals by Owner" ,"Attached report for Columbus Totals by Owner");
                println("reportUnitList ------- Success")
            }
            else {
                def emailSender = inj.getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailFrom,emailTo, vesVoy+" Columbus Totals by Owner","No units reported");
                println("reportUnitList ------- No data to print")
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }


//1.Maps unit Data to report file attribute
    public HashMap populateUnitData(Object unit)
    {
        HashMap map = null;
        try
        {


            String equipType =  unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");



            map = new HashMap();

            equipType = String.format("%-8s", equipType);
            //println("equipType::::"+equipType+" "+unit.unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId)

            map.put("UnitNbr", unit.getFieldValue("unitId"));
            map.put("EquipmentType",equipType+unit.unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId)//EquipmentType
            map.put("EquipmentOwner",unit.unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId)//EquipmentOwner


        }catch(Exception e){
            println("Error iin the report")
            e.printStackTrace();
        }
        return map;
    }


    public List getReportUnits(String vesVoy)
    {

        try {
            println("Inside getReportUnits");
            def id = vesVoy;//"HUG294" // This will be removed when it call automatically after Manifest/Stow process
            //def id = "HUG294" // This will be removed when it call automatically after Manifest/Stow process
            VesVoy = id;
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");

            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID,id)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_POD_ID,"HON"));
            println(dq);
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

    public List getUnits()
    {

        try {
            println("Inside getUnits");
            def id = "ROS313"//"HUG294" // This will be removed when it call automatically after Manifest/Stow process
            VesVoy = id;
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");

            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, id)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_POD_ID,"HON"));
            println(dq);
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