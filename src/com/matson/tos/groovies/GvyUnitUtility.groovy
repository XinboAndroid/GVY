/*
* SrNo Doer  Date      Change
* A1   GR    08/30/10
*/

import com.navis.inventory.business.units.Unit
import com.navis.argo.business.api.GroovyApi
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent

import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.portal.Ordering
import com.navis.argo.business.reference.*
import com.navis.framework.persistence.*
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.QueryUtils
import com.navis.inventory.business.units.Unit
import com.navis.argo.business.atoms.LocTypeEnum


public class GvyUnitUtility{

    /* Method to Get  PreviousDepartedUnit in that facility */
    public Unit getPreviousDepartedUnit(Object api,Object currentUnit){
        def gvyGateClass = api.getGroovyClassInstance("GvyCmisGateData")
        def departedUfvUnit = gvyGateClass.getDepatedUnit(currentUnit)
        def departedUnit = departedUfvUnit.ufvUnit
    }

    /* Method Returns boolean if field was changed    */
    public boolean isFieldChngForNonBuildInEvents(GroovyEvent event,GroovyApi api, String afield, Object gvyEventUtil)
    {
        boolean isFldChange = false
        //Reads and Maps Event Updated Field value
        def gvyEvntUtil = gvyEventUtil == null ? api.getGroovyClassInstance("GvyEventUtil") : gvyEventUtil ;
        Map mapEvntField = gvyEvntUtil.eventFieldChangedValues(event, api)

        //Fetch Event Updated Field : current and Previous value
        def aEvntFieldObj = mapEvntField.get(afield)
        if(aEvntFieldObj == null) { return isFldChange;}
        def fieldname = aEvntFieldObj.getFieldName()
        def previousValue = aEvntFieldObj.getpreviousValue()
        previousValue = previousValue != null ? previousValue : ''
        def currentValue = aEvntFieldObj.getCurrentValue()
        currentValue = currentValue != null ? currentValue : ''

        if(!currentValue.equals(previousValue)){
            isFldChange = true
        }

        return isFldChange

    }

    /*
     * Method Fetches last Departed Ufv Unit
     */
    private Object findRetiredUfvUnit(Object inFacility, long primaryEquiGkey)
    {
        def lastRetiredUfvUnit = null;
        List ufvUnitLst = null;
        try{
            //NEW Query Removes TRUCK Criteria Check
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, inFacility.getFcyGkey())).addDqPredicate(PredicateFactory.eq(UnitField.UFV_VISIT_STATE, UnitVisitStateEnum.RETIRED)).addDqPredicate(PredicateFactory.eq(UnitField.UFV_PRIMARY_EQ, primaryEquiGkey)).addDqOrdering(Ordering.desc(UnitField.UFV_TIME_OUT));

            ufvUnitLst = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

            println('ufvUnitLst ::'+(ufvUnitLst != null ? ufvUnitLst.size() : '0'))
            int count = 0;

            for(aUfv in ufvUnitLst){
                count++;
                def remarks =  aUfv.getFieldValue("ufvUnit.unitRemark");
                def truckerId = aUfv.getFieldValue("ufvActualObCv.carrierOperatorId")
            }

            //The First ufv in the List is the most recent dept unit
            if (ufvUnitLst.size() > 0) {
                lastRetiredUfvUnit = ufvUnitLst.get(0);
                return lastRetiredUfvUnit
            }

        }catch(Exception e){
            e.printStackTrace()
        }
        return lastRetiredUfvUnit ;
    }

    /*
      * Method Fetches last Retired/Departed Ufv Unit
      */
    private Object findRetiredDepartedUfvUnit(Object inFacility, long primaryEquiGkey)
    {
        def lastRetiredUfvUnit = null
        List ufvUnitLst = null
        try{
            //NEW Query Removes TRUCK Criteria Check
            DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_FACILITY, inFacility.getFcyGkey())).addDqPredicate(PredicateFactory.in(UnitField.UFV_VISIT_STATE, [
                    UnitVisitStateEnum.RETIRED,
                    UnitVisitStateEnum.DEPARTED
            ])).addDqPredicate(PredicateFactory.eq(UnitField.UFV_PRIMARY_EQ, primaryEquiGkey)).addDqOrdering(Ordering.desc(UnitField.UFV_TIME_OF_LAST_MOVE))

            ufvUnitLst = HibernateApi.getInstance().findEntitiesByDomainQuery(dq)

            println('ufvUnitLst ::'+(ufvUnitLst != null ? ufvUnitLst.size() : '0'))
            int count = 0

            for(aUfv in ufvUnitLst){
                count++
                def remarks =  aUfv.getFieldValue("ufvUnit.unitRemark")
                def truckerId = aUfv.getFieldValue("ufvActualObCv.carrierOperatorId")
            }

            //The First ufv in the List is the most recent dept unit
            if (ufvUnitLst.size() > 0) {
                lastRetiredUfvUnit = ufvUnitLst.get(0)
                return lastRetiredUfvUnit
            }

        }catch(Exception e){
            e.printStackTrace()
        }
        return lastRetiredUfvUnit
    }

}