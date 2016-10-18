/*
 * Copyright (c) 2016 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.Equipment
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.units.Routing
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger
/**
 * When an accessory is detached from a unit the accessory's routing has to be cleared
 *
 * Peter Seiler
 *
 * Date: 02/08/2016
 * JIRA: ARGO-87850
 * SFDC: 150401
 *
 * Called from: General Notices for event Unit_Acivate if unit is for bare accessory
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MATClearGenSetRouting extends AbstractGeneralNoticeCodeExtension
{
    public void execute(GroovyEvent inGroovyEvent)
    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MATClearGenSetRouting execute Started.");

        if (inGroovyEvent == null)
            return;

        Event thisEvent = inGroovyEvent.getEvent();

        if (thisEvent == null)
            return;

        /* Get the unit */

        Unit ThisUnit = (Unit) inGroovyEvent.getEntity();

        if (ThisUnit == null)
            return;

        UnitFacilityVisit ThisUFV = ThisUnit.getUnitActiveUfvNowActive();

        if (ThisUFV == null)
            return;

        /* check if the unit is for a bare accessory */

        UnitEquipment ThisUE = ThisUnit.getUnitPrimaryUe();

        if(ThisUE == null)
            return;

        Equipment ThisEquip = ThisUE.getUeEquipment();

        if(ThisEquip == null)
            return;

        if (ThisEquip.getEqClass() == EquipClassEnum.ACCESSORY)
        {
            /* the uit being activated is for a bare accessory.  Clear the routig details */

            ThisUnit.updateCategory(UnitCategoryEnum.STORAGE);
            ThisUnit.updateFreightKind(FreightKindEnum.MTY);

            CarrierVisit GenTruck = CarrierVisit.getGenericTruckVisit(ContextHelper.getThreadComplex());

            ThisUnit.updateDeclaredIbCv(GenTruck);

            Routing newRouting = ThisUnit.getUnitRouting();

            newRouting.setRtgDeclaredCv(GenTruck);

            ThisUnit.setUnitRouting(newRouting);

            ThisUFV.updateActualIbCv(GenTruck);
            ThisUFV.updateObCv(GenTruck);
        }
    }
    private Logger LOGGER = Logger.getLogger(MATClearGenSetRouting.class);
}