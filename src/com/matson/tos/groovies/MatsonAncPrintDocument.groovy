/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.DocTypeUsageEnum
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.util.PrintUtil
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.MailServerConfig
import com.navis.framework.business.atoms.PrinterDriverEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.printing.PrintRequest
import com.navis.framework.printing.PrintServiceManager
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.io.ResourceUtils
import com.navis.road.RoadEntity
import com.navis.road.RoadField
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.model.Document
import com.navis.road.business.model.GateLane
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.road.business.reference.Printer
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.springframework.core.io.Resource
import org.springframework.mail.MailSendException
import org.springframework.mail.javamail.JavaMailSenderImpl
import org.springframework.mail.javamail.MimeMessageHelper

import javax.mail.internet.MimeMessage
import java.nio.charset.Charset

import static com.navis.argo.business.reference.HardwareDevice.findHardwareDeviceById

/**
 * Print document.
 *
 * @author <a href="mailto:balamurugan.bakthavachalam@navis.com"> Balamurugan B</a> Date: 07/17/2015
 *
 * Date: 07/17/2015: 5:41 PM
 * JIRA: CSDV-3024
 * SFDC: 00138337
 * Called from: Gate Configuration
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 *
 * Peter Seiler
 * Date: 01/27/2016
 * JIRA: CSDV-3024
 *
 * Add logic to deal with Line Haul and selection of gate lane for printing.
 *
 * Peter Seiler
 * Date: 01/29/2016
 * JIRA: CSDV-3024
 *
 * Add check for truck vist vs. transaction.
 * To support reprinting in the lane allow call from truck visit
 *
 * Peter Seiler
 * Date: 02/04/2016
 *
 * chop the file up into one ticket per file when FTPing to Nascent server to print
 *
 * Peter Seiler
 * 02/05/2016
 *
 * Move the open and close FTP connection outside the loop that sends multiple tickets
 *
 * Peter Seiler
 * 02/16/2016
 *
 * Only execute trouble ticket ptinting if document type is 'TROUBLE'
 *
 * P DelaRosa
 * 05/23/2016
 *
 * Add email for Garage Foremen
 */
public class MatsonAncPrintDocument extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {

    /**
     * Print document based on the configuration docTypeId parameter
     *
     * @param inOutDao
     */
    public void execute(TransactionAndVisitHolder inOutDao) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncPrintDocument execute Stared.");
        def matsonAncValidateGateDocuments = getLibrary("MatsonAncValidateGateDocuments");
        LOGGER.info("MatsonAncCreateDocument about to execute MatsonAncValidateGateDocuments");
        if (!matsonAncValidateGateDocuments.isValidationSuccess(inOutDao)) {
            return;
        }
//        if (!inOutDao.hasTransaction()) {
//            LOGGER.error(" MatsonAncPrintDocument: No truck transaction found to print gate ticket(s).");
//            return;
//        }

        /* Get the truck visit and the lane from the truck visit */

        TruckVisitDetails ThisTruckVisit = inOutDao.getTv();

        GateLane lane = ThisTruckVisit.getTvdtlsExitLane();

        /* Get the trans action from the Visit Holder */

        TruckTransaction tran = inOutDao.getTran();

        if (tran == null) {

            /* if the transaction from the visit holder is null it is a reprint (which is from the truck visit) */

            for (TruckTransaction ThisTran : ThisTruckVisit.getTvdtlsTruckTrans()) {
                /* process the transactions on the truck visit */

                this.processOneTransaction(inOutDao, ThisTran, lane);
            }
        } else {
            /* print request is for one transaction from the transaction form */

            this.processOneTransaction(inOutDao, tran, lane);
        }

        LOGGER.info(" MatsonAncPrintDocument execute Completed.");
    }

    private void processOneTransaction(TransactionAndVisitHolder inOutDao, TruckTransaction inTran, GateLane inLane) {
//        if (RoadBizUtil.getMessageCollector().hasError()) {
//            LOGGER.error(" MatsonAncPrintDocument: Transaction has one or more errors. Cannot print tickets.");
//            return;
//        }

        GeneralReference ftpGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "FTP", "INFO", inLane.getLaneId());
        LOGGER.info("FTP for Lane id    : " + inLane.getLaneId());
        /* if there is no FTP destination found get a Direct print destination */

/*    if (ftpGeneralReference == null)
    {
      ftpGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "DIRECT PRINT", "INFO", inLane.getLaneId());
    }*/

        List<Document> docList = findDocByTransaction(inTran);
        if (docList == null) {
            LOGGER.error(" MatsonAncPrintDocument: No document found for transaction Nbr:" + inTran.getTranNbr());
            return;
        }
        def matsonAncFtpAdaptor = getLibrary("MatsonAncFtpAdaptor");
        boolean isFtpOpened = false;
        boolean retryConnect = false;
        try {
            if (ftpGeneralReference != null) {
                matsonAncFtpAdaptor.openConnection(ftpGeneralReference);
                isFtpOpened = true;
            }

            if (ftpGeneralReference == null) {
                ftpGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "DIRECT PRINT", "INFO", inLane.getLaneId());
            }
            for (Document doc : docList) {
                //com.navis.argo.business.api.GroovyApi.displayMessage("..." + new String(doc.transformData(PrinterDriverEnum.CUSTOM), "UTF-8"));
                saveDocument(doc, matsonAncFtpAdaptor, ftpGeneralReference, inTran, isFtpOpened);
            }
        }
        catch (BizFailure bizFailure) {
            LOGGER.error("FTP Connect Error", bizFailure);
            if (bizFailure.messageKey.key.equals(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE) &&
                    bizFailure.message.contains("FTP_DEFAULT_TIMEOUT")) {
                retryConnect = true
            };
        } finally {
            if (isFtpOpened) {
                matsonAncFtpAdaptor.closeConnection();
            } else if (!isFtpOpened && !retryConnect && ftpGeneralReference == null) {
                String msg = "Couldn't print the document, Please Print it manually";
                if (!"LINE HAUL".equalsIgnoreCase(inLane.getLaneId())) {
                    RoadBizUtil.appendExceptionChainAsWarnings(
                            BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, msg))
                };
            }
        }
        if (retryConnect) {
            try {
                /*
                * Retry again to open the FTP Connection if it's not open
                * */
                if (ftpGeneralReference != null && !isFtpOpened && retryConnect) {
                    matsonAncFtpAdaptor.openConnection(ftpGeneralReference);
                    isFtpOpened = true;
                }
                if (ftpGeneralReference == null) {
                    ftpGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "DIRECT PRINT", "INFO", inLane.getLaneId());
                }
                for (Document doc : docList) {
                    saveDocument(doc, matsonAncFtpAdaptor, ftpGeneralReference, inTran, isFtpOpened);
                }
            } catch (BizFailure bizFailure) {
                LOGGER.error("FTP Connect Error", bizFailure);
                println(bizFailure);
            }

            finally {
                if (isFtpOpened) {
                    matsonAncFtpAdaptor.closeConnection();
                } else if (!isFtpOpened && ftpGeneralReference == null) {
                    String msg = "Couldn't print the document, Please Print it manually";
                    if (!"LINE HAUL".equalsIgnoreCase(inLane.getLaneId())) {
                        RoadBizUtil.appendExceptionChainAsWarnings(
                                BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, msg))
                    };
                }
            }
        }
    }

    private static List<Document> findDocByTransaction(TruckTransaction inTran) {
        DomainQuery dq = QueryUtils.createDomainQuery(RoadEntity.DOCUMENT);
        dq.addDqPredicate(PredicateFactory.eq(RoadField.DOC_TRANSACTION, inTran.getTranGkey()));
        dq.addDqOrdering(Ordering.asc(RoadField.DOC_CREATED));
        return (List<Document>) HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    private void saveDocument(
            final Document inDocument, def inMatsonAncFtpAdaptor, GeneralReference inReference, TruckTransaction inTran,
            boolean inIsFtpOpened) {
        String docTypeId = inDocument.getDocDocType().getDoctypeId();
        if (inTran.getTranStatus() == TranStatusEnum.TROUBLE && inDocument.getDocDocType() != DocTypeUsageEnum.TROUBLE) {

            /* if the transaction is in trouble status and the document printed is not a TROUBLE document, exit without printing */

            return;
        }

        try {

            GeneralReference generalReferenceDamages = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "DAMAGES");
            boolean printDamage = inTran.getTranChsDmg() != null && inTran.getTranChsDmg().getDmgsItems() != null &&
                    !inTran.getTranChsDmg().getDmgsItems().isEmpty() && generalReferenceDamages.getRefValue1().equals(docTypeId);
            if (Boolean.TRUE.equals(printDamage) || TranStatusEnum.TROUBLE.equals(inTran.getTranStatus())) {
                printFile(inDocument, inTran, printDamage);
            } else {

                GateLane lane = inTran.getTranTruckVisit().getTvdtlsExitLane();
                if (lane == null) {
                    RoadBizUtil.
                            appendExceptionChain(
                                    BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, "Couldn't find Gate Entry Lane."));
                    return;
                }
                /* Check if lane is defined for direct print */

                if (inReference.getRefId1() == "DIRECT PRINT") {
                    /* if the lane is set for 'none' printer do not print anything */

                    if (inReference.getRefValue1() != "none") {
                        Printer thisPrinter = Printer.findHardwareDeviceById(inReference.getRefValue1()) as Printer;

                        /* send the document to the printer. */

                        //sendFileToPrinter(thisPrinter, inDocument, inTran, false);
                        /* send the document to the direct printer using FTP logic. */
                        LOGGER.info("Start Send Direct print sentToDirectPrinter");
                        sentToDirectPrinter(thisPrinter,inDocument,inTran,docTypeId);
                        LOGGER.info("End Send Direct print sentToDirectPrinter");
                    }
                } else {
                    String laneId = lane.getLaneId();
                    //def matsonAncFtpAdaptor = getLibrary("MatsonAncFtpAdaptor");

//          try {

//            matsonAncFtpAdaptor.openConnection(inReference);

                    byte[] printByte = inDocument.transformData(PrinterDriverEnum.CUSTOM);

                    int ScanPos = 0;

                    /* the indication of the end of a ticket is '<STX><ETB><ETX>' */

                    while (ScanPos < printByte.size()) {

                        /* scan through the ticket file looking for End Of Ticket indicators */

                        boolean endOfTicket = false;
                        def printTicketByte = new byte[printByte.size()];

                        int printTicketindex = 0;

                        /* ScanPos is the postion in the original byte array.  printTicketIndex is the postion in the new byte array
                           with one ticket to be printed
                         */

                        while (!endOfTicket && ScanPos < printByte.size()) {

                            /* check if a End Of Ticket indicator is found within the ticket byte array */

                            if (ScanPos + 15 < printByte.size()
                                    && (printByte[ScanPos] == '<' && printByte[ScanPos + 1] == 'S' && printByte[ScanPos + 2] == 'T'
                                    && printByte[ScanPos + 3] == 'X' && printByte[ScanPos + 4] == '>' && printByte[ScanPos + 5] == '<'
                                    && printByte[ScanPos + 6] == 'E' && printByte[ScanPos + 7] == 'T' && printByte[ScanPos + 8] == 'B'
                                    && printByte[ScanPos + 9] == '>' && printByte[ScanPos + 10] == '<' && printByte[ScanPos + 11] == 'E'
                                    && printByte[ScanPos + 12] == 'T' && printByte[ScanPos + 13] == 'X' && printByte[ScanPos + 14] == '>'))

                            {

                                /* an end was found. Cut the byte array at this point */

                                endOfTicket = true;

                                /* copy the end of ticket stream to the ticket file to print */

                                for (int i = 0; i < 15; i++) {
                                    printTicketByte[printTicketindex] = printByte[ScanPos];
                                    ScanPos++;
                                    printTicketindex++;
                                }
                            } else {

                                /* if not the end of the ticket copy the bytes over to the one ticket byte array */

                                printTicketByte[printTicketindex] = printByte[ScanPos];
                                ScanPos++;
                                printTicketindex++;
                            }

                            if (endOfTicket) {

                                /* process the one ticket */

                                String fileName = inTran.getTranNbr() + "_" + docTypeId + "_" + ArgoUtils.timeNowMillis() + ScanPos + ".txt";
                                LOGGER.info(" MatsonAncPrintDocument FTP Printing Ticket "+fileName);
                                InputStream is = new ByteArrayInputStream(Arrays.copyOf(printTicketByte, printTicketindex));

                                LOGGER.info("MatsonAncPrintDocument about to execute MatsonAncFtpAdaptor");
                                if (inIsFtpOpened) {
                                    inMatsonAncFtpAdaptor.sendDocument(fileName, is);
                                } else {
                                    LOGGER.info("MatsonAncPrintDocument,FTP connection not opened so couldn't put the print document in FTP server");
                                }
                            }
                        }
                    }
//          }
//          finally
//          {
//            matsonAncFtpAdaptor.closeConnection();
//          }
                }
            }
        } catch (BizViolation bizViolation) {
            RoadBizUtil.appendExceptionChain(bizViolation);
        }
    }

    private void printFile(Document inDocument, TruckTransaction inTran, boolean isDamage) {
        GeneralReference reference = null;
//    boolean isTrouble = TranStatusEnum.TROUBLE.equals(inOutDao.getTran().getTranStatus());
        if (isDamage) {
            reference = GeneralReference.findUniqueEntryById("MATSON", "PRINTER_NAME", "DAMAGES");
        } else {
            reference = GeneralReference.findUniqueEntryById("MATSON", "PRINTER_NAME", "TROUBLE");
        }
        Printer printer = findHardwareDeviceById(reference.getRefValue1()) as Printer;
        sendFileToPrinter(printer, inDocument, inTran, isDamage);
    }

    private void sendFileToPrinter(Printer printer, Document inDocument, TruckTransaction inTran, boolean isDamage) {

        byte[] data = inDocument.transformData(printer.getPrinterDriver());
        if (printer != null) {
            //email notification for chassis damage.
            if (isDamage) {
                GeneralReference emailIdGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "EMAIL", "SHOP");
                String fromEmailId = emailIdGeneralReference.getRefValue1();
                String toEmailId = emailIdGeneralReference.getRefValue2();
                String subject = "Damage Report for Chassis: " + inTran.getTranChsNbr();
                boolean result = sendEmail(toEmailId, fromEmailId, subject, new String(transformData(inDocument.getDocData()), "UTF-8"));
                printInShop(inDocument, inTran, isDamage);
                if (result) {
                    LOGGER.info("MatsonAncPrintDocument:Email notification has been sent to " + toEmailId);
                } else {
                    LOGGER.error("MatsonAncPrintDocument:Failed to send Email notification to " + toEmailId);
                }
            }
            PrintUtil.print(data, printer.getHwHostAddress(), printer.getPrtrQueueName(), 1);
        } else {
            String msg = "Couldn't find printer for ID. Please configure correct printer IP address in General reference.";
            LOGGER.error(msg);
            RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, msg));
            return;
        }
    }

    private void printInShop(Document inDocument, TruckTransaction inTran, boolean isDamage) {
        GeneralReference reference = GeneralReference.findUniqueEntryById("MATSON", "PRINTER_NAME", "SHOP");

        if (reference != null) {
            Printer shopPrinter = findHardwareDeviceById(reference.getRefValue1()) as Printer;
            if (shopPrinter != null) {
                byte[] data = inDocument.transformData(shopPrinter.getPrinterDriver());
                PrintUtil.print(data, shopPrinter.getHwHostAddress(), shopPrinter.getPrtrQueueName(), 1);
            }
        }
    }

/**
 * Send simple email message
 *
 * @param inTo TO email address
 * @param inFrom FROM email address
 * @param inSubject Text in the subject line
 * @param inBody Text in the body of the email
 * @return TRUE/FALSE     True if email has been sent or not
 */
    public Boolean sendEmail(String inTo, String inFrom, String inSubject, String inBody) {
        GroovyEmailSender sender = new GroovyEmailSender();
        MimeMessage msg = sender.createMimeMessage();
        MimeMessageHelper helper = new MimeMessageHelper(msg, "UTF-8");
        helper.setFrom(inFrom);
        helper.setReplyTo(inFrom);
        helper.setTo(inTo);
        helper.setSubject(inSubject);
        helper.setText(inBody, true);
        try {
            sender.send(msg);
        } catch (Exception inException) {
            LOGGER.error("MatsonAncPrintDocument: Exception in email attempt: " + inException);
            return false;
        }
        return true;
    }

    private class GroovyEmailSender extends JavaMailSenderImpl {
        GroovyEmailSender() {
            setMailServerPropertiesFromUserContext();
        }
        /**
         * Sets the Host, Port, and Protocol from the config settings based on the UserContext from the email message.
         *
         * @param inEmailMessage
         */
        private void setMailServerPropertiesFromUserContext() {
            try {
                UserContext userContext = ContextHelper.getThreadUserContext();
                setHost(MailServerConfig.HOST.getSetting(userContext));
                setPort(Integer.parseInt(MailServerConfig.PORT.getSetting(userContext)));
                String protocol = MailServerConfig.PROTOCOL.getSetting(userContext);
                long timeout = MailServerConfig.TIMEOUT.getValue(userContext);
                Properties props = new Properties();
                props.setProperty("mail.pop3.timeout", String.valueOf(timeout));
                setProtocol(protocol);
                if ("smtps".equals(protocol)) {
                    setUsername(MailServerConfig.SMTPS_USER.getSetting(userContext));
                    setPassword(MailServerConfig.SMTPS_PASSWORD.getSetting(userContext));
                    props.put("mail.smtp.auth", "true");
                    props.put("mail.smtps.auth", "true");
                    props.put("mail.smtp.ssl.enable", "true");
                    props.put("mail.transport.protocol", "smtps");
                }
                setJavaMailProperties(props);
                LOGGER.info("Initialized SMTP Mail Server Configuration.");
            } catch (Throwable throwable) {
                String error = "Initializing the SMTP Mail Server configuration encountered the following error:";
                LOGGER.error(error, throwable);
                throw new MailSendException(error, throwable);
            }
        }
        private Logger LOGGER = Logger.getLogger(GroovyEmailSender.class);
    }

    private static byte[] transformData(String inInputData) {
        PrintRequest printRequest = new PrintRequest();
        ByteArrayInputStream byteStream = null;
        try {
            byte[] xslLayout = htmlLayout.getBytes(Charset.forName("UTF-8"));
            byteStream = new ByteArrayInputStream(xslLayout);
            Resource resource = ResourceUtils.loadSerializableInputStreamResource(byteStream);
            printRequest.setSource(inInputData.getBytes(Charset.forName("UTF-8")));
            printRequest.setPrintDriverFormat(PrinterDriverEnum.CUSTOM);
            printRequest.setPrintDriverResource(resource);
        } catch (IOException ioe) {
            throw BizFailure.wrap(ioe);
        }
        return new PrintServiceManager().convertToDriverFormat(printRequest);
    }

    private static htmlLayout = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
            "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" xmlns:fo=\"http://www.w3.org/1999/XSL/Format\" xmlns:argo=\"http://www.navis.com/argo\" version=\"1.1\" >\n" +
            "\t<xsl:template match=\"argo:docDescription\"/>\n" +
            "\t<xsl:template name=\"argo:docBody\"/>\n" +
            "\t<xsl:template match=\"argo:truckVisit\"/>\n" +
            "\t<xsl:template match=\"argo:trkTransaction\">\n" +
            "\t\t<html>\n" +
            "\t\t\t<body>\n" +
            "\t\t\t\t<table cellspacing =\"20\">\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Chassis# / Owner:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:value-of select=\"tranChsNbr\"/>\n" +
            "\t\t\t\t\t\t</td>\n" +
            "\t\t\t\t\t</tr>\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Container# / Operator:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:value-of select=\"tranCtrNbr\"/>\n" +
            "\t\t\t\t\t\t</td>\t\t\t\t\t\n" +
            "\t\t\t\t\t</tr>\t\t\t\t\t\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Equipment Type:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:value-of select=\"tranCtrTypeId\"/>\n" +
            "\t\t\t\t\t\t\t<xsl:value-of select=\"tranGradeId\" />\t\t\t\t\t\t\t\n" +
            "\t\t\t\t\t\t</td>\t\t\t\t\t\n" +
            "\t\t\t\t\t</tr>\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Yard Row:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:value-of select=\"tranFlexString03\"/>\n" +
            "\t\t\t\t\t\t</td>\t\t\t\t\t\n" +
            "\t\t\t\t\t</tr>\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Trucking Code:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:value-of select=\"../argo:truckVisit/tvdtlsTrkCompany\"/>\n" +
            "\t\t\t\t\t\t</td>\t\t\t\t\t\n" +
            "\t\t\t\t\t</tr>\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Trucking Company:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:value-of select=\"../argo:truckVisit/tvdtlsTrkCompanyName\"/>\n" +
            "\t\t\t\t\t\t</td>\t\t\t\t\t\n" +
            "\t\t\t\t\t</tr>\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Driver Name:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:value-of select=\"../argo:truckVisit/tvdtlsDriverName\"/>\n" +
            "\t\t\t\t\t\t</td>\t\t\t\t\t\n" +
            "\t\t\t\t\t</tr>\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Clerk:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:value-of select=\"tranCreator\"/>\n" +
            "\t\t\t\t\t\t</td>\t\t\t\t\t\n" +
            "\t\t\t\t\t</tr>\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Defects:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:for-each select=\"argo:tranCtrDmg\">\t\t\t\n" +
            "\t\t\t\t\t\t\t\t<xsl:value-of select=\"dmgitemType\" />\n" +
            "\t\t\t\t\t\t\t</xsl:for-each>\t\t\t\t\t\t\n" +
            "\t\t\t\t\t\t</td>\t\t\t\t\t\n" +
            "\t\t\t\t\t</tr>\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Comments:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:for-each select=\"argo:tranCtrDmg\">\t\t\t\n" +
            "\t\t\t\t\t\t\t\t<xsl:value-of select=\"dmgitemDescription\" />\n" +
            "\t\t\t\t\t\t\t</xsl:for-each>\t\n" +
            "\t\t\t\t\t\t</td>\t\t\t\t\t\n" +
            "\t\t\t\t\t</tr>\n" +
            "\t\t\t\t\t<tr>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">Date:</td>\n" +
            "\t\t\t\t\t\t<td style=\"text-align:left;font-family:Arial;font-size=12pt\">\n" +
            "\t\t\t\t\t\t\t<xsl:value-of select=\"tranStartTime\"/>\n" +
            "\t\t\t\t\t\t</td>\t\t\t\t\t\n" +
            "\t\t\t\t\t</tr>\n" +
            "\t\t\t\t</table>\n" +
            "\t\t\t</body>\n" +
            "\t\t</html>\n" +
            "\t</xsl:template>\n" +
            "</xsl:stylesheet>";

    /**
     * Extract the print document page by page then send it to printer
     * sentToDirectPrinter
     * @param printer
     * @param inDocument
     */
    private void sentToDirectPrinter(Printer printer, Document inDocument,TruckTransaction inTran,String docTypeId){
        LOGGER.info(" MatsonAncPrintDocument execute sentToDirectPrinter using FTP logic.");
        byte[] printByte = inDocument.transformData(PrinterDriverEnum.CUSTOM);

        int ScanPos = 0;

        /* the indication of the end of a ticket is '<STX><ETB><ETX>' */

        while (ScanPos < printByte.size()) {

            /* scan through the ticket file looking for End Of Ticket indicators */

            boolean endOfTicket = false;
            def printTicketByte = new byte[printByte.size()];

            int printTicketindex = 0;

            /* ScanPos is the postion in the original byte array.  printTicketIndex is the postion in the new byte array
               with one ticket to be printed
             */

            while (!endOfTicket && ScanPos < printByte.size()) {

                /* check if a End Of Ticket indicator is found within the ticket byte array */

                if (ScanPos + 15 < printByte.size()
                        && (printByte[ScanPos] == '<' && printByte[ScanPos + 1] == 'S' && printByte[ScanPos + 2] == 'T'
                        && printByte[ScanPos + 3] == 'X' && printByte[ScanPos + 4] == '>' && printByte[ScanPos + 5] == '<'
                        && printByte[ScanPos + 6] == 'E' && printByte[ScanPos + 7] == 'T' && printByte[ScanPos + 8] == 'B'
                        && printByte[ScanPos + 9] == '>' && printByte[ScanPos + 10] == '<' && printByte[ScanPos + 11] == 'E'
                        && printByte[ScanPos + 12] == 'T' && printByte[ScanPos + 13] == 'X' && printByte[ScanPos + 14] == '>'))

                {

                    /* an end was found. Cut the byte array at this point */

                    endOfTicket = true;

                    /* copy the end of ticket stream to the ticket file to print */

                    for (int i = 0; i < 15; i++) {
                        printTicketByte[printTicketindex] = printByte[ScanPos];
                        ScanPos++;
                        printTicketindex++;
                    }
                } else {

                    /* if not the end of the ticket copy the bytes over to the one ticket byte array */

                    printTicketByte[printTicketindex] = printByte[ScanPos];
                    ScanPos++;
                    printTicketindex++;
                }

                if (endOfTicket) {
                    LOGGER.info(" MatsonAncPrintDocument Printing Ticket "+inTran.getTranNbr() + "_" + docTypeId);
                    /* process the one ticket */
                    sleep(500)
                    PrintUtil.print(printTicketByte, printer.getHwHostAddress(), printer.getPrtrQueueName(), 1);

                }
            }
        }
    }

    private Logger LOGGER = Logger.getLogger(MatsonAncPrintDocument.class);
}