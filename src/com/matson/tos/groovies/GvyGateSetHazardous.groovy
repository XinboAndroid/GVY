/*

Groovy code called to check EIT

*/

import com.navis.apex.business.model.GroovyInjectionBase

public class GvyGateSetHazardous  extends GroovyInjectionBase
{
    public void execute(inDao, api)
    {
        log("\nGvyGateSetHazardous: --Executing Groovy Gate Task---")
        //log("Properties="+inDao.properties);
        //log("tv="+inDao.tv.properties);
        def tran = inDao.getTran();
        //log("tran="+tran.properties);
        if(tran.tranIsHazard) {
            // Get Units
            def unit = tran.getTranUnit();
            if(unit != null && unit.getUnitGoods() != null) {
                def hazardItem = unit.getUnitGoods().attachHazard("X", "");
            }
        }


    }
}