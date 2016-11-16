/*
 * Copyright (c) 2014 Navis LLC. All Rights Reserved.
 *
 */

import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.api.IEventType
import com.navis.argo.business.atoms.EventEnum
import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.framework.util.BizViolation
import com.navis.inventory.business.units.TbdUnitDeliveryValidator
/**
 * This is a sample Groovy Plug-in which illustrates how one could intercept and validate a TBD for Delivery/Dispatch
 * add to or replace the built-in product validations.
 * NOTE: This groovy should be copied and renamed as "TbdUnitValidationGroovyImpl" when used in Groovy Plugin
 * @author <a href="mailto:adeel.azhar@navis.com">Adeel Azhar</a>
 */

public class TbdUnitValidationGroovyImplSample extends GroovyApi {
    public void execute(Map args) throws BizViolation {

        log("TbdUnitValidationGroovyImpl: BEGIN: " + new Date())

        def info = "SAMPLE"

        // getting validator
        TbdUnitDeliveryValidator validator = (TbdUnitDeliveryValidator) args.get("VALIDATOR")
        //first run default validation provided out of the box.
        validator.validateAll(WiMoveKindEnum.YardMove);

        // Customer can now then further validate the TBD to for equipment type validation.
        // Or check for holds/permission associated with any particualr event type.

        try {
            log("Started: Calling APIs other than default");

            //Consider Equipment Type Subsitution when validing UFV against TBD for merge.
            validator.validateEquipmentType();

            //Verify if container is allowed for UNIT_DELIVER
            IEventType eventType = EventEnum.UNIT_DELIVER;
            validator.validateCtrHoldPermission(eventType);
        }
        catch (BizViolation inBizViolation) {
            log("TbdUnitValidationGroovyImpl: END (Validation Failed $info): Error= " + inBizViolation.getLocalizedMessage() + new Date())
            throw inBizViolation;
        }
        log("TbdUnitValidationGroovyImpl: END (Validation Successful $info) : " + new Date())
    }
}
