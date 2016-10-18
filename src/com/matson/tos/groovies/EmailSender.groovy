/*
* Sr  doer  date          Change
* A1 GR    09/07/10   Added Method to specify parameter emailFrom
* A2 GR    08/15/11  Update method SendEmail to custsendEmail
* A3 GR    10/25/11  Removed Weblogic API
* A4 GR    11/10/11  TOS2.1 Get Environment Variable
*/
import com.navis.framework.email.*;
import com.navis.argo.ContextHelper;
import org.springframework.core.io.ByteArrayResource;
import com.navis.apex.business.model.GroovyInjectionBase;
import org.apache.commons.lang.StringUtils;
import com.navis.framework.business.Roastery;
import com.navis.argo.business.api.GroovyApi;

public class EmailSender extends GroovyInjectionBase
{

    private static String emailFrom = "1aktosdevteam@matson.com";
    GroovyApi groovyApi = new GroovyApi();


    public void custSendEmail(EmailMessage msg){
        def emailManager = Roastery.getBean("emailManager");
        EmailManager mng = new EmailManager();
        emailManager.sendEmail(msg);
    }

    public void custSendEmail(String emailTo, String subject, String body){
        EmailMessage msg = emailContext(emailTo, subject, body)
        custSendEmail(msg)
    }

    //A1
    public void custSendEmail(String emailFromAddr, String emailTo, String subject, String body){
        EmailMessage msg = emailContext(emailFromAddr,emailTo, subject, body)
        custSendEmail(msg)
    }

    //Method to Send Reports as PDF attachments
    public void custSendEmail(String emailTo, String subject, String body, ByteArrayResource barAttachment,String attachmentName){
        EmailMessage msg = emailContext(emailTo, subject, body)
        //Add Attachment
        DefaultAttachment attach = new DefaultAttachment();
        attach.setAttachmentContents(barAttachment);
        attach.setAttachmentName(attachmentName+".pdf");
        attach.setContentType("application/octet-stream");
        msg.addAttachment(attach);

        custSendEmail(msg)
    }

    //Method to Send Attacehments as Txt of Html File
    public void custSendEmail(String emailTo, String subject, String body, String inAttachmentContents,String inAttachmentName){
        EmailMessage msg = emailContext(emailTo, subject, body)
        //Add Attachment
        DefaultAttachment attach = DefaultAttachment(inAttachmentName, inAttachmentContents);
        attach.setAttachmentContents(attach.getAttachmentContents());
        attach.setAttachmentName(attach.getAttachmentName());
        attach.setContentType("text/html");
        msg.addAttachment(attach);

        custSendEmail(msg)
    }


    public EmailMessage emailContext(String emailTo, String subject, String body){
        EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
        msg.setTo(StringUtils.split(emailTo, ";,"));
        msg.setSubject(getEnvVersion()+subject);
        msg.setText(body);
        msg.setReplyTo(emailFrom);
        msg.setFrom(emailFrom);
        return msg;
    }


    //A1
    public EmailMessage emailContext(String emailFromAddr, String emailTo, String subject, String body){
        EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
        msg.setTo(StringUtils.split(emailTo, ";,"));
        msg.setSubject(getEnvVersion()+subject);
        msg.setText(body);
        msg.setReplyTo(emailFromAddr);
        msg.setFrom(emailFromAddr);
        return msg;
    }

    public  String getEnvVersion()  {
        String envType = groovyApi.getReferenceValue("ENV", "ENVIRONMENT", null, null, 1)
        if("PRODUCTION".equals(envType)){
            return "";
        }
        return envType+" ";
    }
}