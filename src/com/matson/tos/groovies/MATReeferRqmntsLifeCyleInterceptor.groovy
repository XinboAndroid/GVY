/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChange
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.ReeferRqmnts

/**
 * Check This groovy:
 *     1. Ensures that the 'display in fahrenheit' is set on the reefer requirements.
 *     2. For dual temperature it makes sure the over-all reefer requires temperature is the minimum temperature (temperature 1)
 *
 * Author: Peter Seiler
 * Date: 06/18/15
 * JIRA: CSDV-3035
 * SFDC: 138256
 *
 */

public class MATReeferRqmntsLifeCyleInterceptor extends AbstractEntityLifecycleInterceptor
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
        this.log("MATReeferRqmntsLifeCyleInterceptor: Started");

        /* exit if no changes */

        if (inOriginalFieldChanges == null || inOriginalFieldChanges.getFieldChangeCount() == 0)
            return;

        /* find update to reefer requirements */

        if (inOriginalFieldChanges.hasFieldChange(InventoryField.GDS_REEFER_RQMNTS))
        {
            Boolean SomethingChanged = Boolean.FALSE;

            /* make sure the display is fahrenheit */

            EFieldChange ReeferChng = inOriginalFieldChanges.findFieldChange(InventoryField.GDS_REEFER_RQMNTS);

            ReeferRqmnts NewReeferReq = ReeferChng.getNewValue();

            if (!NewReeferReq.getRfreqTempShowFahrenheit())
            {
                NewReeferReq.setRfreqTempShowFahrenheit(Boolean.TRUE);
                SomethingChanged = Boolean.TRUE;
            }

            /* set the required temperature to the minimum temperature */

            if ((NewReeferReq.rfreqTempLimitMinC != null) && (NewReeferReq.rfreqTempRequiredC != NewReeferReq.rfreqTempLimitMinC))
            {
                NewReeferReq.setRfreqTempRequiredC(NewReeferReq.rfreqTempLimitMinC);
                SomethingChanged = Boolean.TRUE;
            }

            /* if the required temperature is set make sure the minimum is set also */

            if (NewReeferReq.rfreqTempRequiredC != null && NewReeferReq.rfreqTempLimitMinC == null)
            {
                NewReeferReq.setRfreqTempLimitMinC(NewReeferReq.rfreqTempRequiredC);
                SomethingChanged = Boolean.TRUE;
            }

            if (SomethingChanged)
            {
                inMoreFieldChanges.setFieldChange(InventoryField.GDS_REEFER_RQMNTS, NewReeferReq);
            }
        }
    }
}