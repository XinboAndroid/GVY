import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.extract.Guarantee
import com.navis.framework.esb.client.ESBClientHelper
import com.navis.framework.esb.server.FrameworkMessageQueues
import com.navis.framework.portal.UserContext
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.inventory.business.units.GuaranteeManager
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.api.GroovyApi
import com.navis.security.business.user.BaseUser

/**
 * This is a Pre-Deployable Groovy Plug-in for Auto-voiding Expired Waivers exclude FREE_NOCHARGE.
 *
 * @author <a href="mailto:tramakrishnan@navis.com"> tramakrishnan</a> Jul 22, 2009, 1:01:08 PM
 */
public class AutoVoidingExpiredWaivers extends GroovyApi {
    public void execute(Map parameters) {
        logInfo("Start execution of Groovy Plug-in: AutoVoidingExpiredWaivers");
        System.out.println("Start execution of Groovy Plug-in: AutoVoidingExpiredWaivers");

        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());
        List waivers = Guarantee.getListOfExpiredFixedWaiverAndNotGuaranteed(timeNow, BillingExtractEntityEnum.INV);

        if (waivers.isEmpty()) {
            logInfo("No records matched for Groovy Plug-in: AutoVoidingExpiredWaivers");
            System.out.println("No records matched for Groovy Plug-in: AutoVoidingExpiredWaivers");
        } else {

            for (Object object: waivers) {
                Guarantee waiver = (Guarantee) object;

                // Set voided/expired date to timeNow
                waiver.setGnteVoidedOrExpiredDate(timeNow);
                //2010-04-20 psethuraman ARGO-25283 update email sent date.
                waiver.setGnteVoidedEmailSentDate(timeNow);
                ChargeableUnitEvent cue = ChargeableUnitEvent.getCUErecordForGuarantee(waiver);
                if (cue != null) {
                    // 1.  Set CUE.Is Override Amount = FALSE
                    cue.setBexuIsOverrideValue(Boolean.FALSE);
                    // 2. Set CUE.Override Type = NULL
                    cue.setBexuOverrideValueType(null);
                    // 3. Set CUE.Override Value = NULL
                    cue.setBexuOverrideValue(null);
                }

                // If (there is a Guarantee associated with the Waiver) and (Guarantee.Date/Time Voided = NULL) set Guarantee.Date/Time Voided = Now
                Guarantee associatedGnte = waiver.getGnteRelatedGuarantee();
                if (associatedGnte != null && associatedGnte.getGnteVoidedOrExpiredDate() == null) {
                    String associatedGnteId = associatedGnte.getGnteGuaranteeId();
                    logInfo("Voiding the related guarantee : " + associatedGnteId);
                    System.out.println("Voiding the related guarantee : " + associatedGnteId);

                    associatedGnte.setGnteVoidedOrExpiredDate(timeNow);
                    try {
                        // Void the associated Guarantee
                        GuaranteeManager.voidGuarantee(associatedGnte);
                    } catch (Exception e) {
                        LOGGER.error("Exeception Occured while voiding the related guarantee : " + associatedGnteId);
                        System.out.println("Exeception Occured while voiding the related guarantee : " + associatedGnteId);
                    }
                }

                // collect data for email content
                String waiverId = waiver.getGnteGuaranteeId();
                String unitId = waiver.getGnteAppliedToNaturalKey();
                String eventId = waiver.getGnteAppliedToEventId();
                Date gnteCreated = waiver.getGnteCreated();
                ScopedBizUnit guarantor = waiver.getGnteGuaranteeCustomer();
                String guarantorId = guarantor != null ? guarantor.getBzuId() : null;
                Date waiverExpirationDate = waiver.getGnteWaiverExpirationDate();
                Date startDay = waiver.getGnteGuaranteeStartDay();
                Date endDay = waiver.getGnteGuaranteeEndDay();
                String custReferenceId = waiver.getGnteCustomerReferenceId();
                Double amount = waiver.getGnteGuaranteeAmount();
                String notes = waiver.getGnteNotes();

                // For voided guarantee - if GnteN4user id is not null then fetch the email id from Security - user
                // if there is no n4user for GnteN4user id then get the email id from guarantee itself (external users)
                String emailToId = null;
                if (waiver.getGnteN4UserId() != null) {
                    BaseUser user = BaseUser.findBaseUser(waiver.getGnteN4UserId());
                    if (user != null) {
                        if (user.getBuserEMail() != null) {
                            emailToId = user.getBuserEMail();
                        } else {
                            logInfo("SendEmailForVoidedGuarantees: No email Id for the SN4 user " + waiver.getGnteN4UserId());
                            System.out.println("SendEmailForVoidedGuarantees: No email Id for the SN4 user " + waiver.getGnteN4UserId());
                        }
                    } else {
                        logInfo("SendEmailForVoidedGuarantees: " + waiver.getGnteN4UserId() + " is not an SN4 user.");
                        System.out.println("SendEmailForVoidedGuarantees: " + waiver.getGnteN4UserId() + " is not an SN4 user.");

                        emailToId = waiver.getGnteExternalEmailAddress();
                    }
                } else {
                    emailToId = waiver.getGnteExternalEmailAddress();
                }

                String[] noAttachment = "";

                // Subject: Waiver Confirmation# 00000 for container # ABCD1234567 HAS EXPIRED as of (today�s date).
                String subject = "Waiver Confirmation # (ID) " + waiverId + " for container # '" + unitId + "' HAS EXPIRED as of " + timeNow + ".";

                // Create email message body
                String msgBody = "\n Please note that the subject waiver has expired and will need to be reprocessed.\n " +
                        "\n Container               : " + unitId +
                        "\n Waiver for              : " + eventId +
                        "\n Waiver Created Date     : " + gnteCreated +
                        "\n Confirmation # (ID)     : " + waiverId +
                        "\n Guarantor               : " + guarantorId +
                        "\n Authorized By           : " + guarantorId +
                        "\n Waiver Amount           : " + amount +
                        "\n Waiver Expiration Date  : " + waiverExpirationDate +
                        "\n Reference #(ID)         : " + custReferenceId +
                        "\n Remarks                 : " + notes + "\n ";

                if (emailToId != null && !StringUtils.isEmpty(emailToId)) {
                    try {
                        ESBClientHelper.sendEmailAttachments(context, FrameworkMessageQueues.EMAIL_QUEUE, emailToId, "rthandavarayan@zebra.com", subject, msgBody,
                                noAttachment);
                        logInfo("Waiver Voided email sent to 'Waiver SN4 User Id's email address '" + emailToId + "' for '" + waiverId + "' on " + timeNow + "'.");
                        System.out.println("Waiver Voided email sent to 'Waiver SN4 User Id's email address '" + emailToId + "' for '" + waiverId + "' on " + timeNow + "'.");
                    } catch (Exception e) {
                        LOGGER.error(e.getMessage());
                        System.out.println(e.getMessage());
                    }
                } else {
                    // Log if no e-mail Id found in for guarantor
                    logWarn(subject + "\n Details : " + msgBody);
                    System.out.println(subject + "\n Details : " + msgBody);
                }
            }
        }
        logInfo("End execution of Groovy Plug-in: AutoVoidingExpiredWaivers");
        System.out.println("End execution of Groovy Plug-in: AutoVoidingExpiredWaivers");
    }

    private static final Logger LOGGER = Logger.getLogger(AutoVoidingExpiredWaivers.class);
}
