import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import org.apache.log4j.Logger

/**
 * User: babugo - Do not cancel the truck transactions instead close them when the autoclose truck visit job runs
 * Date: 10/31/11
 * Time: 5:46 PM
 * To change this template use File | Settings | File Templates.
 */
class TruckVisitsAutoClose {
    public execute() {
        try {
            Long inThresholdValueMilliSecs = TruckVisitDetails.getActiveTruckVisitSettingInMilliSecs();
            for (TruckVisitDetails tvdtls : TruckVisitDetails.findExpiredTruckVisits(inThresholdValueMilliSecs)) {
                if (tvdtls != null) {
                    for (TruckTransaction tran : tvdtls.getTransactionsToBeClosed()) {
                        if (tran != null) {
                            tran.close(false);
                        }
                    }
                    tvdtls.close(false);
                }
            }
        }catch (Exception closeExp) {
            LOGGER.warn("Error while closing truck visits and transactions through autoclose truck visit background job - Groovy")
        }
    }
    private static final Logger LOGGER = Logger.getLogger(TruckVisitsAutoClose.class);
}
