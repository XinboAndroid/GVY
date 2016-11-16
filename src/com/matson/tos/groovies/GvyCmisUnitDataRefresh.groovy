import com.navis.argo.business.api.GroovyApi;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.InventoryField;
import com.navis.inventory.business.api.UnitField;
import com.navis.inventory.business.api.UnitFinder;
import com.navis.inventory.business.units.Unit;
import com.navis.argo.ContextHelper
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.reference.Equipment
import com.navis.framework.business.Roastery
import com.navis.argo.business.api.ServicesManager
import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.security.business.user.BaseUser
import com.navis.framework.persistence.BaseFinder
import com.navis.services.business.event.GroovyEvent;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.argo.business.atoms.EquipClassEnum;

public class GvyCmisUnitDataRefresh
{
    def unitFinder = null;
    ServicesManager sm = null;
    def complex = null;
    ArrayList activeUnits = new ArrayList()
    Set departedUnits = new HashSet()
    ArrayList vesVistUnitLists = new ArrayList();

    public void processUnitDataExtraction()
    {
        try
        {
            unitFinder = (UnitFinder)Roastery.getBean("unitFinder");
            complex = ContextHelper.getThreadComplex();

            //List all units
            getAllUnits()

            println("activeUnits ::-------"+activeUnits.size()+" Departed units ::--------"+departedUnits.size())
            //Read and Posting Events for Active and Departed Units
/*    for(aUnit in activeUnits){
          //def inUnit = unitFinder.findUnitByHostKey(complex, aUnit)
          postLastRecoredEvent(aUnit)
       }
       for(deptUnit in departedUnits){
          postLastRecoredEvent(deptUnit)
       }
 */
            println("After Posting Unit Events")

        }catch(Exception e){
            e.printStackTrace()
        }
    }

    // For New Ves Client Vessel
    public void processCmisDataRefreshByVesVoy(Object api, Object event, String vesVoy)
    {
        try
        {
            //List all units
            getAllUnits(vesVoy);
            println("Starting CMIS_DATA_REFRESH - Client Vessel Completed")
            //Read and Posting Events for Active and Departed Units

            for(aUnit in vesVistUnitLists){

                Object inUnit = aUnit.getUfvUnit();
                api.getGroovyClassInstance("GvySnxUpdateProcessor").procCmisDataRefresh(api,event,inUnit);
                println("Unit ::-------"+aUnit);
            }

            println("After CMIS_DATA_REFRESH - Client Vessel Completed")

        }catch(Exception e){
            api.log("Exception in GvyCmisUnitDataRefresh.processCmisDataRefreshByVesVoy() " + e);
        }
    }

// For LH new vessel Vessel
    public void processCmisDataRefreshByVesVoyNV(Object api, Object event, String vesVoy)
    {
        try
        {
            //List all units
            getAllUnitsNewvess(vesVoy);
            println("Starting CMIS_DATA_REFRESH - LH New Vessel Completed")
            //Read and Posting Events for Active and Departed Units

            for(aUnit in vesVistUnitLists){

                Object inUnit = aUnit.getUfvUnit();
                api.getGroovyClassInstance("GvySnxUpdateProcessor").procCmisDataRefresh(api,event,inUnit);
                println("Unit ::-------"+aUnit);
            }

            println("After CMIS_DATA_REFRESH - LH New  Vessel Completed")

        }catch(Exception e){
            api.log("Exception in GvyCmisUnitDataRefresh.processCmisDataRefreshByVesVoy() " + e);
        }
    }


    /*
    * Method Fetches a list of all the units in N4
    * 1] Fetches a list of all Active units in Facility HON
    * 2] Fetches a list of all Departed units [last Departed unit in Facility HON]
    */
    public void getAllUnits()
    {
        try
        {
            BaseFinder baseFinder = new BaseFinder()
            List unitList = baseFinder.findAll("Unit")
            println("unit List ::--------"+unitList.size())

            if(unitList != null) {
                Iterator iter = unitList.iterator();
                while(iter.hasNext()) {
                    def unit = iter.next();
                    def eqTypeClass =  unit.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypClass")
                    if(unit.getFieldValue("unitVisitState").equals(UnitVisitStateEnum.ACTIVE) && eqTypeClass.equals(EquipClassEnum.CONTAINER)) {
                        activeUnits.add(unit)
                    }else if(unit.getFieldValue("unitVisitState").equals(UnitVisitStateEnum.DEPARTED) && eqTypeClass.equals(EquipClassEnum.CONTAINER)) {
                        unit = getLastDepartedUnit(unit)
                        if(unit != null){
                            departedUnits.add(unit)
                        }
                    }
                } //While
            }//outer If
            //  List unitFacilityList = baseFinder.findAll("UnitFacilityVisit")
            //  println("unitFacilityList  ::-------------"+unitFacilityList.size())
        }catch(Exception e){
            e.printStackTrace()
        }
    }//Method ends

    /*
    * Method Fetches a list of all the units in N4 by VesVoy
    * For New Ves Client Vessel
    */
    public void getAllUnits(String vesVoy)
    {
        GroovyApi api = new GroovyApi();
        try
        {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_ACTUAL_IB_ID, vesVoy));
            List unitList  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("dq ===="+dq+" unitsList ===="+(unitList != null ? unitList.size() : "NO RESULT"));

            if(unitList != null) {
                vesVistUnitLists.addAll(unitList);
            }//outer If
        }catch(Exception e){
            api.log("Exception in GvyCmisUnitDataRefresh.getAllUnits() " + e);
        }
    }//Method ends

    public void getAllUnitsNewvess(String vesVoy)
    {
        GroovyApi api = new GroovyApi();
        try
        {
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq = dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_DECLARED_IB_ID, vesVoy));
            List unitList  = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("dq ===="+dq+" unitsList ===="+(unitList != null ? unitList.size() : "NO RESULT"));

            if(unitList != null) {
                vesVistUnitLists.addAll(unitList);
            }//outer If
        }catch(Exception e){
            api.log("Exception in GvyCmisUnitDataRefresh.getAllUnits() " + e);
        }
    }//Method ends

/*
 * Method Fetches the Last Departed unit in N4 and Post event
 */
    public Object getLastDepartedUnit(Object unit)
    {
        def deptUnit = null
        try
        {
            def unitId = unit.getFieldValue("unitId")
            def inEquipment = Equipment.loadEquipment(unitId);
            deptUnit = unitFinder.findDepartedUnit(complex, inEquipment)
        }catch(Exception e){
            e.printStackTrace()
        }
        return deptUnit
    }//Method Ends

/*
 *  Execute last event from event history
    so as to rewrite the appropriate unit state in cmis.
 */
    public void postLastRecoredEvent(Object inUnit)
    {
        try
        {
            sm = (ServicesManager)Roastery.getBean("servicesManager");
            List eventList = sm.getEventHistory(inUnit);
            //def lastEvent = eventList.get(1).getEventTypeId()
            //println("Last Event on Unit ::"+lastEvent)
            //Do Not post Event Refresh Again
            for(aEvent in eventList )
            {
                def lastEvent = aEvent.getEventTypeId()
                if(!lastEvent.equals("CMIS_REFRESH_SELECTED"))
                {
                    GroovyEvent event = new GroovyEvent( null, inUnit);
                    // event.postNewEvent(lastEvent, "Cmis Refresh : Unit Correction Executed");
                    println("INSDIE THE REFRESH LOOP : "+lastEvent)
                    break;
                }else{
                    println("DO NOT POST CMIS REFRESH IF ITS THE LAST EVENT : "+aEvent)
                }
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }//Method End



}//Class