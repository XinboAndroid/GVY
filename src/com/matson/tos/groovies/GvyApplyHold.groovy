import com.navis.argo.business.api.GroovyApi;
import com.navis.services.business.event.GroovyEvent;

// Doesn't work
public class GvyApplyHold {

    public void applyEquipmentHold(Object equipment, String eventName,String note) {
        println("Apply Hold "+eventName);
        GroovyEvent event = new GroovyEvent( null, equipment);
        event.postNewEvent( eventName, note);

    }

}