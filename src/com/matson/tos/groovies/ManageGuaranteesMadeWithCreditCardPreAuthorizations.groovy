import org.apache.log4j.Logger
import com.navis.framework.portal.UserContext
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.extract.Guarantee
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.api.GroovyApi
import com.navis.framework.esb.client.ESBClientHelper
import com.navis.framework.esb.server.FrameworkMessageQueues
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.inventory.business.units.GuaranteeManager
import org.apache.commons.lang.StringUtils
import java.util.Map
import java.util.Date
import java.util.List
import java.util.Calendar

/**
 *  This is a Pre-Deployable Groovy Plug-in for Managing Guarantees Made With Credit Card Pre-Authorization.
 * @author <a href="mailto:kjeyapandian@navis.com">Kathiresan Jeyapandian</a> Nov 05, 2009 Time: 4:48:01 PM
 */
public class ManageGuaranteesMadeWithCreditCardPreAuthorizations  extends GroovyApi {


    public void execute(Map parameters) {

        logInfo("Start execution of Groovy Plug-in: ManageGuaranteesMadeWithCreditCardPreAuthorizations");
        System.out.println("Start execution of Groovy Plug-in: ManageGuaranteesMadeWithCreditCardPreAuthorizations");

        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

        // Get the list of credit pre-authorized guarantees not voided or expired
        List guarantees = Guarantee.getCreditPreAuthorizedGurantees(BillingExtractEntityEnum.INV, false);

        for (Object guaranteeObj: guarantees) {

            Guarantee guarantee = (Guarantee) guaranteeObj;

            logInfo("Guarantees made by Credit Card Pre-Authorization -  " + guarantee);
            System.out.println("Guarantees made by Credit Card Pre-Authorization -  " + guarantee);

            // collect data for email content
            String guaranteeId = guarantee.getGnteGuaranteeId();
            String unitId = guarantee.getGnteAppliedToNaturalKey();
            String eventId = guarantee.getGnteAppliedToEventId();
            Date gnteCreated = guarantee.getGnteCreated();
            ScopedBizUnit guarantor = guarantee.getGnteGuaranteeCustomer();
            String guarantorId = guarantor != null ? guarantor.getBzuId() : null;
            String authorizedBy = guarantee.getGnteN4UserId();
            Date waiverExpirationDate = guarantee.getGnteWaiverExpirationDate();
            Date endDay = guarantee.getGnteGuaranteeEndDay();
            String custReferenceId = guarantee.getGnteCustomerReferenceId();
            Double amount = guarantee.getGnteGuaranteeAmount();
            String notes = guarantee.getGnteNotes();
            String emailToId = guarantee.getGnteExternalEmailAddress();
            String authNbr = guarantee.getGnteAuthorizationNbr();

            Date currentDate = ArgoUtils.timeNow();

            // yet to expiration date
            Calendar cal1 = Calendar.getInstance();
            cal1.setTime(gnteCreated);
            cal1.add(Calendar.DATE, notifyGuaranteeYetToExpire); // Adding 6 days to start day date
            Date yetToExpirationDate = cal1.getTime();

            // expired date
            Calendar cal2 = Calendar.getInstance();
            cal2.setTime(gnteCreated);
            cal2.add(Calendar.DATE, notifyExpiredGuaranteeDays); // Adding 7 days to start day date
            Date expiredDate = cal2.getTime();

            logInfo("Guarantee created date " + gnteCreated);
            System.out.println("Guarantee created date  " + gnteCreated);

            logInfo("Notification Guarantee yet to expire date " + yetToExpirationDate);
            System.out.println("Notification Guarantee yet to expire date  " + yetToExpirationDate);

            logInfo("Notification Guarantee expired date " + expiredDate);
            System.out.println("Notification Guarantee expired date  " + expiredDate);


            if(currentDate.equals(expiredDate) || currentDate.after(expiredDate)) {
                // Guarantee Expired --> current time >= Guarantee.Date/Time of Guarantee + 7 days
                logInfo("Send Notification For Guarantee Expired - " + guarantee);
                System.out.println("Send Notification For Guarantee Expired - " + guarantee);

                guarantee.setGnteVoidedOrExpiredDate(timeNow);
                try {
                    // Void the associated Guarantee
                    GuaranteeManager.voidGuarantee(guarantee);
                } catch (Exception e) {
                    LOGGER.error("Exeception Occured while voiding the guarantee : "+guaranteeId);
                    System.out.println("Exeception Occured while voiding the guarantee : "+guaranteeId);
                }

                sendNotificationForGuaranteeExpired(guaranteeId, unitId, eventId, gnteCreated, guarantorId, endDay, custReferenceId,
                        amount, notes, emailToId, authNbr, context, timeNow, expiredDate, authorizedBy);

            } else if(currentDate.equals(yetToExpirationDate) || currentDate.after(yetToExpirationDate)) {
                // Guarantee yet to expire --> current time >= Guarantee.Date/Time of Guarantee + 6 days
                logInfo("Send Notification For Guarantee Yet To Expire - " + guarantee);
                System.out.println("Send Notification For Guarantee Yet To Expire - " + guarantee);

                sendNotificationForGuaranteeYetToExpire(guaranteeId, unitId, eventId, gnteCreated, guarantorId, endDay, custReferenceId,
                        amount, notes, emailToId, authNbr, currentDate, context, timeNow, yetToExpirationDate, authorizedBy);

            }

        }

        logInfo("End execution of Groovy Plug-in: ManageGuaranteesMadeWithCreditCardPreAuthorizations");
        System.out.println("End execution of Groovy Plug-in: ManageGuaranteesMadeWithCreditCardPreAuthorizations");
    }

    private void sendNotificationForGuaranteeYetToExpire(String inGuaranteeId, String inUnitId,  String inEventId, Date inGnteCreated,
                                                         String inGuarantorId, Date inEndDay, String inCustReferenceId, Double inAmount,
                                                         String inNotes, String inEmailToId, String inAuthNbr,
                                                         Date inExpirationDate, UserContext inContext, Date inTimeNow, Date inYetToExpireDate,
                                                         String inAuthorizedBy) {


        Date tommorrowsDate = getTommorrowsDate();

        String subject = "Guarantee Confirmation #'" + inGuaranteeId + "' for container #" + inUnitId + " WILL EXPIRE on " + tommorrowsDate;

        // Create email message body
        String msgBody = "\n Please note that the subject credit card guarantee will expire on  " + tommorrowsDate + "."+
                "\n If the container is not picked up prior to close of business tomorrow, pre-approval will be voided and "+
                "\n you will need to re-process your payment." +
                "\n\n Container :  " + inUnitId +
                "\n Payment For :  " + inEventId +
                "\n Guarantee Created Date  : " + inGnteCreated + "." +
                "\n Confirmation #  : " + inGuaranteeId +
                "\n\n Guarantor : " + inGuarantorId +
                "\n Authorized By : " + inAuthorizedBy +
                "\n Authorized Amount : " + inAmount +
                "\n Authorized Until Date : " + inYetToExpireDate +
                "\n\n Order ID #  : " + inAuthNbr +
                "\n Reference # : " + inCustReferenceId +
                "\n Remarks : " + inNotes +

                "\n\n Note: Based on Mastercard and Visa rules, your pre-approval will void tomorrow (7 days rule)."+
                "\n Voided pre-approval may take between 7 to 12 days to be posted against your available credit line. "+
                "\n If you have any questions regarding this information, please contact the Demurrage Department at (908)436-4844. \n";

        logInfo("Message Body is - " + msgBody);
        System.out.println("Message Body is - " + msgBody);

        if (inEmailToId != null && !StringUtils.isEmpty(inEmailToId)) {
            try {
                ESBClientHelper.sendEmailAttachments(inContext, FrameworkMessageQueues.EMAIL_QUEUE, inEmailToId, "kjeyapandian@zebra.com", subject, msgBody,
                        null);
                logInfo("Guarantee Yet To Expire email sent to '" + inEmailToId + "' for 'Guarantee: " + inGuaranteeId + "' on " + inTimeNow + ".");
                System.out.println("Guarantee Yet To Expire email sent to '" + inEmailToId + "' for 'Guarantee: " + inGuaranteeId + "' on " + inTimeNow + ".");
            } catch (Exception inEx) {
                LOGGER.error("Exception Occurred in sendNotificationForGuaranteeYetToExpire() due to "+ inEx);
                System.out.println("Exception Occurred in sendNotificationForGuaranteeYetToExpire() due to " + inEx);
            }
        }

    }

    private void sendNotificationForGuaranteeExpired(String inGuaranteeId, String inUnitId,  String inEventId, Date inGnteCreated,
                                                     String inGuarantorId, Date inEndDay, String inCustReferenceId, Double inAmount,
                                                     String inNotes, String inEmailToId, String inAuthNbr,
                                                     UserContext inContext, Date inTimeNow, Date inExpiredDate, String inAuthorizedBy) {

        String subject = "Guarantee Confirmation #'" + inGuaranteeId + "' for container #" + inUnitId + " HAS EXPIRED as of " + inTimeNow;

        // Create email message body
        String msgBody = "\n Please note that the subject credit card guarantee has expired."+
                "\n Pre-approval has been voided and you will need to re-process your payment." +

                "\n\n Container :  " + inUnitId +
                "\n Payment For :  " + inEventId +
                "\n Guarantee Created Date  : " + inGnteCreated + "." +
                "\n Confirmation #  : " + inGuaranteeId +
                "\n\n Guarantor : " + inGuarantorId +
                "\n Authorized By : " + inAuthorizedBy +
                "\n Authorized Amount : " + inAmount +
                "\n Authorized Until Date : " + inExpiredDate + "." +
                "\n Order ID #  : " + inAuthNbr +
                "\n Reference # : " + inCustReferenceId +
                "\n Remarks " + inNotes +

                "\n\n Note: Based on Mastercard and Visa rules, your pre-approval has been voided (7 days rule)."+
                "\n Voided pre-approval may take between 7 to 12 days to be posted against your available credit line. "+
                "\n If you have any questions regarding this information, please contact the Demurrage Department at (908)436-4844.";


        logInfo("Message Body is - " + msgBody);
        System.out.println("Message Body is - " + msgBody);

        if (inEmailToId != null && !StringUtils.isEmpty(inEmailToId)) {
            try {
                ESBClientHelper.sendEmailAttachments(inContext, FrameworkMessageQueues.EMAIL_QUEUE, inEmailToId, "kjeyapandian@zebra.com", subject, msgBody,
                        null);
                logInfo("Guarantee Expired email sent to '" + inEmailToId + "' for 'Guarantee: " + inGuaranteeId + "' on " + inTimeNow + ".");
                System.out.println("Guarantee Expired email sent to '" + inEmailToId + "' for 'Guarantee: " + inGuaranteeId + "' on " + inTimeNow + ".");
            } catch (Exception inEx) {
                LOGGER.error("Exception Occurred in sendNotificationForGuaranteeExpired() due to "+ inEx);
                System.out.println("Exception Occurred in sendNotificationForGuaranteeExpired() due to " + inEx);
            }
        }

    }

    private Date getTommorrowsDate(){
        Date currentDate = ArgoUtils.timeNow();
        Calendar cal1 = Calendar.getInstance();
        cal1.setTime(currentDate);
        cal1.add(Calendar.DATE, +1); // Adding 1 day to current date to get Tommorrow's date
        Date tommorrowsDate = cal1.getTime();
        return tommorrowsDate;
    }

    private static final Logger LOGGER = Logger.getLogger(ManageGuaranteesMadeWithCreditCardPreAuthorizations.class);
    private static int notifyGuaranteeYetToExpire = 6;
    private static int notifyExpiredGuaranteeDays = 7;

}
