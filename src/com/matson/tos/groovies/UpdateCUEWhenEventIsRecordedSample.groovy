import com.navis.argo.business.api.GroovyApi
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.Event
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.framework.business.Roastery
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.services.business.event.GroovyEvent

/**
 * Created by IntelliJ IDEA.
 * User: mraghavachari
 * Date: Feb 28, 2010
 * Time: 9:46:05 PM
 * This groovy plug-in is initiated when recording an event. While recording the current event type, it updates the
 * ChargeableUnitEvent of another event's column for the same curent recording event's UnitFacilityVisit.
 * It expects an input of the target Event Type ID as string value.
 */

public class UpdateCUEWhenEventIsRecordedSample extends GroovyApi {

    public void execute(Object event, String inEventTypeId) {

        logInfo("Start execution of Groovy Plug-in: UpdateCUEWhenEventIsRecordedSample");
        def unit =  event.getEntity();
        def evnt =  event.getEvent();
        Date evntAppliedDate = evnt.getEvntAppliedDate();
        // Get UnitFacilityVisit using Event applied date time
        UnitFacilityVisit ufv = unit.getUfvForEventTime(evntAppliedDate);
        // Get ChargeableUnitEvent record
        List cueList = findByEventTypeIdAndUfv(ufv, inEventTypeId);

        if (!cueList.isEmpty()) {
            if (cueList.size() > 1) {

                logWarn("Groovy : UpdateCUEWhenEventIsRecordedSample: Expected only one CUE event of " + inEventTypeId +
                        " for Equipment Id : " + ufv.getPrimaryEqId());
            } else {
                ChargeableUnitEvent cue =  cueList.get(0);
                if (cue != null) {
                    cue.setBexuFlexDate05(evntAppliedDate);
                }
            }
        }
        logInfo("End execution of Groovy Plug-in: UpdateCUEWhenEventIsRecordedSample");

    }


    /**
     * Get CUE List for Event Type ID and Ufv Gkey not equal to CALCELLED status.
     */
    private List findByEventTypeIdAndUfv(UnitFacilityVisit inUfv, String inEventTypeId) {
        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, inEventTypeId))
                .addDqPredicate(PredicateFactory.ne(ArgoExtractField.BEXU_STATUS, CANCELLED))
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_UFV_GKEY, inUfv.getPrimaryKey()));
        return Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
    }
    private static final String CANCELLED = "CANCELLED";
}
