import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.inventory.business.units.*
import com.navis.framework.util.BizViolation
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.framework.business.Roastery
import com.navis.inventory.business.api.UnitFinder
import com.navis.argo.business.reference.Container
import com.navis.argo.ContextHelper


// Flush the unit before using its ufv - to avoid deadlock

class FlushUnit{
    public static String BEAN_ID = "FlushUnit"
    public void execute(TransactionAndVisitHolder dao, api) {

        TruckTransaction tran = dao.tran
        Container ctr = tran.tranContainer

        if (!ctr)
            return

        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID)

        Unit unit = unitFinder.findActiveUnit(ContextHelper.getThreadComplex(), ctr)

        if (!unit)
            unit = unitFinder.findAdvisedUnitByLandModes(ContextHelper.getThreadComplex(), ctr)

        if (!unit)
            return

        // update the unit's flex-string and flush it

        String str = unit.getUnitFlexString15()
        if(str == null || "touchUnit".equals(str))
            unit.setUnitFlexString15("flushUnit");
        else
            unit.setUnitFlexString15("touchUnit");

        Roastery.getHibernateApi().flush();
    }
}