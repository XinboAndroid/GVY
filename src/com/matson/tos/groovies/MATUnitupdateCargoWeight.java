package com.matson.tos.groovies;

import com.navis.external.services.AbstractGeneralNoticeCodeExtension;
import com.navis.inventory.business.units.Unit;
import com.navis.services.business.event.EventFieldChange;
import com.navis.services.business.event.GroovyEvent;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;

import java.util.Iterator;
import java.util.Set;

/**
 * Created by Naveen Krishnan on 8/23/2016.
 */
public class MATUnitUpdateCargoWeight extends AbstractGeneralNoticeCodeExtension {
    private Logger LOGGER = Logger.getLogger(MATUnitUpdateCargoWeight.class);

    public void execute(GroovyEvent inEvent)

    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("MATUnitUpdateCargoWeight Execution Started");
        Unit unit = null;

        try {
            unit = (Unit) inEvent.getEntity();
            if (unit == null) {
                LOGGER.error("Reference to Unit not found!");
                return;
            } else
                LOGGER.info("Unit: " + unit);
        } catch (Exception e) {
            LOGGER.info(" Exception " + e);
    }

            Set set = inEvent.getEvent().getEvntFieldChanges();
            LOGGER.info("field change size : "+set.size());
            Iterator iter = set.iterator();
            EventFieldChange efc;
            boolean freightKindChanged = false;
            String oldVal = "";
            String newVal = "";
            String metaField = "";
            while ( iter.hasNext()) {
                efc = (EventFieldChange)iter.next();
                LOGGER.info("get feild change id :"+efc.getMetafieldId());
                LOGGER.info("get feild change old Value :"+efc.getPrevVal());
                LOGGER.info("get feild change new Value :"+efc.getNewVal());
                LOGGER.info("get feild change fc new Value :"+efc.getEvntfcNewVal());

                metaField = efc.getMetafieldId();
                oldVal = efc.getPrevVal();
                newVal = efc.getNewVal();
                LOGGER.info(" MetafieldID: " + metaField);
                LOGGER.info("Old Value: " + oldVal);
                LOGGER.info("new Value: " + newVal);
                if (metaField == "unitFreightKind") {
                    freightKindChanged = true;
                    break;
                }
            }
            Double grossWeight = 0.0;
            if (freightKindChanged) {
                if ((oldVal== "FCL") && (newVal == "MTY")) {

                    unit.updateGoodsAndCtrWtKg(grossWeight);

                }



            }

            LOGGER.info("MATUnitUpdateCargoWeight Execution Ended.");
        }
}

