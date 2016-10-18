/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

package com.navis.road.business.adaptor.document

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.IEvent
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.model.Yard
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.api.RectifyParms
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.services.business.rules.EventType

/**
 * This groovy places the unit in yard position specified on receive transaction
 *
 * Author: Peter Seiler
 * Date: 06/29/15
 * JIRA: CSDV-3063
 * SFDC: 142561
 *
 * 7/2/2015 - Insert UNIT_RECEIVE and UNIT_IN_GATE events into the units' history.
 *
 * 7/12/2015 - Remove 'executeInternal'
 *
 * 7/30/2015 - Add code to update UCC and ECC codes
 *
 * 9/2/2015 - Remove the ECC and UCC code maitenance
 *
 * 10/16/2015 BG - Added validation for UNIT_IN_GATE through service business rule check
 */

public class MATPlaceUnitInYard extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATPlaceUnitInYard");


        /* check various components of the gate transaction to insure everything needed is present. */

        if (inDao == null)
            return;

        TruckVisitDetails ThisTV = inDao.getTv();

        if (ThisTV == null)
            return;

        /* Get the trasnactions from the truck visit. */

        Set<TruckTransaction> TheseTransactions = ThisTV.getTvdtlsTruckTrans();

        if (TheseTransactions != null && !TheseTransactions.isEmpty())
        {

            Yard ThisYard = ContextHelper.getThreadYard();

            for (TruckTransaction ThisTran : TheseTransactions)
            {

                if (ThisTran.isReceival() && ThisTran.getTranStatus() == TranStatusEnum.COMPLETE)
                {

                    /* get the position specified in the transaction */

                    UnitFacilityVisit ThisUFV = ThisTran.getTranUfv();

                    String ThisYardRow = ThisTran.getTranFlexString03();

                    LocPosition ThisCtrPos = LocPosition.createYardPosition(ThisYard, ThisYardRow, null, null, false);

                    /* rectify the unit into that yard position */

                    RectifyParms thisRectifyParm = new RectifyParms();

                    thisRectifyParm.setEraseHistory(false);
                    thisRectifyParm.setUfvTransitState(UfvTransitStateEnum.S40_YARD);
                    thisRectifyParm.setUnitVisitState(UnitVisitStateEnum.ACTIVE);
                    thisRectifyParm.setPosition(ThisCtrPos);

                    ThisUFV.rectify(thisRectifyParm);

                    /* insert a unit_receive event into the history */

                    ServicesManager sm = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);

                    Unit ThisUnit = ThisTran.getTranUnit();


                    EventType InGateEventType = EventType.findEventType('UNIT_IN_GATE');

                    if (sm != null && InGateEventType != null && ThisUnit != null)
                    {
                        BizViolation bizViolation = sm.verifyEventAllowed(InGateEventType, ThisUnit);
                        if (bizViolation!= null) {
                            RoadBizUtil.appendExceptionChainAsWarnings(bizViolation);
                            //throw bizViolation;
                        }
                        (IEvent) sm.recordEvent(InGateEventType, "Unit received ", null, null, ThisUnit, null, null);
                    }

                    EventType receiveEventType = EventType.findEventType('UNIT_RECEIVE');

                    if (sm != null && receiveEventType != null && ThisUnit != null)
                    {
                        (IEvent) sm.recordEvent(receiveEventType, "Unit received ", null, null, ThisUnit, null, null);
                    }
                }
            }
        }
    }
}