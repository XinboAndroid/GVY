/*
 * Created Plugin ot bypass gate lane check from plugin OutgateTruckVisitOutValidations
 * Passing Gate service messages to now for the PassPass Gate transaction.
 * Srno   Doer  Date       Changes
 * A1     GR    06/04/10
 */

import com.navis.apex.business.model.GroovyInjectionBase

public class PassPassTruckVisit extends GroovyInjectionBase {
    // Outgate Truck Visit Out
    public void executeOutgate(inDao, api) {
        println("Pass Pass Outgate Truck Visit ")
        try {
            api.getGroovyClassInstance("NOWOutgateProcessor").execute(inDao, api, false);
        } catch (Exception e) {
            api.log("Exception in NOWProcessor Outgate , verify=false: " + e)
        }
    }

    public void executeIngate(inDao, api) {
        println("Pass Pass Ingate Truck Visit ")
        try {
            api.getGroovyClassInstance("NOWProcessor").execute(inDao, api, true, false);
        } catch (Exception e) {
            api.log("Exception in NOWProcessor Outgate , verify=false: " + e)
        }
    }
}