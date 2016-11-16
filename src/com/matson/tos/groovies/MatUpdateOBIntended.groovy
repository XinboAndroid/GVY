import com.navis.inventory.business.units.UnitFacilityVisit;
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.inventory.business.units.*
import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.argo.business.model.CarrierVisit;
import com.navis.inventory.InventoryField;

import com.navis.framework.portal.Ordering;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.inventory.business.api.UnitField
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.persistence.Persister;
import  com.navis.framework.business.Roastery

/*
* Author : Raghu Iyer
* Date Written : 09/19/2012
* Description: This groovy is used to generate the Stowplan/Manifest Discrepancies after Stowplan and Manifest process
*/

public class MatUpdateOBIntended extends GroovyInjectionBase
{
    def inj = new GroovyInjectionBase();

    public void execute(Object unit)
    {
        try
        {
            LocTypeEnum locEnum = LocTypeEnum.TRUCK;
            def carrier = CarrierVisit.getGenericCarrierVisitForMode(ContextHelper.getThreadComplex(),locEnum);
            println("Update OB intended and OB actual with :: "+ carrier + " for unit id ::"+unit.unitId);
            unit.setFieldValue("unitActiveUfv.ufvIntendedObCv",carrier);
            unit.setFieldValue("unitActiveUfv.ufvActualObCv",carrier);

        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }

    public boolean execute(Map params)
    {
        try
        {
            List units = getActiveUnitsRi("MATU2545751");

            if (units.size() > 0) {
                Iterator unitsIter = units.iterator();
                while (unitsIter.hasNext())
                {
                    def unit = unitsIter.next();
                    LocTypeEnum locEnum = LocTypeEnum.TRUCK;
                    def carrier = CarrierVisit.getGenericCarrierVisitForMode(ContextHelper.getThreadComplex(),locEnum);
                    println("Update OB intended and OB actual with :: "+ carrier + " for unit id ::"+unit.unitId);
                    unit.setFieldValue("unitActiveUfv.ufvIntendedObCv",carrier);
                    unit.setFieldValue("unitActiveUfv.ufvActualObCv",carrier);
                }
            }

        }catch(Exception e){
            e.printStackTrace()
            println(e)
        }
    }

    public List getActiveUnitsRi(String unitId)
    {
        try
        {
            ArrayList units = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_UNIT_ID, unitId)).addDqOrdering(Ordering.desc(InventoryField.UFV_TIME_OF_LAST_MOVE));

            println("dq:::::::::"+dq)
            def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            println("After unitList"+ufvList.size());
            if(ufvList != null) {
                Iterator iter = ufvList.iterator();
                while(iter.hasNext()) {
                    def ufv = iter.next();
                    def unit = ufv.ufvUnit;
                    units.add(unit);
                    break;
                }
            }
            println("unitsSize" + units.size);
            return units;
        }catch(Exception e){
            e.printStackTrace();
            println(e.getMessage());
        }
    }

}


