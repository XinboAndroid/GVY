/*
*  A1   KM   02/10/2011     Initial groovyPlugins for vessel (long haul/barges) discharge audit
*                           Sends out Client discharge report
*  A2   GR                  Last container discharge report
*  A3   PS                  Sends out confirmation notice that discharge audit is complete
*  A4   GR                  Altered Query last DFV Rpt(no through cntr,no mty cntr,no pier29 cntr)
                            changed query to lookup on actual IB visit
*  A5   GR                  Changed Obcarrier to vessel visit IBvesvoy
*  A6   GR                  Check line operator split
*  A7   GR   05/02/11       Made Code Generic to Handel Load and Discharge function
*  A8   GR   05/02/11       For Load units change sorting by timeOut
*  A9   GR   05/02/11       For Load units change sort by last moved
*  A10  GR   05/04/11       Set Client unit Timezone, Hardcode HON as Facilty, Status=FreightKind
*  A11  GR   05/20/11       Adding rob,dfv COUNTS
*  A12  GR   05/23/11       OnDeck, Below Deck addition
*  A13  GR   05/24/11       Defect picking up Retired unit. Added Filter
*  A14  GR   05/25/11       Check Nullpointer Exception
*  A15  GR   05/26/11       FIX Replaced TimeIn with UfvFlexDate4 a)Correct Discharge b) Client Restow unit
*  A16  GR   06/15/11       Filter out Through Contianers from Client Cntr report
*  A17  GR   06/28/11       Trailer Report to Accouting
*  A18  KM   07/08/11       Split email audit pau and trailer report
*  A19	GR   07/12/11       Set OBcarrier for Client Reports
*  A20  KM   09/12/11       Updated prod emails addresses
* 08/16/11 2.1 Updated Email Method
* 08/14/2013 Fix the ROB list count to include all THROUGHs, remove the DEPARTED or LOADED condition
* 08/16/2013 Add filter to query for advised, retired
* 08/20/2013 Add filters for Discharge, fix ROB list count
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


public class GvyAuditDischargeCompleteTestUnits extends GroovyInjectionBase
{
    private String outBoundCarrierId = null
    def inj = null;
    def gvyEventUtil = null;
    HashMap mapDisplayLst = new HashMap(); //A2
    def reportType = null;
    def timezone = null;
    List totalUnitList = null;
    private static final String eol = "\r\n";
    def rptFieldSortUtil = null;
    def event = null;
    HashMap mapTrailer = new HashMap();
    def emailTo = '1TOSDevTeamHONProd@gmail.com'  //A20
    def acctEmail = '1TOSDevTeamHONProd@gmail.com'
    def trailerCnt = 0;
    def vesselForDischId = null;

    public String checkForMultipleDischarges(){
        //1. Check if unit has Multiple discharge events
        //2. if Multiple discharge then drop out of list
        //3. resort for last discharge value
        //4. Return last disch unit
    }

    public String checkForRestowUnit(){
        //1. check if its a restow unit then drop it out of the list
    }

    public void init(){
        inj = new GroovyInjectionBase();
        gvyEventUtil = gvyEventUtil == null ? inj.getGroovyClassInstance("GvyEventUtil") : gvyEventUtil ;
        timezone = ContextHelper.getThreadUserTimezone();
    }

    public boolean processClientDischarge(Object eventObj, String rptType)
    {
        //1. lookup all units on Board
        //2. Fetch and Map information
        //3. Generate Report
        init() // Initialize Global variable

        try
        {
            def visit = eventObj.getEntity();
            reportType = rptType;
            event = eventObj

            //getEmail Grp List
            def nextFacility = visit.getFieldValue("cvdCv.cvNextFacility.fcyId")
            def facility = visit.getFieldValue("cvdCv.cvFacility.fcyId")

            //outBoundCarrierId =  visit.cvdCv
            outBoundCarrierId = visit.vvdVessel.vesId+visit.getFieldValue("vvdObVygNbr") //A5
            //if outbound carrier is YB just return   check for client vessels if yes filter out client service
            if(outBoundCarrierId.startsWith('YB')){
                return null;
            }
            // A1 need to find method of discharge units
            List dfvUnits = findAllClientUnits(visit, rptType)
            //println("dfv Unit list "+dfvUnits)
            // A2 need to find on client units
            if(dfvUnits != null && dfvUnits.size() > 0){
                List sortDfvUnits = sortUnitsByLineOp(dfvUnits)
                //println("sort dfv Unit list "+sortDfvUnits)
                // A3 group line operators togethers
                // A4 add report creation and emailing spcecific to line operator
                procClientCntr(sortDfvUnits)
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    /*
    * Method finds all NON-MAT Container and Add the cntrs to a list
    * Returns a List of containers
    */
    public List findAllClientUnits(Object vesVisit, String rptType)
    {
        ArrayList vesVistUnitLists = new ArrayList();
        try{
            Long lineOpGkey = LineOperator.findLineOperatorById("MAT").bzuGkey
            //Long cvGkey = vesVisit.getCvdCv().getCvGkey()
            def id = vesVisit.getCvdCv().getCvId()
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            if('DISCHARGE'.equals(rptType)){
                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, id));
            }else if ('LOAD'.equals(rptType)){
                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID, id));
            }
            dq = dq.addDqPredicate(PredicateFactory.ne(UnitField.UFV_LINE_OPERATOR_GKEY, lineOpGkey)).addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.THROUGH)).addDqOrdering(Ordering.asc(UnitField.UFV_LINE_OPERATOR_GKEY));  //A17
            List vesVistUnits  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("dq ===="+dq+" unitsList ===="+(vesVistUnits != null ? vesVistUnits.size() : "NO RESULT"));

            if('DISCHARGE'.equals(rptType)){
                vesVistUnitLists.addAll(vesVistUnits);
            } else if ('LOAD'.equals(rptType)){
                Iterator iter = vesVistUnits.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def transitState =  ufv.getUfvTransitState()
                    if (transitState != null) {
                        transitState = transitState != null ? transitState.getKey() : ''
                        def tState = transitState.split("_")
                        transitState = tState[1]
                    }

                    if (transitState != null && ("DEPARTED".equals(transitState) || "LOADED".equals(transitState) ))
                    {
                        vesVistUnitLists.add(ufv)
                    }
                }

            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return vesVistUnitLists
    }

    //1.Maps unit Data to report file attribute
    public HashMap populateUnitData(UnitFacilityVisit ufv)
    {
        def unit = ufv.ufvUnit
        def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
        if(ufv.ufvTimeIn == null){ //A14
            return  null;  // Would be a Through unit
        }

        HashMap map = null;
        try
        {
            map = new HashMap();
            map.put("UnitNbr", unit.getFieldValue("unitId"));
            map.put("PositionSlot", unit.unitFreightKind.name);
            map.put("POD", unit.getFieldValue("unitRouting.rtgPOD1.pointId"));
            map.put("POL", unit.getFieldValue("unitRouting.rtgPOL.pointId"));
            map.put("EquipmentType", unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId"));
            map.put("OutBoundCarrierId", outBoundCarrierId);
            map.put("LineOperator",unit.getFieldValue("unitLineOperator.bzuId"));
            map.put("Destination",unit.getFieldValue("unitGoods.gdsDestination"));
            //A8
            if('DISCHARGE'.equals(reportType)){
                map.put("InTime",ContextHelper.formatTimestamp(ufv.ufvTimeIn, timezone));
            }else if ('LOAD'.equals(reportType)){
                map.put("InTime",ContextHelper.formatTimestamp(ufv.ufvTimeOfLastMove, timezone));
            }

        }catch(Exception e){
            e.printStackTrace();
        }
        return map;
    }
    /*
    * Method sorts NON-MAT Container by Line Operator and created Individial list of each LineOperator
    * Returns List of Individual LineOperator List (example: ListOne=ALL MAE,ListTwo=ALL HCL,ListThree=ALL APL)
    */
    public List sortUnitsByLineOp(List unitList)
    {
        ArrayList listAllLineOp = new ArrayList();
        try{
            def aUfv = null;
            def prevLineOp = null;
            ArrayList listPerLineOp = null;
            Iterator itUnitList = unitList.iterator();
            while(itUnitList.hasNext()){
                aUfv = itUnitList.next();
                if(aUfv.ufvUnit.unitLineOperator.bzuId.equals(prevLineOp)){
                    listPerLineOp.add(aUfv);
                }else{
                    if(prevLineOp !=null){
                        listAllLineOp.add(listPerLineOp)
                    }
                    //First time it ill come here and createObject - Initialize new Variable
                    listPerLineOp = new ArrayList();
                    //Set new Variable
                    listPerLineOp.add(aUfv);
                } //Else Ends
                prevLineOp = aUfv.ufvUnit.unitLineOperator.bzuId
            } //While Ends
            //Add last loop values - Just incase there was only one unit and it went into the else loop
            listAllLineOp.add(listPerLineOp)
        }catch(Exception e){
            e.printStackTrace();
        }
        return listAllLineOp
    }
    /*
    * Method a) Reads Each Individual LineOperator conatiner list
    * b) Populates Report data
      c) Gets Line Operator contact Information
      d) Calls Ireport code and mails report to specific LineOperator Email
    */
    public void procClientCntr(List list){
        try{
            Iterator itUnitList = list.iterator();
            def aUfv  = null;
            while(itUnitList.hasNext()){
                def contactName = null;
                HashMap fmtMap = null;
                def contactEmail = "1TOSDevTeamHONProd@gmail.com";
                ArrayList rptUnitList = new ArrayList();
                ArrayList perLinOplist = itUnitList.next();
                Iterator  itperLinOp = perLinOplist.iterator();
                while(itperLinOp.hasNext()){
                    aUfv = itperLinOp.next();
                    //println("unitId="+aUfv.ufvUnit.unitId+"    LineOP="+aUfv.ufvUnit.unitLineOperator.bzuId)
                    //Populate Report Data
                    fmtMap = populateUnitData(aUfv)
                    if(fmtMap != null){
                        rptUnitList.add(fmtMap)
                    }
                }//Inner While Ends

                //Select LineOperator Contact Information
                if(aUfv != null){
                    def lineOperator = aUfv.ufvUnit.unitLineOperator
                    def contactInfo = lineOperator.bzuCtct
                    if(contactInfo != null){
                        contactName = contactInfo.ctctName
                        contactEmail = contactInfo.ctctEmailAddress
                    }//Outer While ends

                    //Call IReport Generation Code Here
                    processRpt(rptUnitList, contactName, contactEmail)
                    //println("--------------------------------------------------")
                    //println("--------------------------------------------------")
                }// If Ends
            } //While Ends
        }catch(Exception e){
            e.printStackTrace()
        }
    }
    public void processRpt(List unitList, String contactName, String contactEmail){
        try{
            println("unitList :"+ (unitList != null ? unitList.size() : "EMPTY"))

            //Set Report Parameter
            def reportDesignName = null;
            def displayType = null;
            HashMap parameters = new HashMap();
            HashMap parametersUnits = new HashMap();
            String strDate = ContextHelper.formatTimestamp(new Date(), timezone)

            //println("Event Time ::"+event.getEvent().getEventTime()+"    TimeZone:"+ContextHelper.getThreadUserTimezone()+"  strDate ::"+strDate)
            parameters.put("Date",strDate);
            parameters.put("recordCount",(unitList != null ? unitList.size() : 0))

            //A7
            if("DISCHARGE".equals(reportType)){
                reportDesignName = "CLIENT DISCHARGE AUDIT";
                displayType = "Discharge";

            }else if("LOAD".equals(reportType)){
                reportDesignName = "CLIENT LOAD AUDIT";
                displayType = "Load";
            }

            //A401
            def gvyRptUtil = getGroovyClassInstance("ReportFieldSortUtil")
            if(unitList != null && unitList.size() > 0){
                unitList =  gvyRptUtil.processFieldSort(unitList,"EquipmentType,UnitNbr")
                Map mapList = (Map)unitList.get(0);
                println("mapList="+mapList);
                //Create and Mail Report
                JRDataSource ds = new JRMapCollectionDataSource(unitList);
                def reportRunner = inj.getGroovyClassInstance("ReportRunner");
                reportRunner.emailReport(ds, parameters, reportDesignName, contactEmail, "Matson Client Report - " +outBoundCarrierId+" "+displayType, "Attached is the "+outBoundCarrierId+ " "+displayType+" report");
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    } //method ends

    //A2 -- Starts last discharge report
    public boolean processLastDischCntrRpt(event, String rptType){
        init() // Initialize Global variable
        HashMap mapAuto = new HashMap();
        HashMap mapCyHon = new HashMap();
        HashMap mapCyOthers = new HashMap();
        //A7
        reportType = reportType == null ? rptType : reportType
        def units = null;
        println("processLastDischCntrRpt begin")
        try{
            inj = new GroovyInjectionBase();
            def visit = event.getEntity();
            def carrierId =  visit.cvdCv

            if("BARGE".equals(visit.vvdVessel.vesVesselClass.vesclassVesselType.name)) {
                return;
            }
            units = findAllUnitsForVesVoy(visit)

            Iterator iter = units.iterator();
            def ufv = null; def unit = null;  def dest = null; def commodity = null;
            def timeIn = null; def aibcarrierId = null;
            while(iter.hasNext()) {
                ufv = iter.next();
                unit = ufv.ufvUnit
                println("unit number is "+unit.getFieldValue("unitId"))
                if(unit.unitActiveUfv == null || UnitVisitStateEnum.RETIRED.equals(unit.unitActiveUfv.ufvVisitState) || UnitVisitStateEnum.ADVISED.equals(unit.unitActiveUfv.ufvVisitState)){
                    continue;
                }
                dest = unit.getFieldValue("unitGoods.gdsDestination")
                commodity = unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")
                commodity = commodity != null ? commodity : ''
                //A8
                if("DISCHARGE".equals(reportType)){
                    timeIn = ufv.ufvTimeIn
                }else if ("LOAD".equals(reportType)){
                    timeIn = ufv.ufvTimeOfLastMove
                }
                def lkpSlot = unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
                lkpSlot = lkpSlot!= null ? lkpSlot : ''
                println("unit "+unit.getFieldValue("unitId")+" commodity "+commodity+" lkpSlot"+lkpSlot+" dest"+dest)
                def lkpSlotValue = lkpSlot.indexOf(".")== -1 ? lkpSlot : lkpSlot.substring(0,lkpSlot.indexOf("."));
                println("exception in the above unit  "+lkpSlotValue);
                boolean pier29Loc = lkpSlotValue.startsWith('P29') || lkpSlotValue.startsWith('29Z') ? true : false
                println("unit "+unit.getFieldValue("unitId")+" commodity "+commodity+" timeIn "+timeIn+" dest "+dest+" pier29Loc "+pier29Loc)
                if(commodity.contains('AUTO') && timeIn != null) {
                    mapAuto.put(ufv, timeIn)
                }else if (!commodity.contains('AUTO') && !pier29Loc && 'HON'.equals(dest) && timeIn != null){
                    mapCyHon.put(ufv, timeIn)
                }else if(!commodity.contains('AUTO') &&  !pier29Loc && !'HON'.equals(dest) && timeIn != null){
                    mapCyOthers.put(ufv, timeIn)
                }

            }
            println("flow comes here ===")
            //println("  mapAuto.size() ==="+ mapAuto.size()+" mapCyHon.size() ==="+mapCyHon.size()+" mapCyOthers.size() ==="+mapCyOthers.size())
            //Sort Map Object
            rptFieldSortUtil = rptFieldSortUtil != null ? rptFieldSortUtil : inj.getGroovyClassInstance("ReportFieldSortUtil");
            mapAuto = mapAuto != null && mapAuto.size() > 0 ? rptFieldSortUtil.sortMapByValue(mapAuto) : null //sort Auto
            mapCyHon = mapCyHon!= null && mapCyHon.size() > 0 ? rptFieldSortUtil.sortMapByValue(mapCyHon) : null //sort CyHon
            mapCyOthers = mapCyOthers != null && mapCyOthers.size() > 0 ? rptFieldSortUtil.sortMapByValue(mapCyOthers) : null //sort CyNonHon

            //last Disch Units
            def ufvAuto = getLastDischUnit(mapAuto,"");
            def ufvCyHon = getLastDischUnit(mapCyHon,"");
            def ufvCyOthers = getLastDischUnit(mapCyOthers,"");

            println("mapDisplayLst size =="+mapDisplayLst.size())
            mapDisplayLst = mapDisplayLst.size() > 0 ? rptFieldSortUtil.sortMapByValue(mapDisplayLst) : null
            def lstDischUfv = getLastDischUnit(mapDisplayLst,"ALL");

            println("ufvAuto="+ufvAuto+" ufvCyHon="+ufvCyHon+" ufvCyOthers="+ufvCyOthers+"  lstDischUfv="+lstDischUfv)
            //For Report Display

            HashMap lastUnit = null;
            ArrayList unitRptList = new ArrayList();
            unitRptList.add(populateLstDischUnit(ufvAuto, "AUTO"))
            unitRptList.add(populateLstDischUnit(ufvCyHon, "CY-HON"))
            unitRptList.add(populateLstDischUnit(ufvCyOthers, "CY-OTHER"))
            unitRptList.add(populateLstDischUnit(lstDischUfv, "OVERALL"))


            //Set Report Parameter
            HashMap parameters = new HashMap();
            String strDate = ContextHelper.formatTimestamp(event.getEvent().getEventTime(), timezone)
            parameters.put("Date",strDate);

            println(" unitRptList size ==="+unitRptList.size())
            //Create and Mail Report
            JRDataSource ds = new JRMapCollectionDataSource(unitRptList);
            def reportRunner = inj.getGroovyClassInstance("ReportRunner");
            def reportDesignName = null; def displayType = null;
            //A7
            if("DISCHARGE".equals(reportType)){
                reportDesignName = "DISCH REPORT FOR LAST CNTR";
                displayType = "Discharge";
            }else if ("LOAD".equals(reportType)){
                reportDesignName = "LOAD REPORT FOR LAST CNTR";
                displayType = "Load";
            }
            println("ds = "+ds+"   unitreportlist = "+unitRptList+" reportType="+reportType);
            //   reportRunner.emailReport(ds, parameters, "DISCH REPORT FOR LAST CNTR", "1aktosdevteam@matson.com",outBoundCarrierId+" Last Container Discharge Report" ,outBoundCarrierId+" Last Container Discharge Report");
            reportRunner.emailReport(ds, parameters,reportDesignName , "1TOSDevTeamHONProd@gmail.com",outBoundCarrierId+" Last Container "+displayType+" Report" ,outBoundCarrierId+" Last Container "+displayType+" Report");  //A20
            println("processLastDischCntrRpt end")
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public UnitFacilityVisit getLastDischUnit(Map map, String type)
    {
        def unit = null;
        def ufv = null;
        def timeIn = null;
        try{
            if(map == null){
                return null;
            }

            Iterator it = map.keySet().iterator();
            while (it.hasNext()){
                ufv = it.next();
                timeIn = map.get(ufv)
            }

            unit = ufv.getUfvUnit();
            //1. Check if Unit has multiple discharges
            EventType evntType = EventType.findEventType("UNIT_DISCH");
            EventManager eventManager = (EventManager)Roastery.getBean("eventManager");
            List events = eventManager.getEventHistory(evntType, unit);

            if (events.size() == 2 && !"ALL".equals(type)) {
                def  event1 = events.get(0)
                def previousPos1 = event1.getEvntFieldChangesString();
                def note1 = previousPos1 != null ? previousPos1 : "";
                boolean b1 = note1.contains(outBoundCarrierId);

                def  event2 = events.get(1)
                def previousPos2 = event2.getEvntFieldChangesString();
                def note2 = previousPos2 != null ? previousPos2 : "";
                boolean b2 = note2.contains(outBoundCarrierId)
                //Set Correct Disch Time in Unit
                if(b1){
                    println("Event Date 1="+event1.getEventTime());
                    map.remove(ufv);
                    map.put(ufv,event1.getEventTime());
                }else if(b2){
                    println("Event Date 2="+event2.getEventTime());
                    map.remove(ufv);
                    map.put(ufv,event2.getEventTime());
                }

                rptFieldSortUtil = rptFieldSortUtil != null ? rptFieldSortUtil : getGroovyClassInstance("ReportFieldSortUtil");
                Map tempMap = map != null && map.size() > 0 ? rptFieldSortUtil.sortMapByValue(map) : null //sort Map Again

                Iterator itAgain = tempMap.keySet().iterator();
                while (itAgain.hasNext()){
                    ufv = itAgain.next();
                    timeIn = tempMap.get(ufv)
                }

            }//If Ends

            if(ufv != null){
                mapDisplayLst.put(ufv,timeIn)
            }

        }catch(Exception e){
            e.printStackTrace()
        }
        return ufv;
    }


    public HashMap populateLstDischUnit(UnitFacilityVisit ufv, String type)
    {
        HashMap map = new HashMap();

        //Unit is Null
        if(ufv == null){
            map.put("type", type );
            return map;
        }

        def unit = ufv.ufvUnit
        try
        {
            map.put("UnitNbr", unit.getFieldValue("unitId"));
            map.put("PositionSlot", ufv.ufvArrivePosition.posSlot);
            map.put("OutBoundCarrierId", outBoundCarrierId);
            map.put("type", type );
            String strTimeInDate = 	gvyEventUtil.formatDate(ufv.ufvTimeIn, timezone)
            String strTimeInTime = 	gvyEventUtil.formatTime(ufv.ufvTimeIn, timezone)
            map.put("InTime", strTimeInTime);
            map.put("InTimeDate", strTimeInDate);
            println("PositionSlot="+ufv.ufvArrivePosition.posSlot+" InTime="+strTimeInDate+" ufv.ufvTimeIn="+ufv.ufvTimeIn)

        }catch(Exception e){
            e.printStackTrace();
        }
        return map;
    }

    public List findAllUnitsForVesVoy(Object vesVisit)
    {
        ArrayList vesVistUnitLists = new ArrayList();
        try{
            //Long cvGkey = vesVisit.getCvdCv().getCvGkey()
            //DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_DECLARED_IB_CV, cvGkey)).addDqOrdering(Ordering.asc(UnitField.UFV_VISIT_STATE));
            def Id = vesVisit.getCvdCv().getCvId()
            println("Id ===="+Id)
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            if("DISCHARGE".equals(reportType)){
                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, Id));
            }else if("LOAD".equals(reportType)){
                dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID, Id));
            }//A13
            //dq = dq.addDqPredicate(PredicateFactory.ne(UnitField.UNIT_CURRENT_UFV_VISIT_STATE, UnitVisitStateEnum.RETIRED)).dq.addDqPredicate(PredicateFactory.ne(UnitField.UNIT_CURRENT_UFV_VISIT_STATE, UnitVisitStateEnum.ADVISED));
            dq = dq.addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY, UnitCategoryEnum.THROUGH)).addDqPredicate(PredicateFactory.ne(UnitField.UFV_FREIGHT_KIND, FreightKindEnum.MTY)).addDqOrdering(Ordering.asc(UnitField.UFV_VISIT_STATE));
            List vesVistUnits  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            vesVistUnitLists.addAll(vesVistUnits);
            println("vesVistUnitLists size is ==="+vesVistUnitLists.size())
        }catch(Exception e){
            e.printStackTrace();
        }
        return vesVistUnitLists
    }

    //A11
    public String processDischCounts(Object vesVisit, String type)
    {

        List onDeckList = new ArrayList();
        List belowDeckList = new ArrayList();
        String rptTitle = null;

        HashSet positionSet = new HashSet();
        List robList = new ArrayList();
        List restowList = new ArrayList();
        List trailerList = new ArrayList();
        List totalDfvList = new ArrayList();
        StringBuffer buf = new StringBuffer();
        def id = vesVisit.getCvdCv().getCvId();

        DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
        if("DISCHARGE".equals(type)){
            //dq = dq.addDqPredicate(PredicateFactory.ne(UnitField.UFV_VISIT_STATE,UnitVisitStateEnum.ADVISED)).addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S10_ADVISED)).addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S99_RETIRED));
            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, id));
            rptTitle = "Discharge Audit Details";
        }else if("LOAD".equals(type)){
            dq = dq.addDqPredicate(PredicateFactory.ne(UnitField.UFV_VISIT_STATE,UnitVisitStateEnum.ADVISED)).addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S10_ADVISED)).addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S99_RETIRED));
            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID, id)).addDqPredicate(PredicateFactory.like(UnitField.UFV_POS_NAME,"V%"));
            rptTitle = "Sail Audit Details";
        }
        dq = dq.addDqOrdering(Ordering.asc(UnitField.UFV_CATEGORY));
        List unitsList  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        println("Load count is "+unitsList.size());
        try{
            Iterator iter = unitsList.iterator();
            while(iter.hasNext()) {
                UnitFacilityVisit ufv = iter.next();
                def unit = ufv.ufvUnit;
                def unitId = unit.getFieldValue("unitId")
                def equipType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                def category =	unit.getUnitCategory();
                def deckPosition = null;
                def restow = unit.getFieldValue("unitActiveUfv.ufvRestowType");
                restow = restow != null ? restow.getKey() : ''

                if("DISCHARGE".equals(type)) {
                    //check to exclude duplicate position for bundles and unitId does not have $
                    if (unitId!=null && !unitId.contains("\$") && !equipType.startsWith('C')) {

                        deckPosition = unit.getFieldValue("unitActiveUfv.ufvFlexString06")
                        //println("deckPosition is "+deckPosition+" for unit "+unit.getFieldValue("unitId"))

                        //No Deck - Below Deck
                        if("OD".equals(deckPosition)){
                            onDeckList.add(unit);
                        }else if("BD".equals(deckPosition)){
                            belowDeckList.add(unit);
                        }


                        //ROB Restow and Trailer
                        if(UnitCategoryEnum.THROUGH.equals(category) && "RESTOW".equals(restow)){
                            restowList.add(unit);
                            robList.add(unit);
                        }else if (UnitCategoryEnum.THROUGH.equals(category)){
                            robList.add(unit);
                        }else if ("RO".equals(deckPosition) || equipType.contains("B40")){
                            procTrailerCount(equipType);
                        }else{
                            totalDfvList.add(unit);
                        }
                    }
                } else if ("LOAD".equals(type)) {
                    //check to exclude duplicate position for bundles and unitId does not have $
                    if (!positionSet.contains(ufv.getUfvLastKnownPosition()) &&
                            (unitId!=null && !unitId.contains("\$"))) {

                        deckPosition = ufv.getUfvFlexString06()
                        //println("deckPosition is "+deckPosition+" for unit "+unit.getFieldValue("unitId"))

                        //No Deck - Below Deck
                        if("OD".equals(deckPosition)){
                            onDeckList.add(unit);
                        }else if("BD".equals(deckPosition)){
                            belowDeckList.add(unit);
                        }


                        //ROB Restow and Trailer
                        if(UnitCategoryEnum.THROUGH.equals(category) && "RESTOW".equals(restow)){
                            restowList.add(unit);
                            robList.add(unit);
                        }else if (UnitCategoryEnum.THROUGH.equals(category)){
                            robList.add(unit);
                        }else if ("RO".equals(deckPosition) || equipType.contains("B40")){
                            procTrailerCount(equipType);
                        }else{
                            totalDfvList.add(unit);
                        }
                    }
                    //else {
                    //println("UFV::"+ufv+" ("+ufv.getUfvLastKnownPosition()+")");
                    //}

                    positionSet.add(ufv.getUfvLastKnownPosition());

                }
            }

            rptFieldSortUtil = rptFieldSortUtil != null ? rptFieldSortUtil : inj.getGroovyClassInstance("ReportFieldSortUtil");
            mapTrailer = rptFieldSortUtil.sortMapByKey(mapTrailer);

            // buf.append("   "+rptTitle+"  "+eol);
            // buf.append("----------------------------"+eol);
            buf.append(""+eol);
            buf.append("Container : "+totalDfvList.size()+eol);
            buf.append("ROB       : "+robList.size()+eol);
            buf.append("Restow    : "+restowList.size()+eol);
            buf.append("Trailer   : "+trailerCnt+eol);
            Iterator it = mapTrailer.keySet().iterator(); //A21
            while (it.hasNext()){
                def equipSize = it.next();
                def eqCount = mapTrailer.get(equipSize)
                buf.append("  "+(equipSize.length()== 3 ? equipSize+" " : equipSize )+" : "+eqCount+eol);
            }
            // buf.append("----------------------------"+eol);
            //buf.append(eol+eol+eol+eol)
            //buf.append("    Deck Position Count  "+eol);
            //buf.append("----------------------------"+eol);
            //buf.append("OnDeck      : "+onDeckList.size()+eol);
            //buf.append("BelowDeck   : "+belowDeckList.size()+eol);

        }catch(Exception e){
            e.printStackTrace();
        }

        return buf.toString();
    }
    //A2- Ends

    // A3 Email Confirmation Starts

    public void discAuditConfirmationEmail(event, String type){
        inj = inj==null ? new GroovyInjectionBase(): inj;
        def sub = "";  def acctSub = "";
        def visit = event.getEntity();
        List acctListRpt = null;
        List unitListRpt = null;
        def reportDesignName = null;
        def reportDesignNameUnits = null;
        try{
            def doer = event.event.evntAppliedBy   //Gets Doer
            doer = doer.replace('user:','')
            def carrierId = visit.cvdCv
            def emailSender = inj.getGroovyClassInstance("EmailSender")  // calls Email call
            if("DISCHARGE".equals(type)){
                sub = "Audit Discharge for "+carrierId+" Pau.";    // Compose Subject String
                acctSub = "Accounting Discharge Total Counts for "+carrierId;
            }else if("LOAD".equals(type)){
                sub = " "+carrierId+" Sailed.";   // Compose Subject String
                acctSub = "Accounting Load Back Total Counts for "+carrierId;
            }

            def body = processDischCounts(visit,type);

            acctListRpt = processAccoutingRpt(visit,type);
            unitListRpt = processAccoutingUnitsRpt(visit,type);

            log(" Units acctListRpt.size() === "+acctListRpt.size() )
            //  emailSender.custSendEmail(acctEmail,acctSub,body+eol); //Accounting
            if (acctListRpt!=null && acctListRpt.size() > 0)
            {
                JRDataSource ds = new JRMapCollectionDataSource(acctListRpt);
                JRDataSource ds1 = new JRMapCollectionDataSource(unitListRpt);
                // get report runner handle
                def reportRunner = inj.getGroovyClassInstance("ReportRunner");

                //Set report parameters
                HashMap parameters = new HashMap();
                HashMap parametersUnits = new HashMap();
                parameters.put("Date",new Date());
                parametersUnits.put("Date",new Date());

                println("Sending UNIT_DETAILS");
                reportDesignNameUnits = "UNIT_DETAILS";
                reportRunner.emailExcelReport(ds1, parametersUnits,reportDesignNameUnits ,acctEmail,"UNITS "+type+" LIST FOR "+ carrierId ,"Report attached");

                // call report design of rehandle containers not loaded back to vessel report.
                if ("LOAD".equals(type))
                {
                    reportDesignName = "ACCT AUDIT LOAD REPORT";
                    parameters.put("outboundVesVoy",visit.getCvdCv().getCvId());
                } else if ("DISCHARGE".equals(type))
                {
                    println("Sending ACCT AUDIT DISCH REPORT TEST");
                    reportDesignName = "ACCT AUDIT DISCH REPORT";
                    parameters.put("inboundVesVoy",visit.getCvdCv().getCvId());
                }
                // Emailing report
                //reportRunner.emailExcelReport(ds, parameters,reportDesignName ,acctEmail,acctSub,body+eol);
            }


            if ("LOAD".equals(type) && visit.getCvdCv().getCvId().startsWith("YB")) {
                return null;
            }
            println(" doer is "+doer)
            if (!"-jms-".equals(doer)) {
                if ("LOAD".equals(type)) {
                    emailTo = emailTo;
                }
                emailSender.custSendEmail(emailTo,sub,"Action done by "+doer);  //executes Email Procedure //A18
            }

        }catch(Exception e){
            e.printStackTrace();
        }

    }//A3- Ends


    public void procTrailerCount(String wholeEquipType)
    {
        //def gvyStrUtility =  getGroovyClassInstance("GvyStringUtility")
        //def equipType = gvyStrUtility.getOnlyNumerics(wholeEquipType);
        //def equipType = wholeEquipType.substring(1,3);
        if(mapTrailer.get(wholeEquipType) != null){
            Integer count = (Integer)mapTrailer.get(wholeEquipType);
            int tempCnt = count.intValue();
            tempCnt = tempCnt+1;
            mapTrailer.put(wholeEquipType,tempCnt);
        }else{
            mapTrailer.put(wholeEquipType,new Integer(1));
        }
        trailerCnt = trailerCnt+1;
    }//Method Ends

    public List processAccoutingUnitsRpt(Object vesVisit, String type)
    {
        println("Calling processAccoutingUnitsRpt for :: " + type);
        List resultAcctList = new ArrayList();
        List resultUnitList = new ArrayList();
        HashSet positionSet = new HashSet();
        HashMap outputMap = null;
        HashMap outputUnitMap = null;
        List acctList = null;
        vesselForDischId = vesVisit.getCvdCv().getCvId();
        def Id = vesVisit.getCvdCv().getCvId();
        String vesselGkey = vesVisit.getCvdCv().getCvGkey();

        if ("LOAD".equals(type))
        {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_VISIT_STATE,UnitVisitStateEnum.ADVISED))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S10_ADVISED))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S99_RETIRED))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY,UnitCategoryEnum.THROUGH))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID, Id))
                    .addDqPredicate(PredicateFactory.like(UnitField.UFV_POS_NAME,"V%"))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_CMDTY));

            acctList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("===acctList size==="+acctList.size());
            Iterator iter = acctList.iterator();
            while(iter.hasNext()) {
                def ufv = iter.next();
                Unit unit = ufv.ufvUnit;
                def unitId = unit.getFieldValue("unitId");
                //check to exclude duplicate position for bundles and unitId does not have $
                if (!positionSet.contains(ufv.getUfvLastKnownPosition()) && (unitId!=null && !unitId.contains("\$"))) {
                    outputUnitMap = populateUnitListByType(ufv,type)
                    resultUnitList.add(outputUnitMap)
                }
                //else {
                //println("UFV::"+ufv);
                //}

                positionSet.add(ufv.getUfvLastKnownPosition());

            }
        } else if ("DISCHARGE".equals(type)) {
            log("<<<<TESTING DISCHARGE REPORT ID For UNIT LIST>>>>"+Id);

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY,UnitCategoryEnum.THROUGH))
                    .addDqPredicate(PredicateFactory.like(UnitField.UFV_ARRIVE_POS_LOC_TYPE, "VESSEL"))
                    .addDqPredicate(PredicateFactory.like(UnitField.UFV_ARRIVE_POS_LOC_GKEY, vesselGkey))
            //.addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S10_ADVISED))
            //.addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S99_RETIRED))
            //.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID,"MATU2575205"))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_CMDTY));

            acctList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            log("<<<<ACCTLIST SIZE IS >>>>"+acctList.size());
            Iterator iter = acctList.iterator();
            while(iter.hasNext()) {
                def ufv = iter.next()
                def unit = ufv.ufvUnit

                def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
                transitState = transitState != null ? transitState.getKey() : null
                def tState = transitState!=null ? transitState.split("_") : null
                transitState = tState != null ? tState[1] : null
                //LOGGER.warn(" DISCHARGE transitState is "+transitState+"for unit "+unit.getFieldValue("unitId"))
                outputUnitMap = populateUnitListByType(ufv,type)
                resultUnitList.add(outputUnitMap)
            }

        }
        return resultUnitList;
    }

    public List processAccoutingRpt(Object vesVisit, String type)
    {
        println("Calling processAccoutingRpt");
        List resultAcctList = new ArrayList();
        HashSet positionSet = new HashSet();
        HashMap outputMap = null;
        List acctList = null;
        vesselForDischId = vesVisit.getCvdCv().getCvId();
        def Id = vesVisit.getCvdCv().getCvId();
        String vesselGkey = vesVisit.getCvdCv().getCvGkey();

        if ("LOAD".equals(type))
        {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_VISIT_STATE,UnitVisitStateEnum.ADVISED))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S10_ADVISED))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S99_RETIRED))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY,UnitCategoryEnum.THROUGH))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID, Id))
                    .addDqPredicate(PredicateFactory.like(UnitField.UFV_POS_NAME,"V%"))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_CMDTY));

            acctList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("===acctList size==="+acctList.size());
            Iterator iter = acctList.iterator();
            while(iter.hasNext()) {
                def ufv = iter.next();
                Unit unit = ufv.ufvUnit;
                def unitId = unit.getFieldValue("unitId");
                //check to exclude duplicate position for bundles and unitId does not have $
                if (!positionSet.contains(ufv.getUfvLastKnownPosition()) && (unitId!=null && !unitId.contains("\$"))) {
                    outputMap = populateAcctListByType(ufv,type);
                    resultAcctList.add(outputMap);
                }
                //else {
                //println("UFV::"+ufv);
                //}

                positionSet.add(ufv.getUfvLastKnownPosition());

            }
        } else if ("DISCHARGE".equals(type)) {
            log("<<<<TESTING DISCHARGE REPORT ID>>>>"+Id+":::"+vesselGkey);

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
            //.addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY,UnitCategoryEnum.THROUGH))
                    .addDqPredicate(PredicateFactory.like(UnitField.UFV_ARRIVE_POS_LOC_TYPE, "VESSEL"))
                    .addDqPredicate(PredicateFactory.like(UnitField.UFV_ARRIVE_POS_LOC_GKEY, vesselGkey))
            //.addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S10_ADVISED))
            //.addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S99_RETIRED))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_CMDTY));

            acctList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("DomainQuery:::"+dq);
            log("<<<<ACCTLIST SIZE IS >>>>"+acctList.size());
            Iterator iter = acctList.iterator();
            while(iter.hasNext()) {
                def ufv = iter.next()
                def unit = ufv.ufvUnit

                def transitState = unit.getFieldValue("unitActiveUfv.ufvTransitState")
                transitState = transitState != null ? transitState.getKey() : null
                def tState = transitState!=null ? transitState.split("_") : null
                transitState = tState != null ? tState[1] : null
                //LOGGER.warn(" DISCHARGE transitState is "+transitState+"for unit "+unit.getFieldValue("unitId"))
                outputMap = populateAcctListByType(ufv,type)
                resultAcctList.add(outputMap)
            }

        }
        return resultAcctList;
    }

/*    public List processAccoutingRpt(Object vesVisit, String type)
    {
        List resultAcctList = new ArrayList();
        HashSet positionSet = new HashSet();
        HashMap outputMap = null;
        List acctList = null;
        def Id = vesVisit.getCvdCv().getCvId()
        if ("LOAD".equals(type))
        {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_VISIT_STATE,UnitVisitStateEnum.ADVISED))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S10_ADVISED))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S99_RETIRED))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY,UnitCategoryEnum.THROUGH))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_OB_ID, Id))
                    .addDqPredicate(PredicateFactory.like(UnitField.UFV_POS_NAME,"V%"))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_CMDTY));

            acctList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("===acctList size==="+acctList.size());
            Iterator iter = acctList.iterator();
            while(iter.hasNext()) {
                def ufv = iter.next();
                Unit unit = ufv.ufvUnit;
                def unitId = unit.getFieldValue("unitId");
                //check to exclude duplicate position for bundles and unitId does not have $
                if (!positionSet.contains(ufv.getUfvLastKnownPosition()) && (unitId!=null && !unitId.contains("\$"))) {
                    outputMap = populateAcctListByType(ufv,type);
                    resultAcctList.add(outputMap);
                }
                //else {
                //println("UFV::"+ufv);
                //}

                positionSet.add(ufv.getUfvLastKnownPosition());


            }
        } else if ("DISCHARGE".equals(type)) {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit")
                    //.addDqPredicate(PredicateFactory.ne(UnitField.UFV_VISIT_STATE,UnitVisitStateEnum.ADVISED))
                    //.addDqPredicate(PredicateFactory.ne(UnitField.UFV_VISIT_STATE,UnitVisitStateEnum.RETIRED))
                    .addDqPredicate(PredicateFactory.ne(UnitField.UFV_UNIT_CATEGORY,UnitCategoryEnum.THROUGH))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, Id))
                    .addDqOrdering(Ordering.asc(UnitField.UFV_CMDTY));

            acctList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            Iterator iter = acctList.iterator();
            while(iter.hasNext()) {
                def ufv = iter.next();
                Unit unit = ufv.ufvUnit;
                def unitId = unit.getFieldValue("unitId");
                def equipType = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId");
                //check to exclude duplicate position for bundles and unitId does not have $
                if (unitId!=null && !unitId.contains("\$") && !equipType.startsWith('C')) {
                    outputMap = populateAcctListByType(ufv,type);
                    resultAcctList.add(outputMap);
                }

            }

        }
        return resultAcctList;
    }
*/
    public HashMap populateUnitListByType(UnitFacilityVisit ufv, String type) {
        println("Calling populateUnitListByType for ::"+type);

        HashMap resMap = new HashMap();
        def unit = ufv.ufvUnit
        UnitEquipment chasEquip = unit.getUnitCarriageUe();
        def freightkindout = unit.getFieldValue("unitFreightKind").getKey();
        def attachedUnit = unit.getFieldValue("unitAttachedEquipIds")
        //println("chasEquip   "+chasEquip)
        if ("FCL".equals(freightkindout))
        {
            freightkindout = "F";
        } else if ("MTY".equals(freightkindout))
        {
            freightkindout = "E";
        }

        resMap.put("Commodity",unit.getFieldValue("unitGoods.gdsCommodity.cmdyId"));
        resMap.put("UfvFlexString01",unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass").getKey());
        resMap.put("EquipmentType",unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId"))
        resMap.put("FreightKind",freightkindout)
        resMap.put("UnitNbr",unit.getFieldValue("unitId"))
        if (attachedUnit != null && chasEquip !=null)
        {
            resMap.put("AttachedUnits",unit.getFieldValue("unitAttachedEquipIds"))
        } else if (attachedUnit != null && chasEquip == null)
        {
            resMap.put("AttachedUnits",null)
        }
        def _category = unit.getFieldValue("unitCategory");
        def categoryKey = _category != null ? _category.getKey() : "";
        //println("TransitState:::::::::::::::::"+ufv.ufvTransitState);
        def transitState = ufv.ufvTransitState;
        def transitStateKey = null;
        if (transitState != null) {
            transitStateKey = transitState != null ? transitState.getKey() : ''
            def tState = transitStateKey.split("_")
            transitStateKey = tState[1]
        }
        //log("Unit Details:::"+unit.getFieldValue("unitId")+"::"+transitState+"::"+transitStateKey);
        if ("LOAD".equals(type))
        {
            resMap.put("UfvFlexDate01",ufv.getFieldValue("ufvActualObCv.cvATA"))
            resMap.put("UfvFlexDate02",ufv.getFieldValue("ufvActualObCv.cvATD"))
            resMap.put("OutboundCarrierId",unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId"))
            resMap.put("POL",unit.getFieldValue("unitGoods.gdsDestination"))
            resMap.put("UfvFlexString06",ufv.getUfvFlexString06());
            resMap.put("Category",categoryKey)
            resMap.put("TransitState",transitStateKey)
        } else if ("DISCHARGE".equals(type)) {
            resMap.put("UfvFlexDate01",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvATA"))
            resMap.put("UfvFlexDate02",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvATD"))
            resMap.put("POL",unit.getFieldValue("unitRouting.rtgPOL.pointId"))
            resMap.put("Category",categoryKey)
            resMap.put("TransitState",transitStateKey)
            resMap.put("ArrivalPositionFull",ufv.ufvArrivePosition.posName);
            resMap.put("UfvFlexString06",ufv.getUfvFlexString06())
        }

        return resMap;
    }

    public HashMap populateAcctListByType(UnitFacilityVisit ufv, String type) {

        HashMap resMap = new HashMap();
        def unit = ufv.ufvUnit
        UnitEquipment chasEquip = unit.getUnitCarriageUe();
        def freightkindout = unit.getFieldValue("unitFreightKind").getKey();
        def attachedUnit = unit.getFieldValue("unitAttachedEquipIds")
        //println("chasEquip   "+chasEquip)
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
        if (attachedUnit != null && chasEquip !=null)
        {
            resMap.put("AttachedUnits",unit.getFieldValue("unitAttachedEquipIds"))
        } else if (attachedUnit != null && chasEquip == null)
        {
            resMap.put("AttachedUnits",null)
        }

        if ("LOAD".equals(type))
        {
            resMap.put("OutboundCarrierATA",ufv.getFieldValue("ufvActualObCv.cvATA"))
            resMap.put("OutboundCarrierATD",ufv.getFieldValue("ufvActualObCv.cvATD"))
            resMap.put("OutboundCarrierId",unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId"))
            resMap.put("Destination",unit.getFieldValue("unitGoods.gdsDestination"))
            resMap.put("UfvFlexString06",ufv.getUfvFlexString06())
        } else if ("DISCHARGE".equals(type)) {
            resMap.put("InboundCarrierATA",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvATA"))
            resMap.put("InboundCarrierATD",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvATD"))
            resMap.put("OPL",unit.getFieldValue("unitRouting.rtgOPL.pointId"))
            //resMap.put("InboundCarrierId",unit.getFieldValue("unitActiveUfv.ufvActualIbCv.cvId"))
            resMap.put("InboundCarrierId",vesselForDischId)
            resMap.put("UfvFlexString06",unit.getFieldValue("unitActiveUfv.ufvFlexString06"))
        }
        //println("Result map is "+resMap);
        return resMap;
    }



}//class ends
