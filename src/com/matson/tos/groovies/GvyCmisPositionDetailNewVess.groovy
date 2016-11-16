/*
* Srno Doer Date       Change
* A1   GR   08/26/10   LOC formatting (SN4Q change)
*                      Adding Yard Position Formatting Method
* A2   GR   10/21/10   Format LOC only FOR HON Pass AS-IS value for NIS Ports
*/
import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.model.Yard
import com.navis.xpscache.yardmodel.api.*;
import com.navis.xpscache.yardmodel.impl.*;

public class GvyCmisPositionDetailNewVess {

    public String doIt(String msgType, Object gvyTxtMsgFmt, Object unitObj,String eventType,Object event)
    {
        println("In Class GvyCmisPositionDetail.doIt()")
        def poistionFieldAttr = ''
        try
        {
            def gvyBaseClass = new GroovyInjectionBase()
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            def obcarrierChng = gvyEventUtil.wasFieldChanged(event, "ufvIntendedObCv")
            obcarrierChng = obcarrierChng != null ? obcarrierChng : ''

            //Verify Event Notes for unitRefersh
            def gvyEventObj = event.getEvent()
            def eventNotes = gvyEventObj.getEventNote()
            eventNotes = eventNotes != null ? eventNotes : ""

            //Verifying if the event Changes the position Field
            def gvyFldUpdtObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventFieldUpdateFilter");
            boolean evntUpdatesFlg = gvyFldUpdtObj.evntFilterOnPositionFldChng(eventType)
            if( !eventNotes.contains("Unit Correction") && evntUpdatesFlg )
            {
                poistionFieldAttr = positionPercentageValue(gvyTxtMsgFmt)
                return poistionFieldAttr
            }

            //Calling Msg Formater class

            def gvyCmisUtil = gvyBaseClass.getGroovyClassInstance("GvyCmisUtil");

            def u = unitObj
            def lkpSlot=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
            lkpSlot = lkpSlot!= null ? lkpSlot : ''
            def lkpSlotValue = lkpSlot.indexOf(".")== -1 ? lkpSlot : lkpSlot.substring(0,lkpSlot.indexOf("."));

            def lkpLocType=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            def lkpLocTypeKey = lkpLocType != null ? lkpLocType.getKey() : ''


            def _transitState=u.getFieldValue("unitActiveUfv.ufvTransitState")
            def transitStateKey = _transitState != null ? _transitState.getKey() : ''

            def _drayStatus=u.getFieldValue("unitDrayStatus")
            def drayStatusKey = _drayStatus!= null ? _drayStatus.getKey() : _drayStatus

            def lkpCarrierId=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocId")

            def _category=u.getFieldValue("unitCategory")
            def categoryKey = _category != null ? _category.getKey() : ''

            def dischPort = u.getFieldValue("unitRouting.rtgPOD1.pointId")

            //LOC
            //def _loc = lkpLocTypeKey.equals('YARD') ? lkpSlotValue : ''
            def loc  =  getLoc(gvyCmisUtil,lkpCarrierId,lkpLocTypeKey,lkpSlotValue,transitStateKey)
            loc = '%';
            def locAttr = gvyTxtMsgFmt.doIt('loc',loc)

            //CELL
            //def _cell = lkpLocTypeKey.equals('VESSEL') ? lkpSlotValue : ''
            def _cell = '%';
            def cellAttr = gvyTxtMsgFmt.doIt('cell',_cell)

            //LOCATION TIER -
            def locationTier = getLocationTier(lkpLocTypeKey, lkpSlotValue)
            locationTier = '%';
            def locationTierAttr = gvyTxtMsgFmt.doIt('locationTier',locationTier)

            //A1 LOCATION STATUS
            def locationType = getLocationStatus(lkpLocTypeKey,lkpCarrierId,transitStateKey,gvyCmisUtil,categoryKey,dischPort)
            locationType = '%';
            def locationStatusAttr = gvyTxtMsgFmt.doIt('locationStatus',locationType)

            //LOCATION STALL CONFIG
            def locationStallConfig= u.getFieldValue("unitFlexString08") != null ? 'PS' : ''
            //def locationStallConfigAttr =  gvyTxtMsgFmt.doIt('locationStallConfig',locationStallConfig)

            poistionFieldAttr = locAttr+cellAttr+locationTierAttr+locationStatusAttr

        }catch(Exception e){
            e.printStackTrace()
        }

        return poistionFieldAttr

    }

    private String getLocationTier(String lkpLocTypeKy, String lkpSlotVal)
    {
        def locationTier = ''
        try
        {
            if(lkpLocTypeKy.equals('YARD'))
            {
                if(lkpSlotVal.startsWith('P2A') || lkpSlotVal.startsWith('P29') || lkpSlotVal.startsWith('29Z'))
                {
                    locationTier = 'T2'
                }else if(lkpSlotVal.startsWith('WOA')){
                    locationTier = 'T3'
                }else{
                    locationTier = ''
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return locationTier
    }

    public String getLocationStatus(String lkpLocTypeKy,String lkpCarrId,String transitStateky,Object gvyUtil,String categoryKey,String dischPort)
    {
        def locationStatus = ''
        try
        {
            if(lkpLocTypeKy.equals('TRUCK') && transitStateky.equals('S30_ECIN') ){
                locationStatus='1'
            }
            else if(lkpLocTypeKy.equals('TRUCK')){
                locationStatus='3'
            }
            else if(lkpLocTypeKy.equals('YARD')){
                locationStatus='1'
            }
            else if(lkpLocTypeKy.equals('VESSEL'))
            {
                if(gvyUtil != null && gvyUtil.getVesselClassType(lkpCarrId).equals('BARGE')){
                    locationStatus='7'
                }
                else if(gvyUtil != null && gvyUtil.getVesselClassType(lkpCarrId).equals('CELL'))
                {
                    if(categoryKey.equals('THRGH')){
                        locationStatus='2'
                    }else if(transitStateky.equals('S60_LOADED') || transitStateky.equals('S70_DEPARTED')){
                        locationStatus='2'
                    }else if(transitStateky.equals('S20_INBOUND')){
                        locationStatus='4'
                    }
                }/*else if (gvyUtil.isNISPort(dischPort)){
                locationStatus='7'
         }*/

            }

        }catch(Exception e){
            e.printStackTrace()
        }
        return locationStatus
    }

    public String getLoc(Object gvyUtil,String lkpCarrId, String lkpLocTypeKy,String lkpSlotValue,String transitStateky)
    {
        def loc = ''
        if(lkpLocTypeKy.equals('YARD')){
            loc= lkpSlotValue
            loc = formatYardPosition(loc) //A1
        }
        else if(lkpLocTypeKy.equals('VESSEL'))
        {
            if(gvyUtil != null && gvyUtil.getVesselClassType(lkpCarrId).equals('BARGE'))
            {
                loc= 'NIS'
            }
        }
        return loc
    }

    public String positionPercentageValue(Object gvyTxtMsgFmt){

        def  poistionFieldAttr = ''
        def loc = '%'
        def  cell='%'
        def  locationTier= '%'
        def locationType =  '%'
        //LOC
        def locAttr = gvyTxtMsgFmt.doIt('loc',loc)

        //CELL
        def cellAttr = gvyTxtMsgFmt.doIt('cell',cell)

        //LOCATION TIER
        def locationTierAttr = gvyTxtMsgFmt.doIt('locationTier',locationTier)

        //A1 LOCATION STATUS
        def locationStatusAttr = gvyTxtMsgFmt.doIt('locationStatus',locationType)

        poistionFieldAttr = locAttr+cellAttr+locationTierAttr+locationStatusAttr

        return poistionFieldAttr
    }

//Format yard position based onyard file
    public String formatYardPosition(String inYardSlot){
        def binName = inYardSlot;
        try{
            int lastDot = inYardSlot.lastIndexOf('.');

            def inFacility = com.navis.argo.ContextHelper.getThreadFacility()
            //10/20/10 - IF Facility HON Format as per N4yard file Else IF NIS Pass AS-IS
            if(inFacility != null && !ContextHelper.getThreadFacility().getFcyId().equals(inFacility.getFcyId())){
                println("Dont Compute Location as not a "+ContextHelper.getThreadFacility().getFcyId()+" Transaction")
                return binName;
            }
            Yard inYard =  Yard.findYard("SI", inFacility)
            IXpsYardModel yardModel = inYard.getYardModel();
            if (lastDot > 0) {
                binName = inYardSlot.substring(0, lastDot);
            }
            IXpsYardBin bin = yardModel.getBin(binName);
            if(bin == null){
                return binName
            }
            IXpsYardBlock yardBlock = bin.getBlock();
            if(yardBlock != null && yardBlock.isWheeled()){
                if(binName.length() >=4){
                    binName = binName.substring(0,3)+' '+binName.substring(3);
                }
            }else{
                //println("yardBlock.getBlockType()------------------------- "+yardBlock.getBlockType())
                if(binName.length() > 3 && yardBlock != null &&
                        (yardBlock.getBlockType()== 13 || yardBlock.getBlockType()== 12 || yardBlock.getBlockType()== 1)){
                    binName = binName.substring(0,2)+' '+binName.substring(2);
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return binName
    }

}//Class Ends