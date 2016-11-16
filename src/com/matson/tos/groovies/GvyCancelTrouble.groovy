import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.portal.QueryUtils;
import com.navis.road.RoadField;


/*
* Get a list of all the Trouble Gate transactions and close them
*/
class GvyCancelTrouble {
    public String execute(Map inParameters) {
        return;
        def gvyBaseClass = new GroovyInjectionBase()

        DomainQuery dq = QueryUtils.createDomainQuery("TruckTransaction");

        dq.addDqPredicate(PredicateFactory.eq(RoadField.TRAN_STATUS,"TROUBLE" ));
        def list = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        def iter = list.iterator();

        gvyBaseClass.log("Cancel "+list.size()+" transactions");
        while(iter.hasNext()) {
            def tran = iter.next();
            try {
                tran.cancelTransaction();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }
}