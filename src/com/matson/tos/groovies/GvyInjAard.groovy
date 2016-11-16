/*
**********************************************************************
* Srno   Date	             Changer	 Change Description
* A1     12/29/08       Glenn Raposo	 Unit Field Tracker code Added
* A2     05/22/09	Steven Bauer	 Lookup unit in complex
* A3     06/02/09	Steven Bauer	 403 - Supress all updates before GetNV
**********************************************************************
*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.reference.*
import com.navis.inventory.business.units.*
import com.navis.framework.util.BizViolation
import com.navis.services.business.event.GroovyEvent
import com.navis.argo.util.FieldChangeTracker
import com.navis.inventory.InventoryField;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdList;
import com.navis.argo.business.api.GroovyApi
import java.lang.Thread

class GvyInjAard extends GroovyInjectionBase {
    public String execute(Map inParameters) {

        def ctrId = (String) inParameters.get("equipment-id");
        def vesvoyArd = (String) inParameters.get( "vesvoy");
        def recDateArd = (String) inParameters.get( "autoRecDate");

        com.navis.argo.ContextHelper.setThreadExternalUser("ACETS");

        // Find the UFV
        log( "Starting ARD Injection process.");
        def ufv;
        def unit;
        def tracker;
        GroovyApi gvyApi = new GroovyApi();
        def unitLookup = gvyApi.getGroovyClassInstance("GvyUnitLookup");

        try {
            Thread.sleep( 5000);
            //ufv = findActiveUfv(ctrId);
            // A2
            ufv =  unitLookup.getUfvActiveInComplex(ctrId);
            unit = ufv.getUfvUnit();
            //A1-Tracker Change
            def gvyUnitFldTracker = gvyApi.getGroovyClassInstance("GvyUnitFieldTracker");
            tracker = gvyUnitFldTracker.createFieldChangeTracker(unit)

        } catch ( Exception ex) {
            ex.printStackTrace()
            fail((new StringBuilder()).append(ex.toString()).append(" ERR_GVY_ARD_001. Could not find unit: ").append( ctrId).toString());
        }

        //A3
        if(isStowplan(unit)) return;

        def cmdyName = unit.getFieldValue("unitGoods.gdsCommodity.cmdyShortName");
        log( "CmdyName=" + cmdyName);
        if ( cmdyName != null && ! cmdyName.contains( "AUTO")) {
            fail( "ERR_GVY_ARD_002. ARD commodity code: " + cmdyName + " is not AUTO.");
        }

        def obDecVesvoy = ufv.getUfvIntendedObCv().toString();

        //def obDecVesvoy = unit.getFieldValue( "unitActiveUfv.ufvIntendedObCv");
        log( "obDecVesvoy=" + obDecVesvoy);
        if ( obDecVesvoy != vesvoyArd) {
            fail( "ERR_GVY_ARD_003. OB Carrier in N4: " + obDecVesvoy + " does not match ARD vesvoy: " + vesvoyArd);
        }


        unit.setFieldValue("unitGoods.gdsBlNbr", recDateArd);
        log( "post ARD event.");

        //A1-Tracker Change
        def changes = tracker.getChanges(unit);
        if(changes != null && changes.getFieldChangeCount() != 0) {
            unit.recordUnitEvent(com.navis.argo.business.atoms.EventEnum.UNIT_PROPERTY_UPDATE,changes , "Field Update ARD Data");
        }

        def event = new GroovyEvent( null, unit);
        event.postNewEvent( "ARD", "ACETS");
    }

    private boolean isStowplan(unit) {
        def remark = unit.unitRemark;
        if(remark == null) return false;
        return remark.startsWith("Stowplan Data");
    }
}