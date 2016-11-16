/*
**********************************************************************
* Srno   Date	             Changer	 Change Description
* A1     12/29/08       Glenn Raposo	 Unit Field Tracker code Added
* A2     04/29/09       Steven Bauer     Added reroute on return to storage.
* A3	 05/26/09	Steven Bauer	 Look unit at complex level.
* A4     06/02/09	Steven Bauer	 403 - Supress all updates before GetNV
* A5   GR   12/13/11  Update HOLD FOR LNK
**********************************************************************
*/
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.inventory.business.units.*
import com.navis.argo.business.atoms.*;
import com.navis.services.business.event.GroovyEvent
import com.navis.argo.business.reference.*
import com.navis.orders.business.eqorders.EquipmentOrderManagerPea
import com.navis.orders.business.eqorders.Booking
import com.navis.argo.ContextHelper;
import com.navis.argo.business.model.CarrierVisit;
import com.navis.argo.business.api.ServicesManager;
import com.navis.framework.business.Roastery;
import com.navis.argo.business.api.GroovyApi



class GvyInjAulk extends GroovyInjectionBase {
    public String execute(Map inParameters) {
        com.navis.argo.ContextHelper.setThreadExternalUser("ACETS");

        final String  emailFrom = '1aktosdevteam@matson.com'
        final String emailTo = "1aktosdevteam@matson.com";
        def note = "\n\nPlease fix either the booking or unit and review the routing after correction";

        def inj = new GroovyInjectionBase();
        def emailSender = inj.getGroovyClassInstance("EmailSender");

        def ctrId = (String) inParameters.get("equipment-id");
        def recorder = (String) inParameters.get("recorder");

        // find the unit
        def ufv;
        def unit;
        //A1- tracker
        def tracker;
        GroovyApi gvyApi = new GroovyApi();
        def unitLookup = gvyApi.getGroovyClassInstance("GvyUnitLookup");
        try {
            //ufv = findActiveUfv(ctrId);
            //A6
            ufv = unitLookup.getUfvActiveInComplex(ctrId);
            if(ufv == null) {
                throw new Exception("Could not find active unit");
            }
            unit = ufv.getUfvUnit();
            //A1- Tracker Change
            def gvyUnitFldTracker = gvyApi.getGroovyClassInstance("GvyUnitFieldTracker");
            tracker = gvyUnitFldTracker.createFieldChangeTracker(unit)
        } catch ( Exception ex) {
            emailSender.custSendEmail(emailFrom,emailTo," ULK error for Unit " +ctrId ,"Could not find active unit: " + ctrId+note);
            fail("ERR_GVY_ULK_001. Could not find active unit: " + ctrId);
        }

        if ( unit == null) {
            emailSender.custSendEmail(emailFrom,emailTo," ULK error for Unit " +ctrId ,"Could not find active unit: " + ctrId+note);
            fail("ERR_GVY_ULK_001. Could not find active unit: " + ctrId);
        }

        //A4
        if(isStowplan(unit)) return;

        def transitState = ufv.getUfvTransitState();
        def obCarrierMode = unit.getFieldValue("unitActiveUfv.ufvActualObCv.cvCarrierMode");
        def obCarrier = unit.getFieldValue( "unitActiveUfv.ufvActualObCv.cvId");
        log( "obCarrierMode = " + obCarrierMode);
        log( "obCarrier = " + obCarrier);

        if ( !(transitState.equals( UfvTransitStateEnum.S50_ECOUT) ||
                transitState.equals( UfvTransitStateEnum.S30_ECIN) ||
                transitState.equals( UfvTransitStateEnum.S20_INBOUND) ||
                transitState.equals( UfvTransitStateEnum.S40_YARD))) {

            if ( ((transitState.equals( UfvTransitStateEnum.S70_DEPARTED) ||
                    transitState.equals( UfvTransitStateEnum.S60_LOADED)) &&
                    !isBarge( obCarrier))) {

                emailSender.custSendEmail(emailFrom,emailTo," ULK error for Unit " +ctrId ,"The unit: " + ctrId +
                        " TransitState is not in DEPARTURED or LOAD and OB Carrier is not barge." +
                        " And TransitState is not in YARD or INBOUND or EC/In or EC/out."+note);
                fail( "ERR_GVY_ULK_002. The unit: " + ctrId +
                        " TransitState is not in DEPARTURED or LOAD and OB Carrier is not barge." +
                        " And TransitState is not in YARD or INBOUND or EC/In or EC/out.");
            }
        }


        if ( transitState.equals( UfvTransitStateEnum.S60_LOADED) && LocTypeEnum.VESSEL.equals( obCarrierMode)) {
            emailSender.custSendEmail(emailFrom,emailTo," TransitState is LOADED and OB Carrier is VESSEL."+note);
            fail( "ERR_GVY_ULK_003. The unit: " + ctrId + " TransitState is LOADED and OB Carrier is VESSEL.");
        }

        if ( UnitCategoryEnum.EXPORT.equals(unit.getUnitCategory())) {
            // find booking
            if(unit.unitFreightKind.name.equals("MTY")) {
                // Save a reroute mty.
                def declaredCv = null;
                def intendedCv = null;
                def actualCv = null;
                def pol = null;
                def opl = null;

                if(unit.unitRouting != null) {
                    declaredCv = unit.unitRouting.getRtgDeclaredCv();
                    pol =  unit.unitRouting.rtgPOL;
                    opl =  unit.unitRouting.rtgOPL;
                }
                if(unit.unitActiveUfv != null) {
                    intendedCv = unit.unitActiveUfv.ufvIntendedObCv;
                    actualCv   = unit.unitActiveUfv.ufvActualObCv;
                }

                def servicesMgr = (ServicesManager)Roastery.getBean("servicesManager");
                servicesMgr.applyGuardedPermission( "RTN_TO_STORAGE", unit, null, null, "ULK release");
                unit.returnToStorageUnit();

                if(declaredCv != null) unit.unitRouting.setRtgDeclaredCv(declaredCv);
                if(intendedCv != null) unit.unitActiveUfv.ufvIntendedObCv = intendedCv;
                if(actualCv   != null) unit.unitActiveUfv.ufvActualObCv = actualCv;
                if(pol != null) unit.unitRouting.rtgPOL = pol;
                if(opl != null) unit.unitRouting.rtgOPL = opl;

            } else {
                def servicesMgr = (ServicesManager)Roastery.getBean("servicesManager");
                servicesMgr.applyHold("HOLD FOR LNK", unit, null, null, "ULK BKG hold");
            }

        }

        unit.setFieldValue("unitGoods.gdsBlNbr", "CSR_ACTION_REQUIRED");

        def shipper = ScopedBizUnit.findOrCreateScopedBizUnit( "CSACTION", BizRoleEnum.SHIPPER);
        //unit.getUnitGoods().updateShipper( shipper);

        //unit.setFieldValue("unitGoods.gdsDestination", ContextHelper.getThreadFacility().getFcyId());

        Routing rtg = unit.getUnitRouting();

        if ( rtg == null) {
            emailSender.custSendEmail(emailFrom,emailTo," Can not find routing info for unit: " + ctrId +note);
            fail( "ERR_GVY_ULK_007. Can not find routing info for unit: " + ctrId);
        }
        def rtgPoint = RoutingPoint.findRoutingPoint( ContextHelper.getThreadFacility().getFcyId());
        //rtg.setRtgPOD1( rtgPoint);

        //unit.setFieldValue("unitRemark", "ACETS: " + recorder);
        def event = new GroovyEvent( null, unit);
        //A1 - Tracker Change
        def changes = tracker.getChanges(unit);
        if(changes != null && changes.getFieldChangeCount() != 0) {
            unit.recordUnitEvent(com.navis.argo.business.atoms.EventEnum.UNIT_PROPERTY_UPDATE,changes , "Field Updated ULK Data");
        }
        event.postNewEvent( "ULK", "ACETS: " + recorder);
    }

    private boolean isBarge( String vesvoy) {
        //def gvyBaseClass = new GroovyInjectionBase()
        def gvyUtil = getGroovyClassInstance("GvyCmisUtil");
        def vType = gvyUtil.getVesselClassType( vesvoy);
        return vType == "BARGE" ? true : false;
    }

    private boolean isStowplan(unit) {
        def remark = unit.unitRemark;
        if(remark == null) return false;
        return remark.startsWith("Stowplan Data");
    }

}