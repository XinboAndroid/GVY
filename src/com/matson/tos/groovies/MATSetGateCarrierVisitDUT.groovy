/*
* Copyright (c) 2015 Navis LLC. All Rights Reserved.
*
*/

package com.navis.road.business.adaptor.document

import com.navis.external.road.AbstractGateTaskInterceptor
import com.navis.external.road.EGateTaskInterceptor
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.model.TruckVisitDetails
import com.navis.argo.business.model.CarrierVisit
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.log4j.Level
import org.apache.log4j.Logger
//import com.navis.argo.ContextHelper

/**
 * This groovy customizes the carrier visit used in gate transactions
 *
 * Author: Bruno Chiarini
 * Date: 19-Sep-2015
 * JIRA: CSDV-3258
 * SFDC: 145538
 *
 * -----------------------------------------------------------------
 * Updated, cleared code and added more information in log
 * Author: Bruno Chiarini
 * Date: 23-Sep-2015
 * -----------------------------------------------------------------
 * Changed carrier visit id per customer requirement. From date to driver name.
 * Author: Bruno Chiarini
 * Date: 03-Oct-2015
 *------------------------------------------------------------------
 * Modified for facility DUT facility. Carrier visit set to driver's name.
 * Class name changed to MATSetGateCarrierDUT to differentiate from the code used for facility ANK
 * Author: Bruno Chiarini
 * Date: 06-Oct-2015
 */

public class MATSetGateCarrierVisitDUT extends AbstractGateTaskInterceptor implements EGateTaskInterceptor

{
    private Logger LOGGER = Logger.getLogger(MATSetGateCarrierVisitDUT.class);

    public void execute(TransactionAndVisitHolder inDao)

    {
        LOGGER.setLevel(Level.INFO);
        LOGGER.info("MATSetCarrierVisitDUT Execution Started.");

        /* Execute the built-in logic got the business task. */

        executeInternal(inDao);

        // get current transaction
        TruckTransaction thisTran = inDao.getTran();
        if (thisTran == null)
        {
            LOGGER.error("Truck Transaction not found!");
            return;
        }
        //LOGGER.info("Truck Transaction: " + ThisTran);

        // get transaction's truck visit
        TruckVisitDetails thisTruckVisit = thisTran.getTranTruckVisit();
        if (thisTruckVisit == null)
        {
            LOGGER.error("Truck Visit not found. "
                    + "Gate Transaction [" + thisTran.getTranNbr() + "], "
                    + ( (!thisTran.isBareChassis())
                    ? ("Unit [" + thisTran.getTranCtrNbr())
                    : ("Chassis [" + thisTran.getTranChsNbr() )
            )
                    + "]");
            return;
        }
        //LOGGER.info("Truck Visit: " + ThisTruckVisit);

        // get truck visit's carrier visit
        CarrierVisit thisCV = thisTruckVisit.getCv(thisTruckVisit);
        if (thisCV == null)
        {
            LOGGER.error("Carrier visit not found. "
                    + "Gate Transaction [" + thisTran.getTranNbr() + "], "
                    + ( (!thisTran.isBareChassis())
                    ? ("Unit [" + thisTran.getTranCtrNbr())
                    : ("Chassis [" + thisTran.getTranChsNbr() )
            )
                    + "]");
            return;
        }

        // this no longer required, changed from date to driver's name
//        Calendar cal = Calendar.getInstance();
//        Date thisDate = cal.time;                                                   //get current time

        //String truckingCoId = thisTruckVisit.getTvdtlsTrkCompany().getBzuId();

//        if (truckingCoId.length() > 10)
//        {
//            truckingCoId = truckingCoId.substring(0, 9);
//        }

//        thisCV.updateCvId( truckingCoId                     //Trucking company id
//            + "-" + thisDate.format("ddMMM", ContextHelper.getThreadFacility().getTimeZone()) );  //Transaction Date

        //new code to set carrier visit to driver's name in gate transaction
        String newCVId = "";
        String driverName = thisTruckVisit.getTvdtlsDriverName();
        if (driverName != null)
        {
            newCVId = driverName;  //driver name
        }
        else
        {
            newCVId = "NoDriver";   //making sure we get a value in case it is null
        }

        //trim to a max of 16 chars (max length allowed for carrier visit id)
        if (newCVId.length() > 16) {
            newCVId = newCVId.substring(0,16);
        }

        thisCV.updateCvId(newCVId);

        LOGGER.info("Carrier Visit Id set to ["  + thisCV.getCvId()
                + "] for transaction [" + thisTran.getTranNbr() + "], "
                + ( (!thisTran.isBareChassis())
                ? ("Unit [" + thisTran.getTranCtrNbr())
                : ("Chassis [" + thisTran.getTranChsNbr() )
        )
                + "]");
        LOGGER.info("MATSetCarrierVisitDUT Execution Ended.");
    }

}
