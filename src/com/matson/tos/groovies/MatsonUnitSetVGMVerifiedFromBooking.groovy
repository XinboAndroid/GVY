import com.navis.argo.business.api.GroovyApi;
import com.navis.external.services.AbstractGeneralNoticeCodeExtension;
import com.navis.framework.portal.FieldChanges;
import com.navis.inventory.business.units.EqBaseOrder;
import com.navis.inventory.business.units.Unit;
import com.navis.orders.business.eqorders.Booking;
import com.navis.orders.business.eqorders.EquipmentOrder;
import com.navis.services.business.event.Event;
import com.navis.services.business.event.GroovyEvent;
import com.navis.services.business.rules.EventType;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import com.navis.framework.business.Roastery;
import com.navis.argo.business.api.ServicesManager;

/**
 * This groovy calls the library to retrieve the weight and set VGMRequired field
 * <p/>
 * SOLAS VGM
 * Date: 25/05/2016
 * call to:  MatsonUnitGetWeightWSLibrary groovy
 */

class MatsonUnitSetVGMVerifiedFromBooking extends AbstractGeneralNoticeCodeExtension {

    private Logger LOGGER = Logger.getLogger(MatsonUnitSetVGMVerifiedFromBooking.class);

    public void execute(GroovyEvent inGroovyEvent) {

        try {
            LOGGER.setLevel(Level.INFO);
            LOGGER.info(" MatsonUnitSetVGMVerifiedFromBooking execute Started.");

            String FLAG_YES = "YES";
            String FLAG_NO = "NO";
            String vgmVerified = FLAG_YES;
            String bookingNo = "", equipmentId = "";
            def weight = "";
            String eventIdentifier = "VGM_UPDATE";

            if (inGroovyEvent != null) {
                Event event = inGroovyEvent.getEvent();
                if (event != null) {
                    Unit unit = (Unit) inGroovyEvent.getEntity();
                    if (unit != null) {
                        EqBaseOrder baseOrder = unit.getDepartureOrder();
                        if (baseOrder != null) {
                            EquipmentOrder equipmentOrder = EquipmentOrder.resolveEqoFromEqbo(baseOrder);
                            if (equipmentOrder != null) {
                                Booking booking = Booking.resolveBkgFromEqo(equipmentOrder);
                                bookingNo = booking.getEqboNbr();
                                equipmentId = unit.getUnitId();

                                String fullReturnLocation = booking.getEqoFullReturnLocation();
                                if (fullReturnLocation != null && FLAG_YES.equalsIgnoreCase(fullReturnLocation)) {
                                    try {
                                        vgmVerified = FLAG_NO;
                                        def getWeightLibrary = getLibrary("MatsonUnitGetWeightWSLibrary");
                                        LOGGER.info("About to execute MatsonUnitGetWeightWSLibrary");

                                        weight = getWeightLibrary.getUnitWeight(bookingNo, equipmentId);
                                        // call WS to identify the weight
                                    } catch (Exception e) {
                                        LOGGER.error("MatsonUnitSetVGMVerifiedFromBooking : Problem occured while attempting to set VGM Required flag ", e);
                                    }
                                }

                                LOGGER.info("returned weight value: " + weight);

                                if (weight != null && !weight.isEmpty()) {
                                    BigInteger biWeight = new BigInteger(weight);
                                    if (biWeight > 0) {
                                        vgmVerified = FLAG_YES;
                                    }
                                }
                            }
                        }
                        unit.setUnitFlexString06(vgmVerified);  //Set the VGM verified flag of Unit
                        getLibrary("CommonUtils").recordEvent(unit, eventIdentifier);
                        GroovyApi groovyApi = new GroovyApi();
                        groovyApi.getGroovyClassInstance("MATUtil").refreshUnit(unit);
                    }
                }
            }
            LOGGER.info(" MatsonUnitSetVGMVerifiedFromBooking execute End.");

        } catch (Exception e) {
            e.printStackTrace();
            LOGGER.error("MatsonUnitSetVGMVerifiedFromBooking : Problem occured while attempting to set VGM Required flag ", e);
        }
    }

}
