import java.io.Serializable;
import com.navis.external.edi.entity.AbstractEdiLoadInterceptor;
import com.navis.argo.business.api.ArgoUtils;
import org.apache.log4j.Logger;



public class MATGvyEDI301UpdateV1Segment extends AbstractEdiLoadInterceptor {

    @Override
    public String beforeEdiLoad(String inFileAsString, Serializable inEdiBatchGkey, String inDelimiter) {
        try {
            LOGGER.info("in MATGvyEDI301UpdateV1Segment.class Started" );
            return "";
        } catch (Exception e){
            LOGGER.info("Exception here!!!");
        }
    }


    private static final Logger LOGGER = Logger.getLogger(MATGvyEDI301UpdateV1Segment.class);
}
