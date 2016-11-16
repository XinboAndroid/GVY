import com.navis.external.framework.entity.AbstractEntityLifecycleInterceptor
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChanges
import com.navis.external.framework.util.EFieldChangesView
import com.navis.inventory.InventoryField;
import com.navis.inventory.business.units.Unit;
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.EqBaseOrder
import com.navis.inventory.business.units.GoodsBase;
import com.navis.argo.business.reference.Shipper;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.argo.business.atoms.UnitCategoryEnum;
import com.navis.argo.business.atoms.FreightKindEnum;
import com.navis.framework.business.Roastery;
import com.navis.framework.persistence.HibernateApi;
import org.apache.log4j.Logger;
import com.navis.cargo.business.model.BillOfLading;
import com.navis.inventory.business.api.UnitField;
import com.navis.cargo.business.api.CargoCompoundField;
import com.navis.cargo.InventoryCargoField;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.business.reference.RoutingPoint;
import com.navis.argo.business.reference.LineOperator;
import com.navis.argo.business.atoms.DataSourceEnum;
import com.navis.argo.business.reference.Equipment;
import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.business.reference.ScopedBizUnit;
import com.navis.inventory.business.atoms.UfvTransitStateEnum;
import com.navis.inventory.business.units.UnitEquipment;
import com.navis.inventory.business.units.EquipmentState;
import com.navis.argo.business.model.CarrierVisit;
import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.orders.business.api.OrdersFinder;
import com.navis.orders.business.eqorders.EquipmentOrder;

import com.navis.services.business.api.EventManager
import com.navis.services.business.rules.EventType
import com.navis.framework.business.Roastery
import com.navis.framework.portal.FieldChanges
import com.navis.argo.ArgoBizMetafield
import com.navis.argo.ArgoField

/*
* Author : Raghu Iyer
* Date Written : 02/20/2013
* Description: This groovy is used to update the unit details with BL details when BL get updated.
*
* Modified : Raghu Iyer
* Date     : 05/21/2013
* Description: Added updateUnitWithBlData method to update the unit consignee/shipper details with BL
*              consignee/shipper details using "BL_UPDATE_CONSIGNEE" general notice.
*/

public class MatUnitUpdateWithBlDetails extends GroovyInjectionBase
{
    private void getUnitListForBl(String blNbr,String blPol,String blPod1,String blPod2,String blDest,String blOrgn,String blLinrOptr){
        println("MatUnitUpdateWithBlDetails.getUnitListForBl")
        String	ownerCode = null;
        ownerCode = blLinrOptr+"U";
        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_GDS_BL_NBR,blNbr));
//			.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_ID,"GLDU0570236"));
        println("dq:::::::::"+dq)
        HibernateApi hibernate = HibernateApi.getInstance();
        List unitList  = hibernate.findEntitiesByDomainQuery(dq);
        Iterator iterUnitList = unitList.iterator()
        while (iterUnitList.hasNext())
        {
            def unit = iterUnitList.next();
            println("Before Update UnitId:::"+unit.unitId);
            println("POL:"+ unit.getFieldValue("unitRouting.rtgPOL.pointId"))
            println("POD1:"+unit.getFieldValue("unitRouting.rtgPOD1.pointId"))
            println("destination:"+unit.getFieldValue("unitGoods.gdsDestination"))
            println("Orogin:"+unit.getFieldValue("unitGoods.gdsOrigin"))
            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("equiOptr:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOperator.bzuId"))
            println("equiOwner:"+unit.getFieldValue("unitFlexString13"))
            println("Carr Owner:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOwner.bzuId"))


            def operator = LineOperator.findLineOperatorById(blLinrOptr);
            println("<<<<<<<<<<<<<<>>>>>>>>>>>>>>"+operator)
            unit.setUnitLineOperator(operator);

            def routing = unit.getUnitRouting();

            routing.setRtgPOL(RoutingPoint.findRoutingPoint(blPol));
            routing.setRtgPOD1(RoutingPoint.findRoutingPoint(blPod1));
            routing.setRtgPOD2(RoutingPoint.findRoutingPoint(blPod2));
            unit.setFieldValue("unitGoods.gdsDestination",blDest);
            unit.setFieldValue("unitGoods.gdsOrigin",blOrgn);
            unit.setUnitFlexString13(ownerCode); // Owner code

            println("After Update UnitId:::"+unit.unitId);
            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("POL:"+ unit.getFieldValue("unitRouting.rtgPOL.pointId"))
            println("POD1:"+unit.getFieldValue("unitRouting.rtgPOD1.pointId"))
            println("destination:"+unit.getFieldValue("unitGoods.gdsDestination"))
            println("Orogin:"+unit.getFieldValue("unitGoods.gdsOrigin"))

            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("equiOptr:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOperator.bzuId"))
            println("equiOwner:"+unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId"))
            println("Carr Owner:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOwner.bzuId"))

        }
    }

    private void getUnitListForBlTest(String blDest ){

        println("MatUnitUpdateWithBlDetails.getUnitListForBlTest")
        String  blNumber = "TEST123456";
        //String	blDest = "HON";
        String  blLinrOptr = "APL";
        String	blPod1 = "HIL";
        String  blPod2 = "KHI";
        String  blPol = "HIL";
        String  blOrgn = "MEL";
        String ownerCode = null;
        if (blLinrOptr != null)
        {
            ownerCode = blLinrOptr+"U";
        }

        DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_GDS_BL_NBR,blNumber));
        //.addDqPredicate(PredicateFactory.in(UnitField.UNIT_ID, "GLDU0570236"));
        println("dq:::::::::"+dq)
        HibernateApi hibernate = HibernateApi.getInstance();
        List unitList  = hibernate.findEntitiesByDomainQuery(dq);
        Iterator iterUnitList = unitList.iterator()
        while (iterUnitList.hasNext())
        {
            def unit = iterUnitList.next();
            println("Before Update UnitId:::"+unit.unitId);
            println("POL:"+ unit.getFieldValue("unitRouting.rtgPOL.pointId"))
            println("POD1:"+unit.getFieldValue("unitRouting.rtgPOD1.pointId"))
            println("destination:"+unit.getFieldValue("unitGoods.gdsDestination"))
            println("Orogin:"+unit.getFieldValue("unitGoods.gdsOrigin"))
            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("equiOptr:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOperator.bzuId"))
            println("equiOwner:"+unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId"))
            println("Carr Owner:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOwner.bzuId"))


            def operator = LineOperator.findLineOperatorById("APL");
            println("<<<<<<<<<<<<<<>>>>>>>>>>>>>>"+operator)
            unit.setUnitLineOperator(operator);

            def routing = unit.getUnitRouting();

            routing.setRtgPOL(RoutingPoint.findRoutingPoint(blPol));
            routing.setRtgPOD1(RoutingPoint.findRoutingPoint(blPod1));
            routing.setRtgPOD2(RoutingPoint.findRoutingPoint(blPod2));
            unit.setFieldValue("unitGoods.gdsDestination",blDest);
            unit.setFieldValue("unitGoods.gdsOrigin",blOrgn);
            unit.setUnitFlexString13(ownerCode);

            println("After Update UnitId:::"+unit.unitId);
            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("POL:"+ unit.getFieldValue("unitRouting.rtgPOL.pointId"))
            println("POD1:"+unit.getFieldValue("unitRouting.rtgPOD1.pointId"))
            println("destination:"+unit.getFieldValue("unitGoods.gdsDestination"))
            println("Orogin:"+unit.getFieldValue("unitGoods.gdsOrigin"))

            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("equiOptr:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOperator.bzuId"))
            println("equiOwner:"+unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId"))
            println("Carr Owner:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOwner.bzuId"))
        }
    }

    private void getUnitListForBlUpdate(Unit unit){
        try {
            String  blNumber = unit.getFieldValue("unitGoods.gdsBlNbr");
            String	blDest = null;
            String  blLinrOptr = null;
            String	blPod1 = null;
            String  blPod2 = null;
            String  blPol = null;
            String  blOrgn = null;
            String ownerCode = null;

            println("getUnitListForBlUpdate")

            DomainQuery dq = QueryUtils.createDomainQuery("BillOfLading")
                    .addDqPredicate(PredicateFactory.eq(CargoCompoundField.BL_NBR,blNumber));
            println(dq);

            HibernateApi hibernate = HibernateApi.getInstance();
            List blList  = hibernate.findEntitiesByDomainQuery(dq);
            println("blList: "+blList.size())

            Iterator iterBlList = blList.iterator()
            while (iterBlList.hasNext())
            {
                def blDetails = iterBlList.next();
                blDest =  blDetails.blDestination;
                blLinrOptr =  blDetails.blLineOperator.bzuId;
                if (blDetails.blPol != null)
                {
                    blPol = blDetails.blPol.pointId;
                }
                if (blDetails.blPod1 != null)
                {
                    blPod1 = blDetails.blPod1.pointId;
                }
                if (blDetails.blPod2 != null)
                {
                    blPod2 = blDetails.blPod2.pointId;
                }

                blOrgn =  blDetails.blOrigin;
                if (blLinrOptr != null)
                {
                    if (blLinrOptr != "ANL")
                    {
                        ownerCode = blLinrOptr+"U";
                    }
                    else {
                        ownerCode = blLinrOptr+"C";
                    }
                }
                println("Bl Details:"+blNumber+":"+blDest+":"+blLinrOptr+":"+blOrgn+":"+blPol+":"+blPod1+":"+blPod2+":"+ownerCode);
            }

            println("Before Update UnitId:::"+unit.unitId);
            println("BL Number:"+ blNumber)
            println("POL:"+ unit.getFieldValue("unitRouting.rtgPOL.pointId"))
            println("POD1:"+unit.getFieldValue("unitRouting.rtgPOD1.pointId"))
            println("destination:"+unit.getFieldValue("unitGoods.gdsDestination"))
            println("Orogin:"+unit.getFieldValue("unitGoods.gdsOrigin"))
            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("Owner:"+unit.getFieldValue("unitFlexString13"))
            println("equiOwner:"+unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId"))
            println("Carr Owner:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOwner.bzuId"))

            def operator = LineOperator.findLineOperatorById(blLinrOptr);
            println("<<<<<<<<<<<<<<>>>>>>>>>>>>>>"+operator+"<<>>"+ownerCode)
            unit.setUnitLineOperator(operator);

            def routing = unit.getUnitRouting();

            routing.setRtgPOL(RoutingPoint.findRoutingPoint(blPol));
            routing.setRtgPOD1(RoutingPoint.findRoutingPoint(blPod1));
            routing.setRtgPOD2(RoutingPoint.findRoutingPoint(blPod2));
            unit.setFieldValue("unitGoods.gdsDestination",blDest);
            unit.setFieldValue("unitGoods.gdsOrigin",blOrgn);
            unit.setUnitFlexString13(ownerCode);

            Equipment equipment = unit.getPrimaryEq();
            UnitEquipment ue = unit.getUnitPrimaryUe();
            EquipmentState eqs = ue.getUeEquipmentState();
            ScopedBizUnit equipmentOwner = ScopedBizUnit.findEquipmentOwner(ownerCode);
            eqs.upgradeEquipmentOwner(equipmentOwner,DataSourceEnum.USER_DBA);

            println("After Update UnitId:::"+unit.unitId);
            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("POL:"+ unit.getFieldValue("unitRouting.rtgPOL.pointId"))
            println("POD1:"+unit.getFieldValue("unitRouting.rtgPOD1.pointId"))
            println("destination:"+unit.getFieldValue("unitGoods.gdsDestination"))
            println("Orogin:"+unit.getFieldValue("unitGoods.gdsOrigin"))
            println("LineOptr:::"+unit.getFieldValue("unitLineOperator.bzuId"));
            println("Owner:"+unit.getFieldValue("unitFlexString13"));
            println("equiOwner:"+unit.getFieldValue("unitPrimaryUe.ueEquipmentState.eqsEqOwner.bzuId"))
            println("Carr Owner:"+unit.getFieldValue("unitCarriageUe.ueEquipmentState.eqsEqOwner.bzuId"))
        } catch (Throwable e) {
            log("Testing " + e.getMessage());
            e.printStackTrace();
        }

    }

    private List getUnitsForUpdate(String blNbr){

        println("getUnitsForUpdate");
        List unitList = null;
        def unitShipper = "";
        def unitConsignee = "";
        def unitShipperId = "";
        def unitConsigneeId = "";
        try {

            DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                    .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_GDS_BL_NBR,blNbr));

            HibernateApi hibernate = HibernateApi.getInstance();
            unitList  = hibernate.findEntitiesByDomainQuery(dq);

        } catch (e) {
            println("Error in Bl Units::::: " + blNbr + " ::::: "+ e);
            return;
        }
        return unitList;
    }

    private void updateUnitWithBlData(CarrierVisit vesVoy){

        def blNbr = "";
        def blConsignee = "";
        def blShipper = "";
        def blConsigneeId = "";
        def blShipperId = "";
        def unitShipper = "";
        def unitConsignee = "";
        def unitShipperId = "";
        def unitConsigneeId = "";
        List unitList = null;

        try {

            DomainQuery dq = QueryUtils.createDomainQuery("BillOfLading")
                    .addDqPredicate(PredicateFactory.eq(InventoryCargoField.BL_CARRIER_VISIT,vesVoy.getCvGkey()));
            //.addDqPredicate(PredicateFactory.eq(CargoCompoundField.BL_NBR,"AU4408388"));
            println(dq);

            HibernateApi hibernate = HibernateApi.getInstance();
            List blList  = hibernate.findEntitiesByDomainQuery(dq);
            println("blList  "+blList.size())
            Iterator iterBlList = blList.iterator()
            while (iterBlList.hasNext())
            {
                blShipper = "";
                blConsignee = "";
                blShipperId = "";
                blConsigneeId = "";
                try
                {
                    def blDetails = iterBlList.next();
                    blNbr = blDetails.blNbr;

                    if ( blDetails.blShipper != null)
                    {
                        blShipperId = blDetails.blShipper.bzuId;
                        blShipper = blDetails.blShipper.bzuName;
                    }
                    if ( blDetails.blConsignee != null)
                    {
                        blConsigneeId = blDetails.blConsignee.bzuId;
                        blConsignee = blDetails.blConsignee.bzuName;
                    }

                    def consignee = ScopedBizUnit.findScopedBizUnit( blConsigneeId, BizRoleEnum.SHIPPER);
                    def shipper = ScopedBizUnit.findScopedBizUnit( blShipperId, BizRoleEnum.SHIPPER);

                    println("============================================================================================================================");
                    println ("BL Number :::: "+ blNbr + " Consignee Id :: "+ blConsigneeId  +  " Consignee Name :: "+ blConsignee  +" Shipper Id:: "+ blShipperId + " Shipper Name :: "+ blShipper);
                    println("============================================================================================================================");

                    unitList= getUnitsForUpdate(blNbr);
                    if (unitList.size() > 0)
                    {
                        Iterator iterUnitList = unitList.iterator()
                        while (iterUnitList.hasNext())
                        {
                            unitShipper = "";
                            unitConsignee = "";
                            unitShipperId = "";
                            unitConsigneeId = "";

                            def inUnit = iterUnitList.next();

                            inUnit.getUnitGoods().updateConsignee( consignee);
                            inUnit.getUnitGoods().updateShipper( shipper);


                            if ( inUnit.unitGoods != null)
                            {
                                unitConsigneeId = inUnit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuId");
                                unitConsignee = inUnit.getFieldValue("unitGoods.gdsConsigneeBzu.bzuName");
                                unitShipperId = inUnit.getFieldValue("unitGoods.gdsShipperBzu.bzuId");
                                unitShipper = inUnit.getFieldValue("unitGoods.gdsShipperBzu.bzuName");
                                println ("Before Unit Number :::: "+ inUnit.unitId + " Consignee Id :: "+ unitConsigneeId  +  " Consignee Name :: "+ unitConsignee  +" Shipper Id:: "+ unitShipperId + " Shipper Name :: "+ unitShipper);
                            }
                        }
                    }

                }
                catch (e)
                {
                    println("Error in Bl ::::: " + blNbr + " ::::: "+ e);
                }
            }

        } catch (e) {
            println("Testing " + e);

        }

    }

    private String getBlDest(String blNbr){

        def blDest = "";

        try {

            DomainQuery dq = QueryUtils.createDomainQuery("BillOfLading")
                    .addDqPredicate(PredicateFactory.eq(CargoCompoundField.BL_NBR,blNbr));
            println("dq:::::::::"+dq);

            HibernateApi hibernate = HibernateApi.getInstance();
            List blList  = hibernate.findEntitiesByDomainQuery(dq);
            println("blList  "+blList.size())
            Iterator iterBlList = blList.iterator()
            while (iterBlList.hasNext())
            {
                def blDetails = iterBlList.next();
                blDest = blDetails.blDestination;
                println("BL Destination:"+blDest);
            }
        } catch (e) {
            println("Testing " + e);
        }
        return blDest;
    }

    private void updateUnitWithBlDest(String unitId){

        def blNbr = "";
        def blDest = "";

        try {

            DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                    .addDqPredicate(PredicateFactory.in(UnitField.UNIT_ID, unitId));
            println("dq:::::::::"+dq)
            HibernateApi hibernate = HibernateApi.getInstance();
            List unitList  = hibernate.findEntitiesByDomainQuery(dq);
            Iterator iterUnitList = unitList.iterator()
            while (iterUnitList.hasNext())
            {
                def unit = iterUnitList.next();
                println("UnitId:::"+unit.unitId);
                println("destination:"+unit.getFieldValue("unitGoods.gdsDestination"));
                blNbr = unit.getFieldValue("unitGoods.gdsBlNbr");
                println("blNbr:"+blNbr);
                blDest = this.getBlDest(blNbr);
                println("blDest:"+blDest);

                unit.setFieldValue("unitGoods.gdsDestination",blDest);


            }
        } catch (e) {
            println("Testing " + e);

        }
    }

    private void updateUnitWithBookingShipperAndUCC(String unitId){

        try {

            DomainQuery dq = QueryUtils.createDomainQuery("Unit")
                    .addDqPredicate(PredicateFactory.in(UnitField.UNIT_ID, unitId))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CATEGORY, UnitCategoryEnum.STORAGE))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_FREIGHT_KIND, FreightKindEnum.MTY))
                    .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CURRENT_UFV_TRANSIT_STATE, UfvTransitStateEnum.S40_YARD));

            println("dq:::::::::"+dq)
            HibernateApi hibernate = HibernateApi.getInstance();
            List unitList  = hibernate.findEntitiesByDomainQuery(dq);
            Iterator iterUnitList = unitList.iterator()
            while (iterUnitList.hasNext())
            {
                def unit = iterUnitList.next();
                println("UnitId:::"+unit.unitId);


                println("FInding booking : "+unit.getUnitPrimaryUe().getUeDepartureOrderItem());
                EqBaseOrder order = unit.getUnitPrimaryUe().getUeDepartureOrderItem().getEqboiOrder();

                /*OrdersFinder ordersFinder = (OrdersFinder)Roastery.getBean("ordersFinder");
                EquipmentOrder eqOrder = ordersFinder.findEquipmentOrderByGkey(order.getEqboGkey())*/
                EquipmentOrder eqOrder = EquipmentOrder.resolveEqoFromEqbo(order);
                println("Found Order : "+eqOrder);
                unit.getUnitGoods().setFieldValue("gdsShipperBzu", eqOrder.getEqoShipper())
                println("Updated Shipper into UNIT : "+ eqOrder.getShipperAsString())
                println("UCC value : "+unit.getUnitFlexString15());
                if (unit.getUnitFlexString15().equalsIgnoreCase("CP")) {
                    unit.setUnitFlexString15("RB");
                    println("UCC Updated with RB");
                }

            }
        } catch (e) {
            println("Testing " + e);

        }
    }
}

