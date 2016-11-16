import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.event.GroovyEvent
import com.navis.orders.business.eqorders.Booking
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.reference.*

class GvyInjAbob extends GroovyInjectionBase {
    public String execute(Map inParameters) {

        com.navis.argo.ContextHelper.setThreadExternalUser("ACETS");

        def eventId = (String) inParameters.get("eventId");
        def bookingNum = (String) inParameters.get( "bookingNum");
        def vesvoy = (String) inParameters.get( "vesvoy");
        log( "GvyInjAbob start event: " + eventId + " for booking: " + bookingNum);

        // find booking
        def facility = getFacility();
        def cv = CarrierVisit.findVesselVisit( facility, vesvoy);
        if ( cv == null) {
            fail( "ERR_GVY_BOB_001. Could not find the carrier visit: " + vesvoy);
        }

        def bizScope = ScopedBizUnit.findScopedBizUnit( "MAT", BizRoleEnum.LINEOP);
        if ( bizScope == null) {
            fail( "ERR_GVY_BOB_002. Could not find the business unit: MAT");
        }

        def booking = Booking.findBookingByUniquenessCriteria( bookingNum, bizScope, cv);
        if ( booking == null) {
            fail( "ERR_GVY_BOB_003. Could not find booking: " + bookingNum);
        }

        def event = new GroovyEvent( null, booking);

        event.postNewEvent( eventId);
        log( "GvyInjAbob post event: " + eventId + " for booking: " + bookingNum);
    }

}