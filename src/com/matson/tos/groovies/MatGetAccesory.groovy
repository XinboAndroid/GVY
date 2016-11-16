import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.api.UnitField


import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery

import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;

/*
* Author : Raghu Iyer
* Date Written : 09/19/2012
* Description: This groovy is used to generate the Stowplan/Manifest Discrepancies after Stowplan and Manifest process
*/

public class MatGetAccesory extends GroovyInjectionBase
{
    def inj = new GroovyInjectionBase();

    public String getUnitAccesorry(String containerId)
    {

        try {
            println("Inside getUnitAccesorry");
            inj = new GroovyInjectionBase();
            ArrayList units = new ArrayList();
            String accessory;

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID,containerId));
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_TRANSIT_STATE,UfvTransitStateEnum.S40_YARD))
            println("getUnit "+dq);
            def unitList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+unitList.size());
            if (unitList.size() > 0)
            {
                def trucker = null;
                Iterator iterUnitList = unitList.iterator()
                while (iterUnitList.hasNext())
                {
                    def ufv = iterUnitList.next();
                    def unit = ufv.ufvUnit;
                    def consignee = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName");
                    accessory = unit.getUnitAcryEquipIds();
                    println("accessory--->"+accessory);
                    def acryObj = unit.getUnitCtrAccessory();
                    println("acryObj--->"+acryObj);

                }
            }
            return accessory;
        }catch (Exception e){
            println("Error :" + e);
        }
    }
}

