import org.apache.log4j.Logger
import com.navis.framework.portal.UserContext
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.api.GroovyApi
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.QueryUtils
import com.navis.argo.ArgoExtractEntity
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.business.Roastery
import com.navis.argo.ArgoExtractField
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.framework.portal.query.Junction
import com.navis.framework.portal.query.Disjunction

/**
 *  This is a Pre-Deployable Groovy Plug-in which cancels the CUEs where STORAGE/REEFER CUE is in QUEUED status,
 *                  has no Guarantees and the unit is delivered.
 * @author <a href="mailto:psethuraman@zebra.com">Prakash</a> Apr 14, 2010 Time: 3:10:01 PM
 */
public class UpdateCUEsCancelledForDeliveredAndNoGuarantee extends GroovyApi {
    public void execute(Map parameters) {
        logInfo("Start execution of Groovy Plug-in: UpdateCUEsCancelledForDeliveredAndNoGuarantee");
        System.out.println("Start execution of Groovy Plug-in: UpdateCUEsCancelledForDeliveredAndNoGuarantee");

        // USER CAN CHANGE THESE FIEDLS BASED ON REQUIREMENTS.
        List eventTypeList = new ArrayList();
        eventTypeList.add("STORAGE");
        eventTypeList.add("REEFER");
        String[] eventTypes = eventTypeList.toArray(new String[eventTypeList.size()]);

        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = new Date(ArgoUtils.timeNow().dateString);

        List chargeEvents = getStorateReeferQueuedCUEs(eventTypes, QUEUED, timeNow);

        if(chargeEvents.isEmpty()) {
            logInfo("UpdateCUEsCancelledForDeliveredAndNoGuarantee: No storage/Reefer records found for Groovy Plug-in");
            System.out.println("UpdateCUEsCancelledForDeliveredAndNoGuarantee: No storage/Reefer records found for Groovy Plug-in");
        }
        for (ChargeableUnitEvent chargeEvent: chargeEvents) {
            try {
                if (!hasValidGuarantee(chargeEvent)) {
                    chargeEvent.setBexuStatus(CANCELLED);
                    Roastery.getHibernateApi().update(chargeEvent);
                    System.out.println("UpdateCUEsCancelledForDeliveredAndNoGuarantee: Queued CUE ("+chargeEvent.getBexuGkey()+
                            ") for unit "+chargeEvent.getBexuEqId()+" is Cancelled");
                }
            } catch (Exception e) {
                System.out.println("UpdateCUEsCancelledForDeliveredAndNoGuarantee: Exception occured while trying to cancel the CUE ("
                        +chargeEvent.getBexuGkey()+") for unit : "+chargeEvent.getBexuEqId());
            }
        }
        logInfo("End execution of Groovy Plug-in: UpdateCUEsCancelledForDeliveredAndNoGuarantee");
        System.out.println("End execution of Groovy Plug-in: UpdateCUEsCancelledForDeliveredAndNoGuarantee");
    }

    private List getStorateReeferQueuedCUEs(String[] inEventTypes, String inStatus, Date inTimeNow) {

        Junction timeNotNullOrBeforeToday =  new Disjunction()
                .add(PredicateFactory.isNotNull(ArgoExtractField.BEXU_EVENT_END_TIME))
                .add(PredicateFactory.lt(ArgoExtractField.BEXU_EVENT_END_TIME, inTimeNow));

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT)
                .addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_EVENT_TYPE, inEventTypes))
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_STATUS, inStatus))
                .addDqPredicate(PredicateFactory.isNotNull(ArgoExtractField.BEXU_RULE_END_DAY))
                .addDqPredicate(timeNotNullOrBeforeToday);

        return Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
    }

    private boolean hasValidGuarantee(ChargeableUnitEvent inChargeEvent) {

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.GUARANTEE)
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY, inChargeEvent.getBexuGkey()))
                .addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_APPLIED_TO_CLASS, BillingExtractEntityEnum.INV))
                .addDqPredicate(PredicateFactory.isNull(ArgoExtractField.GNTE_VOIDED_OR_EXPIRED_DATE))
                .addDqPredicate(PredicateFactory.lt(ArgoExtractField.GNTE_GUARANTEE_START_DAY, inChargeEvent.getBexuRuleEndDay()));

        List guarantees = Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);;
        if (guarantees.isEmpty()) {
            logInfo("UpdateCUEsCancelledForDeliveredAndNoGuarantee: No valid Guarantees found for CUE : "+inChargeEvent.getBexuGkey());
            System.out.println("UpdateCUEsCancelledForDeliveredAndNoGuarantee: No valid Guarantees found for CUE : "+inChargeEvent.getBexuGkey());
            return false;
        }
        System.out.println("UpdateCUEsCancelledForDeliveredAndNoGuarantee: valid guarantee found for CUE : "+inChargeEvent.getBexuGkey());
        logInfo("UpdateCUEsCancelledForDeliveredAndNoGuarantee: valid guarantee found for CUE : "+inChargeEvent.getBexuGkey());
        return true;
    }


    public static final String QUEUED = "QUEUED";
    public static final String CANCELLED = "CANCELLED";
    private static final Logger LOGGER = Logger.getLogger(UpdateCUEsCancelledForDeliveredAndNoGuarantee.class);
}
