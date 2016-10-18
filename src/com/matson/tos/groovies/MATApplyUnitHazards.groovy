/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

package com.navis.road.business.adaptor.document

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.business.Roastery
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.HazardItemPlacard
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder

/**
 * This groovy fixes the problem that the 'Move Method' is not copied by the CloneHazards metod
 *
 * Author: Peter Seiler
 * Date: 08/26/15
 * JIRA: ARGO-79452
 * SFDC: 144566
 *
 */

public class MATApplyUnitHazards extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    public void execute(TransactionAndVisitHolder inDao)

    {
        this.log("Execution Started MATApplyUnitHazards");

        if (inDao == null)
            return;

        TruckTransaction ThisTran = inDao.getTran();

        if (ThisTran == null)
            return;

        /* get the booking and the unit for the transaction */

        EquipmentOrder ThisEqOrdr = ThisTran.getTranEqo();

        Unit ThisUnit = ThisTran.getTranUnit();

        if (ThisEqOrdr == null || ThisUnit == null)
        {
            return;
        }

        /* get the hazards for the booking */

        Booking ThisBooking = Booking.resolveBkgFromEqo(ThisEqOrdr);

        Hazards BookingHazards = ThisBooking.getEqoHazards();

        if (BookingHazards == null)
        {
            return;
        }

        /* get the hazards for the unit */

        GoodsBase ThisGDS = ThisUnit.getUnitGoods();

        /* Copy the booking hazards onto the unit */

        Hazards hazards = Hazards.createHazardsEntity();

        for (Iterator<HazardItem> itr = BookingHazards.getHazardItemsIterator(); itr.hasNext(); )
        {
            HazardItem tranHazardItem = itr.next();
            HazardItem clonedItem = HazardItem.createHazardItemEntity(hazards, tranHazardItem.getHzrdiImdgClass(), tranHazardItem.getHzrdiUNnum());
            clonedItem.setHzrdiDcLgRef(tranHazardItem.getHzrdiDcLgRef());
            clonedItem.setHzrdiDeckRestrictions(tranHazardItem.getHzrdiDeckRestrictions());
            clonedItem.setHzrdiEmergencyTelephone(tranHazardItem.getHzrdiEmergencyTelephone());
            clonedItem.setHzrdiEMSNumber(tranHazardItem.getHzrdiEMSNumber());
            clonedItem.setHzrdiERGNumber(tranHazardItem.getHzrdiERGNumber());
            clonedItem.setHzrdiExplosiveClass(tranHazardItem.getHzrdiExplosiveClass());
            clonedItem.setHzrdiFireCode(tranHazardItem.getHzrdiFireCode());
            clonedItem.setHzrdiFlashPoint(tranHazardItem.getHzrdiFlashPoint());
            clonedItem.setHzrdiHazIdUpper(tranHazardItem.getHzrdiHazIdUpper());
            clonedItem.setHzrdiImdgClass(tranHazardItem.getHzrdiImdgClass());
            clonedItem.setHzrdiInhalationZone(tranHazardItem.getHzrdiInhalationZone());
            clonedItem.setHzrdiLtdQty(tranHazardItem.getHzrdiLtdQty());
            clonedItem.setHzrdiMarinePollutants(tranHazardItem.getHzrdiMarinePollutants());
            clonedItem.setHzrdiMFAG(tranHazardItem.getHzrdiMFAG());
            clonedItem.setHzrdiPackageType(tranHazardItem.getHzrdiPackageType());
            clonedItem.setHzrdiPackingGroup(tranHazardItem.getHzrdiPackingGroup());
            clonedItem.setHzrdiPageNumber(tranHazardItem.getHzrdiPageNumber());
            clonedItem.setHzrdiProperName(tranHazardItem.getHzrdiProperName());
            clonedItem.setHzrdiQuantity(tranHazardItem.getHzrdiQuantity());
            clonedItem.setHzrdiSecondaryIMO1(tranHazardItem.getHzrdiSecondaryIMO1());
            clonedItem.setHzrdiSecondaryIMO2(tranHazardItem.getHzrdiSecondaryIMO2());
            clonedItem.setHzrdiSubstanceLower(tranHazardItem.getHzrdiSubstanceLower());
            clonedItem.setHzrdiTechName(tranHazardItem.getHzrdiTechName());
            clonedItem.setHzrdiWeight(tranHazardItem.getHzrdiWeight());
            clonedItem.setHzrdiMoveMethod(tranHazardItem.getHzrdiMoveMethod());
            Roastery.getHibernateApi().save(clonedItem);

            if (tranHazardItem.getHzrdiPlacardSet() != null)
            {
                Set<HazardItemPlacard> clonedPlacardSet = new LinkedHashSet<HazardItemPlacard>();
                clonedItem.setHzrdiPlacardSet(clonedPlacardSet);
                for (HazardItemPlacard tranHazardItemPlacard : (Set<HazardItemPlacard>) tranHazardItem.getHzrdiPlacardSet())
                {
                    HazardItemPlacard clonedPlacard = HazardItemPlacard.createHazardItemPlacardEntity(clonedItem);
                    clonedPlacard.setHzrdipPlacard(tranHazardItemPlacard.getHzrdipPlacard());
                    clonedPlacard.setHzrdipDescription(tranHazardItemPlacard.getHzrdipDescription());
                    clonedPlacardSet.add(clonedPlacard);
                }
            }
        }
        ThisGDS.attachHazards(hazards);
    }
}