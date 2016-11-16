import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.ArgoPropertyKeys
import com.navis.argo.BillingTransactionDocument
import com.navis.argo.BillingTransactionsDocument
import com.navis.argo.ContextHelper
import com.navis.argo.EdiInvoice
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.atoms.GuaranteeTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
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
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.DateUtil
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

public class GvyGenerateExportRedeliveryInvoices extends GroovyApi {

    private static final String IS_INVOICE_FINAL = "False";
    private static final String FROM_EMAIL_ADDRESS = "test@zebra.com";
    private static final int NUMBER_OF_GRACE_DAYS = 14;

    public void execute(Map parameters) {

        logInfo("Groovy : GvyGenerateExportRedeliveryInvoices Generate invoices for Export re-deliveries");

        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = DateUtil.getTodaysDate(context.getTimeZone());
        long timeNowInMillis = timeNow.getTime();

        // For Invoice generation
        String invoiceTypeID = "EXPORT REDELIVERY";
        String currencyId = "USD";
        String action = "DRAFT";

        // For CUE selection
        String eventType = "STORAGE";
        String status = "QUEUED";
        String category = UnitCategoryEnum.EXPORT.getKey();
        String freightKind = FreightKindEnum.FCL.getKey();

        // get the chargeable unit events with eventType = STORAGE, status = QUEUED , category = EXPORT and freightkind = FCL
        List unitCUEs = getCUEsFor(status, category, eventType, freightKind);

        if (unitCUEs.isEmpty()) {
            log("Groovy : GenerateExportRedeliveryInvoices : No chargeable events exists!")
            return;
        }

        for (ChargeableUnitEvent cueToBeInvoiced: unitCUEs) {
            Date complexInTime;
            String equipmentId = cueToBeInvoiced.getBexuEqId();
            Date endTime = cueToBeInvoiced.getBexuEventEndTime();

            // get the equipment/unit id from the cueToBeInvoiced
            String unitId = cueToBeInvoiced.getBexuEqId();
            long gkey = cueToBeInvoiced.getBexuGkey()
            log("Groovy : GenerateExportRedeliveryInvoices : Found Chargeable Unit Event with gkey " + gkey + " for Equip id " + unitId);

            ChargeableUnitEvent cue = getCUEMatchingCriteria(status, eventType, equipmentId, endTime);

            // Case - 1
            //======
            //= If no record found for CUE - (2)
            //- CUE - (1) of Time out + 14 > = Today ? Generate Export Re-Delivery invoice for CUE - (1)
            Boolean generateExportReDeliveryInvoice = false;
            if (cue == null && cueToBeInvoiced.getBexuUfvTimeOut() != null) {
                Date dateAfterCueUfvTimeOutAndGraceDays = addDays(cueToBeInvoiced.getBexuUfvTimeOut(), NUMBER_OF_GRACE_DAYS, context.getTimeZone());
                if (dateAfterCueUfvTimeOutAndGraceDays.getTime() >= timeNowInMillis) {
                    generateExportReDeliveryInvoice = true;
                }
            }

            // Case - 2
            //======
            //= If record found for CUE - (2)
            //- (2A) Find the difference between CUE - (2) of In Time - CUE - (1) of Out Time
            //- If (2A) is > = 14 ? Generate Export Re-Delivery invoice for CUE - (1)

            if (cue != null) {
                long diffOfNewCueUfvTimeInAndOldCueUfvTimeOut = DateUtil.differenceInDays(cueToBeInvoiced.getBexuUfvTimeOut(),
                        cue.getBexuUfvTimeIn(), context.getTimeZone());   // or DateUtil.differenceInTruncatedDays

                if (diffOfNewCueUfvTimeInAndOldCueUfvTimeOut > 0) {
                    if (diffOfNewCueUfvTimeInAndOldCueUfvTimeOut >= NUMBER_OF_GRACE_DAYS) {
                        generateExportReDeliveryInvoice = true;
                    }

                    //Case - 3
                    //======
                    //= If (2A) is < 14 and If CUE - (2) FreightKind = EMTPY ? Generate Export Re-Delivery invoice for CUE - (1).
                    //Update CUE (1) bexuFlexLong5 = -99999998

                    if (diffOfNewCueUfvTimeInAndOldCueUfvTimeOut < NUMBER_OF_GRACE_DAYS && FreightKindEnum.MTY.getKey().equals(cue.getBexuFreightKind())) {
                        generateExportReDeliveryInvoice = true;
                    }
                }
            }
            // to avoid the reprocessing of CUE bexuFlexLong05 is updated with -99999998
            cueToBeInvoiced.setBexuFlexLong05(new Long(-99999998));
            HibernateApi.getInstance().save(cueToBeInvoiced);

            if (generateExportReDeliveryInvoice) {

                List guarantees = getGuarantees(cueToBeInvoiced);

                for (Guarantee guarantee: guarantees) {
                    try {
                        logInfo("Groovy : GenerateExportRedeliveryInvoices for ");

                        Element element = buildGetInvoiceByInvTypeIdForUnitElement(unitId, invoiceTypeID, guarantee.getGnteGuaranteeCustomer(), null,
                                currencyId, timeNow, endTime, cueToBeInvoiced.getBexuGkey(), guarantee.getGnteCustomerReferenceId());
                        appendFlexFieldDetails(element, guarantee);
                        EdiInvoice ediInvoice = getInvoiceByInvTypeIdForUnit(element);

                        if (ediInvoice != null) {
                            // get the ToEmailAddress
                            String emailToId = ediInvoice.getInvoiceAddress() != null ? ediInvoice.getInvoiceAddress().getEmailAddress() : null;
                            logInfo("Groovy : GenerateExportRedliveryInvoices for " + guarantee.getGnteGuaranteeCustomer().getBzuId() + " emailed to " + emailToId);

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

                                    String msgBody = "Please find " + subject + " attached.";

                                    if (emailToId != null && !StringUtils.isEmpty(emailToId)) {
                                        try {
                                            ESBClientHelper.sendEmailAttachments(context, FrameworkMessageQueues.EMAIL_QUEUE, emailToId,
                                                    FROM_EMAIL_ADDRESS, subject, msgBody, attachment);
                                            logInfo("Groovy : GenerateExportRedeliveryInvoices " + subject + "' " + emailToId + "' on " + timeNow + ".");
                                            System.out.println("Groovy : GenerateExportRedeliveryInvoices " + subject + "' " + emailToId + "' on " + timeNow + ".");
                                        } catch (Exception e) {
                                            LOGGER.error(e.getMessage());
                                        }
                                    } else {
                                        // Log if no e-mail Id found in for guarantor
                                        logWarn(subject + "\n Details : " + msgBody);
                                        System.out.println(subject + "\n Details : " + msgBody);
                                    }
                                } catch (Exception e) {
                                    logWarn("Groovy : GenerateExportRedeliveryInvoices: Invoice report preparation failed for Invoice Draft Nbr :" +
                                            ediInvoice.getDraftNumber() + " due to : " + e.toString());
                                    System.out.println("Groovy : GenerateExportRedeliveryInvoices: Invoice report preparation failed for Invoice Draft Nbr :" +
                                            ediInvoice.getDraftNumber() + " due to : " + e.toString());
                                }
                            } catch (Exception e) {
                                logWarn("Groovy : GenerateExportRedeliveryInvoices: Invoice report creation failed for Draft Nbr :" + ediInvoice.getDraftNumber() +
                                        " due to : " + e.toString());
                                System.out.println("Groovy : GenerateExportRedeliveryInvoices: Invoice report creation failed for Draft Nbr :" +
                                        ediInvoice.getDraftNumber() + " due to : " + e.toString());
                            }
                            // Update the status and draft id for the invoiced guaranteed record.
                            guarantee.setGnteInvDraftNbr(ediInvoice.getDraftNumber());
                            guarantee.setGnteInvoiceStatus(INVOICED);
                            Roastery.getHibernateApi().saveOrUpdate(guarantee);
                        }
                    } catch (Exception e) {
                        logWarn("Groovy : GenerateExportRedeliveryInvoices: Invoice Generation failed for cueToBeInvoiced :" + cueToBeInvoiced.toString() +
                                " due to : " + e.toString());
                        System.out.println("Invoice Generation failed for cueToBeInvoiced :" + cueToBeInvoiced.toString() + " due to : " + e.toString());
                    }
                }
            }
        }
    }

    private static List getCUEsFor(String inStatus, String inCategory, String inEventTypeId, String inFreightKind) {

        if (inEventTypeId == null || inStatus == null) {
            log("Groovy : GenerateExportRedliveryInvoices executed without EventType or Status.");
            return null;
        }

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, inEventTypeId)).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_STATUS, inStatus)).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_CATEGORY, inCategory)).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_FREIGHT_KIND, inFreightKind)).

                addDqPredicate(PredicateFactory.isNull(ArgoExtractField.BEXU_FLEX_LONG05)).
                addDqPredicate(PredicateFactory.isNotNull(ArgoExtractField.BEXU_DRAY_STATUS)).
                addDqPredicate(PredicateFactory.isNotNull(ArgoExtractField.BEXU_UFV_TIME_OUT));

        return Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
    }

//  Get another CUE - (2)
//i) Get another CUE record
//- Status = QUEUED
//- Equipment Id = Match with (1)
//- Unit - Ufv != (1)
//- EvenType = STORAGE
//- Start Time >= CUE (1) End Time

    private static ChargeableUnitEvent getCUEMatchingCriteria(String inStatus, String inEventTypeId, String inEquipmentId, Date inEndTime) {
        if (inEventTypeId == null || inStatus == null) {
            log("Groovy : GenerateExportRedliveryInvoices executed without EventType or Status.");
            return null;
        }
        ChargeableUnitEvent cue;

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.CHARGEABLE_UNIT_EVENT).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EVENT_TYPE, inEventTypeId)).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_STATUS, inStatus)).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.BEXU_EQ_ID, inEquipmentId)).
                addDqPredicate(PredicateFactory.ge(ArgoExtractField.BEXU_EVENT_START_TIME, inEndTime)).
                addDqOrdering(Ordering.desc(ArgoExtractField.BEXU_UFV_TIME_IN));

        List matchingCUEs = Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
        if (!matchingCUEs.isEmpty()) {
            cue = (ChargeableUnitEvent) matchingCUEs.get(0);
        }
        return cue;
    }

    private Date addDays(Date inDate, int inNumOfDays, TimeZone inTimeZone) {
        Calendar calendar = Calendar.getInstance(inTimeZone);

        calendar.setTime(inDate);
        int dayA = calendar.get(Calendar.DAY_OF_MONTH);
        calendar.set(Calendar.DAY_OF_MONTH, dayA + inNumOfDays);
        return calendar.getTime();
    }

    private List getGuarantees(ChargeableUnitEvent inEvent) {
        List gntTypeList = new ArrayList<String>();
        gntTypeList.add(GuaranteeTypeEnum.PRE_PAY.getKey());
        gntTypeList.add(GuaranteeTypeEnum.OAC.getKey());
        gntTypeList.add(GuaranteeTypeEnum.CREDIT_PREAUTHORIZE.getKey());

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.GUARANTEE).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_APPLIED_TO_PRIMARY_KEY, inEvent.getServiceExtractGkey())).
                addDqPredicate(PredicateFactory.in(ArgoExtractField.GNTE_GUARANTEE_TYPE, gntTypeList)).
                addDqPredicate(PredicateFactory.isNull(ArgoExtractField.GNTE_VOIDED_EMAIL_SENT_DATE)).
                addDqPredicate(PredicateFactory.eq(ArgoExtractField.GNTE_APPLIED_TO_CLASS, BillingExtractEntityEnum.INV));
        return Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
    }

    private Element buildGetInvoiceByInvTypeIdForUnitElement(String unitId, String invoiceTypeId, ScopedBizUnit inPayee,
                                                             ScopedBizUnit inContractCustomer, String inCurrencyId, Date inContractEffectiveDate,
                                                             Date inPaidThruDate, Long cueGkey, String inCustomerRefId) {
        //build the request xml
        Element rootElem = new Element(BillingWsApiConsts.BILLING_ROOT, XmlUtil.ARGO_NAMESPACE);
        Element elem = new Element(BillingWsApiConsts.GENERATE_INVOICE_REQUEST, XmlUtil.ARGO_NAMESPACE);
        rootElem.addContent(elem);
        addChildTextElement(BillingWsApiConsts.INVOICE_TYPE_ID, invoiceTypeId, elem);
        addChildTextElement(BillingWsApiConsts.PAYEE_CUSTOMER_ID, inPayee.getBzuId(), elem);
        addChildTextElement(BillingWsApiConsts.PAYEE_CUSTOMER_BIZ_ROLE, inPayee.getBzuRole().getKey(), elem);
        String contractCustId = inContractCustomer != null ? inContractCustomer.getBzuId() : "";
        addChildTextElement(BillingWsApiConsts.CONTRACT_CUSTOMER_ID, contractCustId, elem);
        addChildTextElement(BillingWsApiConsts.CONTRACT_CUSTOMER_BIZ_ROLE, inContractCustomer != null ? inContractCustomer.getBzuRole().getKey() : null,
                elem);
        addChildTextElement(BillingWsApiConsts.CURRENCY_ID, inCurrencyId, elem);
        //  Start ---------- SPECIAL childTextElement to process Guaranteed records for STORAGE and REEFER event
        //  Add another root element to indicate that it is initiated from groovy and the GUARANTEED records are to be invoiced.
        //  Otherwise N4 Billing skips the GURANTEED days.  Applicable for STORAGE and REEFER events of Guaranteed records.
        //  ********** DO NOT INCLUDE THIS ELEMENT WITHOUT KNOWING THE REASON ****************
        addChildTextElement(BillingWsApiConsts.IS_GUARANTEED_DAY_TOBE_INVOICED, "True", elem);
        //  End ---------- SPECIAL childTextElement to process Guaranteed records for STORAGE and REEFER event

        String effectiveDateStr = null;
        if (inContractEffectiveDate != null) {
            effectiveDateStr = BillingWsApiConsts.DATE_FORMAT.format(inContractEffectiveDate);
        }
        addChildTextElement(BillingWsApiConsts.CONTRACT_EFFECTIVE_DATE, effectiveDateStr, elem);
        addChildTextElement(BillingWsApiConsts.IS_INVOICE_FINAL, IS_INVOICE_FINAL, elem);
        addChildTextElement(BillingWsApiConsts.CUSTOMER_REFERENCE_ID, inCustomerRefId, elem);
        Element paramsElem = new Element(BillingWsApiConsts.INVOICE_PARAMETERS, XmlUtil.ARGO_NAMESPACE);
        Element paramElem = new Element(BillingWsApiConsts.INVOICE_PARAMETER, XmlUtil.ARGO_NAMESPACE);
//    addChildTextElement(BillingWsApiConsts.RULE_START_DAY, startDayStr, paramElem);
        addChildTextElement(BillingWsApiConsts.EQUIPMENT_ID, unitId, paramElem);
        String paidThruDayStr = null;
        if (inPaidThruDate != null) {
            paidThruDayStr = BillingWsApiConsts.DATE_FORMAT.format(inPaidThruDate);
        }
        addChildTextElement(BillingWsApiConsts.PAID_THRU_DAY, paidThruDayStr, paramElem);
        addChildTextElement(BillingWsApiConsts.CUE_GKEY, cueGkey.toString(), paramElem);
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
                LOGGER.error("Groovy : GenerateExportRedeliveryInvoices: Downloading Invoice failed due to :" + ioe.toString());
                System.out.println("Groovy : GenerateExportRedeliveryInvoices: Downloading Invoice failed due to :" + ioe.toString());
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
        String gnteId = inGuarantee.getGnteGuaranteeId();
        String gnteType = inGuarantee.getGnteGuaranteeType().getKey();
        String paymentType = inGuarantee.getGntePaymentType().getKey()
        addChildTextElement(BillingWsApiConsts.FLEX_STRING09, gnteId + ";" + gnteType + ";" + paymentType, flexElement);
        element.addContent(flexElement);
    }

    private static final String INVOICED = "INVOICED";
    private static final Logger LOGGER = Logger.getLogger(GvyGenerateExportRedeliveryInvoices.class);
}
