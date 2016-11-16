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

/*
* Class Departs all the UFV Units and sets the Master Visit State Departed
*/
public class GvyCmisEventYbEbDone
{

    //Method Set all the Complex Level Active units to Departed
    public String processNLT(Object unit)
    {
        println("GvyInjNLTProcess.execute(inparameters)")
        try
        {
            //Processing One unit at a time
            def unitId = unit.getFieldValue("unitId");
            processUnit(unit)

        }catch(Exception e){
            e.printStackTrace()
        }

    }//Method execute Ends

    public void processUnit(Object unit)
    {
        try
        {
            //Set ufvSet = findVisitStateActiveUnit(unit)
            Set ufvSet = unit != null ? unit.getUnitUfvSet() : null;
            for(aUfv in ufvSet)
            {
                if (!UnitVisitStateEnum.DEPARTED.equals(aUfv.getUfvVisitState())) {
                    RectifyParms rparms = new RectifyParms();
                    rparms.setUfvTransitState(UfvTransitStateEnum.S70_DEPARTED);
                    rparms.setUnitVisitState(UnitVisitStateEnum.DEPARTED)
                    aUfv.rectify(rparms);
                    println("Executed DEPARTED on NLT Unit: "+unit.getFieldValue("unitId"))
                }//If Ends
            }

        }catch(Exception e){
            e.printStackTrace()
        }
    }


    // Method returns a Set of Complex Level Master State Active Units
    public Set findVisitStateActiveUnit(Object unitId)
    {
        Set unitUfvSet = null
        try{
            def injBase = new GroovyInjectionBase()
            def unitFinder = injBase.getUnitFinder()
            def complex = ContextHelper.getThreadComplex();
            def inEquipment = Equipment.loadEquipment(unitId);
            def inUnit = unitFinder.findActiveUnit(complex,inEquipment)
            unitUfvSet = inUnit != null ? inUnit.getUnitUfvSet() : null;
        }catch(Exception e){
            e.printStackTrace()
        }
        return unitUfvSet
    }

}//Class Ends