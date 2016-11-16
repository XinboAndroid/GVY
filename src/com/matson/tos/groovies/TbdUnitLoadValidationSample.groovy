/*
 * Copyright (c) 2010 Zebra Technologies Corp. All Rights Reserved.
 * $Id: TbdUnitLoadValidationSample.groovy,v 1.1 2016/10/05 21:10:21 vnatesan Exp $
 */

import com.navis.external.framework.util.EFieldChanges
import com.navis.argo.business.api.ServicesManager
import com.navis.framework.util.ValueObject
import com.navis.argo.business.atoms.BillingExtractEntityEnum
import com.navis.argo.business.services.IServiceExtract
import com.navis.inventory.InventoryBizMetafield
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.argo.business.extract.ChargeableUnitEvent
import com.navis.argo.business.extract.ChargeableMarineEvent
import com.navis.inventory.InventoryPropertyKeys
import com.navis.framework.util.BizViolation
import com.navis.framework.portal.FieldChanges
import com.navis.argo.business.extract.Guarantee
import com.navis.argo.ArgoExtractEntity
import com.navis.argo.ArgoExtractField
import com.navis.argo.business.atoms.GuaranteeTypeEnum
import com.navis.argo.business.atoms.PaymentTypeEnum
import com.navis.framework.metafields.MetafieldId
import com.navis.external.framework.util.EFieldChange
import com.navis.argo.ContextHelper
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.argo.business.reference.ContactInfo
import com.navis.inventory.external.inventory.AbstractRecordGuarantee
import com.navis.inventory.external.inventory.ERecordGuarantee
import com.navis.framework.util.DateUtil

/**
 * This is a sample Groovy Plug-in which illustrates how one could intercept and validate a TBD Unit load
 * by a hatch clerk and add to or replace the built-in product validations.
 * NOTE: This groovy should be copied and renamed as "TbdUnitValidationMerge" when used in Code Extensions
 * @author <a href="mailto:ssampath@zebra.com">Sumitha Sampath</a>
 * @author <a href="mailto:milind.padhye@navis.com">Milind Padhye</a>
 */
import com.navis.inventory.external.inventory.AbstractTbdUnitValidationMerge
import com.navis.inventory.business.units.TbdUnitLoadValidator
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.business.units.TbdUnit
import com.navis.inventory.business.units.Unit
import com.navis.argo.business.reference.Equipment
import com.navis.inventory.business.units.UnitEquipDamages
import com.navis.inventory.business.units.UnitEquipment;

public class TbdUnitLoadValidationSample extends AbstractTbdUnitValidationMerge
{

    /*
    In this example, we want to do all the built-in validations but further add the condition that unit should not be damaged.
     */
    public void performVesselLoadValidations(TbdUnitLoadValidator inValidator, Map inArgs) {
        logMsg("Groovy: performVesselLoadValidations started!");

        UnitFacilityVisit ufv = inValidator.getUfv();
        if(ufv == null) {
            registerError("Excepted to receive a valid instance of UnitFacilityVisit in performVesselValidations");
            logMsg("Done performVesselLoadValidations with Exceptions");
            return;
        }

        TbdUnit tbdu = inValidator.getTbdUnit();
        if(tbdu == null) {
            registerError("Excepted to receive a valid instance of TbdUnit in performVesselValidations");
            logMsg("Done performVesselLoadValidations with Exceptions");
            return;
        }

        Unit unit = inValidator.getUfvUnit();
        UnitEquipment unitEquip = inValidator.getUfvUnitEquipment();
        Equipment equip = unitEquip.getUeEquipment();
        UnitEquipDamages damages = unit.getDamages(equip);
        if(damages != null) {
            registerError("TBD Validation failed: Equipment is Damaged");
            logMsg("Done performVesselLoadValidations with Exceptions");
        }

        //do built-in validations
        super.performVesselLoadValidations(inValidator, inArgs);

        logMsg("Groovy: performVesselLoadValidations Done!");
    }

    @Override
    /*
    In this method, we want to do all the built-in validations but further add the condition that unit should not be damaged during rail load operation
     */
    void performRailLoadValidations(TbdUnitLoadValidator inValidator, Map inArgs) {
        logMsg("Groovy: performRailLoadValidations started!");

        UnitFacilityVisit ufv = inValidator.getUfv();
        if(ufv == null) {
            registerError("Excepted to receive a valid instance of UnitFacilityVisit in performRailValidations");
            logMsg("Done performRailLoadValidations with Exceptions");
            return;
        }

        TbdUnit tbdu = inValidator.getTbdUnit();
        if(tbdu == null) {
            registerError("Excepted to receive a valid instance of TbdUnit in performRailValidations");
            logMsg("Done performRailLoadValidations with Exceptions");
            return;
        }

        Unit unit = inValidator.getUfvUnit();
        UnitEquipment unitEquip = inValidator.getUfvUnitEquipment();
        Equipment equip = unitEquip.getUeEquipment();
        UnitEquipDamages damages = unit.getDamages(equip);
        if(damages != null) {
            registerError("TBD Validation failed: Equipment is Damaged");
            logMsg("Done performRailLoadValidations with Exceptions");
        }

        //do built-in validations
        super.performRailLoadValidations(inValidator, inArgs);

        logMsg("Groovy: performRailLoadValidations Done!");
    }

    private void logMsg(String inMsg) {
        log(inMsg);
        System.out.println(inMsg);
    }
}
