/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

package com.navis.road.business.adaptor.document
import com.navis.argo.ContextHelper
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.model.Yard
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.inventory.business.api.RectifyParms
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
/**
 * This groovy places the unit in yard into the yard heap 'Yard'
 *
 * Author: Peter Seiler
 * Date: 08/25/15
 * JIRA: CSDV-3063
 * SFDC: 142561
 *
 */

public class MATPlaceUnitInYardHeap extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATPlaceUnitInYardHeap");

        /* check various components of the gate transaction to insure everything needed is present. */

        if (inDao == null)
            return;

        TruckTransaction ThisTran = inDao.getTran();

        if (ThisTran == null)
            return;

        Yard ThisYard = ContextHelper.getThreadYard();

        /* get the position specified in the transaction */

        UnitFacilityVisit ThisUFV = ThisTran.getTranUfv();

        if (ThisUFV == null)
        {
            return;
        }

        /* get the yard heap called 'Yard' */
        String tranYardRow = ThisTran.getTranFlexString03();

        if (tranYardRow == null){
            tranYardRow = "YARD";
        }
        LocPosition ThisCtrPos = LocPosition.createYardPosition(ThisYard, tranYardRow, null, null, false);

        /* rectify the unit into that yard position */

        RectifyParms thisRectifyParm = new RectifyParms();

        thisRectifyParm.setEraseHistory(false);
        thisRectifyParm.setUfvTransitState(UfvTransitStateEnum.S40_YARD);
        thisRectifyParm.setUnitVisitState(UnitVisitStateEnum.ACTIVE);
        thisRectifyParm.setPosition(ThisCtrPos);

        ThisUFV.rectify(thisRectifyParm);
    }
}