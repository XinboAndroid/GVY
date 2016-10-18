import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.ScopeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.LocPosition
import com.navis.argo.business.model.N4EntityScoper
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.portal.context.ArgoUserContextProvider
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.context.IUserContextProvider
import com.navis.framework.portal.context.PortalApplicationContext
import com.navis.framework.util.TransactionParms
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.scope.ScopeCoordinates
import com.navis.inventory.business.api.RectifyParms
import com.navis.inventory.business.api.UnitManager
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.HazardItemPlacard
import com.navis.inventory.business.imdg.Hazards
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.ReeferRqmnts
import com.navis.inventory.business.units.Routing
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.util.RoadBizUtil
import com.navis.spatial.business.model.Position
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Logger

public class MATUpdateClonedUnit {

    public boolean createAndUpdateClonedUnit(Unit inOldUnit, CarrierVisit inCarrierVisit, UnitFacilityVisit inUFV, Facility inNewFacility, UnitFacilityVisit inUFVOldFac) {

        /* rectify the unit to departed */

        final UserContext userContext = ContextHelper.getThreadUserContext();
        final UserContext uc = getNewUserContext(inNewFacility);
        final Unit inUnit = inOldUnit;
        final Facility inFacility = inNewFacility;
        final CarrierVisit inCv = inCarrierVisit;
        final UnitFacilityVisit inExistingUFV = inUFV;
        final Facility oldFacility = ContextHelper.getThreadFacility();
        final UnitFacilityVisit inUFVForPrevFacility = inUFVOldFac;

        PersistenceTemplate template = new PersistenceTemplate(uc);
        MessageCollector mc = template.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                GroovyApi api = new GroovyApi();
                try {
                    TransactionParms.getBoundParms().setUserContext(uc);
                    LOGGER.warn("Cloning UFV: " + inExistingUFV);
                    /* create a new unit for the vessel */
                    UnitCategoryEnum newCategory = computeUnitCategory(inUnit.getUnitRouting().getRtgPOD1(), inFacility.getFcyRoutingPoint());
                    UnitManager unitMgr = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);

                    UnitFacilityVisit newUfv = unitMgr.findOrCreateStowplanUnit(inUnit.getUnitPrimaryUe().getUeEquipment(),
                            inCv, inUnit.getUnitLineOperator(), inFacility, null, inUnit.getUnitFreightKind(), newCategory);

                    if (newUfv == null) {
                        LOGGER.warn("New Cloned UFV can't be created");
                        return;
                    }

                    Unit newUnit = newUfv.getUfvUnit();

                    LOGGER.warn("New Cloned Unit " + newUnit);
                    newUnit.updateCategory(newCategory);
                    newUnit.updateFreightKind(inUnit.getUnitFreightKind());
                    newUnit.updateRemarks(inUnit.getUnitRemark())
                    newUnit.updateSeals(inUnit.getUnitSealNbr1(), inUnit.getUnitSealNbr2(), inUnit.getUnitSealNbr3(),
                            inUnit.getUnitSealNbr4(), inUnit.getUnitIsCtrSealed());
                    newUnit.updateSpecialStow(inUnit.getUnitSpecialStow());
                    newUnit.setUnitGoodsAndCtrWtKg(inUnit.getUnitGoodsAndCtrWtKg());

                    CarrierVisit GenTruck = CarrierVisit.getGenericTruckVisit(ContextHelper.getThreadComplex());

                    Routing newRouting = newUnit.getUnitRouting();

                    if (UnitCategoryEnum.IMPORT.equals(newCategory)) {
                        newRouting.setRtgDeclaredCv(GenTruck);
                    } else {
                        newRouting.setRtgDeclaredCv(inCv);
                    }


                    newRouting.setRtgPOL(inUnit.getUnitRouting().getRtgPOL());
                    newRouting.setRtgCarrierService(inUnit.getUnitRouting().getRtgCarrierService());
                    newRouting.setRtgPOD1(inUnit.getUnitRouting().getRtgPOD1());
                    newRouting.setRtgGroup(inUnit.getUnitRouting().getRtgGroup());

                    newUnit.setUnitRouting(newRouting);

                    GoodsBase NewGoodsBase = newUnit.getUnitGoods();

                    GoodsBase ThisGoodsBase = inUnit.getUnitGoods();

                    NewGoodsBase.setCommodity(ThisGoodsBase.getGdsCommodity());
                    NewGoodsBase.setOrigin(ThisGoodsBase.getGdsOrigin());
                    NewGoodsBase.setDestination(ThisGoodsBase.getGdsDestination());
                    NewGoodsBase.setGdsBlNbr(ThisGoodsBase.getGdsBlNbr());
                    NewGoodsBase.setGdsConsigneeBzu(ThisGoodsBase.getGdsConsigneeBzu());
                    NewGoodsBase.setGdsShipperBzu(ThisGoodsBase.getGdsShipperBzu());

                    if (ThisGoodsBase.getGdsHazards() != null) {
                        NewGoodsBase.attachHazards(this.copyhazards(ThisGoodsBase.getGdsHazards()))
                    }

                    if (inUnit.getUnitRequiresPower()) {
                        ReeferRqmnts thisGoodsBaseReeferReqs = ThisGoodsBase.getGdsReeferRqmnts();
                        if (thisGoodsBaseReeferReqs != null) {
                            NewGoodsBase.setGdsReeferRqmnts(thisGoodsBaseReeferReqs.makeCopy());
                        }
                        newUnit.updateRequiresPower(true);
                    }

                    newUnit.updateOog(inUnit.getUnitOogBackCm(), inUnit.getUnitOogFrontCm(), inUnit.getUnitOogLeftCm(), inUnit.getUnitOogRightCm(), inUnit.getUnitOogTopCm());

                    HibernateApi.getInstance().flush();

                    LOGGER.warn("newUnit.getUnitRouting().getRtgDeclaredCv() " + newUnit.getUnitRouting().getRtgDeclaredCv())
                    LOGGER.warn("newUfv.getUfvActualIbCv() " + newUfv.getUfvActualIbCv());

                    VesselVisitDetails newIbVisit = VesselVisitDetails.resolveVvdFromCv(inCv);

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

                    Position position = inExistingUFV.getUfvArrivePosition();
                    if (position != null) {
                        String slot = position.getPosSlot();
                        if (slot != null) {
                            Position newPosition = inCv.createInboundPosition(LocPosition.createVesselPosition(inCv, slot, null));
                            newUfv.updateArrivePosition(newPosition);
                            newUfv.setUfvLastKnownPosition(newPosition);
                        }
                    }
                    //newUfv.updateArrivePosition(inExistingUFV.getUfvArrivePosition());
                    //newUfv.setUfvLastKnownPosition(inExistingUFV.getUfvArrivePosition());

                    if (UnitCategoryEnum.IMPORT.equals(newCategory)) {
                        newUfv.setUfvIntendedObCv(GenTruck);
                        newUfv.setUfvActualObCv(GenTruck);
                        newUfv.setUfvActualIbCv(inCv);
                    } else {
                        newUfv.setUfvIntendedObCv(inCv);
                        newUfv.setUfvActualObCv(inCv);
                        newUfv.setUfvActualIbCv(inCv);
                    }

                    newUfv.setUfvFlexString01(inUFVForPrevFacility.getUfvFlexString01());
                    newUfv.setUfvFlexString05(inUFVForPrevFacility.getUfvFlexString05());
                    newUfv.setUfvFlexString03(inUFVForPrevFacility.getUfvFlexString03());
                    newUfv.setUfvFlexString04(inUFVForPrevFacility.getUfvFlexString04());
                    newUfv.setUfvFlexString02("CLONED");
                    newUfv.setUfvFlexString06(inUFVForPrevFacility.getUfvFlexString06());
                    newUfv.setUfvFlexString07(inUFVForPrevFacility.getUfvFlexString07());
                    newUfv.setUfvFlexString08(inUFVForPrevFacility.getUfvFlexString08());
                    newUfv.setUfvFlexString09(inUFVForPrevFacility.getUfvFlexString09());
                    newUfv.setUfvFlexString10(inUFVForPrevFacility.getUfvFlexString10());
                    newUfv.setUfvStowFactor(inUFVForPrevFacility.getUfvStowFactor());
                    inUnit.deleteUfv(inExistingUFV);
                    newUnit.updateDenormalizedFields(true);
                    newUnit.setUnitActiveUfv(newUfv);
                    try {
                        LOGGER.warn("Start Updating KQA units : "+newUnit.getUnitGoods().getGdsDestination());
                        if (newUnit.getUnitGoods() != null &&
                                ("KQA".equalsIgnoreCase(newUnit.getUnitGoods().getGdsDestination()) ||
                                        "AKU".equalsIgnoreCase(newUnit.getUnitGoods().getGdsDestination()))) {
    //                        UnitFacilityVisit ufv = newUnit.getUnitActiveUfvNowActive();
                            CarrierVisit cv = newUfv.getUfvActualObCv();
                            LOGGER.warn("Updating KQA units : "+newUnit.getUnitId());
                            if (inFacility != null && "DUT".equalsIgnoreCase(inFacility.getFcyId())) {
                                newUnit.getUnitRouting().setRtgPOD1(RoutingPoint.findRoutingPoint("KQA"));
                                CarrierVisit kqaVisit = CarrierVisit.findVesselVisit(Facility.findFacility("ANK"), "BARGE");
                                newUnit.setFieldValue("unitRouting.rtgDeclaredCv", kqaVisit);
                                newUnit.setUnitCategory(UnitCategoryEnum.EXPORT);
                                newUfv.setFieldValue("ufvActualObCv", kqaVisit);
                                newUfv.setFieldValue("ufvIntendedObCv", kqaVisit);
                            }
                        }
                        LOGGER.warn("Finished Updating KQA units : ");
                    } catch (Exception e) {
                        LOGGER.error("Errored out while updating KQA units in DUT : "+e.getMessage());
                        e.printStackTrace();
                    }
                    RoadBizUtil.commit();
                } catch (Exception e) {
                    api.sendEmail("gbabu@matson.com","gbabu@matson.com","failed","Failed iwth message "+e.toString());
                    LOGGER.warn("Exception while cloning unit " + e);
                }
            }
        }
        );
        return true;
    }


    public Hazards copyhazards(Hazards inHazards) {

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

    private final void log(String inMsg) {
        LOGGER.warn(inMsg);
    }

    private UserContext getNewUserContext(Facility inFacility) {
        ScopeCoordinates scopeCoordinates = _scoper.getScopeCoordinates(ScopeEnum.YARD, inFacility.getActiveYard().getYrdGkey());
        UserContext uc = ContextHelper.getThreadUserContext();
        UserContext newUserContext = _contextProvider.createUserContext(uc.getUserKey(), uc.getUserId(), scopeCoordinates);
        //set security session id for the user
        newUserContext.setSecuritySessionId(uc.getSecuritySessionId());
        return newUserContext;
    }

    private static final Logger LOGGER = Logger.getLogger(MATUpdateClonedUnit.class);

    private final HibernateApi _hibernateApi = Roastery.getHibernateApi();
    private final _unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
    private N4EntityScoper _scoper = (N4EntityScoper) Roastery.getBeanFactory().getBean(N4EntityScoper.BEAN_ID);
    private ArgoUserContextProvider _contextProvider = (ArgoUserContextProvider) PortalApplicationContext.getBean(IUserContextProvider.BEAN_ID);
}