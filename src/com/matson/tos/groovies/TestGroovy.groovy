package com.matson.tos.groovies

/**
 * Created by kramachandran on 10/7/2016.
 */
class TestGroovy {
    /*println("//Start Event ---:"+event.event.eventTypeId+ " on Unit :"+event.entity.unitId+" ----//")
*//*
 * Setting BookingNumber to BL_NBR
 *//*
    def u = event.getEntity()

    def gvyUtilObj = api.getGroovyClassInstance("GvyEventUtil")

    boolean processCmisFeed = true
    if(processCmisFeed)
    {

            Thread.sleep(2000);

        // N4 TO CMIS data processing
        println('Unit Deliver Execute Code')
        def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
        def unitDtl = unitDetails.doIt(event)

        //SERVICE MSG CHECK
        println("---------------SERVICE MSG Starts -------------------------- ")
        def gvySrvObj = api.getGroovyClassInstance("GvyCmisSrvMsgProcessor");
        gvySrvObj.processServiceMessage(unitDtl,event,api)
        println("---------------SERVICE MSG Ends -------------------------- ")

        // Deliver
        def evntDeliverObj = api.getGroovyClassInstance("GvyCmisEventUnitDeliver");
        def deliverXml = evntDeliverObj.processUnitDeliver(unitDtl,event,api)


        // sending CLS event incase of locationStatus = 'A'
        println("--------------- GN processCLSforLocStatusA Starts -------------------------- ")
        def clsXml = evntDeliverObj.processCLSforLocStatusA(deliverXml,event,api);
        println("clsXml====="+clsXml);
        api.sendXml(clsXml);
        println("--------------- GN processCLSforLocStatusA Ends -------------------------- ")
        //end CLS
    }
    println("//End Event ---:"+event.event.eventTypeId+ " on Unit :"+event.entity.unitId+" ----//")*/

/*

 */
    println("//Start Event --->:"+event.event.eventTypeId+ " on Unit :"+event.entity.unitId+" ----//")


    def unit = event.getEntity();
    def expGateBkgNbr = unit.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr")
    println("BOOKING EMPTY UNIT_LOAD::::::::"+expGateBkgNbr);
    api.getGroovyClassInstance("GvyCmisUtil").checkValidUnitSendEmail(event,unit)
    def lookup = api.getGroovyClassInstance("GvyVesselLookup");
    def position =  lookup.setDeckPositionType(unit);
    boolean isRoroUnit = false
// RORO vessel with attached eqmnt gets unmounted incorrectly. A1 Correct this.
//    A1 Correct this.
    if("RO".equals(position) && unit.subsidiaryEquipment != null) {
        // Set the event user
        isRoroUnit = true
        com.navis.argo.ContextHelper.setThreadExternalUser(event.event.evntAppliedBy);
        def iter = unit.subsidiaryEquipment.iterator();
        def now = new java.util.Date();
        while(iter.hasNext()) {
            def nextItem = iter.next();

            if( nextItem.ueDetachTime == null) continue;
            def ellapseTime = now.getTime() - nextItem.ueDetachTime.getTime();
            // More than 2 minutes since it was mounted;
// More than 2 minutes since it was mounted; api.log("Ellapse time for "+nextItem+" ="+ellapseTime);
            if(ellapseTime > 120000) continue;

            def eq = nextItem.ueEquipment;
            def ueEqRole = nextItem.ueEqRole;
            if(eq != null && ueEqRole != null) {
                try {
                    unitEquip = unit.attachEquipment(eq,ueEqRole,true);
                } catch (Throwable e) {
                    java.io.StringWriter w = new StringWriter();
                    java.io.PrintWriter pw = new java.io.PrintWriter(w);
                    e.printStackTrace(pw);
                    api.log(w.toString());
                }
            }
        }
    }






    def gvyLoadObj = null
    def gvyUtilObj = api.getGroovyClassInstance("GvyEventUtil")
    boolean processCmisFeed  = gvyUtilObj.verfiyCmisFeedProcessing(event)
    if(processCmisFeed)
    {
        Thread.sleep(3000)
        // N4 TO CMIS data processing
        def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
        def unitDtl = unitDetails.doIt(event)
        gvyLoadObj = api.getGroovyClassInstance("GvyCmisEventUnitLoad");
        String outMsg = gvyLoadObj.getLoadedEquipClassMsg(unitDtl,event,api)
        def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil")
        def destination = unit.getFieldValue("unitGoods.gdsDestination");
        boolean isNisPort = gvyCmisUtil.isNISPort(destination);
        if(isNisPort){
            outMsg = gvyCmisUtil.eventSpecificFieldValue(outMsg,"misc3=","null")
        }
        api.sendXml(outMsg)
    }

    gvyLoadObj = gvyLoadObj == null ? api.getGroovyClassInstance("GvyCmisEventUnitLoad") : gvyLoadObj
    gvyLoadObj.setPolIfBlank(unit)
    gvyLoadObj.passBareChassisToNow(unit, api, event)

    /*
    new code for CLS
    */
    def u = event.getEntity()

    gvyUtilObj = api.getGroovyClassInstance("GvyEventUtil")

    processCmisFeed = true
    if(processCmisFeed)
    {

        Thread.sleep(2000);

        // N4 TO CMIS data processing
        println('Unit Deliver Execute Code')
        def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
        def unitDtl = unitDetails.doIt(event)

        //SERVICE MSG CHECK
        println("---------------SERVICE MSG Starts -------------------------- ")
        def gvySrvObj = api.getGroovyClassInstance("GvyCmisSrvMsgProcessor");
        gvySrvObj.processServiceMessage(unitDtl,event,api)
        println("---------------SERVICE MSG Ends -------------------------- ")

        // Deliver
        def evntDeliverObj = api.getGroovyClassInstance("GvyCmisEventUnitDeliver");
        def deliverXml = evntDeliverObj.processUnitDeliver(unitDtl,event,api)


        // sending CLS event incase of locationStatus = 'A'
        println("--------------- GN processCLSforLocStatusA Starts -------------------------- ")
        def clsXml = evntDeliverObj.processCLSforLocStatusA(deliverXml,event,api);
        println("clsXml====="+clsXml);
        api.sendXml(clsXml);
        println("--------------- GN processCLSforLocStatusA Ends -------------------------- ")
        //end CLS
    }



    println("//End Event ---:"+event.event.eventTypeId+ " on Unit :"+event.entity.unitId+" ----//")



}
