import com.navis.argo.business.reports.ReportDesign;
import net.sf.jasperreports.engine.*;
import net.sf.jasperreports.engine.data.JRMapCollectionDataSource;
import net.sf.jasperreports.engine.design.JasperDesign;
import net.sf.jasperreports.engine.export.*;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.StringUtils;
import com.navis.framework.email.*;
import com.navis.framework.business.Roastery;
import org.springframework.core.io.ByteArrayResource;
import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.ContextHelper;
import java.io.Serializable;
import com.navis.framework.util.scope.ScopeCoordinates;
import com.navis.framework.portal.context.UserContextUtils;
import com.navis.framework.portal.UserContext;
import com.navis.argo.business.atoms.ScopeEnum;

import com.navis.framework.persistence.*;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.ArgoReportsField;
import com.navis.argo.ContextHelper
import java.io.ByteArrayOutputStream;
import com.navis.argo.business.api.GroovyApi;


/*
* A1 01/08/2010  GR   Added Code to lookup Report Design for JMS Msg
                      Grouped common code together
* 08/16/11 2.1 Updated Email Method
* A1  GR   10/25/11  Removed Weblogic API
* A2  GR   11/10/11  TOS2.1 Get Environment Variable
*/

class ReportRunner extends GroovyInjectionBase {
    private static final String emailTo = "1aktosdevteam@matson.com";
    private static final String errorEmailTo = "1aktosdevteam@matson.com";
    private static final String emailFrom = "1aktosdevteam@matson.com";
    GroovyApi groovyApi = new GroovyApi();
    String userId = null;


    public byte[] generateReport(JRDataSource ds,  String reportDefinitionName) {
        return generateReport(list, reportDefinitionName);
    }


    public byte[] generateReport(JRDataSource ds, Map parameters, String reportDefinitionName) {
        String xml = null;
        //Created for JMS to Direclty Lookup Report Design and ByPasses N4 User Visibility Checks
        if("-jms-".equals(userId)){
            xml = findExistingDesignName(reportDefinitionName).getRepdesXmlContent();
        }else{
            xml =  ReportDesign.findReportDesign(reportDefinitionName,com.navis.argo.business.atoms.ScopeEnum.YARD).getRepdesXmlContent();
        }
        InputStream inputStream = new ByteArrayInputStream(xml.getBytes());
        return createJaperReport(inputStream, ds, parameters);
    }


    public byte[] createJaperReport(InputStream inputStream, JRDataSource ds, Map parameters ){

        JasperReport report = JasperCompileManager.compileReport(inputStream);
        JasperPrint print = JasperFillManager.fillReport(report, parameters, ds);
        ByteArrayOutputStream pdfByteArray = new ByteArrayOutputStream();
        JRPdfExporter exporterPDF = new JRPdfExporter();
        exporterPDF.setParameter(JRExporterParameter.JASPER_PRINT,print);
        exporterPDF.setParameter(JRExporterParameter.OUTPUT_STREAM,pdfByteArray);
        exporterPDF.exportReport();
        return pdfByteArray.toByteArray();
    }

    public void emailReport( JRDataSource ds , Map parameters, String reportDefinitionName, String emailTo, String subject, String body) {
        try{
            userId = ContextHelper.getThreadUserId()
            ByteArrayResource bar = new ByteArrayResource(generateReport(ds,parameters, reportDefinitionName));
            testComposeEmail(reportDefinitionName, emailTo, subject, body, bar);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void composeEmail(String reportDefinitionName, String emailTo, String subject, String body, ByteArrayResource barAttachment){
        EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
        msg.setTo(StringUtils.split(emailTo, ";,"));
        msg.setSubject(getEnvVersion()+subject);
        msg.setText(body);
        msg.setReplyTo(emailFrom);
        msg.setFrom(emailFrom);
        DefaultAttachment attach = new DefaultAttachment();
        attach.setAttachmentContents(barAttachment);
        attach.setAttachmentName(reportDefinitionName+".pdf");
        attach.setContentType("application/octet-stream");
        msg.addAttachment(attach);
        def  emailManager = Roastery.getBean("emailManager");
        EmailManager mng = new EmailManager();
        emailManager.sendEmail(msg);
    }

    public void testComposeEmail(String reportDefinitionName, String emailTo, String subject, String body, ByteArrayResource barAttachment){
        try{
            EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
            msg.setTo(StringUtils.split(emailTo, ";,"));
            msg.setSubject(getEnvVersion()+subject);
            msg.setText(body);
            msg.setReplyTo(emailFrom);
            msg.setFrom(emailFrom);
            DefaultAttachment attach = new DefaultAttachment();
            attach.setAttachmentContents(barAttachment);
            attach.setAttachmentName(reportDefinitionName+".pdf");
            attach.setContentType("application/octet-stream");
            msg.addAttachment(attach);
            def  emailManager = Roastery.getBean("emailManager");
            EmailManager mng = new EmailManager();
            emailManager.sendEmail(msg);
        }catch(Exception e){
            e.printStackTrace();
        }
    }



    //Method To Lookup Report Design
    public ReportDesign findExistingDesignName(String inRepdesName)
    {
        DomainQuery query = QueryUtils.createDomainQuery("ReportDesign").addDqPredicate(PredicateFactory.eq(ArgoReportsField.REPDES_NAME, inRepdesName));
        List repDes = Roastery.getHibernateApi().findEntitiesByDomainQuery(query);
        for (i in repDes) {
            ReportDesign oldReportDesign = (ReportDesign)i;
            return oldReportDesign
        }
    }

    public void testReport() {
        HashMap map = new HashMap();
        ArrayList list = new ArrayList();
        map.put("UnitNbr", "1");
        map.put("Destination", "test");
        map.put("DepartureOrderNo", "123");
        map.put("HazardItemUNNumber", "1921");
        map.put("HazardItemProperName", "Hazard");
        map.put("HazardItemProperName", "Hazard");
        map.put("GoodsShipperName", "shipper");
        map.put("HazardItemImdgClass", "1.1");
        list.add(map);
        map = new HashMap();
        map.put("UnitNbr", "2");
        map.put("Destination", "test 2");
        map.put("DepartureOrderNo", "999");
        map.put("HazardItemUNNumber", "1921");
        map.put("HazardItemProperName", "Hazard");
        map.put("HazardItemProperName", "Hazard");
        map.put("GoodsShipperName", "Steve co");
        map.put("HazardItemImdgClass", "1.2");
        list.add(map);
        HashMap parameters = new HashMap();
        parameters.put("NAME","Steve");
        JRDataSource ds = new JRMapCollectionDataSource(list);
        emailReport(ds, parameters, "BMR DCM TEST", emailTo, "Test Email DCM", "This is a test");
    }


    public  String getEnvVersion()  {
        String envType = groovyApi.getReferenceValue("ENV", "ENVIRONMENT", null, null, 1)
        if("PRODUCTION".equals(envType)){
            return "";
        }
        return envType+" ";
    }


    // the below code is to email excel report.

    public void emailExcelReport( JRDataSource ds , Map parameters, String reportDefinitionName, String emailTo, String subject, String body) {
        try{
            println("calling emailExcelReport");
            userId = ContextHelper.getThreadUserId()
            ByteArrayResource bar = new ByteArrayResource(generateExcelReport(ds,parameters, reportDefinitionName));
            testComposeXlEmail(reportDefinitionName, emailTo, subject, body, bar);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public byte[] generateExcelReport(JRDataSource ds, Map parameters, String reportDefinitionName) {

        println("calling generateExcelReport");
        String xml = null;
        //Created for JMS to Direclty Lookup Report Design and ByPasses N4 User Visibility Checks
        if("-jms-".equals(userId)){
            xml = findExistingDesignName(reportDefinitionName).getRepdesXmlContent();
        }else{
            xml =  ReportDesign.findReportDesign(reportDefinitionName,com.navis.argo.business.atoms.ScopeEnum.YARD).getRepdesXmlContent();
        }
        InputStream inputStream = new ByteArrayInputStream(xml.getBytes());
        return createExcelReport(inputStream, ds, parameters);
    }

    public byte[] createExcelReport(InputStream inputStream, JRDataSource ds, Map parameters ){
        println("calling createExcelReport");
        JasperReport report = JasperCompileManager.compileReport(inputStream);
        JasperPrint jasperPrint = JasperFillManager.fillReport(report, parameters, ds);

        println("before instantiating JRXlsExporter");
        JRXlsExporter exporter = new JRXlsExporter();
        println("after instantiating JRXlsExporter");

        ByteArrayOutputStream pdfByteArray = new ByteArrayOutputStream();

        Map imagesMap = new HashMap();
        exporter.setParameter(JRExporterParameter.JASPER_PRINT, jasperPrint);
        exporter.setParameter(JRExporterParameter.OUTPUT_STREAM, pdfByteArray);
        exporter.setParameter(JRXlsExporterParameter.IS_ONE_PAGE_PER_SHEET, Boolean.FALSE);
        exporter.setParameter(JRXlsExporterParameter.IS_DETECT_CELL_TYPE, Boolean.TRUE);
        exporter.setParameter(JRXlsExporterParameter.IS_WHITE_PAGE_BACKGROUND, Boolean.FALSE);
        exporter.setParameter(JRXlsExporterParameter.IS_REMOVE_EMPTY_SPACE_BETWEEN_ROWS, Boolean.TRUE);
        exporter.exportReport();
        return pdfByteArray.toByteArray();
    }

    public byte[] generateCsvReport(JRDataSource ds, Map parameters, String reportDefinitionName) {

        println("calling generateCsvReport");
        String xml = null;
        //Created for JMS to Direclty Lookup Report Design and ByPasses N4 User Visibility Checks
        if("-jms-".equals(userId)){
            xml = findExistingDesignName(reportDefinitionName).getRepdesXmlContent();
        }else{
            xml =  ReportDesign.findReportDesign(reportDefinitionName,com.navis.argo.business.atoms.ScopeEnum.YARD).getRepdesXmlContent();
        }
        InputStream inputStream = new ByteArrayInputStream(xml.getBytes());
        return createCsvReport(inputStream, ds, parameters);
    }

    public byte[] createCsvReport(InputStream inputStream, JRDataSource ds, Map parameters ){
        println("calling createCsvReport");

        parameters.put(JRParameter.IS_IGNORE_PAGINATION, Boolean.TRUE);

        JasperReport report = JasperCompileManager.compileReport(inputStream);
        JasperPrint jasperPrint = JasperFillManager.fillReport(report, parameters, ds);

        println("before instantiating JRCsvExporter");
        JRCsvExporter exporterCsv = new JRCsvExporter();
        println("after instantiating JRCsvExporter");

        ByteArrayOutputStream output = new ByteArrayOutputStream();

        exporterCsv.setParameter(JRCsvExporterParameter.JASPER_PRINT, jasperPrint);
        exporterCsv.setParameter(JRCsvExporterParameter.OUTPUT_STREAM, output);
        exporterCsv.exportReport();

        return output.toByteArray();
    }

    public void testComposeXlEmail(String reportDefinitionName, String emailTo, String subject, String body, ByteArrayResource barAttachment){
        try{
            EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
            msg.setTo(StringUtils.split(emailTo, ";,"));
            msg.setSubject(getEnvVersion()+subject);
            msg.setText(body);
            msg.setReplyTo(emailFrom);
            msg.setFrom(emailFrom);
            DefaultAttachment attach = new DefaultAttachment();
            attach.setAttachmentContents(barAttachment);
            attach.setAttachmentName(reportDefinitionName+".xls");
            attach.setContentType("application/octet-stream");
            msg.addAttachment(attach);
            def  emailManager = Roastery.getBean("emailManager");
            EmailManager mng = new EmailManager();
            emailManager.sendEmail(msg);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    /*
        Created By : Raghu Iyer
        Created On : 10:01:2012
        Comments   : This method is used to send the multiple attachments with single mail
    */
    public void emailReports( Map resultMap ,Map parameters, String emailTo, String subject, String body) {
        try{
            println("Inside the email Class " + resultMap);
            String design = null;
            JRDataSource ds = null;

            EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
            msg.setTo(StringUtils.split(emailTo, ";,"));
            msg.setSubject(getEnvVersion()+subject);
            msg.setText(body);
            msg.setReplyTo(emailFrom);
            msg.setFrom(emailFrom);

            Iterator resultMapItr = resultMap.entrySet().iterator();
            while (resultMapItr.hasNext())
            {
                DefaultAttachment attach = new DefaultAttachment();

                Map.Entry reportMap = (Map.Entry) resultMapItr.next();
                design = reportMap.getKey();
                ds = reportMap.getValue();
                println("Design = " + design);
                println("Value = " + ds);

                ByteArrayResource bar = new ByteArrayResource(generateReport(ds,parameters,design));
                attach.setAttachmentContents(bar);
                attach.setAttachmentName(design+".pdf");
                attach.setContentType("application/octet-stream");
                msg.addAttachment(attach);

                println("Attachment Added");
            }
            def  emailManager = Roastery.getBean("emailManager");
            EmailManager mng = new EmailManager();
            emailManager.sendEmail(msg);

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void emailXLSReports( Map resultMap ,Map parameters, String emailTo, String subject, String body) {
        try{
            println("Inside the email Class " + resultMap);
            String design = null;
            JRDataSource ds = null;

            EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
            msg.setTo(StringUtils.split(emailTo, ";,"));
            msg.setSubject(getEnvVersion()+subject);
            msg.setText(body);
            msg.setReplyTo(emailFrom);
            msg.setFrom(emailFrom);

            Iterator resultMapItr = resultMap.entrySet().iterator();
            while (resultMapItr.hasNext())
            {
                DefaultAttachment attach = new DefaultAttachment();

                Map.Entry reportMap = (Map.Entry) resultMapItr.next();
                design = reportMap.getKey();
                ds = reportMap.getValue();
                println("Design = " + design);
                println("Value = " + ds);


                ByteArrayResource bar = new ByteArrayResource(generateExcelReport(ds,parameters,design));
                attach.setAttachmentContents(bar);
                attach.setAttachmentName(design+".XLS");
                attach.setContentType("application/octet-stream");
                msg.addAttachment(attach);

                println("Attachment Added");
            }
            def  emailManager = Roastery.getBean("emailManager");
            EmailManager mng = new EmailManager();
            emailManager.sendEmail(msg);

        }catch(Exception e){
            e.printStackTrace();
        }
    }


    /**
     * Author		: Karthik Rajendran
     * Description 	: This method is used to generate the PDF report as an email attachment.
     * Created 		: 7/30/2013
     *
     * @param designName
     * @param dataSource
     * @param parameters
     * @param attachFileName
     * @return
     */
    public DefaultAttachment generateReportAttachment(String designName, JRDataSource dataSource, Map parameters, String attachFileName)
    {
        DefaultAttachment attachment = null
        try {
            attachment = new DefaultAttachment()
            ByteArrayResource bar = new ByteArrayResource(generateReport(dataSource,parameters,designName))
            attachment.setAttachmentContents(bar)
            attachment.setAttachmentName(attachFileName+".pdf")
            attachment.setContentType("application/octet-stream")
        } catch(Exception e) {
            attachment = null
            e.printStackTrace()
        }
        //
        return attachment
    }
    /**
     * Author 		: Karthik Rajendran
     * Description 	: This method is used to send email with the provided email attachments.
     * Created 		: 7/30/2013
     *
     * @param attachments
     * @param fromId
     * @param toId
     * @param subject
     * @param message
     */
    public void emailReportAttachments(ArrayList<DefaultAttachment> attachments, String fromId, String toId, String subject, String message)
    {
        try {
            EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext())
            msg.setTo(StringUtils.split(toId, ";,"))
            msg.setSubject(getEnvVersion()+subject)
            msg.setText(message)
            msg.setReplyTo(fromId)
            msg.setFrom(fromId)
            if(attachments!=null && attachments.size()>0)
            {
                for(int i=0; i<attachments.size(); i++)
                {
                    msg.addAttachment(attachments.get(i))
                }
            }
            else
            {
                println("No attachments")
            }
            def  emailManager = Roastery.getBean("emailManager")
            EmailManager mng = new EmailManager()
            emailManager.sendEmail(msg)
        }catch(Exception e) {
            e.printStackTrace()
        }
    }


    /*
        Created By : Lisa Crouch
        Created On : 06:28:2013
        Comments   : This method is used to send the PDF/CSV attachments with single mail
    */
    public void emailCsvPDFReports(ArrayList reportUnitList, Map parameters,String design, String emailTo, String subject, String body) {
        try{
            println("Inside the email Class ");
            /*
            Need to create two datasources, one for each attachment
             */
            JRDataSource pdfDS = new JRMapCollectionDataSource(reportUnitList);
            JRDataSource excelDS = new JRMapCollectionDataSource(reportUnitList);


            EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
            msg.setTo(StringUtils.split(emailTo, ";,"));
            msg.setSubject(getEnvVersion()+subject);
            msg.setText(body);
            msg.setReplyTo(emailFrom);
            msg.setFrom(emailFrom);


            DefaultAttachment attach = new DefaultAttachment();
            ByteArrayResource bar = new ByteArrayResource(generateReport(pdfDS, parameters, design));
            attach.setAttachmentContents(bar);
            attach.setAttachmentName(design+".pdf");
            attach.setContentType("application/octet-stream");
            msg.addAttachment(attach);

            println("Attachment Added");


            DefaultAttachment attach2 = new DefaultAttachment();
            ByteArrayResource xlsbar = new ByteArrayResource(generateCsvReport(excelDS, parameters, design));
            attach2.setAttachmentContents(xlsbar);
            attach2.setAttachmentName(design+".csv");
            attach2.setContentType("application/octet-stream");
            msg.addAttachment(attach2);

            println("Attachment Added");

            def  emailManager = Roastery.getBean("emailManager");
            EmailManager mng = new EmailManager();
            emailManager.sendEmail(msg);

        }catch(Exception e){
            e.printStackTrace();
        }
    }

    // Added by Raghu Iyer on 10/10/2012 to emailReportWithoutAttachment sebd the report without attachment
    public void emailReportWithoutAttachment( JRDataSource ds , Map parameters, String reportDefinitionName, String emailTo, String subject, String body) {
        try{
            userId = ContextHelper.getThreadUserId()
            ByteArrayResource bar = new ByteArrayResource(generateReport(ds,parameters, reportDefinitionName));
            composeEmailWithoutAttachment(reportDefinitionName, emailTo, subject, body, bar);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void composeEmailWithoutAttachment(String reportDefinitionName, String emailTo, String subject, String body, ByteArrayResource barAttachment){
        try{
            EmailMessage msg = new EmailMessage(ContextHelper.getThreadUserContext());
            msg.setTo(StringUtils.split(emailTo, ";,"));
            msg.setSubject(getEnvVersion()+subject);
            msg.setText(body);
            msg.setReplyTo(emailFrom);
            msg.setFrom(emailFrom);
            def  emailManager = Roastery.getBean("emailManager");
            EmailManager mng = new EmailManager();
            emailManager.sendEmail(msg);
        }catch(Exception e){
            e.printStackTrace();
        }
    }

}