package codeExtensions

import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.BizRoleEnum
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.atoms.EquipClassEnum
import com.navis.argo.business.model.Facility
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.Equipment
import com.navis.argo.business.reference.ScopedBizUnit
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.inventory.business.atoms.EqUnitRoleEnum
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.services.business.event.GroovyEvent
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @author Keerthi Ramachandran
 * @since 6/20/2016
 * <p>MATUpdateEquipmentOwnerOnUnitCreate is ..</p>
 */
class MATUpdateEquipmentOwnerOnUnitCreate extends AbstractGeneralNoticeCodeExtension {

    private Logger LOGGER = Logger.getLogger(MATUpdateEquipmentOwnerOnUnitCreate.class);

    public void execute(GroovyEvent inEvent)

    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("MATUpdateEquipmentOwnerOnUnitCreate Execution Started");

        Boolean isValidEvent = Boolean.TRUE; //this.isValidEvent(inEvent);
        if (isValidEvent) {

            try {
                Unit ThisUnit = (Unit) inEvent.getEntity();
                if (ThisUnit == null) {
                    LOGGER.error("Reference to Unit not found!");
                    sendMailAndReturn("Reference to Unit not found!");
                } else
                    LOGGER.info("Unit: " + ThisUnit);

                Facility ThisFacility = ContextHelper.getThreadFacility();
                /*UnitFacilityVisit ThisUFV = ThisUnit.getUfvForFacilityAndEventTime(ThisFacility,
                        inEvent.getEvent().getEventTime());
                if (ThisUFV == null) {
                    LOGGER.error("Reference to UFV not found!");
                    sendMailAndReturn("Reference to UFV not found!")
                    LOGGER.error("Container owner update failed, no UFV found")
                    return;
                } else
                    LOGGER.info("UFV: " + ThisUFV);*/

                UnitEquipment ThisUnitEquip = ThisUnit.getUnitPrimaryUe();
                Equipment equipment = ThisUnitEquip.getUeEquipment();
                LOGGER.info("ThisUnitEquip  " + ThisUnitEquip);
                LOGGER.info("Equipment  " + equipment + " getEqDataSource  " + equipment.getEqDataSource());

                if (equipment.getEqDataSource().equals(DataSourceEnum.EDI_STOW)
                        || equipment.getEqDataSource().equals(DataSourceEnum.SNX)) {
                    EquipmentState ThisEqState = ThisUnitEquip.getUeEquipmentState();
                    EqUnitRoleEnum UeRole = ThisUnitEquip.getUeEqRole();
                    EquipClassEnum equipClass = ThisUnitEquip.getUeEquipment().getEqEquipType().getEqtypClass();
                    LOGGER.info("getUeEquipmentState    " + ThisEqState + "     UeRole  " + UeRole + "      equipClass  " + equipClass);


                    if (EqUnitRoleEnum.PRIMARY.equals(UeRole) && EquipClassEnum.CONTAINER.equals(equipClass)) {
                        //String containerIdFull = ThisUnit.getUnitId();
                        String originalEquipmentOwner = ThisEqState.getEqsEqOwner().getBzuId();
                        String originalEquipmentOperator = ThisEqState.getEqsEqOperator().getBzuId();

                        /*
                        * Check the Original Equipment, if the owner is Maersk, the update the stripped empty operator to MAE
                        */
                        LOGGER.info("The Equipment Original Owner is " + originalEquipmentOwner + " Operator is " + originalEquipmentOperator);

                        boolean isOwnerIDInManipulationList = isOwnerIdInOperatorManipulatioList(originalEquipmentOwner);
                        if (isOwnerIDInManipulationList)
                            LOGGER.info("The Equipment Original Owner ID " + originalEquipmentOwner + " is available in the Manipulation List");
                        if (isOwnerIDInManipulationList && (originalEquipmentOwner == null || originalEquipmentOwner.length() < 4)) {
                            String newOwnerCode = get4LetterOwnerCode(originalEquipmentOwner);
                            if (newOwnerCode != null && newOwnerCode.length() > 3) {
                                ScopedBizUnit changedContainerOwner = ScopedBizUnit.findScopedBizUnit(newOwnerCode, BizRoleEnum.LINEOP);
                                LOGGER.info("New 4 letter owner code is " + changedContainerOwner.getBzuId());
                                EquipmentState.upgradeEqOwner(equipment, changedContainerOwner, DataSourceEnum.SNX);
                            } else {
                                sendMailAndReturn("There is no valid 4 letter owner code in configuration for " + originalEquipmentOwner);
                            }
                        }
                    }
                }
                LOGGER.info("Update Successful.")
            }
            catch (Exception e) {
                LOGGER.error("Update Failed. Exception [" + e + "].");
                sendMail("Update Failed.", e);
            }
            finally {
                LOGGER.info("MATUpdateEquipmentOwnerOnUnitCreate Execution Ended.")
            }
        }
    }

    private void sendMailAndReturn(String inMessage) {
        LOGGER.error(inMessage);
        //sendMail(inMessage, null);
        return;
    }

    private void sendMail(String inMessage, Exception inException) {
        LOGGER.error(inMessage, inException);
    }

    /*public boolean isValidEvent(Object event) {
        try {
            def eventType = event.event.getEventTypeId();
            eventType = eventType != null ? eventType : "";
            if (eventType.equalsIgnoreCase("UNIT_CREATE")) {
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace()
        }
        return false;
    }*/

    public boolean isOwnerIdInOperatorManipulatioList(String inEquipmentOwnerId) {
        GeneralReference reference = GeneralReference.findUniqueEntryById("MATSON", "OPERATOR", "OWNER", "MAPPING");
        String operatorAsString = reference.getRefValue1() != null ? reference.getRefValue1() : null;
        LOGGER.info("GeneralReference.findUniqueEntryById(\"MATSON\", \"OPERATOR\", \"OWNER\", \"MAPPING\") = " + operatorAsString);
        if (operatorAsString != null && !operatorAsString.isEmpty()) {
            String[] operators = operatorAsString.split("\\,");
            for (String operator : operators) {
                LOGGER.info("Operator " + operator + "Matching against" + inEquipmentOwnerId);
                if (inEquipmentOwnerId.equalsIgnoreCase(operator)) {
                    LOGGER.info("Match Found")
                    return true;
                }
            }
        }
        return false;
    }

    String get4LetterOwnerCode(String inEquipmentOwnerId) {
        GeneralReference reference = GeneralReference.findUniqueEntryById("MATSON", "OPERATOR", "OWNER", inEquipmentOwnerId);
        LOGGER.info("GeneralReference.findUniqueEntryById(\"MATSON\", \"OPERATOR\", \"OWNER\", " + inEquipmentOwnerId + ")" + reference.getRefValue1());
        return reference.getRefValue1();
    }

}

