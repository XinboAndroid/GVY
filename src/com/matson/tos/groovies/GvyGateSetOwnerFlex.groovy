/*

Groovy code called to set owner

*/

import com.navis.apex.business.model.GroovyInjectionBase

public class GvyGateSetOwnerFlex  extends GroovyInjectionBase
{
    public void execute(inDao, api)
    {
        log("\nGvyGateSetOwnerFlex: --Executing Groovy Gate Task---")
        def tran = inDao.getTran();
        if(tran == null) return
        def unit = tran.getTranUnit();
        if(unit == null) return;
        def owner = unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId");
        unit.setFieldValue("unitFlexString13", owner);
    }
}