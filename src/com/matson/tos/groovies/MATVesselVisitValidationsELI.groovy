import com.navis.argo.ArgoField
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.CarrierModeEnum
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizViolation
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.InventoryPropertyKeys
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.vessel.api.VesselVisitField
import com.navis.vessel.business.schedule.VesselVisitDetails

/**
 * Created  on 3/29/2016.
 */
public class MATVesselVisitValidationsELI extends AbstractEntityLifecycleInterceptor {

    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        //this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
    }

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
    }

    void onCreateOrUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges)
    {
        GroovyApi groovyApi = new GroovyApi();
        if (inOriginalFieldChanges == null) {
            return;
        }
        CarrierVisit cv = (CarrierVisit) inEntity._entity;
//        CarrierVisit cv = vesselVisitDetails.getCvdCv();
        if (cv == null) {
            return;
        }
        if (cv.getCvCarrierMode()==null || !LocTypeEnum.VESSEL.equals(cv.getCvCarrierMode())) {
            return;
        }
        CarrierVisitPhaseEnum cvPhase;
        if (inOriginalFieldChanges.hasFieldChange(ArgoField.CV_VISIT_PHASE)) {
            cvPhase = (CarrierVisitPhaseEnum) inOriginalFieldChanges.findFieldChange(ArgoField.CV_VISIT_PHASE).getNewValue();
        }
        if (cvPhase==null) {
            return;
            //cvPhase = cv.getCvVisitPhase();
        }
        Date atd;
        //VesselVisitDetails vesselVisitDetails = cv.get
        if (inOriginalFieldChanges.hasFieldChange(VesselVisitField.VVD_A_T_D)) {
            atd = (Date) inOriginalFieldChanges.findFieldChange(VesselVisitField.VVD_A_T_D).getNewValue();
        }
        if (atd == null) {
            atd = cv.getCvATD();
        }
        Serializable[] onBoardKeys;
        if (cvPhase != null && CarrierVisitPhaseEnum.DEPARTED.equals(cvPhase)) {
            onBoardKeys = findUfvsWithInTimeLaterThanDepartTime(cv, atd);
            if (onBoardKeys != null && onBoardKeys.length > 0) {
                StringBuffer units = new StringBuffer();
                for (Serializable ufvGkey : onBoardKeys) {
                    UnitFacilityVisit ufv = (UnitFacilityVisit) HibernateApi.getInstance().load(UnitFacilityVisit.class, ufvGkey);
                    if (ufv != null) {
                        units.append(ufv.getUfvUnit().getUnitId() + " ");
                    }
                }
                //BizViolation bizViolation = BizViolation.create(InventoryPropertyKeys.FACILITY_TIME_IN_DATE_IS_AFTER_TIME_OUT_DATE, null, "Units have in time after the ATD " + units.toString());
                //groovyApi.sendEmail("gbabu@matson.com","gbabu@matson.com","Biz violation while departing", units.toString());
                this.registerError("Units have in time after the Actual Time of Departure of vessel : " + units.toString());
                //throw bizViolation;
            }
            onBoardKeys = findUfvsWithPODasCurrentPort(cv);
            if (onBoardKeys != null && onBoardKeys.length > 0) {
                StringBuffer unitsforCurrentFacility= new StringBuffer();
                for (Serializable ufvGkey : onBoardKeys) {
                    UnitFacilityVisit ufv = (UnitFacilityVisit) HibernateApi.getInstance().load(UnitFacilityVisit.class, ufvGkey);
                    if (ufv != null) {
                        unitsforCurrentFacility.append(ufv.getUfvUnit().getUnitId() + " ");
                    }
                }
                //BizViolation bizViolation = BizViolation.create(InventoryPropertyKeys.FACILITY_TIME_IN_DATE_IS_AFTER_TIME_OUT_DATE, null, "Units have in time after the ATD " + units.toString());
                //groovyApi.sendEmail("gbabu@matson.com","gbabu@matson.com","Biz violation while departing", units.toString());
                this.registerError("Units with POD as current Port still on board the vessel : " + unitsforCurrentFacility.toString());
                //throw bizViolation;
            }
            onBoardKeys = findUfvsWithNoPOD(cv);
            if (onBoardKeys != null && onBoardKeys.length > 0) {
                StringBuffer unitsforNoFacility= new StringBuffer();
                for (Serializable ufvGkey : onBoardKeys) {
                    UnitFacilityVisit ufv = (UnitFacilityVisit) HibernateApi.getInstance().load(UnitFacilityVisit.class, ufvGkey);
                    if (ufv != null) {
                        unitsforNoFacility.append(ufv.getUfvUnit().getUnitId() + " ");
                    }
                }
                //BizViolation bizViolation = BizViolation.create(InventoryPropertyKeys.FACILITY_TIME_IN_DATE_IS_AFTER_TIME_OUT_DATE, null, "Units have in time after the ATD " + units.toString());
                //groovyApi.sendEmail("gbabu@matson.com","gbabu@matson.com","Biz violation while departing", units.toString());
                this.registerError("Units without POD is loaded on the vessel : " + unitsforNoFacility.toString());
                //throw bizViolation;
            }

        }
    }
    // This method returns a list of UFV gkeys that are on board with intime after departure time.
    private Serializable[] findUfvsWithInTimeLaterThanDepartTime(CarrierVisit inCv, Date inAtd) {
        if (inCv == null || inCv.getCvFacility() == null) {
            return null;
        }
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, inCv.getCvFacility().getFcyGkey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_GKEY, inCv.getCvGkey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_TYPE, LocTypeEnum.VESSEL))
                .addDqPredicate(PredicateFactory.ne(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.RETIRED))
        //.addDqPredicate(PredicateFactory.eq(UnitField.UFV_POD, inCv.getCvFacility().getFcyRoutingPoint().getPointGkey()))
                .addDqPredicate(PredicateFactory.gt(InventoryField.UFV_TIME_IN, inAtd));
        dq.setScopingEnabled(false);
        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq);
    }

    private Serializable[] findUfvsWithPODasCurrentPort(CarrierVisit inCv) {
        if (inCv == null || inCv.getCvFacility() == null) {
            return null;
        }
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, inCv.getCvFacility().getFcyGkey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_GKEY, inCv.getCvGkey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_TYPE, LocTypeEnum.VESSEL))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S20_INBOUND))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_POD, inCv.getCvFacility().getFcyRoutingPoint().getPointGkey()));
        dq.setScopingEnabled(false);
        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq);
    }
    private Serializable[] findUfvsWithNoPOD(CarrierVisit inCv) {
        if (inCv == null || inCv.getCvFacility() == null) {
            return null;
        }
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, inCv.getCvFacility().getFcyGkey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_GKEY, inCv.getCvGkey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_TYPE, LocTypeEnum.VESSEL))
        //.addDqPredicate(PredicateFactory.eq(UnitField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S20_INBOUND))
                .addDqPredicate(PredicateFactory.isNull(UnitField.UFV_POD));
        dq.setScopingEnabled(false);
        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq);
    }

}
