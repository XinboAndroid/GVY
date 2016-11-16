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

import com.navis.services.business.event.GroovyEvent;
import net.sf.jasperreports.engine.JRDataSource;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;

import com.navis.road.RoadField;
import com.navis.argo.business.reference.LineOperator;

import com.navis.argo.ArgoConfig;
import com.navis.argo.ArgoPropertyKeys;
import com.navis.argo.ArgoRefField;
import com.navis.argo.business.reference.AgentRepresentation;
import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.business.reference.Agent;
import com.navis.argo.business.reference.Shipper;
import com.navis.argo.business.reference.ScopedBizUnit;
import java.sql.Connection;

/*
* Author : Raghu Iyer
* Date Written : 01/07/2013
* Description: This groovy is used to generate the Stowplan/Manifest Discrepancies after Stowplan and Manifest process
*/

public class MatGetUpdatedConsignee extends GroovyInjectionBase
{
    public boolean execute(Map params)
    {
        def inj = new GroovyInjectionBase();
        //private final String emailTo = "1aktosdevteam@matson.com";
        //private final String emailTo = "1aktosdevteam@matson.com";
        String emailTo = "1aktosdevteam@matson.com";
        String  emailFrom = '1aktosdevteam@matson.com';
        Connection conn;
        try
        {
            HashMap map = null;
            ArrayList reportUnitList =  new ArrayList();
            List shipperList = null;
            List unitList = null;
            shipperList = getConsigneeChangedYesterday();
            println("End : MatUpdateShipperNotes");


            if(shipperList != null) {
                Iterator iter = shipperList.iterator();
                while(iter.hasNext()) {
                    def shipper = iter.next();
                    String shipperId = shipper.bzuId;
                    def GvyRefDataLookup = inj.getGroovyClassInstance("GvyRefDataLookup");
                    //conn = GvyRefDataLookup.connect();
                    //def orgnConsignee = GvyRefDataLookup.getOrgnlConsignee(shipperId);
                    def orgnConsignee = null;
                    unitList = getUnits(shipperId);
                    if(unitList != null) {
                        Iterator unitIter = unitList.iterator();
                        while(unitIter.hasNext()) {
                            def ufv = unitIter.next();
                            def unit = ufv.ufvUnit;
                            map = new HashMap();

                            map.put("UnitNbr", unit.unitId);
                            map.put("UnitFlexString01", shipperId);
                            map.put("UnitFlexString02", shipper.bzuName);
                            map.put("UnitFlexString03", orgnConsignee);
                            map.put("UnitFlexString04", shipper.bzuChanger);
                            map.put("UfvFlexDate01", shipper.bzuChanged);
                            map.put("UfvFlexDate02", unit.getFieldValue("unitActiveUfv.ufvTimeOfLastMove"));
                            //println("<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>"+unit.getFieldValue("unitActiveUfv.ufvTimeOfLastMove"));
                            if(map != null) {
                                reportUnitList.add(map);
                            }
                        }
                    }
                    break;
                }
            }

            println("reportUnitList.size() "+reportUnitList.size());
            if (reportUnitList.size() > 0)
            {
                HashMap parameters = new HashMap();

                //Create and Mail Report
                JRDataSource ds = new JRMapCollectionDataSource(reportUnitList);

                HashMap reportDesignsmap = new HashMap();
                reportDesignsmap.put("CONSIGNEE CHANGE",ds);
                try
                {
                    def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                    reportRunner.emailExcelReport(ds,parameters, "CONSIGNEE CHANGE",emailTo, "Consignee Changes" ,"Attached consignee changed yesterday report ");
                    println("reportUnitList ------- Success")
                }catch (Exception e){
                    println("No design");
                }
            }
            else {
                def emailSender = inj.getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailFrom,emailTo, "CONSIGNEE CHANGE","No Changes are reported");
                println("reportUnitList ------- No data to print")
            }

        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }

    public void generateReport()
    {
        def inj = new GroovyInjectionBase();
        //private final String emailTo = "1aktosdevteam@matson.com";
        //private final String emailTo = "1aktosdevteam@matson.com;";
        String emailTo = "1aktosdevteam@matson.com";
        String  emailFrom = '1aktosdevteam@matson.com';
        Connection conn;
        try
        {
            HashMap map = null;
            HashMap shipperMap = null;
            ArrayList reportUnitList =  new ArrayList();
            ArrayList reportShipperList =  new ArrayList();
            List shipperList = null;
            List unitList = null;
            shipperList = getConsigneeChangedYesterday();
            println("End : MatUpdateShipperNotes");


            if(shipperList != null) {
                Iterator iter = shipperList.iterator();
                while(iter.hasNext()) {
                    shipperMap = new HashMap();
                    def shipper = iter.next();
                    String shipperId = shipper.bzuId;
                    def GvyRefDataLookup = inj.getGroovyClassInstance("GvyRefDataLookup");
                    //conn = GvyRefDataLookup.connect();
                    //println("Connection ::"+conn);
                    //def orgnConsignee = GvyRefDataLookup.getOrgnlConsignee(shipperId);
                    def orgnConsignee = null;
                    shipperMap.put("UnitFlexString01", shipperId);
                    shipperMap.put("UnitFlexString02", shipper.bzuName);
                    shipperMap.put("UnitFlexString03", orgnConsignee);
                    shipperMap.put("UnitFlexString04", shipper.bzuChanger);
                    shipperMap.put("UfvFlexDate01", shipper.bzuChanged);
                    shipperMap.put("UnitFlexString05", shipper.bzuCreator);
                    shipperMap.put("UfvFlexDate03", shipper.bzuCreated);
                    if(shipperMap != null) {
                        reportShipperList.add(shipperMap);
                    }

                    /*unitList = getUnits(shipperId);
                    if(unitList != null) {
                        Iterator unitIter = unitList.iterator();
                        while(unitIter.hasNext()) {
                            def ufv = unitIter.next();
                            def unit = ufv.ufvUnit;
                            map = new HashMap();
                            def tranState = unit.getFieldValue("unitActiveUfv.ufvTransitState");
                            tranState = tranState != null ? tranState.getKey():null;
                            //println("tranState ::::"+tranState);
                            map.put("UnitNbr", unit.unitId);
                            map.put("UnitFlexString01", shipperId);
                            map.put("UnitFlexString02", shipper.bzuName);
                            map.put("UnitFlexString03", orgnConsignee);
                            map.put("UnitFlexString04", shipper.bzuChanger);
                            map.put("UfvFlexDate01", shipper.bzuChanged);
                            map.put("UfvFlexDate02", unit.getFieldValue("unitActiveUfv.ufvTimeOfLastMove"));
                            map.put("UnitFlexString05", tranState);
                            //println("<<<<<<<<<<<<>>>>>>>>>>>>>>>>>>>>>"+unit.getFieldValue("unitActiveUfv.ufvTimeOfLastMove"));
                            if(map != null) {
                                reportUnitList.add(map);
                            }
                        }
                    }*/
                    //break;
                }
            }

            println("reportUnitList.size() "+reportUnitList.size());
            println("reportShipperList.size() "+reportShipperList.size());
            HashMap parameters = new HashMap();
            HashMap reportDesignsmap = new HashMap();
            String printRpt = "N";

            if (reportUnitList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsUnit = new JRMapCollectionDataSource(reportUnitList);
                reportDesignsmap.put("CONSIGNEE CHANGE UNIT",dsUnit);
                printRpt = "Y";
            }
            if (reportShipperList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsShip = new JRMapCollectionDataSource(reportShipperList);
                reportDesignsmap.put("CONSIGNEE CHANGE",dsShip);
                printRpt = "Y";
            }

            if ("Y".equals(printRpt)){
                try
                {
                    def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                    reportRunner.emailXLSReports(reportDesignsmap,parameters, emailTo, "Consignee Update","Attached consignee changed yesterday report");
                    println("Consignee Change ------- Success")
                }catch (Exception e){
                    println("No design");
                }
            }
            else {
                def emailSender = inj.getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailFrom,emailTo, "Consignee Update","No Changes are reported");
                println("Consignee Change ------- No data to print")
            }

        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }//

    private List getConsigneeChangedYesterday()
    {
        //Date startDate = new Date() -1;
        /*String trimDate = startDate.format('yyyy-MM-dd')
        startDate = startDate.parse('yyyy-MM-dd', trimDate);
        println("startDate "+ startDate);*/

        Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.HOUR_OF_DAY, -12);
        Date startDate = cal.getTime();
        println("startDate "+ startDate);

        def shipperName = null;
        List shipperListFinal = null;
        DomainQuery dq = QueryUtils.createDomainQuery("Shipper").addDqPredicate(PredicateFactory.ge(ArgoRefField.BZU_CHANGED,startDate));
        println(dq);
        List shipperList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        println("shipperList.size()"+shipperList.size());
        if (shipperList.size() > 0){
            Iterator iterShipper = shipperList.iterator();
            while(iterShipper.hasNext()) {
                def shipper = iterShipper.next();
                shipperName = shipper.bzuName;
                //println("Shippers change yesterday : "+shipperName +"<<>>"+shipper.bzuId+"<<>>"+shipper.bzuGkey+"<<>>"+shipper.bzuChanged+"<<>>"+shipper.bzuChanger);
                //break;
            }
        }
        return shipperList;
    }

    public List getUnits(String shipperId)
    {
        Date startDate = new Date() - 12;
        //String trimDate = startDate.format('yyyy-MM-dd')
        //startDate = startDate.parse('yyyy-MM-dd', trimDate);
        //Date endDate = startDate + 1;
        //println("startDate "+ startDate);
        //println("endDate "+ endDate);


        /*Calendar cal = Calendar.getInstance();
        cal.setTime(new Date());
        cal.add(Calendar.HOUR_OF_DAY, -5);
        Date startDate = cal.getTime();
        println("startDate "+ startDate);*/

        try {
            ArrayList units = new ArrayList();
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq = dq.addDqPredicate(PredicateFactory.ge(UnitField.UFV_TIME_OUT,startDate))
            //.addDqPredicate(PredicateFactory.le(UnitField.UFV_TIME_OUT,endDate))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_GDS_CONSIGNEE_ID,shipperId));

            //println(dq);
            List ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            return ufvList;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }
}