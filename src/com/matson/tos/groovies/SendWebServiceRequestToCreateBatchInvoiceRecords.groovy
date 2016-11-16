import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.framework.util.DateUtil
import com.navis.vessel.business.WebServiceRequestToBilling
import com.navis.vessel.business.schedule.VesselVisitDetails
import com.navis.vessel.business.schedule.VesselVisitLine
import java.text.SimpleDateFormat
import org.jdom.Element
import org.jdom.Text

/**
 * Created by IntelliJ IDEA.
 * User: murali raghavachari
 * Date: Dec 5, 2008
 * Time: 12:06:33 PM
 *
 */
class SendWebServiceRequestToCreateBatchInvoiceRecords {
    public void execute(event) {
        println("Start: SendWebServiceRequestToCreateBatchInvoiceRecords");
        TimeZone timeZone = ContextHelper.getThreadUserTimezone();
        Calendar calendar = Calendar.getInstance(timeZone);
        //********************************************************************************
        // Please replace the following values as per your N4 Billing setup and requireemnt
        //********************************************************************************
        String invoiceClass = "Vessel";   //  Replace with appropriate class name as defined in N4 Billing for Customer Invoice Type
        String InvoiceTypeId = ""; // Replace with approprieate Invoice Type id. If Invoice Type is sepecified then there is no need of Invoice Class
        String currencyId = "USD";
        String remark = null;  // Enter remark if any
        String batchDescription = null; // Default descr if not specified "Vessel visit " + visitId + " created from SPARCS N4 WebService request"
        Date contractEffectiveDate = calendar.getTime();
        SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat(DateUtil.DATE_TIME_FORMAT);
        String contractEffectiveDateStr = DATE_TIME_FORMAT.format(contractEffectiveDate);
        VesselVisitDetails vvDetail = (VesselVisitDetails) event.entity
        String visitId = vvDetail.getCvdCv().getCvId();
        Set vvdLines = vvDetail.getVvdVvlineSet();
        Iterator itr = vvdLines.iterator();
        while (itr.hasNext()) {
            // now format the XML for each vessel operating line
            VesselVisitLine vvdLine = (VesselVisitLine) itr.next();
            ScopedBizUnit vvLineBizUnit = vvdLine.getVvlineBizu();
            String LineId = vvLineBizUnit.getBzuId();
            String contractCustomerId = LineId;   // assigned the vessel operating line as contract customer
            String payeeCustomerId = LineId;       // assigned the vessel operating line as payee customer
            String payeeCustomerBizRole = "LINEOP";
            String customerCustomerBizRole = "LINEOP";
            // The parameter list chosen by the N4 Billing is unique to each Customer site.
            // The element name should match with the ChargeableMarineEvent reportable entity external tag name.
            // This XML is embaded into the root XML in WebServiceRequestToBilling.SendWebServiceRequestToCreateBatchInvoiceRecords method.
            Element parmGroupElem = new Element("batchInvoiceParameter");
            parmGroupElem.addContent(new Element("IbId").addContent(new Text(visitId)));
            parmGroupElem.addContent(new Element("LineOperatorId").addContent(new Text(LineId)));
            WebServiceRequestToBilling webRequestBilling = new WebServiceRequestToBilling();
            String response = webRequestBilling.sendWebServiceRequestToCreateBatchInvoiceRecords(vvDetail, vvLineBizUnit, invoiceClass,
                    contractCustomerId, customerCustomerBizRole, payeeCustomerId, payeeCustomerBizRole, currencyId, remark, contractEffectiveDateStr,
                    batchDescription, parmGroupElem);
            println("Response : " + response);
        }
        println("End: SendWebServiceRequestToCreateBatchInvoiceRecords");
    }
}
