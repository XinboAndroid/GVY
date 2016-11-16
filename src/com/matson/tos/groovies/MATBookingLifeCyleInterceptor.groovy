/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.orders.OrdersField
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderItem

/**
 * The priory code passed in the booking SNX needs to be populated onto unitFlexString08
 *
 * Author: Peter Seiler
 * Date: 08/04/15
 * JIRA: ARGO-76865
 * SFDC: 142550
 *
 */

public class MATBookingLifeCyleInterceptor extends AbstractEntityLifecycleInterceptor
{

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges)
    {

        String ThisStowBlock = null;

        this.log("MATBookingLifeCyleInterceptor: Started");

        /* get the equipment order entity */

        Booking ThisEqOrder = inEntity._entity;

        if (inOriginalFieldChanges.hasFieldChange(OrdersField.EQO_STOW_BLOCK))
        {

            EFieldChange RemarksChng = inOriginalFieldChanges.findFieldChange(OrdersField.EQO_STOW_BLOCK);

            ThisStowBlock = RemarksChng.getNewValue();
        }
        else
        {
            /* if the stow block is not updated, exit */

            return;
        }

        /* get the order items on the booking */

        for (EquipmentOrderItem ThisEQOI : ThisEqOrder.getEqboOrderItems())
        {

            /* get the units for each order item */

            List<UnitEquipment> UE_OnEQOI = this.findUnitEqForOrderItem(ThisEQOI.getEqboiGkey());

            for (UnitEquipment ThisUE : UE_OnEQOI)
            {

                /* set the priority stow for each unit associated with the booking */

                Unit ThisUnit = ThisUE.getUeUnit();
                ThisUnit.setUnitFlexString08(ThisStowBlock);
            }
        }
    }

    /* local function to find UnitEquipment associated with an EquipmentOrderItem */

    private List<UnitEquipment> findUnitEqForOrderItem(long inEQOI)
    {

        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_EQUIPMENT)
                .addDqPredicate(PredicateFactory.eq(InventoryField.UE_DEPARTURE_ORDER_ITEM, inEQOI));

        List<UnitEquipment> UEList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        this.log ("UEList " + UEList)

        return UEList;
    }
}