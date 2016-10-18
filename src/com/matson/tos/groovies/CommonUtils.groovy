import com.navis.argo.business.api.ServicesManager
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.business.Roastery
import com.navis.inventory.business.units.Unit
import com.navis.services.business.rules.EventType
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 *
 * This groovy utility library contains commonly used methods
 * Date: 10/06/2016
 *
 */
class CommonUtils extends AbstractExtensionCallback {

    private Logger LOGGER = Logger.getLogger(CommonUtils.class);

    public void recordEvent(Unit inUnit, String eventId) {
        LOGGER.setLevel(Level.INFO);

        if(inUnit) {
            LOGGER.info("recordEvent BEGIN");
            ServicesManager srvcMgr = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
            EventType eventType = EventType.findEventType(eventId);
            srvcMgr.recordEvent(eventType, eventId, null, null, inUnit);
        }
        LOGGER.info("recordEvent End");
    }

}
