import com.navis.argo.*
import com.navis.argo.BookingTransactionDocument.BookingTransaction
import com.navis.argo.BookingTransactionsDocument.BookingTransactions
import com.navis.argo.BookingTransactionDocument.BookingTransaction.EdiBookingItem
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.VesselVisitFinder
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.Facility
import com.navis.argo.business.reference.CarrierItinerary
import com.navis.argo.business.reference.RoutingPoint
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernatingEntity
import com.navis.framework.portal.UserContext
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.orders.OrdersPropertyKeys
import com.navis.orders.business.eqorders.Booking
import com.navis.road.business.util.RoadBizUtil
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject;
import com.navis.vessel.business.schedule.VesselVisitDetails;
import com.navis.vessel.business.schedule.VesselVisitLine;

public class MATGvy301MsgFunctionCheck extends AbstractEdiPostInterceptor {
    UserContext context = ContextHelper.getThreadUserContext();
    Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

    @Override
    public void beforeEdiPost(Serializable inSessionGkey, XmlObject inXmlObject) throws BizViolation {
        com.navis.argo.business.api.GroovyApi groovyApi =new GroovyApi();
        LOGGER.warn("in MATGvy301MsgFunctionCheck Started" + timeNow);

        if (inXmlObject == null) {
            LOGGER.warn("Input XML Object is null");
            throw BizFailure.create(OrdersPropertyKeys.ERRKEY__NULL_XMLBEAN, null);
        }
        if (!BookingTransactionsDocument.class.isAssignableFrom(inXmlObject.getClass())) {
            throw BizFailure.create(OrdersPropertyKeys.ERRKEY__TYPE_MISMATCH_XMLBEAN, null, inXmlObject.getClass().getName());
        }
        BookingTransactionsDocument bkgDocument = (BookingTransactionsDocument) inXmlObject;
        final BookingTransactions bkgtrans = bkgDocument.getBookingTransactions();
        final BookingTransaction[] bkgtransArray = bkgtrans.getBookingTransactionArray();
        if (bkgtransArray.length != 1) {
            throw BizFailure.create(OrdersPropertyKeys.ERRKEY__XML_TRANSACTION_DOCUMENT_LENGTH_EXCEED, null, String.valueOf(bkgtransArray.length));
        }
        BookingTransaction bkgTrans = bkgtransArray[0];
        try {
            EdiOperator ediOp = bkgTrans.getLineOperator();
            Facility facility = ContextHelper.getThreadFacility();
            Complex complex = ContextHelper.getThreadComplex();
            EdiVesselVisit EdiVv = bkgTrans.getEdiVesselVisit();
            ScopedBizUnit bkgLineOp = this.resolveLineOperator(EdiVv, ediOp);
            CarrierVisit ediCv = this.resolveCarrierVisit(EdiVv, complex, facility, bkgLineOp);
            Booking book = this.getBookingDetails(bkgTrans, ediCv, bkgLineOp);
            // if user changed the O/B carrier or POD of any units manually, then skip updating the Booking with NSS700
            // Update the booking with its old VV and POD.
            String vslId = EdiVv.getVesselId();
            try {
                if (vslId != null && vslId.equalsIgnoreCase("DUMMY") && book != null && book.eqoTally != null && book.eqoTally > 0
                        && book.getEqoVesselVisit() != null && !vslId.equalsIgnoreCase(book.getEqoVesselVisit().getCvId())) {
                    CarrierVisit carrierVisit = book.getEqoVesselVisit();
                    VesselVisitDetails vesselVisitDetails = VesselVisitDetails.resolveVvdFromCv(carrierVisit);
                    String lyoldsId = vesselVisitDetails.getVvdVessel().getVesLloydsId();
                    String vesselName = vesselVisitDetails.getVvdVessel().getVesName();
                    EdiVv.setVesselId(lyoldsId);
                    EdiVv.setVesselName(vesselName);
                    VesselVisitLine vslVisitLine = VesselVisitLine.findVesselVisitLine(vesselVisitDetails, bkgLineOp);
                    if (vslVisitLine!= null) {
                        EdiVv.setOutOperatorVoyageNbr(vslVisitLine.getVvlineOutVoyNbr());
                    }

                    EdiVv
                    if (bkgTrans.getDischargePort1() != null && "TBA".equalsIgnoreCase(bkgTrans.getDischargePort1().getPortId())) {
                        bkgTrans.getDischargePort1().setPortId(book.getEqoPod1().pointUnlocId);
                        bkgTrans.getDischargePort1().setPortName(book.getEqoPod1().pointId);
                    }
                }
            } catch (Exception e) {
                //groovyApi.sendEmail("gbabu@matson.com", "gbabu@matson.com","edi error ", e.toString());
                //ignore any errors
            }
            //bkg msg function code.
            this.checkMsgFunctionCode(bkgTrans, book);
            //update dates to null as the date format is not correct
            this.checkDateFormat(bkgTrans, book);
            // dkanndasan - Fix to consolidate booking items quantity
            this.checkBookingItems(bkgTrans);
        } catch (Exception e) {
            LOGGER.warn("Error while processing before edi post");
        }
        LOGGER.warn("in MATGvy301MsgFunctionCheck Ended" + timeNow);
    }

    @Override
    public void afterEdiPost(XmlObject inXmlObject, HibernatingEntity inHibernatingEntity, Map inParams) throws BizViolation {
        LOGGER.warn("in MATGvy301MsgFunctionCheck after EDI Post Started");
    }

    public void checkDateFormat(BookingTransaction inBkgTrans, Booking inBook) throws BizViolation {
        try {
            inBkgTrans.setMsgProducedDateTime(null);
            Interchange interchange = inBkgTrans.getInterchange();
            interchange.setDate(null);
            interchange.setTime(null);
            inBkgTrans.setInterchange(interchange);
        } catch (Exception e) {
            LOGGER.warn(" date and time set to null exception " + e);
        }
    }

    private void checkMsgFunctionCode(BookingTransaction inBkgTrans, Booking inBook) throws BizViolation {
        String msgFunction = this.getMsgFunction(inBkgTrans);
        if (msgFunction == null) {
            LOGGER.warn("msgFunction is Null");
            this.reportUserError("Message Function cannot be Null");
            return;
        }
        if (msgFunction.equalsIgnoreCase("D") || msgFunction.equalsIgnoreCase("R") || msgFunction.equalsIgnoreCase("E")) {
            LOGGER.warn("Booking Cancel is received No Action is Taken");
            return;
        }
        LOGGER.warn("msgFunction:" + msgFunction);
        if (inBook == null) {
            inBkgTrans.setMsgFunction("N");
            LOGGER.warn("Msg Function:" + "N");
        }
    }

    private Booking getBookingDetails(BookingTransaction inBkgTrans, CarrierVisit inCv, ScopedBizUnit inBkgLineOp) {
        EdiBooking bkgNbr = inBkgTrans.getEdiBooking();
        String bookingNumber = bkgNbr.getBookingNbr();
        Booking book = null;
        try {
            book = this.checkBooking(bookingNumber, inBkgLineOp, inCv);
            return book;
        } catch (Exception e) {
            LOGGER.warn(" Exception:" + e);
            return book;
        }
    }

    private String getMsgFunction(BookingTransaction inBkgTrans) {
        return inBkgTrans.getMsgFunction();
    }

    private CarrierVisit resolveCarrierVisit(EdiVesselVisit inEdiVv, Complex complex, Facility inFacility, ScopedBizUnit bkgLineOp) throws BizViolation {
        if (complex == null) {
            LOGGER.warn(" Thread Complex is Null");
        }
        String vvConvention = null;
        String vvId = null;
        final String ibVoyg = null;
        final String obVoyg = null;
        if (inEdiVv != null) {
            vvConvention = inEdiVv.getVesselIdConvention();
            vvId = inEdiVv.getVesselId();
            ibVoyg = inEdiVv.getInVoyageNbr();
            if (ibVoyg == null) {
                ibVoyg = inEdiVv.getInOperatorVoyageNbr();
            }
            obVoyg = inEdiVv.getOutVoyageNbr();
            if (obVoyg == null) {
                obVoyg = inEdiVv.getOutOperatorVoyageNbr();
            }
        }
        CarrierVisit cv;
        VesselVisitFinder vvf = (VesselVisitFinder) Roastery.getBean(VesselVisitFinder.BEAN_ID);
        // Note: This will throw a BizViolation if the vessel visit can not be found
        LOGGER.warn('Convention ' + vvConvention + ' vvId' + vvId + " voyage " + ibVoyg);
        if (ibVoyg != null) {
            cv = vvf.findVesselVisitForInboundStow(complex, vvConvention, vvId, ibVoyg, null, null);
        } else {
            cv = vvf.findOutboundVesselVisit(complex, vvConvention, vvId, obVoyg, bkgLineOp, null);
        }
        LOGGER.warn(cv);
        return cv;
    }

    private ScopedBizUnit resolveLineOperator(EdiVesselVisit inEdiVesselVisit, EdiOperator inEdiOperator) {
        LOGGER.warn(" in Resolve Line Operator");
        ScopedBizUnit inLine = null;
        String lineCode;
        String lineCodeAgency;
        try {
            if (inEdiOperator != null) {
                lineCode = inEdiOperator.getOperator();
                lineCodeAgency = inEdiOperator.getOperatorCodeAgency();
                inLine = ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP);
            }
            if (inLine == null && inEdiVesselVisit != null && inEdiVesselVisit.getShippingLine() != null) {
                lineCode = inEdiVesselVisit.getShippingLine().getShippingLineCode();
                lineCodeAgency = inEdiVesselVisit.getShippingLine().getShippingLineCodeAgency();
                inLine = ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP);
            }
        } catch (Exception e) {
            LOGGER.warn("Cannot Resolve Line Operator" + e);
        }
        return inLine;
    }

    private Booking checkBooking(String inBkgNbr, ScopedBizUnit inLineOperator, CarrierVisit inCv) {
        LOGGER.warn(" in Check Booking");
        if (inBkgNbr == null) {
            LOGGER.warn("inBkgNbr is null");
        }
        if (inLineOperator == null) {
            LOGGER.warn("inLineOperator is Null");
        }
        if (inCv == null) {
            LOGGER.warn("inCv is Null");
        }
        Booking bkg = Booking.findBookingByUniquenessCriteria(inBkgNbr, inLineOperator, inCv);
        LOGGER.warn("bkg:" + bkg.toString());
        return bkg;
    }

    private ScopedBizUnit getLineOperator(EdiVesselVisit inEdiVv) throws BizViolation {
        ScopedBizUnit line = null;
        String lineCode = null;
        String lineCodeAgency = null;
        if (inEdiVv != null && inEdiVv.getShippingLine() != null) {
            lineCode = inEdiVv.getShippingLine().getShippingLineCode();
            lineCodeAgency = inEdiVv.getShippingLine().getShippingLineCodeAgency();
            line = ScopedBizUnit.resolveScopedBizUnit(lineCode, lineCodeAgency, BizRoleEnum.LINEOP);
        }
        if (line == null) {
            throw BizViolation.create(OrdersPropertyKeys.ERRKEY__UNKNOWN_ENCODED_LINE_ID, null, lineCodeAgency, lineCode);
        }
        return line;
    }

    private boolean fcyPortNotInItinerary(CarrierVisit inCarrierVisit, RoutingPoint inRoutingPoint) {
        boolean notInItin = false;
        CarrierItinerary itin = inCarrierVisit.getCvCvd().getCvdItinerary();
        if (itin != null) {
            notInItin = itin.isPointInItinerary(inRoutingPoint);
        }
        return notInItin;
    }
    // Adds an error to the list of errors that will be displayed
    private void reportUserError(String message) {
        RoadBizUtil.messageCollector.appendMessage(BizFailure.create(message));
    }

    public void checkBookingItems(BookingTransaction inBkgTran)  throws BizViolation{
        List<BookingTransactionDocument.BookingTransaction.EdiBookingItem> bkgItems =  inBkgTran.getEdiBookingItemList();
        LOGGER.warn("Bookings Items Size::"+bkgItems.size());
        HashMap<String,Integer> uniqueItemMap = new HashMap();
        for (EdiBookingItem bkgItem : bkgItems){
            String isoCode = bkgItem.getISOcode();
            int newQuantity = Integer.parseInt(bkgItem.getQuantity());
            int  quantity = uniqueItemMap.get(isoCode) == null?0:uniqueItemMap.get(isoCode);
            int finalQuantity = newQuantity+quantity;
            uniqueItemMap.put(isoCode,finalQuantity);

        }
        LOGGER.warn("Unique Items Map ::::"+uniqueItemMap);

        for (EdiBookingItem bkgItem : bkgItems){
            int finalQty = uniqueItemMap.get(bkgItem.getISOcode());
            if(finalQty!=null || finalQty==0){
                bkgItem.setQuantity(String.valueOf(finalQty));
            }
        }
    }
    private static final Logger LOGGER = Logger.getLogger(MATGvy301MsgFunctionCheck.class);
}