/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.reference.Equipment
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder

/**
 * This groovy sets Equipment Container Type on the receive transactions.
 *
 * Author: Peter Seiler
 * Date: 06/07/15
 * JIRA: ARGO-75843
 * SFDC: 141197
 *
 */

public class MATPopulateEqTypeInGateTrans extends AbstractGateTaskInterceptor implements EGateTaskInterceptor
{

    GroovyApi gapi = new GroovyApi();

    public void execute(TransactionAndVisitHolder inOutDao)
    {
        executeInternal(inOutDao);

        gapi.log("Begin Groovy MATPopulateEqTypeinGateTrans");

        if (inOutDao.getTv() == null || inOutDao.getTran() == null)
        {
            gapi.log("Null Truck Visit or Null Transaction");
            return;
        }

        TruckTransaction thisTran = inOutDao.getTran();

        Equipment thisContainer = thisTran.getTranEq();

        if (thisContainer == null)
        {
            gapi.log("Null equipment");
            return;
        }

        thisTran.setTranCtrTypeId(thisContainer.getEqEquipType().getEqtypId());

    }
}
