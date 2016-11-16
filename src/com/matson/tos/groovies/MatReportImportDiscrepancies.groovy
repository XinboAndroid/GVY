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
* Date Written : 09/19/2012
* Description: This groovy is used to generate the Stowplan/Manifest Discrepancies after Stowplan and Manifest process
*/

public class MatReportImportDiscrepancies extends GroovyInjectionBase
{
    private final String  emailFrom = '1aktosdevteam@matson.com'
    //private final String emailTo = "1aktosdevteam@matson.com";
    private final String emailTo = "1aktosdevteam@matson.com";
    private String outBoundCarrierId = null
    def inj = null;
    String VesVoy = null;

// Checking if the container listed in both Stowplan and Manifest.
    public String getEDIStowManifest(Object unit)
    {
        try{
            Date date = new Date();
            String formattedDate = date.format('MM/dd/yyyy')
            String isBothEvents = "N";
            String isManifestEvents = "N";
            String isStowEvents = "N";

            EventType evntType = EventType.findEventType("EDI_MANIFEST");
            EventManager eventManager = (EventManager)Roastery.getBean("eventManager");
            List events = eventManager.getEventHistory(evntType, unit);
            if (events.size() > 0 )
            {
                Iterator eventsList = events.iterator();
                while (eventsList.hasNext()) {
                    def event = eventsList.next();
                    String eventDate = event.getEventTime().format('MM/dd/yyyy')
                    if (eventDate == formattedDate )
                    {
                        isManifestEvents = "Y";
                        //println("ManifestEvent ======================== > " + event.getEventTypeId() + " "+event.getEventTypeDescription()+" "+event.getEventAppliedToNaturalKey()+" "+event.getEventComplexId()+"  "+ event.getEventTime()+" "+eventDate);
                    }
                    break;
                }
            }
            if (isManifestEvents == "Y")
            {
                evntType = EventType.findEventType("EDI_STOWPLAN");
                events = eventManager.getEventHistory(evntType, unit);
                if (events.size() > 0 )
                {
                    Iterator eventsList = events.iterator();
                    while (eventsList.hasNext()) {
                        def event = eventsList.next();
                        String eventDate = event.getEventTime().format('MM/dd/yyyy')
                        if (eventDate == formattedDate )
                        {
                            isStowEvents = "Y";
                            //println("StowPlanEvent ======================== > " + event.getEventTypeId() + " "+event.getEventTypeDescription()+" "+event.getEventAppliedToNaturalKey()+" "+event.getEventComplexId()+"  "+ event.getEventTime()+" "+eventDate);
                        }
                        break;
                    }
                    if (isStowEvents == "N")
                    {
                        String unitRemark = unit.getFieldValue("unitRemark");
                        String createDate = unit.unitCreateTime.format('MM/dd/yyyy');
                        // Identifying the new containers inserted in TOS through stowplan
                        if ((unitRemark == "Stowplan Data")&& (createDate == formattedDate))
                        {
                            isStowEvents = "Y"
                        }
                    }
                }
            }

            if (isManifestEvents == "Y" && isStowEvents == "Y" )
            {
                isBothEvents = "Y"
            }
            return isBothEvents;
        }catch(Exception e){
            e.printStackTrace()
        }
    }
// Extracting the Manifest (Not in stowplan) contaner information required to show on the report
    public HashMap getEDIManifest(Object unit)
    {
        try{
            Date date = new Date();
            String formattedDate = date.format('MM/dd/yyyy')

            EventType evntType = EventType.findEventType("EDI_MANIFEST");
            EventManager eventManager = (EventManager)Roastery.getBean("eventManager");
            List events = eventManager.getEventHistory(evntType, unit);
            if (events.size() > 0 )
            {
                Iterator eventsList = events.iterator();
                while (eventsList.hasNext()) {
                    def event = eventsList.next();
                    String eventDate = event.getEventTime().format('MM/dd/yyyy')
                    if (eventDate == formattedDate )
                    {
                        //println("ManifestEvent ================ > " + event.getEventTypeId() + " "+event.getEventTypeDescription()+" "+event.getEventAppliedToNaturalKey()+" "+event.getEventComplexId()+"  "+ event.getEventTime()+" "+eventDate);

                        HashMap map = null;
                        try
                        {
                            map = new HashMap();

                            String cmdtyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                            String freightkind = unit.unitFreightKind.getKey();
                            def ds = getDs(freightkind,cmdtyId);

                            println("Discharge Port " + unit.unitId + " " + unit.getFieldValue("unitRouting.rtgPOD1.pointId"))

                            map.put("InboundCarrierId",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"));//Inbound Vessel
                            map.put("UnitNbr", unit.unitId);
                            map.put("UnitFlexString01", "EDI_MANIFEST");
                            map.put("UnitFlexString02", "The following Containers were in the Manifest but not the Stowplan");
                            map.put("UnitFlexString03",unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr"))//Booking Number
                            map.put("UnitFlexString04",ds)//DS
                            map.put("Destination",unit.getFieldValue("unitGoods.gdsDestination"))//Destination
                            map.put("GoodsConsigneeName",unit.getFieldValue("unitGoods.gdsConsigneeAsString"))//Consignee
                            map.put("CommodityDescription",unit.getFieldValue("unitGoods.gdsCommodity.cmdyId"));//Commodity
                            map.put("SealNbr1" , unit.getFieldValue("unitSealNbr1"));
                            map.put("SealNbr2" , unit.getFieldValue("unitSealNbr2"));
                            map.put("SealNbr3" , unit.getFieldValue("unitSealNbr3"));
                            map.put("SealNbr4" , unit.getFieldValue("unitSealNbr4"));
                            map.put("PositionSlot", unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));//Position Slot
                            map.put("GoodsBlNbr",unit.getFieldValue("unitGoods.gdsBlNbr"));//BL Number

                        }catch(Exception e){
                            e.printStackTrace();
                        }
                        return map;
                    }
                    break;
                }
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }
// Extracting the Stowplan (Not in Manifest) contaner information required to show on the report
    public HashMap getEDIStowplan(Object unit)
    {
        try{
            Date date = new Date();
            String formattedDate = date.format('MM/dd/yyyy')
            String isStowEvents = "N";
            String createDate = null;

            EventType evntType = EventType.findEventType("EDI_STOWPLAN");
            EventManager eventManager = (EventManager)Roastery.getBean("eventManager");
            List events = eventManager.getEventHistory(evntType, unit);
            //println("Stowplanevents.size()" + unit.unitId +" ==== "+events.size());
            if (events.size() > 0 )
            {
                Iterator eventsList = events.iterator();
                while (eventsList.hasNext()) {
                    def event = eventsList.next();
                    String eventDate = event.getEventTime().format('MM/dd/yyyy')
                    if (eventDate == formattedDate)
                    {
                        //println("StowPlanEvent ======================== > " + event.getEventTypeId() + " "+event.getEventTypeDescription()+" "+event.getEventAppliedToNaturalKey()+" "+event.getEventComplexId()+"  "+ event.getEventTime()+" "+eventDate);
                        isStowEvents = "Y"
                    }
                    break;
                }
            }

            String unitRemark = unit.getFieldValue("unitRemark");
            createDate = unit.unitCreateTime.format('MM/dd/yyyy');
            // Identifying the new containers inserted in TOS through stowplan
            if ((unitRemark == "Stowplan Data")&& (createDate == formattedDate))
            {
                isStowEvents = "Y"
            }

            if (isStowEvents == "Y")
            {
                HashMap map = null;
                try
                {
                    map = new HashMap();
                    String cmdtyId = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                    String freightkind = unit.unitFreightKind.getKey();//("unitFreightKind");
                    def ds = getDs(freightkind,cmdtyId);

                    println("Discharge Port " + unit.unitId + " " + unit.getFieldValue("unitRouting.rtgPOD1.pointId"))

                    map.put("InboundCarrierId",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"));//Inbound Vessel
                    map.put("UnitNbr", unit.unitId);
                    map.put("UnitFlexString01", "EDI_STOWPLAN");
                    map.put("UnitFlexString02", "The following Containers were in the Stowplan but not the Manifest");
                    map.put("UnitFlexString03",unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr"))//Booking Number
                    map.put("UnitFlexString04",ds)//DS
                    map.put("Destination",unit.getFieldValue("unitGoods.gdsDestination"))//Destination
                    map.put("GoodsConsigneeName",unit.getFieldValue("unitGoods.gdsConsigneeAsString"))//Consignee
                    map.put("CommodityDescription",unit.getFieldValue("unitGoods.gdsCommodity.cmdyId"));//Commodity
                    map.put("SealNbr1" , unit.getFieldValue("unitSealNbr1"));
                    map.put("SealNbr2" , unit.getFieldValue("unitSealNbr2"));
                    map.put("SealNbr3" , unit.getFieldValue("unitSealNbr3"));
                    map.put("SealNbr4" , unit.getFieldValue("unitSealNbr4"));
                    map.put("PositionSlot", unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));//Position Slot
                    map.put("GoodsBlNbr",unit.getFieldValue("unitGoods.gdsBlNbr"));//BL Number

                    //println(unit.getFieldValue("unitGoods.gdsBlNbr"));

                }catch(Exception e){
                    e.printStackTrace();
                }
                return map;
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }

// Retrieves DS Field-Based on freightkindKey,commodityId
    public String getDs(String freightkindKey,String cmdtyId)
    {
        def ds = ''
        if(cmdtyId.equals('AUTOCON')){
            ds = 'CON'
        }else if(cmdtyId.equals('AUTO')){
            ds = 'AUT'
        }else if (freightkindKey.equals('FCL') || cmdtyId.equals('AUTOCY')){
            ds = 'CY'
        }else{
            ds = '%'
        }
        return ds
    }

    public boolean execute(Map params)
    {
        try
        {
            println("GvyTestHistoryCall")
            inj = new GroovyInjectionBase();

            List unitList = null;
            ArrayList reportUnitList =  new ArrayList();
            unitList = getUnits()
            println("unitList.size()"+unitList.size());
            Iterator unitIterator = unitList.iterator();
            while(unitIterator.hasNext())
            {
                def unit = unitIterator.next();
                // Call the getEDIManifest amd getEDIStowplan only if containers are not listed in both.
                if (getEDIStowManifest(unit) == "N")
                {
                    HashMap unitsDataMap = getEDIManifest(unit);
                    if(unitsDataMap != null) {
                        //println("unitsDataMap"+unitsDataMap)
                        reportUnitList.add(unitsDataMap);
                    }

                    unitsDataMap = getEDIStowplan(unit);
                    if(unitsDataMap != null) {
                        reportUnitList.add(unitsDataMap);
                    }
                }
            }

            println("reportUnitList -------------------- :"+ (reportUnitList != null ? reportUnitList.size() : "EMPTY"))
            //Set Report Parameter
            if (reportUnitList.size() > 0) {
                HashMap parameters = new HashMap();

                //Create and Mail Report
                JRDataSource ds = new JRMapCollectionDataSource(reportUnitList);

                HashMap reportDesignsmap = new HashMap();
                reportDesignsmap.put("IMPORT_DISCREPANCIES",ds);

                def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                reportRunner.emailReports(reportDesignsmap,parameters, emailTo, VesVoy+" Import Discrepancies" ,"Attached report for Stowplan/Manifest Discrepancies");
                println("reportUnitList ------- Success")

            }
            else {
                def emailSender = inj.getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailFrom,emailTo, vesVoy+" Import Discrepancies","No Discrepancies reported");
                println("reportUnitList ------- No data to print")
            }

            def reeferReport = inj.getGroovyClassInstance("MatReportReefers");
            println("reeferReport"+reeferReport)
            reeferReport.generateReeferReport(unitList,VesVoy);

            def columbusReport = inj.getGroovyClassInstance("MatReportColumbusTotals");
            println("columbusReport")
            columbusReport.generateColumbusReport(unitList,vesVoy);

        }catch(Exception e){
            e.printStackTrace()
            println("printStackTrace ------- "+e)
        }
    }

    public boolean generateReport(Map params,String vesVoy)
    {
        try
        {
            println("GvyTestHistoryCall===>generateReport")
            inj = new GroovyInjectionBase();

            List unitList = null;

            ArrayList reportUnitList =  new ArrayList();

            unitList = getReportUnits(vesVoy)
            println("unitList.size()"+unitList.size());
            Iterator unitIterator = unitList.iterator();
            while(unitIterator.hasNext())
            {
                def unit = unitIterator.next();
                // Call the getEDIManifest amd getEDIStowplan only if containers are not listed in both.
                if (getEDIStowManifest(unit) == "N")
                {
                    HashMap unitsDataMap = getEDIManifest(unit);
                    if(unitsDataMap != null) {
                        //println("unitsDataMap"+unitsDataMap)
                        reportUnitList.add(unitsDataMap);
                    }

                    unitsDataMap = getEDIStowplan(unit);
                    if(unitsDataMap != null) {
                        reportUnitList.add(unitsDataMap);
                    }
                }
            }

            println("reportUnitList -------------------- :"+ (reportUnitList != null ? reportUnitList.size() : "EMPTY"))
            //Set Report Parameter
            if (reportUnitList.size() > 0) {
                HashMap parameters = new HashMap();

                //Create and Mail Report
                JRDataSource ds = new JRMapCollectionDataSource(reportUnitList);

                HashMap reportDesignsmap = new HashMap();
                reportDesignsmap.put("IMPORT_DISCREPANCIES",ds);

                def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                reportRunner.emailReports(reportDesignsmap,parameters, emailTo, vesVoy+" Import Discrepancies" ,"Attached report for Stowplan/Manifest Discrepancies");
                println("reportUnitList ------- Success")
            }
            else {

                def emailSender = inj.getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailFrom,emailTo, vesVoy+" Import Discrepancies","No Discrepancies reported");

                println("reportUnitList ------- No data to print")
            }

            def reeferReport = inj.getGroovyClassInstance("MatReportReefers");
            println("reeferReport"+reeferReport)
            reeferReport.generateReeferReport(unitList,vesVoy);


            def columbusReport = inj.getGroovyClassInstance("MatReportColumbusTotals");
            println("columbusReport")
            columbusReport.generateColumbusReport(unitList,vesVoy);

        }catch(Exception e){
            e.printStackTrace()
        }
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
            def id = "HZB323"//"HUG294" // This will be removed when it call automatically after Manifest/Stow process
            VesVoy = id;
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");

            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, id)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_POD_ID,"HON"));;
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