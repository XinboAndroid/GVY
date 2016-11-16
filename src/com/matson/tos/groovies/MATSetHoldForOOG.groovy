/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

package com.navis.road.business.adaptor.document
import com.navis.argo.business.api.ServicesManager
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.inventory.business.units.Unit
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
/**
 * This groovy checks if the transaction OOG flag is set and applies a OOG_PENDING hold
 *
 * Author: Peter Seiler
 * Date: 07/29/15
 * JIRA: CSDV-3063
 * SFDC: 142561
 *
 * Peter Seiler
 * 09/08/2015
 *
 * Fix null pointer exception if transaction has error
 *
 */

public class MATSetHoldForOOG extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATSetHoldForOOG");

        if (inDao == null)
            return;

        TruckTransaction ThisTran = inDao.getTran();

        this.log("ThisTV " + ThisTran)

        if (ThisTran == null)
            return;

        /* if the transaction is flagged as OOG, place a OOG_PENDING hold on the unit */

        Unit ThisUnit = ThisTran.getTranUnit();

        if (ThisTran.tranIsOog && ThisUnit != null)
        {

            ServicesManager servicesMgr = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);

            servicesMgr.applyHold('OOG_PENDING', ThisUnit, null, null, null);

        }
    }
}