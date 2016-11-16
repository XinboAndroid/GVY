import com.navis.argo.util.FieldChangeTracker
import com.navis.inventory.InventoryField;
import com.navis.framework.metafields.MetafieldId;
import com.navis.framework.metafields.MetafieldIdList;
import com.navis.inventory.business.api.UnitField;


public class GvyUnitFieldTracker
{

    //Method post a unit Property Update event for Field change
    public void recordFieldUpdtEvent(Object tracker,Object unit)
    {
        try{
            def changes = tracker.getChanges(unit);
            if(changes != null && changes.getFieldChangeCount() != 0) {
                unit.recordUnitEvent(com.navis.argo.business.atoms.EventEnum.UNIT_PROPERTY_UPDATE,changes , "Field Update");
            }
        }catch(Exception e){
            e.printStacktrace()
        }
    }

    //Method maps the Field changed
    public FieldChangeTracker createFieldChangeTracker(Object unit)
    {
        MetafieldId [] metaFieldId = null
        try{
            metaFieldId = [InventoryField.UNIT_VISIT_STATE,InventoryField.UNIT_DECLARED_IB_CV, InventoryField.UNIT_CATEGORY, InventoryField.UNIT_FREIGHT_KIND, InventoryField.UNIT_DRAY_STATUS, InventoryField.UNIT_SPECIAL_STOW, InventoryField.UNIT_SPECIAL_STOW2, InventoryField.UNIT_SPECIAL_STOW3, InventoryField.UNIT_DECK_RQMNT,InventoryField.UNIT_REQUIRES_POWER,InventoryField.UNIT_IS_POWERED, InventoryField.UNIT_WANT_POWERED, InventoryField.UNIT_POWER_RQST_TIME, InventoryField.UNIT_IS_OOG, InventoryField.UNIT_OOG_BACK_CM, InventoryField.UNIT_OOG_FRONT_CM, InventoryField.UNIT_OOG_LEFT_CM, InventoryField.UNIT_OOG_RIGHT_CM, InventoryField.UNIT_OOG_TOP_CM,InventoryField.UNIT_LINE_OPERATOR,InventoryField.UNIT_GOODS_AND_CTR_WT_KG, InventoryField.UNIT_GOODS_AND_CTR_WT_KG_ADVISED, InventoryField.UNIT_GOODS_AND_CTR_WT_KG_GATE_MEASURED, InventoryField.UNIT_GOODS_AND_CTR_WT_KG_YARD_MEASURED, InventoryField.UNIT_SEAL_NBR1, InventoryField.UNIT_SEAL_NBR2, InventoryField.UNIT_SEAL_NBR3, InventoryField.UNIT_SEAL_NBR4, InventoryField.UNIT_REMARK, InventoryField.UNIT_FLEX_STRING01,InventoryField.UNIT_FLEX_STRING02, InventoryField.UNIT_FLEX_STRING03, InventoryField.UNIT_FLEX_STRING04, InventoryField.UNIT_FLEX_STRING05, InventoryField.UNIT_FLEX_STRING06, InventoryField.UNIT_FLEX_STRING07, InventoryField.UNIT_FLEX_STRING08, InventoryField.UNIT_FLEX_STRING09, InventoryField.UNIT_FLEX_STRING10, InventoryField.UNIT_FLEX_STRING11,InventoryField.UNIT_FLEX_STRING12, InventoryField.UNIT_FLEX_STRING13, InventoryField.UNIT_FLEX_STRING14, InventoryField.UNIT_FLEX_STRING15, UnitField.UNIT_DEPARTURE_ORDER_NBR, UnitField.UNIT_DECLARED_OB_CV, UnitField.UNIT_CURRENT_UFV_INTENDED_OB_CV, UnitField.UNIT_RTG_POL, UnitField.UNIT_RTG_POD1, UnitField.UNIT_RTG_POD2,UnitField.UNIT_RTG_OPT1, UnitField.UNIT_RTG_OPT2, UnitField.UNIT_RTG_OPT3, UnitField.UNIT_RTG_GROUP, UnitField.UNIT_RTG_RETURN_TO_LOCATION, UnitField.UNIT_RTG_TRUCKING_COMPANY, UnitField.UNIT_RTG_CARRIER_SERVICE, UnitField.UNIT_CMDY_ID, UnitField.UNIT_GDS_CONSIGNEE_AS_STRING, UnitField.UNIT_GDS_SHIPPER_AS_STRING,UnitField.UNIT_GDS_ORIGIN, UnitField.UNIT_GDS_DESTINATION, UnitField.UNIT_UE_MNR_STATUS, UnitField.UNIT_UE_PLACARDED, UnitField.UNIT_GDS_RFREQ_TEMP_SET_POINT_C, UnitField.UNIT_PRIMARY_EQ_BUILD_DATE, UnitField.UNIT_PRIMARY_EQ_CSC_EXPIRATION, UnitField.UNTI_EQS_GRADE_I_D, UnitField.UNIT_EQ_MATERIAL, InventoryField.GDS_BL_NBR,UnitField.UNIT_GDS_BL_NBR,UnitField.UNIT_TEMP_REQUIRED_C,InventoryField.GDS_COMMODITY,UnitField.EQS_EQ_OWNER_ID,UnitField.EQS_EQ_OPERATOR_ID,UnitField.EQS_EQ_EQTYPE,UnitField.UE_EQ_OWNER,UnitField.UE_EQ_OPERATOR,UnitField.UE_EQ_OWNER_ID,UnitField.UE_EQ_OPERATOR_ID,UnitField.UNIT_PRIMARY_EQTYPE,InventoryField.UFV_TIME_IN,UnitField.WI_UFV_TIME_IN]
        }catch(Exception e){
            e.printStackTrace()
        }
        return new FieldChangeTracker(unit,metaFieldId);
    }

    //Method maps the Equipment Field changed
    public FieldChangeTracker createFieldChangeTrackerEquip(Object equip)
    {
        MetafieldId [] metaFieldId = null
        try{
            metaFieldId = [UnitField.EQ_EQTYPE_ID,UnitField.EQ_EQTYPE_ISO_GROUP,UnitField.EQ_RFR_TYPE,UnitField.EQS_EQ_ID_FULL,UnitField.EQS_EQ_EQTYPE,UnitField.EQS_EQ_EQTYPE_ISO_GROUP,UnitField.EQS_EQ_RFR_TYPE,UnitField.EQS_EQ_OPERATOR_ID,UnitField.EQS_EQ_OWNER_ID,UnitField.EQS_EQ_PREVIOUS_OPERATOR_ID,UnitField.EQS_EQ_ISO_GROUP,UnitField.UE_EQ_OWNER,UnitField.UE_EQ_OPERATOR,UnitField.UE_EQ_PREVIOUS_OPERATOR,UnitField.UE_EQ_OWNER_ID,UnitField.UE_EQ_OPERATOR_ID,UnitField.UE_EQ_PREVIOUS_OPERATOR_ID,UnitField.UE_EQ_CLASS,UnitField.UNIT_OPERATOR,UnitField.UNIT_PRIMARY_EQ_ID,UnitField.UNIT_PRIMARY_EQ_ID_FULL,UnitField.UNIT_PRIMARY_EQTYPE,UnitField.UNIT_PRIMARY_RFR_TYPE,UnitField.UNIT_PRIMARY_EQUIP_ISO_GROUP,UnitField.UE_EQ_ID,UnitField.UE_EQ_ID_FULL,UnitField.UE_OPERATOR,UnitField.UE_LINE_OPERATOR,UnitField.UE_EQ_EQTYPE,UnitField.UE_EQ_EQTYPE_ID,UnitField.UE_EQ_OWNER_BIC,UnitField.UE_EQ_OWNER_SCAC,UnitField.UNIT_PRIMARY_UE_EQ_OWNER_ID,UnitField.UNIT_CARRIAGE_UE_EQ_OWNER_ID]
        }catch(Exception e){
            e.printStackTrace()
        }
        return new FieldChangeTracker(equip,metaFieldId);
    }
}


