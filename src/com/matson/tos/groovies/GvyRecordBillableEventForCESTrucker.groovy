import org.apache.log4j.Logger;
import org.apache.log4j.Level

import com.navis.argo.business.api.ServicesManager
import com.navis.services.business.rules.EventType
import com.navis.framework.business.Roastery
import com.navis.framework.util.BizViolation
import com.navis.argo.business.api.Serviceable
import com.navis.inventory.business.units.Unit
import com.navis.argo.business.atoms.EventEnum
import com.navis.services.business.event.GroovyEvent
import com.navis.argo.business.atoms.DrayStatusEnum
import com.navis.services.business.api.EventManager
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.argo.TruckingCompany

/**
 * BILL-920 This is a Pre-Deployable Groovy Plug-in which records an event if the Gate transaction's trucking company
 * is an ExamSiteCarrier.
 * ARGO-25669 modified this groovy to cover the below scenarios,
 *
 * CASE1: This is executed during gate transaction. If the tran type = DeliverExport, if unit is assigned with a trucking company and
 *  the truckingcompany of unit is a CES designated one - then record the billable event if the event does not already exist for that unit
 *
 * CASE2: While recording the event UNIT_TRUCKER_ASSIGNED, if the unit category is EXPORT, if it is assigned to a trucking company which
 *  is a CES designated trucking company - then record the billable event if the event does not already exist for that unit
 *
 * CASE3: While recording the event UNIT_REROUTE, if the unit category is EXPORT and if the unit's dray status is Dray out and back -
 *  then record the billable event if the event does not already exist for that unit
 *
 * @author <a href="mailto:mkamalakannan@zebra.com"> mkamalakannan</a> Apr 14, 2010 Time: 10:36:01 AM
 */
public class GvyRecordBillableEventForCESTrucker {

    // This is a customizable eventId. The customer can modify the billableEventId.
    String billableEventId = "EXPORT_EXAM_REQUIRED";

    public void execute(Object inDao) {
        Level previousLevel = LOGGER.getLevel();
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("Start Groovy Execution: GvyRecordBillableEventForCESTrucker");

        // CASE1: This is executed during gate transaction. If the tran type = DeliverExport, if unit is assigned with a trucking company and
        //  the truckingcompany of unit is a CES designated one - then record the billable event if it does not already exist for that unit
        if (inDao.getTran() != null && com.navis.road.business.atoms.TranSubTypeEnum.DE.equals(inDao.getTran().getTranSubType())) {
            Unit unit = inDao.getTran().getTranUnit();
            TruckingCompany trkCo = inDao.getTran().getTranTruckingCompany();

            if (unit != null && UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory()) && trkCo != null && trkCo.equals(unit.getUnitRtgTruckingCompany())) {
                // event is recorded only if there is no history of it
                Boolean eventRecorded = isEventAlreadyRecorded(unit, billableEventId);

                if (eventRecorded != null && !eventRecorded && inDao.getTran().getTranTruckingCompany().getTrkcIsExamSiteCarrier()) {
                    postEvent(billableEventId, unit, unit.getUnitId());
                }
            }
        }
        LOGGER.info("End Groovy Execution: GvyRecordBillableEventForCESTrucker");
        LOGGER.setLevel(previousLevel);
    }

    public void execute(GroovyEvent inEvent, Object inApi) {
        println("Start Groovy Execution: GvyRecordBillableEventForCESTrucker");
        Level previousLevel = LOGGER.getLevel();
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("Start Groovy Execution: GvyRecordBillableEventForCESTrucker ");

        Unit unit = inEvent.getEntity();
        if(!UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory())) {
            LOGGER.info("End Groovy Execution: GvyRecordBillableEventForCESTrucker");
            LOGGER.setLevel(previousLevel);
            return;
        }

        String eventTypeId = inEvent.getEvent().getEvntEventType().getEvnttypeId();
        // event is recorded only if there is no history of it
        Boolean eventRecorded = isEventAlreadyRecorded(unit, billableEventId);
        if(eventRecorded) {
            LOGGER.info("GvyRecordBillableEventForCESTrucker: Event "+ billableEventId + " already recorded for the unit " + unit.getUnitId());
            LOGGER.info("End Groovy Execution: GvyRecordBillableEventForCESTrucker");
            LOGGER.setLevel(previousLevel);
            return;
        }

        if(EventEnum.UNIT_TRUCKER_ASSIGNED.equals(EventEnum.getEnum(eventTypeId))) {
            // CASE2: While recording the event UNIT_TRUCKER_ASSIGNED, if the unit category is EXPORT, if it is assigned to a trucking company which
            //  is a CES designated trucking company - then record the billable event if it does not already exist for that unit

            if (unit != null) {
                TruckingCompany trkCo = null;
                ScopedBizUnit trkCoSbu = unit.getUnitRtgTruckingCompany();
                if (trkCoSbu != null && BizRoleEnum.HAULIER.equals(trkCoSbu.getBzuRole())) {
                    trkCo = (TruckingCompany) HibernateApi.getInstance().
                            downcast(trkCoSbu, com.navis.road.business.model.TruckingCompany.class);
                }
                if(trkCo != null && trkCo.getTrkcIsExamSiteCarrier()) {
                    postEvent(billableEventId, unit, unit.getUnitId());
                }
            }
        } else if(EventEnum.UNIT_REROUTE.equals(EventEnum.getEnum(eventTypeId))) {
            // CASE3: While recording the event UNIT_REROUTE, if the unit category is EXPORT and if the unit's dray status is Dray out and back -
            //  then record the billable event if it does not already exist for that unit
            if(unit != null && DrayStatusEnum.OFFSITE.equals(unit.getUnitDrayStatus())) {  // check for dray out and back
                postEvent(billableEventId, unit, unit.getUnitId());
            }
        }

        LOGGER.info("End Groovy Execution: GvyRecordBillableEventForCESTrucker");
        LOGGER.setLevel(previousLevel);
    }

    private void postEvent(String inEventId, Serviceable inServiceable, String inUnitId) {
        ServicesManager sm = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID);
        EventType eventType = EventType.findEventType(inEventId);
        if (eventType == null) {
            LOGGER.error("GvyRecordBillableEventForCESTrucker postNewEvent: Unknown EventType in script: " + inEventId);
            return;
        }
        try {
            sm.recordEvent(eventType, null, null, null, inServiceable);
            LOGGER.info("GvyRecordBillableEventForCESTrucker : Event " + billableEventId + " recorded for Unit "+ inUnitId);
        } catch (BizViolation bv) {
            LOGGER.error("GvyRecordBillableEventForCESTrucker postEvent: failed with = " + bv.getLocalizedMessage());
        }
    }

    private boolean isEventAlreadyRecorded(Unit inUnit, String inEventId) {
        EventType eventType = EventType.findEventType(inEventId);
        if (eventType == null) {
            LOGGER.error("GvyRecordBillableEventForCESTrucker: Unknown Event type " + inEventId);
            return null;
        }
        EventManager em = (EventManager) Roastery.getBean(EventManager.BEAN_ID);
        List events = em.getEventHistory(eventType, inUnit);
        if (events.isEmpty()) {
            return false;
        } else {
            return true;
        }
    }

    private static final Logger LOGGER = Logger.getLogger(GvyRecordBillableEventForCESTrucker.class);
}
