/*
* Sr   Doer Date      Change
* A1   GR   08/03/11  added method to handel Haz gems posting
*/
import java.util.Map;
import java.util.ArrayList;
import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.business.reference.Equipment;
import com.navis.argo.ContextHelper;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.services.business.event.GroovyEvent;

class GvyInjRemoveHazards extends GroovyInjectionBase {
    public String execute(Map inParameters) {
        try{
            def fileName = (String)inParameters.get("fileName");
            if(fileName != null && fileName.endsWith("INS.TXT")){
                reconcileHazToGems(inParameters);
            }else if (fileName != null && fileName.endsWith("LT.TXT")){
                cancelHazards(inParameters);
            }
        }catch(Exception e){
            e.printStackTrace();
        }

    }


    public void cancelHazards(Map inParameters){

        def vesvoy = (String)inParameters.get("vesvoy");
        def unitStr = (String)inParameters.get("units");
        // List of units
        ArrayList units = (ArrayList)unitStr.split(",");

        def lookup = getGroovyClassInstance("GvyVesselLookup");
        def availLookup = getGroovyClassInstance("GvyAvailDate");
        def unitFinder = getUnitFinder();

        def cv = lookup.getCarrierVisit(vesvoy);
        def list = unitFinder.findAllUnitsByDeclaredIbCarrier(cv);
        def iter = list.iterator();


        while(iter.hasNext()) {
            try {
                def unit = iter.next();
                def unitName = unit.unitId;
                if(units.contains(unitName)) continue;
                if(unit.getGoods() != null) unit.getGoods().attachHazards(null);

            } catch (Exception e) {
                java.io.StringWriter w = new StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(w);
                e.printStackTrace(pw);
                log(w.toString());
            }
        }

        // Now update advanced vessels.
        list = availLookup.getAdvancedUnits(vesvoy);
        iter = list.iterator();
        while(iter.hasNext()) {
            try {
                def unit = iter.next();
                def unitName = unit.unitId;
                if(units.contains(unitName)) continue;
                if(unit.getGoods() != null) unit.getGoods().attachHazards(null);

            } catch (Exception e) {
                java.io.StringWriter w = new StringWriter();
                java.io.PrintWriter pw = new java.io.PrintWriter(w);
                e.printStackTrace(pw);
                log(w.toString());
            }
        }

    }//Method Ends


    public void reconcileHazToGems(Map inParameters){
        try{
            def transitState = null;
            def unitFinder = getUnitFinder();
            def complex = ContextHelper.getThreadComplex();
            def unitStr = (String)inParameters.get("units");
            ArrayList units = (ArrayList)unitStr.split(",");
            Thread.sleep(10000) //Added Sleep for unit posting
            def iter = units.iterator();
            while(iter.hasNext()) {
                def unit = iter.next();
                def inEquipment = Equipment.loadEquipment(unit);
                def inUnit = unitFinder.findActiveUnit(complex,inEquipment);
                transitState = inUnit.getFieldValue("unitActiveUfv.ufvTransitState")
                Boolean isHaz = inUnit.getFieldValue("unitGoods.gdsIsHazardous")
                if(isHaz != null && isHaz.booleanValue() && !UfvTransitStateEnum.S70_DEPARTED.equals(transitState)
                        && !UfvTransitStateEnum.S99_RETIRED.equals(transitState)){

                    def event = new GroovyEvent( null, inUnit);
                    event.postNewEvent( "HAZ_UPDATE", "HazInsProc");
                    println("HAZ_UPDATE -------------")
                }
                Thread.sleep(3000) //Sleep after each unit posting
            }
        }catch(Exception e){
            e.printStackTrace();
        }

    }//Method Ends

}