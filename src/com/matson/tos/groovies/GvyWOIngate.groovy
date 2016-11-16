import com.navis.argo.business.api.GroovyApi;
import com.navis.services.business.event.GroovyEvent;
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.argo.business.api.ServicesManager
import java.util.Iterator;
import java.util.Collection;

public class GvyWOIngate {

/** If commodity code is SIT reapply DRAY status
 */
    public void setDray(Object unit) {
        println("WO Gate set dray");
        if(unit.getFieldValue("unitGoods.gdsCommodity.cmdyId").equals("SIT")) {
            unit.setFieldValue("unitDrayStatus",com.navis.argo.business.atoms.DrayStatusEnum.OFFSITE);
        }
    }

    public void setPosition(Object unit) {
        // Update Position to WOA1
        println("WO Gate set positiony");
        GroovyEvent moveEvent = new GroovyEvent( null, unit);
        moveEvent.setProperty("PositionFull","Y-SI-WOA-1");
        moveEvent.setProperty("PositionSlot","WOA1");
        moveEvent.postNewEvent( "UNIT_YARD_MOVE", "Position Update on West Oahu In Gate");

    }

    public void transferToSI(Object unit) {
        println("WO Gate transfer to SI");
        GroovyEvent event = new GroovyEvent( null, unit);
        event.postNewEvent( "TRANSFER_TO_SI", "Transfer on West Oahu In Gate");

    }

}