import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.inventory.InventoryField;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.GoodsBase;
import com.navis.argo.business.reference.Shipper;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.framework.business.Roastery;
import com.navis.framework.persistence.HibernateApi;
import com.navis.inventory.business.units.UnitFacilityVisit;
import org.apache.log4j.Logger;
import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.business.reference.Shipper;
import com.navis.argo.ArgoRefField;

import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;


/*
* Author : Raghu Iyer
* Date Written : 05/01/2013
* Description: This groovy is used to update the UfvFlexString08 with consignee notes.
*/

public class MatUpdateUnitConsigneeNotes extends GroovyInjectionBase
{

    private updateNotes(Unit unit)
    {
        println("Start MatUpdateUnitConsigneeNotes");
        def consignee = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName");

        def notes = unit.getFieldValue("unitActiveUfv.ufvFlexString06");
        def dbNotes = getTrucker(consignee);
        unit.setFieldValue("unitActiveUfv.ufvFlexString08",dbNotes);
        def UpdtNotes = unit.getFieldValue("unitActiveUfv.ufvFlexString08");
        println("Consignee notes ::::: "+ unit.unitId +" :: "+consignee +" :: "+ notes +" :: "+ UpdtNotes+" :: "+dbNotes);
        println("End MatUpdateUnitConsigneeNotes");
    }

    private String getTrucker(String ShipperBzuName)
    {
        String notes = null;

        DomainQuery dqShipper = QueryUtils.createDomainQuery("Shipper").addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_NAME,ShipperBzuName))
                .addDqPredicate(PredicateFactory.isNotNull(ArgoRefField.BZU_NOTES))
                .addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_LIFE_CYCLE_STATE,"ACT"));

        println(dqShipper);

        List shipperList = HibernateApi.getInstance().findEntitiesByDomainQuery(dqShipper);
        println ("shipperList.size()" + shipperList.size())
        if (shipperList.size() > 0)
        {
            Iterator iter = shipperList.iterator();
            while(iter.hasNext()) {
                def shipper = iter.next();
                notes = shipper.bzuNotes;
            }
        }
        return (notes)
    }

}