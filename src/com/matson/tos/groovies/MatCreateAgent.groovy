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

public class MatCreateAgent extends GroovyInjectionBase
{

    public boolean execute(Map params)
    {
        try
        {
            println("MatUpdateTrucker")

            String trucker = null;
            //String shipperBzuId = "ABF CARTAGE INC"
            //trucker = getTrucker(shipperBzuId);
            createAgent();
        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }


    private createAgent()
    {
        try
        {

            DomainQuery dq = QueryUtils.createDomainQuery("TruckingCompany");
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ROLE, BizRoleEnum.HAULIER));
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_LIFE_CYCLE_STATE, LifeCycleStateEnum.ACTIVE));
            dq.addDqPredicate(PredicateFactory.isNotNull(ArgoRefField.BZU_PER_UNIT_GUARANTEE_LIMIT));
            //dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_NAME,"A & W TRANSPORTATION LLC_GR"));

            println(dq);

            List agentList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println ("agentList.size()" + agentList.size());

            Iterator iter = agentList.iterator();
            while(iter.hasNext()) {
                def agent = iter.next();
                String agentFind = agent.getBzuId();
                String agentName = agent.getBzuName();
                Agent agent1 = Agent.findOrCreateAgent (agentFind,agentName);
                println(" Agent Id ::::: " + agent1 + "::::"+agentName)
            }
        }
        catch (e)
        {
            println("Exception :::: " + e);
        }


    }
}