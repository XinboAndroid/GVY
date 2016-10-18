import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.ScopeEnum
import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.argo.business.model.*
import com.navis.argo.portal.context.ArgoUserContextProvider
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.context.IUserContextProvider
import com.navis.framework.portal.context.PortalApplicationContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.TransactionParms
import com.navis.framework.util.message.MessageCollector
import com.navis.framework.util.scope.ScopeCoordinates
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.api.UnitManager
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.MoveInfoBean
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.vessel.business.schedule.VesselVisitDetails
import org.apache.log4j.Logger

/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

/**
 * Created by ahmadim on 9/17/2015.
 * Jira: CSDV-3252
 *
 * This groovy to be installed as a code Extension of type GENERAL_NOTICES_CODE_EXTENSION
 *
 * Create a General Notice based on a new Vessel Visit custom event. For example DISCHARGE AND DEPART UNITS. Define it as "Notifiable".
 *
 * Purpose: To automatically Discharge and Gate out the containers at the non-operational Facility like Akutan, which is the Barge destination
 * This event to be logged on the Barge Vessel Visit whose Facility is the non-operational facility (e.g. AKU), once it is departed from the origin
 * Facility e.g. Dutch Harbor.
 *
 * Event to be logged manually. This will trigger this groovy which will discharge and gate out all containers at the non-operational facility.
 */
class MATdischargeAndDepartBargeUnits extends AbstractGeneralNoticeCodeExtension {
    public void execute(GroovyEvent inEvent) {
        Event event = inEvent.getEvent();

        if (event == null) {
            return
        };

        /* Get the unit and the Booking */

        VesselVisitDetails vesselVisitDetails = (VesselVisitDetails) inEvent.getEntity();

        if (vesselVisitDetails == null) {
            return;
        }

        final CarrierVisit cv = ((VisitDetails) vesselVisitDetails).getInboundCv();

        if (cv == null) {
            return;
        }

        LOGGER.warn("Starting MATdischargeAndDepartBargeUnits");
        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
        final Serializable[] onBoardKeys = findArrivingUfvsByIbCarrier(cv);
        final UserContext userContext = ContextHelper.getThreadUserContext();
        final UserContext uc = getNewUserContext(cv.getCvFacility());

        LOGGER.warn("Onboard gkeys " + onBoardKeys);

        PersistenceTemplate template = new PersistenceTemplate(uc);
        MessageCollector mc = template.invoke(new CarinaPersistenceCallback() {
            @Override
            protected void doInTransaction() {
                try {
                    TransactionParms.getBoundParms().setUserContext(uc);
                    if (onBoardKeys != null && onBoardKeys.size() > 0) {
                        for (Serializable ufvGkey : onBoardKeys) {
                            UnitFacilityVisit ufv = (UnitFacilityVisit) _hibernateApi.load(UnitFacilityVisit.class, ufvGkey);
                            if (ufv != null) {
                                MoveInfoBean mib = createDefaultMoveInfoBean(WiMoveKindEnum.VeslDisch, ArgoUtils.timeNow(), cv.getCvFacility());
                                ufv.setFieldValue(InventoryField.UFV_ACTUAL_IB_CV, cv);
                                _unitManager.dischargeUnitFromInboundVisit(ufv, cv.getCvCvd(), mib, null, null);
//                _hibernateApi.flush();
//                departUnitOnTruck(ufv, cv.getCvFacility());
                            }
                        }
                    }
                    Thread.sleep(60000);
                    if (onBoardKeys != null && onBoardKeys.size() > 0) {
                        for (Serializable ufvGkey : onBoardKeys) {
                            UnitFacilityVisit ufv = (UnitFacilityVisit) _hibernateApi.load(UnitFacilityVisit.class, ufvGkey);
                            if (ufv != null) {
                                departUnitOnTruck(ufv, cv.getCvFacility());
                            }
                        }
                    }
                }
                finally {
                    TransactionParms.getBoundParms().setUserContext(userContext);
                }
            }
        });

        if (mc.hasError()) {
            ArgoUtils.appendMessagesToCollector(mc.getMessages());
        }
    }

    private void departUnitOnTruck(UnitFacilityVisit inUfv, Facility inFacility) {
//    CarrierVisit cv = CarrierVisit.getGenericTruckVisit(ContextHelper.getThreadComplex());
        CarrierVisit cv = CarrierVisit.findCarrierVisit(inFacility, LocTypeEnum.TRUCK, 'XXX');
        cv.safelyUpdateVisitPhase(CarrierVisitPhaseEnum.ARRIVED);
        LocPosition pos = LocPosition.createTruckPosition(cv, null, null);
        Unit unit = inUfv.getUfvUnit();
        unit.move(pos);
        unit.deliverOutOfFacility(inFacility);
    }

    private UserContext getNewUserContext(Facility inFacility) {
        ScopeCoordinates scopeCoordinates = _scoper.getScopeCoordinates(ScopeEnum.YARD, inFacility.getActiveYard().getYrdGkey());
        UserContext uc = ContextHelper.getThreadUserContext();
        UserContext newUserContext = _contextProvider.createUserContext(uc.getUserKey(), uc.getUserId(), scopeCoordinates);
        //set security session id for the user
        newUserContext.setSecuritySessionId(uc.getSecuritySessionId());
        return newUserContext;
    }

    private static MoveInfoBean createDefaultMoveInfoBean(WiMoveKindEnum inMoveKind, Date inMoveTime, Facility inFacility) {
        Serializable yrdGkey;
        if (inFacility != null) {
            for (Yard yard : (Set<Yard>) inFacility.getFcyYrdSet()) {
                yrdGkey = yard.getYrdGkey();
                break;
            }
        }

        MoveInfoBean mib = new MoveInfoBean(yrdGkey);
        mib.setMoveKind(inMoveKind);
        mib.setTimePut(inMoveTime);

        if (WiMoveKindEnum.VeslDisch.equals(inMoveKind)
                || WiMoveKindEnum.RailDisch.equals(inMoveKind)
                || WiMoveKindEnum.Receival.equals(inMoveKind)) {
            mib.setTimeDischarge(inMoveTime);
        }

        return mib;
    }

    // This method returns a list of UFV gkeys that are on board the barge and are to be discharged at this Facility.
    private Serializable[] findArrivingUfvsByIbCarrier(CarrierVisit inIbCarrier) {
        if (inIbCarrier == null || inIbCarrier.getCvFacility() == null) {
            return null;
        }
        Object[] states = new Object[2];
        states[0] = UfvTransitStateEnum.S10_ADVISED;
        states[1] = UfvTransitStateEnum.S20_INBOUND;

        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, inIbCarrier.getCvFacility().getFcyGkey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_GKEY, inIbCarrier.getCvGkey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_TYPE, LocTypeEnum.VESSEL))
                .addDqPredicate(PredicateFactory.ne(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.RETIRED))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_POD, inIbCarrier.getCvFacility().getFcyRoutingPoint().getPointGkey()))
                .addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE, states));

        dq.setScopingEnabled(false);
        return _hibernateApi.findPrimaryKeysByDomainQuery(dq);
    }

    private final HibernateApi _hibernateApi = Roastery.getHibernateApi();
    private final _unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
    private N4EntityScoper _scoper = (N4EntityScoper) Roastery.getBeanFactory().getBean(N4EntityScoper.BEAN_ID);
    private ArgoUserContextProvider _contextProvider = (ArgoUserContextProvider) PortalApplicationContext.getBean(IUserContextProvider.BEAN_ID);
    private static Logger LOGGER = Logger.getLogger(MATdischargeAndDepartBargeUnits.class);
}
