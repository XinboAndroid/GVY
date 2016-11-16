/*
 * Copyright (c) 2015 Navis LLC. All Rights Reserved.
 *
 */
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.persistence.hibernate.CarinaPersistenceCallback
import com.navis.framework.persistence.hibernate.PersistenceTemplate
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.context.UserContextUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.util.message.MessageCollector
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.atoms.UnitVisitStateEnum
import com.navis.inventory.business.units.GoodsBase
import com.navis.xpscache.util.BatchKeys
import org.apache.log4j.Logger

/**
 * This groovy plug-in is created from an upgrade action InventoryUpgradeAction250011 and can be executed as groovy job if required.
 * This groovy plug-in will calculate top 3 important hazards and save as a string in the column "Hzd UNNbrs" in unit screen.
 * This processes the departed and retired units.
 */
public class CalculateThreeMainHazardsForDepartedAndRetiredUnits {

    public void execute(Map parameters) {
        int countSuccess = 0;
        PersistenceTemplate pt = new PersistenceTemplate(UserContextUtils.getSystemUserContext());
        pt.invoke(new CarinaPersistenceCallback() {
            protected void doInTransaction() {
                try {
                    try {
                        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.GOODS_BASE)
                                .addDqPredicate(PredicateFactory.isNotNull(UnitField.GDS_HAZARDS))
                                .addDqPredicate(PredicateFactory.
                                in(UnitField.GDS_UNIT_VIST_STATE, Arrays.asList(UnitVisitStateEnum.DEPARTED, UnitVisitStateEnum.RETIRED)))
                                .addDqPredicate(PredicateFactory.isNull(InventoryField.GDS_THREE_MAIN_HAZARD_U_N_NUMBERS))
                                .setDqMaxResults(MAX_QUERY_RETURN_SIZE);
                        final Serializable[] primaryKeys = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq);
                        BatchKeys br = new BatchKeys(primaryKeys, GOODSBASE_BATCH_SIZE);


                        for (Iterator<Serializable[]> itr = br.getIterator(); itr.hasNext();) {
                            final Serializable[] goodBaseGkeys = itr.next();
                            MessageCollector msgCollector =
                                    new PersistenceTemplate(UserContextUtils.getSystemUserContext()).invoke(new CarinaPersistenceCallback() {
                                        @Override
                                        protected void doInTransaction() {
                                            for (Serializable gbGkey : goodBaseGkeys) {
                                                GoodsBase goods = GoodsBase.hydrate(gbGkey);
                                                if (goods == null) {
                                                    LOGGER.error("No GoodsBase found for gkey " + gbGkey);
                                                } else if (goods.getGdsUnit() == null) {
                                                    LOGGER.error("No Unit found for Goods with gkey " + gbGkey);
                                                } else {
                                                    goods.calculateDenormalizedHazardUNFields();
                                                    countSuccess++;
                                                }
                                            }
                                        }
                                    });
                        }
                    } catch (Exception e) {
                        LOGGER.error("doInTransaction: exception in denormalizedHazardUNFields for departed/retired units: " + e);
                    }
                } catch (Exception e) {
                    LOGGER.error("doInTransaction: exception in calculateUNHazards for departed/retired units: " + e);
                }
            }
        });
        LOGGER.info("Number of records processed successfully : " + countSuccess);
    }

    private static final Logger LOGGER = Logger.getLogger(CalculateThreeMainHazardsForDepartedAndRetiredUnits.class);
    private static final int GOODSBASE_BATCH_SIZE = 100;
    private static final int MAX_QUERY_RETURN_SIZE = 50000;
}
