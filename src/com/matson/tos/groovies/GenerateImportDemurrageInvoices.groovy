import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.BillingTransactionDocument
import com.navis.argo.BillingTransactionsDocument
import com.navis.argo.ContextHelper
import com.navis.argo.EdiInvoice
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.GuaranteeTypeEnum
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.extract.Guarantee
import com.navis.argo.business.extract.billing.ConfigurationProperties
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.portal.BillingWsApiConsts
import com.navis.argo.util.FileUtil
import com.navis.argo.util.XmlUtil
import com.navis.argo.webservice.types.v1_0.GenericInvokeResponseWsType
import com.navis.argo.webservice.types.v1_0.MessageType
import com.navis.argo.webservice.types.v1_0.QueryResultType
import com.navis.argo.webservice.types.v1_0.ResponseType
import com.navis.argo.webservice.types.v1_0.ScopeCoordinateIdsWsType
import com.navis.framework.business.Roastery
import com.navis.framework.esb.client.ESBClientHelper
import com.navis.framework.esb.server.FrameworkMessageQueues
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.inventory.InventoryPropertyKeys
import com.navis.inventory.business.api.UnitStorageManager
import com.navis.www.services.argoservice.ArgoServiceLocator
import com.navis.www.services.argoservice.ArgoServicePort
import javax.xml.rpc.ServiceException
import javax.xml.rpc.Stub
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import org.jdom.Element
import org.jdom.Text

/**
 *  This is a Pre-Deployable Groovy Plug-in which generates Invoices for STORAGE events.
 *  Container delivered and Guaranteed STORAGE events are invoiced individually for each GUARANTEED record.
 *  The PAID - Gurantee record will be exlcuded
 *  The container is delivered but it doen't have guarantee record CUE - STATUS will be marked as CANCELLED. It is assumed that container is deliverd
 *  in the free period time.
 * @author <a href="mailto:tramakrishnan@navis.com"> tramakrishnan</a> Oct 14, 2009 Time: 5:48:01 PM (Major modification done by Murali Raghavachari)
 *
 * *****************************************************************************************************
 * Refer BILRM-47 1.2.2 Groovy Plug-in to Generate Import Demurrage Invoices
 * *****************************************************************************************************
 */
public class GenerateImportDemurrageInvoices extends GroovyApi {

    public void execute(Map parameters) {

        /**
         *   **** Please review and modify the following variables ****
         * 1) Comment out all the System.out.println lines before moving to production.
         * 2) Update the  invoiceTypeId as per your setup
         * 3) Do you want to finalize the invoice?   Update isInvoiceFinal as "False" or "True".
         * 4) Modify the currency id
         * 5) Modify From Email Id (Eg: rthandavarayan@zebra.com)
         */


        LOGGER.info("Groovy : GenerateImportDemurrageInvoices starts to send email notification for STORAGE events with generated invoices attached !");
        System.out.println("Groovy : GenerateImportDemurrageInvoices starts to send email notification for STORAGE events with generated invoices attached !");
        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

        /**
         * User modifyable values
         */
        String invoiceTypeId = "IMPORT DEMMURAGE INVOICE";
        String currencyId = "USD";
        String fromEmailId = "rthandavarayan@zebra.com";
        String isInvoiceFinal = "False";

        // ------------ Do not change anything below  -------------------
        String action = "DRAFT";
        String eventTypeId = "STORAGE";
        List statusList = new ArrayList<String>();
        statusList.add(QUEUED);
        statusList.add(PARTIAL);
        String[] statuses = statusList.toArray(new String[statusList.size()]);
        IS_INVOICE_FINAL = isInvoiceFinal;

        // Run a separte logic here to set CUE status as "CANCELLED" if the container is already delivered (End Date <> NULL) and it doesn't have
        // any guarantee record. It is assumed that container is deliverd in the free period time.
        // TODO: Yet to be coded.

        //For each "STORAGE" CUE with status in [DRAFT, QUEUED, PARTIAL] and (End Date <> NULL)
        List cues = getCueRecordHavingNonPaidGuarantees(eventTypeId, statuses);

        if (cues.isEmpty()) {
            logInfo("No records matched for Groovy Plug-in: GenerateImportDemurrageInvoices");
            System.out.println("No records matched for Groovy Plug-in: GenerateImportDemurrageInvoices");
        } else {
            for (ChargeableUnitEvent cue: cues) {
                // Get Guarantees
                // Each CUE STORAGE record may have more than one Guaranteed record. Get the list of Guarantees for each CUE gkey.
                List guarantees = getNonVoidedGuaranteesForCUE(cue);
                String unitId = cue.getBexuEqId();
                // Get the line operator from CUE to be used as Contract Customer. Refer: ARGO-26415.
                String lineId = cue.getBexuLineOperatorId();
                ScopedBizUnit lineOperator = ScopedBizUnit.findScopedBizUnit(lineId, BizRoleEnum.LINEOP);
                // for each guarantee record generate an invoice.
                for (Guarantee guarantee: guarantees) {
                    String gnteId = guarantee.getGnteGuaranteeId();
                    Date startDate = guarantee.getGnteGuaranteeStartDay();
                    Date paidThruDate = guarantee.getGnteGuaranteeEndDay();
                    try {
                        Element element = buildGetInvoiceByInvTypeIdForUnitElement(unitId, invoiceTypeId, action, guarantee.getGnteGuaranteeCustomer(), lineOperator,
                                currencyId, timeNow, gnteId, paidThruDate, startDate, guarantee.getGnteCustomerReferenceId());
                        appendBillToAddress(element, guarantee);
                        EdiInvoice ediInvoice = getInvoiceByInvTypeIdForUnit(element);

                        if (ediInvoice != null) {
                            // Compose subject message as "Import Demurrage Invoice for " Guarantee.CUE.Unit ID " and Guarantee "Guarantee.Guarantee ID"created."
                            String subject = " STOAGE Invoice Draft Id " + ediInvoice.getDraftNumber() + " for UNIT: " + unitId + " and Guarantee Id: " +
                                    gnteId + " is attached.";

                            logInfo("Generated sucessfully: - " + subject);
                            System.out.println("Generated sucessfully: - " + subject);

                            try {
                                UnitStorageManager manager = (UnitStorageManager) Roastery.getBean(UnitStorageManager.BEAN_ID);
                                String invoiceReportUrlWithType = manager.getInvoiceReportUrl(ediInvoice.getGkey());

                                try {
                                    String fileName = "StorgeInvoiceFor_" + unitId + "_" + guarantee.getGnteGuaranteeId();
                                    File reportTempFile = getFile(invoiceReportUrlWithType, fileName);

                                    String[] attachment = new String[1];
                                    String attachmentPrefix = "file:";
                                    if (reportTempFile != null) {
                                        attachment[0] = attachmentPrefix + reportTempFile.getAbsolutePath();
                                    } else {
                                        attachment[0] = "";
                                    }

                                    String emailToId = ediInvoice.getInvoiceAddress() != null ? ediInvoice.getInvoiceAddress().getEmailAddress() : null;

                                    String msgBody = "Please find " + subject;

                                    if (emailToId != null && !StringUtils.isEmpty(emailToId)) {
                                        try {
                                            ESBClientHelper.sendEmailAttachments(context, FrameworkMessageQueues.EMAIL_QUEUE, emailToId, fromEmailId,
                                                    subject, msgBody, attachment);

                                            // Update the status and draft id for the invoiced guaranteed record.
                                            guarantee.setGnteInvDraftNbr(ediInvoice.getDraftNumber());
                                            guarantee.setGnteInvoiceStatus(INVOICED);
                                            Roastery.getHibernateApi().saveOrUpdate(guarantee);

                                            logInfo("Groovy : GenerateImportDemurrageInvoices " + subject + "' " + emailToId + "' on " + timeNow + ".");
                                            System.out.println("Groovy : GenerateImportDemurrageInvoices " + subject + "' " + emailToId + "' on " + timeNow + ".");
                                        } catch (Exception e) {
                                            LOGGER.error(e.getMessage());
                                        }
                                    } else {
                                        // Log if no e-mail Id found in for guarantor
                                        logWarn(subject + "\n Details : " + msgBody);
                                        System.out.println(subject + "\n Details : " + msgBody);
                                    }
                                    // Add log entry: "STORAGE event for " CUE.Unit ID " invoiced"."
                                    logInfo("Groovy : GenerateImportDemurrageInvoices: " + eventTypeId + " event for " + unitId + " invoiced");
                                    System.out.println("Groovy : GenerateImportDemurrageInvoices: " + eventTypeId + " event for " + unitId + " invoiced");

                                } catch (Exception e) {
                                    logWarn("Groovy : GenerateImportDemurrageInvoices: Invoice report preparation failed for Invoice Draft Nbr :" +
                                            ediInvoice.getDraftNumber() + " due to : " + e.toString());
                                    System.out.println("Groovy : GenerateImportDemurrageInvoices: Invoice report preparation failed for Invoice Draft Nbr :" +
                                            ediInvoice.getDraftNumber() + " due to : " + e.toString());
                                }

                            } catch (Exception e) {
                                logWarn("Groovy : GenerateImportDemurrageInvoices: Invoice report creation failed for Draft Nbr :" + ediInvoice.getDraftNumber() +
                                        " due to : " + e.toString());
                                System.out.println("Groovy : GenerateImportDemurrageInvoices: Invoice report creation failed for Draft Nbr :" +
                                        ediInvoice.getDraftNumber() + " due to : " + e.toString());
                            }

                        }
                    } catch (Exception e) {
                        logWarn("Groovy : GenerateImportDemurrageInvoices: Invoice Generation failed for event :" + eventTypeId + " due to : "
                                + e.toString());
                        System.out.println("Groovy : GenerateImportDemurrageInvoices: Invoice Generation failed for event :" + eventTypeId +
                                " due to : " + e.toString());
                    }
                }
                // Having invoiced all the guarantees associated to STORAGE - CUE, update the CUE status as INVOICED".
                cue.setBexuStatus(INVOICED);
                Roastery.getHibernateApi().saveOrUpdate(cue);
            }
        }
    }

    private static List getCueRecordHavingNonPaidGuarantees(String inEventTypeId, String[] inStatuses) {

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, inEventTypeId)).
                addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_STATUS, inStatuses)).
                addDqPredicate(PredicateFactory.isNotNull(ArgoExtractField.BEXU_EVENT_END_TIME));

        DomainQuery gteSubQuery = QueryUtils.createDomainQuery(ArgoExtractEntity.GUARANTEE).addDqField(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_APPLIED_TO_CLASS, BillingExtractEntityEnum.INV.getKey())).
                addDqPredicate(PredicateFactory.ne(ArgoExtractField.GNTE_GUARANTEE_TYPE, GuaranteeTypeEnum.PAID.getKey())).
                addDqPredicate(PredicateFactory.isNull(ArgoExtractField.GNTE_INVOICE_STATUS)).
                addDqPredicate(PredicateFactory.isNull(ArgoExtractField.GNTE_VOIDED_OR_EXPIRED_DATE));


        if (gteSubQuery != null) {
            dq.addDqPredicate(PredicateFactory.subQueryIn(gteSubQuery, ArgoExtractField.BEXU_GKEY));
        }

        return (Roastery.getHibernateApi().findEntitiesByDomainQuery(dq));
    }

    private static List getNonVoidedGuaranteesForCUE(ChargeableUnitEvent inCue) {

        // Select all the non voided Guarantees excluding PAID types
        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.GUARANTEE).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_APPLIED_TO_CLASS, BillingExtractEntityEnum.INV.getKey())).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY, inCue.getPrimaryKey())).
                addDqPredicate(PredicateFactory.ne(ArgoExtractField.GNTE_GUARANTEE_TYPE, GuaranteeTypeEnum.PAID.getKey())).
                addDqPredicate(PredicateFactory.isNull(ArgoExtractField.GNTE_VOIDED_OR_EXPIRED_DATE));

        return (Roastery.getHibernateApi().findEntitiesByDomainQuery(dq));
    }

    private Element buildGetInvoiceByInvTypeIdForUnitElement(String inUnitId, String invoiceTypeId, String inAction, ScopedBizUnit inPayee,
                                                             ScopedBizUnit inContractCustomer, String inCurrencyId, Date inContractEffectiveDate,
                                                             String inGnteId, Date inPaidThruDate, Date inStartDate, String inCustomerRefId) {
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
        //  Start ---------- SPECIAL childTextElement to process Guaranteed records for STORAGE and REEFER event
        //  Add another root element to indicate that it is initiated from groovy and the GUARANTEED records are to be invoiced.
        //  Otherwise N4 Billing skips the GURANTEED days.  Applicable for STORAGE and REEFER events of Guaranteed records.
        //  ********** DO NOT INCLUDE THIS ELEMENT WITHOUT KNOWING THE REASON ****************
        addChildTextElement(BillingWsApiConsts.IS_GUARANTEED_DAY_TOBE_INVOICED, "True", elem);
        //  End ---------- SPECIAL childTextElement to process Guaranteed records for STORAGE and REEFER event

        String effectiveDateStr = null;
        if (inContractEffectiveDate != null) {
            // in String XML_DATE_TIME_ZONE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss Z"
            effectiveDateStr = BillingWsApiConsts.XML_DATE_TIME_ZONE_FORMAT.format(inContractEffectiveDate);
        }
        addChildTextElement(BillingWsApiConsts.CONTRACT_EFFECTIVE_DATE, effectiveDateStr, elem);
        addChildTextElement(BillingWsApiConsts.IS_INVOICE_FINAL, IS_INVOICE_FINAL, elem);
        addChildTextElement(BillingWsApiConsts.CUSTOMER_REFERENCE_ID, inCustomerRefId, elem);
        Element paramsElem = new Element(BillingWsApiConsts.INVOICE_PARAMETERS, XmlUtil.ARGO_NAMESPACE);
        Element paramElem = new Element(BillingWsApiConsts.INVOICE_PARAMETER, XmlUtil.ARGO_NAMESPACE);

        // invoice parameters
        String paidThruDayStr = null;
        if (inPaidThruDate != null) {
            // in String XML_DATE_TIME_ZONE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss Z"
            paidThruDayStr = BillingWsApiConsts.XML_DATE_TIME_ZONE_FORMAT.format(inPaidThruDate);

        }
        addChildTextElement(BillingWsApiConsts.PAID_THRU_DAY, paidThruDayStr, paramElem);
        String startDayStr = null;
        if (inStartDate != null) {
            // in String XML_DATE_TIME_ZONE_FORMAT = "yyyy-MM-dd'T'HH:mm:ss Z"
            startDayStr = BillingWsApiConsts.XML_DATE_TIME_ZONE_FORMAT.format(inStartDate);

        }
        addChildTextElement(BillingWsApiConsts.RULE_START_DAY, startDayStr, paramElem);
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

    private static final String QUEUED = "QUEUED";
    private static final String PARTIAL = "PARTIAL";
    private static final String INVOICED = "INVOICED";
    private static String IS_INVOICE_FINAL;
    private static final Logger LOGGER = Logger.getLogger(GenerateImportDemurrageInvoices.class);
}
