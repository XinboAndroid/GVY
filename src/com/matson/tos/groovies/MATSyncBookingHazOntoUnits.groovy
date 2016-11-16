/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.HazardItemPlacard
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.orders.business.eqorders.Booking
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
/**
 * This groovy checks the booking hazards to ensure that the unit and the booking hazard items are kept in sync
 * This is triggered by a BOOKING_HAZARDS_UPDATE event
 *
 * Author: Peter Seiler
 * Date: 07/27/15
 * JIRA: CSDV-3117
 * SFDC: 143518
 *
 * Peter Seiler 07/30/2015 If all hazards are deleted from the booking clear the hazards from the unit
 *
 * Peter Seiler 09/03/2015
 *
 * JIRA: ARGO-79452
 *
 * Implement Groovy workaround for bug in 'cloneHazards' not copying all the hazard items attributes
 *
 */

public class MATSyncBookingHazOntoUnits extends AbstractGeneralNoticeCodeExtension

{
    public void execute(GroovyEvent inEvent)

    {
        this.log("Execution Started MATSyncBookingHazOntoUnits");

        /* get the event */

        Event ThisEvent = inEvent.getEvent();

        if (ThisEvent == null)
            return;

        /* Get the booking and the Booking Hazards */

        Booking ThisBooking = (Booking) inEvent.getEntity();

        Hazards BookingHazards = ThisBooking.getEqoHazards();

        /* Get the Units for the Booking */

        List<Unit> UnitsForBooking = this.findUnitsForBooking(ThisBooking);

        /* Step through all the units */

        for (Unit ThisUnit : UnitsForBooking)
        {

            /* get the hazards for the unit */

            GoodsBase ThisGDS = ThisUnit.getUnitGoods();

            if (ThisGDS.getGdsHazards() != null)
            {

                /* Delete the existing hazards for he unit */

                ThisGDS.getGdsHazards().deleteAllHazardItems();
            }

            /* Copy the booking hazards onto the unit */

            if (BookingHazards == null)
            {
                /* if there are no booking hazards clear the unit hazards */

                ThisGDS.clearHazardsRef();
                ThisGDS.calculateDenormalizedHazardFields();
            }
            else
            {
                /* clone the booking hazards onto the unit. */

                // ThisGDS.attachHazards(BookingHazards.cloneHazards());

                /* Copy the booking hazards onto the unit */

                Hazards hazards = Hazards.createHazardsEntity();

                for (Iterator<HazardItem> itr = BookingHazards.getHazardItemsIterator(); itr.hasNext(); )
                {
                    HazardItem tranHazardItem = itr.next();
                    HazardItem clonedItem = HazardItem.createHazardItemEntity(hazards, tranHazardItem.getHzrdiImdgClass(), tranHazardItem.getHzrdiUNnum());

                    clonedItem.setHzrdiNbrType(tranHazardItem.getHzrdiNbrType());
                    clonedItem.setHzrdiLtdQty(tranHazardItem.getHzrdiLtdQty());
                    clonedItem.setHzrdiPackageType(tranHazardItem.getHzrdiPackageType());
                    clonedItem.setHzrdiInhalationZone(tranHazardItem.getHzrdiInhalationZone());
                    clonedItem.setHzrdiImdgCode(tranHazardItem.getHzrdiImdgCode());
                    clonedItem.setHzrdiExplosiveClass(tranHazardItem.getHzrdiExplosiveClass());
                    clonedItem.setHzrdiPageNumber(tranHazardItem.getHzrdiPageNumber());
                    clonedItem.setHzrdiFlashPoint(tranHazardItem.getHzrdiFlashPoint());
                    clonedItem.setHzrdiTechName(tranHazardItem.getHzrdiTechName());
                    clonedItem.setHzrdiProperName(tranHazardItem.getHzrdiProperName());
                    clonedItem.setHzrdiEMSNumber(tranHazardItem.getHzrdiEMSNumber());
                    clonedItem.setHzrdiERGNumber(tranHazardItem.getHzrdiERGNumber());
                    clonedItem.setHzrdiMFAG(tranHazardItem.getHzrdiMFAG());
                    clonedItem.setHzrdiPackingGroup(tranHazardItem.getHzrdiPackingGroup());
                    clonedItem.setHzrdiHazIdUpper(tranHazardItem.getHzrdiHazIdUpper());
                    clonedItem.setHzrdiSubstanceLower(tranHazardItem.getHzrdiSubstanceLower());
                    clonedItem.setHzrdiWeight(tranHazardItem.getHzrdiWeight());
                    clonedItem.setHzrdiPlannerRef(tranHazardItem.getHzrdiPlannerRef());
                    clonedItem.setHzrdiQuantity(tranHazardItem.getHzrdiQuantity());
                    clonedItem.setHzrdiMoveMethod(tranHazardItem.getHzrdiMoveMethod());
                    clonedItem.setHzrdiSecondaryIMO1(tranHazardItem.getHzrdiSecondaryIMO1());
                    clonedItem.setHzrdiSecondaryIMO2(tranHazardItem.getHzrdiSecondaryIMO2());
                    clonedItem.setHzrdiDeckRestrictions(tranHazardItem.getHzrdiDeckRestrictions());
                    clonedItem.setHzrdiMarinePollutants(tranHazardItem.getHzrdiMarinePollutants());
                    clonedItem.setHzrdiDcLgRef(tranHazardItem.getHzrdiDcLgRef());
                    clonedItem.setHzrdiEmergencyTelephone(tranHazardItem.getHzrdiEmergencyTelephone());
                    clonedItem.setHzrdiNotes(tranHazardItem.getHzrdiNotes());
                    clonedItem.setHzrdiFireCode(tranHazardItem.getHzrdiFireCode());
                    clonedItem.setHzrdiSeq(tranHazardItem.getHzrdiSeq());
                    clonedItem.setHzrdiImdgClass(tranHazardItem.getHzrdiImdgClass());

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
    }

    /* Local function to find the units received for booking */

    private List<Unit> findUnitsForBooking (Booking inBooking)
    {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_DEPARTURE_ORDER_NBR, inBooking.getEqboNbr()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_LINE_OPERATOR_GKEY, inBooking.getEqoLine().getBzuGkey()));

        List<Unit> unitList = Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);

        if(unitList == null || unitList.size()==0)
        {
            return null;
        }
        return unitList;
    }
}