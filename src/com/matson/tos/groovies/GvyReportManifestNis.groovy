/*
*  A1   GR   02/08/2010     Updt Report Sorting for KAH port
*  A2   GR   03/09/2012     Updt Sorting for NAW port
*  A3   PS    01/11/2013     Added Buzz to KAH email dist list
*  A4   LC   08/08/2013      Added fix for departed unit with YB OB Actual Carrier
*  A5   KR   12/30/2013      Exclude RETIRED units from reporting
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


public class GvyReportManifestNis
{
    String emailTo = null;
    private String reportStatus = "Final"
    private String outBoundCarrierId = null
    private HashSet unitSet = new HashSet();
    def inj = null;

    private static final String hilKhiEmailList = "1aktosdevteam@matson.com";
    private static final String  kahEmailList =  "1aktosdevteam@matson.com";
    private static final String nawEmailList = "1aktosdevteam@matson.com";

    public boolean processManifest(Object event)
    {
        //1. lookup all units on Board
        //2. Fetch and Map information
        //3. Generate Report

        try
        {
            inj = new GroovyInjectionBase();
            def visit = event.getEntity();

            //getEmail Grp List
            emailTo = formatProdEmailList(visit)

            def phase = event.getPropertyAsString("VisitPhase");
            if( phase.equals("40WORKING") || phase.equals("50COMPLETE")) {
                reportStatus = "Preliminary"
            }

            def nextFacility = visit.getFieldValue("cvdCv.cvNextFacility.fcyId")
            def facility = visit.getFieldValue("cvdCv.cvFacility.fcyId")

            def status  =  reportStatus
            reportStatus = reportStatus+":"+facility+" to "+nextFacility


            ArrayList unitList = new ArrayList();
            List units = null;
            outBoundCarrierId =  ""+visit.cvdCv
            println("YB OBCarrierId ::"+outBoundCarrierId)

            if(outBoundCarrierId.startsWith('YB')){
                try{
                    //Replace once Navis Error is fixed
                    //units = getYbRtgUnitsOnVesVoy(visit)

                    //A15 This Code should be removed after the Navis Issue with Vesvisit lookup on unit Reoute is fixed
                    units = getYbRtgUnitsForAllVesVoy(outBoundCarrierId)
                    //A15 - Ends
                }catch(Exception e){
                    e.printStackTrace()
                }
            }else{
                units = inj.getUnitFinder().findAllUfvOnBoard(visit.cvdCv);
            }
            //println("units -------------"+units)
            if(units == null || emailTo == null){
                return false;
            }else if(units != null && units.size() == 0){
                HashMap noUnitMap = new HashMap()
                noUnitMap.put("OutboundCarrierId", outBoundCarrierId);
                noUnitMap.put("reportStatus","NO DATA TO DISPLAY");
                unitList.add(noUnitMap);
            }

            Iterator iter = units.iterator();
            while(iter.hasNext()) {
                def ufv = iter.next();
                HashMap unitDataMap = populateUnitData(ufv)
                if(unitDataMap != null) {
                    unitList.add(unitDataMap);
                }
            }

            //Sorting By Field
            def gvyRptUtil = inj.getGroovyClassInstance("ReportFieldSortUtil")
            if(unitList != null && unitList.size() > 0){
                if(nextFacility.equals('HIL') || nextFacility.equals('KHI') || nextFacility.equals('NAW')){
                    unitList =  gvyRptUtil.processFieldSort(unitList,"UnitNbr")
                }else if(nextFacility.equals('KAH')){
                    unitList =  gvyRptUtil.processFieldSort(unitList,"GoodsConsigneeName,UnitNbr")
                }else{
                    unitList =  gvyRptUtil.processFieldSort(unitList,"DeclaredIbCarrierId,UnitNbr")
                }
            }

            println("unitList :"+ (unitList != null ? unitList.size() : "EMPTY"))
            //Set Report Parameter
            HashMap parameters = new HashMap();
            String strDate = ContextHelper.formatTimestamp(event.getEvent().getEventTime(), ContextHelper.getThreadUserTimezone())

            println("Event Time ::"+event.getEvent().getEventTime()+"    TimeZone:"+ContextHelper.getThreadUserTimezone()+"  strDate ::"+strDate)

            parameters.put("Date",strDate);
            parameters.put("recordCount",(unitList != null && unitList.size() > 1 ? unitList.size() : 0))
            // parameters.put("MATSONLOGO",DigitalAsset.findImage("MATSONLOGO"));

            //Create and Mail Report
            JRDataSource ds = new JRMapCollectionDataSource(unitList);
            def reportRunner = inj.getGroovyClassInstance("ReportRunner");
            reportRunner.emailReport(ds, parameters, "MANIFEST NIS", emailTo, outBoundCarrierId+" "+status+" Manifest : " + facility + " to " + nextFacility ,outBoundCarrierId+" "+status+" Manifest : " + facility + " to " + nextFacility);

        }catch(Exception e){
            e.printStackTrace()
        }
    }


    //1.Maps unit Data to report file attribute
    public HashMap populateUnitData(UnitFacilityVisit ufv)
    {
        def unit = ufv.ufvUnit
        def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
        println(unit.getFieldValue("unitId")+" UFVTRANSITSTATE="+transitState+", UnitVisitState="+unit.getFieldValue("UnitVisitState")) // A5
        if((UnitVisitStateEnum.DEPARTED.equals(unit.getFieldValue("UnitVisitState"))||UnitVisitStateEnum.RETIRED.equals(unit.getFieldValue("UnitVisitState"))) && (outBoundCarrierId!= unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId"))) //A5
        {

            println("***** OB Actual Carrier for unit ("+unit+")::"+unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvId"));

            return null;
        }
        if(outBoundCarrierId.startsWith('YB') && unitSet.contains(unit.unitId)){
            return null;
        }

        HashMap map = null;

        try
        {
            if(outBoundCarrierId.startsWith('YB') && ( UfvTransitStateEnum.S70_DEPARTED.equals(transitState) || UfvTransitStateEnum.S60_LOADED.equals(transitState)) && getRecentUnit(unit) == null)
            {
                setUnitId(unit);
                map = new HashMap();
                map.put("UnitNbr", unit.getFieldValue("unitId"));
                map.put("GoodsConsigneeName", unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName"));
                map.put("PositionSlot", unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));
                map.put("Commodity", unit.getFieldValue("unitGoods.gdsCommodity.cmdyId"));
                // map.put("POD", unit.getFieldValue("unitRouting.rtgPOD1.pointId"));
                map.put("POD", unit.getFieldValue("unitGoods.gdsDestination"));
                map.put("POL", unit.getFieldValue("unitRouting.rtgPOL.pointId"));
                def dIbcarrierId = null;
                def dIbcarrierMode= unit.getFieldValue("unitDeclaredIbCv.cvCarrierMode")
                dIbcarrierMode = dIbcarrierMode != null ? dIbcarrierMode.getKey() : ''
                if(dIbcarrierMode.equals('TRUCK')){
                    dIbcarrierId = "T-"+unit.getFieldValue("unitDeclaredIbCv.carrierOperatorId")
                }else{
                    dIbcarrierId=unit.getFieldValue("unitDeclaredIbCv.cvId")
                }
                map.put("DeclaredIbCarrierId", dIbcarrierId);
                map.put("GrossWeightKgValue", unit.getFieldValue("unitGoodsAndCtrWtKg"));
                map.put("EquipmentIdFull", unit.getFieldValue("unitCarriageUe.ueEquipment.eqIdFull"));
                map.put("EquipmentType", unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass"));
                map.put("OutboundCarrierId", outBoundCarrierId);
                map.put("AttachedUnits", unit.getFieldValue("unitAttachedPayloadEquipIds"));
                map.put("UfvSparcsNotes",reportStatus);
            }
            else if(!outBoundCarrierId.startsWith('YB')){
                setUnitId(unit);
                map = new HashMap();
                map.put("UnitNbr", unit.getFieldValue("unitId"));
                map.put("GoodsConsigneeName", unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName"));
                map.put("PositionSlot", unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot"));
                map.put("Commodity", unit.getFieldValue("unitGoods.gdsCommodity.cmdyId"));
                // map.put("POD", unit.getFieldValue("unitRouting.rtgPOD1.pointId"));
                map.put("POD", unit.getFieldValue("unitGoods.gdsDestination"));
                map.put("POL", unit.getFieldValue("unitRouting.rtgPOL.pointId"));
                def dIbcarrierId = null;
                def dIbcarrierMode= unit.getFieldValue("unitDeclaredIbCv.cvCarrierMode")
                dIbcarrierMode = dIbcarrierMode != null ? dIbcarrierMode.getKey() : ''
                if(dIbcarrierMode.equals('TRUCK')){
                    dIbcarrierId = "T-"+unit.getFieldValue("unitDeclaredIbCv.carrierOperatorId")
                }else{
                    dIbcarrierId=unit.getFieldValue("unitDeclaredIbCv.cvId")
                }
                map.put("DeclaredIbCarrierId", dIbcarrierId);
                map.put("GrossWeightKgValue", unit.getFieldValue("unitGoodsAndCtrWtKg"));
                map.put("EquipmentIdFull", unit.getFieldValue("unitCarriageUe.ueEquipment.eqIdFull"));
                map.put("EquipmentType", unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass"));
                map.put("OutboundCarrierId", outBoundCarrierId);
                map.put("AttachedUnits", unit.getFieldValue("unitAttachedPayloadEquipIds"));
                map.put("UfvSparcsNotes",reportStatus);
            }

        }catch(Exception e){
            e.printStackTrace();
        }
        return map;
    }


    public String formatProdEmailList(Object visit)
    {
        def facilityEmailList = null
        try{
            def nextFacility = visit.getFieldValue("cvdCv.cvNextFacility.fcyId")
            if(nextFacility.equals('HIL') || nextFacility.equals('KHI')){
                facilityEmailList = hilKhiEmailList
            }else if (nextFacility.equals('KAH')){
                facilityEmailList = kahEmailList
            }else if (nextFacility.equals('NAW')){
                facilityEmailList = nawEmailList
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return facilityEmailList
    }


    public List getCarrierVisits(String id) {
        DomainQuery dq = QueryUtils.createDomainQuery("CarrierVisit");
        dq.addDqPredicate(PredicateFactory.eq(ArgoField.CV_ID,id ));
        def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        if(list != null && list.size() > 0) {
            println("YB VV list Size :"+list.size())
            return list
        }
        return null;
    }

    public List getYbRtgUnitsOnVesVoy(Object visit)
    {
        List ufvYbUnits = null;
        try
        {
            Long facilityGkey = ContextHelper.getThreadFacility().getFcyGkey();
            Long cvGkey = visit.getCvdCv().getCvGkey()
            println("YB OBCarrId ::"+visit.getCvdCv()+" YBGKEY :"+cvGkey)
            println("ContextHelper.getThreadFacility().getFcyGkey() : "+ContextHelper.getThreadFacility().getFcyGkey())
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, facilityGkey)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_INTENDED_OB_CV, cvGkey)).addDqOrdering(Ordering.asc(UnitField.UFV_VISIT_STATE));;
            //DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, facilityGkey)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_INTENDED_OB_CVD, 7531607)).addDqOrdering(Ordering.asc(UnitField.UFV_VISIT_STATE));
            //println("Domain Query ::"+dq)
            ufvYbUnits  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("unitUfvYB ::"+ufvYbUnits != null ? ufvYbUnits.size() : 0)
        }catch(Exception e){
            e.printStackTrace()
        }
        return ufvYbUnits;
    }


    public List getYbRtgUnitsForAllVesVoy(String YbVesVoyId)
    {
        ArrayList vesVistUnitLists = new ArrayList();
        try
        {
            List vesVisitList = getCarrierVisits(YbVesVoyId)
            for(aVesVisit in vesVisitList){
                Long cvGkey = aVesVisit.getCvGkey()
                DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_INTENDED_OB_CV, cvGkey)).addDqOrdering(Ordering.asc(UnitField.UFV_VISIT_STATE));
                //println("Domain Query ::"+dq)
                List vesVistUnits  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
                vesVistUnitLists.addAll(vesVistUnits);
                println("YB_OBCarrId :"+YbVesVoyId+"  YB_GKEY :"+cvGkey+"  NbrOfUnits :"+(vesVistUnits != null ? vesVistUnits.size() : 0))
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return vesVistUnitLists
    }

    private void setUnitId(Object unit){
        unitSet.add(unit.getUnitId());
    }

    //Added Method Lookup to Resolve SIT cancel Condition
    private Object getRecentUnit(Object unit)
    {
        def ufv = null;
        try{
            def equiClass = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
            def equiClassKey = equiClass != null ? equiClass.getKey() : ''
            if(equiClassKey.equals('CONTAINER')){
                ufv = inj.findActiveUfv(unit.getUnitId())
                if(ufv != null && ufv.ufvTransitState.equals(UfvTransitStateEnum.S60_LOADED)){
                    ufv = null;
                }
            }else{
                ufv = unit
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return ufv;
    }

}
