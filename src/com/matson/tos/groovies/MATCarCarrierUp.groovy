/*
 * Copyright (c) 2016 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.reference.Equipment
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Before loading an auto frame the corner posst need to be extended.
 *
 * Peter Seiler
 *
 * Date: 02/23/2016
 * JIRA: CSDV-3208
 * SFDC:
 *
 * Called from: General Notices for user-created event CAR CARRIER UP
 *
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 *
 */
class MATCarCarrierUp extends AbstractGeneralNoticeCodeExtension
{
    public void execute(GroovyEvent inGroovyEvent)
    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MATCarCarrierUp execute Started.");

        if (inGroovyEvent == null)
            return;

        Event thisEvent = inGroovyEvent.getEvent();

        if (thisEvent == null)
            return;

        /* Get the unit */

        Unit ThisUnit = (Unit) inGroovyEvent.getEntity();

        if (ThisUnit == null)
            return;

        /* it has to be empty */

        /*if (ThisUnit.getUnitFreightKind() != FreightKindEnum.MTY)
            return;*/

        /* get the unit equipment */

        UnitEquipment ThisUE = ThisUnit.getUnitPrimaryUe();

        if(ThisUE == null)
            return;

        Equipment ThisEquip = ThisUE.getUeEquipment();

        if(ThisEquip == null)
            return;

        /* if the height is not 13'6" set it to 13'6" */

        if (ThisEquip.eqHeightMm != 4115)
        {
            ThisEquip.upgradeEqHeight((Long) 4115, DataSourceEnum.USER_LCL)
        }
    }
    private Logger LOGGER = Logger.getLogger(MATCollapseAutoFrame.class);
}