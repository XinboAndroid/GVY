import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ArgoBizMetafield
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.EqBaseOrder
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.Booking
import com.navis.services.business.event.Event
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import org.apache.log4j.Level
import org.apache.log4j.Logger
import com.navis.framework.business.Roastery;
import com.navis.argo.business.api.ServicesManager;

import java.util.Collection;

/**
 * This groovy record the event UNIT_BOOKING_VGM_UPDATE for units that's been associated with the given booking
 * SOLAS VGM
 * Date: 02-June-2016
 */


public class MatsonUnitVGMUpdateOnBookingFieldChange extends AbstractGeneralNoticeCodeExtension {

    private static Logger LOGGER = Logger.getLogger(MatsonUnitVGMUpdateOnBookingFieldChange.class);

    public void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonUnitVGMUpdateOnBookingFieldChange execute Started.");

        String uiFieldName = "eqoFullReturnLocation";
        String FLAG_YES = "YES";
        String FLAG_NO = "NO";
        String vgmUpdateEventId = "UNIT_BOOKING_VGM_UPDATE";

        try {
            if (inGroovyEvent != null) {
                Booking booking = (Booking) inGroovyEvent.getEntity();
                if (booking == null) {
                    return;
                }
                Event event = inGroovyEvent.getEvent();
                if (event != null) {
                    if (inGroovyEvent.wasFieldChanged(uiFieldName)) {
                        EventFieldChange fieldChange = (EventFieldChange) inGroovyEvent.getFieldChange(uiFieldName);
                        if (fieldChange != null) {
                            //String prevVal = (String) fieldChange.getPrevVal();
                            String newVal  = (String) fieldChange.getNewVal();
                            //if (prevVal != null && FLAG_NO.equalsIgnoreCase(prevVal) && newVal != null && FLAG_YES.equalsIgnoreCase(newVal) ) {
                            if(newVal != null && FLAG_YES.equalsIgnoreCase(newVal) ) {

                                if (booking != null) {
                                    EqBaseOrder baseOrder = (EqBaseOrder) booking;
                                    GroovyInjectionBase injBase = new GroovyInjectionBase();
                                    UnitFinder unitFinder = injBase.getUnitFinder();

                                    Collection<Unit> units = unitFinder.findUnitsForOrder(baseOrder);
                                    for(Unit unit : units) {
                                        if(unit != null) {
                                            LOGGER.info("MatsonUnitVGMUpdateOnBookingFieldChange : recording event for unit "+unit.getUnitId());
                                            getLibrary("CommonUtils").recordEvent(unit, vgmUpdateEventId);
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch(Exception e) {
            LOGGER.error("MatsonUnitVGMUpdateOnBookingFieldChange error : "+e.getMessage());
        }
    }

}