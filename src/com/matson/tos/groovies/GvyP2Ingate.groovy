import com.navis.argo.business.api.GroovyApi;
import com.navis.services.business.event.GroovyEvent;
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.argo.business.api.ServicesManager
import java.util.Iterator;
import java.util.Collection;

public class GvyP2Ingate {

/** If commodity code is SIT reapply DRAY status
 */
    public void setDray(Object unit) {
        if(unit.getFieldValue("unitGoods.gdsCommodity.cmdyId").equals("SIT")) {
            unit.setFieldValue("unitDrayStatus",com.navis.argo.business.atoms.DrayStatusEnum.OFFSITE);
        }
    }

    public void setPosition(Object unit) {
        // Update Position to P2A1
        GroovyEvent moveEvent = new GroovyEvent( null, unit);
        moveEvent.setProperty("PositionFull","Y-SI-P2A-1");
        moveEvent.setProperty("PositionSlot","P2A1");
        moveEvent.postNewEvent( "UNIT_YARD_MOVE", "Position Update on P2 In Gate");

    }


}