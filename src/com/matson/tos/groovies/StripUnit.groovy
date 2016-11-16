import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.reference.*
import com.navis.inventory.business.units.*
import com.navis.framework.util.BizViolation
import com.navis.argo.ContextHelper;

/*
**********************************************************************
* Srno   Date	             Changer	 Change Description
* A1	 05/26/09	Steven Bauer	 Look unit at complex level.
* A2     05/29/09	Steven Bauer	 406 - No error on stripping empty unit.
* A3     06/02/09	Steven Bauer	 403 - Supress all updates before GetNV
* A4     08/14/09	Steven Bauer	 EP000100565, should have been using cmdyId not name.
**********************************************************************
*/
class StripUnit extends GroovyInjectionBase {
    public String execute(Map inParameters) {

        com.navis.argo.ContextHelper.setThreadExternalUser("ACETS");
        def ctrId = inParameters.get("equipment-id");
        log("StripUnit "+ctrId);
        try {
            def recorder = (String) inParameters.get("recorder");

            // Find the active UFV
            def fullUfv = null;
            try {
                // fullUfv = findActiveUfv(ctrId);
                // A1
                def unitLookup = getGroovyClassInstance("GvyUnitLookup");
                fullUfv = unitLookup.getUfvActiveInComplex(ctrId);
                if(fullUfv == null) throw new Exception("Could not find active unit");
            } catch ( BizViolation ex) {
                fail((new StringBuilder()).append("ERR_GVY_STRIP_001. Could not find unit: ").append(ctrId).toString());
            }
            // check if the commodity id is ok.
            def ufvUnit = fullUfv.getUfvUnit();
            if ( ufvUnit.isStorageEmpty()) {
                return
                //fail((new StringBuilder()).append("ERR_GVY_STRIP_002. Could not STRIP EMPTY unit: ").append(ctrId).toString());
            }

            // A3
            if(isStowplan(ufvUnit)) return;

            def stripGoods = ufvUnit.getUnitGoods();
            //Commodity check is not required because LCL cargo might not be having commodity some times hence UNIT_STRIP does not get recorded becuase of the below check.
            /* if ( stripGoods != null) {
                def stripComm = stripGoods.getGdsCommodity();
                if ( stripComm != null) {
                    def commId = stripComm.getCmdyId();
                    if ( commId.length() < 3)
                        fail((new StringBuilder()).append("ERR_GVY_STRIP_003. Could not STRIP unit: ").append(ctrId).append(" with COMMODITY code: ").append(commId).toString());

                    if ( !commId.substring(0, 3).equalsIgnoreCase( "AUT")
                        && !commId.substring(0, 3).equalsIgnoreCase( "CFS")
                        && !commId.substring(0, 3).equalsIgnoreCase( "COB")) {
                        fail((new StringBuilder()).append("ERR_GVY_STRIP_003. Could not STRIP unit: ").append(ctrId).append(" with COMMODITY code: ").append(commId).toString());
                    }
                } else {
            fail((new StringBuilder()).append("ERR_GVY_STRIP_003. Could not STRIP unit: ").append(ctrId).append(" withno  COMMODITY code. ").toString());
                }
            } else {
            fail((new StringBuilder()).append("ERR_GVY_STRIP_003. Could not STRIP unit: ").append(ctrId).append(" withno  COMMODITY code. ").toString());
            } */


            // Strp it, and get back the new full UFV and Unit
            def strippedUfv = stripUfvAndRecordEvent( fullUfv, null, "JF");
            def strippedUnit = strippedUfv.getUfvUnit();
            // get commodity code
            def unitGoods = strippedUnit.getUnitGoods();
            def unitComm = unitGoods.getGdsCommodity();
            if ( unitComm != null) {
                def commodityId = unitComm.getCmdyShortName();
                if ( commodityId.equalsIgnoreCase( "AUTOCON")) {
                    unitComm = Commodity.findOrCreateCommodity( commodityId);
                    unitComm.setCmdyShortName( "EMPTY CFS");
                } else {
                    unitComm = Commodity.findOrCreateCommodity( "MTYAUT");
                    unitComm.setCmdyShortName( "EMPTY AUTO");
                }
            } else {
                unitComm = Commodity.findOrCreateCommodity( "MTYAUT");
                unitComm.setCmdyShortName( "EMPTY AUTO");
            }

            unitGoods.setGdsCommodity(unitComm);

            // Record an event
            strippedUnit.recordUnitEvent(EventEnum.UNIT_STRIP, null, "Stripped by " + recorder);
            return "done via Groovy, unit is: " + strippedUnit;

        } catch ( Exception ex) {
            fail((new StringBuilder()).append(ex.toString()).append("ERR_GVY_STRIP_999. Could not STRIP unit: ").append(ctrId).toString());
            ex.printStackTrace();
        }
    }

    private boolean isStowplan(unit) {
        def remark = unit.unitRemark;
        if(remark == null) return false;
        return remark.startsWith("Stowplan Data");
    }
}

