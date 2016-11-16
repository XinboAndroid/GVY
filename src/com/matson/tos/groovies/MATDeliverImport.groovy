/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

package com.navis.road.business.adaptor.document

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
/**
 * This groovy fixes the transaction so that an import can be delivered in a one-stage gate
 *
 * Author: Peter Seiler
 * Date: 06/30/15
 * JIRA: CSDV-3063
 * SFDC: 142561
 *
 */

public class MATDeliverImport extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATDeliverImport");


        TruckTransaction ThisTran = inDao.getTran();

        /* get out if no gate transaction is found */

        if (ThisTran == null)
            return;

        /* copy the container number to the assigned and provided number */

        ThisTran.setTranCtrNbrAssigned(ThisTran.getTranCtrNbr());
        ThisTran.setTranCtrNbrProvided(ThisTran.getTranCtrNbr());

        /* Execute the built-in logic got the business task. */

        executeInternal(inDao);
    }
}