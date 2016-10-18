import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.units.Unit
import com.navis.road.business.model.RoadInspection
import com.navis.road.business.model.TruckTransaction;
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Logger

/**
 * Created by BGopal on 10/27/2015.
 */
class MATGvyUpdateInspectionOnOutGate extends AbstractGateTaskInterceptor implements EGateTaskInterceptor{
    public void execute(TransactionAndVisitHolder inDao){
        executeInternal(inDao);
        if (inDao == null ){
            LOGGER.warn("Transaction visit holder is null");
            return;
        }
        if (inDao.getTran() == null) {
            LOGGER.warn("Transaction is null");
            return;
        }
        TruckTransaction transaction = inDao.getTran();
        Unit unit = transaction.getTranUnit();
        if (unit == null) {
            LOGGER.warn("Transaction unit is null");
            return;
        }
        String unitId = unit.getUnitId();

        List<RoadInspection> roadInspectionList = RoadInspection.findInspectionsForEq(unitId, 100000L);
        if (roadInspectionList.size() >0) {
            for (RoadInspection roadInspection: roadInspectionList) {
                HibernateApi.getInstance().delete(roadInspection);
            }
        }
        RoadInspection newRoadInspection = RoadInspection.createInspection(unitId, null);
        if (newRoadInspection != null) {
            newRoadInspection.setInspGensetId(inDao.getTran().getCtrAccessoryId());
            newRoadInspection.setInspEqGrade(unit.getUnitEquipment(unitId).getUeEquipmentState().getEqsGradeID());
            if (transaction.getTranChassis()!=null) {
                newRoadInspection.setInspUnitFlexString04(transaction.getTranChassis().getEqIdFull());
            }
            //newRoadInspection.setInspUnitFlexString12(unit.getUnitFlexString12());
            // newRoadInspection.setInspUnitFlexString15(unit.getUnitFlexString15());
            //newRoadInspection.set
            HibernateApi.getInstance().saveOrUpdate(newRoadInspection);
        }
    }
    private static Logger LOGGER = Logger.getLogger(MATGvyUpdateInspectionOnOutGate.class);
}
