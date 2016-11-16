/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

import com.navis.argo.business.api.IEvent
import com.navis.argo.business.api.ServicesManager
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.units.EqBaseOrder
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.services.business.rules.EventType

/**
 * This groovy checks the hazard items to ensure that the unit and the booking hazard items are kept in sync
 *
 * Author: Peter Seiler
 * Date: 07/27/15
 * JIRA: CSDV-3117
 * SFDC: 143518
 *
 * Peter Seiler 07/28/15 Add logic to deal with all hazards removed from the booking
 *
 */

public class MATHazardItemLifeCyleInterceptor extends AbstractEntityLifecycleInterceptor
{

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges)
    {
        this.log("MATHazardItemLifeCyleInterceptor: Started");
        this.log("inOriginalFieldChanges - " + inOriginalFieldChanges)

        /* get the hazard item entity */

        HazardItem ThisHazItm = inEntity._entity;

        if (ThisHazItm == null)
        {
            return;
        }
        this.log("ThisHazItm - " + ThisHazItm )

        Hazards ThisHazard =  ThisHazItm.getHzrdiHazards();

        this.log("ThisHazard - " + ThisHazard)

        if (ThisHazard == null)
        {

            /* the existing entity has null hazard.  Get it from the changes */

            if (inOriginalFieldChanges.hasFieldChange(InventoryField.HZRDI_HAZARDS))
            {

                EFieldChange HazardChng = inOriginalFieldChanges.findFieldChange(InventoryField.HZRDI_HAZARDS);

                ThisHazard = HazardChng.getNewValue();
            }

            if (ThisHazard == null)
            {
                return;
            }
        }

        if (ThisHazard.getHzrdOwnerEntityName() == 'Booking')
        {

            this.log("Update is for booking")

            Booking ThisBooking = this.findBookingFromGkey(ThisHazard.getHzrdOwnerEntityGkey());

            /* insert BOOKING_HAZARDS_UPADTE event into Booking history */

            ServicesManager sm = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);

            EventType HazardUpdateEvent = EventType.findEventType('BOOKING_HAZARDS_UPDATE');

            if (sm != null && HazardUpdateEvent != null && ThisBooking != null)
            {
                (IEvent) sm.recordEvent(HazardUpdateEvent, "Booking Hazards Updated ", null, null, ThisBooking, null, null);
            }
        }
        else
        {
            if(ThisHazard.getHzrdOwnerEntityName() == 'GoodsBase')
            {
                Unit ThisUnit = this.findUnitFromGDSGkey(ThisHazard.getHzrdOwnerEntityGkey());
            }
        }
    }
    @Override
    void validateDelete(EEntityView inEntity)
    {
        this.log("MATHazardItemLifeCyleInterceptor: validateDelete Started");

        /* get the hazard item entity */

        HazardItem ThisHazItm = inEntity._entity;

        if (ThisHazItm == null)
        {
            return;
        }

        Hazards ThisHazard =  ThisHazItm.getHzrdiHazards();

        if (ThisHazard.getHzrdOwnerEntityName() == 'Booking')
        {

            Booking ThisBooking = this.findBookingFromGkey(ThisHazard.getHzrdOwnerEntityGkey());

            /* insert BOOKING_HAZARDS_DELETE event into Booking history */

            ServicesManager sm = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);

            EventType HazardUpdateEvent = EventType.findEventType('BOOKING_HAZARDS_DELETE');

            if (sm != null && HazardUpdateEvent != null && ThisBooking != null)
            {
                (IEvent) sm.recordEvent(HazardUpdateEvent, "Booking Hazards Updated ", null, null, ThisBooking, null, null);
            }
        }
    }

    private Booking findBookingFromGkey (Long inBaseOderGkey)
    {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.EQ_BASE_ORDER)
                .addDqPredicate(PredicateFactory.eq(InventoryField.EQBO_GKEY, inBaseOderGkey));

        List<EqBaseOrder> EBO_List = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        if (EBO_List == null || EBO_List.size() == 0)
        {
            return(null);
        }
        else
        {
            return(Booking.resolveBkgFromEqo(EquipmentOrder.resolveEqoFromEqbo(EBO_List[0])));
        }
    }

    private Unit findUnitFromGDSGkey (Long inGDSGkey)
    {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.GOODS_BASE)
                .addDqPredicate(PredicateFactory.eq(InventoryField.GDS_GKEY, inGDSGkey));

        List<GoodsBase> GDS_List = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        if (GDS_List == null || GDS_List.size() == 0)
        {
            return(null);
        }
        else
        {
            return(GDS_List[0].getGdsUnit());
        }
    }
}