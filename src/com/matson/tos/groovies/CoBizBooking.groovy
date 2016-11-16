/*
**********************************************************************
* Srno   Date	        Changer	 	 Change Description
* A1    10/12/2011  Glenn Raposo	 updated Assign Booking Method for TOS2.1
**********************************************************************
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderManagerPea
import com.navis.argo.business.reference.*
import com.navis.inventory.business.units.*
import com.navis.framework.util.BizViolation
import com.navis.argo.business.api.GroovyApi
import com.navis.services.business.event.GroovyEvent

public class CoBizBooking {
    public String process(Object unit) {


        def ctrId = unit.getFieldValue("unitId");
        def bl_nbr = unit.getFieldValue("unitFlexString09");
        def dobCvId = unit.getFieldValue("unitFlexString10");
        def unitNotes = unit.getFieldValue("unitRemark");
        unit.setFieldValue("unitFlexString09", "");
        unit.setFieldValue("unitFlexString10", "");

        try {

            // check if the unit is empty.
            if ( unit.isStorageEmpty())
                return (new StringBuilder()).append("ERR_GVY_COBIZ_BK_001. Could not ASSIGN CO BIZ BOOKING to EMPTY unit: ").append(ctrId).toString();

            // find booking
            def injBase = new GroovyInjectionBase();
            def facility = injBase.getFacility();
            def cv = CarrierVisit.findVesselVisit( facility, dobCvId);
            if ( cv == null) {
                return "ERR_GVY_COBIZ_BK_002. Could not find the carrier visit: " + dobCvId;
            }

            def bizScope = ScopedBizUnit.findScopedBizUnit( "MAT", BizRoleEnum.LINEOP);
            if ( bizScope == null) {
                return "ERR_GVY_COBIZ_BK_003. Could not find the business unit: MAT";
            }

            def booking = Booking.findBookingByUniquenessCriteria( bl_nbr, bizScope, cv);
            if ( booking == null) {
                return "ERR_GVY_COBIZ_BK_004. Could not find booking: " + bl_nbr;
            }

            // set CATEGORY
            unit.setUnitCategory(UnitCategoryEnum.EXPORT);
            // set BOOKING NUM and Carrier info
            unit.setFieldValue("unitGoods.gdsBlNbr", bl_nbr);
            unit.setUnitRemark( unitNotes);

            def eqoMgr = new EquipmentOrderManagerPea();
            //eqoMgr.assignExportBookingToUnit( booking, unit);
            eqoMgr.assignExportBookingToUnit( booking, null, unit); //Update Method for TOS2.1 -- A16

            GroovyEvent event = new GroovyEvent( null, unit);
            //event.postNewEvent( "UNIT_ROLL", unitNotes);
            event.postNewEvent( "ROUTE_COBIZ", unitNotes);

            // Record an event
            return "done via Groovy, unit=" + ctrId + "booking=" + bl_nbr + " cv=" + dobCvId;

        } catch ( Exception ex) {
            return ((new StringBuilder()).append("ERR_GVY_COBIZ_BK_999. Could not fire COBIZ_BOOKING event on unit: ").append(ctrId).append("\nSN4 Exception: ").append(ex.toString()).toString());
        }
    }
}

