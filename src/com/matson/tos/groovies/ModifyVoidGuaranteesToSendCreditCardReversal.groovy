
import org.apache.log4j.Logger
import com.navis.framework.portal.UserContext
import com.navis.argo.ContextHelper
import com.navis.argo.business.extract.Guarantee
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.api.GroovyApi
import com.navis.framework.esb.client.ESBClientHelper
import com.navis.framework.esb.server.FrameworkMessageQueues
import org.apache.commons.lang.StringUtils
import com.navis.payment.PaymentServiceRequest
import com.navis.payment.IPaymentServiceManager
import com.navis.framework.business.Roastery
import com.navis.framework.util.message.MessageCollectorFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.payment.IPaymentService

import com.navis.payment.PaymentBizMetafield

import com.navis.framework.util.BizViolation
import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Map
import java.util.List
import java.util.Date
import java.util.HashMap
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.payment.PaymentServiceResponse
import com.navis.framework.util.MetafieldUserMessageImp
import com.navis.framework.util.TransactionParms
import com.navis.argo.ArgoPropertyKeys

/**
 *  This is a Pre-Deployable Groovy Plug-in which Modify Void Guarantees To Send Credit Card Reversal
 *
 * @author <a href="mailto:kjeyapandian@navis.com">Kathiresan Jeyapandian</a> Nov 30, 2009 Time: 11:48:01 PM
 */
public class ModifyVoidGuaranteesToSendCreditCardReversal extends GroovyApi {

    public void execute(Map parameters) {

        logInfo("Start execution of Groovy Plug-in: ModifyVoidGuaranteesToSendCreditCardReversal");
        System.out.println("Start execution of Groovy Plug-in: ModifyVoidGuaranteesToSendCreditCardReversal");

        // Get the list of credit pre-authorized guarantees voided or expired
        List guarantees = Guarantee.getCreditPreAuthorizedGurantees(BillingExtractEntityEnum.INV, true);

        UserContext userContext = ContextHelper.getThreadUserContext();

        if(guarantees.size() == 0) {
            logInfo("No records matched for Groovy Plug-in ModifyVoidGuaranteesToSendCreditCardReversal to notify Voided Guarantee");
            System.out.println("No records matched for Groovy Plug-in ModifyVoidGuaranteesToSendCreditCardReversal to notify Voided Guarantee");
        }

        for (Object guaranteeObj: guarantees) {

            Guarantee guarantee = (Guarantee) guaranteeObj;

            logInfo("Guarantees made by Credit Card Pre-Authorization -  " + guarantee);
            System.out.println("Guarantees made by Credit Card Pre-Authorization -  " + guarantee);

            // collect data for email content
            String guaranteeId = guarantee.getGnteGuaranteeId();
            String unitId = guarantee.getGnteAppliedToNaturalKey();
            String eventId = guarantee.getGnteAppliedToEventId();
            Date gnteCreated = guarantee.getGnteCreated();
            String custReferenceId = guarantee.getGnteCustomerReferenceId();
            Double amount = guarantee.getGnteGuaranteeAmount();
            String notes = guarantee.getGnteNotes();
            String emailToId = guarantee.getGnteExternalEmailAddress();
            String authNbr = guarantee.getGnteAuthorizationNbr();

            Date voidOrExpiredDate = guarantee.getGnteVoidedOrExpiredDate();
            Date emailSentDate = guarantee.getGnteVoidedEmailSentDate();

            if(voidOrExpiredDate != null && emailSentDate != null) {

                MessageCollector errorMsgs = MessageCollectorFactory.createMessageCollector();
                IPaymentService paymentService = PPS_MGR.getService(userContext, errorMsgs);

                if(paymentService==null){
                    LOGGER.error("Unable to get Payment Service for User: " + userContext.getUserId());
                    //throw BizViolation.create(PaymentServicePropertyKeys.PPS__SERVICE_NOT_AVAILABLE, null);
                }

                //Set<MetafieldId> fieldsForAuthPayment = paymentService.getMetafieldsForTransaction(PaymentServiceTransactionTypeEnum.PMT_VOID);

                // Create payment fields map
                Map paymentFields = new HashMap();
                paymentFields.put(PaymentBizMetafield.PPS_CUSTOMER_REF_NUM, custReferenceId);
                paymentFields.put(PaymentBizMetafield.PPS_AMOUNT, amount);

                ChargeableUnitEvent cue = ChargeableUnitEvent.getCUErecordForGuarantee(guarantee);
                String invoiceId = cue.getBexuLastDraftInvNbr();

                String timeStamp = SDF.format(new Date(System.currentTimeMillis()));
                paymentFields.put(PaymentBizMetafield.PPS_INVOICE_ID, invoiceId);
                paymentFields.put(PaymentBizMetafield.PPS_CCH_NAME, guarantee.getGnteExternalContactName());
                paymentFields.put(PaymentBizMetafield.PPS_CCH_ADDR1, guarantee.getGnteExternalAddress1());
                paymentFields.put(PaymentBizMetafield.PPS_CCH_ADDR2, guarantee.getGnteExternalAddress2());
                paymentFields.put(PaymentBizMetafield.PPS_CCH_CITY, guarantee.getGnteExternalCity());
                paymentFields.put(PaymentBizMetafield.PPS_CCH_POSTAL_CODE, guarantee.getGnteExternalMailCode());
                paymentFields.put(PaymentBizMetafield.PPS_CCH_REGION, guarantee.getGnteExternalStateName());
                paymentFields.put(PaymentBizMetafield.PPS_CCH_COUNTRY_CODE, guarantee.getGnteExternalCountry().getCntryCode());
                paymentFields.put(PaymentBizMetafield.PPS_CCH_PHONE, guarantee.getGnteExternalTelephone());
                paymentFields.put(PaymentBizMetafield.PPS_CCH_EMAIL, guarantee.getGnteExternalEmailAddress());
                paymentFields.put(PaymentBizMetafield.PPS_CC_ACCOUNT_NUM, cue.getBexuRestowAccount());
                paymentFields.put(PaymentBizMetafield.PPS_CC_EXP_DATE, guarantee.getGnteVoidedOrExpiredDate());
                paymentFields.put(PaymentBizMetafield.PPS_CC_VERIFICATION_NUM, authNbr);

                PaymentServiceRequest authReq = new PaymentServiceRequest(userContext);
                authReq.setPmtFields(paymentFields);

                PaymentServiceResponse response = paymentService.voidPayment(authReq);

                if (!response.isSuccess()) {
                    prasePaymentServiceError(response);
                } else {
                    sendNotificationForGuaranteeVoidedOrExpired(guaranteeId, unitId, eventId, gnteCreated, custReferenceId, amount,
                            notes, emailToId, userContext, voidOrExpiredDate);
                }

            }

        }

        logInfo("End execution of Groovy Plug-in: ModifyVoidGuaranteesToSendCreditCardReversal");
        System.out.println("End execution of Groovy Plug-in: ModifyVoidGuaranteesToSendCreditCardReversal");

    }

    private void sendNotificationForGuaranteeVoidedOrExpired(String inGuaranteeId, String inUnitId,  String inEventId, Date inGnteCreated,
                                                             String inCustReferenceId, Double inAmount,
                                                             String inNotes, String inEmailToId,
                                                             UserContext inContext, Date inVoidedExpiredDate) {

        String subject = "Guarantee ID " + inGuaranteeId + "' for container #" + inUnitId + " voided at " + inVoidedExpiredDate;

        // Create email message body
        String msgBody = "\n Please note that the following guarantee voided at "+ inVoidedExpiredDate + "." +

                "\n\n Guarantee ID :  " + inGuaranteeId +
                "\n Guarantee Amount :  " + inAmount +
                "\n Guarantee Created Date  : " + inGnteCreated + "." +
                "\n Confirmation #  : " + inGuaranteeId +
                "\n Reference # : " + inCustReferenceId +
                "\n Remarks " + inNotes +
                "\n Unit ID " + inUnitId +
                "\n Event ID " + inEventId +

                "\n\n Guarantee Voided email sent to. "+ inEmailToId + " for Guarantee " + inGuaranteeId;

        logInfo("Message Body is - " + msgBody);
        System.out.println("Message Body is - " + msgBody);

        if (inEmailToId != null && !StringUtils.isEmpty(inEmailToId)) {
            try {
                ESBClientHelper.sendEmailAttachments(inContext, FrameworkMessageQueues.EMAIL_QUEUE, inEmailToId, "kjeyapandian@zebra.com", subject, msgBody,
                        null);
                logInfo("Guarantee Expired email sent to '" + inEmailToId + "' for 'Guarantee: " + inGuaranteeId + "' on " + inVoidedExpiredDate + ".");
                System.out.println("Guarantee Expired email sent to '" + inEmailToId + "' for 'Guarantee: " + inGuaranteeId + "' on " + inVoidedExpiredDate + ".");
            } catch (Exception inEx) {
                LOGGER.error("Exception Occurred in sendNotificationForGuaranteeVoidedOrExpired() due to "+ inEx);
                System.out.println("Exception Occurred in sendNotificationForGuaranteeVoidedOrExpired() due to " + inEx);
            }
        }

    }

    private static void prasePaymentServiceError(PaymentServiceResponse inResponse) throws BizViolation {
        if (inResponse.hasProcessorStatusMsgs()) {
            Map errorMap = inResponse.getProcessorStatusMsgs();
            if (errorMap != null && !errorMap.isEmpty()) {
                Set msgCodes = errorMap.keySet();
                for (Iterator it = msgCodes.iterator(); it.hasNext();) {
                    String code = (String) it.next();
                    String msg = (String) errorMap.get(code);
                    def list = [code, msg];
                    TransactionParms.getBoundParms().getMessageCollector()
                            .appendMessage(new MetafieldUserMessageImp(ArgoPropertyKeys.CREDIT_CARD_PROCESS_IS_FAILED, null,
                            list));
                }
                throw BizViolation
                        .create(ArgoPropertyKeys.ERROR_WHILE_RECORDING_GUARANTEE, null, null, null);
            }
        }
    }

    private static final Logger LOGGER = Logger.getLogger(ModifyVoidGuaranteesToSendCreditCardReversal.class);
    private static final DateFormat SDF = new SimpleDateFormat("yyyyMMdd'T'HHmmss");
    private static final IPaymentServiceManager PPS_MGR = (IPaymentServiceManager) Roastery.getBean(IPaymentServiceManager.BEAN_ID);

}
