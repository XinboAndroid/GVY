import com.navis.inventory.business.api.UnitManager;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.api.UnitField;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.argo.business.api.GroovyApi;
import com.navis.framework.business.Roastery;
/*
* Get a list of all the Advised Chassis' and deletes them
*/
class GvyAdvisedChassisCleanupJob  {
    public String execute(Map inParameters) {

        GroovyApi gvyApi = new GroovyApi();

        gvyApi.logWarn("Advised chassis::starting...");

        DomainQuery dq = QueryUtils.createDomainQuery("Unit");
        dq.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CURRENT_UFV_TRANSIT_STATE, UfvTransitStateEnum.S10_ADVISED));
        dq.addDqPredicate(PredicateFactory.like(UnitField.UNIT_PRIMARY_EQTYPE_ID, "C%"));

        def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        def iter = list.iterator();
        gvyApi.logWarn("Advised chassis: " + (list!=null ? list.size() : "0"));
        while(iter.hasNext()) {
            UnitManager unitManager = (UnitManager) Roastery.getBean(UnitManager.BEAN_ID);
            try {
                Unit chsUnit = (Unit) iter.next();
                unitManager.purgeUnit(chsUnit);
                gvyApi.logWarn("Delete Advised Chassis: "+chsUnit.getUnitId()+" /inUnit:"+chsUnit);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}