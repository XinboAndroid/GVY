/*

Groovy code called from the outgate form to prevent from outgating MG with chassis.


Amine Nebri, anebri@navis.com - June 25 2008
Steven Bauer - Nov 11 2008
Changed from using the flex field to Container status rules.

*/

import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.road.business.util.RoadBizUtil
import com.navis.framework.util.BizFailure

public class OutGateCheck extends GroovyInjectionBase
{
    public void execute(inDao, api)
    {
        log("Out gate check!!!");
        // We retrieve the transaction object
        def tran = inDao.getTran()

        if (tran == null) return

        // We exit here if no chassis accessory number was entered
        if (tran.getTranChsAccNbr() == null) return

        // We retrieve everything we need to get to ufvFlexString01
        def unit = tran.getTranUnit()

        // We now retrieve the unit facility visit
        def ufv = tran.getTranUfv()

        if (ufv == null) return


        /* Change from Joce (11/17/2008)
               Using Container status not chassisType
        */
        // Always allowed if it has an MG.
        def holdsList = unit.getFieldValue("unitAppliedHoldOrPermName");
        if(holdsList != null && holdsList.indexOf("MGOK") != -1) return;

        // Can not outgate an MG with a non-reefer
        def ue = unit.unitPrimaryUe;
        if(ue != null) {
            def eq = ue.ueEquipment;
            if(eq!= null && !eq.eqIsTemperatureControlled) {
                RoadBizUtil.getMessageCollector().appendMessage(BizFailure.create("MG cannot be outgated with a dry container"));
            }
        }

        // Can not outgate MG for XMAS trees
        def commodityName =  unit.getFieldValue("unitGoods.gdsCommodity.cmdyId");
        if(commodityName != null && (commodityName.contains("XMASTREE") ||  commodityName.contains("XMAS40") )) {
            RoadBizUtil.getMessageCollector().appendMessage(BizFailure.create("MG cannot be outgated with a XMas Tree container"))
        }

        // Can not outgate MG for MTYs
        if(unit.unitFreightKind!= null && unit.unitFreightKind.name.equals("MTY")) {
            RoadBizUtil.getMessageCollector().appendMessage(BizFailure.create("MG cannot be outgated with a empty container"))
        }

    }

}