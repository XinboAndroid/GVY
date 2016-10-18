/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

import com.navis.argo.*
import com.navis.argo.business.atoms.EquipRfrTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.*
import com.navis.argo.business.reference.*
import com.navis.edi.business.util.XmlUtil
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.atoms.MassUnitEnum
import com.navis.framework.business.atoms.TemperatureUnitEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.DateUtil
import com.navis.framework.util.internationalization.ITranslationContext
import com.navis.framework.util.internationalization.TranslationUtils
import com.navis.framework.util.time.TimeUtils
import com.navis.framework.util.unit.UnitUtils
import com.navis.inventory.business.imdg.HazardItem
import com.navis.inventory.business.imdg.HazardItemPlacard
import com.navis.inventory.business.imdg.ImdgClass
import com.navis.inventory.business.imdg.Placard
import com.navis.inventory.business.units.ReeferRqmnts
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipDamageItem
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.RoadBizMetafield
import com.navis.road.RoadPropertyKeys
import com.navis.road.business.appointment.model.GateAppointment
import com.navis.road.business.appointment.model.TruckVisitAppointment
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.atoms.TruckerFriendlyTranSubTypeEnum
import com.navis.road.business.model.*
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.util.TransactionTypeUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.road.portal.configuration.CachedGateConfiguration
import com.navis.road.portal.configuration.CachedGateStage
import org.apache.commons.io.IOUtils
import org.apache.commons.lang.StringUtils
import org.apache.log4j.Level
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlException
import org.apache.xmlbeans.XmlObject
import org.jetbrains.annotations.Nullable
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.w3c.dom.NodeList

import java.math.RoundingMode
import java.text.DateFormat
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import com.navis.framework.portal.query.DomainQuery

/**
 Create gate document.
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
 */
class MatsonAncCreateDocument extends AbstractGateTaskInterceptor implements EGateTaskInterceptor {

    /**
     * Create document based on the configuration docTypeId parameter
     *
     * @param inOutDao
     */
    public void execute(TransactionAndVisitHolder inOutDao) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncCreateDocument execute Stared.");
        /**
         * Before continuing, make sure there are no errors exist in the main session
         */
        if (!RoadBizUtil.getMessageCollector().hasError() && !inOutDao.hasTransaction()) {
            LOGGER.error(" MatsonAncCreateDocument: No truck transaction found or transaction has one or more errors.");
            return;
        }
        def matsonAncValidateGateDocuments = getLibrary("MatsonAncValidateGateDocuments");
        LOGGER.info("MatsonAncCreateDocument about to execute MatsonAncValidateGateDocuments");
        if (!matsonAncValidateGateDocuments.isValidationSuccess(inOutDao)) {
            return;
        }

        TruckTransaction tran = inOutDao.getTran();
        boolean isChsDamaged = tran.getTranChsDmg() != null && tran.getTranChsDmg().getDmgsItems() != null &&
                !tran.getTranChsDmg().getDmgsItems().isEmpty();
        GeneralReference generalReferenceDamages = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "DAMAGES");
        GeneralReference generalReferenceTir = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "TIR");
        GeneralReference generalReferenceTirLoad = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "TIR_LOAD");
        GeneralReference generalReferenceDeliveryReceipt = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "DR");
        GeneralReference generalReferenceDNB = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "DNB");
        GeneralReference multiStopCommodityIdReference = GeneralReference.findUniqueEntryById("MATSON", "MULTISTOP", "COMMODITY_ID");
        String cmdyId = tran.getTranCommodity() != null ? tran.getTranCommodity().getCmdyId() : null;
        boolean isMultiStop = cmdyId != null &&
                (cmdyId.equals(multiStopCommodityIdReference.getRefValue1()) || cmdyId.equals(multiStopCommodityIdReference.getRefValue2()) ||
                        cmdyId.equals(multiStopCommodityIdReference.getRefValue3()) || cmdyId.equals(multiStopCommodityIdReference.getRefValue4()));

        if (tran.isReceival()) {
            //1) create TIR document for Receival transaction
            DocumentType docType = DocumentType.findDocumentType(generalReferenceTir.getRefValue1());
            createTranDocument(inOutDao, docType, isMultiStop, null, "Page: 1 of 1");

            //2) create damage document
            if (isChsDamaged) {
                docType = DocumentType.findDocumentType(generalReferenceDamages.getRefValue1());
                createTranDocument(inOutDao, docType, isMultiStop, null, null);
            }
        } else if (tran.isDelivery()) {
            _document = null;
            //1) create TIR document for delivery transaction
            DocumentType docType = DocumentType.findDocumentType(generalReferenceTir.getRefValue1());
            DocumentDocument tirDocument = createTranDocument(inOutDao, docType, isMultiStop, null, null);

            //2) create TIR Load Info & Delivery Receipt
            if (isMultiStop) {
                docType = DocumentType.findDocumentType(generalReferenceTirLoad.getRefValue1());
                String ctrNbr = tran.getTranContainer() != null ? tran.getTranContainer().getEqIdFull() : tran.getTranCtrNbr();
                String bookingNbr = null;
                Unit unit = tran.getTranUnit();
                //first look for BL nbr, if exist then use it to call webservice. If BL is null then look for booking Nbr.
                bookingNbr = getBLNbr(tran);
                if (bookingNbr == null) {
                    if (tran.getTranEqoNbr() != null) {
                        bookingNbr = tran.getTranEqoNbr();
                    } else if (unit != null && unit.getUnitPrimaryUe().getUeDepartureOrderItem() != null) {
                        bookingNbr = unit.getUnitPrimaryUe().getUeDepartureOrderItem().getEqboiOrder().getEqboNbr();
                    }
                }
                if (ctrNbr != null && bookingNbr != null) {
                    if (bookingNbr.length() > 7) {
                        bookingNbr = bookingNbr.substring(0, 7);
                    }
                    println("Booking Number : " + bookingNbr);
                    //Element rootElement = getMultiStopRootElement(ctrNbr, bookingNbr);
                    Element rootElement = null;
                    //if record doesn't exist then look without check digit.
                    // if (rootElement == null || rootElement.getChildNodes().length == 0) {
                    //As per Bruno's suggestion, changed code to looking into the web service without container check digit.
                    Container ctr = Container.findContainerWithoutValidation(ctrNbr);
                    if (ctr != null) {
                        rootElement = getMultiStopRootElement(ctr.getEqIdNoCheckDigit(), bookingNbr);
                    }
//          }
                    if (rootElement != null) {
                        initTotalPagesForMultiStop(rootElement);
                        _currentPage = 1;
                        NodeList stopElement = rootElement.getElementsByTagName("stop");
                        if (stopElement != null) {
                            for (int i = stopElement.getLength() - 1; i >= 0; i--) {
                                if (hasTIRLOAD(stopElement.item(i))) {
                                    createTranDocument(inOutDao, docType, isMultiStop, stopElement.item(i), null);
                                } else {
                                    //If no TIR LOAD is printed then print DIR alone.
                                    docType = DocumentType.findDocumentType(generalReferenceDeliveryReceipt.getRefValue1());
                                    createTranDocument(inOutDao, docType, isMultiStop, stopElement.item(i), null);
                                }
                            }
                        }
                    }
                } else {
                    LOGGER.error(
                            "MatsonAncCreateDocument, couldn't create multistop ticket. Either container nbr[" + ctrNbr + "] or booking nbr[" + bookingNbr +
                                    "] is null");
                }
            } else if (tran.isHazardous()) {
                docType = DocumentType.findDocumentType(generalReferenceTirLoad.getRefValue1());
                createTranDocument(inOutDao, docType, isMultiStop, null, null);
            } else {
                // Printer Enhancement DO NOT PRINT DELIVERY RECEIPT FOR DM(Delivery Empty)
                if (!(TranSubTypeEnum.DM.equals(tran.getTranSubType()) || TranSubTypeEnum.DC.equals(tran.getTranSubType()))) {
                    //3) create Delivery Receipt Alone(without TIR LOAD)
                    docType = DocumentType.findDocumentType(generalReferenceDeliveryReceipt.getRefValue1());
                    createTranDocument(inOutDao, docType, isMultiStop, null, "Page: 2 of 2");
                }
            }

            // Update page no. for TIR
            int pageCount = 0;
            if (_totalPages == 0) {
                pageCount = 2;//TIR+DR
            } else {
                pageCount = _totalPages;
            }
            if (tirDocument != null && _document != null) {
                tirDocument.getDocument().getDocBody().getTrkTransactionList().get(0).setTranUnitFlexString02("Page: 1 of " + pageCount);
                String xmlText = tirDocument.xmlText();
                validateXML(xmlText);
                _document.setDocData(xmlText);
                HibernateApi.getInstance().update(_document)
            }
            //4) create DNB
            boolean isDoNotBackLoad = tran.getTranUfv() != null &&
                    ("Y".equals(tran.getTranUfv().getUfvFlexString09()) || "Yes".equals(tran.getTranUfv().getUfvFlexString09()));
            if (isDoNotBackLoad) {
                docType = DocumentType.findDocumentType(generalReferenceDNB.getRefValue1());
                createTranDocument(inOutDao, docType, isMultiStop, null, null);
            }

            //5) create damage document
            if (isChsDamaged) {
                docType = DocumentType.findDocumentType(generalReferenceDamages.getRefValue1());
                createTranDocument(inOutDao, docType, isMultiStop, null, null);
            }
        }

        LOGGER.info(" MatsonAncCreateDocument execute Completed.");
    }

    private static Element getMultiStopRootElement(String inUnitNbr, String inBookingNbr) {
        GeneralReference multiStopUrlReference = GeneralReference.findUniqueEntryById("MATSON", "MULTISTOP", "URL");
        String urlString = multiStopUrlReference.getRefValue2() != null ? multiStopUrlReference.getRefValue1() + multiStopUrlReference.getRefValue2() :
                multiStopUrlReference.getRefValue1();
        URL url = new URL(urlString + "cn=" + inUnitNbr + "&bn=" + inBookingNbr);
        println("Multistop : " + url.toString());
        URLConnection connection = url.openConnection();
        InputStream stream = connection.getInputStream();
        if (stream != null) {
            String StringFromInputStream = IOUtils.toString(stream, "UTF-8");
            return XmlUtil.getXmlRootElement(StringFromInputStream);
        } else {
            RoadBizUtil.appendExceptionChain(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                    "No multistop record found from webservice for Unit:" + inUnitNbr + " and Booking Nbr:" + inBookingNbr));
            return null;
        }
    }

    private DocumentDocument createTranDocument(TransactionAndVisitHolder inOutDao, DocumentType inDocType, boolean inIsMultiStop,
                                                Node inMultiStopNode, String inPageNbr) {
        TruckTransaction tran = inOutDao.getTran();
        DocumentDocument documentdoc = null;
        if (inDocType.isDocumentRequired(
                tran.getTranIsHazard(),
                tran.getTranUnit() != null && tran.getTranUnit().isReefer(),
                TranStatusEnum.TROUBLE.equals(tran.getTranStatus()))
        ) {
            documentdoc = DocumentDocument.Factory.newInstance();
            DocumentDocument.Document doc = documentdoc.addNewDocument();
            DocDescriptionDocument.DocDescription descr = doc.addNewDocDescription();
            descr.setDocType(inDocType.getDoctypePk().toString());
            descr.setDocName(inDocType.getDoctypeId());

            addDocBody(doc, inOutDao, inIsMultiStop, inDocType, inMultiStopNode, inPageNbr);
            MessagesDocument.Messages msgs = doc.addNewMessages();
            String xmlText = documentdoc.xmlText();
            validateXML(xmlText);
            String documentStageId = tran.getTranStageId();
            if (inOutDao.getParameters() != null && inOutDao.getParameters().containsKey(RoadBizMetafield.GATE_API_STAGE_ID)) {
                String apiGateStageId = (String) inOutDao.getParameters().get(RoadBizMetafield.GATE_API_STAGE_ID);
                if (inOutDao.getTv() != null && inOutDao.getTv().getTvdtlsGate() != null) {
                    CachedGateConfiguration config = inOutDao.getTv().getGateConfiguration();
                    if (apiGateStageId != null) {
                        boolean isExchangeLaneAssignmentBooth = config.getStage(apiGateStageId).isExchangeLaneAssignmentBooth();
                        if (isExchangeLaneAssignmentBooth) {
                            documentStageId = apiGateStageId;
                        }
                    }
                }
            }
            GeneralReference generalReferenceTir = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "TIR");
            if (generalReferenceTir.getRefValue1().equals(inDocType.getDoctypeId())) {
                _document = saveDocument(
                        inDocType,
                        inOutDao.getDocumentBatchNbr(),
                        documentStageId,
                        tran,
                        xmlText,
                );
            } else {
                saveDocument(
                        inDocType,
                        inOutDao.getDocumentBatchNbr(),
                        documentStageId,
                        tran,
                        xmlText,
                );
            }
            inOutDao.addDocument(tran.getTranGkey(), _document);
        }
        return documentdoc;
    }

    /**
     * This method validates the generated xml
     *
     * @param inXmlDoc : Generated Xml document.
     */
    private static void validateXML(String inXmlDoc) throws BizFailure {
        try {
            XmlObject.Factory.parse(inXmlDoc);
        } catch (XmlException e) {
            throw BizFailure.create(RoadPropertyKeys.ROAD__UNABLE_TO_PARSE_GENERATED_XML, e, inXmlDoc);
        }
    }

    private static void addDocBody(DocumentDocument.Document inDocument, TransactionAndVisitHolder inOutDao, boolean inIsMultiStop,
                                   DocumentType inDocType, Node inMultiStopNode, String inPageNbr) {
        DocBodyDocument.DocBody body = inDocument.addNewDocBody();
        TruckVisitDetails tvdtls = inOutDao.getTv();
        addTruckVisit(tvdtls, body, inOutDao);
        if (inOutDao.getTran() != null) {
            addTruckTransaction(inOutDao, body, inIsMultiStop, inDocType, inMultiStopNode, inPageNbr);
        }
    }

    @Nullable
    private static GateConfigStage getConfigStage(Gate inGate, String inTtstageId) {
        GateConfigStage gateStage = null;
        if (inGate != null && inGate.getGateConfig() != null) {
            gateStage = inGate.getGateConfig().getStageById(inTtstageId);
        }

        return gateStage;
    }

    private static List<TruckTransactionStage> sortTranStagesByStageOrder(Collection<TruckTransactionStage> inTruckTranStages) {
        List list = new ArrayList(inTruckTranStages);
        Collections.sort(list, new Comparator<TruckTransactionStage>() {
            public int compare(TruckTransactionStage inS1, TruckTransactionStage inS2) {
                Long s1Order = inS1.getTtstageOrder();
                Long s2Order = inS2.getTtstageOrder();
                return s1Order.compareTo(s2Order);
            }
        });

        return list;
    }

    private static void addTruckTransaction(TransactionAndVisitHolder inOutDao, DocBodyDocument.DocBody inDocBody, boolean inIsMultiStop,
                                            DocumentType inDocType, Node inMultiStopNode, String inPageNbr) {
        TruckTransaction tran = inOutDao.getTran();
        TrkTransactionDocument.TrkTransaction docTran = inDocBody.addNewTrkTransaction();
        GeneralReference companyNameGeneralReference = GeneralReference.findUniqueEntryById("MATSON", "LABEL", "COMPANY_NAME");
        docTran.setTranUfvFlexString01(companyNameGeneralReference.getRefValue1());
        docTran.setTranUfvFlexString02(companyNameGeneralReference.getRefValue2());
        ITranslationContext translator = TranslationUtils.getTranslationContext(ContextHelper.getThreadUserContext());
        docTran.setTranNbr(tran.getTranNbr() == null ? String.valueOf(tran.getTranGkey()) : String.valueOf(tran.getTranNbr()));
        Unit unit = tran.getTranUnit();
        String docTypeId = inDocType.getDoctypeId();
        String unitNotes = unit != null ? unit.getUnitRemark() : null;
        String notes = null;
        if (unitNotes != null) {
            notes = unitNotes.endsWith(".") ? unitNotes : unitNotes + ". ";
        }
        if (tran.getTranNotes() != null) {
            notes = notes + tran.getTranNotes();
        }
        //Production defect to limit the Cargo notes to avoid extra paper printing Start
        if (notes != null) {
            if (notes.length() > 200) {
                docTran.setTranNotes(notes.substring(0, 100));
                docTran.setTranFlexString04(notes.substring(100, 200));
                if (notes.length() > 300) {
                    docTran.setTranFlexString05(notes.substring(200, 300));
                } else {
                    docTran.setTranFlexString05(notes.substring(200));
                }
            } else if (notes.length() > 100) {
                docTran.setTranNotes(notes.substring(0, 100));
                docTran.setTranFlexString04(notes.substring(100));
            } else {
                docTran.setTranNotes(notes);
            }
        }
        //Production defect to limit the Cargo notes to avoid extra paper printing End

        TruckerFriendlyTranSubTypeEnum truckerFriendlySubType = null;
        TranSubTypeEnum subType = tran.getTranSubType();
        if (subType != null) {
            if (TranSubTypeEnum.DE.equals(subType)) {
                if (inIsMultiStop) {
                    //set deliver Import
                    docTran.setTranSubType(translator.getMessageTranslator().getMessage(TranSubTypeEnum.DI.getDescriptionPropertyKey()));
                } else {
                    docTran.setTranSubType("Deliver Work Order");
                }
            } else if (TranSubTypeEnum.RI.equals(subType)) {
                if (inIsMultiStop) {
                    //set Receive Export
                    docTran.setTranSubType(translator.getMessageTranslator().getMessage(TranSubTypeEnum.RE.getDescriptionPropertyKey()));
                } else {
                    docTran.setTranSubType("Receive Work Order");
                }
            } else {
                docTran.setTranSubType(translator.getMessageTranslator().getMessage(tran.getTranSubType().getDescriptionPropertyKey()));
            }
            truckerFriendlySubType = TransactionTypeUtil.getTruckerFriendlyTranSubTypeEnum(subType);
        }
        if (tran.getTranUnitCategory() != null) {
            docTran.setTranUnitCategory(tran.getTranUnitCategory().toString());
        }
        docTran.setTranTrouble(tran.getTranTrouble());
        docTran.setTranTroubleStatus(tran.getTranTroubleStatus());
        boolean tranHadTrouble = tran.getTranHadTrouble() != null && tran.getTranHadTrouble();
        docTran.setTranHadTrouble(Boolean.valueOf(tranHadTrouble).toString());

        docTran.setTranTrkcId(tran.getTranTrkcId());

        if (tran.getTranCreated() != null) {
            docTran.setTranCreated(getDateFormatter().format(tran.getTranCreated()));
        }
        docTran.setTranCreator(tran.getTranCreator());

        if (tran.getTranTruckTareWeight() != null) {
            docTran.setTranTruckTareWeight(tran.getTranTruckTareWeight().toString());
        }
        docTran.setTranCtrNbr(tran.getTranCtrNbr());
        docTran.setTranCtrOwnerId(tran.getTranCtrOwnerId());

        if (tran.getTranCtrFreightKind() != null) {
            docTran.setTranCtrFreightKind(tran.getTranCtrFreightKind().getKey());
        }

        String eqTypeId = tran.getTranCtrTypeId() != null ? tran.getTranCtrTypeId() : tran.getTranChsTypeId();
        EquipType equipType = EquipType.findEquipType(eqTypeId);
        EquipGrade equipGrade = unit != null ? unit.getUnitPrimaryUe().getUeGradeID() : null;

        String typeId = "";
        String desc = "";
        if (equipType != null && equipGrade != null) {
            typeId = equipType.getEqtypId() + "-" + equipGrade.getEqgrdId() + ":";
            if (equipType.getEqtypDescription() != null && equipGrade.getEqgrdDescription() != null) {
                desc = equipType.getEqtypDescription() + " " + equipGrade.getEqgrdDescription();
            } else if (equipType.getEqtypDescription() != null) {
                desc = equipType.getEqtypDescription();
            } else if (equipGrade.getEqgrdDescription() != null) {
                desc = equipGrade.getEqgrdDescription();
            }
            // Printer Enhancement truncate the equipement grade id printing on next page
            if(typeId !=null && typeId != "" && typeId.length()>35){
                typeId = typeId.substring(0,35);
            }
            docTran.setTranCtrTypeId(typeId);
            if(desc !=null && desc != "" && desc.length()>35){
                desc = desc.substring(0,35);
            }
            docTran.setTranGradeId(desc);
        } else if (equipType != null) {
            typeId = equipType.getEqtypId();
            if (equipType.getEqtypDescription() != null) {
                typeId = typeId + ":" + equipType.getEqtypDescription();
            }
            // Printer Enhancement truncate the equipement grade id printing on next page
            if(typeId !=null && typeId != "" && typeId.length()>35){
                typeId = typeId.substring(0,35);
            }
            docTran.setTranCtrTypeId(typeId);
        }

        if (tran.getTranCtrGrossWeight() != null) {
//      double grossWeight = new BigDecimal(UnitUtils.convertTo(tran.getTranCtrGrossWeight(), MassUnitEnum.KILOGRAMS, MassUnitEnum.POUNDS)).
//              setScale(2, RoundingMode.HALF_UP).doubleValue();
            double grossWeight = UnitUtils.convertTo(tran.getTranCtrGrossWeight(), MassUnitEnum.KILOGRAMS, MassUnitEnum.POUNDS);
            docTran.setTranCtrGrossWeight("" + Math.round(grossWeight));
        }

        docTran.setTranCtrTicketPosId(tran.getTranCtrTicketPosId());
        docTran.setTranChsNbr(tran.getTranChsNbr());
        docTran.setTranChsOwnerId(tran.getTranChsOwnerId());

        if (tran.getTranChsIsOwners() != null) {
            docTran.setTranChsIsOwners(tran.getTranChsIsOwners().toString());
        }

        docTran.setTranChsTypeId(tran.getTranChsTypeId());
        if (tran.getTranChsIsDamaged() != null) {
            docTran.setTranChsIsDamaged(tran.getTranChsIsDamaged().toString());
        }

        if (tran.getTranDischargePoint1() != null) {
            TranDischargePoint1Document.TranDischargePoint1 point1 = docTran.addNewTranDischargePoint1();
            docTran.setTranDischargePoint1(point1);
            point1.setPointId(tran.getTranDischargePoint1().getPointId());
        }
        if (tran.getTranDischargePoint2() != null) {
            TranDischargePoint2Document.TranDischargePoint2 point2 = docTran.addNewTranDischargePoint2();
            docTran.setTranDischargePoint2(point2);
            point2.setPointId(tran.getTranDischargePoint2().getPointId());
        }
        if (tran.getTranLoadPoint() != null) {
            TranLoadPointDocument.TranLoadPoint point = docTran.addNewTranLoadPoint();
            docTran.setTranLoadPoint(point);
            point.setPointId(tran.getTranLoadPoint().getPointId());
        }

        docTran.setTranOrigin(tran.getTranOrigin());
        docTran.setTranDestination(tran.getTranDestination());
        docTran.setTranLineId(tran.getTranLineId());
        docTran.setTranShipper(tran.getTranShipper());
        docTran.setTranConsignee(tran.getTranConsignee());
        ScopedBizUnit consignee = null;
        ScopedBizUnit shipper = null;

        if (UnitCategoryEnum.IMPORT.equals(tran.getTranUnitCategory())) {
            if (unit != null && unit.getUnitGoods() != null) {
                consignee = unit.getUnitGoods().getGdsConsigneeBzu();
                shipper = unit.getUnitGoods().getGdsShipperBzu();
            }
        } else if (UnitCategoryEnum.EXPORT.equals(tran.getTranUnitCategory())) {
            if (tran.getTranEqo() != null) {
                shipper = tran.getTranEqo().getEqoShipper();
                // Consignee may not be needed during export
                consignee = tran.getTranEqo().getEqoConsignee();
            } else {
                //Dray-Off of the Import container
                if (unit != null && unit.getUnitGoods() != null) {
                    consignee = unit.getUnitGoods().getGdsConsigneeBzu();
                    shipper = unit.getUnitGoods().getGdsShipperBzu();
                }
            }
            //delivered against a Booking/Order
        } else if (UnitCategoryEnum.STORAGE.equals(tran.getTranUnitCategory())) {
            if (tran.getTranEqo() != null) {
                shipper = tran.getTranEqo().getEqoShipper();
            }
        }

        if (consignee != null && !inIsMultiStop) {
            docTran.setTranConsigneeId(getAddress(consignee.getBzuCtct()));
            String name = consignee.getBzuName() != null && consignee.getBzuName().length() > 30 ? consignee.getBzuName().substring(0, 30) :
                    consignee.getBzuName();
            docTran.setTranConsigneeName(name);
        }

        if (shipper != null) {
            docTran.setTranShipperId(shipper.getBzuId());
            String name = shipper.getBzuName() != null && shipper.getBzuName().length() > 30 ? shipper.getBzuName().substring(0, 30) :
                    shipper.getBzuName();
            docTran.setTranShipperName(name);
        }

        String tranLineId = tran.getTranLine() != null ? tran.getTranLine().getBzuId() :
                (tran.getTranLineId() != null ? tran.getTranLineId() : null);
        if (tranLineId != null) {
            docTran.setTranLineId(tranLineId);
        }

        if (tran.getTranChassis() != null) {
            if (tran.getTranChassis().getEqEquipType() != null) {
                docTran.setTranChsTypeId(tran.getTranChassis().getEqEquipType().getEqtypId());
            }
        }
        if (tran.getTranChsOwner() != null) {
            docTran.setTranChsOwnerId(tran.getTranChsOwner().getBzuId());
        }

        if (tran.getTranCtrOwner() != null) {
            docTran.setTranCtrOwnerId(tran.getTranCtrOwner().getBzuId());
        }

        if (tran.getTranCtrOperator() != null) {
            TranCtrOperatorDocument.TranCtrOperator ctropr = docTran.addNewTranCtrOperator();
            ctropr.setBizuId(tran.getTranCtrOperator().getBzuId());
        }
        if (tran.getTranUnit() != null) {
            TranUnitDocument.TranUnit tranUnit = docTran.addNewTranUnit();
            tranUnit.setUnitId(tran.getTranUnit().getUnitId());
        }

        if (tran.getTranEqo() != null) {
            TranEqoDocument.TranEqo docTranEqo = docTran.addNewTranEqo();
            docTranEqo.setEqboNbr(tran.getTranEqo().getEqboNbr());
            docTranEqo.setEqoLineId(tran.getTranEqo().getEqoLine().getBzuId());
            docTranEqo.setEqoLineName(tran.getTranEqo().getEqoLine().getBzuName());
        }

        if (tran.getTranCarrierVisit() != null) {
            TranCarrierVisitDocument.TranCarrierVisit cv = docTran.addNewTranCarrierVisit();
            CarrierVisit carrierVisit = tran.getTranCarrierVisit();
            cv.setCvId(carrierVisit.getCvId());
            VisitDetails vstDtls = carrierVisit.getCvCvd();
            DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);

            if (carrierVisit.getCvATA() != null) {
                cv.setCvATA(dateFormatter.format(carrierVisit.getCvATA()));
            }
            if (carrierVisit.getCvATD() != null) {
                cv.setCvATD(dateFormatter.format(carrierVisit.getCvATD()));
            }

            if (vstDtls != null) {
                cv.setCvCvdCarrierIbVygNbr(vstDtls.getCarrierIbVoyNbrOrTrainId());
                cv.setCvCvdCarrierObVygNbr(vstDtls.getCarrierObVoyNbrOrTrainId());
                cv.setCvCvdCarrierVehicleName(vstDtls.getCarrierVehicleName());
                if (vstDtls.getCvdETA() != null) {
                    cv.setCvCvdETA(dateFormatter.format(vstDtls.getCvdETA()));
                }
                if (vstDtls.getCvdETD() != null) {
                    cv.setCvCvdETD(dateFormatter.format(vstDtls.getCvdETD()));
                }
            }
        }

        if (tran.getTranOperator() != null) {
            TranOperatorDocument.TranOperator opr = docTran.addNewTranOperator();
            opr.setOprId(tran.getTranOperator().getOprId());
        }

        if (tran.getTranContainer() != null) {
            docTran.setTranCtrNbr(tran.getTranContainer().getEqIdFull());
        }
        GeneralReference generalReferenceDNB = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "DNB");
        GeneralReference generalReferenceTirLoad = GeneralReference.findUniqueEntryById("MATSON", "DOCUMENTS", "TIR_LOAD");
        String ctrNbr = docTran.getTranCtrNbr();
        if (ctrNbr != null && !generalReferenceDNB.getRefValue1().equals(docTypeId) && !generalReferenceTirLoad.getRefValue1().equals(docTypeId)) {
            if (tran.getTranCtrOperator() != null) {
                docTran.setTranCtrNbr(ctrNbr + " " + tran.getTranCtrOperator().getBzuId());
            }
        }
        String chsNbr = docTran.getTranChsNbr();
        if (chsNbr != null && !generalReferenceDNB.getRefValue1().equals(docTypeId) && !generalReferenceTirLoad.getRefValue1().equals(docTypeId)) {
            if (tran.getTranChsOwnerId() != null) {
                chsNbr = chsNbr + " " + tran.getTranChsOwnerId();
            } else if (tran.getTranChsOwner() != null) {
                chsNbr = chsNbr + " " + tran.getTranChsOwner().getBzuId();
            }
            docTran.setTranChsNbr(chsNbr);
        }
        if (tran.getTranChsAccessory() != null) {
            if (tran.getTranChsAccessory().getEqEquipType() != null) {
                docTran.setTranChsAccTypeId(tran.getTranChsAccessory().getEqEquipType().getEqtypId());
            }
        }

        if (unit != null) {
            if (tran.isReceival()) {
                docTran.setTranCtrAccNbr(tran.getCtrAccessoryId());
            } else if (tran.isDelivery()) {
                String ctrAccNbr = null;
                String chsAccNbr = null;
                Accessory accessory = unit.getUnitCtrAccessory();
                if (accessory != null) {
                    ctrAccNbr = accessory.getEqIdFull();
                } else {
                    ctrAccNbr = tran.getCtrAccessoryId();
                }
                accessory = unit.getUnitChsAccessory();
                if (accessory != null) {
                    chsAccNbr = accessory.getEqIdFull();
                } else {
                    chsAccNbr = tran.getChsAccessoryId();
                }
                if (ctrAccNbr != null && chsAccNbr != null) {
                    docTran.setTranCtrAccNbr(ctrAccNbr + "/" + chsAccNbr);
                } else if (ctrAccNbr != null) {
                    docTran.setTranCtrAccNbr(ctrAccNbr);
                } else if (chsAccNbr != null) {
                    docTran.setTranChsAccNbr(chsAccNbr);
                }
            }
        }

        String blNbr = getBLNbr(tran);
        if (blNbr == null) {
            if (tran.getTranEqoNbr() != null) {
                blNbr = tran.getTranEqoNbr();
            } else if (unit != null && unit.getUnitPrimaryUe().getUeDepartureOrderItem() != null) {
                blNbr = unit.getUnitPrimaryUe().getUeDepartureOrderItem().getEqboiOrder().getEqboNbr();
            }
        }
        docTran.setTranEqoNbr(blNbr);
        if (tran.getTranSealNbr1() != null) {
            docTran.setTranSealNbr1(tran.getTranSealNbr1());
        } else if (unit != null) {
            docTran.setTranSealNbr1(unit.getUnitSealNbr1());
        }
        if (tran.getTranChsDmg() != null) {
            TranCtrDmgDocument.TranCtrDmg dmg = docTran.addNewTranCtrDmg();
            for (Iterator itr = tran.getTranChsDmg().getDamageItemsIterator(); itr.hasNext();) {
                UnitEquipDamageItem item = (UnitEquipDamageItem) itr.next();
                if (dmg.getDmgitemType() != null) {
                    dmg.setDmgitemType(dmg.getDmgitemType() + ", " + item.getDmgitemType().getEqdmgtypDescription());
                } else {
                    dmg.setDmgitemType(item.getDmgitemType().getEqdmgtypDescription());
                }
                if (dmg.getDmgitemDescription() != null) {
                    dmg.setDmgitemDescription(dmg.getDmgitemDescription() + ", " + item.getDmgitemDescription());
                } else {
                    dmg.setDmgitemDescription(item.getDmgitemDescription());
                }
            }
        } else {
            TranCtrDmgDocument.TranCtrDmg dmg = docTran.addNewTranCtrDmg();
            dmg.setDmgitemType("None");
        }


        if (inIsMultiStop) {
            if (inMultiStopNode != null) {
                Element stopElement = (Element) inMultiStopNode;
                NodeList consigneeNodeList = stopElement.getElementsByTagName("stopOffConsignee");
                NodeList hazdousCommodityNodeList = stopElement.getElementsByTagName("hazCommodity");
                NodeList nonHazousCommodityNodeList = stopElement.getElementsByTagName("nonHazCommodity");
                NodeList totalNbrOfStopsNodeList = stopElement.getElementsByTagName("totalNumberOfStops");
                NodeList stopSequenceNodeList = stopElement.getElementsByTagName("StopSequenceNumber");
                NodeList addressNodeList = stopElement.getElementsByTagName("address");
                int limit = 3;
                int size = 0;
                if (hazdousCommodityNodeList != null && nonHazousCommodityNodeList != null) {
                    size = hazdousCommodityNodeList.getLength() + nonHazousCommodityNodeList.getLength();
                } else if (hazdousCommodityNodeList != null) {
                    size = hazdousCommodityNodeList.getLength();
                } else if (nonHazousCommodityNodeList != null) {
                    size = nonHazousCommodityNodeList.getLength();
                }

                //Set Consignee
                if (consigneeNodeList != null && consigneeNodeList.length == 1) {
                    String name = consigneeNodeList.item(0).getTextContent() != null && consigneeNodeList.item(0).getTextContent().length() > 30 ?
                            consigneeNodeList.item(0).getTextContent().substring(0, 30) :
                            consigneeNodeList.item(0).getTextContent();
                    docTran.setTranConsigneeName(name);
                }

                //set Address
                docTran.setTranConsigneeId(getMultiStopAddress(addressNodeList));

                //Set Stop Nbr
                String stopNbr = "";
                if (totalNbrOfStopsNodeList != null && totalNbrOfStopsNodeList.length == 1) {
                    stopNbr = totalNbrOfStopsNodeList.item(0).getTextContent();
                }
                if (stopSequenceNodeList != null && stopSequenceNodeList.length == 1) {
                    String seqString = stopSequenceNodeList.item(0).getTextContent();
                    stopNbr = "Stop: " + seqString + " of " + stopNbr;
                }
                docTran.setTranFlexString02(stopNbr);
                int commoditySize = 0;
                if (hasTIRLOAD(inMultiStopNode.getParentNode())) {
                    ++_currentPage;
                    if (hazdousCommodityNodeList != null) {
                        for (Node hazdousCommodityNode : hazdousCommodityNodeList) {
                            TranHazardDocument.TranHazard haz = docTran.addNewTranHazard();
                            NodeList emergencyContactNameNodeList = ((Element) hazdousCommodityNode).getElementsByTagName("hazEmergencyContactName");
                            NodeList emergencyContactPhoneNodeList = ((Element) hazdousCommodityNode).getElementsByTagName("hazEmergencyPhone");

                            ++commoditySize;
                            //set Page Number
                            if (commoditySize % 4 == 0) {
                                ++_currentPage;
                            }
                            //TIR Load Page
                            haz.setHzrdiPageNumber("Page: " + _currentPage + " of " + _totalPages);

                            //Set Emergency Contact Name and Number
                            if (emergencyContactNameNodeList != null && emergencyContactNameNodeList.length == 1) {
                                docTran.setTranFlexString01(emergencyContactNameNodeList.item(0).getTextContent());
                            }
                            if (emergencyContactPhoneNodeList != null && emergencyContactPhoneNodeList.length == 1) {
                                docTran.setTranFlexString01(docTran.getTranFlexString01() + ", " + emergencyContactPhoneNodeList.item(0).getTextContent());
                            }

                            //Set HM
                            haz.setHzrdiInhalationZone("X");

                            //Set UN/NA Number
                            NodeList hazNumberNodeList = ((Element) hazdousCommodityNode).getElementsByTagName("hazNumber");
                            if (hazNumberNodeList != null && hazNumberNodeList.length == 1) {
                                haz.setHzrdiUNnum(hazNumberNodeList.item(0).getTextContent());
                            }

                            //Set Description
                            NodeList hazCommodityNameNodeList = ((Element) hazdousCommodityNode).getElementsByTagName("hazCommodityName");
                            if (hazCommodityNameNodeList != null && hazCommodityNameNodeList.length == 1) {
                                haz.setHzrdiProperName(hazCommodityNameNodeList.item(0).getTextContent());
                                String hazardDesc = haz.getHzrdiProperName();
                                if (hazardDesc != null) {
                                    if (hazardDesc.length() > 100) {
                                        haz.setHzrdiProperName(hazardDesc.substring(0, 50));
                                        haz.setHzrdiHazIdUpper(hazardDesc.substring(50, 100));
                                        haz.setHzrdiSubstanceLower(hazardDesc.substring(100));
                                    } else if (hazardDesc.length() > 50) {
                                        haz.setHzrdiProperName(hazardDesc.substring(0, 50));
                                        haz.setHzrdiHazIdUpper(hazardDesc.substring(50));
                                    }
                                }
                            }

                            //Set Class
                            NodeList hazClassNodeList = ((Element) hazdousCommodityNode).getElementsByTagName("hazPrimaryClass");
                            if (hazClassNodeList != null && hazClassNodeList.length == 1) {
                                haz.setHzrdiDescription(hazClassNodeList.item(0).getTextContent());
                            }

                            //Set P group
                            NodeList hazPGrpNodeList = ((Element) hazdousCommodityNode).getElementsByTagName("hazPackageGroup");
                            if (hazPGrpNodeList != null && hazPGrpNodeList.length == 1) {
                                haz.setHzrdiPackingGroup(hazPGrpNodeList.item(0).getTextContent());
                            }

                            //Set Flash Point
                            String flashPoint = "";
                            NodeList hazFlashPointList = ((Element) hazdousCommodityNode).getElementsByTagName("hazFlashPoint");
                            if (hazFlashPointList != null && hazFlashPointList.length == 1) {
                                flashPoint = hazFlashPointList.item(0).getTextContent();
                            }
                            NodeList hazFlashPointUOMList = ((Element) hazdousCommodityNode).getElementsByTagName("hazFlashPointUOM");
                            if (hazFlashPointUOMList != null && hazFlashPointUOMList.length == 1) {
                                flashPoint = flashPoint + hazFlashPointUOMList.item(0).getTextContent();
                            }
                            haz.setHzrdiMFAG(flashPoint);

                            //Set ERG Number
                            NodeList ergNumberNodeList = ((Element) hazdousCommodityNode).getElementsByTagName("ergNumber");
                            if (ergNumberNodeList != null && ergNumberNodeList.length == 1) {
                                haz.setHzrdiERGNumber(ergNumberNodeList.item(0).getTextContent());
                            }

                            //Set Qty/Unit
                            String qty = "";
                            NodeList qtyList = ((Element) hazdousCommodityNode).getElementsByTagName("hazPieces");
                            if (qtyList != null && qtyList.length == 1) {
                                qty = qtyList.item(0).getTextContent();
                            }
                            NodeList qtyUnitList = ((Element) hazdousCommodityNode).getElementsByTagName("hazPiecesUOM");
                            if (qtyUnitList != null && qtyUnitList.length == 1) {
                                qty = qty + "/" + qtyUnitList.item(0).getTextContent();
                            }
                            haz.setHzrdiLtdQty(qty);

                            //Set Weight
                            NodeList weightList = ((Element) hazdousCommodityNode).getElementsByTagName("hazWeight");
                            if (weightList != null && weightList.length == 1) {
                                String weight = weightList.item(0).getTextContent();
                                if (weight != null) {
                                    haz.setHzrdiWeight(Double.valueOf(weight));
                                }
                            }
                        }
                    }
                    if (nonHazousCommodityNodeList != null) {
                        for (Node nonHazdousCommodityNode : nonHazousCommodityNodeList) {
                            TranHazardDocument.TranHazard haz = docTran.addNewTranHazard();
                            //Set Description
                            NodeList hazCommodityDescNodeList = ((Element) nonHazdousCommodityNode).getElementsByTagName("commodityDescription");
                            if (hazCommodityDescNodeList != null && hazCommodityDescNodeList.length == 1) {
                                haz.setHzrdiProperName(hazCommodityDescNodeList.item(0).getTextContent());
                                String hazardDesc = haz.getHzrdiProperName();
                                if (hazardDesc != null) {
                                    if (hazardDesc.length() > 100) {
                                        haz.setHzrdiProperName(hazardDesc.substring(0, 50));
                                        haz.setHzrdiHazIdUpper(hazardDesc.substring(50, 100));
                                        haz.setHzrdiSubstanceLower(hazardDesc.substring(100));
                                    } else if (hazardDesc.length() > 50) {
                                        haz.setHzrdiProperName(hazardDesc.substring(0, 50));
                                        haz.setHzrdiHazIdUpper(hazardDesc.substring(50));
                                    }
                                }
                            }
                            //TIR Load Page
                            haz.setHzrdiPageNumber("Page: " + _currentPage + " of " + _totalPages);

                            //Set Qty/Unit
                            String qty = "";
                            NodeList qtyList = ((Element) nonHazdousCommodityNode).getElementsByTagName("countOfPieces");
                            if (qtyList != null && qtyList.length == 1) {
                                qty = qtyList.item(0).getTextContent();
                            }
                            NodeList qtyUnitList = ((Element) nonHazdousCommodityNode).getElementsByTagName("unitOfMeasure");
                            if (qtyUnitList != null && qtyUnitList.length == 1) {
                                qty = qty + "/" + qtyUnitList.item(0).getTextContent();
                            }
                            haz.setHzrdiLtdQty(qty);

                            //Set Weight
                            NodeList weightList = ((Element) nonHazdousCommodityNode).getElementsByTagName("weight");
                            if (weightList != null && weightList.length == 1) {
                                String weight = weightList.item(0).getTextContent();
                                if (weight != null) {
                                    haz.setHzrdiWeight(Double.valueOf(weight));
                                }
                            }
                        }
                    }
                }
                //DIR Page
                docTran.setTranUnitFlexString02("Page: " + (++_currentPage) + " of " + _totalPages);
            }
        } else {
            if (tran.getTranHaz() != null) {
                List hazItemList = tran.getTranHaz().getHzrdItems();
                if (hazItemList != null) {
                    int limit = 3;
                    int out = hazItemList.size() / limit;
                    if (hazItemList.size() % limit > 0) {
                        ++out;
                    }
                    //count+2(TIR+DR)
                    _totalPages = hazItemList != null ? out + 2 : 0;
                } else {
                    _totalPages = 2;
                }
                //For DIR ticket(last)
                docTran.setTranUnitFlexString02("Page: " + _totalPages + " of " + _totalPages);
                int tlPageCount = 2;
                int count = 0;
                for (Iterator itr = tran.getTranHaz().getHazardItemsIterator(); itr.hasNext();) {
                    ++count;
                    HazardItem hazi = (HazardItem) itr.next();
                    TranHazardDocument.TranHazard haz = docTran.addNewTranHazard();
                    if (count % 3 == 0) {
                        ++tlPageCount;
                    }
                    haz.setHzrdiPageNumber("Page: " + tlPageCount + " of " + _totalPages);
                    haz.setHzrdiImdgCode(hazi.getHzrdiImdgCode().getKey());
                    haz.setHzrdiUNnum(hazi.getHzrdiUNnum());
                    if (hazi.getHzrdiNbrType() != null) {
                        haz.setHzrdiNbrType(translator.getMessageTranslator().getMessage(hazi.getHzrdiNbrType().getDescriptionPropertyKey()));
                    }
                    ImdgClass imdg = hazi.getHzrdiImdgClass();
                    haz.setHzrdiDescription(translator.getMessageTranslator().getMessage(imdg.getCodePropertyKey()));
                    if (hazi.getHzrdiQuantity() != null) {
                        haz.setHzrdiQuantity(hazi.getHzrdiQuantity().intValue());
                    }
                    haz.setHzrdiPackageType(hazi.getHzrdiPackageType());
                    haz.setHzrdiProperName(hazi.getHzrdiProperName());
                    if (hazi.getHzrdiLtdQty()) {
                        haz.setHzrdiProperName(haz.getHzrdiProperName() + " (LTD QTY)");
                    }
                    String hazardDesc = haz.getHzrdiProperName();
                    if (hazardDesc != null) {
                        if (hazardDesc.length() > 100) {
                            haz.setHzrdiProperName(hazardDesc.substring(0, 50));
                            haz.setHzrdiHazIdUpper(hazardDesc.substring(50, 100));
                            haz.setHzrdiSubstanceLower(hazardDesc.substring(100));
                        } else if (hazardDesc.length() > 50) {
                            haz.setHzrdiProperName(hazardDesc.substring(0, 50));
                            haz.setHzrdiHazIdUpper(hazardDesc.substring(50));
                        }
                    }
                    haz.setHzrdiEmergencyTelephone(hazi.getHzrdiEmergencyTelephone());
                    if (docTran.getTranFlexString01() == null) {
                        docTran.setTranFlexString01(hazi.getHzrdiEmergencyTelephone());
                    }
                    if (hazi.getHzrdiPackingGroup() != null) {
                        haz.setHzrdiPackingGroup(
                                customizePackingGroup(translator.getMessageTranslator().getMessage(hazi.getHzrdiPackingGroup().getDescriptionPropertyKey())));
                    }
                    // No need for null check because in hzrd item constructor it is set to false as default value.
                    haz.setHzrdiLtdQty(hazi.getHzrdiQuantity().toString() + "/" + hazi.getHzrdiPackageType());
                    if (hazi.getHzrdiWeight() != null) {
                        double weight = new BigDecimal(UnitUtils.convertTo(hazi.getHzrdiWeight().doubleValue(), MassUnitEnum.KILOGRAMS, MassUnitEnum.POUNDS)).
                                setScale(2, RoundingMode.HALF_UP).doubleValue();
                        haz.setHzrdiWeight(weight);
                    }
                    //This field used for HM
                    haz.setHzrdiInhalationZone("X");
                    haz.setHzrdiExplosiveClass(hazi.getHzrdiExplosiveClass());
//          haz.setHzrdiPageNumber(hazi.getHzrdiPageNumber());
                    if (hazi.getHzrdiFlashPoint() != null) {
                        DecimalFormat format = new DecimalFormat("000C");
                        haz.setHzrdiMFAG("" + Math.round(hazi.getHzrdiFlashPoint()));
                    }
//          haz.setHzrdiTechName(hazi.getHzrdiTechName());
                    haz.setHzrdiEMSNumber(hazi.getHzrdiEMSNumber());
                    haz.setHzrdiERGNumber(hazi.getHzrdiERGNumber());
//          haz.setHzrdiMFAG(hazi.getHzrdiMFAG());
//          haz.setHzrdiHazIdUpper(hazi.getHzrdiHazIdUpper());
//          haz.setHzrdiSubstanceLower(hazi.getHzrdiSubstanceLower());
                    haz.setHzrdiPlannerRef(hazi.getHzrdiPlannerRef());
                    haz.setHzrdiMoveMethod(hazi.getHzrdiMoveMethod());
                    if (hazi.getHzrdiSecondaryIMO1() != null) {
                        haz.setHzrdiSecondaryIMO1(
                                translator.getMessageTranslator().getMessage(hazi.getHzrdiSecondaryIMO1().getDescriptionPropertyKey()));
                    }
                    if (hazi.getHzrdiSecondaryIMO2() != null) {
                        haz.setHzrdiSecondaryIMO2(
                                translator.getMessageTranslator().getMessage(hazi.getHzrdiSecondaryIMO2().getDescriptionPropertyKey()));
                    }
                    haz.setHzrdiDeckRestrictions(hazi.getHzrdiDeckRestrictions());
                    //This field is used to populate Consignee
                    haz.setHzrdiMarinePollutants(hazi.getHzrdiMarinePollutants().toString());
                    haz.setHzrdiDcLgRef(hazi.getHzrdiDcLgRef());
                    haz.setHzrdiNotes(hazi.getHzrdiNotes());
                    if (hazi.getHzrdiPlacardSet() != null) {
                        for (Object object : hazi.getHzrdiPlacardSet()) {
                            HazardItemPlacard itemPlacard = (HazardItemPlacard) object;
                            // create a new tranHazardPlacard in XML
                            TranHazardPlacardDocument.TranHazardPlacard hazardPlacard = haz.addNewTranHazardPlacard();
                            hazardPlacard.setHzrdipDescription(itemPlacard.getHzrdipDescription());
                            Placard placard = itemPlacard.getHzrdipPlacard();
                            if (placard != null) {
                                hazardPlacard.setPlacardText(placard.getPlacardText());
                                hazardPlacard.setPlacardFurtherExplanation(placard.getPlacardFurtherExplanation());
                                if (placard.getPlacardMinWtKg() != null) {
                                    hazardPlacard.setPlacardMinWtKg(placard.getPlacardMinWtKg());
                                }
                            }
                        }
                    }
                }
            }
        }
        if (docTran.getTranCtrOperator() == null) {
            TranCtrOperatorDocument.TranCtrOperator ctrOpr = docTran.addNewTranCtrOperator();
            if (tran.getTranLineId() != null) {
                ctrOpr.setBizuId(tran.getTranLineId());
            }
        }
        boolean isLastStage = tran.getTranNextStageId() == null;
        setTruckTranTimes(tran, docTran, isLastStage);

        if (tran.getTranUfvFlexString01() != null) {
            docTran.setTranUfvFlexString01(tran.getTranUfvFlexString01());
        }
        if (tran.getTranUfvFlexString02() != null) {
            docTran.setTranUfvFlexString02(tran.getTranUfvFlexString02());
        }
        if (tran.getTranUfvFlexString03() != null) {
            docTran.setTranUfvFlexString03(tran.getTranUfvFlexString03());
        }
        if (tran.getTranUfvFlexString04() != null) {
            docTran.setTranUfvFlexString04(tran.getTranUfvFlexString04());
        }
        if (tran.getTranUfvFlexString05() != null) {
            docTran.setTranUfvFlexString05(tran.getTranUfvFlexString05());
        }
        if (tran.getTranUfvFlexString06() != null) {
            docTran.setTranUfvFlexString06(tran.getTranUfvFlexString06());
        }
        if (tran.getTranUfvFlexString07() != null) {
            docTran.setTranUfvFlexString07(tran.getTranUfvFlexString07());
        }
        if (tran.getTranUfvFlexString08() != null) {
            docTran.setTranUfvFlexString08(tran.getTranUfvFlexString08());
        }
        if (tran.isDelivery()) {
            UnitFacilityVisit ufv = tran.getTranUfv();
            if (ufv != null && ("Y".equals(ufv.getUfvFlexString09()) || "Yes".equals(ufv.getUfvFlexString09()))) {
                docTran.setTranUfvFlexString09("DO NOT BACKLOAD");
            }
        }
        if (tran.getTranUfvFlexString10() != null) {
            docTran.setTranUfvFlexString10(tran.getTranUfvFlexString10());
        }
        if (tran.getTranUfvFlexDate01() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(tran.getTranUfvFlexDate01());
            docTran.setTranUfvFlexDate01(cal);
        }
        if (tran.getTranUfvFlexDate02() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(tran.getTranUfvFlexDate02());
            docTran.setTranUfvFlexDate02(cal);
        }
        if (tran.getTranUfvFlexDate03() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(tran.getTranUfvFlexDate03());
            docTran.setTranUfvFlexDate03(cal);
        }
        if (tran.getTranUfvFlexDate04() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(tran.getTranUfvFlexDate04());
            docTran.setTranUfvFlexDate04(cal);
        }
        if (tran.getTranUnitFlexString01() != null) {
            docTran.setTranUnitFlexString01(tran.getTranUnitFlexString01());
        }
        if (inPageNbr != null) {
            docTran.setTranUnitFlexString02(inPageNbr);
        }
/*    if (tran.getTranUnitFlexString02() != null) {
      docTran.setTranUnitFlexString02(tran.getTranUnitFlexString02());
    }*/
        if (tran.getTranUnitFlexString03() != null) {
            docTran.setTranUnitFlexString03(tran.getTranUnitFlexString03());
        }
        if (tran.getTranUnitFlexString04() != null) {
            docTran.setTranUnitFlexString04(tran.getTranUnitFlexString04());
        }
        if (tran.getTranUnitFlexString05() != null) {
            docTran.setTranUnitFlexString05(tran.getTranUnitFlexString05());
        }
        if (tran.getTranUnitFlexString06() != null) {
            docTran.setTranUnitFlexString06(tran.getTranUnitFlexString06());
        }
        if (tran.getTranUnitFlexString07() != null) {
            docTran.setTranUnitFlexString07(tran.getTranUnitFlexString07());
        }
        if (tran.getTranUnitFlexString08() != null) {
            docTran.setTranUnitFlexString08(tran.getTranUnitFlexString08());
        }
        if (tran.getTranUnitFlexString09() != null) {
            docTran.setTranUnitFlexString09(tran.getTranUnitFlexString09());
        }
        if (tran.getTranUnitFlexString10() != null) {
            docTran.setTranUnitFlexString10(tran.getTranUnitFlexString10());
        }
        if (tran.getTranUnitFlexString11() != null) {
            docTran.setTranUnitFlexString11(tran.getTranUnitFlexString11());
        }
        if (tran.getTranUnitFlexString12() != null) {
            docTran.setTranUnitFlexString12(tran.getTranUnitFlexString12());
        }
        if (tran.getTranUnitFlexString13() != null) {
            docTran.setTranUnitFlexString13(tran.getTranUnitFlexString13());
        }
        if (tran.getTranUnitFlexString14() != null) {
            docTran.setTranUnitFlexString14(tran.getTranUnitFlexString14());
        }
        if (tran.getTranUnitFlexString15() != null) {
            docTran.setTranUnitFlexString15(tran.getTranUnitFlexString15());
        }

        //Set Yard Row
        if (tran.getTranFlexString03() != null) {
            docTran.setTranFlexString03(tran.getTranFlexString03());
        }

        //Temp
        if (unit != null) {
            UnitFacilityVisit ufv = tran.getTranUfv();
            if (ufv != null) {
                String flexString07 = ufv.getUfvFlexString07();
                if ("KFF".equals(flexString07) || "AMB".equals(flexString07)) {
                    docTran.setTranFlexString06(flexString07);
                } else if (unit.getUnitGoods() != null) {
                    ReeferRqmnts reeferRqmnts = unit.getUnitGoods().getGdsReeferRqmnts();
                    if (reeferRqmnts != null) {
                        Double minC = reeferRqmnts.getRfreqTempLimitMinC();
                        Double maxC = reeferRqmnts.getRfreqTempLimitMaxC();
                        if (minC != null && maxC != null) {
                            minC = UnitUtils.convertTo(minC, TemperatureUnitEnum.C, TemperatureUnitEnum.F);
                            maxC = UnitUtils.convertTo(maxC, TemperatureUnitEnum.C, TemperatureUnitEnum.F);
                            docTran.setTranFlexString06(Math.round(minC) + "/" + Math.round(maxC));
                        } else if (minC != null) {
                            minC = UnitUtils.convertTo(minC, TemperatureUnitEnum.C, TemperatureUnitEnum.F);
                            docTran.setTranFlexString06("" + Math.round(minC));
                        }
                    }
                }
            }
        }
        //Carrier Service
        CarrierVisit cv = tran.getCarrierVisit();
        if (cv != null && cv.getCvCvd() != null && cv.getCvCvd().getCvdService() != null) {
            docTran.setTranFlexString07(cv.getCvCvd().getCvdService().getSrvcId());
        }

        //Is reefer
        if (equipType != null) {
            docTran.setTranFlexString08(EquipRfrTypeEnum.NON_RFR.equals(equipType.getEqtypRfrType()) ? "No" : "Yes");
        } else {
            docTran.setTranFlexString08("No");
        }

        if (tran.getTranFlexDate01() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(tran.getTranFlexDate01());
            docTran.setTranFlexDate01(cal);
        }
        if (tran.getTranFlexDate02() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(tran.getTranFlexDate02());
            docTran.setTranFlexDate02(cal);
        }
        if (tran.getTranFlexDate03() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(tran.getTranFlexDate03());
            docTran.setTranFlexDate03(cal);
        }
        if (tran.getTranFlexDate04() != null) {
            Calendar cal = Calendar.getInstance();
            cal.setTime(tran.getTranFlexDate04());
            docTran.setTranFlexDate04(cal);
        }

        GateAppointment gappt = tran.getTranAppointment();
        if (gappt != null) {
            docTran.setTranAppointmentState(gappt.getGapptState().getKey());
        }
    }

    private static String getBLNbr(TruckTransaction inTran) {
        Unit unit = inTran.getTranUnit();
        if (unit != null && unit.getUnitGoods() != null) {
            return unit.getUnitGoods().getGdsBlNbr();
        }
        return null;
    }

    private static boolean hasTIRLOAD(Node inMultiStopNode) {
        Element stopElement = (Element) inMultiStopNode;
        NodeList hazdousCommodityNodeList = stopElement.getElementsByTagName("hazCommodity");
        NodeList nonHazousCommodityNodeList = stopElement.getElementsByTagName("nonHazCommodity");
        return (hazdousCommodityNodeList != null && hazdousCommodityNodeList.getLength() > 0) ||
                (nonHazousCommodityNodeList != null && nonHazousCommodityNodeList.getLength() > 0);
    }

    private static void initTotalPagesForMultiStop(Element inRootElement) {
        NodeList stopElementList = inRootElement.getElementsByTagName("stop");
        _totalPages = 1;//TIR
        for (int i = stopElementList.getLength() - 1; i >= 0; i--) {
            Element stopElement = (Element) stopElementList.item(i);
            NodeList consigneeNodeList = stopElement.getElementsByTagName("stopOffConsignee");
            NodeList hazdousCommodityNodeList = stopElement.getElementsByTagName("hazCommodity");
            NodeList nonHazousCommodityNodeList = stopElement.getElementsByTagName("nonHazCommodity");
            if ((hazdousCommodityNodeList != null && hazdousCommodityNodeList.getLength() > 0) ||
                    (nonHazousCommodityNodeList != null && nonHazousCommodityNodeList.getLength() > 0)) {
                int limit = 3;
                int size = 0;
                if (hazdousCommodityNodeList != null && nonHazousCommodityNodeList != null) {
                    size = hazdousCommodityNodeList.getLength() + nonHazousCommodityNodeList.getLength();
                } else if (hazdousCommodityNodeList != null) {
                    size = hazdousCommodityNodeList.getLength();
                } else {
                    size = nonHazousCommodityNodeList.getLength();
                }
                int out = size / limit;
                if (size % limit > 0) {
                    ++out;
                }
                _totalPages = _totalPages + out + 1;
            } else {
                _totalPages = _totalPages + 1;//DIR alone
            }
        }
    }

    private static String customizePackingGroup(String inPackingGroup) {
        if ("Packaging Group - I".equals(inPackingGroup)) {
            return "I";
        } else if ("Packaging Group - II".equals(inPackingGroup)) {
            return "II";
        } else if ("Packaging Group - III".equals(inPackingGroup)) {
            return "III";
        }
        return inPackingGroup
    }

    private static String getAddress(ContactInfo inContactInfo) {
        if (inContactInfo == null) {
            return null;
        }
        String returnValue = "";
        if (inContactInfo.getCtctAddressLine1() != null) {
            returnValue = inContactInfo.getCtctAddressLine1();
        }

        if (inContactInfo.getCtctAddressLine2() != null) {
            returnValue = returnValue + ", " + inContactInfo.getCtctAddressLine2();
        }

        if (inContactInfo.getCtctAddressLine3() != null) {
            returnValue = returnValue + ", " + inContactInfo.getCtctAddressLine3();
        }

        if (inContactInfo.getCtctCity() != null) {
            returnValue = returnValue + ", " + inContactInfo.getCtctCity();
        }

        if (inContactInfo.getCtctState() != null) {
            returnValue = returnValue + ", " + inContactInfo.getCtctState().getStateName();
        }

        if (inContactInfo.getCtctMailCode() != null) {
            returnValue = returnValue + ", " + inContactInfo.getCtctMailCode();
        }

        if (inContactInfo.getCtctCountry() != null) {
            returnValue = returnValue + ", " + inContactInfo.getCtctCountry().getCntryName();
        }

        return returnValue;
    }

    private static String getMultiStopAddress(NodeList inNodeList) {
        if (inNodeList == null || inNodeList.getLength() == 0) {
            return null;
        }
        Node node = inNodeList.item(0);
        String returnValue = "";
        NodeList addressLine1List = ((Element) node).getElementsByTagName("addressLine1");
        if (addressLine1List != null && addressLine1List.length == 1) {
            String value = addressLine1List.item(0).getTextContent();
            if (StringUtils.isNotBlank(value)) {
                returnValue = value;
            }
        }
        NodeList addressLine2List = ((Element) node).getElementsByTagName("addressLine2");
        if (addressLine2List != null && addressLine2List.length == 1) {
            String value = addressLine2List.item(0).getTextContent();
            if (StringUtils.isNotBlank(value)) {
                returnValue = returnValue + ", " + value;
            }
        }
        NodeList cityList = ((Element) node).getElementsByTagName("city");
        if (cityList != null && cityList.length == 1) {
            String value = cityList.item(0).getTextContent();
            if (StringUtils.isNotBlank(value)) {
                returnValue = returnValue + ", " + value;
            }
        }
        NodeList stateList = ((Element) node).getElementsByTagName("state");
        if (stateList != null && stateList.length == 1) {
            String value = stateList.item(0).getTextContent();
            if (StringUtils.isNotBlank(value)) {
                returnValue = returnValue + ", " + value;
            }
        }
        NodeList zipList = ((Element) node).getElementsByTagName("zip");
        if (zipList != null && zipList.length == 1) {
            String value = zipList.item(0).getTextContent();
            if (StringUtils.isNotBlank(value)) {
                returnValue = returnValue + ", " + value;
            }
        }
        return returnValue;
    }

    private static void setTruckVisitTimes(TruckVisitStats inStats, TruckVisitDocument.TruckVisit inDocTv, boolean inIsLastStage) {
        if (inStats != null && inStats.getTvstatStart() != null) {
            TimeZone tz;
            Facility facility = ContextHelper.getThreadFacility();
            if (facility != null) {
                tz = facility.getTimeZone();
            } else {
                tz = ContextHelper.getThreadComplex().getTimeZone();
            }
            inDocTv.setTvdtlsTrkStartTime(convertDateToLocalTime(inStats.getTvstatStart(), tz));
            if (inIsLastStage) {
                StringBuilder duration = new StringBuilder();
                long durationMins = (TimeUtils.getCurrentTimeMillis() - inStats.getTvstatStart().getTime()) / 1000 / 60;
                long hours = durationMins / 60;
                long minutes = durationMins % 60;
                duration.append(hours).append(':');
                if (minutes < 10) {
                    duration.append('0');
                }
                duration.append(minutes);
                inDocTv.setTvdtlsDuration(duration.toString());
                Date tvEndTime = TimeUtils.getCurrentTime();
                inDocTv.setTvdtlsTrkEndTime(convertDateToLocalTime(tvEndTime, tz));
            }

            TruckVisitStage yardStageStats = inStats.findStageById(CachedGateStage.STAGE_ID__YARD);
            if (yardStageStats != null && yardStageStats.getTvstageEnd() != null) {
                inDocTv.setTvdtlsYardCompletionTime(DateUtil.convertDateToLocalTime(yardStageStats.getTvstageEnd(), tz));
            }
        }
    }

    private static String convertDateToLocalTime(Date inDate, TimeZone inTimeZone) {
        if (inDate == null) {
            return null;
        }
        SimpleDateFormat formatter = new SimpleDateFormat("MM/dd/yyyy HH:mm:ss");
        formatter.setTimeZone(inTimeZone);
        return formatter.format(inDate);
    }

    private static void addTruckVisit(TruckVisitDetails inTvdtls, DocBodyDocument.DocBody inDocBody, TransactionAndVisitHolder inOutDao) {
        TruckVisitDocument.TruckVisit docTv = inDocBody.addNewTruckVisit();
        if (inTvdtls != null) {
            docTv.setTvdtlsLicNbr(inTvdtls.getTvdtlsTruckLicenseNbr());
            docTv.setTvdtlsTruckId(inTvdtls.getTvdtlsTruckId());
            if (inTvdtls.getTvdtlsTruckId() != null) {
                docTv.setTvdtlsTrkId(inTvdtls.getTvdtlsTruckId());
            }
            docTv.setTvdtlsDriverCardId(inTvdtls.getTvdtlsDriverCardId());
            docTv.setTvdtlsDriverLicenseNbr(inTvdtls.getTvdtlsDriverLicenseNbr());
            docTv.setTvdtlsDriverName(inTvdtls.getTvdtlsDriverName());
            docTv.setTvdtlsBatNbr(inTvdtls.getTvdtlsBatNbr());
            docTv.setTvdtlsTvKey(inTvdtls.getCvdGkey());
            if (inTvdtls.getTvdtlsGosTvKey() != null) {
                docTv.setTvdtlsGosTvKey(inTvdtls.getTvdtlsGosTvKey());
            }
            if (inTvdtls.getTvdtlsTrkCompany() != null) {
                docTv.setTvdtlsTrkCompany(inTvdtls.getTvdtlsTrkCompany().getBzuId());
                String name = inTvdtls.getTvdtlsTrkCompany().getBzuName();
                if (name != null && name.length() > 25) {
                    docTv.setTvdtlsTrkCompanyName(name.substring(0, 25));
                } else {
                    docTv.setTvdtlsTrkCompanyName(name);
                }
            }
            docTv.setTvdtlsFlexString01(inTvdtls.getTvdtlsFlexString01());
            docTv.setTvdtlsFlexString02(inTvdtls.getTvdtlsFlexString02());
            docTv.setTvdtlsFlexString03(inTvdtls.getTvdtlsFlexString03());

            if (inTvdtls.getTvdtlsFlexDate01() != null) {
                Calendar cal = Calendar.getInstance();
                cal.setTime(inTvdtls.getTvdtlsFlexDate01());
                docTv.setTvdtlsFlexDate01(cal);
            }

            if (inTvdtls.getTvdtlsEntryLane() != null) {
                GateLaneType laneType = docTv.addNewTvdtlsEntryLane();
                laneType.setLaneId(inTvdtls.getTvdtlsEntryLane().getLaneId());
            }
            if (inTvdtls.getTvdtlsExchangeLane() != null) {
                GateLaneType laneType = docTv.addNewTvdtlsExchangeLane();
                laneType.setLaneId(inTvdtls.getTvdtlsExchangeLane().getLaneId());
            }
            if (inTvdtls.getTvdtlsExitLane() != null) {
                GateLaneType laneType = docTv.addNewTvdtlsExitLane();
                laneType.setLaneId(inTvdtls.getTvdtlsExitLane().getLaneId());
            }
            if (inTvdtls.getTvdtlsTroubleLane() != null) {
                GateLaneType laneType = docTv.addNewTvdtlsTroubleLane();
                laneType.setLaneId(inTvdtls.getTvdtlsTroubleLane().getLaneId());
            }
            String nextExchangeAreaId = inTvdtls.getTvdtlsNextExchangeAreaId();
            if (nextExchangeAreaId != null) {
                docTv.setTvdtlsNextExchangeAreaId(nextExchangeAreaId);
            }
            List<TruckTransactionStage> gateStages = inTvdtls.findTruckVisitsStages();
            if (gateStages != null && !gateStages.isEmpty()) {
                List<TruckTransactionStage> sortedStages = sortTranStagesByStageOrder(gateStages);
                for (Object gateStage : sortedStages) {
                    TruckTransactionStage item = (TruckTransactionStage) gateStage;
                    TvdtlsRequiredStagesDocument.TvdtlsRequiredStages stage = docTv.addNewTvdtlsRequiredStages();
                    String stageId = item.getTtstageId();
                    stage.setStageId(stageId);
                    if (item.getTtstageOrder() != null) {
                        stage.setStageOrder(item.getTtstageOrder().intValue());
                    }
                    if (item.getStageTransaction() != null) {
                        GateConfigStage configStage = getConfigStage(item.getStageTransaction().getGate(), stageId);
                        if (configStage != null) {
                            stage.setStageDescription(configStage.getStageDescription());
                        }
                    }
                }
            }

            String nextStageId;
            if (inOutDao.hasTransaction()) {
                nextStageId = inTvdtls.getNextStageIdFromTransactions(inOutDao.getStage());
            } else {
                nextStageId = inTvdtls.getTvdtlsNextStageId();
            }
            boolean isLastStage = nextStageId == null;

            setTruckVisitTimes(inTvdtls.getStats(), docTv, isLastStage);

            //Printer Enhancement Start Receive Export Only
            TruckTransaction transaction = inOutDao.getTran();
            if(TranSubTypeEnum.RE.equals(transaction.getTranSubType())){
                Unit unit = transaction.getTranUnit();
                if(unit!=null) {
                    UnitFacilityVisit ufv = unit.getUnitActiveUfv();
                    String truckVisitTime = getUnitLastDeliveryTime(unit.getUnitId());
                    if (truckVisitTime != null) {
                        docTv.setTvdtlsTrkEndTime(truckVisitTime);
                    }
                }
            }
            //Printer Enhancement End

            docTv.setTvdtlsAppointmentNbr(String.valueOf(inTvdtls.getTvdtlsTvAppointmentNbr()));
            TruckVisitAppointment tva = inTvdtls.getTvdtlsTruckVisitAppointment();
            if (tva != null) {
                docTv.setTvdtlsAppointmentState(tva.getTvapptState().getKey());
            }
        }
    }

    private static com.navis.road.business.model.Document saveDocument(
            DocumentType inDoctype,
            Long inBatchNbr,
            String inStageId,
            TruckTransaction inTrkTran,
            String inDocData
    ) {
        return com.navis.road.business.model.Document.
                createDocument(inDoctype, inBatchNbr, inStageId, inTrkTran, inDocData);
    }

    private static void setTruckTranTimes(TruckTransaction inTransaction, TrkTransactionDocument.TrkTransaction inDocTran, boolean inIsLastStage) {
        if (inTransaction != null && inTransaction.getTranCreated() != null) {
            TimeZone tz;
            Facility facility = ContextHelper.getThreadFacility();
            if (facility != null) {
                tz = facility.getTimeZone();
            } else {
                tz = ContextHelper.getThreadComplex().getTimeZone();
            }

            // tranStartTime is the transaction's created time
            inDocTran.setTranStartTime(convertDateToLocalTime(inTransaction.getTranCreated(), tz));

            // tranEndTime is set only if the transaction is at the last stage. If so, the current time is set as the tranEndTime
            if (inIsLastStage) {
                Date tranEndTime = TimeUtils.getCurrentTime();
                inDocTran.setTranEndTime(convertDateToLocalTime(tranEndTime, tz));
            }
        }
    }

    private static DateFormat getDateFormatter() {
        DateFormat dateFormatter = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT);
        dateFormatter.setTimeZone(ContextHelper.getThreadUserTimezone());
        return dateFormatter;
    }
    /**
     * This method is used to get the container last delivery time
     * getUnitLastDeliveryTime
     * @param unitId
     */
    public static String getUnitLastDeliveryTime(String  tranCtrNbr) {
        DomainQuery truckTransQuery = com.navis.framework.portal.QueryUtils.createDomainQuery("TruckTransaction")
                .addDqPredicate(com.navis.framework.portal.query.PredicateFactory.eq(com.navis.road.RoadField.TRAN_CTR_NBR, tranCtrNbr)).
                addDqOrdering(com.navis.framework.portal.Ordering.desc(com.navis.road.RoadField.TRAN_HANDLED));
        List<TruckTransaction> truckTransactions=  HibernateApi.getInstance().findEntitiesByDomainQuery(truckTransQuery);
        Iterator iterator = truckTransactions.iterator();
        Date createdDate = null;
        while(iterator.hasNext()) {
            TruckTransaction transaction = (TruckTransaction) iterator.next();
            if (transaction.getTranHandled() != null && transaction.isDelivery()){
                createdDate = transaction.getTranHandled();
                break
            }

        }
        TimeZone tz;
        Facility facility = ContextHelper.getThreadFacility();
        if (facility != null) {
            tz = facility.getTimeZone();
        } else {
            tz = ContextHelper.getThreadComplex().getTimeZone();
        }

        return convertDateToLocalTime(createdDate, tz);
    }

    private static int _totalPages = 0;
    private static int _currentPage = 0;
    private com.navis.road.business.model.Document _document
    private Logger LOGGER = Logger.getLogger(MatsonAncCreateDocument.class);
}