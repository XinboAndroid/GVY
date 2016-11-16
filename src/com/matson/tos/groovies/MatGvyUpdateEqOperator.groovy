import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.Container
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.inventory.business.units.EqBaseOrder
import com.navis.inventory.business.units.EquipmentState
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder

/**
 * Created by psethuraman on 11/2/2016.
 */
class MatGvyUpdateEqOperator extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {
    public void execute(TransactionAndVisitHolder inDao) {
        this.log("Execution Started MatGvyUpdateEqOperator");
        if (inDao == null)
            return;

        TruckTransaction tran = inDao.getTran();

        if (tran == null)
            return;

        /* Execute the built-in logic got the business task. */

        executeInternal(inDao);

        EquipmentOrderItem orderItem = tran.getTranEqoItem();
        if (orderItem != null) {
            EqBaseOrder baseOrder = orderItem.getEqboiOrder();
            if (baseOrder != null) {
                EquipmentOrder order = EquipmentOrder.resolveEqoFromEqbo(baseOrder);
                this.log("Order : "+order.getEqboNbr());
                if (order != null) {
                    Container container = tran.getTranContainer();
                    if (container != null) {
                        if (order != null) {
                            ScopedBizUnit line = order.getEqoLine();
                            this.log("Line for Booking : "+line);
                            if (line != null) {
                                EquipmentState eqState = EquipmentState.findEquipmentState(container, ContextHelper.getThreadOperator());
                                if (eqState != null) {
                                    eqState.setEqsEqOperator(line);
                                    this.log("Update booking Operator to Eq Operator : "+line);
                                }
                            }
                        }
                    }
                }
            }
        }

        this.log("Execution Ended MatGvyUpdateEqOperator");

    }
}
