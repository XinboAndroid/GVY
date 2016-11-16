/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.LocPosition
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.inventory.business.moves.MoveEvent
import com.navis.inventory.business.units.MoveInfoBean
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * Upon discharging APL empty containers from vessel, deliver those conatiners thru gate.
 *
 * @author <a href="mailto:balamurugan.bakthavachalam@navis.com"> Balamurugan B</a> Date: 10/07/2015
 *
 * Date: 10/07/2015: 5:41 PM
 * JIRA: CSDV-3298
 * SFDC: 00145302
 * Called from: MatsonAncVesselDischGNHandler groovy
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MatsonAncVesselDischLibrary extends AbstractExtensionCallback {
    public void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncVesselDischLibrary execute started.");
        departUnit(inGroovyEvent);
        LOGGER.info(" MatsonAncVesselDischLibrary execute completed.");
    }

    private void departUnit(GroovyEvent inGroovyEvent) {
        Unit unit = inGroovyEvent.getEntity() as Unit;
        UnitFacilityVisit ufv = unit.getUnitActiveUfv();
        if (ufv != null) {
            CarrierVisit cv = ufv.getUfvObCv();
            if (LocTypeEnum.TRUCK.equals(cv.getCvCarrierMode())) {
                ufv.depart(null);
                LocPosition loc = LocPosition.createTruckPosition(ufv.getUfvObCv(), null, null);
                MoveInfoBean moveInfo = MoveInfoBean.createDefaultMoveInfoBean(WiMoveKindEnum.Delivery, ArgoUtils.timeNow());
                MoveEvent.recordMoveEvent(ufv, ufv.getUfvLastKnownPosition(), loc, ufv.getUfvObCv(), moveInfo, EventEnum.UNIT_DELIVER);
                //2015-10-20 bbakthavachalam update the last known position to truck
                ufv.updateLastKnownPosition(loc, null);
                ufv.setUfvTimeOfLastMove(ArgoUtils.timeNow());
            } else {
                LOGGER.error("MatsonAncVesselDischLibrary, To depart the container thru gate, the outbound cv should be TRUCK/GEN-TRUCK");
            }
        }
    }
    private Logger LOGGER = Logger.getLogger(MatsonAncVesselDischGNHandler.class);
}