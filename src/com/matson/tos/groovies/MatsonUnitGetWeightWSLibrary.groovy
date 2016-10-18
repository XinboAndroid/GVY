import com.navis.external.framework.AbstractExtensionCallback
import com.sun.deploy.config.ClientConfig
import org.apache.log4j.Level
import java.awt.PageAttributes
import com.navis.argo.business.model.GeneralReference
import com.sun.jersey.api.client.Client
import com.sun.jersey.api.client.ClientResponse
import com.sun.jersey.api.client.WebResource
import com.sun.jersey.api.client.config.ClientConfig
import com.sun.jersey.api.client.config.DefaultClientConfig
import org.apache.log4j.Logger
import javax.ws.rs.core.MediaType;

/**
 *
 * This groovy library calls the webservice to identify the VGMRequired field
 *
 * SOLAS VGM
 * Date: 25/05/2016
 * Called from:  MatsonUnitSetVGMVerifiedFromBooking groovy
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MatsonUnitGetWeightWSLibrary extends AbstractExtensionCallback {

    private Logger LOGGER = Logger.getLogger(MatsonUnitGetWeightWSLibrary.class);

    private String xmlWeightTagBegin = "<grossWeight>";
    private String xmlWeightTagEnd   = "</grossWeight>";

    public String getUnitWeight(String bookingNo, String equipmentId) {

        LOGGER.setLevel(Level.WARN);
        LOGGER.info(" MatsonUnitGetWeightWSLibrary execute Started.");

        GeneralReference genRef = GeneralReference.findUniqueEntryById("MATSON", "VGMQualifier", "URL");
        String webserviceURL = genRef.getRefValue1();   // WS URL
        String connTimeout    = genRef.getRefValue2();   // connection timeout
        String readTimeout      = genRef.getRefValue3();   // read timeout
        String bookingNoText = genRef.getRefValue4();   // bookingNo text
        String eqipmentIdText = genRef.getRefValue5();   // equipmentId text
        String urlExtn                = genRef.getRefValue6();   // equipment text
        String unitNbr = "";

        if (equipmentId != null && equipmentId.length()==11) {
            unitNbr = equipmentId.substring(0,10);
        } else
        {
            unitNbr = equipmentId;
        }
        LOGGER.info("webserviceURL: "+webserviceURL +", urlExtn: "+urlExtn + ", connTimeout: "+connTimeout+", readTimeout: "+readTimeout + ", bookingNoText: "+bookingNoText+", eqipmentIdText: "+eqipmentIdText);

        URL url = new URL(webserviceURL + urlExtn + "?" + bookingNoText + "=" + bookingNo + "&" + eqipmentIdText + "=" + unitNbr);
        //below line is hardcoded for testing - to be removed
        //URL url = new URL("http://dev2.svc.gates.matson.com/gates-service/vgm/equipment?bookingNo=6000152&equipmentId=TOLU4831156");
        logMsg(url.toString());

        /*DefaultClientConfig clientConfig1 = new DefaultClientConfig();
        clientConfig1.getProperties().put(ClientConfig.PROPERTY_CONNECT_TIMEOUT, connTimeout);
        clientConfig1.getProperties().put(ClientConfig.PROPERTY_READ_TIMEOUT, readTimeout);
        Client client = Client.create(clientConfig1);*/
        Client client = Client.create();

        WebResource resource = client.resource(url.toString());
        //ClientResponse response = (ClientResponse) resource.accept(PageAttributes.MediaType.APPLICATION_XML).get(ClientResponse.class);
        ClientResponse response =  (ClientResponse) resource.accept(MediaType.APPLICATION_XML).get(ClientResponse.class);
        if (response.getStatus() != 200) {
            logMsg("Request failed");
            logMsg(response.toString());
        } else {
            logMsg("Request Success");
            logMsg(response.toString());
        }
        String xmlResponse = response.getEntity(String.class);
        logMsg(xmlResponse);

        String weight ="";
        if (xmlResponse.contains(xmlWeightTagBegin) && xmlResponse.contains(xmlWeightTagEnd)) {
            int iAdjust = xmlWeightTagBegin.length();
            int iBegin = xmlResponse.indexOf(xmlWeightTagBegin);
            int iEnd = xmlResponse.lastIndexOf(xmlWeightTagEnd);
            weight = xmlResponse.substring(iBegin + iAdjust, iEnd);
        }

        LOGGER.info(" MatsonUnitGetWeightWSLibrary about to End - weight :"+weight);
        return weight;
    }
    private void logMsg(String inMsg) {
        LOGGER.warn(inMsg);
    }
}
