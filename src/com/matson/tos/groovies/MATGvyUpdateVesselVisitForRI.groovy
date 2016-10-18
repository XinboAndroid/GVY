import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.model.CarrierVisit
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.persistence.HibernateApi
import com.navis.inventory.business.units.Routing
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.road.business.model.RoadInspection
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Logger

/**
 * Created by BGopal on 10/27/2015.
 */
class MATGvyUpdateVesselVisitForRI extends AbstractGateTaskInterceptor implements EGateTaskInterceptor{
    public void execute(TransactionAndVisitHolder inDao){
        executeInternal(inDao);
        GroovyApi groovyApi = new GroovyApi();
        if (inDao == null ){
            LOGGER.warn("Transaction visit holder is null");
            return;
        }
        TruckTransaction truckTransaction = inDao.getTran();
        if (truckTransaction == null) {
            LOGGER.warn("Transaction is null");
            return;
        }
        Unit unit = inDao.getTran().getTranUnit();
        if (unit == null) {
            LOGGER.warn("Transaction unit is null");
            return;
        }
        String unitId = unit.getUnitId();
        Routing unitRouting = unit.getUnitRouting();
        if (unitRouting != null && truckTransaction.getTranDischargePoint1()!= null){
            unitRouting.setRtgPOD1(truckTransaction.getTranDischargePoint1());
        }
        String cmdyId="";
        if (unit.getUnitGoods() !=null && unit.getUnitGoods().getGdsCommodity()!=null  ){
            cmdyId = unit.getUnitGoods().getGdsCommodity().getCmdyId();
        }
        if (cmdyId!=null && cmdyId.equalsIgnoreCase("MULTISTOP SIT")) {
            CarrierVisit carrierVisit = truckTransaction.getTranCarrierVisit();
            if (carrierVisit != null) {
                if (LocTypeEnum.VESSEL.equals(carrierVisit.getCvCarrierMode())) {
                    UnitFacilityVisit unitFacilityVisit = unit.getUfvForFacilityLiveOnly(ContextHelper.getThreadFacility())
                    if (unitFacilityVisit != null) {
                        unitFacilityVisit.setUfvObCv(carrierVisit.getPrimaryKey());
                        HibernateApi.getInstance().saveOrUpdate(unitFacilityVisit);
                    }
                }
            }
        }
    }
    private static Logger LOGGER = Logger.getLogger(MATGvyUpdateVesselVisitForRI.class);
}
