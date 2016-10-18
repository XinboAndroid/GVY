/*
* Copyright (c) 2016 Navis LLC. All Rights Reserved.
*
*/


import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.ReeferRqmnts
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.Event
import com.navis.services.business.event.EventFieldChange
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger
/**
 * Copies the reefer temp setting flex field (ufvFlexSting07) to the reefer requirements and head temperature fields
 *
 * Author: Peter Seiler
 * Date: 28-Jan-2016
 * JIRA: CSDV-3026
 * SFDC: 138256
 *
 */

public class MATSyncReeferTempFields extends AbstractGeneralNoticeCodeExtension
{
    private Logger LOGGER = Logger.getLogger(MATSyncReeferTempFields.class);

    public void execute(GroovyEvent inEvent)

    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("MATSyncReeferTempFields Execution Started");

        Unit ThisUnit = (Unit)inEvent.getEntity();
        if (ThisUnit == null)
        {
            LOGGER.error("Reference to Unit not found!");
            return;
        }

        /* get the event */

        Event ThisEvent = inEvent.getEvent();

        EventFieldChange ThisChange = null;

        Set<EventFieldChange> TheseChanges = ThisEvent.evntFieldChanges as Set<EventFieldChange>;

        /* See if the 'Reefer Temp' flex field (ufvFlexString07) is updated */

        for (EventFieldChange TestChange : TheseChanges)
        {
            if ((java.lang.String) TestChange.getMetafieldId() == "ufvFlexString07")
            {
                ThisChange = TestChange;
            }
        }

        /* reefer temp flex was changed */

        if (ThisChange != null)
        {

            /* get the reefer requirements entity */

            GoodsBase ThisGoods = ThisUnit.getUnitGoods();

            if (ThisGoods != null)
            {
                ReeferRqmnts ThisRfrReq = ThisGoods.getGdsReeferRqmnts();

                if (ThisRfrReq != null)
                {

                    /* if the new setting is numeric place that value in the Minimum tempuraue and the reefer reqirements filed. */

                    if(ThisChange.getNewVal().isNumber())
                    {
                        Double ThisTempValue = ThisChange.getNewVal().toDouble();
                        ThisRfrReq.setRfreqTempLimitMinC(this.fahrenheitToCelsius(ThisTempValue));
                        ThisRfrReq.setRfreqTempRequiredC(this.fahrenheitToCelsius(ThisTempValue));
                    }
                    else
                    {
                        /* if the new reefer value is not numeric clear the reefer requirements */

                        ThisRfrReq.setRfreqTempLimitMinC(null);
                        ThisRfrReq.setRfreqTempRequiredC(null);
                    }
                }
            }
        }
    }

    private Double fahrenheitToCelsius(Double fahrenheit)
    {
        Double result = (fahrenheit - 32) * 5.0/9;
        return result;
    }
}
