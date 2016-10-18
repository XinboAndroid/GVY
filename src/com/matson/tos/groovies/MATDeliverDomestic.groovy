/*
* Copyright (c) 2016 Navis LLC. All Rights Reserved.
*
*/

package com.navis.road.business.adaptor.document

import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.inventory.business.units.Unit
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
/**
 * This allow a DOMESTIC unit to be delivered with the DI transaction
 *
 * Author: Peter Seiler
 * Date: 02/06/16
 * JIRA: CSDV-3208
 *
 *
 */

public class MATDeliverDomestic extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATDeliverDomestic");

        TruckTransaction ThisTran = inDao.getTran();

        /* get out if no gate transaction is found */

        if (ThisTran == null)
            return;

        /* get the unit */

        Unit ThisUnit = ThisTran.getTranUnit();

        /* if it is a DOMESTIC unit retuen to allow the deliver transaction to be executed. */

        if (ThisUnit != null && ThisUnit.getUnitCategory() == UnitCategoryEnum.DOMESTIC)
            return;

        /* process the DI transaction */

        executeInternal(inDao);
    }
}