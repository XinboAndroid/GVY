import org.apache.log4j.Logger
import com.navis.framework.portal.UserContext
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.extract.Guarantee
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.api.GroovyApi
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.QueryUtils
import com.navis.argo.ArgoExtractEntity
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.business.Roastery
import com.navis.argo.ArgoExtractField
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.reference.ScopedBizUnit
import org.jdom.Element
import com.navis.argo.portal.BillingWsApiConsts
import com.navis.argo.util.XmlUtil
import org.jdom.Text
import com.navis.argo.EdiInvoice
import com.navis.framework.util.BizViolation
import com.navis.www.services.argoservice.ArgoServicePort
import javax.xml.rpc.ServiceException
import com.navis.www.services.argoservice.ArgoServiceLocator
import javax.xml.rpc.Stub
import com.navis.argo.business.extract.billing.ConfigurationProperties
import com.navis.argo.webservice.types.v1_0.ScopeCoordinateIdsWsType
import com.navis.argo.webservice.types.v1_0.GenericInvokeResponseWsType
import com.navis.argo.webservice.types.v1_0.ResponseType
import com.navis.argo.webservice.types.v1_0.QueryResultType
import com.navis.argo.webservice.types.v1_0.MessageType
import com.navis.argo.BillingTransactionsDocument
import com.navis.argo.BillingTransactionDocument
import com.navis.framework.util.BizFailure
import com.navis.inventory.InventoryPropertyKeys
import com.navis.argo.ArgoPropertyKeys
import com.navis.inventory.business.api.UnitStorageManager
import com.navis.argo.util.FileUtil
import org.apache.commons.lang.StringUtils
import com.navis.framework.esb.client.ESBClientHelper
import com.navis.framework.esb.server.FrameworkMessageQueues
import com.navis.argo.business.reference.ContactInfo

/**
 *  This is a Pre-Deployable Groovy Plug-in which generates Invoices for CUE in QUEUE, PARTIAL status.
 * @author <a href="mailto:psethuraman@zebra.com">Prakash</a> Jun 23, 2010 Time: 3:10:01 PM
 */
public class GenerateInvoicesForNotInvoicedCUE extends GroovyApi {
    public void execute(Map parameters) {

        logWarn("starts to generate invoices for not invoiced CUE");
        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

        String isInvoiceFinal = "True";
        String invoiceTypeId = "INVOICE TYPE";
        String currencyId = "USD";
        String[] statusses = ["QUEUED", "PARTIAL"];
        String emailFromAddress = "navis@zebra.com";

        List chargeEvents = getCUEs(statusses);

        if (chargeEvents.isEmpty()) {
            logWarn("No records matched for Groovy Plug-in");
        } else {
            for (ChargeableUnitEvent chargeEvent: chargeEvents) {
                String unitId = chargeEvent.getBexuEqId();
                ScopedBizUnit lineOperator = ScopedBizUnit.findScopedBizUnit(chargeEvent.getBexuLineOperatorId(),
                        com.navis.argo.business.atoms.BizRoleEnum.LINEOP);
                try {

                    Element element = buildGetInvoiceByInvTypeIdForUnitElement(unitId, invoiceTypeId, isInvoiceFinal, lineOperator,
                            null, currencyId, null);

                    appendBillToAddress(element, lineOperator.getBzuCtct());
                    EdiInvoice ediInvoice = getInvoiceByInvTypeIdForUnit(element);

                    if (ediInvoice != null) {
                        // Compose subject message as "Invoice for " Unit ID " and Payee ID "chargeEvent.LineOperator Id" created."
                        String subject = " Invoice for UNIT:" + unitId + " and Payee ID: " + chargeEvent.getBexuLineOperatorId();

                        logWarn(subject + " generated sucessfully !");

                        try {
                            UnitStorageManager manager = (UnitStorageManager) Roastery.getBean(UnitStorageManager.BEAN_ID);
                            String invoiceReportUrlWithType = manager.getInvoiceReportUrl(ediInvoice.getGkey());

                            try {
                                String fileName = "InvoiceFor" + unitId;
                                File reportTempFile = getFile(invoiceReportUrlWithType, fileName);

                                String[] attachment = new String[1];
                                String attachmentPrefix = "file:";
                                if (reportTempFile != null) {
                                    attachment[0] = attachmentPrefix + reportTempFile.getAbsolutePath();
                                } else {
                                    attachment[0] = "";
                                }

                                String emailToId = lineOperator.getBzuCtct().getCtctEmailAddress();
                                String msgBody = "Please find " + subject + " attached.";

                                if (emailToId != null && !StringUtils.isEmpty(emailToId)) {
                                    try {
                                        ESBClientHelper.sendEmailAttachments(context, FrameworkMessageQueues.EMAIL_QUEUE, emailToId, emailFromAddress, subject, msgBody, attachment);
                                        logWarn(subject + "' " + emailToId + "' on " + timeNow + ".");
                                    } catch (Exception e) {
                                        LOGGER.error(e.getMessage());
                                    }
                                } else {
                                    // Log if no e-mail Id found in for guarantor
                                    logWarn(subject + "\n Details : " + msgBody);
                                }
                            } catch (Exception e) {
                                logWarn("Invoice report preparation failed for Invoice Draft Nbr :" +
                                        ediInvoice.getDraftNumber() + " due to : " + e.toString());
                            }
                        } catch (Exception e) {
                            logWarn("Invoice report creation failed for Draft Nbr :" + ediInvoice.getDraftNumber() + " due to : " + e.toString());
                        }
                    }
                } catch (Exception e) {
                    logWarn("Invoice Generation failed for event :" + chargeEvent.toString() +
                            " due to : " + e.toString());
                }
            }
        }
    }

    private static List getCUEs(String[] inStatusses) {
        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT).
                addDqPredicate(PredicateFactory.in(ArgoExtractField.BEXU_STATUS, inStatusses));
        return (Roastery.getHibernateApi().findEntitiesByDomainQuery(dq));
    }

    private Element buildGetInvoiceByInvTypeIdForUnitElement(String unitId, String invoiceTypeId, String isInvoiceFinal, ScopedBizUnit inPayee,
                                                             ScopedBizUnit inContractCustomer, String inCurrencyId, Date inContractEffectiveDate) {
        //build the request xml
        Element rootElem = new Element(BillingWsApiConsts.BILLING_ROOT, XmlUtil.ARGO_NAMESPACE);
        Element elem = new Element(BillingWsApiConsts.GENERATE_INVOICE_REQUEST, XmlUtil.ARGO_NAMESPACE);
        rootElem.addContent(elem);
        addChildTextElement(BillingWsApiConsts.INVOICE_TYPE_ID, invoiceTypeId, elem);
        addChildTextElement(BillingWsApiConsts.PAYEE_CUSTOMER_ID, inPayee.getBzuId(), elem);
        addChildTextElement(BillingWsApiConsts.PAYEE_CUSTOMER_BIZ_ROLE, inPayee.getBzuRole().getKey(), elem);
        String contractCustId = inContractCustomer != null ? inContractCustomer.getBzuId() : "";
        addChildTextElement(BillingWsApiConsts.CONTRACT_CUSTOMER_ID, contractCustId, elem);
        addChildTextElement(BillingWsApiConsts.CONTRACT_CUSTOMER_BIZ_ROLE,
                inContractCustomer != null ? inContractCustomer.getBzuRole().getKey() : null, elem);
        addChildTextElement(BillingWsApiConsts.CURRENCY_ID, inCurrencyId, elem);
        String effectiveDateStr = null;
        if (inContractEffectiveDate != null) {
            effectiveDateStr = BillingWsApiConsts.DATE_FORMAT.format(inContractEffectiveDate);
        }
        addChildTextElement(BillingWsApiConsts.CONTRACT_EFFECTIVE_DATE, effectiveDateStr, elem);
        addChildTextElement(BillingWsApiConsts.IS_INVOICE_FINAL, isInvoiceFinal, elem);
        Element paramsElem = new Element(BillingWsApiConsts.INVOICE_PARAMETERS, XmlUtil.ARGO_NAMESPACE);
        Element paramElem = new Element(BillingWsApiConsts.INVOICE_PARAMETER, XmlUtil.ARGO_NAMESPACE);
        addChildTextElement(BillingWsApiConsts.EQUIPMENT_ID, unitId, paramElem);
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

    void appendBillToAddress(Element inRootElement, ContactInfo inContact) {

        Element element = inRootElement.getChild(BillingWsApiConsts.GENERATE_INVOICE_REQUEST, XmlUtil.ARGO_NAMESPACE);

        Element billToPartyElement = new Element(BillingWsApiConsts.BILL_TO_PARTY, XmlUtil.ARGO_NAMESPACE);
        Element addressElement = new Element(BillingWsApiConsts.ADDRESS, XmlUtil.ARGO_NAMESPACE);
        if (inContact != null) {
            addChildTextElement(BillingWsApiConsts.CONTACT_NAME, inContact.getCtctName(), addressElement);
            addChildTextElement(BillingWsApiConsts.ADDRESS_LINE_1, inContact.getCtctAddressLine1(), addressElement);
            addChildTextElement(BillingWsApiConsts.ADDRESS_LINE_2, inContact.getCtctAddressLine2(), addressElement);
            addChildTextElement(BillingWsApiConsts.ADDRESS_LINE_3, inContact.getCtctAddressLine3(), addressElement);
            addChildTextElement(BillingWsApiConsts.CITY, inContact.getCtctCity(), addressElement);
            addChildTextElement(BillingWsApiConsts.MAIL_CODE, inContact.getCtctMailCode(), addressElement);
            addChildTextElement(BillingWsApiConsts.STATE, inContact.getCtctState().getStateName(), addressElement);
            addChildTextElement(BillingWsApiConsts.COUNTRY, inContact.getCtctCountry().getCntryName(), addressElement);
            //todo: add telephone and fax details since they are not supported in billing invoices right now
            //    addChildTextElement(BillingWsApiConsts.TELEPHONE, inGuarantee.getGnteExternalTelephone(), addressElement);
            //    addChildTextElement(BillingWsApiConsts.FAX, inGuarantee.getGnteExternalFax(), addressElement);
            addChildTextElement(BillingWsApiConsts.EMAIL_ADDRESS, inContact.getCtctEmailAddress(), addressElement);
        }
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
                LOGGER.error("Groovy : GenerateInvoicesForNotInvoicedCUE: Downloading Invoice failed due to :" + ioe.toString());
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

    private static final Logger LOGGER = Logger.getLogger(GenerateInvoicesForNotInvoicedCUE.class);
}
