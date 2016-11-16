import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.model.CarrierVisit
import com.navis.inventory.business.units.Unit
import com.navis.services.business.event.GroovyEvent;
import com.navis.services.business.event.Event;


public class PostNewEventUsingCarrierVisitAta extends GroovyApi {

    public void execute(Object event) {

        Boolean isOverTime = false;
        Unit unit =  event.getEntity();

        // Get the inbound Carrier Visit actual arrival time.

        CarrierVisit cv = unit.getUnitDeclaredIbCv();
        Date cvAtaDate = cv.getCvATA();

        if (cvAtaDate != null) {

            Calendar cvAtaCalendarDate = Calendar.getInstance();
            cvAtaCalendarDate.setTime(cvAtaDate);

            //get the arrival Hour from carrier visit
            int hours = cvAtaCalendarDate.getTime().getHours();

            //Returns (0 = Sunday, 1 = Monday, 2 = Tuesday, 3 = Wednesday, 4 = Thursday, 5 = Friday, 6 = Saturday)
            int dayOfWeek = cvAtaCalendarDate.getTime().getDay();

            //Check for the day - Whether the vessel arrived on SAT or SUN
            if ((dayOfWeek == 0) || (dayOfWeek == 6)) {
                isOverTime = true;
            }

            //Check for the day - Whether the vessel arrived on FRI after 5:00 PM or on MON before 8:00 AM.

            if ((dayOfWeek == 5)) {
                if (hours >= 17) {
                    isOverTime = true;
                }
            }

            if ((dayOfWeek == 1)) {
                if (hours <= 8) {
                    isOverTime = true;
                }
            }
            // if the boolean overTime is true - set by the above methods then record OVERTIME event.
            if (isOverTime) {
                event.postNewEvent("UNIT_DISCH_BILLING");
            }

        }
    }
}
