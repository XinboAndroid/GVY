import com.navis.argo.ArgoExtractField
import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.CreditStatusEnum
import com.navis.argo.business.atoms.GuaranteeOverrideTypeEnum
import com.navis.argo.business.atoms.GuaranteeTypeEnum
import com.navis.argo.business.atoms.PaymentTypeEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.util.XmlUtil
import com.navis.external.framework.ECallingContext
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.business.Roastery
import com.navis.framework.portal.context.PortalApplicationContext
import com.navis.framework.presentation.internationalization.IMessageTranslatorProvider
import com.navis.framework.presentation.internationalization.MessageTranslator
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.internationalization.PropertyKey
import javax.xml.parsers.DocumentBuilder
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Document
import org.w3c.dom.Element

/**
 * This class contains custom code of Guarantee validation
 *
 * Note: This code is written for Maher's requirement. Please copy the code and modify as per specific customer requirement.
 */

public class GuaranteeValidation extends AbstractEntityLifecycleInterceptor {

    @Override
    public void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges,
                         EFieldChanges inMoreFieldChanges) {
        ECallingContext context = this.getCallingContext();
        if (context != null) {
            GNTE_ACTION = (String) context.getAttribute("ACTION");
            processGuarantee(inEntity);
        }
    }

    public void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        ECallingContext context = this.getCallingContext();
        if (context != null) {
            if (context != null) {
                GNTE_ACTION = (String) context.getAttribute("ACTION");
                processGuarantee(inEntity);
            }
        }
    }

    private void processGuarantee(EEntityView inEntity) {
        GuaranteeTypeEnum gnteType = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_TYPE);
        if (GuaranteeTypeEnum.OAC.equals(gnteType)) {
            guaranteeCustomerSpecific(inEntity);
        } else if (GuaranteeTypeEnum.WAIVER.equals(gnteType)) {
            waiverCustomerSpecific(inEntity);
        }
    }

    private void guaranteeCustomerSpecific(EEntityView inEntity) throws BizViolation {

        // 2010-05-18 psethuraman ARGO-24974: To check whether allowed to create guarantee for Customer Credit status, gnte payment type and gnte type.
        validToCreateGuarantee(inEntity);
        // First validate that the billable event (extract record) is in valid status.
        validateExtractRecordStatus(inEntity);

        String id = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_ID);
        BizViolation bv = null;
        // if not waiver validate the amount should be > 0
        if (!GuaranteeTypeEnum.WAIVER.equals(inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_TYPE))) {
            Double amount = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_AMOUNT);
            if (amount != null && !(amount > 0.0)) {
                bv = BizViolation.create(ArgoPropertyKeys.BILLING_GUARANTEE_AMOUNT_SHOULD_NOT_BE_ZERO, bv, id);
                registerError(bv.getLocalizedMessage().toString() + inEntity);
            }
        }

        String eventType = getGnteAppliedToEventId(inEntity);
        boolean isStorageOrReefer = STORAGE.equals(eventType) || REEFER.equals(eventType);
        // validate that end date should not be before start date for STORAGE and REEFER events
        if (isStorageOrReefer) {
            Date startDay = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_START_DAY);
            Date endDay = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_END_DAY);
            if (endDay.before(startDay)) {
                bv = BizViolation.create(ArgoPropertyKeys.BILLING_GUARANTEE_END_DATE_IS_BEFORE_START_DATE, bv, endDay, startDay, id);
                registerError(bv.getLocalizedMessage().toString() + inEntity);
            }
        }

    }

    private void waiverCustomerSpecific(EEntityView inEntity) throws BizViolation {

        // First validate that the billable event (extract record) is in valid status.
        validateExtractRecordStatus(inEntity);

        String id = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_ID);
        BizViolation bv = null;

        String eventType = getGnteAppliedToEventId(inEntity);
        boolean isStorageOrReefer = STORAGE.equals(eventType) || REEFER.equals(eventType);
        // validate that end date should not be before start date for STORAGE and REEFER events
        if (isStorageOrReefer) {
            Date startDay = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_START_DAY);
            Date endDay = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_END_DAY);
            if (endDay.before(startDay)) {
                bv = BizViolation.create(ArgoPropertyKeys.BILLING_GUARANTEE_END_DATE_IS_BEFORE_START_DATE, bv, endDay, startDay, id);
                registerError(bv.getLocalizedMessage().toString() + inEntity);
            }
        }

        GuaranteeTypeEnum guaranteeType = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_TYPE);
        GuaranteeOverrideTypeEnum gnteOverrideType = inEntity.getField(ArgoExtractField.GNTE_OVERRIDE_VALUE_TYPE);
        // Fixed rate waiver must have expiration date
        if (GuaranteeTypeEnum.WAIVER.equals(guaranteeType) && GuaranteeOverrideTypeEnum.FIXED_PRICE.equals(gnteOverrideType)) {
            if (inEntity.getField(ArgoExtractField.GNTE_WAIVER_EXPIRATION_DATE) == null) {
                registerError("Fixed rate Waivers must have Guarantee waiver expiration date." + inEntity);
            }
        }
    }


    private void validateExtractRecordStatus(EEntityView inEntity) throws BizViolation {
        /**
         * Validate Extract record status.
         *
         * @param inGuarantee
         * @param inCreateOrUpdateOrVoid
         * @throws BizViolation
         */
        Boolean isStatusValid = Boolean.TRUE;
        Long gnteGkey = inEntity.getField(ArgoExtractField.GNTE_GKEY);
        Long cueGkey = inEntity.getField(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY);
        ChargeableUnitEvent cue = (ChargeableUnitEvent) Roastery.getHibernateApi().load(ChargeableUnitEvent.class, cueGkey);

        /**
         *  GUARANTEED or PRE_AUTHORIZE
         *  1. CUE event type STORAGE or REEFER:  The CUE status should be in QUEUED / PARTIAL status. Because customer would have paid for first few
         *     days after LFD. The remaining period is allowed to be guaranteed.
         *  2. GUARANTEE  CREATE - CUE status should be in QUEUED status
         *  3. GUARANTEE  UPDATE - CUE status should be in GUARANTEED status
         *  3. GUARANTEE  VOID - CUE status should be in GUARANTEED status
         * --------------------------------------------------------------------------------
         *  WAIVED for FREE OF CHARGE
         *  1. WAIVER  CREATE - CUE status should be in QUEUED status
         *  2. WAIVER  UPDATE - CUE status should be in CANCELLED status
         *  3. WAIVER  VOID - CUE status should be in CANCELLED status
         * --------------------------------------------------------------------------------
         *  WAIVED for FIXED PRICE etc., ( not FREE OF CHARGE). Guarantee record will be recorded for WAIVED record of non FREE_OF_CHARGE
         *  1. WAIVER  CREATE - CUE status should be in QUEUED status
         *  2. WAIVER  UPDATE - CUE status should be in QUEUED status
         *  3. WAIVER  VOID - CUE status should be in QUEUED status
         */
        if (cue != null) {
            if (cue.getBexuEventType().equals(STORAGE) || cue.getBexuEventType().equals(REEFER)) {
                if (cue.getBexuStatus().equals(QUEUED) || cue.getBexuStatus().equals(PARTIAL)) {
                    isStatusValid = Boolean.TRUE;   //ARGO-21264
                } else {
                    isStatusValid = Boolean.FALSE;
                }
            } else if ((GuaranteeTypeEnum.OAC.equals(inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_TYPE))) ||
                    (GuaranteeTypeEnum.CREDIT_PREAUTHORIZE.equals(inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_TYPE)))) {

                if (GNTE_ACTION.equals(CREATE)) {
                    if (!cue.getBexuStatus().equals(QUEUED)) {
                        isStatusValid = Boolean.FALSE;
                    }
                } else if (GNTE_ACTION.equals(UPDATE)) {
                    if (!cue.getBexuStatus().equals(GUARANTEED)) {
                        isStatusValid = Boolean.FALSE;
                    }
                } else if (GNTE_ACTION.equals(VOID)) {
                    if (!cue.getBexuStatus().equals(GUARANTEED)) {
                        isStatusValid = Boolean.FALSE;
                    }
                }
            } else if ((GuaranteeTypeEnum.WAIVER.equals(inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_TYPE))) &&
                    ((GuaranteeOverrideTypeEnum.FREE_NOCHARGE).equals(inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_TYPE)))) {
                if (GNTE_ACTION.equals(CREATE)) {
                    if (!cue.getBexuStatus().equals(QUEUED)) {
                        isStatusValid = Boolean.FALSE;
                    }
                } else if (GNTE_ACTION.equals(UPDATE)) {
                    if (!cue.getBexuStatus().equals(CANCELLED)) {
                        isStatusValid = Boolean.FALSE;
                    }
                } else if (GNTE_ACTION.equals(VOID)) {
                    if (!cue.getBexuStatus().equals(CANCELLED)) {
                        isStatusValid = Boolean.FALSE;
                    }
                }
            } else if ((GuaranteeTypeEnum.WAIVER.equals(inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_TYPE))) &&
                    (!(GuaranteeOverrideTypeEnum.FREE_NOCHARGE).equals(inEntity.getField(ArgoExtractField.GNTE_OVERRIDE_VALUE_TYPE)))) {
                //Waived for FIXED price or Discounted rate. Equivalent (matching) Guarantee record will be created that will set the CUE status
                if (GNTE_ACTION.equals(CREATE)) {
                    if (!cue.getBexuStatus().equals(QUEUED)) {
                        isStatusValid = Boolean.FALSE;
                    }
                } else if (GNTE_ACTION.equals(UPDATE)) {
                    if (!cue.getBexuStatus().equals(QUEUED)) {
                        isStatusValid = Boolean.FALSE;
                    }
                } else if (GNTE_ACTION.equals(VOID)) {
                    if (!cue.getBexuStatus().equals(QUEUED)) {
                        isStatusValid = Boolean.FALSE;
                    }
                }
            }
        }
        if (!isStatusValid) {
            BizViolation bv = BizViolation.create(ArgoPropertyKeys.ERROR_INVALID_CHARGEABLE_UNIT_EVENT_RECORD_STATUS, null, cue.toString(),
                    cue.getBexuStatus());
            registerError(bv.getLocalizedMessage().toString() + inEntity);
        }
    }

    private String getGnteAppliedToEventId(EEntityView inEntity) {
        String eventId = null;
        Long extractGkey = inEntity.getField(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY);
        ChargeableUnitEvent cue = (ChargeableUnitEvent) Roastery.getHibernateApi().load(ChargeableUnitEvent.class, extractGkey);
        if (cue != null) {
            eventId = cue.getBexuEventType();
        }
        return eventId;
    }

    /**
     * Parse the below specified XML, checks whether authorised to create Guarantee.
     * Throws error, if not allowed.
     */
    // 2010-05-18 psethuraman ARGO-24974: To check whether allowed to create guarantee for Customer Credit status, gnte payment type and gnte type.
    private void validToCreateGuarantee(EEntityView inEntity) throws BizViolation {

        XML_STRING = XmlUtil.XML_HEADER + GNTE_VALIDATTION_START_TAG + OAC_XML + CASH_XML + CHECK_XML + GNTE_VALIDATTION_END_TAG;

        ScopedBizUnit customer = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_CUSTOMER);
        PaymentTypeEnum paymentType = inEntity.getField(ArgoExtractField.GNTE_PAYMENT_TYPE);
        GuaranteeTypeEnum guaranteeType = inEntity.getField(ArgoExtractField.GNTE_GUARANTEE_TYPE);

        Element xmlRootElm;
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            InputStream is = new ByteArrayInputStream(XML_STRING.getBytes("UTF-8"));
            Document doc = builder.parse(is);
            xmlRootElm = doc.getDocumentElement();
        } catch (Exception e) {
            throw BizFailure.create("Unable to parse XML" + e);
        }

        BizViolation bv = null;

        if (customer != null) {
            CreditStatusEnum creditStatus = customer.getBzuCreditStatus();
            if (creditStatus != null) {

                /**
                 * xpath format : //cust-credit-status[@value='OAC']//gnte-payment-type[@value='CHECK']//gnte-type[@value='OAC']
                 * */
                String xPath = "//" + CUS_CREDIT_STATUS_TAG + "[@value='" + creditStatus.getKey() + "']//" + GNTE_PAYMENT_TYPE_TAG +
                        "[@value='" + paymentType.getKey() + "']//" + GNTE_TYPE_TAG + "[@value='" + guaranteeType.getKey() + "']";

                Element element = XmlUtil.getXmlElement(xmlRootElm, xPath);
                if (element != null && NO.equalsIgnoreCase(element.getAttribute(IS_ALLOWED_ATTR))) {
                    bv = BizViolation.create(ArgoPropertyKeys.BILLING_INVALID_CREDIT_PAYMENT_GUARANTEE_TYPE, bv,
                            getMessage(guaranteeType.getDescriptionPropertyKey()), getMessage(creditStatus.getDescriptionPropertyKey()),
                            getMessage(paymentType.getDescriptionPropertyKey()));
                    registerError(bv.getLocalizedMessage());
                }
            }
        }
    }

    // get the localised error message
    private String getMessage(PropertyKey inPropertyKey) {
        IMessageTranslatorProvider translatorProvider =
                (IMessageTranslatorProvider) PortalApplicationContext.getBean(IMessageTranslatorProvider.BEAN_ID);
        MessageTranslator translator = translatorProvider.getMessageTranslator(ContextHelper.getThreadUserContext().getUserLocale());
        return translator.getMessage(inPropertyKey);
    }

    // CUSTOMER SPECIFIC GUARANTEE VALIDATION, USER CAN CHANGE THE BELOW XML VALUE OF (OAC_XML, CASH_XML AND CHECK_XML) BASED ON THEIR REQUIREMENTS
    /* XML FORMAT
     *  <guarantee-validation-map>
     *    <cust-credit-status value="OAC">
     *      <gnte-payment-type value="CREDIT">
     *        <gnte-type value="OAC" is-allowed="NO"/>
     *        <gnte-type value="CREDIT_PREAUTHORIZE" is-allowed="YES"/>
     *        <gnte-type value="PRE_PAY" is-allowed="NO"/>
     *        <gnte-type value="PAID" is-allowed="YES"/>
     *      </gnte-payment-type>
     *      ..,
     *    </cust-credit-status>
     *    ..,
     *  </guarantee-validation-map>
     */

    // CUSTOMER SPECIFIC GUARANTEE VALIDATION, USER CAN CHANGE VALUE FOR "is-allowed" ATTRIBUTE WITH (yes/no), BASED ON THEIR REQUIREMENTS
    private static final String OAC_XML = "<cust-credit-status value=\"OAC\">\n" +
            "\n" +
            "        <gnte-payment-type value=\"CREDIT\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"YES\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"YES\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"CHECK\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"YES\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"YES\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"MONEY_ORDER\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"YES\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"YES\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"CASH\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"ON_ACCOUNT\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"YES\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"WIRE_TRANSFER\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"YES\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"FREE\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "    </cust-credit-status>";

    // CUSTOMER SPECIFIC GUARANTEE VALIDATION, USER CAN CHANGE VALUE FOR "is-allowed" ATTRIBUTE WITH (yes/no), BASED ON THEIR REQUIREMENTS
    private static final String CASH_XML = "<cust-credit-status value=\"CASH\">\n" +
            "\n" +
            "        <gnte-payment-type value=\"CREDIT\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"YES\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"CHECK\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"YES\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"MONEY_ORDER\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"YES\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"CASH\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"ON_ACCOUNT\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"WIRE_TRANSFER\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"FREE\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "    </cust-credit-status>";

    // CUSTOMER SPECIFIC GUARANTEE VALIDATION, USER CAN CHANGE VALUE FOR "is-allowed" ATTRIBUTE WITH (yes/no), BASED ON THEIR REQUIREMENTS
    private static final String CHECK_XML = "<cust-credit-status value=\"CHECK\">\n" +
            "\n" +
            "        <gnte-payment-type value=\"CREDIT\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"YES\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"YES\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"CHECK\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"YES\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"YES\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"MONEY_ORDER\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"YES\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"YES\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"CASH\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"ON_ACCOUNT\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"WIRE_TRANSFER\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"YES\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "        <gnte-payment-type value=\"FREE\">\n" +
            "            <gnte-type value=\"OAC\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"CREDIT_PREAUTHORIZE\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PRE_PAY\" is-allowed=\"NO\"/>\n" +
            "            <gnte-type value=\"PAID\" is-allowed=\"NO\"/>\n" +
            "        </gnte-payment-type>\n" +
            "\n" +
            "    </cust-credit-status>";

    //DO NOT CHANGE
    private static final String GNTE_VALIDATTION_START_TAG = "<guarantee-validation-map>";
    private static final String GNTE_VALIDATTION_END_TAG = "</guarantee-validation-map>";
    private static final String CUS_CREDIT_STATUS_TAG = "cust-credit-status";
    private static final String GNTE_PAYMENT_TYPE_TAG = "gnte-payment-type";
    private static final String GNTE_TYPE_TAG = "gnte-type";
    private static final String VALUE_ATTR = "value";
    private static final String IS_ALLOWED_ATTR = "is-allowed";
    private static String XML_STRING = "";
    private static final String YES = "YES";
    private static final String NO = "NO";

    public static final String STORAGE = "STORAGE";
    public static final String REEFER = "REEFER";
    public static final String ACTION = "action";
    public static final String CREATE = "CREATE";
    public static final String UPDATE = "UPDATE";
    public static final String VOID = "VOID";
    public static final String GUARANTEED = "GUARANTEED";
    public static final String CANCELLED = "CANCELLED";
    public static final String QUEUED = "QUEUED";
    public static final String PARTIAL = "PARTIAL";
    private static String GNTE_ACTION;
}
