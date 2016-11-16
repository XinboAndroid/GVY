/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.argo.business.api.IEvent
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.reference.RoutingPoint
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.business.Roastery
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.InventoryField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.rules.EventType

/**
 * This groovy checks the category of units that should be imports but the advance VV leaves them as Exports and leaves the OB carrier vessel
 *
 * Author: Peter Seiler
 * Date: 08/13/15
 * JIRA: CSDV-3123
 * SFDC: 143603
 *
 * Peter Seiler 09/12/2015
 *
 * Create a second unit for a unit moving from one facility to another on the ship
 *
 * Modified: 9/25/2015: Imran Ahmad: do not log duplicate events. Handle Storge Empties also.
 * Modified: 10/6/2015: Imran Ahmad: To handle category computation for the new Unit
 * Modified: 10/14/2015: Bruno Chiarini: Changed ufvFlexString used to flag cloned units from 05 to 02
 *
 */

public class MATUnitFacilityVisitLifeCyleInterceptor extends AbstractEntityLifecycleInterceptor {
    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        //this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
    }

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
    }

    @Override
    void onCreateOrUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges)

    {
        this.log("MATUnitFacilityVisitLifeCyleInterceptor: Started");

        /* get the unit entity */

        UnitFacilityVisit ufv = inEntity._entity;

        if (ufv == null || "CLONED".equals(ufv.getUfvFlexString02())) {
            return;
        }

        Unit unit = ufv.getUfvUnit();

        if (unit == null) {
            return;
        }

        /* get the routing point for the POD for the Unit */

        RoutingPoint pod = unit.getUnitRouting().getRtgPOD1();

        /* get the facility that the UFV is associated with */

        Facility ThisUFVFacility = ufv.getUfvFacility();

        if (inOriginalFieldChanges.hasFieldChange(InventoryField.UFV_FACILITY)) {
            EFieldChange FacilityChg = inOriginalFieldChanges.findFieldChange(InventoryField.UFV_FACILITY);
            ThisUFVFacility = FacilityChg.getNewValue() as Facility;
        }

        /* if this is not the facility that is the POD for the UFV, exit */

        this.log("ThisUFV " + ufv); //added by Bruno
        this.log("ThisUnit " + unit); // added by Bruno
        this.log("ThisUnitPOD " + pod)
        this.log("ThisUFVFacility routing point " + ThisUFVFacility.getFcyRoutingPoint())


        if (ThisUFVFacility.getFcyRoutingPoint() != pod) {
            if (UnitCategoryEnum.THROUGH.equals(unit.getUnitCategory())) {
                // if the unit is a through unit and if the POD is not this Facility's routing point then exit. In all other cases we may want to
                //  change category and create a new Unit
                return;
            } else if (unit.getUnitCreateTime() > (new Date(System.currentTimeMillis() - (1000 * 60)))) {
                // exit if the existing Unit was just created (within one minute) e.g. from Barge Stowplan or other
                return;
            }
        }

        /* get the carrier for the UFV */

        LocPosition Curr_CV = ufv.getUfvLastKnownPosition();

        if (inOriginalFieldChanges.hasFieldChange(InventoryField.UFV_LAST_KNOWN_POSITION)) {
            EFieldChange Pos_CVChg = inOriginalFieldChanges.findFieldChange(InventoryField.UFV_LAST_KNOWN_POSITION);
            Curr_CV = Pos_CVChg.getNewValue() as LocPosition;
        }

        /* if the current carrier is designated as vessel, and the category is Import, Export or Through, log an event on the Unit to trigger a
         Geberal Notice to clone the unit for the next facility.
         */

        this.log("ThisUnit.getUnitCategory() " + unit.getUnitCategory())
        this.log("Curr_CV " + Curr_CV)
        this.log("Curr_CV.getLocType() " + Curr_CV.getPosLocType())

        UfvTransitStateEnum stateEnum = ufv.getUfvTransitState();
        if (inOriginalFieldChanges.hasFieldChange(InventoryField.UFV_TRANSIT_STATE)) {
            stateEnum = (UfvTransitStateEnum) inOriginalFieldChanges.findFieldChange(InventoryField.UFV_TRANSIT_STATE).getNewValue();
        }
        if ((UnitCategoryEnum.IMPORT.equals(unit.getUnitCategory()) || UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory()) ||
                UnitCategoryEnum.THROUGH.equals(unit.getUnitCategory())) && LocTypeEnum.VESSEL.equals(Curr_CV.getPosLocType()) &&
                (UfvTransitStateEnum.S70_DEPARTED.equals(stateEnum) || UfvTransitStateEnum.S20_INBOUND.equals(stateEnum))) {
            /* insert event into unit to trigger a general notice to clone the unit to a new a */

            ServicesManager sm = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);

            EventType cloneUnitEventType = EventType.findEventType('UNIT_CLONE_NEXT_FACILITY');

            if (sm != null && cloneUnitEventType != null && !sm.hasEventTypeBeenRecorded(cloneUnitEventType, unit)) {
                (IEvent) sm.recordEvent(cloneUnitEventType, "Clone Unit for next facility ", null, null, unit, (FieldChanges) null);
            }
        }
    }
}