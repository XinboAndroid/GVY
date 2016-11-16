import com.navis.inventory.business.api.UnitManager;
import com.navis.inventory.business.api.RectifyParms;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.reference.Equipment
import com.navis.argo.ContextHelper
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.framework.business.Roastery;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.api.UnitFinder;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.services.business.event.GroovyEvent
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.road.business.model.TruckingCompany

public class GvyTruckerCorrection extends GroovyInjectionBase
{

    public String execute(Map inParameters)
    {
        try
        {

            //Processing One unit at a time
            def unitId = (String) inParameters.get("unitId");
            def truckCd = (String) inParameters.get("truck");

            def unit =  findVisitStateActiveUnit(unitId)
            if(unit != null)
            {
                def truck = unit.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")

                def lkpLocType=unit.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
                def lkpLocTypeKey = lkpLocType != null ? lkpLocType.getKey() : ''
                println("UNIT :"+unitId+"  truck : "+truck+"   truckCD ::"+truckCd+"  lkpLocTypeKey ::"+lkpLocTypeKey)
                if(lkpLocTypeKey.equals('YARD') || lkpLocTypeKey.equals('VESSEL')){

                    def trkc = TruckingCompany.findTruckingCompany(truckCd)
                    unit.getUnitRouting().setRtgTruckingCompany(trkc);
                    println("Unit : "+unitId+"  Set Trucker :"+truckCd)
                } else{
                    println("Trucker Code does not need to be set")
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method execute Ends


    // Method returns a Set of Complex Level Master State Active Units
    public Object findVisitStateActiveUnit(String unitId)
    {
        def inUnit = null;
        try{
            def unitFinder = getUnitFinder()
            def complex = ContextHelper.getThreadComplex();
            def inEquipment = Equipment.loadEquipment(unitId);
            if(inEquipment != null){
                inUnit = unitFinder.findActiveUnit(complex,inEquipment)
            }
            println("unitId Lookup for Equipment :::"+unitId+"unitId Lookup for Object :::"+inUnit)
        }catch(Exception e){
            e.printStackTrace()
        }
        return inUnit
    }

}//Class Ends