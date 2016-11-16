import com.navis.argo.ArgoEntity
import com.navis.argo.ArgoField
import com.navis.argo.business.model.Quay
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory

/*
* Copyright (c) 2007 Navis LLC. All Rights Reserved.
* $Id: QuayCleanup.groovy,v 1.1 2016/10/05 21:10:21 vnatesan Exp $
*/
class QuayCleanup {
    public void execute(Map inParameters) {
        DomainQuery dq = QueryUtils.createDomainQuery(ArgoEntity.QUAY);
        dq.addDqPredicate(PredicateFactory.eq(ArgoField.QUAY_ID, "52"));
        dq.addDqPredicate(PredicateFactory.eq(ArgoField.PRTL_GKEY, 256424));
        List quays = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        for (Quay quay in quays) {
            try {
                HibernateApi.getInstance().delete(quay, true);
            } catch (Exception e) {
                println("could not delete quay: " + e)
            }
        }
    }
}