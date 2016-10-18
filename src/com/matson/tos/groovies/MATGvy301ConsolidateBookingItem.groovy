import com.navis.argo.*
import com.navis.argo.BookingTransactionDocument.BookingTransaction
import com.navis.argo.BookingTransactionsDocument.BookingTransactions
import com.navis.argo.BookingTransactionDocument.BookingTransaction.EdiBookingItem
import com.navis.argo.business.api.ArgoUtils
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
import org.apache.xmlbeans.XmlObject


public class MATGvy301ConsolidateBookingItem extends AbstractEdiPostInterceptor {
    UserContext context = ContextHelper.getThreadUserContext();
    Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

    @Override
    public void beforeEdiPost(Serializable inSessionGkey, XmlObject inXmlObject) throws BizViolation {
        LOGGER.warn("in MATGvy301ConsolidateBookingItem Started" + timeNow);

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

            this.checkBookingItems(bkgTrans);


        } catch (Exception e) {
            LOGGER.warn("Error while processing before edi post:"+e);
        }
        LOGGER.warn("in MATGvy301ConsolidateBookingItem Ended" + timeNow);
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
    @Override
    public void afterEdiPost(XmlObject inXmlObject, HibernatingEntity inHibernatingEntity, Map inParams) throws BizViolation {
        LOGGER.warn("in MATGvy301ConsolidateBookingItem after EDI Post Started");
    }


    private static final Logger LOGGER = Logger.getLogger(MATGvy301ConsolidateBookingItem.class);
}