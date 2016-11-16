import org.apache.log4j.Logger

import com.navis.framework.util.BizFailure
import com.navis.framework.portal.QueryUtils
import com.navis.framework.business.Roastery
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.Junction
import com.navis.framework.portal.query.Disjunction
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory

import com.navis.argo.ContextHelper
import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.CarrierModeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.atoms.LogicalEntityEnum
import com.navis.argo.business.extract.ChargeableUnitEvent

import com.navis.inventory.business.api.UnitStorageManager
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.api.EventManager
import com.navis.services.business.rules.EventType

/**
 * This is a Pre-Deployable Groovy Plug-in to apply On Terminal fee for storage units in yard more than 10 days.
 *
 * @author <a href="mailto:psethuraman@zebra.com">Prakash</a> Apr 05, 2010, 1:01:08 PM
 */
public class GenerateOnTerminalFeeEvent extends GroovyApi {
    public void execute(Map parameters) {
        logInfo("GenerateOnTerminalFeeEvent: Start execution of Groovy Plug-in:");
        System.out.println("GenerateOnTerminalFeeEvent: Start execution of Groovy Plug-in");

        // USER CAN CHANGE THESE FIEDLS BASED ON REQUIREMENTS.
        String cueEventType = "STORAGE";
        String newEventTypeId = "ON TERMINAL OVER 10 DAYS";  // Expected to have this event in Event Type.

        //  Do not change below this line.
        String category = UnitCategoryEnum.EXPORT.getKey();
        EventType eventType = EventType.findEventType(newEventTypeId);
        if (eventType == null) {
            throw BizFailure.create("GenerateOnTerminalFeeEvent: No EventType defined already for \'ON TERMINAL OVER 10 DAYS\'")
        }

        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());
        UnitStorageManager manager = (UnitStorageManager) Roastery.getBean(UnitStorageManager.BEAN_ID);
        List chargeEvents = getCUEsForOnTermianl(cueEventType, category);

        if(chargeEvents.isEmpty()) {
            logInfo("GenerateOnTerminalFeeEvent: No storage records found for Groovy Plug-in");
            System.out.println("GenerateOnTerminalFeeEvent: No storage records found for Groovy Plug-in");
        }
        for (ChargeableUnitEvent chargeEvent: chargeEvents) {
            try {
                UnitFacilityVisit ufv = (UnitFacilityVisit) Roastery.getHibernateApi().load(UnitFacilityVisit.class, chargeEvent.getBexuUfvGkey());
                if (ufv != null && ufv.getUfvUnit() != null) {
                    System.out.println("GenerateOnTerminalFeeEvent: Unit Id : " + ufv.getUfvUnit().getUnitId());
                    int owedDays = manager.getStorageDaysOwed(ufv, cueEventType);
                    if (owedDays > ALLOWED_OWED_DAYS) {
                        System.out.println("GenerateOnTerminalFeeEvent: Days owed : " + owedDays);
                        EventManager em = (EventManager) Roastery.getBean(EventManager.BEAN_ID);
                        Event event = new Event();
                        em.persistEventAndPerformRules(event, timeNow, eventType, null, null, null, ufv.getUfvUnit(), null, null);
                        System.out.println("GenerateOnTerminalFeeEvent: " + newEventTypeId + " event created for : " + chargeEvent.getBexuEqId());
                        logInfo("GenerateOnTerminalFeeEvent:" + newEventTypeId + " event created for : " + chargeEvent.getBexuEqId());
                        chargeEvent.setBexuFlexLong05(ON_TERMINAL_EVENT_NEGATIVE_DEFAULT_VALUE);
                        Roastery.getHibernateApi().update(chargeEvent);
                    }
                }
            } catch (Exception e) {
                System.out.println("GenerateOnTerminalFeeEvent: Event is not generated for : " + chargeEvent.getBexuEqId());;
            }
        }
    }

    private List getCUEsForOnTermianl(String inEventType, String category) {

        List locTypesList = new ArrayList();
        locTypesList.add(CarrierModeEnum.TRUCK.getKey());
        locTypesList.add(CarrierModeEnum.VESSEL.getKey());

        String[] locTypes = locTypesList.toArray(new String[locTypesList.size()]);

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, inEventType))
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_CATEGORY, category))
                .addDqPredicate(PredicateFactory.isNull(ArgoExtractField.BEXU_FLEX_LONG05))
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_IB_LOC_TYPE, CarrierModeEnum.TRAIN))
                .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_OB_LOC_TYPE, locTypes));

        return Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
    }
    //User can change max. owed days
    public static final int ALLOWED_OWED_DAYS = 10;
    public static final Long ON_TERMINAL_EVENT_NEGATIVE_DEFAULT_VALUE = new Long(-999999999);
    private static final Logger LOGGER = Logger.getLogger(GenerateOnTerminalFeeEvent.class);
}
