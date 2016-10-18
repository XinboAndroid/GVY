/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */

package com.navis.road.business.adaptor.document

import com.navis.argo.ContextHelper
import com.navis.external.framework.AbstractExtensionCallback
import com.navis.external.framework.entity.EEntityView
import com.navis.external.framework.util.EFieldChangesView
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplatePropagationRequired
import com.navis.framework.util.message.MessageCollector
import com.navis.inventory.InventoryField
import com.navis.inventory.business.units.EqBaseOrderItem
import com.navis.inventory.business.units.GoodsBase
import com.navis.inventory.business.units.Unit
import com.navis.road.business.util.RoadBizUtil
import org.apache.log4j.Level
import org.apache.log4j.Logger

/**
 Whenever the booking number of a unit is updated, the value of the booking unit gets copied to the bill of lading (BL) number of the unit.
 This includes the cases when booking number is set to null, or from null to a value.
 *
 * @author <a href="mailto:balamurugan.bakthavachalam@navis.com"> Balamurugan B</a> Date: 10/21/2015
 *
 * Date: 10/21/2015: 5:41 PM
 * JIRA: CSDV-3307
 * SFDC: 00146342
 * Called from: MatsonAncUnitEquipmentInterceptor.
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 * Revision History
 * ---------------------------------------------------------------------------------------------------------------------------------------------------
 */
class MatsonAncUnitEquipmentLibrary extends AbstractExtensionCallback {
    private Logger LOGGER = Logger.getLogger(MatsonAncUnitEquipmentLibrary.class);
    public void execute(Map inParam) {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("BL NUMBER DISAPPEARED : MatsonAncUnitEquipmentLibrary");
        EEntityView entity = (EEntityView) inParam.get("ENTITY");
        EFieldChangesView fieldChangesView = (EFieldChangesView) inParam.get("ORIGINAL_FIELD_CHANGES");
        if (fieldChangesView.hasFieldChange(InventoryField.UE_DEPARTURE_ORDER_ITEM)) {
            LOGGER.info("BL NUMBER DISAPPEARED MatsonAncUnitEquipmentLibrary : UE_DEPARTURE_ORDER_ITEM");
            EqBaseOrderItem item = fieldChangesView.findFieldChange(InventoryField.UE_DEPARTURE_ORDER_ITEM).getNewValue() as EqBaseOrderItem;
            Unit unit = entity.getField(InventoryField.UE_UNIT) as Unit;
            if (unit != null) {
                LOGGER.info("BL NUMBER DISAPPEARED MatsonAncUnitEquipmentLibrary unit not null");
                Serializable gKey = unit.getUnitGoods().getGdsGkey();
                String bkgNbr = null;
                if (item != null) {
                    LOGGER.info("BL NUMBER DISAPPEARED MatsonAncUnitEquipmentLibrary Item not null");
                    bkgNbr = item.getEqboiOrder().getEqboNbr();
                }
                LOGGER.info("BL NUMBER DISAPPEARED MatsonAncUnitEquipmentLibrary booking number"+bkgNbr);
                PersistenceTemplatePropagationRequired pt = new PersistenceTemplatePropagationRequired(ContextHelper.getThreadUserContext());
                MessageCollector collector = pt.invoke(new CarinaPersistenceCallback() {
                    protected void doInTransaction() {
                        GoodsBase goods = (GoodsBase) HibernateApi.getInstance().load(GoodsBase.class, gKey);
                        goods.setFieldValue(InventoryField.GDS_BL_NBR, bkgNbr);
                        HibernateApi.getInstance().update(goods);
                        RoadBizUtil.commit();
                    }
                });
            }
        }
    }
}