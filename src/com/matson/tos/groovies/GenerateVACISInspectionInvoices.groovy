import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.BillingTransactionDocument
import com.navis.argo.BillingTransactionDocument.BillingTransaction
import com.navis.argo.BillingTransactionsDocument
import com.navis.argo.BillingTransactionsDocument.BillingTransactions
import com.navis.argo.BillingTransactionsDocument.Factory
import com.navis.argo.ContextHelper
import com.navis.argo.EdiInvoice
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.BillingExtractEntityEnum
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
import java.rmi.RemoteException
import javax.xml.rpc.ServiceException
import javax.xml.rpc.Stub
import org.apache.commons.lang.StringUtils
import org.apache.xmlbeans.XmlException
import org.jdom.Document
import org.jdom.Element
import org.jdom.Text
import com.navis.argo.business.atoms.GuaranteeTypeEnum

public class GenerateVACISInspectionInvoices extends GroovyApi {

    public void execute(Map parameters) {

        logInfo("Groovy : GenerateVACISInspectionInvoices starts to send email notification for VACIS Inspection events with generated invoices attached !");
        System.out.println("Groovy : GenerateVACISInspectionInvoices starts to send email notification for VACIS Inspection events with generated invoices attached !");

        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

        /**
         * User modifyable values
         */
        String eventType = "VACIS INSPECTION";
        String fromEmailId = "rthandavarayan@zebra.com";
        String invoiceTypeID = "VACIS EXAM";
        String currencyId = "USD";
        boolean detailedTracing = false;
        String effectiveDateType = "Use Tariff Effective on performed date";
        String unitId = "";
        String status = GUARANTEED;
        String ufvFlexDateFeildId1 = ArgoExtractField.BEXU_FLEX_DATE01.getFieldId();
        String ufvFlexDateFeildId2 = ArgoExtractField.BEXU_FLEX_DATE02.getFieldId();

        // ------------ Do not change anything below  -------------------
        String fcyId = "";
        Date effDate = null;
        String payeeCustomerId = null;
        String contractCustomerId = null;
        String guaranteeId = null;


        // get the chargeable unit events with eventType = VACIS_INSPECTION and status = GUARANTEED
        List unitEvents = getCUEsFor(eventType, status);

        if (unitEvents.isEmpty()) {
            println("Groovy : GenerateVACISInspectionInvoices : No chargeable events exists!")
            return;
        }

        for (ChargeableUnitEvent event: unitEvents) {
            // get the equipment/unit id from the event
            unitId = event.getBexuEqId();

            Guarantee guarantee = getGuarantee(event);
            if (guarantee != null) {
                try {

                    Element element = buildGetInvoiceByInvTypeIdForUnitElement(invoiceTypeID, guarantee.getGnteGuaranteeCustomer(), null,
                            currencyId, null, guarantee, event);
                    appendBillToAddress(element, guarantee);
                    appendFlexFieldDetails(element, guarantee);
                    EdiInvoice ediInvoice = getInvoiceByInvTypeIdForUnit(element);
                    if (ediInvoice != null) {
                        // Compose subject message
                        String subject = eventType + " Invoice for UNIT:" + unitId;

                        logInfo(subject + " generated sucessfully !");
                        System.out.println(subject + " generated sucessfully !");

                        try {
                            UnitStorageManager manager = (UnitStorageManager) Roastery.getBean(UnitStorageManager.BEAN_ID);
                            String invoiceReportUrlWithType = manager.getInvoiceReportUrl(ediInvoice.getGkey());

                            try {
                                String fileName = eventType + "InvoiceFor" + unitId;
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
                                        logInfo("Groovy : GenerateVACISInspectionInvoices " + subject + "' " + emailToId + "' on " + timeNow + ".");
                                        System.out.println("Groovy : GenerateVACISInspectionInvoices " + subject + "' " + emailToId + "' on " + timeNow + ".");
                                    } catch (Exception e) {
                                        LOGGER.error(e.getMessage());
                                    }
                                } else {
                                    // Log if no e-mail Id found in for guarantor
                                    logWarn(subject + "\n Details : " + msgBody);
                                    System.out.println(subject + "\n Details : " + msgBody);
                                }
                            } catch (Exception e) {
                                logWarn("Groovy : GenerateVACISInspectionInvoices: Invoice report preparation failed for Invoice Draft Nbr :" + ediInvoice.getDraftNumber() + " due to : " + e.toString());
                                System.out.println("Groovy : GenerateVACISInspectionInvoices: Invoice report preparation failed for Invoice Draft Nbr :" + ediInvoice.getDraftNumber() + " due to : " + e.toString());
                            }

                        } catch (Exception e) {
                            logWarn("Groovy : GenerateVACISInspectionInvoices: Invoice report creation failed for Draft Nbr :" + ediInvoice.getDraftNumber() + " due to : " + e.toString());
                            System.out.println("Groovy : GenerateVACISInspectionInvoices: Invoice report creation failed for Draft Nbr :" + ediInvoice.getDraftNumber() + " due to : " + e.toString());
                        }

                    }
                    // Update the status and draft id for the invoiced guaranteed record.
                    guarantee.setGnteInvDraftNbr(ediInvoice.getDraftNumber());
                    guarantee.setGnteInvoiceStatus(INVOICED);
                    Roastery.getHibernateApi().saveOrUpdate(guarantee);
                } catch (Exception e) {
                    logWarn("Groovy : GenerateVACISInspectionInvoices: Invoice Generation failed for event :" + event.toString() + " due to : " + e.toString());
                    System.out.println("Groovy : GenerateVACISInspectionInvoices: Invoice Generation failed for event :" + event.toString() + " due to : " + e.toString());
                }
            }
        }
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

            copyStream(buffInStream, outputStream);

            buffInStream.close();
            outputStream.close();

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

    private static List getCUEsFor(String inEventTypeId, String inStatus) {

        if (inEventTypeId == null || inStatus == null) {
            log("Groovy : GenerateVACISInspectionInvoices executed without EventType or Status.");
            return null;
        }

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, inEventTypeId)).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_STATUS, inStatus));


        return Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);


    }

    private Guarantee getGuarantee(ChargeableUnitEvent inEvent) {

        List gntTypeList = new ArrayList<String>();
        gntTypeList.add(GuaranteeTypeEnum.PRE_PAY.getKey());
        gntTypeList.add(GuaranteeTypeEnum.OAC.getKey());
        gntTypeList.add(GuaranteeTypeEnum.CREDIT_PREAUTHORIZE.getKey());
        String[] statuses = gntTypeList.toArray(new String[gntTypeList.size()]);

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.GUARANTEE).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY, inEvent.getServiceExtractGkey())).
                addDqPredicate(PredicateFactory.in(ArgoExtractField.GNTE_GUARANTEE_TYPE, gntTypeList)).
                addDqPredicate(PredicateFactory.isNull(ArgoExtractField.GNTE_VOIDED_EMAIL_SENT_DATE)).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_APPLIED_TO_CLASS, BillingExtractEntityEnum.INV));
        return (Guarantee) Roastery.getHibernateApi().getUniqueEntityByDomainQuery(dq);
    }


    private Element buildGetInvoiceByInvTypeIdForUnitElement(String invoiceTypeId, ScopedBizUnit inPayee,
                                                             ScopedBizUnit inContractCustomer, String inCurrencyId, Date inContractEffectiveDate,
                                                             Guarantee inGuarantee, ChargeableUnitEvent inEvent) {
        //build the request xml
        Element rootElem = new Element(BillingWsApiConsts.BILLING_ROOT, XmlUtil.ARGO_NAMESPACE);
        Element elem = new Element(BillingWsApiConsts.GENERATE_INVOICE_REQUEST, XmlUtil.ARGO_NAMESPACE);
        rootElem.addContent(elem);
        addChildTextElement(BillingWsApiConsts.INVOICE_TYPE_ID, invoiceTypeId, elem);
        addChildTextElement(BillingWsApiConsts.PAYEE_CUSTOMER_ID, inPayee.getBzuId(), elem);
        addChildTextElement(BillingWsApiConsts.PAYEE_CUSTOMER_BIZ_ROLE, inPayee.getBzuRole().getKey(), elem);
        String contractCustId = inContractCustomer != null ? inContractCustomer.getBzuId() : "";
        addChildTextElement(BillingWsApiConsts.CONTRACT_CUSTOMER_ID, contractCustId, elem);
        addChildTextElement(BillingWsApiConsts.CONTRACT_CUSTOMER_BIZ_ROLE, inContractCustomer != null ? inContractCustomer.getBzuRole().getKey() : null, elem);
        addChildTextElement(BillingWsApiConsts.CURRENCY_ID, inCurrencyId, elem);
        String effectiveDateStr = null;
        if (inContractEffectiveDate != null) {
            effectiveDateStr = BillingWsApiConsts.DATE_FORMAT.format(inContractEffectiveDate);
        }
        addChildTextElement(BillingWsApiConsts.CONTRACT_EFFECTIVE_DATE, effectiveDateStr, elem);
        addChildTextElement(BillingWsApiConsts.IS_INVOICE_FINAL, "False", elem);
        Element paramsElem = new Element(BillingWsApiConsts.INVOICE_PARAMETERS, XmlUtil.ARGO_NAMESPACE);
        Element paramElem = new Element(BillingWsApiConsts.INVOICE_PARAMETER, XmlUtil.ARGO_NAMESPACE);
        addChildTextElement(BillingWsApiConsts.EQUIPMENT_ID, inEvent.getBexuEqId(), paramElem);
        addChildTextElement(BillingWsApiConsts.GUARANTEE_ID, inGuarantee.getGnteGuaranteeId(), paramElem);
        addChildTextElement(BillingWsApiConsts.CUSTOMER_REFERENCE_ID, inGuarantee.getGnteCustomerReferenceId(), elem);
        paramsElem.addContent(paramElem);
        elem.addContent(paramsElem);
        return rootElem;
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

    public String getInvoiceReportUrl(Long inInvoiceGkey) {
        try {
            ArgoServicePort port = getWsStub();
            Element requestElem = new Element("reporting");
            Element getInvoiceElem = new Element("get-invoice-report");
            Element invoiceGkeyElem = new Element("invoice-gkey");
            invoiceGkeyElem.addContent(inInvoiceGkey.toString());
            getInvoiceElem.addContent(invoiceGkeyElem);
            requestElem.addContent(getInvoiceElem);

            //invoke webservice
            ScopeCoordinateIdsWsType scopeCoordinates = getScopeCoordenatesForWs();
            GenericInvokeResponseWsType invokeResponseWsType = port.genericInvoke(scopeCoordinates, XmlUtil.toString(requestElem, false));
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
            Document responseDoc = XmlUtil.parse(responseString);
            Element responseRoot = responseDoc.getRootElement();
            Element targetUrlChild = responseRoot.getChild("file-url");
            String targetUrlStr = targetUrlChild.getText().trim();
            return targetUrlStr;
        } catch (ServiceException e) {
            throw BizFailure.create(InventoryPropertyKeys.BILLING_WEBSERVICE_ERROR, e, null);
        } catch (java.rmi.RemoteException e) {
            throw BizFailure.create(InventoryPropertyKeys.BILLING_WEBSERVICE_ERROR, e, null);
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

    private void addChildTextElement(String inElementName, String inElementText, Element inParentElement) {
        Element childElement = new Element(inElementName, XmlUtil.ARGO_NAMESPACE);
        Text childText = new Text(inElementText);
        childElement.addContent(childText);
        inParentElement.addContent(childElement);
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

    void appendFlexFieldDetails(Element inRootElement, Guarantee inGuarantee) {

        Element element = inRootElement.getChild(BillingWsApiConsts.GENERATE_INVOICE_REQUEST, XmlUtil.ARGO_NAMESPACE);

        Element flexElement = new Element(BillingWsApiConsts.INVOICE_FLEX_FIELDS, XmlUtil.ARGO_NAMESPACE);
        String gnteId =   inGuarantee.getGnteGuaranteeId();
        String gnteType = inGuarantee.getGnteGuaranteeType().getKey();
        String paymentType = inGuarantee.getGntePaymentType().getKey()
        addChildTextElement(BillingWsApiConsts.FLEX_STRING09, gnteId + ";" +  gnteType + ";" + paymentType, flexElement);
        element.addContent(flexElement);
    }
    private static final String GUARANTEED = "GUARANTEED";
    private static final String INVOICED = "INVOICED";

}
