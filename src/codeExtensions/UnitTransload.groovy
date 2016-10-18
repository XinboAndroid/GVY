package codeExtensions

import com.navis.services.business.event.GroovyEvent

/**
 * Created by kramachandran on 6/22/2016.
 */
class UnitTransload {
    public void execute(GroovyEvent event)

    {
        //Send ULK Message
        println("//Start Event ---:"+event.event.eventTypeId+ " on Unit :"+event.entity.unitId+" ----//")

        //Create Cmis Feed for Msg from Acets & UI Transaction
        def gvyUtilObj = api.getGroovyClassInstance("GvyEventUtil")
        boolean processCmisFeed  = gvyUtilObj.verfiyCmisFeedProcessing(event) // will send message for UNIT_TRANSLOAD_STRIP
        if(processCmisFeed)
        {
            // N4 TO CMIS data processing
            def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
            def unitDtl = unitDetails.doIt(event) // builds xml with attributes from unit, refactoring needed here
            def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil")
            gvyCmisUtil.postMsgForAction(unitDtl,api,'AULK') //set last action and send message

        }
        println("//End Event ---:"+event.event.eventTypeId+ " on Unit :"+event.entity.unitId+" ----//")

        /**
         * <GroovyMsg msgType='ULK'  unitClass='CONTAINER' ctrNo='MRKU357049' checkDigit='0' chassisNumber='null'
         * chassisCd='null' category='STRGE' accessory='null' mgWeight='null' facility='ANK' ybBarge='%' ybTrucker='null'
         * flex02='%'  tareWeight='8100' typeCode='%' hgt='%' strength='FB' owner='MAE' damageCode='null' srv='MAT'
         * temp='null' tempMeasurementUnit='null' temp2='null' equipTypeCode='D40H' hazOpenCloseFlag='A' tempSetting='null'
         * locationRow='MAE' cWeight='8554' seal='null' stowRestCode='null' stowFlag='%' odf='null'
         * bookingNumber='[booking number here]'
         * consignee='MAERSK LINE' shipper='MAERSK LINE' cneeCode='0023019010'
         * hazF='null' hazImdg='null' hazUnNum='null' locationCategory='null' arrDate='null' consigneePo='null' restow='NONE'
         * shipperId='0023019010' hazDesc='null' hazRegs='null' ucc='null' ecc='null' doNotBackLoad='null'
         * commodity='--' dir='MTY' dsc='%' planDisp='null' ds='%' orientation='E'  shipperPool='null' dischargePort='DUT'
         * dPort='KQA' loadPort='DUT' retPort='null' overLongBack='%' overLongFront='%' overWideLeft='%' overWideRight='%'
         * overHeight='%'  loc='%' cell='%' locationTier='%' locationStatus='%'  vesvoy='OMA607' truck='%' misc1='%'
         * actualVessel='%' actualVoyage='%' leg='%'  hsf7='null' pmd='null' locationRun='%' misc2='CK' misc3='null'
         * action='AULK' -- new action here
         * aDate='06/02/2016' aTime='13:17:20'
         * doer='ACETS' -- user here
         * sectionCode='Z'
         * lastAction='AULK' -- new action here
         * lastADate='06/02/2016' lastATime='13:17:20' lastDoer='ACETS' aei='null' dss='%' erf='null'
         * comments='null' crStatus='null' cargoNotes='null'  safetyExpiry='%' lastInspection='%' lastAnnual='%'
         * chassisAlert='%' mgp='null' chassisHold='MAE' chassisNotes='%' chassTareWeight='null'  gateTruckCmpyCd='%'
         * gateTruckCmpyName='%' gateTruckId='%' batNumber='%' turnTime='%' gateSeqNo='%' laneGateId='%' deptTruckCode='null'
         * deptDport='null' deptVesVoy='null' deptOutGatedDt='null' deptConsignee='null' deptCneeCode='null' deptBkgNum='null'
         * deptMisc3='null' deptCargoNotes='null' gateId='%' />
         *
         **/
        // unit strip
        println("//Start Event ---:"+event.event.eventTypeId+ " on Unit :"+event.entity.unitId+" ----//")
        gvyUtilObj = api.getGroovyClassInstance("GvyEventUtil")
        def stripObj = api.getGroovyClassInstance("GvyCmisEventUnitStrip")
        processCmisFeed  = gvyUtilObj.verfiyCmisFeedProcessing(event)
        def acetsStrip = stripObj.checkAcetsStrip(event) // check for user strip by transload
        if(processCmisFeed && acetsStrip)
        {
            stripObj.stripUnit(event)
        }
        println("//End Event ---:"+event.event.eventTypeId+ " on Unit :"+event.entity.unitId+" ----//")
        /**
         * <GroovyMsg msgType='UNIT_STRIP' unitClass='CONTAINER' ctrNo='SEAU881089' checkDigit='2' srv='MAT' loc='%'
         * owner='MATU' cWeight='15000' tareWeight='15000' retPort='null' strength='B1' dir='MTY' dPort='OPT'
         * dischargePort='OPT' locationRun='NO' orientation='E' locationStatus='1' damageCode='null' locationRow='MAT'
         * action='AULD' -- new action here
         * aDate='05/29/2016' aTime='15:05:13'
         * doer='ACETS' --user here
         * sectionCode='Z'
         * lastAction='AULD' --new action here
         * lastADate='05/29/2016'
         * lastATime='15:05:13' lastDoer='ACETS' aei='null' dss='%' erf='null' availDt='null' dtnDueDt='null'
         * lastFreeStgDt='null' oldVesvoy='%' lineTime='%' tractorNbr='%' vNumber='%' chassAei='%' mgAei='%'
         * chasdamageCode='%' leg='null' typeCode='%' hgt='%' commodity='MTYAUT' />
         */

        //Send LNK Message
        println("//Start Event ---:" + event.event.eventTypeId + " on Unit :" + event.entity.unitId + " ----//")

        // Create Cmis Feed for Msg from Acets & UI Transaction
        def gvyUtilObj = api.getGroovyClassInstance("GvyEventUtil")
        boolean processCmisFeed = gvyUtilObj.verfiyCmisFeedProcessing(event)
        if (processCmisFeed) {
            // N4 TO CMIS data processing
            def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
            def unitDtl = unitDetails.doIt(event)
            def gvyCmisUtil = api.getGroovyClassInstance("GvyCmisUtil")
            gvyCmisUtil.postMsgForAction(unitDtl, api, 'ALNK')
        }
        println("//End Event ---:" + event.event.eventTypeId + " on Unit :" + event.entity.unitId + " ----//")

        /**
         *  <GroovyMsg msgType='LNK'  unitClass='CONTAINER' ctrNo='260513' checkDigit='0' chassisNumber='null' chassisCd='null'
         *  category='THRGH' accessory='null' mgWeight='null' facility='KDK' ybBarge='%' ybTrucker='null' flex02='%'
         *  tareWeight='7960' typeCode='%' hgt='%' strength='A' owner='MATU' damageCode='null' srv='MAT' temp='null'
         *  tempMeasurementUnit='null' temp2='null' equipTypeCode='D40H' hazOpenCloseFlag='A' tempSetting='null'
         *  locationRow='MAT' cWeight='49620' seal='223124' stowRestCode='null' stowFlag='%' odf='null'
         *  bookingNumber='4543880'
         *  consignee='SCHNITZER STEEL PRODUCTS COMPANY' shipper='SPAN ALASKA TRANSPORTATION INC' cneeCode='0000184086'
         *  hazF='null' hazImdg='null' hazUnNum='null' locationCategory='null' arrDate='null' consigneePo='null'
         *  restow='NONE' shipperId='0000177242' hazDesc='null' hazRegs='null' ucc='null' ecc='null' doNotBackLoad='null'
         *  commodity='--' dir='OUT' dsc='%' planDisp='null' ds='CY' orientation='F'  shipperPool='null' dischargePort='TAC'
         *  dPort='TAC' loadPort='ANK' retPort='null' overLongBack='%' overLongFront='%' overWideLeft='%' overWideRight='%'
         *  overHeight='%'  loc='null' cell='030003' locationTier='null' locationStatus='2' locationStallConfig='null'
         *  vesvoy='MKD012' truck='null' misc1='null' actualVessel='MKD' actualVoyage='012' leg='S'  hsf7='null' pmd='CLONED'
         *  locationRun='%' misc2='KR' misc3='null'
         *  action='ALNK'  -- new action here
         *  aDate='05/30/2016' aTime='17:26:24'
         *  doer='ACETS' -- user here
         *  sectionCode='Z'
         *  lastAction='ALNK' -- new action here
         *  lastADate='05/30/2016' lastATime='17:26:24' lastDoer='ACETS' aei='null' dss='%' erf='null'  comments='null'
         *  crStatus='null' cargoNotes='ACETS: PMOORE'  safetyExpiry='%' lastInspection='%' lastAnnual='%' chassisAlert='%'
         *  mgp='null' chassisHold='MAT' chassisNotes='%' chassTareWeight='null'  gateTruckCmpyCd='%' gateTruckCmpyName='%'
         *  gateTruckId='%' batNumber='%' turnTime='%' gateSeqNo='%' laneGateId='%' deptTruckCode='null' deptDport='null'
         *  deptVesVoy='null' deptOutGatedDt='null' deptConsignee='null' deptCneeCode='null' deptBkgNum='null' deptMisc3='null'
         *  deptCargoNotes='null' gateId='%' />
         * */

        //unit stuff
        /**
         * <GroovyMsg msgType='UNIT_STUFF'  unitClass='CONTAINER' ctrNo='HRZU580796' checkDigit='8' chassisNumber='SEAC850101'
         * chassisCd='X' category='EXPRT' accessory='null' mgWeight='null' facility='ANK' ybBarge='%' ybTrucker='null' flex02='%'
         * tareWeight='10890' typeCode='%' hgt='%' strength='A' owner='MATU' damageCode='null' srv='MAT' temp='null'
         * tempMeasurementUnit='null' temp2='null' equipTypeCode='R40H' hazOpenCloseFlag='A' tempSetting='null'  locationRow='MAT'
         * cWeight='12227' seal='null' stowRestCode='null' stowFlag='%' odf='null'  bookingNumber='null' consignee='null'
         * shipper='null' cneeCode='null' hazF='null' hazImdg='null' hazUnNum='null' locationCategory='null' arrDate='null'
         * consigneePo='null' restow='NONE' shipperId='null' hazDesc='null' hazRegs='null' ucc='null' ecc='PT' doNotBackLoad='null'
         * commodity='CFS' dir='OUT' dsc='%' planDisp='null' ds='CY' orientation='F'  shipperPool='null' dischargePort='DUT'
         * dPort='DUT' loadPort='ANK' retPort='null' overLongBack='%' overLongFront='%' overWideLeft='%' overWideRight='%'
         * overHeight='%'  loc='C' cell='null' locationTier='null' locationStatus='1' locationStallConfig='null'  vesvoy='MCN018'
         * truck='null' misc1='null' actualVessel='MCN' actualVoyage='018' leg='S'  hsf7='null' pmd='null' locationRun='%'
         * misc2='null' misc3='null'
         * action='ALDC'
         * aDate='05/10/2016' aTime='14:20:16' sectionCode='Z'
         * doer='ACETS'
         * lastAction='ALDC' lastADate='05/10/2016' lastATime='14:20:16' lastDoer='ACETS' aei='null' dss='%' erf='null'
         * comments='null' crStatus='null' cargoNotes='3 LCL'  safetyExpiry='%' lastInspection='%' lastAnnual='%' chassisAlert='%'
         * mgp='null' chassisHold='MAT' chassisNotes='%' chassTareWeight='6850'  gateTruckCmpyCd='%' gateTruckCmpyName='%'
         * gateTruckId='%' batNumber='%' turnTime='%' gateSeqNo='%' laneGateId='%' deptTruckCode='null' deptDport='null'
         * deptVesVoy='null' deptOutGatedDt='null' deptConsignee='null' deptCneeCode='null' deptBkgNum='null' deptMisc3='null'
         * deptCargoNotes='null' gateId='%' />
         */

        println("//Start Event ---:"+event.event.eventTypeId+ " on Unit :"+event.entity.unitId+" ----//")


        def unit = event.getEntity();
        def commodityId=unit.getFieldValue("unitGoods.gdsCommodity.cmdyId")
        def commodity = commodityId != null ?commodityId : ''



        // Set Avail Date
        //def unit = event.getEntity()
        def availLookup = api.getGroovyClassInstance("GvyAvailDate");
        boolean update =  availLookup.setAvailDate(unit, event);


//1] Create Cmis Feed for Msg from Acets & UI Transaction

        def gvyUtilObj = api.getGroovyClassInstance("GvyEventUtil")
        boolean processCmisFeed  = gvyUtilObj.verfiyCmisFeedProcessing(event)
        println("EVNT_STUFF ::Avail Date:"+update+"   processCmisFeed::"+processCmisFeed)
        if(processCmisFeed)
        {
            // N4 TO CMIS data processing
            def unitDetails = api.getGroovyClassInstance("GvyCmisDataProcessor")
            def unitDtl = unitDetails.doIt(event)

            //Stuff specific Changes
            def gvyStuff = api.getGroovyClassInstance("GvyEventSpecificFldValue")
            def unitStuffXml = gvyStuff.getEventUnitStuff(unitDtl,commodity,unit,event)

            //Detention Msg Check
            if(update){
                def gvyDentObj = api.getGroovyClassInstance("GvyCmisDetentionMsgProcess");
                gvyDentObj.detentionProcess(unitStuffXml,event,api)
            }

            boolean blockForMultipleStuff = gvyUtilObj.holdEventProcessing(event, 'UNIT_STUFF', 2)
            if(!blockForMultipleStuff){     api.sendXml(unitStuffXml); }
        }

        println("//End Event ---:"+event.event.eventTypeId+ " on Unit :"+event.entity.unitId+" ----//")
    }
}
