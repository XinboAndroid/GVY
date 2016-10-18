/*
 * Copyright (c) 2016 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * When the dray status is changed to dray-out-and-back or the unit is resurrected the BL from the unit is placed in the 'Booking Num' flex field
 * This flex field can then be used on the Dray-In gate screen (UnitFlexString09)
 *
 * Peter Seiler
 *
 * Date: 02/09/2016
 * JIRA:
 * SFDC:
 *
 * Called from: General Notices for user-created event UNIT_REROUTE
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MATSetBookingNumFlex extends AbstractGeneralNoticeCodeExtension
{
    public void execute(GroovyEvent inGroovyEvent)
    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MATSetBookingNumFlex execute Started.");

        if (inGroovyEvent == null)
            return;

        Event thisEvent = inGroovyEvent.getEvent();

        if (thisEvent == null)
            return;

        /* Get the unit */

        Unit ThisUnit = (Unit) inGroovyEvent.getEntity();

        if (ThisUnit == null)
            return;

        /* it has to have a dray status */

        if (ThisUnit.getUnitDrayStatus() == null)
            return;

        if(ThisUnit.getUnitGoods() != null)
        {
            ThisUnit.setUnitFlexString09(ThisUnit.getUnitGoods().getGdsBlNbr());
        }
    }
    private Logger LOGGER = Logger.getLogger(MATSetBookingNumFlex.class);
}