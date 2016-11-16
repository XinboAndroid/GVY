/*
 *
 * Groovy code called at OG truck visit out.
 *
 * All truck visit out at OG validations must be inside this plugin.
 * Specifically inside the if statement below that checks for lane.
 * If lane is null, we are at security outgate and we should not run
 * any validations!
 *
 * Change History
 * 1: cnb 4/13/09 Moved all OG truck visit out validations to a single plugin to simplify the gate config.
 */

import com.navis.apex.business.model.GroovyInjectionBase

public class OutgateTruckVisitOutValidations extends GroovyInjectionBase {
    // Outgate Truck Visit Out
    public void execute(inDao, api) {
        api.log("Truck Visit Out at Outgate Stage validations...")

        //If OG lane is null, then we are at the security outgate lane. Do not process the validations.
        api.log("OG Check the Lane: " + inDao.getTv().getTvdtlsExitLane())

        if (inDao != null && inDao.getTv() != null && inDao.getTv().getTvdtlsExitLane() != null) {
            /*
             * READ THIS!!!!
             *
             * Add new validations here!!!!!
             * You really want your validations inside the above check.
             */

            // EIT Validation, Can't fix it here.
            //  api.getGroovyClassInstance("EITCheck").execute(inDao, api);


            // NOW Validation
            try {
                api.getGroovyClassInstance("NOWOutgateProcessor").execute(inDao, api, false);
            } catch (Exception e) {
                api.log("Exception in NOWProcessor Outgate , verify=false: " + e)
            }
        } else {
            api.log("Something was null, so we are assuming this is the security outgate and we did not process any validations on the TV OUT at OUTGATE")
            try { api.log("inDao == null? " + inDao) } catch (Exception ignore) {}
            try { api.log("inDao.getTv() == null? " + inDao.getTv()) } catch (Exception ignore) {}
            try { api.log("inDao.getTv().getTvdtlsExitLane() == null? " + inDao.getTv().getTvdtlsExitLane()) } catch (Exception ignore) {}
            api.log("If the third one {lane} was null, then it was security outgate and we are OK to have skipped validations.")
        }
    }
}