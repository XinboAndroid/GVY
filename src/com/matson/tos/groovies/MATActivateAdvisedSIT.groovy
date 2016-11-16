/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

import groovy.time.TimeCategory

import java.util.Date;
import java.text.*;
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.api.GroovyApi
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.business.api.RectifyParms
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.argo.business.atoms.LocTypeEnum

/**
 * This groovy sets UFVs that are advised imports to active in N4.
 *
 * This will make them visible in XPS.
 *
 * Author: Peter Seiler
 * Date: 06/05/15
 * JIRA: ARGO-75773
 * SFDC: 141015
 *
 * Date: 06/08/15
 *
 * Correct the timing check.
 *
 * Test to be sure no other UFVs for this same unit are visible in SPARCS
 */

public class MATActivateAdvisedSIT extends GroovyApi
{

    public void execute(Map parameters)
    {

        /* find the UFVs to fix */

        List<UnitFacilityVisit> UFVs_to_fix = this.findUFVsAdvised();

        for (UnitFacilityVisit UFV_to_fix : UFVs_to_fix)
        {

            this.log ("Processing for UFV: " + UFV_to_fix);
            this.log("UFV created " + UFV_to_fix.getUfvCreateTime());

            /* check if the unit to be fixed is an import */

            if  (UFV_to_fix.getUfvUnit().getLocType() == LocTypeEnum.TRUCK)
            {

                /* find other UFV for this same unit.  Make sure it is not visible in SPARCS */

                Set<UnitFacilityVisit> UFV_to_fix_other_UFVs = UFV_to_fix.getUfvUnit().getUnitUfvSet();

                Boolean Other_UFVs_visible = Boolean.FALSE;

                for (UnitFacilityVisit UFV_to_fix_other_UFV : UFV_to_fix_other_UFVs)
                {
                    if (UFV_to_fix_other_UFV.getUfvVisibleInSparcs())
                    {
                        Other_UFVs_visible = Boolean.TRUE;
                    }
                }

                /* only attempt the update if no other UFVS for the same unit are visible in SPARCS */

                if (Other_UFVs_visible)
                {
                    this.log ("Other UNFs are visible for this unit.  Not safe to activate " + UFV_to_fix.getUfvUnit());
                }
                else
                {
                    try
                    {

                        this.log("process UFV " + UFV_to_fix);

                        /* use rectify function to set UFV to 'inbound' 'active' */

                        RectifyParms thisRectifyParm = new RectifyParms();
                        thisRectifyParm.setUfvTransitState(UfvTransitStateEnum.S20_INBOUND);
                        thisRectifyParm.setUnitVisitState(UnitVisitStateEnum.ACTIVE);

                        UFV_to_fix.rectify(thisRectifyParm);

                        /* UFV_to_fix.makeActive(); */
                    } catch (Exception e)
                    {
                        this.log("MATActivateAdvisedSIT exception " + e.getMessage() + " when activating UFV " + UFV_to_fix);
                    }
                }
            }
        }
    }

    /* local function to find advised unitfaclity visits less that 5 minutes old */

    private List<UnitFacilityVisit> findUFVsAdvised()
    {

        /* get the current time */

        Date timeNow = ArgoUtils.timeNow();

        Date Five_minutes_ago;

        use(TimeCategory)
                {
                    Five_minutes_ago = timeNow - 5.minutes;
                }

        /* put a delay in so that there is no chance this happens at the same time the gate transaction */

        this.log ("time now " + timeNow)
        this.log ("Five_minutes_ago " + Five_minutes_ago)

        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(InventoryField.UFV_TRANSIT_STATE, UfvTransitStateEnum.S10_ADVISED))
                .addDqPredicate(PredicateFactory.le(InventoryField.UFV_CREATE_TIME, Five_minutes_ago));

        List<UnitFacilityVisit> UFVList = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);

        this.log ("UFVList " + UFVList)

        return UFVList;
    }
}