import com.navis.argo.business.model.Facility;
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.business.Roastery
import com.navis.argo.business.api.ServicesManager
import com.navis.services.business.event.Event;
import com.navis.services.business.rules.EventType
import com.navis.argo.business.atoms.WiMoveKindEnum;
import com.navis.inventory.business.moves.MoveEvent
import com.navis.inventory.business.units.UnitFacilityVisit;
//import com.navis.argo.business.model.Position;
import com.navis.spatial.business.model.Position;
import com.navis.inventory.business.units.Unit;
import org.apache.log4j.Logger;
import com.navis.framework.portal.FieldChanges;

/**
 * 2011.10.30 Gbabu Unit discharge is created when the container is placed on the truck, so updates
 * are not being sent to other interfaces with the correct position
 * This Groovy records a custom event UNIT_DISCH_COMPLETE
 */
class DischargeCompleteGroovy extends AbstractEntityLifecycleInterceptor {

    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        if (inOriginalFieldChanges == null) {
            log("MoveEvent(onupdate) Groovy called with null field changes, ignoring the call");
            return;
        }

        try {
            recordServiceEventOnDischargeCompletion(inEntity, inOriginalFieldChanges, inMoreFieldChanges)
        } catch (Throwable e) {
            log("MoveEventGroovy: Problem occured while attempting to write custom event on discharge/deramp completion " + e.getMessage());
            e.printStackTrace();
            LOGGER.error("MoveEventGroovy: error occured while attempting to write custom event", e);
        }

    }

    public void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        if (inOriginalFieldChanges == null) {
            log("MoveEvent(onupdate) Groovy called with null field changes, ignoring the call");
            return;
        }

        try {
            recordServiceEventOnDischargeCompletion(inEntity, inOriginalFieldChanges, inMoreFieldChanges)
        } catch (Throwable e) {
            log("MoveEventGroovy: Problem occured while attempting to write custom event on discharge/deramp completion " + e.getMessage());
            e.printStackTrace();
            LOGGER.error("MoveEventGroovy: error occured while attempting to write custom event", e);
        }
    }

    public void validateChanges(EEntityView inEntity, EFieldChangesView inFieldChanges) {

// Extra logging for debuggin, can be removed.

    }

    private void recordServiceEventOnDischargeCompletion(EEntityView inEntity, EFieldChangesView inFieldChanges, EFieldChanges inMoreFieldChanges) {

        Object entityObject = inEntity._entity
        if (!(entityObject instanceof MoveEvent)) {
            log("ignoring the call as the underlying entity is not MoveEntity")
            return;
        }

        MoveEvent thisEvent = inEntity._entity

        WiMoveKindEnum moveKind = thisEvent.getMveMoveKind();
        if (moveKind != null && WiMoveKindEnum.VeslDisch.equals(moveKind) && thisEvent.mveTimePut != null) {

            UnitFacilityVisit inUfv = thisEvent.getMveUfv();

            String completionStringFromXps = thisEvent.getEvntAppliedBy();
            Facility eventFacility = thisEvent.getEvntFacility();
            String completionStringExpectedFromXps = "COMPLETE_MOVE";
            Position toPos = thisEvent.getMveToPosition();
            Position fromPos = thisEvent.getMveFromPosition();
            boolean isTimeComplete = thisEvent.mveTimePut != null;
            ServicesManager sm = (ServicesManager) Roastery.getBean(ServicesManager.BEAN_ID)
            EventType eventType = EventType.findEventType('UNIT_DISCH_COMPLETE')
            if (sm != null && eventType != null) {
                if (inUfv != null) {
                    Unit unit = inUfv.getUfvUnit();
                    if (unit != null) {
                        String unitId = unit.getUnitId();
                        String eventNote = "Discharge completed for unit : " + unitId;
                        try {
                            sm.recordEvent(eventType, eventNote, null, null, unit, (FieldChanges) null)
                            log("MoveEventGroovy: Recorded Event : " + eventNote);
                            if (eventFacility != null) {
                                Event event = (Event) sm.getMostRecentEvent(eventType, unit);
                                if (event != null) {
                                    event.setEvntFacility(eventFacility);
                                    event.setEvntYard(eventFacility.getActiveYard());
                                }
                            }

                        } catch (Exception e) {
                            // do not throw error for now
                        }
                    }
                }
            }
        }
    }
    private Logger LOGGER = Logger.getLogger(MoveEvent.class);
}
