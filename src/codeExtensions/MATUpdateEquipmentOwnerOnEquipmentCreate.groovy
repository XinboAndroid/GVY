package codeExtensions

import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.inventory.InventoryField
import org.apache.log4j.Level

/**
 * Created by kramachandran on 9/6/2016.
 */
class MATUpdateEquipmentOwnerOnEquipmentCreate extends AbstractEntityLifecycleInterceptor {
    @Override
    public void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncUnitEquipmentInterceptor invoked onCreate Method.");
        LOGGER.info("BL NUMBER DISAPPEARED : MatsonAncUnitEquipmentInterceptor onCreate");
        if (inOriginalFieldChanges.hasFieldChange(InventoryField.UE_DEPARTURE_ORDER_ITEM)) {
            LOGGER.info("BL NUMBER DISAPPEARED : MatsonAncUnitEquipmentInterceptor UE_DEPARTURE_ORDER_ITEM");
            copyBkgNbrToBLNbr(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
            //EqBaseOrderItem item = inOriginalFieldChanges.findFieldChange(InventoryField.UE_DEPARTURE_ORDER_ITEM) as EqBaseOrderItem;
        }
        LOGGER.info(" MatsonAncUnitEquipmentInterceptor completed onCreate Method.");
    }

    @Override
    public void onUpdate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncUnitEquipmentInterceptor invoked onUpdate Method.");
        LOGGER.info("BL NUMBER DISAPPEARED : MatsonAncUnitEquipmentInterceptor onUpdate");
        copyBkgNbrToBLNbr(inEntity, inOriginalFieldChanges, inMoreFieldChanges);
        LOGGER.info(" MatsonAncUnitEquipmentInterceptor completed onUpdate Method.");
    }

}
