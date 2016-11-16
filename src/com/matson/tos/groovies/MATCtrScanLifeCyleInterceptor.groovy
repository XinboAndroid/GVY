/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.business.Roastery
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.Unit
import com.navis.road.RoadField

/**
 * This groovy pulls the seal number from a unit in the yard and adds it to the scan data when record-scan is processed
 * The process needs the existing unit's seals to populate on the gate screen for the outgate.
 *
 * Author: Peter Seiler
 * Date: 06/09/19
 * JIRA: ARGO-75840
 * SFDC: 141196
 *
 */

public class MATCtrScanLifeCyleInterceptor extends AbstractEntityLifecycleInterceptor
{
    @Override
    void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges)
    {
        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
    }

    @Override
    void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges)
    {

        this.onCreateOrUpdate(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
    }

    @Override
    void onCreateOrUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges)

    {
        this.log ("MATCtrScanLifeCyleInterceptor: Started");

        this.log ("inOriginalFieldChanges " + inOriginalFieldChanges)

        /* exit if no changes */

        if (inOriginalFieldChanges == null || inOriginalFieldChanges.getFieldChangeCount() == 0)
            return;

        /* find the active unit for the container in the scan */

        if (inOriginalFieldChanges.hasFieldChange(RoadField.SCANCTR_ID))
        {

            /* if there is a container ID in the scan being saved get the ID */

            EFieldChange scanContainer = inOriginalFieldChanges.findFieldChange(RoadField.SCANCTR_ID);
            String ctrId = scanContainer.getNewValue();

            /* find any active unit in the yard with that ID */

            Unit thisUnit = findActiveUnitInYard(ctrId);

            this.log ("thisUnit " + thisUnit)

            if (thisUnit != null)
            {

                /* if there is an active unit in the yard copy it's seal number into the scan so that it will populate on the gate screen */

                inMoreFieldChanges.setFieldChange(RoadField.SCANCTR_SEAL_NBR1, thisUnit.getUnitSealNbr1());

                this.log("inMoreFieldChanges " + inMoreFieldChanges)
            }
        }
    }


    /* Local function to find the an active container unit in the yard unit */

    private Unit findActiveUnitInYard (String ctrId)

    {
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_VISIT_STATE,  UnitVisitStateEnum.ACTIVE))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CURRENT_UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ID, ctrId))

        Unit[] unitList=Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);

        if(unitList == null || unitList.size()==0)
        {
            return null;
        }
        return unitList[0];
    }
}