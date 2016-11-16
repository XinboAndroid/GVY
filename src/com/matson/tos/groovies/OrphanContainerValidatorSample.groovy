/*
 * Copyright (c) 2014 Navis LLC. All Rights Reserved.
 *
 */



package extension.system

/**
 * Created by perumsu on 21/8/14.
 */

/*
 * Orphan Container Validator in N4 Mobile Hatch Clerk
 *
 * Description :
 * The sample groovy to validate the orphan container.
 * NOTE: This groovy should be copied and renamed as "OrphanContainerValidator" when used in Code Extensions
 *
 *  * Installation Instructions:
 *     1. Go to Administration >> System >> Code Extensions
 *     2. Click Add (+)
 *     3. Input details as below:  Extension Type -> INV_ORPHAN_CTR_VALIDATOR, Code Extension Name -> OrphanContainerValidator
 *     4. Click Edit Contents and paste the contents of MTLCodecoEdiPostInterceptor.groovy into the Contents Edit Area.
 */

import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.portal.FieldChange
import com.navis.framework.portal.FieldChanges
import com.navis.inventory.business.api.SearchResults
import com.navis.inventory.business.api.UnitFinder
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.external.inventory.AbstractOrphanContainerValidator
import com.navis.inventory.external.inventory.EOrphanContainerValidator

/**
 * Created by perumsu on 17/8/14.
 */
class OrphanContainerValidatorSample extends AbstractOrphanContainerValidator {

    public void validateOrphanContainer(Map inParams) {
        log("Groovy : OrphanContainerValidator started!");
        Map programData = (HashMap) inParams.get(EOrphanContainerValidator.TRIGGER_FIELD_CHANGES);
        FieldChange fc = ((FieldChanges) programData.get("MNHCContainer")).getFieldChange(MetafieldIdFactory.valueOf("mnhcCntrID1"));
        String ctrId = fc.getNewValue();
        UnitFinder unitFinder = (UnitFinder) Roastery.getBean(UnitFinder.BEAN_ID);
        SearchResults results = unitFinder.findUfvByExactDigits(ctrId, true, true);
        UnitFacilityVisit ufv = UnitFacilityVisit.hydrate(results.getFoundPrimaryKey());
        if (ufv != null) {
            boolean hasPlan = ufv.hasPlannedWi(WiMoveKindEnum.VeslDisch);
            if (hasPlan) {
                registerError(CANNOT_DISCHARGE_UNIT_WITH_DISCH_PLAN_IN_ORPHAN_MODE);
                log("Groovy : OrphanContainerValidator : " + CANNOT_DISCHARGE_UNIT_WITH_DISCH_PLAN_IN_ORPHAN_MODE);
            }
        }
        log("Groovy : OrphanContainerValidator done!")
    }

    private final String CANNOT_DISCHARGE_UNIT_WITH_DISCH_PLAN_IN_ORPHAN_MODE = "Cannot discharge actual unit with plan to yard in orphan mode";
}
