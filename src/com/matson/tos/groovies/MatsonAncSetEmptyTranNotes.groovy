/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 Unit notes is being copied to gate notes. To supress this set empty tran notes if user didn't enter value for tran notes.
 *
 * @author <a href="mailto:balamurugan.bakthavachalam@navis.com"> Balamurugan B</a> Date: 10/08/2015
 *
 * Date: 10/08/2015: 5:41 PM
 * JIRA: CSDV-3024
 * SFDC: 00138337
 * Called from: Gate Configuration
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MatsonAncSetEmptyTranNotes extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {

    public void execute(TransactionAndVisitHolder inOutDao) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncSetEmptyTranNotes execute Stared.");
        TruckTransaction tran = inOutDao.getTran();
        if (tran.getTranNotes() == null) {
            tran.setTranNotes("");
        }
        LOGGER.info(" MatsonAncSetEmptyTranNotes execute Completed.");
    }

    private Logger LOGGER = Logger.getLogger(MatsonAncSetEmptyTranNotes.class);
}