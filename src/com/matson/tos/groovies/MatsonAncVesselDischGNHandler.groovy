/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.external.services.AbstractGeneralNoticeCodeExtension
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
 * Called from: General Notices
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MatsonAncVesselDischGNHandler extends AbstractGeneralNoticeCodeExtension {
    @Override
    public void execute(GroovyEvent inGroovyEvent) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncVesselDischGNHandler execute Stared.");
        if (inGroovyEvent == null) {
            LOGGER.error(" MatsonAncVesselDischGNHandler, Object inGroovyEvent is null.");
            return;
        }
        def matsonAncVesselDischLibrary = getLibrary("MatsonAncVesselDischLibrary");
        if (matsonAncVesselDischLibrary == null) {
            LOGGER.error(" MatsonAncVesselDischGNHandler, Couldn't find the groovy MatsonAncVesselDischLibrary.");
            return;
        }
        LOGGER.info("MatsonAncVesselDischGNHandler about to execute MatsonAncVesselDischGNHandler");
        matsonAncVesselDischLibrary.execute(inGroovyEvent);
        LOGGER.info(" MatsonAncVesselDischGNHandler execute completed.");
    }
    private Logger LOGGER = Logger.getLogger(MatsonAncVesselDischGNHandler.class);
}