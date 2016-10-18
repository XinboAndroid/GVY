import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.units.Routing
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.RoadInspection
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Logger

/**
 * Created by BGopal on 11/10/2015.
 */
class MATGvyUpdateGrossWeight extends AbstractGateTaskInterceptor implements EGateTaskInterceptor{
    public void execute(TransactionAndVisitHolder inDao){
        GroovyApi api = new GroovyApi();
        executeInternal(inDao);
        if (inDao == null ){
            LOGGER.warn("Transaction visit holder is null");
            return;
        }
        TruckTransaction truckTransaction = inDao.getTran();
        if (truckTransaction == null) {
            LOGGER.warn("Transaction is null");
            return;
        }
        /*Unit unit = inDao.getTran().getTranUnit();
        if (unit == null) {
            LOGGER.warn("Transaction unit is null");
            return;
        }*/
        if ( (truckTransaction.getTranCtrGrossWeight() == null || truckTransaction.getTranCtrGrossWeight() <= 0)
                && truckTransaction.getTranCtrTareWeight()!= null && truckTransaction.getTranCtrNetWeight() != null){
            Double grossWeight = truckTransaction.getTranCtrNetWeight() + truckTransaction.getTranCtrTareWeight();
            truckTransaction.setTranCtrGrossWeight(grossWeight);
        }

    }
    private static Logger LOGGER = Logger.getLogger(MATGvyUpdateGrossWeight.class);
}
