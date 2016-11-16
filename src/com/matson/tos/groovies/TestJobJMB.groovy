///////////////////////////////////////////////////////////////////////////////
///
/// Copyright 2014, Matson Inc.
///
///////////////////////////////////////////////////////////////////////////////

import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitFinder;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.units.EquipmentState;
import com.navis.inventory.business.api.UnitField;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;

///
/// - 20140829 - JMB - Created
/// - 20140902 - JMB - Updated to list units
///
public class TestJobJMB extends GroovyInjectionBase
{
    def inj = new GroovyInjectionBase();

    private final String emailTo = "1aktosdevteam@matson.com";
    private final String  emailFrom = "1aktosdevteam@matson.com";

    //public void execute(String vessel, String vesselGkey)
    public boolean execute(Map inParameters)
    {
        String  emailBody = "";

        ArrayList reportUnitList = new ArrayList();

        List unitList = null;
        unitList = getUnits();

        Iterator unitIterator = unitList.iterator();
        while(unitIterator.hasNext())
        {
            def unit = unitIterator.next();

            def categoryOther = unit.getUnitCategory();
//		   categoryOther = categoryOther != null ? categoryOther.getKey() : ""
//         categoryOther = categoryOther == "STRGE" ? "STORAGE" : ""
            //if ("STRGE" == categoryOther)
            //{
            emailBody = emailBody + "\r\n" + categoryOther;
            //}

        }

        def emailSender = inj.getGroovyClassInstance("EmailSender")
        emailSender.custSendEmail(emailFrom,
                emailTo,
                "ALL EVENTS JMB",
                "Data = " + emailBody);

        println("jmb Done");
    }

    public List getUnits()
    {
        try
        {
            ArrayList units = new ArrayList();

            DomainQuery dq = QueryUtils.createDomainQuery("Unit");
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CURRENT_UFV_TRANSIT_STATE, UfvTransitStateEnum.S10_ADVISED));
            dq.addDqPredicate(PredicateFactory.like(UnitField.UNIT_PRIMARY_EQTYPE_ID, "C%"));
//         dq.addDqPredicate(PredicateFactory.eq(UnitField.UFV_CATEGORY,UnitCategoryEnum.STORAGE));
            dq.addDqPredicate(PredicateFactory.like(UnitField.UFV_CATEGORY,"STRGE"));

/*
         DomainQuery dq = QueryUtils.createDomainQuery("Unit")
         //DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
            .addDqPredicate(PredicateFactory.eq(UnitField.UFV_CATEGORY,UnitCategoryEnum.STORAGE));
*/
            println("jmb DomainQuery = " + dq);
            HibernateApi hibernate = HibernateApi.getInstance();
            List unitList  = hibernate.findEntitiesByDomainQuery(dq);

            return unitList;

/*
	      HibernateApi hibernate = HibernateApi.getInstance();
 		   DomainQuery dq = QueryUtils.http://localhost:8280/apex/api/query?filtername=VESSEL_TEST&operatorId=LPC&complexId=NZLYT&facilityId=LCT("UnitFacilityVisit");
			dq.addDqPredicate(PredicateFactory.ne(UnitField.UFV_CATEGORY,UnitCategoryEnum.STORAGE))
            .addDqPredicate(PredicateFactory.ne(UnitField.RESTOW, UnitCategoryEnum.THROUGH))
         List unitList = hibernate.findEntitiesByDomainQuery(dq);
         return unitList;
*/



/*
         DomainQuery dq = QueryUtils.createDomainQuery("Unit")
			   .addDqPredicate(PredicateFactory.in(UnitField.CATEGORY, STRGE));
	      println("jmb DomainQuery = " + dq)
			HibernateApi hibernate = HibernateApi.getInstance();
			List unitList = hibernate.findEntitiesByDomainQuery(dq);
         return unitList;
*/
/*
         ArrayList units = new ArrayList();

         DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit");
         dq.addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE,"S40_YARD"));
         //dq.addDqPredicate(PredicateFactory.in(UnitField.UNIT_IMPEDIMENT_ROAD,"RM"));

         println("jmb DomainQuery :::: "+ dq);
         def ufvList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
         println("jmb After unitList"+ufvList.size());

         if(ufvList != null)
         {
            Iterator iter = ufvList.iterator();
            while(iter.hasNext())
            {
               def ufv = iter.next();
               def unit = ufv.ufvUnit;
               if(unit.getFieldValue("unitVisitState").equals(
                  com.navis.inventory.business.atoms.UnitVisitStateEnum.ACTIVE) &&
                  (unit.getFieldValue("unitAppliedHoldOrPermName") != null &&
                  unit.getFieldValue("unitAppliedHoldOrPermName").contains("RM")))
               {
                  units.add(unit);
               }
            }
         }
         println("jmb unitsSize" + units.size);
         return units;
*/
        }
        catch(Exception e)
        {
            def emailSender = inj.getGroovyClassInstance("EmailSender")
            emailSender.custSendEmail(emailFrom, emailTo, "TestJobJMB Error: " + e.getMessage());

            e.printStackTrace();
            println("jmb ERROR = " + e.getMessage());
        }
    }
}


