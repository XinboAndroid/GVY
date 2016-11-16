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
import org.jdom.Element
import com.navis.argo.EdiInvoice
import com.navis.inventory.business.api.UnitStorageManager
import com.navis.framework.esb.client.ESBClientHelper
import com.navis.framework.esb.server.FrameworkMessageQueues
import com.navis.argo.portal.BillingWsApiConsts
import com.navis.argo.util.XmlUtil
import org.jdom.Text
import org.apache.commons.lang.StringUtils
import com.navis.framework.util.BizViolation
import com.navis.www.services.argoservice.ArgoServicePort
import com.navis.argo.webservice.types.v1_0.ScopeCoordinateIdsWsType
import com.navis.argo.webservice.types.v1_0.GenericInvokeResponseWsType
import com.navis.argo.webservice.types.v1_0.ResponseType
import com.navis.argo.webservice.types.v1_0.QueryResultType
import com.navis.argo.webservice.types.v1_0.MessageType
import com.navis.framework.util.BizFailure
import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.BillingTransactionsDocument
import com.navis.argo.BillingTransactionDocument
import javax.xml.rpc.ServiceException
import com.navis.www.services.argoservice.ArgoServiceLocator
import javax.xml.rpc.Stub
import com.navis.inventory.InventoryPropertyKeys
import com.navis.argo.business.extract.billing.ConfigurationProperties
import com.navis.argo.util.FileUtil


/**
 * This is a Pre-Deployable Groovy Plug-in which generates Invoices for STORAGE_WAIVER events.
 * @author <a href="mailto:tramakrishnan@navis.com"> tramakrishnan</a> Dec 02, 2009 Time: 2:36:01 PM
 */
public class GenerateWaivedStorageInvoices extends GroovyApi {

    public void execute(Map parameters) {

        LOGGER.info("Groovy : GenerateWaivedStorageInvoices starts to send email notification for STORAGE_WAIVER events with generated invoices attached !");
        System.out.println("Groovy : GenerateWaivedStorageInvoices starts to send email notification for STORAGE_WAIVER events with generated invoices attached !");
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
            logInfo("No records matched for Groovy Plug-in: GenerateWaivedStorageInvoices");
            System.out.println("No records matched for Groovy Plug-in: GenerateWaivedStorageInvoices");
        } else {
            for (Guarantee guarantee: guarantees) {
                // for each guarantee create "STORAGE_WAIVER" CUE event
                ChargeableUnitEvent storageGnteEvent = createStorageWaiverEvent(guarantee, eventTypeIdToCreate);
                String unitId = storageGnteEvent.getBexuEqId();
                String gnteId = guarantee.getGnteGuaranteeId();
                Date paidThruDate = storageGnteEvent.getBexuEventEndTime();

                try {
                    Element element = buildGetInvoiceByInvTypeIdForUnitElement(unitId, invoiceTypeId, action, guarantee.getGnteGuaranteeCustomer(), null,
                            currencyId, timeNow, gnteId, paidThruDate, guarantee.getGnteCustomerReferenceId());
                    appendBillToAddress(element, guarantee);
                    appendFlexFieldDetails(element, guarantee);
                    EdiInvoice ediInvoice = getInvoiceByInvTypeIdForUnit(element);

                    if (ediInvoice != null) {
                        // Compose subject message as "Import Demurrage Invoice for " Guarantee.CUE.Unit ID " and Guarantee "Guarantee.Guarantee ID"created."
                        String subject = eventTypeIdToCreate + " Invoice for UNIT:" + unitId + " and Guarantee:" + gnteId + ".";

                        logInfo(subject + " generated sucessfully !");
                        System.out.println(subject + " generated sucessfully !");

                        try {
                            UnitStorageManager manager = (UnitStorageManager) Roastery.getBean(UnitStorageManager.BEAN_ID);
                            String invoiceReportUrlWithType = manager.getInvoiceReportUrl(ediInvoice.getGkey());

                            try {
                                String fileName = eventTypeIdToCreate + "InvoiceFor" + unitId;
                                File reportTempFile = getFile(invoiceReportUrlWithType, fileName);

                                String[] attachment = new String[1];
                                String attachmentPrefix = "file:";
                                if (reportTempFile != null) {
                                    attachment[0] = attachmentPrefix + reportTempFile.getAbsolutePath();
                                } else {
                                    attachment[0] = "";
                                }

                                String emailToId = ediInvoice.getInvoiceAddress() != null ? ediInvoice.getInvoiceAddress().getEmailAddress() : null;
                                String msgBody = "Please find " + subject + " attached.";

                                if (emailToId != null && !StringUtils.isEmpty(emailToId)) {
                                    try {
                                        ESBClientHelper.sendEmailAttachments(context, FrameworkMessageQueues.EMAIL_QUEUE, emailToId, "rthandavarayan@zebra.com", subject, msgBody, attachment);
                                        logInfo("Groovy : GenerateWaivedStorageInvoices " + subject + "' " + emailToId + "' on " + timeNow + ".");
                                        System.out.println("Groovy : GenerateWaivedStorageInvoices " + subject + "' " + emailToId + "' on " + timeNow + ".");
                                    } catch (Exception e) {
                                        LOGGER.error(e.getMessage());
                                    }
                                } else {
                                    // Log if no e-mail Id found in for guarantor
                                    logWarn(subject + "\n Details : " + msgBody);
                                    System.out.println(subject + "\n Details : " + msgBody);
                                }

                                // Cancel the actual event "STORAGE"
                                ChargeableUnitEvent actualStorageEvent = ChargeableUnitEvent.getCUErecordForGuarantee(guarantee);
                                actualStorageEvent.setBexuStatus("CANCELLED");
                                Roastery.getHibernateApi().saveOrUpdate(actualStorageEvent);

                                // Add log entry: "STORAGE event for " CUE.Unit ID " invoiced and cancelled"."
                                logInfo("Groovy : GenerateWaivedStorageInvoices: " + eventTypeId + " event for " + unitId + " invoiced and cancelled");
                                System.out.println("Groovy : GenerateWaivedStorageInvoices: " + eventTypeId + " event for " + unitId + " invoiced and cancelled");

                            } catch (Exception e) {
                                logWarn("Groovy : GenerateWaivedStorageInvoices: Invoice report preparation failed for Invoice Draft Nbr :" + ediInvoice.getDraftNumber() + " due to : " + e.toString());
                                System.out.println("Groovy : GenerateWaivedStorageInvoices: Invoice report preparation failed for Invoice Draft Nbr :" + ediInvoice.getDraftNumber() + " due to : " + e.toString());
                            }

                        } catch (Exception e) {
                            logWarn("Groovy : GenerateWaivedStorageInvoices: Invoice report creation failed for Draft Nbr :" + ediInvoice.getDraftNumber() + " due to : " + e.toString());
                            System.out.println("Groovy : GenerateWaivedStorageInvoices: Invoice report creation failed for Draft Nbr :" + ediInvoice.getDraftNumber() + " due to : " + e.toString());
                        }

                    }
                    // Update the status and draft id for the invoiced guaranteed record.
                    guarantee.setGnteInvDraftNbr(ediInvoice.getDraftNumber());
                    guarantee.setGnteInvoiceStatus(INVOICED);
                    Roastery.getHibernateApi().saveOrUpdate(guarantee);
                } catch (Exception e) {
                    logWarn("Groovy : GenerateWaivedStorageInvoices: Invoice Generation failed for event :" + storageGnteEvent.toString() + " due to : " + e.toString());
                    System.out.println("Groovy : GenerateWaivedStorageInvoices: Invoice Generation failed for event :" + storageGnteEvent.toString() + " due to : " + e.toString());
                }
            }
        }

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
        cue.applyFieldChanges(changes);
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


    private Element buildGetInvoiceByInvTypeIdForUnitElement(String inUnitId, String invoiceTypeId, String inAction, ScopedBizUnit inPayee,
                                                             ScopedBizUnit inContractCustomer, String inCurrencyId, Date inContractEffectiveDate,
                                                             String inGnteId, Date inPaidThruDate, String inGnteCustomerRefId) {
        //build the request xml
        Element rootElem = new Element(BillingWsApiConsts.BILLING_ROOT, XmlUtil.ARGO_NAMESPACE);
        Element elem = new Element(BillingWsApiConsts.GENERATE_INVOICE_REQUEST, XmlUtil.ARGO_NAMESPACE);
        rootElem.addContent(elem);
        addChildTextElement(BillingWsApiConsts.ACTION, inAction, elem);
        addChildTextElement(BillingWsApiConsts.INVOICE_TYPE_ID, invoiceTypeId, elem);
        addChildTextElement(BillingWsApiConsts.PAYEE_CUSTOMER_ID, inPayee.getBzuId(), elem);
        addChildTextElement(BillingWsApiConsts.PAYEE_CUSTOMER_BIZ_ROLE, inPayee.getBzuRole().getKey(), elem);
        String contractCustId = inContractCustomer != null ? inContractCustomer.getBzuId() : "";
        addChildTextElement(BillingWsApiConsts.CONTRACT_CUSTOMER_ID, contractCustId, elem);
        addChildTextElement(BillingWsApiConsts.CONTRACT_CUSTOMER_BIZ_ROLE, inContractCustomer != null ? inContractCustomer.getBzuRole().getKey() : null, elem);
        addChildTextElement(BillingWsApiConsts.CURRENCY_ID, inCurrencyId, elem);
        addChildTextElement(BillingWsApiConsts.CUSTOMER_REFERENCE_ID, inGnteCustomerRefId, elem);
        String effectiveDateStr = null;
        if (inContractEffectiveDate != null) {
            effectiveDateStr = BillingWsApiConsts.DATE_FORMAT.format(inContractEffectiveDate);
        }
        addChildTextElement(BillingWsApiConsts.CONTRACT_EFFECTIVE_DATE, effectiveDateStr, elem);
        addChildTextElement(BillingWsApiConsts.IS_INVOICE_FINAL, "False", elem);
        Element paramsElem = new Element(BillingWsApiConsts.INVOICE_PARAMETERS, XmlUtil.ARGO_NAMESPACE);
        Element paramElem = new Element(BillingWsApiConsts.INVOICE_PARAMETER, XmlUtil.ARGO_NAMESPACE);

        // invoice parameters
        addChildTextElement(BillingWsApiConsts.GUARANTEE_ID, inGnteId, paramElem);

        String paidThruDayStr = null;
        if (inPaidThruDate != null) {
            paidThruDayStr = BillingWsApiConsts.DATE_FORMAT.format(inPaidThruDate);

        }
        addChildTextElement(BillingWsApiConsts.PAID_THRU_DAY, paidThruDayStr, paramElem);
        addChildTextElement(BillingWsApiConsts.EQUIPMENT_ID, inUnitId, paramElem);
        paramsElem.addContent(paramElem);
        elem.addContent(paramsElem);
        return rootElem;
    }

    private void addChildTextElement(String inElementName, String inElementText, Element inParentElement) {
        Element childElement = new Element(inElementName, XmlUtil.ARGO_NAMESPACE);
        Text childText = new Text(inElementText);
        childElement.addContent(childText);
        inParentElement.addContent(childElement);
    }

    void appendBillToAddress(Element inRootElement, Guarantee inGuarantee) {
        Element element = inRootElement.getChild(BillingWsApiConsts.GENERATE_INVOICE_REQUEST, XmlUtil.ARGO_NAMESPACE);

        Element billToPartyElement = new Element(BillingWsApiConsts.BILL_TO_PARTY, XmlUtil.ARGO_NAMESPACE);
        Element addressElement = new Element(BillingWsApiConsts.ADDRESS, XmlUtil.ARGO_NAMESPACE);

        addChildTextElement(BillingWsApiConsts.CONTACT_NAME, inGuarantee.getGnteExternalContactName(), addressElement);
        addChildTextElement(BillingWsApiConsts.ADDRESS_LINE_1, inGuarantee.getGnteExternalAddress1(), addressElement);
        addChildTextElement(BillingWsApiConsts.ADDRESS_LINE_2, inGuarantee.getGnteExternalAddress2(), addressElement);
        addChildTextElement(BillingWsApiConsts.ADDRESS_LINE_3, inGuarantee.getGnteExternalAddress3(), addressElement);
        addChildTextElement(BillingWsApiConsts.CITY, inGuarantee.getGnteExternalCity(), addressElement);
        addChildTextElement(BillingWsApiConsts.MAIL_CODE, inGuarantee.getGnteExternalMailCode(), addressElement);
        addChildTextElement(BillingWsApiConsts.STATE, inGuarantee.getGnteExternalState() != null ? inGuarantee.getGnteExternalState().getStateCode() : null, addressElement);
        addChildTextElement(BillingWsApiConsts.COUNTRY, inGuarantee.getGnteExternalCountry() != null ? inGuarantee.getGnteExternalCountry().getCntryCode() : null, addressElement);
        //todo: add telephone and fax details since they are not supported in billing invoices right now
        //    addChildTextElement(BillingWsApiConsts.TELEPHONE, inGuarantee.getGnteExternalTelephone(), addressElement);
        //    addChildTextElement(BillingWsApiConsts.FAX, inGuarantee.getGnteExternalFax(), addressElement);
        addChildTextElement(BillingWsApiConsts.EMAIL_ADDRESS, inGuarantee.getGnteExternalEmailAddress(), addressElement);
        billToPartyElement.addContent(addressElement);
        element.addContent(billToPartyElement);
    }


    public EdiInvoice getInvoiceByInvTypeIdForUnit(Element inElement) throws BizViolation {

        try {
            ArgoServicePort port = getWsStub();
            ScopeCoordinateIdsWsType scopeCoordinates = getScopeCoordenatesForWs();
            GenericInvokeResponseWsType invokeResponseWsType = port.genericInvoke(scopeCoordinates, XmlUtil.toString(inElement, false));
            ResponseType response = invokeResponseWsType.getCommonResponse();
            QueryResultType[] queryResultTypes = response.getQueryResults();
            if (queryResultTypes == null || queryResultTypes.length != 1) {
                //todo: generic error
                if (response.getMessageCollector() != null && response.getMessageCollector().getMessages(0) != null) {
                    MessageType type = response.getMessageCollector().getMessages(0);
                    String message = type.getMessage();
                    throw BizFailure.create("Error from Billing Webservice - " + message);
                } else {
                    throw BizFailure.create(ArgoPropertyKeys.BILLING_WEBSERVICE_SERVICES_URL, null, null);
                }
            }
            String responseString = queryResultTypes[0].getResult();

            BillingTransactionsDocument billingTransactionsDocument = BillingTransactionsDocument.Factory.parse(responseString);
            BillingTransactionsDocument.BillingTransactions transactions = billingTransactionsDocument.getBillingTransactions();
            List<BillingTransactionDocument.BillingTransaction> transactionList = transactions.getBillingTransactionList();
            //todo: error if more than 1 transaction returned
            BillingTransactionDocument.BillingTransaction billingTransaction = transactionList.get(0);
            List<EdiInvoice> list = billingTransaction.getInvoiceList();
            if (list.isEmpty()) {
                throw BizFailure.create(InventoryPropertyKeys.NO_INVOICE_RETRIEVED, null, null);
            } else if (list.size() > 1) {
                throw BizFailure.create(InventoryPropertyKeys.MULTIPLE_INVOICES_RETURNED, null, list.size());
            }
            EdiInvoice ediInvoice = list.get(0);

            return ediInvoice;
        } catch (ServiceException e) {
            throw BizFailure.create(InventoryPropertyKeys.BILLING_WEBSERVICE_ERROR, e, null);
        } catch (java.rmi.RemoteException e) {
            throw BizFailure.create(InventoryPropertyKeys.BILLING_WEBSERVICE_ERROR, e, null);
        } catch (IOException e) {
            throw BizFailure.create(InventoryPropertyKeys.BILLING_WEBSERVICE_XML_ERROR, e, null);
        } catch (org.apache.xmlbeans.XmlException e) {
            throw BizFailure.create(InventoryPropertyKeys.BILLING_WEBSERVICE_XML_ERROR, e, null);
        }
    }

    private ArgoServicePort getWsStub() throws ServiceException {
        ArgoServiceLocator locator = new ArgoServiceLocator();
        ArgoServicePort port = locator.getArgoServicePort(ConfigurationProperties.getBillingServiceURL());
        Stub stub = (Stub) port;
        stub._setProperty(Stub.USERNAME_PROPERTY, ConfigurationProperties.getBillingWebServiceUserId());
        stub._setProperty(Stub.PASSWORD_PROPERTY, ConfigurationProperties.getBillingWebServicePassWord());
        return port;
    }

    private ScopeCoordinateIdsWsType getScopeCoordenatesForWs() {
        //build the scope coordinates for the web service based on the user context;
        ScopeCoordinateIdsWsType scopeCoordinates = new ScopeCoordinateIdsWsType();
        UserContext uContext = ContextHelper.getThreadUserContext();
        scopeCoordinates.setOperatorId(ContextHelper.getThreadOperator() != null ? ContextHelper.getThreadOperator().getId() : null);
        scopeCoordinates.setComplexId(ContextHelper.getThreadComplex() != null ? ContextHelper.getThreadComplex().getCpxId() : null);
        scopeCoordinates.setFacilityId(ContextHelper.getThreadFacility() != null ? ContextHelper.getThreadFacility().getFcyId() : null);
        scopeCoordinates.setYardId(ContextHelper.getThreadYard() != null ? ContextHelper.getThreadYard().getYrdId() : null);
        return scopeCoordinates;
    }

    private File getFile(String inDownloadUrl, String inFileName) {
        String[] fileAttributes = inDownloadUrl.split(":::");
        String invoiceReportUrl = fileAttributes[0];
        String mimeType = fileAttributes[1];

        if (invoiceReportUrl != null) {

            URL url = new URL(invoiceReportUrl);
            URLConnection connection = url.openConnection();
            connection.connect();
            InputStream urlInputStream = connection.getInputStream();

            String classPath = FileUtil.getClassPath();
            File classPathDirectory = new File(classPath);
            File file = new File(inFileName + mimeType, classPathDirectory);
            file.deleteOnExit();

            BufferedInputStream buffInStream = new BufferedInputStream(urlInputStream);
            BufferedOutputStream outputStream = new BufferedOutputStream(new FileOutputStream(file));
            try {
                copyStream(buffInStream, outputStream);
            } catch (IOException ioe) {
                LOGGER.error("Groovy : GenerateImportDemurrageInvoices: Downloading Invoice failed due to :" + ioe.toString());
                System.out.println("Groovy : GenerateImportDemurrageInvoices: Downloading Invoice failed due to :" + ioe.toString());
            } finally {
                buffInStream.close();
                outputStream.close();
            }
            return file;
        }
    }

    private void copyStream(InputStream inInputStream, OutputStream inOutputStream) throws IOException {
        byte[] buffer = new byte[256];
        while (true) {
            int bytesRead = inInputStream.read(buffer);
            if (bytesRead == -1) {
                break;
            }
            inOutputStream.write(buffer, 0, bytesRead);
        }
    }

    void appendFlexFieldDetails(Element inRootElement, Guarantee inGuarantee) {

        Element element = inRootElement.getChild(BillingWsApiConsts.GENERATE_INVOICE_REQUEST, XmlUtil.ARGO_NAMESPACE);

        Element flexElement = new Element(BillingWsApiConsts.INVOICE_FLEX_FIELDS, XmlUtil.ARGO_NAMESPACE);
        String gnteId =   inGuarantee.getGnteGuaranteeId();
        String gnteType = inGuarantee.getGnteGuaranteeType().getKey();
        String paymentType = inGuarantee.getGntePaymentType().getKey()
        addChildTextElement(BillingWsApiConsts.FLEX_STRING09, gnteId + ";" +  gnteType + ";" + paymentType, flexElement);
        element.addContent(flexElement);
    }
    private static final String INVOICED = "INVOICED";

    private static final String DRAFT = "DRAFT";
    private static final String QUEUED = "QUEUED";
    private static final String PARTIAL = "PARTIAL";
    private static final Logger LOGGER = Logger.getLogger(GenerateWaivedStorageInvoices.class);
}
