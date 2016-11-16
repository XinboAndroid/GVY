import com.navis.argo.business.api.ArgoUtils;
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.InventoryEntity;
import com.navis.inventory.business.api.UnitField;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.UnitFacilityVisit;

import org.apache.log4j.Logger;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: lcrouch
 * Date: 8/31/12
 * Time: 11:57 AM
 * To change this template use File | Settings | File Templates.
 */
public class GvySetUnitsSparcsVisibilityJob {
    public String execute(Map inParameters) {
        final Date earliestDate = new Date(ArgoUtils.timeNowMillis() - ArgoUtils.MILLIS_PER_HOUR);
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.in(UnitField.UFV_VISIT_STATE, Unit.HISTORY_STATES))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIBLE_IN_SPARCS, Boolean.TRUE))
                .addDqPredicate(PredicateFactory.ne(UnitField.UFV_POS_LOC_TYPE, LocTypeEnum.VESSEL))
                .addDqPredicate(PredicateFactory.isNotNull(UnitField.UFV_TIME_COMPLETE))
                .addDqPredicate(PredicateFactory.le(UnitField.UFV_TIME_COMPLETE, earliestDate))
                .setDqMaxResults(200);
        dq.setRequireTotalCount(false);
        List<UnitFacilityVisit> ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        for (Iterator<UnitFacilityVisit> iterator = ufvList.iterator(); iterator.hasNext();) {
            UnitFacilityVisit ufv = iterator.next();
            ufv.setUfvVisibleInSparcs(Boolean.FALSE);
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("making " + ufv.getUfvUnit().getUnitId() + " invisible in SPARCS");
            }
        }
    }
    private static final Logger LOGGER = Logger.getLogger(GvySetUnitsSparcsVisibilityJob.class);

}