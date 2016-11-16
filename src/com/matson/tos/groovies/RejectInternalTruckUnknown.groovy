import java.io.Serializable;

import com.navis.argo.ArgoEntity;
import com.navis.argo.ArgoField;
import com.navis.argo.ContextHelper;
import com.navis.argo.business.xps.model.Che;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.util.internationalization.PropertyKey;
import com.navis.framework.util.internationalization.PropertyKeyFactory;
import com.navis.framework.util.message.MessageLevel;
import com.navis.road.business.model.TruckVisitDetails;
import com.navis.road.business.util.RoadBizUtil;
import com.navis.road.business.workflow.TransactionAndVisitHolder;

public class RejectInternalTruckUnknown {

    public static String BEAN_ID = "rejectInternalTruckUnknown";
    public static PropertyKey INTERNAL_TRUCK_ID_UNKNOWN = PropertyKeyFactory.valueOf("gate.internal_truck_id_unknown");

    public void execute(TransactionAndVisitHolder dao, api) {

        TruckVisitDetails tv = dao.getTv();
        if (tv == null) {
            return;
        }

        String truckLicenseNbr = tv.getTvdtlsTruckLicenseNbr();
        api.log('TV DETAILS, LIC #: ' + truckLicenseNbr)
        api.log('Event' + dao.properties);
        api.log('TV' + tv.properties)
        api.log('Tran' + dao.tran.properties)


        if (truckLicenseNbr == null) {
            return;
        }

        Serializable yardKey = ContextHelper.getThreadYardKey();

        Che xpeChe = findCheByShortName(truckLicenseNbr, yardKey);

        if (xpeChe == null || xpeChe.getCheKind() != INTERNAL_TRUCK) {
            RoadBizUtil.appendMessage(MessageLevel.SEVERE, INTERNAL_TRUCK_ID_UNKNOWN, truckLicenseNbr);
        }
    }

    private Che findCheByShortName(String shortName, Serializable yardKey) {
        DomainQuery dq = QueryUtils.createDomainQuery(ArgoEntity.CHE)
                .addDqPredicate(PredicateFactory.eq(ArgoField.CHE_YARD, yardKey))
                .addDqPredicate(PredicateFactory.eq(ArgoField.CHE_SHORT_NAME, shortName));

        return (Che) HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
    }

    private static final Long INTERNAL_TRUCK = 3L;
}

