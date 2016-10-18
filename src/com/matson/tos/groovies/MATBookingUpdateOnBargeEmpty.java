import com.navis.external.services.AbstractGeneralNoticeCodeExtension;
import com.navis.inventory.business.units.Unit;
import com.navis.services.business.event.EventFieldChange;
import com.navis.services.business.event.GroovyEvent;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by psethuraman on 8/23/2016.
 */
public class MATBookingUpdateOnBargeEmpty extends AbstractGeneralNoticeCodeExtension {
    private Logger LOGGER = Logger.getLogger(MATBookingUpdateOnBargeEmpty.class);

    public void execute(GroovyEvent inEvent)

    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("MATBookingUpdateOnBargeEmpty Execution Started");

        try {
            Unit ThisUnit = (Unit) inEvent.getEntity();
            if (ThisUnit == null) {
                LOGGER.error("Reference to Unit not found!");
                return;
            } else
                LOGGER.info("Unit: " + ThisUnit);


            Set set = inEvent.getEvent().getEvntFieldChanges();
            LOGGER.info("field change size : "+set.size());
            Iterator iter = set.iterator();
            EventFieldChange efc;
            while ( iter.hasNext()) {
                efc = (EventFieldChange)iter.next();
                LOGGER.info("get feild change id :"+efc.getMetafieldId());
                LOGGER.info("get feild change old Value :"+efc.getPrevVal());
                LOGGER.info("get feild change new Value :"+efc.getNewVal());
                LOGGER.info("get feild change fc new Value :"+efc.getEvntfcNewVal());

               /* String oldVal = efc.getPrevVal();
                String newVal = efc.getNewVal();

                LOGGER.info("Values changed to ::"+oldVal +"::::"+newVal);*/

            }

            /*if(inOutChanges.hasFieldChange(com.navis.inventory.InventoryBizMetafield.BKG_AND_ERO)) {
                LOGGER.info("Got my Booking : "+inOutChanges.getFieldChange(InventoryBizMetafield.BKG_AND_ERO));

            }*/
        String expGateBkgNbr = (String) ThisUnit.getFieldValue("unitPrimaryUe.ueArrivalOrderItem.eqboiOrder.eqboNbr");
        LOGGER.info("BOOKING EMPTY UNIT_CREATE::::::::"+expGateBkgNbr);
    } catch (Exception e) {
        LOGGER.error("Update Failed. Exception [" + e + "].");
    } finally {
        LOGGER.info("MATBookingUpdateOnBargeEmpty Execution Ended.");
    }
}
}
