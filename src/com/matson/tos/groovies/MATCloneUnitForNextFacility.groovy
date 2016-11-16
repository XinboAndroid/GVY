package workshop

import com.navis.argo.ContextHelper

/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.reference.RoutingPoint
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.api.RectifyParms
import com.navis.inventory.business.api.UnitManager
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.HazardItemPlacard
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Routing
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.inventory.business.units.ReeferRqmnts

/**
 *
 *  Create a second unit for a unit moving from one facility to another on the ship
 *
 * Author: Peter Seiler
 * Date: 09/15/15
 * JIRA: CSDV-3123
 * SFDC: 143603
 *
 * --------------------------------------------------------------------------------------------------------------------------------
 * Date       Author        Description
 * --------------------------------------------------------------------------------------------------------------------------------
 * 10/01/2016 Murali R      Handled the NEP and coding standard correction.
 * 10/06/2015 Imran Ahmad   To handle category computation for the new Unit
 * 10/14/2015 Bruno Chiarini Changed ufvFlexString used to flag cloned units from 05 to 02
 * 10/16/2015 Bruno Chiarini Added null check of reefer requirements prior to copying them
 * --------------------------------------------------------------------------------------------------------------------------------
 */

public class MATCloneUnitForNextFacility extends AbstractGeneralNoticeCodeExtension

{
    public void execute(GroovyEvent inEvent)

    {
        this.log("Execution Started MATCloneUnitForNextFacility");

        /* get the event */

        Event thisEvent = inEvent.getEvent();

        if (thisEvent == null)
            return;

        /* Get the unit and the Booking */

        Unit thisUnit = (Unit) inEvent.getEntity();

        if (thisUnit == null)
            return;

        UnitFacilityVisit thisUFV = thisUnit.getUnitActiveUfvNowActive();

        UnitCategoryEnum newCategory = computeUnitCategory(thisUnit.getUnitRouting().getRtgPOD1(), thisUFV.getUfvFacility().getFcyRoutingPoint());

        // exit if there is no category change required
        if (newCategory.equals(thisUnit.getUnitCategory())){
            if (UnitCategoryEnum.THROUGH.equals(thisUnit.getUnitCategory())) {
                return;
            } else if (UnitCategoryEnum.IMPORT.equals(thisUnit.getUnitCategory())){
                thisUnit.updateCategory(UnitCategoryEnum.THROUGH);
            }
        }

        /* rectify the unit to departed */

        RectifyParms thisRectifyParm = new RectifyParms();

        thisRectifyParm.setEraseHistory(false);
        thisRectifyParm.setUfvTransitState(UfvTransitStateEnum.S70_DEPARTED);
        thisRectifyParm.setUnitVisitState(UnitVisitStateEnum.DEPARTED);

        thisUFV.rectify(thisRectifyParm);

        this.log("Cloning UFV: " + thisUFV);

        HibernateApi.getInstance().flush();

        /* create a new unit for the vessel */

        UnitManager unitMgr = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);

        UnitFacilityVisit newUfv = unitMgr.findOrCreateStowplanUnit(thisUnit.getUnitPrimaryUe().getUeEquipment(),
                thisUnit.getUnitRouting().getRtgDeclaredCv(), thisUnit.getUnitLineOperator(), thisUFV.getUfvFacility(), null,
                thisUnit.getUnitFreightKind(),
                newCategory);

        Unit newUnit = newUfv.getUfvUnit();

        this.log("newUnit.getUnitRouting().getRtgDeclaredCv() " + newUnit.getUnitRouting().getRtgDeclaredCv())
        this.log("newUfv.getUfvIntendedObCv() " + newUfv.getUfvIntendedObCv())

        newUnit.updateCategory(newCategory);
        newUnit.updateSeals(thisUnit.getUnitSealNbr1(), thisUnit.getUnitSealNbr2(), thisUnit.getUnitSealNbr3(),
                thisUnit.getUnitSealNbr4(), thisUnit.getUnitIsCtrSealed());
        newUnit.updateSpecialStow(thisUnit.getUnitSpecialStow());
        newUnit.setUnitGoodsAndCtrWtKg(thisUnit.getUnitGoodsAndCtrWtKg());

        CarrierVisit GenTruck = CarrierVisit.getGenericTruckVisit(ContextHelper.getThreadComplex());

        Routing newRouting = newUnit.getUnitRouting();

        if (UnitCategoryEnum.IMPORT.equals(newCategory)){
            newRouting.setRtgDeclaredCv(GenTruck);
        } else {
            newRouting.setRtgDeclaredCv(thisUFV.getUfvActualIbCv());
        }

        newRouting.setRtgPOL(thisUnit.getUnitRouting().getRtgPOL());
        newRouting.setRtgCarrierService(thisUnit.getUnitRouting().getRtgCarrierService());
        newRouting.setRtgPOD1(thisUnit.getUnitRouting().getRtgPOD1());
        newRouting.setRtgGroup(thisUnit.getUnitRouting().getRtgGroup());

        newUnit.setUnitRouting(newRouting);

        GoodsBase NewGoodsBase = newUnit.getUnitGoods();

        GoodsBase ThisGoodsBase = thisUnit.getUnitGoods();

        NewGoodsBase.setCommodity(ThisGoodsBase.getGdsCommodity());
        NewGoodsBase.setOrigin(ThisGoodsBase.getGdsOrigin());
        NewGoodsBase.setDestination(ThisGoodsBase.getGdsDestination());
        NewGoodsBase.setGdsBlNbr(ThisGoodsBase.getGdsBlNbr());
        NewGoodsBase.setGdsConsigneeBzu(ThisGoodsBase.getGdsConsigneeBzu());
        NewGoodsBase.setGdsShipperBzu(ThisGoodsBase.getGdsShipperBzu());

        if (ThisGoodsBase.getGdsHazards() != null) {
            NewGoodsBase.attachHazards(this.copyhazards(ThisGoodsBase.getGdsHazards()))
        }

        if (thisUnit.getUnitRequiresPower()) {
            ReeferRqmnts thisGoodsBaseReeferReqs = ThisGoodsBase.getGdsReeferRqmnts();
            if (thisGoodsBaseReeferReqs != null) {
                NewGoodsBase.setGdsReeferRqmnts(thisGoodsBaseReeferReqs.makeCopy());
            }
            newUnit.updateRequiresPower(true);
        }

        newUnit.updateOog(thisUnit.getUnitOogBackCm(), thisUnit.getUnitOogFrontCm(), thisUnit.getUnitOogLeftCm(), thisUnit.getUnitOogRightCm(), thisUnit.getUnitOogTopCm());

        HibernateApi.getInstance().flush();

        this.log("thisUnit.getUnitRouting().getRtgDeclaredCv() " + thisUnit.getUnitRouting().getRtgDeclaredCv())
        this.log("newUfv.getUfvActualIbCv() " + newUfv.getUfvActualIbCv())

        VesselVisitDetails newIbVisit = VesselVisitDetails.resolveVvdFromCv(newUfv.getUfvActualIbCv());

        CarrierVisitPhaseEnum newIbVVPhase = null;
        if (newIbVisit != null) {
            newIbVVPhase = newIbVisit.getVvdVisitPhase()
        }

        if (CarrierVisitPhaseEnum.CREATED.equals(newIbVVPhase)) {
            if (newUfv.getUfvVisitState() != UnitVisitStateEnum.ADVISED)
                newUfv.setUfvVisitState(UnitVisitStateEnum.ADVISED);

            if (newUfv.getUfvTransitState() != UfvTransitStateEnum.S10_ADVISED)
                newUfv.setUfvTransitState(UfvTransitStateEnum.S10_ADVISED);

            if (newUnit.getUnitVisitState() != UnitVisitStateEnum.ADVISED)
                newUnit.updateUnitVisitState(UnitVisitStateEnum.ADVISED);
        } else {
            if (newUfv.getUfvVisitState() != UnitVisitStateEnum.ACTIVE)
                newUfv.setUfvVisitState(UnitVisitStateEnum.ACTIVE);

            if (newUfv.getUfvTransitState() != UfvTransitStateEnum.S20_INBOUND)
                newUfv.setUfvTransitState(UfvTransitStateEnum.S20_INBOUND);

            if (newUnit.getUnitVisitState() != UnitVisitStateEnum.ACTIVE)
                newUnit.updateUnitVisitState(UnitVisitStateEnum.ACTIVE);

            newUfv.setUfvVisibleInSparcs(true);
        }

        newUfv.updateArrivePosition(thisUFV.getUfvArrivePosition());
        newUfv.setUfvLastKnownPosition(thisUFV.getUfvArrivePosition());

        if (UnitCategoryEnum.IMPORT.equals(newCategory)){
            newUfv.setUfvIntendedObCv(GenTruck);
            newUfv.setUfvActualObCv(GenTruck);
            newUfv.setUfvActualIbCv(thisUFV.getUfvActualIbCv());
        } else {
            newUfv.setUfvIntendedObCv(thisUFV.getUfvActualIbCv());
            newUfv.setUfvActualObCv(thisUFV.getUfvActualIbCv());
            newUfv.setUfvActualIbCv(thisUFV.getUfvActualIbCv());
        }

        newUfv.setUfvFlexString01(thisUFV.getUfvFlexString01());
        newUfv.setUfvFlexString05(thisUFV.getUfvFlexString05());
        newUfv.setUfvFlexString03(thisUFV.getUfvFlexString03());
        newUfv.setUfvFlexString04(thisUFV.getUfvFlexString04());
        newUfv.setUfvFlexString02("CLONED");
        newUfv.setUfvFlexString06(thisUFV.getUfvFlexString06());
        newUfv.setUfvFlexString07(thisUFV.getUfvFlexString07());
        newUfv.setUfvFlexString08(thisUFV.getUfvFlexString08());
        newUfv.setUfvFlexString09(thisUFV.getUfvFlexString09());
        newUfv.setUfvFlexString10(thisUFV.getUfvFlexString10());
        newUfv.setUfvStowFactor(thisUFV.getUfvStowFactor());

        newUnit.updateDenormalizedFields(false);
        newUnit.setUnitActiveUfv(newUfv);

        thisUnit.deleteUfv(thisUFV);
    }

    Hazards copyhazards(Hazards inHazards) {

        /* Copy the hazards from the old unit onto the new unit */

        Hazards hazards = Hazards.createHazardsEntity();

        for (Iterator<HazardItem> itr = inHazards.getHazardItemsIterator(); itr.hasNext();) {
            HazardItem tranHazardItem = itr.next();
            HazardItem clonedItem = HazardItem.createHazardItemEntity(hazards,
                    tranHazardItem.getHzrdiImdgClass(), tranHazardItem.getHzrdiUNnum());

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

            if (tranHazardItem.getHzrdiPlacardSet() != null) {
                Set<HazardItemPlacard> clonedPlacardSet = new LinkedHashSet<HazardItemPlacard>();
                clonedItem.setHzrdiPlacardSet(clonedPlacardSet);
                for (HazardItemPlacard tranHazardItemPlacard : (Set<HazardItemPlacard>) tranHazardItem.getHzrdiPlacardSet()) {
                    HazardItemPlacard clonedPlacard = HazardItemPlacard.createHazardItemPlacardEntity(clonedItem);
                    clonedPlacard.setHzrdipPlacard(tranHazardItemPlacard.getHzrdipPlacard());
                    clonedPlacard.setHzrdipDescription(tranHazardItemPlacard.getHzrdipDescription());
                    clonedPlacardSet.add(clonedPlacard);
                }
            }
        }
        return (hazards);
    }

    // compute the category for the next Unit, based on the current category of the Unit
    private UnitCategoryEnum computeUnitCategory(RoutingPoint inUnitPod, RoutingPoint inFacilityRouting) {
        if (inUnitPod == null || inFacilityRouting == null) {
            return;
        }
        if (inUnitPod.equals(inFacilityRouting)) {
            return UnitCategoryEnum.IMPORT;
        } else {
            return UnitCategoryEnum.THROUGH;
        }
    }
}
