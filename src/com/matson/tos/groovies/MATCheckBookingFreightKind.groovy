import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.util.BizFailure
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.EqBaseOrder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.orders.business.api.OrdersFinder
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.business.adaptor.order.ReadOrder
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.argo.business.reference.Equipment;
import org.apache.log4j.Logger

/**
 *
 */
public class MATCheckBookingFreightKind extends AbstractGateTaskInterceptor implements EGateTaskInterceptor{


    public void execute(TransactionAndVisitHolder inDao){

        LOGGER.warn(" MatCheckBookingFreightKind Execution Started ");

        executeInternal(inDao);
        if (inDao == null ){
            LOGGER.warn("Transaction visit holder is null");
            return;
        }
        TruckTransaction tran = inDao.getTran();
        if (tran == null) {
            LOGGER.warn("Transaction is null");
            return;
        }
        String bookingNbr = tran.getTranEqoNbr();
        LOGGER.warn(" Tran Booking : " + bookingNbr);

        Unit unit = tran.getTranUnit();
        if (unit != null) {
            Booking booking = null;
            if (unit.getUnitPrimaryUe().getUeArrivalOrderItem() == null) {
                UnitFacilityVisit ufv = (UnitFacilityVisit) unit.getUnitUfvSet().iterator().next();
                LOGGER.warn("UFV :::: " + ufv);
                booking = Booking.findBooking(bookingNbr, unit.getUnitLineOperator(), ufv.getUfvActualObCv());
                LOGGER.warn("Find Booking from Line/CV : "+booking);
                OrdersFinder finder = (OrdersFinder) Roastery.getBean(OrdersFinder.BEAN_ID);
                EquipmentOrderItem orderItem = finder.findEqoItemByEqType(booking, unit.getUnitPrimaryUe().getUeEquipment().getEqEquipType());
                LOGGER.warn("Order Item found : "+orderItem);
                if (orderItem !=null) {
                    unit.getUnitPrimaryUe().setFieldValue(InventoryField.UE_DEPARTURE_ORDER_ITEM, orderItem);
                } else if (booking != null && booking.getEqboOrderItems() != null && booking.getEqboOrderItems().iterator().hasNext()) {
                    unit.getUnitPrimaryUe().setFieldValue(InventoryField.UE_DEPARTURE_ORDER_ITEM, booking.getEqboOrderItems().iterator().next());
                }
                //unit.getUnitPrimaryUe().setUeArrivalOrderItem(orderItem);
            } else if (unit.getUnitPrimaryUe().getUeDepartureOrderItem() != null) {
                LOGGER.warn(" Arrival item : " + unit.getUnitPrimaryUe().getUeDepartureOrderItem());

                if (unit.getUnitPrimaryUe().getUeDepartureOrderItem().getEqboiOrder() != null) {

                    String eqBaseGkey = unit.getUnitPrimaryUe().getUeDepartureOrderItem().getEqboiOrder().getEqboGkey();
                    LOGGER.warn(" Booking Gkey: " + eqBaseGkey);
                    EqBaseOrder baseOrder = HibernateApi.getInstance().load(EqBaseOrder.class, eqBaseGkey)
                    EquipmentOrder eqOrder = EquipmentOrder.resolveEqoFromEqbo(baseOrder);
                    booking = (Booking) Booking.resolveBkgFromEqo(eqOrder);
                    LOGGER.warn(" Booking : " + booking);
                }else {
                    LOGGER.warn(" booking not found : "+booking);
                }

            } else {
                LOGGER.error("Order item not found for unit : "+unit.getUnitId());
            }
            if (booking != null) {

                LOGGER.warn(" Equipment Order Status: " + booking.getEqoEqStatus());
                FreightKindEnum fke = booking.getEqoEqStatus();
                if (!FreightKindEnum.MTY.equals(fke)) {
                    this.reportUserError("For Receive Empty Containers, Full Booking Cannot be Used ");
                    return;
                }
                tran.setTranOrigin(booking.getEqoOrigin());
                tran.setTranDestination(booking.getEqoDestination());
                if (unit.getUnitGoods() != null) {
                    unit.getUnitGoods().setOrigin(booking.getEqoOrigin());
                    unit.getUnitGoods().setDestination(booking.getEqoDestination())
                }
                RoadBizUtil.commit();
            }
        } else {
            LOGGER.error("Unit not found : "+unit);
        }
    }

    private void reportUserError(String message) {
        RoadBizUtil.messageCollector.appendMessage(BizFailure.create(message));

    }
    private static Logger LOGGER = Logger.getLogger(MATCheckBookingFreightKind.class);

}