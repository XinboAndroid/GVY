import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EquipRfrTypeEnum
import com.navis.argo.business.reference.Container
import com.navis.framework.metafields.Metafield
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.util.internationalization.ITranslationContext
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.internationalization.TranslationUtils
import com.navis.framework.util.message.MessageLevel
import com.navis.framework.util.unit.TemperatureUnit
import com.navis.framework.util.unit.UnitUtils
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.argo.business.api.GroovyApi
import com.navis.inventory.business.units.Unit;
import com.navis.framework.util.DateUtil;

import com.navis.services.business.event.Event;
import com.navis.services.business.rules.EventType;
import com.navis.services.business.api.EventManager;
import com.navis.services.business.event.EventFieldChange;
import com.navis.framework.portal.FieldChanges

import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.ArgoRefField;

import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;

import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.road.business.model.TruckingCompany;
import com.navis.road.business.model.TruckingCompanyLine;

import com.navis.framework.business.atoms.LifeCycleStateEnum;
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.road.RoadField;
import com.navis.argo.business.reference.LineOperator;
import com.navis.road.business.atoms.TrkcStatusEnum;
import com.navis.argo.UserArgoField;
import com.navis.argo.business.security.ArgoUser;
import com.navis.security.SecurityField;


import com.navis.inventory.*
import com.navis.framework.business.Roastery
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.HibernatingEntity
import com.navis.framework.portal.FieldChanges
import com.navis.services.business.api.EventManager
import com.navis.services.business.rules.EventType

import org.apache.log4j.Logger


/*
* Author : Raghu Iyer
* Date Written : 09/15/2014
* Description: This groovy is used to update
*/

public class MatSitYbDirect extends GroovyInjectionBase
{
    def inj = new GroovyInjectionBase();

    public void getEventChanges(Object event)
    {
        try
        {
            LOGGER.warn("Calling MatSitYbDirect.getEventChanges :: " + event.event.evntAppliedBy);
            def unit = event.entity;

            def gvyBaseClass = new GroovyInjectionBase()
            Set set = event.getEvent().getEvntFieldChanges();
            Iterator iter = set.iterator();
            EventFieldChange efc;
            while ( iter.hasNext()) {
                efc = (EventFieldChange)iter.next();
                println("get feild change Value :"+efc.getMetafieldId() + "-->"+ efc.getPrevVal() + "-->"+ efc.getNewVal())

                if ("unitFreightKind".equalsIgnoreCase(efc.getMetafieldId()))
                {

                    String ctrNbr = unit.unitId;
                    String oldVal = efc.getPrevVal();
                    String newVal = efc.getNewVal();

                    println("Values changed to ::"+oldVal +"::::"+newVal);

                    if (newVal.equalsIgnoreCase("MTY")){
                        updateUnitFeilds(unit);
                    }
                }

            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public void updateUnitFeilds (Object unit){
        try
        {
            def unitId = unit.unitId;


            LOGGER.warn("<<<<unit>>>>>"+unit.unitId);
            LOGGER.warn("<<<< BEFORE >>>>>>");
            LOGGER.warn("<<<<Bl Nbr>>>>>"+unit.getFieldValue("unitGoods.gdsBlNbr"));
            LOGGER.warn("<<<<Gross Weight>>>>>"+unit.getFieldValue("unitGoodsAndCtrWtKgLong"));
            LOGGER.warn("<<<<Tare Weight>>>>>"+unit.getFieldValue("unitPrimaryUe.ueEquipment.eqTareWeightKg"));
            LOGGER.warn("<<<<Consignee>>>>>"+unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId") + "::"+ unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName"));
            LOGGER.warn("<<<<Shipper>>>>>"+unit.getFieldValue("unitGoods.gdsShipperBzu.bzuId") +"::"+unit.getFieldValue("unitGoods.gdsShipperBzu.bzuName"));
            LOGGER.warn("<<<<AvailDate>>>>>"+unit.getFieldValue("unitActiveUfv.ufvFlexDate02"));
            LOGGER.warn("<<<<DueDate>>>>>"+unit.getFieldValue("unitActiveUfv.ufvFlexDate03"));
            Double tempReq;
            if (unit.getUnitGoods().getGdsReeferRqmnts() != null){
                LOGGER.warn("<<<<Temp Required>>>>>"+unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempRequiredC());
                tempReq = unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempRequiredC();
            }

            LOGGER.warn("<<<<STIF Shpr>>>>>"+unit.getFieldValue("unitFlexString06"));
            LOGGER.warn("<<<<Unit Notes>>>>>"+unit.getFieldValue("unitRemark"));
            LOGGER.warn("<<<<CSR ID>>>>>"+unit.getFieldValue("unitFlexString03"));
            LOGGER.warn("<<<<Seal Number1>>>>>"+unit.getFieldValue("unitSealNbr1"));
            LOGGER.warn("<<<<Assigned To>>>>>"+unit.getFieldValue("unitFlexString11"));

            def blNbr = unit.getFieldValue("unitGoods.gdsBlNbr");
            Double grsWt = unit.getFieldValue("unitGoodsAndCtrWtKgLong");
            Double trWt = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqTareWeightKg");
            def consignee = unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId");
            def shipper = unit.getFieldValue("unitGoods.gdsShipperBzu.bzuId");
            def AvailDate = unit.getFieldValue("unitActiveUfv.ufvFlexDate02");
            def DueDate = unit.getFieldValue("unitActiveUfv.ufvFlexDate03");
            def stifShpr = unit.getFieldValue("unitFlexString06");
            def unitNotes = unit.getFieldValue("unitRemark");
            def csrId = unit.getFieldValue("unitFlexString03");
            def sealNbr1 = unit.getFieldValue("unitSealNbr1");
            def assignedTo = unit.getFieldValue("unitFlexString11");


            def tareWt = unit.getFieldValue("unitPrimaryUe.ueEquipment.eqTareWeightKg");



            unit.setFieldValue("unitGoods.gdsBlNbr", "");
            unit.updateGoodsAndCtrWtKg(new Double(tareWt));
            unit.getUnitGoods().updateConsignee("");
            unit.getUnitGoods().updateShipper("");
            unit.setFieldValue("unitActiveUfv.ufvFlexDate02",null);
            unit.setFieldValue("unitActiveUfv.ufvFlexDate03",null);
            if (unit.getUnitGoods().getGdsReeferRqmnts() != null){
                unit.getUnitGoods().getGdsReeferRqmnts().setRfreqTempRequiredC();
            }
            unit.setFieldValue("unitFlexString06",null);
            unit.setFieldValue("unitRemark",null);
            unit.setFieldValue("unitFlexString03",null);
            unit.setFieldValue("unitSealNbr1",null);
            unit.setFieldValue("unitFlexString03",null);

            LOGGER.warn("<<<< AFTER >>>>>>");
            LOGGER.warn("<<<<unit>>>>>"+unit.unitId);
            LOGGER.warn("<<<<Bl Nbr>>>>>"+unit.getFieldValue("unitGoods.gdsBlNbr"));
            LOGGER.warn("<<<<Gross Weight>>>>>"+unit.getFieldValue("unitGoodsAndCtrWtKgLong"));
            LOGGER.warn("<<<<Tare Weight>>>>>"+unit.getFieldValue("unitPrimaryUe.ueEquipment.eqTareWeightKg"));
            LOGGER.warn("<<<<Consignee>>>>>"+unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId") + "::"+ unit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName"));
            LOGGER.warn("<<<<Shipper>>>>>"+unit.getFieldValue("unitGoods.gdsShipperBzu.bzuId") +"::"+unit.getFieldValue("unitGoods.gdsShipperBzu.bzuName"));
            LOGGER.warn("<<<<AvailDate>>>>>"+unit.getFieldValue("unitActiveUfv.ufvFlexDate02"));
            LOGGER.warn("<<<<DueDate>>>>>"+unit.getFieldValue("unitActiveUfv.ufvFlexDate03"));
            if (unit.getUnitGoods().getGdsReeferRqmnts() != null){
                LOGGER.warn("<<<<Temp Required>>>>>"+unit.getUnitGoods().getGdsReeferRqmnts().getRfreqTempRequiredC());
            }
            LOGGER.warn("<<<<STIF Shpr>>>>>"+unit.getFieldValue("unitFlexString06"));
            LOGGER.warn("<<<<Unit Notes>>>>>"+unit.getFieldValue("unitRemark"));
            LOGGER.warn("<<<<CSR ID>>>>>"+unit.getFieldValue("unitFlexString03"));
            LOGGER.warn("<<<<Seal Number1>>>>>"+unit.getFieldValue("unitSealNbr1"));
            LOGGER.warn("<<<<Assigned To>>>>>"+unit.getFieldValue("unitFlexString11"));

            try
            {
                String eventId = "SIT_DIRECT_YB_FLD_CHNG";
                EventManager sem = (EventManager) Roastery.getBean(EventManager.BEAN_ID);
                EventType eventType = EventType.findEventType(eventId);
                FieldChanges changes = new FieldChanges();

                if (blNbr != null) changes.setFieldChange(InventoryField.GDS_BL_NBR, blNbr, null);
                if (consignee != null) changes.setFieldChange(InventoryField.GDS_CONSIGNEE_BZU, consignee , null);
                if (shipper != null) changes.setFieldChange(InventoryField.GDS_SHIPPER_BZU, shipper, null);
                changes.setFieldChange(InventoryField.UNIT_GOODS_AND_CTR_WT_KG, grsWt , trWt);
                if (AvailDate != null) changes.setFieldChange(InventoryField.UFV_FLEX_DATE02, AvailDate , null);
                if (DueDate != null) changes.setFieldChange(InventoryField.UFV_FLEX_DATE03, DueDate , null);
                if (stifShpr != null) changes.setFieldChange(InventoryField.UNIT_FLEX_STRING06, stifShpr , null);
                if (tempReq != null) changes.setFieldChange(InventoryField.RFREQ_TEMP_REQUIRED_C, tempReq , null);
                if (unitNotes != null) changes.setFieldChange(InventoryField.UNIT_REMARK, unitNotes , null);
                if (csrId != null) changes.setFieldChange(InventoryField.UNIT_FLEX_STRING03, csrId , null);
                if (sealNbr1 != null) changes.setFieldChange(InventoryField.UNIT_SEAL_NBR1, sealNbr1 , null);
                if (assignedTo != null) changes.setFieldChange(InventoryField.UNIT_FLEX_STRING11, assignedTo , null);

                unit.recordUnitEvent(eventType, changes, null);
            }catch (Exception ex){
                LOGGER.warn("<<<<Exception while recording field changes for SIT_DIRECT_YB_FLD_CHNG >>>>>"+ex);
            }



        }catch(Exception e){
            e.printStackTrace()
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MatSitYbDirect.class);
}