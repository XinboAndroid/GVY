/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

import com.navis.argo.business.model.DocumentType
import com.navis.argo.business.model.GeneralReference
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.util.BizViolation
import com.navis.framework.util.message.MessageLevel
import com.navis.road.RoadPropertyKeys
import com.navis.road.business.model.GateLane
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 Class for validation. This class is being used from MatsonAncCreateDocument and MatsonAncPrintDocument.
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
 * Date: 01/28/2016
 * JIRA: CSDV-3024
 * Allow for 'direct print' lane set up.
 *
 */
class MatsonAncValidateGateDocuments extends AbstractExtensionCallback {
    public boolean isValidationSuccess(TransactionAndVisitHolder inOutDao) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncValidateGateDocuments execute Stared.");
        try {
            GeneralReference generalReferenceDamages = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "DAMAGES");
            if (generalReferenceDamages == null || generalReferenceDamages.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure Document Type Id in General Reference for DAMAGES."));
                return false;
            }
            GeneralReference generalReferenceTir = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "TIR");
            if (generalReferenceTir == null || generalReferenceTir.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure Document Type Id in General Reference for TIR."));
                return;
            }
            GeneralReference generalReferenceTirLoad = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "TIR_LOAD");
            if (generalReferenceTirLoad == null || generalReferenceTirLoad.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure Document Type Id in General Reference for TIR_LOAD."));
                return false;
            }
            GeneralReference generalReferenceDeliveryReceipt = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "DR");
            if (generalReferenceDeliveryReceipt == null || generalReferenceDeliveryReceipt.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure Document Type Id in General Reference for DR."));
                return false;
            }
            GeneralReference generalReferenceDNB = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "DNB");
            if (generalReferenceDNB == null || generalReferenceDNB.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure Document Type Id in General Reference for DNB."));
                return false;
            }

            GeneralReference emailIdGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "EMAIL", "NOTIFICATION");
            if (emailIdGeneralReference == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "please configure General Reference for  Notification(Damage) Email Id."));
                return false;
            }
            if (emailIdGeneralReference.getRefValue1() == null || emailIdGeneralReference.getRefValue2() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "please configure From and To Email Id in General Reference(Notification)."));
                return false;
            }

            GeneralReference multiStopUrlReference = GeneralReference.findUniqueEntryById("MATSON", "MULTISTOP", "URL");
            if (multiStopUrlReference == null || multiStopUrlReference.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "please configure multistop URL in General Reference."));
                return false;
            }

            GeneralReference multiStopCommodityIdReference = GeneralReference.findUniqueEntryById("MATSON", "MULTISTOP", "COMMODITY_ID");
            if (multiStopCommodityIdReference == null || multiStopCommodityIdReference.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "please configure multistop Commodity Id in General Reference."));
                return false;
            }

            GeneralReference damagePrinterIpReference = GeneralReference.findUniqueEntryById("MATSON", "PRINTER_NAME", "DAMAGES");
            if (damagePrinterIpReference == null || damagePrinterIpReference.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "please configure printer IP address for Damages in General Reference."));
                return false;
            }

            GeneralReference troubleIpReference = GeneralReference.findUniqueEntryById("MATSON", "PRINTER_NAME", "TROUBLE");
            if (troubleIpReference == null || troubleIpReference.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "please configure printer IP address for Trouble in General Reference."));
                return false;
            }

            GateLane lane = inOutDao.getTv().getTvdtlsExitLane();
            if (lane == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Couldn't find Gate Exit Lane."));
                return false;
            }

            GeneralReference ftpGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "FTP", "INFO", lane.getLaneId());

            /* if no general reference found for FTP check for Direct Print */

            if (ftpGeneralReference == null) {
                ftpGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "DIRECT PRINT", "INFO", lane.getLaneId());
            }

            if (ftpGeneralReference == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure FTP or DIRECT PRINT Connection Info in General Reference for Lane Id." + lane.getLaneId()));
                return false;
            }

            if (ftpGeneralReference.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure FTP IP address in General Reference."));
                return false;
            }

            if (ftpGeneralReference.getRefValue2() == null && ftpGeneralReference.getRefId1() == "FTP") {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure FTP directory in General Reference."));
                return false;
            }

            if (ftpGeneralReference.getRefValue3() == null && ftpGeneralReference.getRefId1() == "FTP") {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure FTP user Id in General Reference."));
                return false;
            }

            if (ftpGeneralReference.getRefValue4() == null && ftpGeneralReference.getRefId1() == "FTP") {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure FTP user password in General Reference."));
                return false;
            }

            if (ftpGeneralReference.getRefValue5() == null && ftpGeneralReference.getRefId1() == "FTP") {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure FTP default time out in General Reference."));
                return false;
            }

            if (ftpGeneralReference.getRefValue6() == null && ftpGeneralReference.getRefId1() == "FTP") {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "Please configure FTP read write time out in General Reference."));
                return false;
            }

            GeneralReference directPrintGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "DIRECT PRINT", "INFO", lane.getLaneId());;
            if (directPrintGeneralReference == null) {
                GeneralReference localGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "LOCAL_PATH", lane.getLaneId());
                if (localGeneralReference == null) {
                    RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                            "Please create a General Reference for Lane Id." + lane.getLaneId() + " to save tickets locally."));
                    return false;
                }
                if (localGeneralReference.getRefValue1() == null) {
                    RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                            "Please configure local path(to save tickets) in RefValue1 in General Reference for Lane Id." + lane.getLaneId()));
                    return false;
                }
            }

            DocumentType docType = DocumentType.findDocumentType(generalReferenceDamages.getRefValue1());
            if (docType == null) {
                RoadBizUtil.getMessageCollector().appendMessage(
                        MessageLevel.WARNING, RoadPropertyKeys.GATE__PRINT_INVALID_DOC_TYPE, null, [generalReferenceDamages.getRefValue1()]);
                return false;
            }
            docType = DocumentType.findDocumentType(generalReferenceTir.getRefValue1());
            if (docType == null) {
                RoadBizUtil.getMessageCollector().appendMessage(
                        MessageLevel.WARNING, RoadPropertyKeys.GATE__PRINT_INVALID_DOC_TYPE, null, [generalReferenceTir.getRefValue1()]);
                return false;
            }
            docType = DocumentType.findDocumentType(generalReferenceTirLoad.getRefValue1());
            if (docType == null) {
                RoadBizUtil.getMessageCollector().appendMessage(
                        MessageLevel.WARNING, RoadPropertyKeys.GATE__PRINT_INVALID_DOC_TYPE, null, [generalReferenceTirLoad.getRefValue1()]);
                return false;
            }
            docType = DocumentType.findDocumentType(generalReferenceDeliveryReceipt.getRefValue1());
            if (docType == null) {
                RoadBizUtil.getMessageCollector().appendMessage(
                        MessageLevel.WARNING, RoadPropertyKeys.GATE__PRINT_INVALID_DOC_TYPE, null, [generalReferenceDeliveryReceipt.getRefValue1()]);
                return false;
            }
            docType = DocumentType.findDocumentType(generalReferenceDNB.getRefValue1());
            if (docType == null) {
                RoadBizUtil.getMessageCollector().appendMessage(
                        MessageLevel.WARNING, RoadPropertyKeys.GATE__PRINT_INVALID_DOC_TYPE, null, [generalReferenceDNB.getRefValue1()]);
                return false;
            }
            GeneralReference companyNameGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "LABEL", "COMPANY_NAME");
            if (companyNameGeneralReference == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "MatsonAncValidateGateDocuments: Please configure Company Name(Label1 in Data Value1 and Label2 in Data Value2) in General Reference."));
                return false;
            }
            if (companyNameGeneralReference.getRefValue1() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "MatsonAncValidateGateDocuments: Please configure Label1(Company Name) in Data Value1 in General Reference."));
                return false;
            }
            if (companyNameGeneralReference.getRefValue2() == null) {
                RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "MatsonAncValidateGateDocuments: Please configure Label2(Legal Form) in Data Value2 in General Reference."));
                return false;
            }
        } finally {
            LOGGER.info(" MatsonAncValidateGateDocuments execute completed.");
        }
        return true;
    }
    private Logger LOGGER = Logger.getLogger(MatsonAncValidateGateDocuments.class);
}