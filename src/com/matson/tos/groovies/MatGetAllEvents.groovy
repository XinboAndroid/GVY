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
import com.navis.services.ServicesField;
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

import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;


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
import java.util.HashMap

import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.LineOperator;

import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery
import com.navis.argo.ArgoField;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.argo.business.atoms.UnitCategoryEnum;
import com.navis.argo.business.atoms.FreightKindEnum;
import com.navis.services.business.rules.EventType;
import com.navis.services.business.event.Event;
import com.navis.services.business.api.EventManager
import com.navis.inventory.InventoryField;
import com.navis.inventory.business.units.UnitEquipment



/*
* Author : Raghu Iyer
* Date Written : 09/19/2012
* Description: This groovy is used to generate the Stowplan/Manifest Discrepancies after Stowplan and Manifest process
*/

public class MatGetAllEvents extends GroovyInjectionBase
{
    def inj = new GroovyInjectionBase();
    //String vessel = "MHI285";
    //def vesselGkey = "130043692";
    //String vessel = "MHI295";
    //def vesselGkey = "141859481";

    private final String emailTo = "1aktosdevteam@matson.com";
    private final String  emailFrom = '1aktosdevteam@matson.com';
    //public boolean execute(Map params)
    public void execute(String vessel, String vesselGkey)
    {
        String replaceVes = "V-"+vessel+"-";
        println("inside MatGetAllEvents.execute");
        def gvyEventUtil = inj.getGroovyClassInstance("GvyEventUtil");
        HashMap reportDesignsmap = new HashMap();
        String subject = vessel+" Last Container Discharge Report";
        String p2Subject = " ";
        String siSubject = " ";


        ArrayList unitRptList = new ArrayList();
        println("Started : MatGetAllEvents");
        try{
            List events = getAllDischEvents(vessel);
            ArrayList dischUnits = new ArrayList();
            Iterator dischItr = events.iterator();
            List acctList = null;
            def type = "DISCHARGE";
            HashMap outputMap = null;
            List resultAcctList = new ArrayList();
            while(dischItr.hasNext())
            {
                Event disch = dischItr.next();
                def creator = disch.evntCreator;
                if (creator == "-xps-"){
                    def unitId = disch.evntAppliedToNaturalKey;
                    def changes = disch.evntFieldChangesString;
                    DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                            .addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY,UnitCategoryEnum.THROUGH))
                            .addDqPredicate(PredicateFactory.like(UnitField.UFV_ARRIVE_POS_LOC_TYPE, "VESSEL"))
                            .addDqPredicate(PredicateFactory.like(UnitField.UFV_ARRIVE_POS_LOC_GKEY, vesselGkey))
                            .addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID,unitId))
                    //.addDqOrdering(Ordering.asc(UnitField.UFV_CMDTY));
                    acctList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
                    //println("dq===="+dq);
                    Iterator unitIter = acctList.iterator();
                    while(unitIter.hasNext()) {
                        def ufv = unitIter.next()
                        def unit = ufv.ufvUnit
                        outputMap = populateAcctListByType(ufv,type,vessel)
                        resultAcctList.add(outputMap)
                    }
                    dischUnits.add(disch);
                    //break;
                }
            }

            println ("resultAcctList.size  "+resultAcctList.size);
            JRDataSource reportDs = new JRMapCollectionDataSource(resultAcctList);
            def runReport = inj.getGroovyClassInstance("ReportRunner");

            //Set report parameters
            HashMap reportParameters = new HashMap();
            reportParameters.put("outboundVesVoy","MHI285");
            reportParameters.put("Date",new Date());
            // call report design of rehandle containers not loaded back to vessel report.
            def reportDesignName = "ACCT AUDIT DISCH REPORT";

            // Emailing report
            runReport.emailExcelReport(reportDs, reportParameters,reportDesignName ,"1aktosdevteam@matson.com","ACCT AUDIT DISCH REPORT-"+vessel,"ACCT AUDIT DISCH REPORT");


            println("dischUnits.size ::: " + dischUnits.size);

            ArrayList siUnits = new ArrayList();
            ArrayList p2Units = new ArrayList();

            Iterator itr = events.iterator();
            while(itr.hasNext())
            {
                Event disch = itr.next();
                def changes = disch.evntFieldChangesString;
                if (!changes.contains("P2")){
                    siUnits.add(disch);
                }
                if (changes.contains("P2")){
                    p2Units.add(disch);
                }
            }

            List siList = generateReport(siUnits,vessel);
            List p2List = generateReport(p2Units,vessel);

            HashMap parameters = new HashMap();
            Date startDate = new Date();
            String strDate = startDate.format('MM/dd/yyyy')
            parameters.put("Date",strDate);


            if (siList.size > 0)
            {
                JRDataSource ds = new JRMapCollectionDataSource(siList);
                reportDesignsmap.put("TEST REPORT",ds);
            }
            else
            {
                siSubject  = "Note : No SI Gate discharges found";
            }

            if (p2List.size > 0)
            {
                //Create and Mail Report
                JRDataSource ds1 = new JRMapCollectionDataSource(p2List);
                reportDesignsmap.put("TEST REPORT1",ds1);
            }
            else
            {
                p2Subject  = "Note : No P2 Gate discharges found";
            }
            subject = subject + "\n \n" + p2Subject + "\n \n" + siSubject;
            def reportRunner = inj.getGroovyClassInstance("ReportRunner");
            reportRunner.emailReports(reportDesignsmap, parameters,emailTo, "Last Container Discharge for-"+vessel ,subject);


        } catch (Exception e){
            println("Exception in getAllDischEvents "+e);
        }
    }

    public List generateReport(List events, String vessel)
    {
        def gvyEventUtil = inj.getGroovyClassInstance("GvyEventUtil");
        String replaceVes = "V-"+vessel+"-";
        ArrayList unitRptList = new ArrayList();

        try{
            Iterator itr = events.iterator();
            while(itr.hasNext())
            {
                Event disch = itr.next();
                def changes = disch.evntFieldChangesString;

                println ("1 :::"+disch.evntAppliedToNaturalKey+"::"+changes+"::"+disch.evntAppliedDate+"::"+disch.evntAppliedBy);
                changes = changes.replace(replaceVes,"")
                def lkpSlotValue = changes.indexOf("-")== -1 ? changes : changes.substring(0,changes.indexOf("-"));
                println("lkpSlotValue "+lkpSlotValue);
                HashMap map = new HashMap();
                def dischDate = disch.evntAppliedDate;

                map.put("UnitNbr", disch.evntAppliedToNaturalKey);
                map.put("PositionSlot", lkpSlotValue);
                map.put("OutBoundCarrierId", vessel);
                map.put("type", "OVERALL" );
                map.put("InTime", disch.evntAppliedDate);
                map.put("InTimeDate", disch.evntAppliedDate);

                unitRptList.add(map);

                break;
            }

            Iterator cyHon = events.iterator();
            while(cyHon.hasNext())
            {
                Event disch = cyHon.next();
                def changes = disch.evntFieldChangesString;
                String cyHonFlg = getCyHonUnit(disch.evntAppliedToNaturalKey,vessel);
                if (cyHonFlg == "Y"){
                    println ("2 :::"+disch.evntAppliedToNaturalKey+"::"+changes+"::"+disch.evntAppliedDate+"::"+disch.evntAppliedBy);
                    changes = changes.replace(replaceVes,"")
                    def lkpSlotValue = changes.indexOf("-")== -1 ? changes : changes.substring(0,changes.indexOf("-"));
                    println("lkpSlotValue "+lkpSlotValue);
                    HashMap map = new HashMap();
                    map.put("UnitNbr", disch.evntAppliedToNaturalKey);
                    map.put("PositionSlot", lkpSlotValue);
                    map.put("OutBoundCarrierId", vessel);
                    map.put("type", "CY-HON" );
                    map.put("InTime", disch.evntAppliedDate);
                    map.put("InTimeDate", disch.evntAppliedDate);

                    unitRptList.add(map);

                    break;
                }
            }

            Iterator auto = events.iterator();
            while(auto.hasNext())
            {
                Event disch = auto.next();
                def changes = disch.evntFieldChangesString;
                String autoFlg = getAutoUnit(disch.evntAppliedToNaturalKey,vessel);
                if (autoFlg == "Y"){
                    println ("3 :::"+disch.evntAppliedToNaturalKey+"::"+changes+"::"+disch.evntAppliedDate+"::"+disch.evntAppliedBy);
                    changes = changes.replace(replaceVes,"")
                    def lkpSlotValue = changes.indexOf("-")== -1 ? changes : changes.substring(0,changes.indexOf("-"));
                    println("lkpSlotValue "+lkpSlotValue);
                    HashMap map = new HashMap();
                    map.put("UnitNbr", disch.evntAppliedToNaturalKey);
                    map.put("PositionSlot", lkpSlotValue);
                    map.put("OutBoundCarrierId", vessel);
                    map.put("type", "AUTO" );
                    map.put("InTime", disch.evntAppliedDate);
                    map.put("InTimeDate", disch.evntAppliedDate);

                    unitRptList.add(map);

                    break;
                }
            }

            Iterator cy = events.iterator();
            while(cy.hasNext())
            {
                Event disch = cy.next();
                def changes = disch.evntFieldChangesString;
                String cyFlg = getCyUnit(disch.evntAppliedToNaturalKey,vessel);
                //println("Auto :::"+auto);
                if (cyFlg == "Y"){
                    println ("4 :::"+disch.evntAppliedToNaturalKey+"::"+changes+"::"+disch.evntAppliedDate+"::"+disch.evntAppliedBy);
                    changes = changes.replace(replaceVes,"")
                    def lkpSlotValue = changes.indexOf("-")== -1 ? changes : changes.substring(0,changes.indexOf("-"));
                    println("lkpSlotValue "+lkpSlotValue);
                    HashMap map = new HashMap();
                    map.put("UnitNbr", disch.evntAppliedToNaturalKey);
                    map.put("PositionSlot", lkpSlotValue);
                    map.put("OutBoundCarrierId", vessel);
                    map.put("type", "CY-OTHER" );
                    map.put("InTime", disch.evntAppliedDate);
                    map.put("InTimeDate", disch.evntAppliedDate);

                    unitRptList.add(map);

                    break;
                }
            }
            return unitRptList;
        } catch (Exception e){
            println("Exception in getAllDischEvents "+e);
        }
    }

    public boolean execute(Map params)
    {
        println("Started : MatGetAllEvents");
        try
        {
            try{
                def MatGetUpdatedConsignee = inj.getGroovyClassInstance("MatGetUpdatedConsignee");
                println("Calling MatGetUpdatedConsignee.generateReport()");
                MatGetUpdatedConsignee.generateReport();
            } catch (Exception e){
                println("Error while calling MatGetUpdatedConsignee");
            }
            HashMap map = null;
            ArrayList reportYBList =  new ArrayList();
            ArrayList reportGenTruckList =  new ArrayList();
            ArrayList reportSATList =  new ArrayList();
            ArrayList reportCONList =  new ArrayList();
            ArrayList reportCATList =  new ArrayList();
            ArrayList reportYBSERList =  new ArrayList();
            ArrayList reportMASList =  new ArrayList();
            ArrayList reportPORTList =  new ArrayList();
            ArrayList reportRECONList =  new ArrayList();
            ArrayList reportSitUnAssignList =  new ArrayList();
            List events = null;
            events = getAllEvents();

            Iterator eventsIterator = events.iterator();
            while(eventsIterator.hasNext())
            {
                Event event = eventsIterator.next();
                def changes = event.evntFieldChangesString;
                map = new HashMap();
                if (changes.contains("YB-")){

                    map.put("UnitFlexString06", event.evntAppliedToNaturalKey);
                    map.put("UnitFlexString01", event.evntEventType.evnttypeId);
                    map.put("UnitFlexString02", event.evntNote);
                    map.put("UnitFlexString03", event.evntAppliedBy);
                    map.put("UnitFlexString04", event.evntCreator);
                    map.put("UnitFlexString05", event.evntFieldChangesString);
                    map.put("UnitFlexString07", event.evntFacility.fcyId);
                    map.put("UfvFlexDate01", event.evntAppliedDate);
                    map.put("UfvFlexDate02", event.evntCreated);

                    if(map != null) {
                        reportYBList.add(map);
                    }
                }
                if (changes.contains("GEN_TRUCK")){

                    map.put("UnitFlexString06", event.evntAppliedToNaturalKey);
                    map.put("UnitFlexString01", event.evntEventType.evnttypeId);
                    map.put("UnitFlexString02", event.evntNote);
                    map.put("UnitFlexString03", event.evntAppliedBy);
                    map.put("UnitFlexString04", event.evntCreator);
                    map.put("UnitFlexString05", event.evntFieldChangesString);
                    map.put("UnitFlexString07", event.evntFacility.fcyId);
                    map.put("UfvFlexDate01", event.evntAppliedDate);
                    map.put("UfvFlexDate02", event.evntCreated);

                    if(map != null) {
                        reportGenTruckList.add(map);
                    }
                }
                if (changes.contains("YB_")){

                    map.put("UnitFlexString06", event.evntAppliedToNaturalKey);
                    map.put("UnitFlexString01", event.evntEventType.evnttypeId);
                    map.put("UnitFlexString02", event.evntNote);
                    map.put("UnitFlexString03", event.evntAppliedBy);
                    map.put("UnitFlexString04", event.evntCreator);
                    map.put("UnitFlexString05", event.evntFieldChangesString);
                    map.put("UnitFlexString07", event.evntFacility.fcyId);
                    map.put("UfvFlexDate01", event.evntAppliedDate);
                    map.put("UfvFlexDate02", event.evntCreated);

                    if(map != null) {
                        reportYBSERList.add(map);
                    }
                }
                if (changes.contains("SAT")){
                    map.put("UnitFlexString06", event.evntAppliedToNaturalKey);
                    map.put("UnitFlexString01", event.evntEventType.evnttypeId);
                    map.put("UnitFlexString02", event.evntNote);
                    map.put("UnitFlexString03", event.evntAppliedBy);
                    map.put("UnitFlexString04", event.evntCreator);
                    map.put("UnitFlexString05", event.evntFieldChangesString);
                    map.put("UnitFlexString07", event.evntFacility.fcyId);
                    map.put("UfvFlexDate01", event.evntAppliedDate);
                    map.put("UfvFlexDate02", event.evntCreated);

                    if(map != null) {
                        reportSATList.add(map);
                    }
                }

                if (changes.contains("UNAPPROVED VARIANCE") || changes.contains("REQUIRES")){
                    map.put("UnitFlexString06", event.evntAppliedToNaturalKey);
                    map.put("UnitFlexString01", event.evntEventType.evnttypeId);
                    map.put("UnitFlexString02", event.evntNote);
                    map.put("UnitFlexString03", event.evntAppliedBy);
                    map.put("UnitFlexString04", event.evntCreator);
                    map.put("UnitFlexString05", event.evntFieldChangesString);
                    map.put("UnitFlexString07", event.evntFacility.fcyId);
                    map.put("UfvFlexDate01", event.evntAppliedDate);
                    map.put("UfvFlexDate02", event.evntCreated);

                    if(map != null) {
                        reportCONList.add(map);
                    }
                }

                if (changes.contains("Import") || changes.contains("Export") || changes.contains("Transship") || changes.contains("Through") || changes.contains("Storage")){
                    map.put("UnitFlexString06", event.evntAppliedToNaturalKey);
                    map.put("UnitFlexString01", event.evntEventType.evnttypeId);
                    map.put("UnitFlexString02", event.evntNote);
                    map.put("UnitFlexString03", event.evntAppliedBy);
                    map.put("UnitFlexString04", event.evntCreator);
                    map.put("UnitFlexString05", event.evntFieldChangesString);
                    map.put("UnitFlexString07", event.evntFacility.fcyId);
                    map.put("UfvFlexDate01", event.evntAppliedDate);
                    map.put("UfvFlexDate02", event.evntCreated);

                    if(map != null) {
                        reportCATList.add(map);
                    }
                }

                if (changes.contains("Departed") || changes.contains("Active")){
                    map.put("UnitFlexString06", event.evntAppliedToNaturalKey);
                    map.put("UnitFlexString01", event.evntEventType.evnttypeId);
                    map.put("UnitFlexString02", event.evntNote);
                    map.put("UnitFlexString03", event.evntAppliedBy);
                    map.put("UnitFlexString04", event.evntCreator);
                    map.put("UnitFlexString05", event.evntFieldChangesString);
                    map.put("UnitFlexString07", event.evntFacility.fcyId);
                    map.put("UfvFlexDate01", event.evntAppliedDate);
                    map.put("UfvFlexDate02", event.evntCreated);

                    if(map != null) {
                        reportMASList.add(map);
                    }
                }

                if (changes.contains(ContextHelper.getThreadFacility().getFcyId()+"->") || changes.contains("->"+ContextHelper.getThreadFacility().getFcyId())){
                    map.put("UnitFlexString06", event.evntAppliedToNaturalKey);
                    map.put("UnitFlexString01", event.evntEventType.evnttypeId);
                    map.put("UnitFlexString02", event.evntNote);
                    map.put("UnitFlexString03", event.evntAppliedBy);
                    map.put("UnitFlexString04", event.evntCreator);
                    map.put("UnitFlexString05", event.evntFieldChangesString);
                    map.put("UnitFlexString07", event.evntFacility.fcyId);
                    map.put("UfvFlexDate01", event.evntAppliedDate);
                    map.put("UfvFlexDate02", event.evntCreated);

                    if(map != null) {
                        reportPORTList.add(map);
                    }
                }

                if (changes.contains("RECON")){
                    map.put("UnitFlexString06", event.evntAppliedToNaturalKey);
                    map.put("UnitFlexString01", event.evntEventType.evnttypeId);
                    map.put("UnitFlexString02", event.evntNote);
                    map.put("UnitFlexString03", event.evntAppliedBy);
                    map.put("UnitFlexString04", event.evntCreator);
                    map.put("UnitFlexString05", event.evntFieldChangesString);
                    map.put("UnitFlexString07", event.evntFacility.fcyId);
                    map.put("UfvFlexDate01", event.evntAppliedDate);
                    map.put("UfvFlexDate02", event.evntCreated);

                    if(map != null) {
                        reportRECONList.add(map);
                    }
                }

            }
            List sitUnAssign = null;
            sitUnAssign = getAllSitUnassignEvents();

            Iterator sitIterator = sitUnAssign.iterator();
            while(sitIterator.hasNext())
            {
                Event sits = sitIterator.next();
                map = new HashMap();

                map.put("UnitFlexString06", sits.evntAppliedToNaturalKey);
                map.put("UnitFlexString01", sits.evntEventType.evnttypeId);
                map.put("UnitFlexString02", sits.evntNote);
                map.put("UnitFlexString03", sits.evntAppliedBy);
                map.put("UnitFlexString04", sits.evntCreator);
                map.put("UnitFlexString05", sits.evntFieldChangesString);
                map.put("UnitFlexString07", sits.evntFacility.fcyId);
                map.put("UfvFlexDate01", sits.evntAppliedDate);
                map.put("UfvFlexDate02", sits.evntCreated);

                if(map != null) {
                    reportSitUnAssignList.add(map);
                }
            }

            HashMap parameters = new HashMap();
            HashMap reportDesignsmap = new HashMap();
            String printRpt = "N";
            println("reportYBList.size() "+reportYBList.size());
            println("reportGenTruckList.size() "+reportGenTruckList.size());
            println("reportYBSERList.size() "+reportYBSERList.size());
            println("reportSATList.size() "+reportSATList.size());
            println("reportCONList.size() "+reportCONList.size());
            println("reportCATList.size() "+reportCATList.size());
            println("reportMASList.size() "+reportMASList.size());
            println("reportPORTList.size() "+reportPORTList.size());
            println("reportRECONList.size() "+reportRECONList.size());
            println("reportSitUnAssignList.size() "+reportSitUnAssignList.size());
            if (reportYBList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsYb = new JRMapCollectionDataSource(reportYBList);
                reportDesignsmap.put("ALL EVENTS YB",dsYb);
                printRpt = "Y";
            }
            if (reportGenTruckList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsGt = new JRMapCollectionDataSource(reportGenTruckList);
                reportDesignsmap.put("ALL EVENTS GEN TRUCK",dsGt);
                printRpt = "Y";
            }
            if (reportYBSERList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsYbSer = new JRMapCollectionDataSource(reportYBSERList);
                reportDesignsmap.put("ALL EVENTS YB_SER",dsYbSer);
                printRpt = "Y";
            }
            if (reportSATList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsSat = new JRMapCollectionDataSource(reportSATList);
                reportDesignsmap.put("ALL EVENTS SAT",dsSat);
                printRpt = "Y";
            }
            if (reportCONList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsCon = new JRMapCollectionDataSource(reportCONList);
                reportDesignsmap.put("ALL EVENTS CON",dsCon);
                printRpt = "Y";
            }
            if (reportCATList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsCat = new JRMapCollectionDataSource(reportCATList);
                reportDesignsmap.put("ALL EVENTS CATEGORY",dsCat);
                printRpt = "Y";
            }
            if (reportMASList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsMas = new JRMapCollectionDataSource(reportMASList);
                reportDesignsmap.put("ALL EVENTS MASTER",dsMas);
                printRpt = "Y";
            }
            if (reportPORTList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsPort = new JRMapCollectionDataSource(reportPORTList);
                reportDesignsmap.put("ALL EVENTS ROUTING",dsPort);
                printRpt = "Y";
            }
            if (reportRECONList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsRecon = new JRMapCollectionDataSource(reportRECONList);
                reportDesignsmap.put("ALL EVENTS RECON",dsRecon);
                printRpt = "Y";
            }
            if (reportSitUnAssignList.size() > 0)
            {
                //Create and Mail Report
                JRDataSource dsSit = new JRMapCollectionDataSource(reportSitUnAssignList);
                reportDesignsmap.put("ALL EVENTS SIT UNASSIGN",dsSit);
                printRpt = "Y";
            }
            if ("Y".equals(printRpt)){
                try
                {
                    def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                    reportRunner.emailXLSReports(reportDesignsmap,parameters, emailTo, "All Events - UNIT_SNX_UPDATE","Attached All events for UNIT_SNX_UPDATE ");
                    println("reportYBList ------- Success")
                }catch (Exception e){
                    println("No design "+ e);
                }
            }
            else {
                def emailSender = inj.getGroovyClassInstance("EmailSender")
                emailSender.custSendEmail(emailFrom,emailTo, "ALL EVENTS","No Changes are reported");
                println("reportYBList ------- No data to print")
            }

        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }

    private List getAllEvents()
    {

        try{

            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.HOUR_OF_DAY, -3);
            Date startDate = cal.getTime();
            println("startDate "+ startDate);


            DomainQuery dq = QueryUtils.createDomainQuery("Event")
                    .addDqPredicate(PredicateFactory.ge(ServicesField.EVNT_APPLIED_DATE, startDate))
            //.addDqPredicate(PredicateFactory.le(ServicesField.EVNT_APPLIED_DATE, endDate))
                    .addDqPredicate(PredicateFactory.like(ServicesField.EVNT_NOTE, "Sup%"))
                    .addDqPredicate(PredicateFactory.eq(ServicesField.EVNT_EVENT_TYPE, "3708255"))
                    .addDqOrdering(Ordering.desc(ServicesField.EVNT_APPLIED_DATE));
            //.addDqPredicate(PredicateFactory.eq(ServicesField.EVNT_APPLIED_TO_NATURAL_KEY, "TRIU8585980"));

            println (dq);

            //dq.setMaxResults(1);
            List events = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println ("events.size()" + events.size());

            return events;

        } catch (Exception e){
            println("Error while getting events:::"+e)
        }
    }

    private List getAllSitUnassignEvents()
    {

        try{

            Calendar cal = Calendar.getInstance();
            cal.setTime(new Date());
            cal.add(Calendar.HOUR_OF_DAY, -3);
            Date startDate = cal.getTime();
            println("startDate "+ startDate);

            DomainQuery dq = QueryUtils.createDomainQuery("Event")
                    .addDqPredicate(PredicateFactory.ge(ServicesField.EVNT_APPLIED_DATE, startDate))
                    .addDqPredicate(PredicateFactory.eq(ServicesField.EVNT_EVENT_TYPE, "3053232"))
            //.addDqPredicate(PredicateFactory.eq(ServicesField.EVNT_APPLIED_TO_NATURAL_KEY, "MATU3701946"))
                    .addDqOrdering(Ordering.desc(ServicesField.EVNT_APPLIED_DATE));


            println (dq);

            //dq.setMaxResults(1);
            List events = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println ("events.size()" + events.size());

            return events;

        } catch (Exception e){
            println("Error while getting events:::"+e)
        }
    }

    private List getAllDischEvents(String vessel)
    {
        try{
            Date startDate = new Date() -8;
            Date endDate = startDate + 4;
            String trimDate = startDate.format('yyyy-MM-dd')
            startDate = startDate.parse('yyyy-MM-dd', trimDate);
            ArrayList eventList = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("Event")
                    .addDqPredicate(PredicateFactory.ge(ServicesField.EVNT_APPLIED_DATE, startDate))
                    .addDqPredicate(PredicateFactory.eq(ServicesField.EVNT_EVENT_TYPE, "2753837"))
                    .addDqOrdering(Ordering.desc(ServicesField.EVNT_APPLIED_DATE));
            //.addDqPredicate(PredicateFactory.eq(ServicesField.EVNT_APPLIED_TO_NATURAL_KEY, "MATU3701946"));

            println (dq);

            //dq.setMaxResults(1);
            List events = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println ("events.size()" + events.size());

            Iterator itr = events.iterator();
            while(itr.hasNext())
            {
                Event disch = itr.next();
                def changes = disch.evntFieldChangesString;
                if (changes.contains(vessel)){
                    eventList.add(disch);
                }
            }
            println("eventList ::"+eventList.size());
            return eventList;

        } catch (Exception e){
            println("Error while getting events:::"+e)
        }
    }

    public String getAutoUnit(String containerId, String vessel)
    {

        try {
            String auto = "N";

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID,containerId));
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID,vessel));
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if (unitList.size() > 0)
            {
                Iterator iterUnitList = unitList.iterator()
                while (iterUnitList.hasNext())
                {
                    def ufv = iterUnitList.next();
                    def unit = ufv.ufvUnit;
                    def commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                    commodity = commodity != null ? commodity : "NO"
                    //println("commodity :::"+ commodity);
                    if (commodity.contains("AUTO")){
                        auto = "Y";
                        break;
                    }

                }
            }
            return auto;
        }catch (Exception e){
            println("Error "+ e);
        }
    }

    public String getCyHonUnit(String containerId , String vessel)
    {

        try {
            String cyHon = "N";

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID,containerId));
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID,vessel));
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if (unitList.size() > 0)
            {
                Iterator iterUnitList = unitList.iterator()
                while (iterUnitList.hasNext())
                {
                    def ufv = iterUnitList.next();
                    def unit = ufv.ufvUnit;
                    def commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                    commodity = commodity != null ? commodity : "NO"
                    def pod = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
                    //println("commodity :::"+ commodity);
                    if (!commodity.contains("AUTO") && pod == ContextHelper.getThreadFacility().getFcyId()){
                        cyHon = "Y";
                        break;
                    }

                }
            }
            return cyHon;
        }catch (Exception e){
            println("Error "+ e);
        }
    }

    public String getCyUnit(String containerId , String vessel)
    {

        try {
            String cy = "N";

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID,containerId));
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID,vessel));
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if (unitList.size() > 0)
            {
                Iterator iterUnitList = unitList.iterator()
                while (iterUnitList.hasNext())
                {
                    def ufv = iterUnitList.next();
                    def unit = ufv.ufvUnit;
                    def commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
                    commodity = commodity != null ? commodity : "NO"
                    def pod = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
                    if (!commodity.contains("AUTO") && pod != ContextHelper.getThreadFacility().getFcyId()){
                        cy = "Y";
                        break;
                    }

                }
            }
            return cy;
        }catch (Exception e){
            println("Error "+ e);
        }
    }

    public HashMap populateAcctListByType(UnitFacilityVisit ufv, String type, String vessel) {

        HashMap resMap = new HashMap();
        def unit = ufv.ufvUnit
        UnitEquipment chasEquip = unit.getUnitCarriageUe();
        def freightkindout = unit.getFieldValue("unitFreightKind").getKey();
        def attachedUnit = unit.getFieldValue("unitAttachedEquipIds")
        if ("FCL".equals(freightkindout))
        {
            freightkindout = "F";
        } else if ("MTY".equals(freightkindout))
        {
            freightkindout = "E";
        }

        resMap.put("Commodity",unit.getFieldValue("unitGoods.gdsCommodity.cmdyId"));
        resMap.put("EquipmentTypeClass",unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass").getKey());
        resMap.put("EquipmentType",unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId"))
        resMap.put("FreightKind",freightkindout)
        resMap.put("UnitNbr",unit.getFieldValue("unitId"))
        if ("DISCHARGE".equals(type)) {
            def commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
            commodity = commodity != null && commodity == "AUTO" ? "AUTO" : " ";
            resMap.put("Commodity",commodity);
        }
        if (attachedUnit != null && chasEquip !=null)
        {
            resMap.put("AttachedUnits",unit.getFieldValue("unitAttachedEquipIds"))
        } else if (attachedUnit != null && chasEquip == null)
        {
            resMap.put("AttachedUnits",null)
        }

        if ("DISCHARGE".equals(type)) {
            resMap.put("InboundCarrierATA",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvATA"))
            resMap.put("InboundCarrierATD",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvATD"))
            resMap.put("InboundCarrierId",vessel)
            resMap.put("UfvFlexString06",unit.getFieldValue("unitActiveUfv.ufvFlexString06"))
        }
        return resMap;
    }

}

