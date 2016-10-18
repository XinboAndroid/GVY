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
 * When an Auto rack is stripped the frame is collapsed.
 *
 * Peter Seiler
 *
 * Date: 02/08/2016
 * JIRA: CSDV-3208
 * SFDC:
 *
 * Called from: General Notices for user-created event COLLAPSE AUTO FRAME
 *
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 *
 * Peter Seiler
 *
 * Date: 02/16/2016
 *
 * Fix the unit gross weight to be the tare weight.
 *
 */
class MATCollapseAutoFrame extends AbstractGeneralNoticeCodeExtension
{
    public void execute(GroovyEvent inGroovyEvent)
    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MATCollapseAutoFrame execute Started.");

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

        if (ThisUnit.getUnitFreightKind() != FreightKindEnum.MTY)
            return;

        /* get the unit equipment */

        UnitEquipment ThisUE = ThisUnit.getUnitPrimaryUe();

        if(ThisUE == null)
            return;

        Equipment ThisEquip = ThisUE.getUeEquipment();

        if(ThisEquip == null)
            return;

        /* if the height is not 8'6" set it to 8'6" */

        if (ThisEquip.eqHeightMm != 2591)
        {
            ThisEquip.upgradeEqHeight((Long) 2591, DataSourceEnum.USER_LCL)
        }

        if (ThisUnit.getUnitGoodsAndCtrWtKg() != ThisEquip.getEqTareWeightKg() && ThisEquip.getEqTareWeightKg() > 0)
        {

            /* if the unit gross weight is not the tare weight set the unit gross = to equipment tare */

            ThisUnit.updateGoodsAndCtrWtKg(ThisEquip.getEqTareWeightKg());
        }
    }
    private Logger LOGGER = Logger.getLogger(MATCollapseAutoFrame.class);
}