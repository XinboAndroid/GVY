import com.navis.argo.ContextHelper
import com.navis.argo.ArgoExtractField
import com.navis.argo.ArgoExtractEntity
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.GuaranteeTypeEnum
import com.navis.argo.business.extract.Guarantee
import com.navis.framework.business.Roastery
import com.navis.framework.esb.client.ESBClientHelper
import com.navis.framework.esb.server.FrameworkMessageQueues
import com.navis.framework.portal.UserContext
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Logger
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.api.GroovyApi
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.PredicateFactory
import com.navis.security.business.user.BaseUser

/**
 * This is a Pre-Deployable Groovy Plug-in to send mail for voided Guarantees/Waiver.
 *
 * @author <a href="mailto:psethuraman@navis.com">Prakash</a> Apr 20, 2010, 1:01:08 PM
 */
public class SendEmailForVoidedGuarantees extends GroovyApi {
    public void execute(Map parameters) {
        logInfo("Start execution of Groovy Plug-in: SendEmailForVoidedGuarantees");
        System.out.println("Start execution of Groovy Plug-in: SendEmailForVoidedGuarantees");

        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());
        List voidedGuarantees = voidedGuarantees();

        if (voidedGuarantees.isEmpty()) {
            logInfo("No Guarantee records matched for Groovy Plug-in: SendEmailForVoidedGuarantees");
            System.out.println("No Guarantee records matched for Groovy Plug-in: SendEmailForVoidedGuarantees");
        } else {

            for (Object object: voidedGuarantees) {
                Guarantee guarantee = (Guarantee) object;

                // Set voided/expired email sent date to timeNow
                guarantee.setGnteVoidedEmailSentDate(timeNow);

                // collect data for email content
                String guaranteeId = guarantee.getGnteGuaranteeId();
                String unitId = guarantee.getGnteAppliedToNaturalKey();
                String eventId = guarantee.getGnteAppliedToEventId();
                Date gnteCreated = guarantee.getGnteCreated();
                ScopedBizUnit guarantor = guarantee.getGnteGuaranteeCustomer();
                String guarantorId = guarantor != null ? guarantor.getBzuId() : null;
                Date startDay = guarantee.getGnteGuaranteeStartDay();
                Date endDay = guarantee.getGnteGuaranteeEndDay();
                String custReferenceId = guarantee.getGnteCustomerReferenceId();
                Double amount = guarantee.getGnteGuaranteeAmount();
                String notes = guarantee.getGnteNotes();

                // For voided guarantee - if GnteN4user id is not null then fetch the email id from Security - user
                // if there is no n4user for GnteN4user id then get the email id from guarantee itself (external users)
                String emailToId = null;
                if (guarantee.getGnteN4UserId() != null) {
                    BaseUser user = BaseUser.findBaseUser(guarantee.getGnteN4UserId());
                    if (user != null) {
                        if (user.getBuserEMail() != null) {
                            emailToId = user.getBuserEMail();
                        } else {
                            logInfo("SendEmailForVoidedGuarantees: No email Id for the SN4 user " + guarantee.getGnteN4UserId());
                            System.out.println("SendEmailForVoidedGuarantees: No email Id for the SN4 user " + guarantee.getGnteN4UserId());
                        }
                    } else {
                        logInfo("SendEmailForVoidedGuarantees: " + guarantee.getGnteN4UserId() + " is not an SN4 user.");
                        System.out.println("SendEmailForVoidedGuarantees: " + guarantee.getGnteN4UserId() + " is not an SN4 user.");

                        emailToId = guarantee.getGnteExternalEmailAddress();
                    }
                } else {
                    emailToId = guarantee.getGnteExternalEmailAddress();
                }

                String[] noAttachment = "";
                String guaranteeType = GUARANTEE;
                if (GuaranteeTypeEnum.WAIVER.equals(guarantee.getGnteGuaranteeType())) {
                    guaranteeType = WAIVER;
                }
                // Subject: Guarantee/Waiver Confirmation# 00000 for container # ABCD1234567 HAS EXPIRED as of (today�s date).
                String subject = guaranteeType +" Confirmation # (ID) " + guaranteeId + " for container # '" + unitId + "' HAS EXPIRED as of " + timeNow + ".";

                // Create email message body
                String msgBody = "\n Please note that the subject "+ guaranteeType + " has expired and will need to be reprocessed.\n " +
                        "\n Container                           : " + unitId +
                        "\n " + guaranteeType + " for                 : " + eventId +
                        "\n" + guaranteeType + " Created Date         : " + gnteCreated +
                        "\n Confirmation # (ID)                 : " + guaranteeId +
                        "\n Guarantor                           : " + guarantorId +
                        "\n Authorized By                       : " + guarantorId +
                        "\n " + guaranteeType + " Amount              : " + amount +
                        "\n Reference #(ID)                     : " + custReferenceId +
                        "\n Remarks                             : " + notes + "\n ";

                if (emailToId != null && !StringUtils.isEmpty(emailToId)) {
                    try {
                        ESBClientHelper.sendEmailAttachments(context, FrameworkMessageQueues.EMAIL_QUEUE, emailToId, "do-not-reply@maher.com", subject, msgBody,
                                noAttachment);
                        logInfo("SendEmailForVoidedGuarantees: Guarantee Voided email sent to 'Guarantee SN4 User Id's email address '" + emailToId + "' for '" + guaranteeId + "' on " + timeNow + "'.");
                        System.out.println("SendEmailForVoidedGuarantees: Guarantee Voided email sent to 'Guarantee SN4 User Id's email address '" + emailToId + "' for '" + guaranteeId + "' on " + timeNow + "'.");
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
        logInfo("End execution of Groovy Plug-in: SendEmailForVoidedGuarantees");
        System.out.println("End execution of Groovy Plug-in: SendEmailForVoidedGuarantees");
    }

    private List voidedGuarantees() {

        DomainQuery dq = QueryUtils.createDomainQuery(ArgoExtractEntity.GUARANTEE)
                .addDqPredicate(PredicateFactory.isNotNull(ArgoExtractField.GNTE_VOIDED_OR_EXPIRED_DATE))
                .addDqPredicate(PredicateFactory.isNull(ArgoExtractField.GNTE_VOIDED_EMAIL_SENT_DATE));

        return Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);;

    }
    private final String GUARANTEE = "GUARANTEE";
    private final String WAIVER = "WAIVER";
    private static final Logger LOGGER = Logger.getLogger(SendEmailForVoidedGuarantees.class);
}
