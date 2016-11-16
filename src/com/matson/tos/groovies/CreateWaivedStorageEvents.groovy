import com.navis.argo.business.api.GroovyApi
import com.navis.framework.portal.UserContext
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import org.apache.log4j.Logger
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.QueryUtils
import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.framework.portal.query.PredicateFactory
import com.navis.argo.business.atoms.GuaranteeTypeEnum
import com.navis.framework.business.Roastery
import com.navis.argo.business.extract.Guarantee
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.framework.util.ValueObject
import com.navis.framework.portal.FieldChanges
import com.navis.framework.portal.Ordering
import com.navis.argo.business.atoms.GuaranteeOverrideTypeEnum
import com.navis.framework.metafields.MetafieldIdList
import com.navis.framework.metafields.MetafieldId


/**
 * This is a Pre-Deployable Groovy Plug-in which creates STORAGE_WAIVER events for waived storage events.
 * @author <a href="mailto:tramakrishnan@navis.com"> tramakrishnan</a> Dec 02, 2009 Time: 2:36:01 PM
 */
public class CreateWaivedStorageEvents extends GroovyApi {

    public void execute(Map parameters) {

        LOGGER.info("Groovy : CreateWaivedStorageEvents - starts to create STORAGE_WAIVER events for waived storage events !");
        System.out.println("Groovy : CreateWaivedStorageEvents - starts to create STORAGE_WAIVER events for waived storage events !");
        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

        List statusList = new ArrayList<String>();
        statusList.add(DRAFT);
        statusList.add(QUEUED);
        statusList.add(PARTIAL);
        String[] statuses = statusList.toArray(new String[statusList.size()]);

        String eventTypeId = "STORAGE";
        String eventTypeIdToCreate = "STORAGE_WAIVER";
        String eventTypeDesc = "Event for STORAGE Waiver";
        String invoiceTypeId = "Waived Storage Invoice";
        String currencyId = "UZD";
        String action = "DRAFT";

        //For each "STORAGE" CUE with status in [DRAFT, QUEUED, PARTIAL] and (End Date <> NULL)
        List guarantees = getNonVoidedGntesForFixedPriceWaiversFor(eventTypeId, statuses);
        if (guarantees.isEmpty()) {
            logInfo("No records matched for Groovy Plug-in: CreateWaivedStorageEvents");
            System.out.println("No records matched for Groovy Plug-in: CreateWaivedStorageEvents");
        } else {
            for (Guarantee guarantee: guarantees) {
                // for each guarantee create "STORAGE_WAIVER" CUE event
                ChargeableUnitEvent storageGnteEvent = createStorageWaiverEvent(guarantee, eventTypeIdToCreate);
                String gnteId = guarantee.getGnteGuaranteeId();
                if (storageGnteEvent != null) {
                    String unitId = storageGnteEvent.getBexuEqId();

                    logInfo("Chargeable unit event '" + eventTypeIdToCreate + "' for Unit : " + unitId + " for Guarantee ID:" + gnteId + " created successfully!");
                    System.out.println("Chargeable unit event '" + eventTypeIdToCreate + "' for Unit : " + unitId + " for Guarantee ID:" + gnteId + " created successfully!");
                } else {
                    logInfo("Chargeable unit event '" + eventTypeIdToCreate + "' for Unit : " + guarantee.getGnteAppliedToNaturalKey() + " for Guarantee ID:" + gnteId + " creation failed !");
                    System.out.println("Chargeable unit event '" + eventTypeIdToCreate + "' for Unit : " + guarantee.getGnteAppliedToNaturalKey() + " for Guarantee ID:" + gnteId + " creation failed!");
                }
            }
        }

        LOGGER.info("Groovy : CreateWaivedStorageEvents - Ends");
        System.out.println("Groovy : CreateWaivedStorageEvents - Ends");
    }


    /**
     * Returns a list of non-voided waivers whose chargeable unit event's Satus in [DRAFT, QUEUED, PARTIAL] and EndDate!= NULL and GuaranteeThruDay!=NULL
     */
    private static List getNonVoidedGntesForFixedPriceWaiversFor(String inEventTypeId, String[] inStatuses) {

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.GUARANTEE).
                addDqPredicate(PredicateFactory.isNull(ArgoExtractField.GNTE_VOIDED_OR_EXPIRED_DATE)).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_GUARANTEE_TYPE, GuaranteeTypeEnum.OAC)).
                addDqOrdering(Ordering.asc(ArgoExtractField.GNTE_GUARANTEE_START_DAY));

        DomainQuery waiverQuery = QueryUtils.createDomainQuery(ArgoExtractEntity.GUARANTEE).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_OVERRIDE_VALUE_TYPE, GuaranteeOverrideTypeEnum.FIXED_PRICE)).
                addDqPredicate(PredicateFactory.isNull(ArgoExtractField.GNTE_VOIDED_OR_EXPIRED_DATE)).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_GUARANTEE_TYPE, GuaranteeTypeEnum.WAIVER));

        DomainQuery subQuery = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, inEventTypeId)).
                addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_STATUS, inStatuses)).
                addDqPredicate(PredicateFactory.isNotNull(ArgoExtractField.BEXU_EVENT_END_TIME)).
                addDqPredicate(PredicateFactory.isNotNull(ArgoExtractField.BEXU_GUARANTEE_THRU_DAY));

        if (subQuery != null) {
            dq.addDqPredicate(PredicateFactory.subQueryIn(subQuery, ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY));
        }

        if (waiverQuery != null) {
            dq.addDqPredicate(PredicateFactory.subQueryIn(waiverQuery, ArgoExtractField.GNTE_RELATED_GUARANTEE));
        }

        return (Roastery.getHibernateApi().findEntitiesByDomainQuery(dq));
    }

    private static ChargeableUnitEvent createStorageWaiverEvent(Guarantee inGuarantee, String inEventTypeToCreate) {
        String status = "GUARANTEED";
        ScopedBizUnit gnteCustomer = inGuarantee.getGnteGuaranteeCustomer();
        String payeeId = gnteCustomer != null ? inGuarantee.getGnteGuaranteeCustomer().getBzuId() : null;
        Date startTime = inGuarantee.getGnteGuaranteeStartDay();
        Date endTime = inGuarantee.getGnteGuaranteeEndDay();
        String unitId = inGuarantee.getGnteAppliedToNaturalKey();
        ChargeableUnitEvent gnteCue = ChargeableUnitEvent.getCUErecordForGuarantee(inGuarantee);
        // create new Event STORAGE_WAIVER
        ChargeableUnitEvent cue = ChargeableUnitEvent.create(gnteCue.getBexuUfvGkey(), ContextHelper.getThreadOperator());
        // copy the previous values to the newly created event
        ValueObject vao = gnteCue.getValueObject();

        FieldChanges changes = new FieldChanges(vao);
        changes.removeFieldChange(ArgoExtractField.BEXU_GKEY);
        MetafieldIdList fields = getFieldsToRemove();
        removeFieldChanges(changes, fields);
        cue.applyFieldChanges(changes);
        //  set facility
        cue.setBexuFacility(gnteCue.getBexuFacility());
        Roastery.getHibernateApi().saveOrUpdate(cue);
        //  update values from Guarantee
        cue.setBexuEventType(inEventTypeToCreate);
        cue.setBexuStatus(status);
        cue.setBexuGuaranteeGkey(inGuarantee.getGnteGkey());
        cue.setBexuGuaranteeId(inGuarantee.getGnteGuaranteeId());
        cue.setBexuEqId(unitId);
        cue.setBexuPayeeCustomerId(payeeId);
        cue.setBexuGuaranteeParty(payeeId);
        cue.setBexuQuantity(inGuarantee.getGnteQuantity());
        cue.setBexuEventStartTime(startTime);
        Date cueEndTime = gnteCue.getBexuEventEndTime();
        cue.setBexuEventEndTime(cueEndTime.before(endTime) ? cueEndTime : endTime);

        //  update related guarantee i.e.Waiver details
        Guarantee waiver = inGuarantee.getGnteRelatedGuarantee();
        //Is Value Override = TRUE
        cue.setBexuIsOverrideValue(Boolean.TRUE);
        //Override Type = Waiver.Waiver Type
        GuaranteeOverrideTypeEnum waiverType = waiver.getGnteOverrideValueType();
        cue.setBexuOverrideValueType(waiverType.getKey());
        //Override Value = Waiver.Guarantee Amount
        Double waiverAmount = waiver.getGnteGuaranteeAmount();
        cue.setBexuOverrideValue(waiverAmount);

        Roastery.getHibernateApi().saveOrUpdate(cue);
        return cue;
    }

    private static void removeFieldChanges(FieldChanges inSoureChanges, MetafieldIdList inFields) {
        if (inFields != null) {
            for (MetafieldId field: inFields) {
                inSoureChanges.removeFieldChange(field);
            }
        }
    }

    private static MetafieldIdList getFieldsToRemove() {
        MetafieldIdList fields = new MetafieldIdList();
        fields.add(ArgoExtractField.BEXU_GKEY);
        fields.add(ArgoExtractField.BEXU_CREATED);
        fields.add(ArgoExtractField.BEXU_CREATOR);
        fields.add(ArgoExtractField.BEXU_CHANGED);
        fields.add(ArgoExtractField.BEXU_CHANGER);
        return fields;
    }

    private static final String DRAFT = "DRAFT";
    private static final String QUEUED = "QUEUED";
    private static final String PARTIAL = "PARTIAL";
    private static final Logger LOGGER = Logger.getLogger(CreateWaivedStorageEvents.class);
}
