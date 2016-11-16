/*
* Srno  Doer  Date       comment
* A1    GR    09/12/11   Update booking method for TOS2.1
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.FreightKindEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.orders.business.eqorders.Booking
import com.navis.orders.business.eqorders.EquipmentOrderManagerPea
import com.navis.orders.business.api.EquipmentOrderManager
import com.navis.framework.business.Roastery;
import com.navis.argo.business.reference.*
import com.navis.inventory.business.units.*
import com.navis.framework.util.BizViolation
import com.navis.argo.business.api.GroovyApi
import com.navis.services.business.event.GroovyEvent

public class MtyFructoseStripUnit {
    public String mtyFrucroseProc(Object inEvent,Object unit) {

        com.navis.argo.ContextHelper.setThreadExternalUser(inEvent.event.evntAppliedBy+":FRUCTOSE_MTY");
        def ctrId = unit.getFieldValue("unitId");
        def bl_nbr = unit.getFieldValue("unitFlexString09");
        def dobCvId = unit.getFieldValue("unitFlexString10");
        def unitNotes = ""; //unit.getFieldValue("unitRemark");
        unit.setFieldValue("unitFlexString09", "");
        unit.setFieldValue("unitFlexString10", "");
        def f09 = unit.getFieldValue("unitFlexString09");
        def f10 = unit.getFieldValue("unitFlexString10");
        try {
            //def recorder = (String) inParameters.get("recorder");

// find booking
            def injBase = new GroovyInjectionBase();
            //injBase.log( "unitID= " + ctrId + " bl_nbr= " + bl_nbr + " dobCvId= " + dobCvId);
            def facility = injBase.getFacility();
            def cv = CarrierVisit.findVesselVisit( facility, dobCvId);
            if ( cv == null) {
                return "ERR_MTY_F_001. Could not find the carrier visit: " + dobCvId;
            }

            def bizScope = ScopedBizUnit.findScopedBizUnit( "MAT", BizRoleEnum.LINEOP);
            if ( bizScope == null) {
                return "ERR_MTY_F_002. Could not find the business unit: MAT";
            }

            def booking = Booking.findBookingByUniquenessCriteria( bl_nbr, bizScope, cv);
            if ( booking == null) {
                return "ERR_MTY_F_003. Could not find booking: " + bl_nbr;
            }

            //get Freight Kind from booking
            def freightKindBkg = booking.getFieldValue("eqoEqStatus");
            injBase.log( "Booking freight kind = " + freightKindBkg);


            // check if the commodity id is ok.
            if ( unit.isStorageEmpty())
                return (new StringBuilder()).append("ERR_GVY_MTY_F_004. Could not STRIP EMPTY unit: ").append(ctrId).toString();

            // find the facilityVisit
            def fullUfv = unit.getUfvForFacilityNewest(facility);
            if ( fullUfv == null) {
                return "ERR_MTY_F_005. Could not find facility visit for unit:" + ctrId;
            }

            // Strip it, and get back the new full UFV and Unit
            def strippedUfv = null;
            try {
                strippedUfv = injBase.stripUfvAndRecordEvent( fullUfv, null, "Gvy MTY Fructose proc");
            } catch ( Exception stripEx) {
                return "ERR_GVY_MTY_F_006. Could not STRIP unit: " + ctrId;
            }
            def strippedUnit = strippedUfv.getUfvUnit();

            if ( strippedUnit == null)
                return "ERR_GVY_MTY_F_007. Could not get UFV unit after STRIP unit: " + ctrId;

//set FrightKind
            if ( !freightKindBkg.equals( FreightKindEnum.MTY)) {
                strippedUnit.setUnitFreightKind(FreightKindEnum.FCL);
            }
            // set CATEGORY
            strippedUnit.setUnitCategory(UnitCategoryEnum.EXPORT);

            EquipmentOrderManager manager = (EquipmentOrderManager)Roastery.getBean("equipmentOrderManager");
            manager.assignExportBookingToUnit(booking, null, strippedUnit); //Update Method for TOS2.1 -- A1
            //def eqoMgr = new EquipmentOrderManagerPea();
            //eqoMgr.assignExportBookingToUnit( booking, strippedUnit);

            // set BOOKING NUM and Carrier info
            strippedUnit.setFieldValue("unitGoods.gdsBlNbr", bl_nbr);
            // set back to MTY
            strippedUnit.setUnitFreightKind(FreightKindEnum.MTY);

//def api = new GroovyApi();
            def orderItemVal;

            def ue = strippedUnit.getUnitPrimaryUe();
            if (ue.getUeDepartureOrderItem() != null) {
                orderItemVal = "OrderItem is not null";
                ue.getUeDepartureOrderItem().getEqboiOrder().setEqboNbr(bl_nbr);
            } else {
                orderItemVal = "OrderItem is null";
            }

            //strippedUnit.recordUnitEvent(EventEnum.UNIT_STRIP, null, "Stripped by MtyFructoseStripUnit");


            GroovyEvent event = new GroovyEvent( null, strippedUnit);
            event.postNewEvent( "FRUCTOSE_LOAD", unitNotes);


            def routing = strippedUnit.getUnitRouting();
            routing = booking.getRoutingInfo();


            if ( routing == null)
                routing = new Routing();
            def rdcv = booking.getEqoVesselVisit();
            routing.setRtgDeclaredCv( rdcv);

            def  pod1 = booking.getEqoPod1();
            routing.setRtgPOD1( pod1);

            def pol = booking.getEqoPol();
            routing.setRtgPOL( pol);



            strippedUnit.updateUnitRouting(routing);
            strippedUnit.getUnitGoods().setGoodsDestination( booking.getEqoDestination());
            strippedUnit.getUnitGoods().updateShipper( booking.getShipperAsString());
            strippedUnit.getUnitGoods().updateConsignee( booking.getConsigneeAsString());
            strippedUnit.setUnitRemark( "Booking number: " + bl_nbr + " vessel visit: " + dobCvId);
            // Record an event
            return "done via Groovy, booking=" + f09 + " cv=" + f10;

        } catch ( Exception ex) {
            return ((new StringBuilder()).append("ERR_GVY_MTY_F_999. Could not fire MTY FRUCTOSE event on unit: ").append(ctrId).append("\nSN4 Exception: ").append(ex.toString()).toString());
        }
    }
}

