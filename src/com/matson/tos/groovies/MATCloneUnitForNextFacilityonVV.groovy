import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.IEvent
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.atoms.CarrierModeEnum
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Facility
import com.navis.argo.business.reference.RoutingPoint
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
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
import com.navis.road.business.util.RoadBizUtil
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.rules.EventType
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.inventory.business.units.ReeferRqmnts
import org.apache.log4j.Logger

/**
 *
 *  Create a second unit for a unit moving from one facility to another on the ship
 */

public class MATCloneUnitForNextFacilityonVV extends AbstractGeneralNoticeCodeExtension

{
    public void execute(GroovyEvent inEvent)

    {
        this.log("Execution Started MATCloneUnitForNextFacilityonVV and sleeping for 45 seconds");

        Thread.sleep(60000);
        /* get the event */

        Event thisEvent = inEvent.getEvent();

        if (thisEvent == null)
            return;

        /* Get the unit and the Booking */

        VesselVisitDetails vesselVisit = (VesselVisitDetails) inEvent.getEntity();

        if (vesselVisit == null)
            return;

        CarrierVisit carrierVisit = vesselVisit.getCvdCv();
        CarrierVisit newCarrierVisit = null;
        Facility facility = ContextHelper.getThreadFacility();
        if (carrierVisit.getCvNextFacility() != null) {
            newCarrierVisit = carrierVisit.findOrCreateNextVisit();
            //CarrierVisit.findCarrierVisit(carrierVisit.getCvNextFacility(), LocTypeEnum.VESSEL, carrierVisit.getCvId());
        }
        if (newCarrierVisit != null) {
            GroovyApi api = new GroovyApi();
            def MATUpdateClonedUnit = api.getGroovyClassInstance("MATUpdateClonedUnit");

            DomainQuery domainQuery = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                    .addDqPredicate(PredicateFactory.eq(InventoryField.UFV_ACTUAL_OB_CV, carrierVisit.getPrimaryKey()))
                    .addDqPredicate(PredicateFactory.eq(InventoryField.UFV_FACILITY, facility.getFcyGkey()));

            List<Serializable> ufvGkeys = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(domainQuery);
            this.log("List is "+ufvGkeys);

            final UserContext userContext = ContextHelper.getThreadUserContext();
            List<Serializable> oldUfvGkeys = ufvGkeys;

            PersistenceTemplate template = new PersistenceTemplate(userContext);
            MessageCollector mc = template.invoke(new CarinaPersistenceCallback() {
                @Override
                protected void doInTransaction() {
                    try {

                        if (oldUfvGkeys != null && oldUfvGkeys.size() > 0) {
                            for (Serializable ufvGkey : oldUfvGkeys) {
                                UnitFacilityVisit unitFacilityVisit = (UnitFacilityVisit) HibernateApi.getInstance().load(UnitFacilityVisit.class, ufvGkey);
                                try {
                                    LOGGER.warn("UFV is " + unitFacilityVisit);
                                    if (unitFacilityVisit != null) {
                                        Unit unit = unitFacilityVisit.getUfvUnit();
                                        UnitFacilityVisit nextUfv = unit.getUfvForFacilityNewest(carrierVisit.getCvNextFacility());
                                        if (nextUfv) {
                                            RectifyParms thisRectifyParm = new RectifyParms();
                                            thisRectifyParm.setEraseHistory(false);
                                            thisRectifyParm.setUfvTransitState(UfvTransitStateEnum.S70_DEPARTED);
                                            thisRectifyParm.setUnitVisitState(UnitVisitStateEnum.DEPARTED);
                                            nextUfv.rectify(thisRectifyParm);
                                            for (UnitFacilityVisit unitFacilityVisit1 : unit.getUnitUfvSet()) {
                                                unitFacilityVisit1.setUfvVisibleInSparcs(false);
                                            }
                                            RoadBizUtil.commit();
                                        }
                                    }
                                } catch (Exception e) {
                                    LOGGER.warn(e);
                                }
                            }
                        }

                    } catch (Exception e) {

//null          }
                    }
                }
            });

            LOGGER.warn("Deleted the old UFVs and going to sleep for 45 seconds");
            Thread.sleep(60000);
            if (ufvGkeys != null && ufvGkeys.size() > 0) {
                for (Serializable ufvGkey : ufvGkeys) {
                    UnitFacilityVisit unitFacilityVisit = (UnitFacilityVisit) HibernateApi.getInstance().load(UnitFacilityVisit.class, ufvGkey);
                    this.log("UFV is " + unitFacilityVisit);
                    if (unitFacilityVisit != null) {
                        Unit unit = unitFacilityVisit.getUfvUnit();
                        UnitFacilityVisit   nextUfv = unit.getUfvForFacilityNewest(carrierVisit.getCvNextFacility());
                        Boolean isUnitCloned = false;
                        if (nextUfv) {
                            this.log("Found next UFV "+ nextUfv);
                            try {
                                if (nextUfv.getUfvActualIbCv()!= null && !newCarrierVisit.equals(nextUfv.getUfvActualIbCv()) &&
                                        LocTypeEnum.VESSEL.equals(nextUfv.getUfvActualIbCv().getCvCarrierMode())) {
                                    newCarrierVisit = nextUfv.getUfvActualIbCv();
                                }
                                unit.deleteUfv(nextUfv);
                                UnitCategoryEnum newCategoryEnum = computeUnitCategory(unit.getUnitRouting().getRtgPOD1(), facility.getFcyRoutingPoint());
                                UnitCategoryEnum existingCategory = unit.getUnitCategory();
                                ServicesManager servicesManager = (ServicesManager)Roastery.getBean(ServicesManager.BEAN_ID);
                                EventType eventType = EventType.findEventType("UNIT_OUT_GATE");
                                Event event = (Event) servicesManager.getMostRecentEvent(eventType,unit);
                                if (event != null && facility.equals(event.getEvntFacility())){
                                    unit.setUnitCategory(UnitCategoryEnum.IMPORT);
                                } else {
                                    if (UnitCategoryEnum.IMPORT.equals(existingCategory) || UnitCategoryEnum.THROUGH.equals(existingCategory)) {
                                        this.log("Updating the category back to " + newCategoryEnum);
                                        unit.setUnitCategory(newCategoryEnum);
                                    }
                                }
                                HibernateApi.getInstance().saveOrUpdate(unit);
                                RoadBizUtil.commit();
                                isUnitCloned = MATUpdateClonedUnit.createAndUpdateClonedUnit(unit, newCarrierVisit, nextUfv, carrierVisit.getCvNextFacility(),unitFacilityVisit);
                                if (isUnitCloned) {
                                    LOGGER.warn("Deleting UFV "+unit.getUnitId());
                                    /*    HibernateApi.getInstance().delete(unitFacilityVisit);

                                        ServicesManager sm = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);

                                        EventType cloneUnitEventType = EventType.findEventType('UNIT_CLONE_NEXT_FACILITY');
                                        if (sm != null && cloneUnitEventType != null) {
                                          (IEvent) sm.recordEvent(cloneUnitEventType, "Clone Unit for next facility ", null, null, unit, (FieldChanges) null);
                                        }*/
                                    RoadBizUtil.commit();
                                }
                            } catch (Exception e) {
                                LOGGER.warn("Exception while cloning unit "+e);
                            }
                        }

                    }
                }
            }
        }
    }
    public void log(String inMsg){
        LOGGER.warn(inMsg);
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
    private final static Logger LOGGER = Logger.getLogger(MATCloneUnitForNextFacilityonVV.class);
}