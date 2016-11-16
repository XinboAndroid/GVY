/*
 * Copyright (c) 2011 Zebra Technologies Corp. All Rights Reserved.
 * $Id: UpdateGrossWeightForDrayInContainer.groovy,v 1.1 2016/10/05 21:10:22 vnatesan Exp $
 */


import com.navis.road.business.workflow.TransactionAndVisitHolder
import com.navis.road.business.model.TruckTransaction
import com.navis.argo.business.reference.Container
import com.navis.inventory.business.units.Unit
import com.navis.argo.business.api.GroovyApi

/**
 * new calculated gross weight in the gate screen will be updated to unit's gross weight
 */

public class UpdateGrossWeightForDrayInContainer extends GroovyApi {

    public void execute(TransactionAndVisitHolder dao, api) {
        logWarn("Groovy UpdateGrossWeightForDrayInContainer Start...");
        TruckTransaction tran = dao.tran
        Unit unit = tran.tranUnit
        if (unit == null) {
            return
        }
        if (tran.getTranCtrGrossWeight() != null) {
            unit.updateGoodsAndCtrWtKg(tran.getTranCtrGrossWeight());
        }
        logWarn("Groovy UpdateGrossWeightForDrayInContainer End...");
    }
}