package com.navis.road.business.adaptor.document

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder

/**
 * Copy User Gate notes to Transaction notes
 *
 * Author: Gopal
 * Date: 10/30/15
 *
 */

public class MATGvyCopyGateNotesToTranNotes extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{

    // Internal cards aren't driver specific so erase the driver
    public void preProcess(TransactionAndVisitHolder inDao) {
        if (inDao.getTran() == null)
            return;

        inDao.getTran().setTranNotes(inDao.getTran().getTranFlexString08());
    }
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATGvyCopyGateNotesToTranNotes");
        executeInternal(inDao);
    }

}