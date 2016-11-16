/*
*  Change   Changer  Date       Desc
*  A1       GR       12/16/11   Updated Reefer Variable for navis case#89220
*  A2       GR       02/17/12   TOS2.1 : Updt Field unitFlexString07 to UfvFlexString07
*  A3       LC       07/20/12   Create Temp discrepancy report on Dray-In
*  A4       LC       04/16/13   Check for the Receive Export Ingate, copies booking req temp to unit req temp
*  A5       LC       09/25/13   Get the trans booking temp required
*  A6       PS       07/30/15   Change to code extension.  Implement for Alaska.  Add KFF logic
*  A7       Bruno Chiarini 27-Sep-2015 Added AMB logic, added comments, reorganized code
*  A8       Bruno Chiarini 23-Oct-2015 Removed validation. Code is now used only to retrieve values from booking and
*                                       apply to unit in transaction.
*/

package com.navis.road.business.adaptor.document
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EquipRfrTypeEnum
import com.navis.argo.business.reference.Container
import com.navis.argo.business.reference.EquipGrade
import com.navis.argo.business.reference.Equipment
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
import com.navis.inventory.business.units.EquipmentState
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.ReeferRqmnts
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitEquipment
import com.navis.orders.business.eqorders.EquipmentOrder
import com.navis.orders.business.eqorders.EquipmentOrderItem
import com.navis.road.RoadPropertyKeys
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
 * Date: Aug. 14, 2015
 * Peter Seiler (A6)
 * SFDC: 142197
 * JIRA: CSDV-3161
 *
 */

public class MATGateReeferTemp extends AbstractGateTaskInterceptor implements EGateTaskInterceptor //A6
{
    // These properties defined in the resource bundle

    public static String BEAN_ID = "applyReeferTempToTranTempSetting"
    // {0} translated field name
    // {1} equipment type
    public static PropertyKey REEFER_TEMP_NOT_ALLOWED = PropertyKeyFactory.valueOf("gate.reefer_temp_not_allowed")
    // {0} translated field name
    // {1} equipment type
    public static PropertyKey REEFER_TEMP_REQUIRED = PropertyKeyFactory.valueOf("gate.reefer_temp_required")
    // {0} translated field name
    public static PropertyKey REEFER_TEMP_INVALID = PropertyKeyFactory.valueOf("gate.reefer_temp_invalid")

    public static String REEFER_TEMP_FLEX_FIELD = "ufvFlexString07" //A2
    public static String AMB_REEFER_TEMP_STRING = "AMB"
    public static String KFF_REEFER_TEMP_STRING = "KFF"
    public static String DUAL_REEFER_TEMP_STRING = "DUAL"//A6
    public static PropertyKey BOOKING_REQUIRES_KFF = PropertyKeyFactory.valueOf("gate.keep_from_freezing_required")//A6
    // {1} booking number
    public static PropertyKey BOOKING_REQUIRES_AMB = PropertyKeyFactory.valueOf("gate.ambient_required")  //A7
    // {1} booking number

    public void execute(TransactionAndVisitHolder dao)
    {

        TruckTransaction tran = dao.tran;
        Container container = tran.tranContainer;

        // No container, exit
        if (container == null) {
            return
        }

        EquipRfrTypeEnum rfrType = container.eqRfrType;
        String fieldValue = tran.getFieldString(getFlexFieldMetafieldId(REEFER_TEMP_FLEX_FIELD));
        String fieldName = getFlexFieldTranslatedName(REEFER_TEMP_FLEX_FIELD);
        String eqTypeId = container.eqEquipType.eqtypId
        String ctrNbr = tran.getTranCtrNbr();   //A6
        String dest = tran.getTranDestination();   //A6

        // GET BOOKING TEMP REQUIREMENTS (STORED IN BOOKING ITEM REMARKS)
        EquipmentOrderItem ThisEQOI = tran.getTranEqoItem();
        String bkItemRemarks = "";
        if (ThisEQOI != null)
        {
            bkItemRemarks = ThisEQOI.getEqoiRemarks();
            if (bkItemRemarks == null || bkItemRemarks.length() == 0) {
                bkItemRemarks = "";
            }
        }

        fieldValue = bkItemRemarks;                 // A8
        tran.setTranUfvFlexString07(bkItemRemarks); // A8

        this.log("Booking Item Remarks: [" + bkItemRemarks + "]");

        // CASE 1: ANY UNIT (REEFER OR NON-REEFER) WITH KFF SETTING
        if (bkItemRemarks == KFF_REEFER_TEMP_STRING)    //Booking says it should be a KF
        {
//            if (fieldValue != KFF_REEFER_TEMP_STRING )   //but field value in gate doesn't match
//            {
//                EquipmentOrder ThisEQO = tran.getTranEqo(); //Booking Number
//                RoadBizUtil.appendMessage(MessageLevel.SEVERE, BOOKING_REQUIRES_KFF, fieldName, ThisEQO.getEqboNbr());  //A6
//            }
            return;
        }

        // CASE 2: ANY UNIT (REEFER OR NON-REEFER) WITH AMB SETTING
        if (bkItemRemarks == AMB_REEFER_TEMP_STRING)     //Booking says it should be an AMB
        {
//            if (fieldValue != AMB_REEFER_TEMP_STRING )  //but field value in gate doesn't match
//            {
//                EquipmentOrder ThisEQO = tran.getTranEqo(); //Booking Number
//                RoadBizUtil.appendMessage(MessageLevel.SEVERE, BOOKING_REQUIRES_AMB, fieldName, ThisEQO.getEqboNbr());  //A6
//            }
            return;
        }

        // CASE 3: NON-REEFER WITH TEMP SETTING ENTERED (NOT AMB or KFF)
        if (EquipRfrTypeEnum.NON_RFR.equals(rfrType))
        {
            //Unit is not a reefer, check if there's a temp.
            if (StringUtils.isNotEmpty(fieldValue))
            {
                RoadBizUtil.appendMessage(MessageLevel.SEVERE, REEFER_TEMP_NOT_ALLOWED, fieldName, eqTypeId);
            }
            return;
        }

        // CASE 4: Reefer equipment types must have a value in the "Reefer Temp" flex field.
        if (!EquipRfrTypeEnum.NON_RFR.equals(rfrType)       //It's a reefer
                && StringUtils.isEmpty(fieldValue))         //and Temp field in gate is empty
        {
            RoadBizUtil.appendMessage(MessageLevel.SEVERE, REEFER_TEMP_REQUIRED, fieldName, eqTypeId, bkItemRemarks);
            return;
        }

        Unit unit = tran.getTranUnit();
        if (unit != null) {
            UnitEquipment unitEquipment = unit.getUnitPrimaryUe();
            EquipmentState eqs = unitEquipment.getUeEquipmentState();
            if (eqs != null ){
                EquipGrade equipGrade = eqs.getEqsGradeID();
                Boolean isDualBooking = bkItemRemarks!= null && bkItemRemarks.toUpperCase().contains(DUAL_REEFER_TEMP_STRING);
                Boolean isDualCtr = equipGrade!= null && (DT.equals(equipGrade.getEqgrdId()))
                if (isDualCtr) {
                    if (!isDualBooking) {
                        RoadBizUtil.appendMessage(MessageLevel.SEVERE, RoadPropertyKeys.GATE__USER_MESSAGE_3,
                                "Cannot receive an Unit of dual temperature grade against this booking with single or no temperature", null);
                        return;
                    }
                } else
                if (isDualBooking)
                {
                    RoadBizUtil.appendMessage(MessageLevel.SEVERE, RoadPropertyKeys.GATE__USER_MESSAGE_2,
                            "Unit not of type dual temp, cannot be received against this booking", null);
                    return;
                }
            }
        }
        // Reefer equipment type and non-empty temp value.

        Double reefer1TempC = 0.0;  //A6
        Double reefer1TempF = 0.0;  //A6
        Double reefer2TempC = null;  //A6
        Double reefer2TempF = null;  //A6

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
//            Temp1Txt = fieldValue[0 .. SlashPos-1];  //A6
//            Temp2Txt = fieldValue[SlashPos+1 .. value.length()-1];  //A6

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

        //gvyApi.logWarn("reeferTempC:"+reeferTempC);
        //gvyApi.logWarn("reeferTempF:"+reeferTempF);
        // Copy the temp value entered to the tranTempSetting field on the transaction so that CreateContainerVisit
        // will create a ReeferRecord
        // with this value in the rfrecSetPointTmp field.
        TranSubTypeEnum tranType = tran.getTranSubType(); //A1
        //For DrayIn Set TempRequired for RE set TempSetting
        if(TranSubTypeEnum.RI.equals(tranType)){
            unit = tran.getTranUnit();
            //gets the unit temp to check for temp discrepancy
            if (unit != null && unit.getUnitGoods() != null && unit.getUnitGoods().getGdsReeferRqmnts() != null)
            {
                Double tempRequiredC = unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempRequiredC();
                Double tempRequiredF = this.celsiusToFahrenheit(tempRequiredC);

                def blNbr = unit.getUnitGoods().getGdsBlNbr();  //A6
                int comparisonVal = 0;
                unit.updateRequiresPower(true); //A8

                //if (tempRequiredF != null && (tempRequiredF.compareTo(reefer1TempF)!= 0)) {
                //    log("Sending email to notify discrepancy in temperature");
                // }
            }
            tran.tranTempRequired = reefer1TempC;  //A6
        }
        else
        {
            tran.tranTempSetting = reefer1TempC;  //A6

            if(TranSubTypeEnum.RE.equals(tranType)){
                //for RE - copy unit's booking temp required
                def tranBooking;
                def tranBookingTemp;
                if(tran != null && tran.getTranEqoItem()!=null)  {
                    tranBooking = tran.getTranEqoNbr();  //get the booking number at the ingate
                    log("Booking Number:"+tranBooking);
                    tranBookingTemp = tran.getTranEqoItem().getEqoiTempRequired(); //get the temp req from booking
                    log("tranTempRequired---->"+tranBookingTemp);
                    log("Booking Item Remarks (temp setting): " + bkItemRemarks);
                }
                tran.tranTempRequired =  tranBookingTemp;

                /* set the unit's temperatures to be the values entered */

                GoodsBase ThisUnitGoods = null;  //A6
                ReeferRqmnts ThisReeferReq = null;  //A6

                Unit ThisUnit = tran.getTranUnit();  //A6
                if (ThisUnit != null)  //A6
                {
                    ThisUnitGoods = ThisUnit.getUnitGoods();  //A6
                    ThisUnit.updateRequiresPower(true); //A8
                }
                if (ThisUnitGoods != null)  //A6
                {
                    ThisReeferReq = ThisUnitGoods.getGdsReeferRqmnts();  //A6
                }
                // if not temp is set then use the temp in the gate
                if (ThisReeferReq != null)  //A6
                {
                    ThisReeferReq.setRfreqTempRequiredC(reefer1TempC);  //A6
                    ThisReeferReq.setRfreqTempLimitMinC(reefer1TempC);  //A6
                    ThisReeferReq.setRfreqTempLimitMaxC(reefer2TempC);  //A6
                }
            }
        }
        /* Execute the built-in logic got the business task. */
        executeInternal(dao); //A6
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

    private Double celsiusToFahrenheit(Double celsius)
    {
        Double result = (celsius * 9.0/5) + 32;
        return result;
    }
    private String DT = "DT";
    private String DV = "DV";

}
