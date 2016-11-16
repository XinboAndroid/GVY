import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper


import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;

import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import com.navis.argo.ArgoField;
import com.navis.road.business.model.*;
import java.util.Calendar;

import com.navis.argo.business.model.Facility;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdFactory;
import com.navis.argo.business.api.ArgoUtils;
import com.navis.argo.business.reference.Shipper;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.services.business.event.GroovyEvent;


import com.navis.argo.ArgoConfig;
import com.navis.argo.ArgoPropertyKeys;
import com.navis.argo.ArgoRefField;
import com.navis.argo.business.reference.AgentRepresentation;
import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.business.reference.Agent;
import com.navis.argo.*;
import com.navis.road.business.model.TruckingCompany;
import com.navis.framework.business.atoms.LifeCycleStateEnum;


/*
* Author : Raghu Iyer
* Date Written : 01/08/2013
* Description: This groovy is used to get the trucker Id attached for the input consignee/shipper
*/

public class MatUpdateTrucker extends GroovyInjectionBase
{

    public boolean execute(Map params)
    {
        try
        {
            println("MatUpdateTrucker")

            String trucker = null;
            String shipperBzuId = "ABF CARTAGE INC"
            trucker = getTrucker(shipperBzuId);
            //createAgent();
        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }

    private String getTruckerById(String ShipperBzuId)
    {
        String trucker = null;

        DomainQuery dqShipper = QueryUtils.createDomainQuery("Shipper").addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_NAME,ShipperBzuId));

        def shipperScopedBizUnit = ScopedBizUnit.findScopedBizUnit(ShipperBzuId, BizRoleEnum.SHIPPER);

        DomainQuery dq = QueryUtils.createDomainQuery("AgentRepresentation")
                .addDqPredicate(PredicateFactory.eq(ArgoRefField.AGENTREP_SCOPED_BUSINESS_UNIT, shipperScopedBizUnit.getBzuGkey()));
        println(dq);
        List agentRepList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        println ("agentRepList.size()" + agentRepList.size())
        if (agentRepList.size() > 0)
        {
            Iterator iter = agentRepList.iterator();
            while(iter.hasNext()) {
                def agentRep = iter.next();
                println ("agentListGkey "+ agentRep.agentrepAgent);
                dq = QueryUtils.createDomainQuery("Agent").addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_GKEY,agentRep.agentrepAgent.getBzuGkey()));
                println(dq);
                List agentList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
                println ("agentList.size()" + agentList.size())
                Iterator iterAgent = agentList.iterator();
                while(iterAgent.hasNext()) {
                    def agent = iterAgent.next();
                    println ("agentId "+ agent.bzuId);
                    trucker = agent.bzuId
                }
            }
            println("trucker " + trucker);
        }
        return (trucker)
    }

    private String getTrucker(String ShipperBzuName)
    {
        String trucker = null;

        DomainQuery dqShipper = QueryUtils.createDomainQuery("Shipper").addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_NAME,ShipperBzuName));

        println(dqShipper);

        List shipperList = HibernateApi.getInstance().findEntitiesByDomainQuery(dqShipper);
        println ("shipperList.size()" + shipperList.size())
        if (shipperList.size() > 0)
        {
            Iterator iter = shipperList.iterator();
            while(iter.hasNext()) {
                def shipper = iter.next();
                println ("agentListGkey "+ shipper.bzuId);
                def shipperScopedBizUnit = ScopedBizUnit.findScopedBizUnit(shipper.bzuId, BizRoleEnum.SHIPPER);
                println ("shipperScopedBizUnit "+ shipperScopedBizUnit);

                DomainQuery dq = QueryUtils.createDomainQuery("AgentRepresentation")
                        .addDqPredicate(PredicateFactory.eq(ArgoRefField.AGENTREP_SCOPED_BUSINESS_UNIT, shipperScopedBizUnit.getBzuGkey()))
                        .addDqOrdering(Ordering.desc(ArgoRefField.AGENTREP_CREATED));

                println(dq);
                List agentRepList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

                println ("agentRepList.size()" + agentRepList.size())
                if (agentRepList.size() > 0)
                {
                    Iterator iter1 = agentRepList.iterator();
                    while(iter1.hasNext()) {
                        def agentRep = iter1.next();
                        println ("agentListGkey "+ agentRep.agentrepAgent);
                        dq = QueryUtils.createDomainQuery("Agent").addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_GKEY,agentRep.agentrepAgent.getBzuGkey()))
                                .addDqPredicate(PredicateFactory.ne(ArgoRefField.BZU_ID,"O/P"));
                        println(dq);
                        List agentList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
                        println ("agentList.size()" + agentList.size())
                        Iterator iterAgent = agentList.iterator();
                        while(iterAgent.hasNext()) {
                            def agent = iterAgent.next();
                            println ("agentId "+ agent.bzuId);
                            trucker = agent.bzuId;
                            break;
                        }
                    }
                    println("trucker " + trucker);
                }
            }
        }
        return (trucker)
    }

}