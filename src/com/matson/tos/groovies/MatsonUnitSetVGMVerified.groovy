import com.navis.argo.ArgoBizMetafield;
import com.navis.external.services.AbstractGeneralNoticeCodeExtension;
import com.navis.framework.portal.FieldChanges;
import com.navis.inventory.business.units.Unit;
import com.navis.services.business.event.Event;
import com.navis.services.business.event.GroovyEvent;
import com.navis.services.business.rules.EventType;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import com.navis.framework.business.Roastery;
import com.navis.argo.business.api.ServicesManager;

/**
 * This groovy calls the MatsonUnitVGMVerifiedCalcLibrary to set the VGM Required field of Unit from Booking's full return location field
 * SOLAS VGM
 * Date: 12-May-2016
 */


public class MatsonUnitSetVGMVerified extends AbstractGeneralNoticeCodeExtension {

    private static Logger LOGGER = Logger.getLogger(MatsonUnitSetVGMVerified.class);
    String vgmUpdateEvent = "VGM_UPDATE";

    public void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.warn(" MatsonUnitSetVGMVerified execute Started.");

        if (inGroovyEvent != null) {
            Event event = inGroovyEvent.getEvent();
            if (event != null) {
                Unit unit = (Unit) inGroovyEvent.getEntity();
                if (unit != null) {
                    def matsonUnitVGMVerifiedCalc = getLibrary("MatsonUnitVGMVerifiedCalcLibrary");
                    LOGGER.warn("MatsonAncVGMRequiredOnUnitRoll about to execute MatsonUnitVGMVerifiedCalcLibrary");
                    matsonUnitVGMVerifiedCalc.resolveVGMRequiredFlagForUnit(unit);
                    getLibrary("CommonUtils").recordEvent(unit, vgmUpdateEvent);
                }
            }
        }
    }

}