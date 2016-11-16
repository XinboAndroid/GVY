/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.Serviceable
import com.navis.argo.business.atoms.DrayStatusEnum
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.argo.business.atoms.UnitCategoryEnum
import com.navis.argo.business.model.Complex
import com.navis.argo.business.model.GeneralReference
import com.navis.argo.business.reference.Commodity
import com.navis.argo.business.reference.Equipment
import com.navis.external.services.AbstractGeneralNoticeCodeExtension
import com.navis.framework.AllOtherFrameworkPropertyKeys
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.*
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.BizFailure
import com.navis.framework.util.BizViolation
import com.navis.framework.util.message.MessageCollector
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.InventoryFacade
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UfvTransitStateEnum
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.services.business.event.Event
import com.navis.services.business.event.GroovyEvent
import com.navis.services.business.event.GroovyEventFailure
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 When user record “Set Multistop Returning”, “Set Multistop Non-Returning” and “Clear Multistop” events, through the groovy we have to manually set or
 clear the status of a unit as multi-stop by means of a service.
 *
 * @author <a href="mailto:balamurugan.bakthavachalam@navis.com"> Balamurugan B</a> Date: 07/17/2015
 *
 * Date: 07/17/2015: 5:41 PM
 * JIRA: CSDV-3025
 * SFDC: 00137329
 * Called from: General Notices
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MatsonAncMultiStopImportDelivery extends AbstractGeneralNoticeCodeExtension {
    public void execute(GroovyEvent inEvent) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info(" MatsonAncMultiStopImportDelivery execute Stared.");
        Event event = inEvent.getEvent();
        Serviceable serviceable = inEvent.getEntity();
        MessageCollector collector = getMessageCollector();
        try {

            //validation
            if (serviceable == null) {
                LOGGER.error("MatsonAncMultiStopImportDelivery, couldn't execute the groovy MatsonAncMultiStopImportDelivery since the event:" + event +
                        " has null value for serviceable entity.");
                return;
            }
            if (!serviceable instanceof Unit) {
                LOGGER.error("MatsonAncMultiStopImportDelivery, couldn't execute the groovy MatsonAncMultiStopImportDelivery since the event:" + event +
                        " is not applicable to Unit Entity.");
                return;
            }
            GeneralReference multiStopCommodityIdReference = GeneralReference.findUniqueEntryById("MATSON", "MULTISTOP", "COMMODITY_ID");
            if (multiStopCommodityIdReference == null || multiStopCommodityIdReference.getRefValue1() == null) {
                collector.appendMessage(BizViolation.
                        create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null, "please configure multistop Commodity Id in General Reference."));
                return;
            }
            Commodity cmdy = Commodity.findCommodity(multiStopCommodityIdReference.getRefValue1());
            if (cmdy == null) {
                collector.appendMessage(BizViolation.create(AllOtherFrameworkPropertyKeys.ERROR__NULL_MESSAGE, null,
                        "No Commodity found for the Id:" + multiStopCommodityIdReference.getRefValue1()));
                return;
            }
            Unit unit = serviceable as Unit;
            GoodsBase goodsBase = unit.getUnitGoods();
            if ("SET MULTISTOP RETURNING".equals(event.getEvntEventType().getEvnttypeId())) {
                //set commodity code
                if (goodsBase != null) {
                    goodsBase.setCommodity(cmdy);
                    HibernateApi.getInstance().update(goodsBase);
                }
                //resurrect delivered
                UnitFacilityVisit ufv = unit.getUnitActiveUfv();
                if (ufv != null && UfvTransitStateEnum.S70_DEPARTED.equals(ufv.getUfvTransitState())) {
                    BizRequest request = new BizRequest(getUserContext());
                    Serializable[] ufvGkeys = new Serializable[1];
                    ufvGkeys[0] = ufv.getUfvGkey();
                    CrudOperation crud = new CrudOperation(null, CrudOperation.TASK_UPDATE, InventoryEntity.UNIT_FACILITY_VISIT, null, ufvGkeys);
                    request.addCrudOperation(crud);
                    BizResponse response = new BizResponse();
                    INVENTORY_FACADE.resurrectUnit(request, response);
                    collector.getMessages().addAll(response.getMessages());
                } else {
                    //set Dray status to Dray out and back
                    unit.updateDrayStatus(DrayStatusEnum.OFFSITE);
                    HibernateApi.getInstance().update(unit);
                }
            } else {
                if ("SET MULTISTOP NON-RETURNING".equals(event.getEvntEventType().getEvnttypeId())) {
                    //set commodity code
                    if (goodsBase != null) {
                        goodsBase.setCommodity(cmdy);
                        HibernateApi.getInstance().update(goodsBase);
                    }
                    //set Dray status to null
                    unit.updateDrayStatus(null);
                    HibernateApi.getInstance().update(unit);

                    //cancel advised units if any.
                    BizRequest request = new BizRequest(getUserContext());
                    Serializable[] advisedUfvs = findAdvisedImportUfvGekys(unit.getPrimaryEq());
                    if (advisedUfvs != null && advisedUfvs.length > 0) {
                        CrudOperation crud =
                                new CrudOperation(null, CrudOperation.TASK_UPDATE, InventoryEntity.UNIT_FACILITY_VISIT, null, advisedUfvs);
                        request.addCrudOperation(crud);
                        BizResponse response = new BizResponse();
                        INVENTORY_FACADE.cancelAdvisedUfv(request, response);
                        collector.getMessages().addAll(response.getMessages());
                    }
                } else if ("CLEAR MULTISTOP".equals(event.getEvntEventType().getEvnttypeId())) {
                    //set commodity code to null
                    if (goodsBase != null) {
                        goodsBase.setCommodity(null);
                        HibernateApi.getInstance().update(goodsBase);
                    }
                    //set Dray status to null
                    unit.updateDrayStatus(null);
                    //cancel advised/inbound units if any.
                    BizRequest request = new BizRequest(getUserContext());
                    Serializable[] advisedUfvs = findAdvisedOrInboundImportUfvGkeys(unit.getPrimaryEq());
                    if (advisedUfvs != null && advisedUfvs.length > 0) {
                        CrudOperation crud =
                                new CrudOperation(null, CrudOperation.TASK_UPDATE, InventoryEntity.UNIT_FACILITY_VISIT, null, advisedUfvs);
                        request.addCrudOperation(crud);
                        BizResponse response = new BizResponse();
                        INVENTORY_FACADE.cancelAdvisedUfv(request, response);
                        collector.getMessages().addAll(response.getMessages());
                    }
                }
            }
        } catch (BizViolation inBizViolation) {
            collector.registerExceptions(GroovyEventFailure.create("Groovy event has failed", inBizViolation, event));
        } catch (BizFailure inBizFailure) {
            collector.registerExceptions(GroovyEventFailure.create("Groovy event has failed", inBizFailure, event));
        } catch (Throwable inException) {
            collector.registerExceptions(inException);
        }
        finally {
            LOGGER.info(" MatsonAncMultiStopImportDelivery execute Completed.");
        }
    }

    private static Serializable[] findAdvisedImportUfvGekys(Equipment inEquipment) {
        List<Unit> unitList = findUnitList(ContextHelper.getThreadComplex(), inEquipment, null, UnitCategoryEnum.IMPORT);
        if (unitList != null && !unitList.isEmpty()) {
            List<Serializable> ufvGkeyList = new ArrayList();
            for (Unit unit : unitList) {
                Set<UnitFacilityVisit> ufvSet = unit.getUnitUfvSet();
                if (ufvSet != null) {
                    for (UnitFacilityVisit ufv : ufvSet) {
                        if (ufv.isFuture()) {
                            ufvGkeyList.add(ufv.getUfvGkey());
                        }
                    }
                }
            }
            return ufvGkeyList.toArray(new Serializable[ufvGkeyList.size()]);
        }
        return null;
    }

    /**
     * Finds Container Units given Complex, Primary Equipment, VisitState and optionally one or more arrival modes.
     *
     * @param inComplex complex of the Unit
     * @param inPrimaryEq the primary equipment for the Unit.
     * @param inCategory unit category
     * @return a List of Unit
     */
    private static List<Unit> findUnitList(Complex inComplex, Equipment inPrimaryEq,
                                           LocTypeEnum[] inArrivalModes,
                                           UnitCategoryEnum inCategory) {

        Serializable cpxGkey = inComplex == null ? null : inComplex.getCpxGkey();
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_COMPLEX, cpxGkey))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CURRENT_UFV_TRANSIT_STATE, UfvTransitStateEnum.S10_ADVISED))
                .addDqPredicate(PredicateFactory.eq(UnitField.UNIT_PRIMARY_EQ, inPrimaryEq.getEqGkey()))
                .addDqOrdering(Ordering.desc(UnitField.UNIT_CREATE_TIME));

        if (inArrivalModes != null) {
            dq.addDqPredicate(PredicateFactory.in(UnitField.UNIT_DECLARED_IB_CARRIER_MODE, inArrivalModes));
        }
        if (inCategory != null) {
            dq.addDqPredicate(PredicateFactory.eq(UnitField.UNIT_CATEGORY, inCategory));
        }
        Serializable[] unitGkey = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq);

        List<Unit> unitList = new ArrayList<Unit>();
        if (unitGkey == null || unitGkey.length == 0) {
            return unitList;
        }
        for (int i = 0; i < unitGkey.length; i++) {
            Unit unit = (Unit) HibernateApi.getInstance().load(Unit.class, unitGkey[i]);
            unitList.add(unit);
        }
        return unitList;
    }

    private static Serializable[] findAdvisedOrInboundImportUfvGkeys(Equipment inPrimaryEq) {
        UfvTransitStateEnum[] states = [UfvTransitStateEnum.S10_ADVISED, UfvTransitStateEnum.S20_INBOUND];
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_PRIMARY_EQ, inPrimaryEq.getEqGkey()))
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_CATEGORY, UnitCategoryEnum.IMPORT))
                .addDqPredicate(PredicateFactory.in(UnitField.UFV_TRANSIT_STATE, states));
        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq);
    }
    private Logger LOGGER = Logger.getLogger(MatsonAncMultiStopImportDelivery.class);
    private static InventoryFacade INVENTORY_FACADE = (InventoryFacade) Roastery.getBean(InventoryFacade.BEAN_ID);
}