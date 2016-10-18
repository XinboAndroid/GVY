import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.units.EqBaseOrder
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.road.business.util.RoadBizUtil
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 *
 * This groovy library applies the associated booking's 'full return location' value to the unit's 'VGM verified' (UnitFlexStingXX)
 *
 * SOLAS VGM
 * Date: 12/05/2016
 * Called from:  MatsonSetUnitVGMVerified groovy
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MatsonUnitVGMVerifiedCalcLibrary extends AbstractExtensionCallback {

    private Logger LOGGER = Logger.getLogger(MatsonUnitVGMVerifiedCalcLibrary.class);

    public void resolveVGMRequiredFlagForUnit(Unit inUnit) {

        try {
            if (inUnit == null) {
                return;
            }
            String VgmVerified = "YES";

            EqBaseOrder baseOrder = inUnit.getDepartureOrder();

            if (baseOrder!= null ){
                EquipmentOrder equipmentOrder = EquipmentOrder.resolveEqoFromEqbo(baseOrder);
                if (equipmentOrder!= null) {
                    Booking booking = Booking.resolveBkgFromEqo(equipmentOrder);
                    if (booking != null && booking.getEqoFullReturnLocation() != null && "YES".equalsIgnoreCase(booking.getEqoFullReturnLocation())) {
                        VgmVerified = "NO";
                    }
                }
            }
            inUnit.setUnitFlexString06(VgmVerified);

        } catch (Throwable e) {
            e.printStackTrace();
            LOGGER.error("resolveVGMRequiredFlagForUnit: Problem occured while attempting to set VGM Required flag ", e);
        }
    }
}
