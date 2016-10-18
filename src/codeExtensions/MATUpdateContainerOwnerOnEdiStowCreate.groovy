package codeExtensions

import com.navis.argo.ArgoBizMetafield
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.Container
import com.navis.argo.business.reference.Equipment
import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.metafields.Metafield
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.portal.FieldChanges
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 * @author Keerthi Ramachandran
 * @since 6/2/2016
 * <p>MATUpdateContainerOwnerOnEdiCreate is ..</p> 
 */
class MATUpdateContainerOwnerOnEdiStowCreate extends AbstractEntityLifecycleInterceptor {

    public void onCreate(EEntityView inEntity, EFieldChangesView inOriginalFieldChanges, EFieldChanges inMoreFieldChanges) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("Inside MATUpdateContainerOwnerOnEdiStowCreate");
        LOGGER.info("inOriginalFieldChanges.hasFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER_ID) " + inOriginalFieldChanges.hasFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER_ID));
        LOGGER.info("inOriginalFieldChanges.hasFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER) " + inOriginalFieldChanges.hasFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER));
        LOGGER.info("inMoreFieldChanges.hasFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER_ID) " + inMoreFieldChanges.hasFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER_ID));
        LOGGER.info("inMoreFieldChanges.hasFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER) " + inMoreFieldChanges.hasFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER));
        /*DataSourceEnum dataSource = inOriginalFieldChanges.findFieldChange(ArgoGuiMetafield.EQ_DATA_SOURCE).getNewValue() as DataSourceEnum;*/
        LOGGER.info("inOriginalFieldChanges.getFieldChangeCount()" + inOriginalFieldChanges.getFieldChangeCount());
        LOGGER.info("inMoreFieldChanges.getFieldChangeCount()" + inMoreFieldChanges.getFieldChangeCount());
        // LOGGER.info("EquipClassEnum.CONTAINER.equals(inOriginalFieldChanges.findFieldChange(ArgoGuiMetafield.EQ_CLASS) as EquipClassEnum)"
        //        +EquipClassEnum.CONTAINER.equals(inOriginalFieldChanges.findFieldChange(ArgoGuiMetafield.EQ_CLASS) as EquipClassEnum));
        FieldChanges changes = inOriginalFieldChanges as FieldChanges;


        Equipment container = inOriginalFieldChanges.findFieldChange(MetafieldIdFactory.valueOf("eqsEquipment")).getNewValue() as Equipment;
        LOGGER.info("equipment.getEquipmentOperatorId()" + container.getEquipmentOperatorId());
        LOGGER.info("equipment.getEquipmentOwnerId()" + container.getEquipmentOwnerId());
        LOGGER.info("equipment" + container);
        for (MetafieldId change : changes.getFieldIds()) {
            if (change.getFieldId().equalsIgnoreCase("eqsEquipment")) {
                Container equipment = inOriginalFieldChanges.findFieldChange(change).getNewValue() as Container;
                LOGGER.info("equipment.getEquipmentOperatorId()" + equipment.getEquipmentOperatorId());
                LOGGER.info("equipment.getEquipmentOwnerId()" + equipment.getEquipmentOwnerId());
                LOGGER.info("equipment" + equipment);
            }
            LOGGER.info("inOriginalFieldChanges.findFieldChange(" + change + ") \t" + inOriginalFieldChanges.findFieldChange(change));
            LOGGER.info("change.getFieldId()" + change.getFieldId());
        }

        //Equipment state = inOriginalFieldChanges.findFieldChange(ArgoBizMetafield.EQui)
        //LOGGER.info("dataSource" + dataSource.toString());
        if (true /*DataSourceEnum.EDI_STOW.equals(dataSource)*/) {
            if (inOriginalFieldChanges.hasFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER_ID)) {
                String equipmentOwnerId = inOriginalFieldChanges.findFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER_ID).getNewValue() as String;
                LOGGER.info(inOriginalFieldChanges.findFieldChange(ArgoBizMetafield.EQUIPMENT_OWNER_ID).getNewValue());
                if (equipmentOwnerId != null && !equipmentOwnerId.isEmpty()) {
                    boolean computeOperator = isOwnerIdInOperatorManipulatioList(equipmentOwnerId);
                    LOGGER.info("isOwnerIdInOperatorManipulatioList(" + equipmentOwnerId + ")" + computeOperator);
                    LOGGER.info("isOwnerIdInOperatorManipulatioList(APL) " + isOwnerIdInOperatorManipulatioList("APL"));
                    LOGGER.info("isOwnerIdInOperatorManipulatioList(MAT) " + isOwnerIdInOperatorManipulatioList("MAT"));
                    if (computeOperator) {
                        String newOperator = getNewOperatorForOwner(equipmentOwnerId);
                        LOGGER.info("getNewOperatorForOwner(" + equipmentOwnerId + ")" + getNewOperatorForOwner(equipmentOwnerId));
                        inMoreFieldChanges.setFieldChange(ArgoBizMetafield.EQUIPMENT_OPERATOR_ID, newOperator);
                    }
                }
            }
        }
    }

    boolean isOwnerIdInOperatorManipulatioList(String inEquipmentOwnerId) {
        GeneralReference reference = GeneralReference.findUniqueEntryById("MATSON", "OPERATOR", "OWNER", "MAPPING");
        String operatorAsString = reference.getRefValue1() != null ? reference.getRefValue1() : null;
        LOGGER.debug("GeneralReference.findUniqueEntryById(\"MATSON\", \"OPERATOR\", \"OWNER\", \"MAPPING\") = " + operatorAsString);
        if (operatorAsString != null && !operatorAsString.isEmpty()) {
            String[] operators = operatorAsString.split("\\,");
            for (String operator : operators) {
                LOGGER.debug("Operator " + operator + "Matching against" + inEquipmentOwnerId);
                if (inEquipmentOwnerId.equalsIgnoreCase(operator)) {
                    LOGGER.debug("Match Found")
                    return true;
                }
            }
        }
        return false;
    }

    String getNewOperatorForOwner(String inEquipmentOwnerId) {
        GeneralReference reference = GeneralReference.findUniqueEntryById("MATSON", "OPERATOR", "OWNER", inEquipmentOwnerId);
        LOGGER.debug("GeneralReference.findUniqueEntryById(\"MATSON\", \"OPERATOR\", \"OWNER\", " + inEquipmentOwnerId + ")" + reference.getRefValue1());
        return reference.getRefValue1();
    }

    private Logger LOGGER = Logger.getLogger(MATUpdateContainerOwnerOnEdiStowCreate.class);
}
