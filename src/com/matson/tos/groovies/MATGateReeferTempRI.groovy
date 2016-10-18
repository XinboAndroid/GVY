/*
 * Copyright (c) 2016 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EquipRfrTypeEnum
import com.navis.argo.business.reference.Container
import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.framework.metafields.Metafield
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.util.internationalization.ITranslationContext
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.internationalization.TranslationUtils
import com.navis.framework.util.message.MessageLevel
import com.navis.inventory.business.units.Unit
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils

/**
 * Validates and applies the reefer temperature entered in the gate screen.
 * The reefer temperature is captured in a unit flex field.
 * The validation rules will result in an error if any of the conditions below is true:
 * 1) Any Unit with KFF
 * 2) Any Unit with AMB
 * 3) Non-reefer with temp. entered
 * 4) Reefer equipment and reefer temperature is not entered.
 *
 * Date: Feb. 10, 2016
 * Peter Seiler
 * JIRA: CSDV-3161
 *
 * Copy the MATGateReeferTemp to make a new code extension specificly for the Dray-in
 * Make changes to allow clerk to adjust temperature on receiving back a split container
 *
 */

public class MATGateReeferTempRI extends AbstractGateTaskInterceptor implements EGateTaskInterceptor
{
    // These properties defined in the resource bundle

    // {0} translated field name
    // {1} equipment type
    public static PropertyKey REEFER_TEMP_NOT_ALLOWED = PropertyKeyFactory.valueOf("gate.reefer_temp_not_allowed")
    // {0} translated field name
    public static PropertyKey REEFER_TEMP_INVALID = PropertyKeyFactory.valueOf("gate.reefer_temp_invalid")

    public static String REEFER_TEMP_FLEX_FIELD = "ufvFlexString07"
    public static String AMB_REEFER_TEMP_STRING = "AMB"
    public static String KFF_REEFER_TEMP_STRING = "KFF"

    public void execute(TransactionAndVisitHolder dao)
    {

        /* Execute the built-in logic got the business task. */
        executeInternal(dao);

        TruckTransaction tran = dao.tran;
        Container container = tran.tranContainer;

        // No container, exit
        if (container == null) {
            return
        }

        Unit unit = tran.getTranUnit();

        if (unit == null)
            return;

        EquipRfrTypeEnum rfrType = container.eqRfrType;
        String fieldValue = tran.getFieldString(getFlexFieldMetafieldId(REEFER_TEMP_FLEX_FIELD));
        String fieldName = getFlexFieldTranslatedName(REEFER_TEMP_FLEX_FIELD);
        String eqTypeId = container.eqEquipType.eqtypId;

        TranSubTypeEnum tranType = tran.getTranSubType();

        if(StringUtils.isEmpty(fieldValue))
        {
            return;
        }

        if(fieldValue.equals(AMB_REEFER_TEMP_STRING || fieldValue.equals(KFF_REEFER_TEMP_STRING)))
        {
            return;
        }

        // NON-REEFER WITH TEMP SETTING ENTERED (NOT AMB or KFF)
        if (EquipRfrTypeEnum.NON_RFR.equals(rfrType))
        {
            RoadBizUtil.appendMessage(MessageLevel.SEVERE, REEFER_TEMP_NOT_ALLOWED, fieldName, eqTypeId);
            return;
        }

        // Reefer equipment type and non-empty temp value.

        Double reefer1TempC = 0.0;
        Double reefer1TempF = 0.0;
        Double reefer2TempC = null;
        Double reefer2TempF = null;

        /* parse out both reefer temperatures */

        String Temp1Txt = null;  //A6
        String Temp2Txt = null;  //A6

        /* find if there is a slash in the string */

        int SlashPos = fieldValue.indexOf('/');  //A6

        if (SlashPos == -1)  //A6
        {
            /* there is no slash assume only the first temperature is specified. */
            Temp1Txt = fieldValue;  //A6
        }
        else
        {
            /* two temperature were entered parse them both */

            def tokens = fieldValue.split('/'); //A8
            Temp1Txt = tokens[1];
            Temp2Txt = tokens[2];
        }


        // Parse and convert temp 1
        try  //A6
        {
            /* convert the first entry to numeric */
            reefer1TempF = Double.parseDouble(Temp1Txt);  //A6
            reefer1TempC = this.fahrenheitToCelsius(reefer1TempF);  //A6

            // if dual temp, parse and convert temp 2
            if (Temp2Txt != null)
            {
                reefer2TempF = Double.parseDouble(Temp2Txt);    //A6
                reefer2TempC = this.fahrenheitToCelsius(reefer2TempF);  //A6
            }
        }
        catch (Exception e)
        {
            RoadBizUtil.appendMessage(MessageLevel.SEVERE, REEFER_TEMP_INVALID, fieldName);
            this.log("Exception parsing temperatures: " + e);
            return;
        }

        //gets the unit temp to check for temp discrepancy
        if (unit != null && unit.getUnitGoods() != null && unit.getUnitGoods().getGdsReeferRqmnts() != null)
        {
            unit.getUnitGoods().getGdsReeferRqmnts().setRfreqTempRequiredC(reefer1TempC);
            unit.getUnitGoods().getGdsReeferRqmnts().setRfreqTempLimitMinC(reefer1TempC);
            unit.getUnitGoods().getGdsReeferRqmnts().setRfreqTempLimitMaxC(reefer2TempC);
        }
        tran.tranTempRequired = reefer1TempC;
    }

    // Returns the MetafieldId of the flex field on the truck transaction.
    private MetafieldId getFlexFieldMetafieldId(String idString) {
        MetafieldIdFactory.valueOf("tran${idString[0].toUpperCase()}${idString[1..-1]}")
    }

    // Returns the user-friendly name for the flex field.
    private String getFlexFieldTranslatedName(String idString) {
        MetafieldId fieldId = MetafieldIdFactory.valueOf(idString)
        ITranslationContext translator = TranslationUtils.getTranslationContext(ContextHelper.getThreadUserContext())
        Metafield metafield = translator.getIMetafieldDictionary().findMetafield(fieldId)

        return translator.getMessageTranslator().getMessage(metafield.getLongLabelKey())
    }

    private Double fahrenheitToCelsius(Double fahrenheit)
    {
        Double result = (fahrenheit - 32) * 5.0/9;
        return result;
    }
}
