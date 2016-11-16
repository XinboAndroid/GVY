import com.navis.argo.ContextHelper;
import com.navis.argo.business.atoms.UnitCategoryEnum;
import com.navis.argo.business.reference.Container;
import com.navis.framework.business.Roastery;
import com.navis.framework.util.BizViolation;
import com.navis.framework.util.message.MessageCollector;
import com.navis.framework.util.message.MessageLevel;
import com.navis.inventory.business.api.UnitFinder;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.road.RoadPropertyKeys;
import com.navis.road.business.adaptor.IGateTaskAdaptor;
import com.navis.road.business.atoms.TranSubTypeEnum;
import com.navis.road.business.model.TruckAction;
import com.navis.road.business.model.TruckTransaction;
import com.navis.road.business.util.RoadBizUtil;
import com.navis.road.business.workflow.TransactionAndVisitHolder;
import java.util.Collection;
import java.util.Iterator;

public class GvyRejectTranAdvisedUnitMismatch
        implements IGateTaskAdaptor
{

    public GvyRejectTranAdvisedUnitMismatch()
    {
    }

    public void execute(TransactionAndVisitHolder inDao)
            throws BizViolation
    {
        println("GvyRejectTranAdvisedUnitMismatch");
        TruckTransaction tran = inDao.getTran();
        if(tran != null)
        {
            TranSubTypeEnum subType = tran.getTranSubType();
            Container ctr = tran.getTranContainer();
            println("GvyRejectTranAdvisedUnitMismatch subType "+subType + "::"+ctr);
            if(ctr != null)
            {
                UnitFinder unitFinder = (UnitFinder)Roastery.getBean("unitFinder");
                Collection unitList = unitFinder.findAllUnitsUsingEq(ContextHelper.getThreadComplex(), ctr);
                if(unitList == null) return;
                Iterator iterator = unitList.iterator();
                while(iterator.hasNext()) {
                    Unit unit = (Unit)iterator.next();
                    println("GvyRejectTranAdvisedUnitMismatch unit "+unit);
                    if(unit != null && unit.getUnitCategory() != null && (UnitVisitStateEnum.ADVISED.equals(unit.getUnitVisitState()) || UnitVisitStateEnum.ACTIVE.equals(unit.getUnitVisitState())))
                    {

                        UnitFacilityVisit ufv = unit.getUfvForFacilityNewest(ContextHelper.getThreadFacility());
                        println("GvyRejectTranAdvisedUnitMismatch ufv "+ufv);
                        if(ufv != null && tran.isReceival() && !tran.isAdvisedUnitCompatible(subType, unit) && ufv.isTransitStateAtMost(UfvTransitStateEnum.S20_INBOUND)) {
                            String[] fields = [ctr.getEqIdFull(), unit.getUnitCategory().getName(), subType.getName()];
                            RoadBizUtil.getMessageCollector().appendMessage(MessageLevel.SEVERE, RoadPropertyKeys.GATE__UNIT_PREADVISED_TRAN_TYPE_MISMATCH, null,  fields
                            );
                        }
                    }
                }
            }
        }
    }

    public static String BEAN_ID = "rejectTranAdvisedUnitMismatch";

    @Override
    public TruckAction getDefaultAction() {
        // TODO Auto-generated method stub
        return null;
    }

}