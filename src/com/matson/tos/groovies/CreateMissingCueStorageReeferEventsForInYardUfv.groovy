/*
 * Copyright (c) 2011 Navis LLC. All Rights Reserved.
 *
 */

import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi
import com.navis.argo.business.atoms.LocTypeEnum
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.inventory.InventoryEntity
import com.navis.inventory.business.api.UnitField
import com.navis.inventory.business.units.UnitEventExtractManager
import com.navis.inventory.business.units.UnitFacilityVisit
import org.apache.log4j.Logger
import com.navis.argo.business.api.ArgoUtils

/**
 * This is a Pre-Deployable Groovy Plug-in to create missing STORAGE/REEFER event in CUE for In Yard containers.
 * Murali R 07/27/2011
 */
public class CreateMissingCueStorageReeferEventsForInYardUfv extends GroovyApi {

    public void execute(Map parameters) {

        UserContext context = ContextHelper.getThreadUserContext();
        Date timeNow = ArgoUtils.convertDateToLocalDateTime(ArgoUtils.timeNow(), context.getTimeZone());

        LOGGER.info("Groovy : CreateMissingCueStorageReeferEventsForInYardUfv - starts! " + timeNow);
        System.out.println("Groovy : CreateMissingCueStorageReeferEventsForInYardUfv - starts! " + timeNow);

        //For each In Yard UFV create STORAGE - CUE if it doesn't exist
        DomainQuery dq = QueryUtils.createDomainQuery(InventoryEntity.UNIT_FACILITY_VISIT)
                .addDqPredicate(PredicateFactory.eq(UnitField.UFV_CURRENT_POSITION_TYPE, LocTypeEnum.YARD));
        //dq.setMaxResults(5000);
        //dq.setRequireTotalCount(false);



        int recordCount = 0;
        List ufvGkeys = HibernateApi.getInstance().findPrimaryKeysByDomainQuery(dq);
        System.out.println("Groovy CreateMissingCueStorageReeferEventsForInYardUfv -- found Ufv primary keys " + ufvGkeys.size());
        if (!ufvGkeys.isEmpty()) {
            logInfo("About to verify STORAGE /REEFER events if it doesn't exist for first set of UFVs " + ufvGkeys.size());
            System.out.println("About to verify STORAGE /REEFER events if it doesn't exist for first set of UFVs " + ufvGkeys.size());
            for (int i = 0; i < ufvGkeys.size(); i++) {
                if (recordCount > 500) {
                    HibernateApi.getInstance().flush();
                    LOGGER.info("Groovy : CreateMissingCueStorageReeferEventsForInYardUfv - committed " + recordCount);
                    System.out.println("Groovy : CreateMissingCueStorageReeferEventsForInYardUfv  - committed " + recordCount);
                    recordCount = 0;
                }
                recordCount++;
                Serializable ufvGkey = ufvGkeys[i];
                UnitFacilityVisit ufv = (UnitFacilityVisit) HibernateApi.getInstance().load(UnitFacilityVisit.class, ufvGkey);
                UnitEventExtractManager.createStorageChargeIfMissing(ufv);
                if (ufv.getUfvUnit().isReefer()) {
                    UnitEventExtractManager.createReeferChargeIfMissing(ufv);
                }
            }
        }
        HibernateApi.getInstance().flush();
        LOGGER.info("Groovy : CreateMissingCueStorageReeferEventsForInYardUfv - FINAL committed " + recordCount);
        System.out.println("Groovy : CreateMissingCueStorageReeferEventsForInYardUfv  - FINAL committed " + recordCount);
        LOGGER.info("Groovy : CreateMissingCueStorageReeferEventsForInYardUfv - Ends " + timeNow);
        System.out.println("Groovy : CreateMissingCueStorageReeferEventsForInYardUfv - Ends " + timeNow);

    }

    private static Serializable[] getUfvGkeys(DomainQuery inDq) {
        return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(inDq);
    }

    protected final HibernateApi _hibernateApi;
    private static final Logger LOGGER = Logger.getLogger(CreateMissingCueStorageReeferEventsForInYardUfv.class);
}
