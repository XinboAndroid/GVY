import com.navis.argo.*
import com.navis.argo.BookingTransactionsDocument.BookingTransactions
import com.navis.argo.BookingTransactionDocument.BookingTransaction
import com.navis.argo.BookingTransactionDocument.BookingTransaction.EdiBookingItem
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.external.edi.entity.AbstractEdiPostInterceptor
import com.navis.framework.persistence.HibernatingEntity
import com.navis.framework.portal.UserContext
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.orders.OrdersPropertyKeys
import org.apache.log4j.Logger
import org.apache.xmlbeans.XmlObject;

public class Gvy301CopyRemarksToItemNotes extends AbstractEdiPostInterceptor {
    UserContext context = ContextHelper.getThreadUserContext();
    Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

    @Override
    public void beforeEdiPost(Serializable inSessionGkey, XmlObject inXmlObject) throws BizViolation {
        LOGGER.warn("in Gvy301CopyRemarksToItemNotes Started" + timeNow);

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
            List <EdiBookingItem> items =  bkgTrans.getEdiBookingItemList();
            List <BookingTransaction.EdiBookingEquipment> bookingEquipmentList = bkgTrans.getEdiBookingEquipmentList();
            for (EdiBookingItem item : items) {
                String itemIsoCode = item.getISOcode();
                for (BookingTransaction.EdiBookingEquipment bookingEquipment : bookingEquipmentList) {
                    EdiContainer ctr = bookingEquipment.getEdiContainer();

                    if (ctr != null && ctr.getContainerISOcode() != null && itemIsoCode != null
                            && itemIsoCode.equalsIgnoreCase(ctr.getContainerISOcode())) {
                        item.setRemarks(ctr.getContainerRemark());
                        ctr.setContainerRemark("");
                    }
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Error while processing before edi post");
        }
        LOGGER.warn("in Gvy301CopyRemarksToItemNotes Ended" + timeNow);
    }

    @Override
    public void afterEdiPost(XmlObject inXmlObject, HibernatingEntity inHibernatingEntity, Map inParams) throws BizViolation {
        LOGGER.warn("in MATGvy301MsgFunctionCheck after EDI Post Started");
    }

    private static final Logger LOGGER = Logger.getLogger(Gvy301CopyRemarksToItemNotes.class);
}
