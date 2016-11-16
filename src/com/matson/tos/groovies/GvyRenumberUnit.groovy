/*
* SrNo  Doer Date      Change
* A1    GR   10/26/10  Added Newves to the DepartAndRenumber (cmis:keyDup process)
*/
import java.util.StringTokenizer;
import java.util.NoSuchElementException;
import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.Chassis
import com.navis.framework.business.Roastery
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.api.UnitManager
import com.navis.inventory.business.units.UnitEquipment
import com.navis.apex.business.model.GroovyInjectionBase

/**
 *
 * Patch Matson N4 Groovy for 2.1/2.6 upgrade
 *
 * Author: Peter Seiler
 * Date: 4 July 2014
 * JIRA: ARGO-59892
 * SFDC: None
 * Called from: Unkown
 *
 */

public class GvyRenumberUnit {

    public void nltDepartAndRenumber(Object unit, String note) {
        def transitState=unit.getFieldValue("unitActiveUfv.ufvTransitState")
        transitState = transitState != null ? transitState.getKey() : ''


        if(note.startsWith("NIS Load Transaction") || note.startsWith("NewVes")) {
            // Handle NLT & Newves
            GroovyInjectionBase injBase = new GroovyInjectionBase()
            def gvyNLTObj = injBase.getGroovyClassInstance("GvyInjNLTProcess")
            gvyNLTObj.processNLT(unit)
        }
        renumber(note);
    }

    /**
     * Process unit renumbers
     * @param args
     */
    public void renumber(String note) {
        try {
            println(note);
            StringTokenizer renum = new StringTokenizer(note,"Renum");
            //renum.nextToken();
            while(renum.hasMoreTokens()) {
                String token = renum.nextToken();
                if(token.indexOf("[") == -1) continue;
                token = token.replaceAll(".*\\[", "");
                token = token.replaceAll("\\].*", "");
                String[] eqList = token.split("\\|");
                if(eqList.length == 2) {
                    System.out.println(eqList[0] + " "+eqList[1]);

                    // Peter Seiler: renamed call to internal method to be different from public method

                    renumberList(eqList);
                }

            }
        }  catch (NoSuchElementException e) {}

    }

    // Peter Seiler: Renamed internal method to have different name from public one.

    private boolean  renumberList (String[] eqList)  {
        Equipment eq =  Equipment.findEquipment(eqList[0])
        //     Chassis eq =  Chassis.findChassis(eqList[0])
        println("eq="+eq);
        if(eq == null) return false;
        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)

        UnitEquipment unitEquipment = unitFinder.findActiveUeUsingEqInAnyRole(null, ContextHelper.getThreadComplex(), eq)
        if(unitEquipment == null) {

            def list = unitFinder.findAllUeUsingEqInAnyRole(ContextHelper.getThreadComplex(), eq, null);
            if(list != null)  unitEquipment =  (UnitEquipment)list.get(0);
            else return false;
        }
        UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID)
        println(eqList[1]);
        unitManager.renumberUnitEquipment(unitEquipment, eqList[1])
        return true;
    }

}
