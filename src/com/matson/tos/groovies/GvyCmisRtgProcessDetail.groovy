/*
* SrNo  Doer Date        Change
* A1    GR   06/24/2010  Added DS AUTOCY=CY
* A2    GR   09/08/10    Added PlanDisp for TTNU event Return to Customer (SN4Q change)
* A3    GR   12/23/11    Commodity SIT-YB checkcode change
*/
public class GvyCmisRtgProcessDetail {

    public String doIt(String strMsgType, Object gvyTxtMsgFmt, Object unitObj, Object gvyBaseClass)
    {
        println("In Class GvyCmisRtgProcessDetail.doIt()")
        def u =  unitObj
        def rtgProcessFieldAttr = ''

        try
        {
            def _freightkind=u.getFieldValue("unitFreightKind")
            def freightkindKey = _freightkind != null ? _freightkind.getKey() : ''

            def _category=u.getFieldValue("unitCategory")
            def categoryKey = _category != null ? _category.getKey() : ''

            def _transitState=u.getFieldValue("unitActiveUfv.ufvTransitState")
            def transitStatekey = _transitState != null ? _transitState.getKey() : ''

            def _drayStatus=u.getFieldValue("unitDrayStatus")
            def drayStatusKey = _drayStatus!= null ? _drayStatus.getKey() : ''

            def group=u.getFieldValue("unitRouting.rtgGroup.grpId")
            group = group != null ? group : ''

            def lkpSlot=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
            lkpSlot = lkpSlot != null ? lkpSlot : ''

            def expGateBkgNbr = u.getFieldValue("unitPrimaryUe.ueDepartureOrderItem.eqboiOrder.eqboNbr")

            def lkpLocType=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posLocType")
            lkpLocType = lkpLocType != null ? lkpLocType.getKey() : ''

            def gdsBlNbr = u.getFieldValue("unitGoods.gdsBlNbr")

            //COMMODITY
            def designatedTrucker=u.getFieldValue("unitRouting.rtgTruckingCompany.bzuId")
            designatedTrucker = designatedTrucker != null ? (designatedTrucker.length() == 3 ? designatedTrucker+' ' : designatedTrucker)  : ''

            def _commodityId=u.getFieldValue("unitGoods.gdsCommodity.cmdyId")
            def cmdtyId = _commodityId != null ? _commodityId : ''
            def commodityId= commodityCode(cmdtyId,group,designatedTrucker,lkpSlot)
            def commodityAttr= gvyTxtMsgFmt.doIt('commodity',commodityId)

            //DIR
            def dir =  getDir(categoryKey,transitStatekey,freightkindKey,expGateBkgNbr,lkpLocType,gdsBlNbr)
            def dirAttr = gvyTxtMsgFmt.doIt('dir',dir)

            //DSC
            def dsc = getDsc(strMsgType,freightkindKey,drayStatusKey,group,lkpSlot,cmdtyId,gvyBaseClass)
            def dscAttr = gvyTxtMsgFmt.doIt('dsc',dsc)

            //PLAN DISP
            def planDisp = getPlanDisp(drayStatusKey, group, lkpSlot)

            println("Testing YB_ASSIGN for planDsip    "+strMsgType);
            if ("YB_ASSIGN".equalsIgnoreCase(strMsgType)) {
                planDisp = '7'
            }

            //A3
            /*if('3'.equals(planDisp) && (strMsgType.contains('ASSIGN') || strMsgType.contains('TRANSFER'))){
                exit;
            }*/
            def planDispAttr = gvyTxtMsgFmt.doIt('planDisp',planDisp)

            //DS - A1 - (Discussion to add more Conditions)
            def ds = getDs(freightkindKey,cmdtyId)
            def dsAttr = gvyTxtMsgFmt.doIt('ds',ds)

            //ORIENTATION
            def orientation = freightkindKey.equals('MTY') ? 'E' : (freightkindKey.length() > 1 ? 'F' : '')
            def orientationAttr = gvyTxtMsgFmt.doIt('orientation',orientation)

            rtgProcessFieldAttr = commodityAttr+dirAttr+dscAttr+planDispAttr+dsAttr+orientationAttr
        }catch(Exception e){
            e.printStackTrace()
        }

        return rtgProcessFieldAttr
    }

    // Retrieves DS Field-Based on freightkindKey,commodityId
    public String getDs(String freightkindKey,String cmdtyId)
    {
        def ds = ''
        if(cmdtyId.equals('AUTOCON')){
            ds = 'CON'
        }else if(cmdtyId.equals('AUTO')){
            ds = 'AUT'
        }else if (freightkindKey.equals('FCL') || cmdtyId.equals('AUTOCY')){
            ds = 'CY'
        }else{
            ds = '%'
        }
        return ds
    }

    //Retrieves DSC Field-Based on transitState,drayStatus and group
    public String getDsc(String strMsgType, String freightkindKey,String  drayStatus, String group, String lkpSlot, String cmdtyId,Object gvyBaseClass)
    {
        def dsc = ''
        if(group != null && group.equals("TS")){
            dsc = 'C'
        }
        else if((group.equals("XFER-P2") || group.equals("XFER-SI") || group.equals("XFER-WO")) && freightkindKey.equals('MTY'))    {
            dsc = ''
        }
        else if((group.equals("XFER-P2") || group.equals("XFER-SI") || group.equals("XFER-WO")) && !freightkindKey.equals('MTY'))  {
            dsc = 'C'
        }
        else if(drayStatus.equals('OFFSITE') && cmdtyId.equals('SIT')){
            dsc = 'S'
        }else if (drayStatus.equals('OFFSITE') || drayStatus.equals('DRAYIN')){
            dsc = 'C'
        }else if(cmdtyId.equals('SIT')){
            dsc = 'S'
        }else{
            def gvyFldUpdtObj = gvyBaseClass.getGroovyClassInstance("GvyCmisEventFieldUpdateFilter");
            dsc = gvyFldUpdtObj.evntFilterOnDscFldChng(strMsgType)
        }
        return dsc
    }

    // Retrieves PlanDisp Field-Based on DrayStatus and Eventype
    public String getPlanDisp(String drayStatus, String group, String lkpSlot)
    {
        def planDisp = ''
        if(group.equals('TS')) {
            planDisp='T'
        }
        else if (group.equals('XFER-P2') || group.equals('XFER-WO')) {
            planDisp = group.equals('XFER-P2') ? '3' : 'W'
        }
        else if(group.equals('XFER-SI')){
            planDisp = lkpSlot.startsWith('WOA') ? "W" : "3"
        }
        else if(group.equals('COMSVC')) {
            planDisp='3'
        }else if((group.equals('OTR') || group.equals('PASSPASS')) && drayStatus.equals('FORWARD')) {
            planDisp='8'
        }else if(group.equals('YB')){
            planDisp='7'
        }else  if (group.equals('1WAY')){
            planDisp='9'
        }else if ((group.equals('SHOW') || group.equals('TTNU') || group.equals('PASSPASS')) && drayStatus.equals('RETURN')){ //A2
            planDisp='A'
        }else if (group.equals('MDA')){
            planDisp='B'
        }
        return planDisp;
    }

    // Retrieves DIR Field-Based on category & transitState
    public  String getDir(String category,String transitState,String freightkindKey, String expGateBkgNbr,String lkpLocType,String gdsBlNbr)
    {
        def dir = ''
        if(category.equals('EXPRT') && !freightkindKey.equals('MTY') ){
            dir='OUT'
        } else if (category.equals('EXPRT') && expGateBkgNbr != null){
            dir='OUT'
        }else if (category.equals('IMPRT') && lkpLocType.equals('VESSEL') && freightkindKey.equals('MTY') && gdsBlNbr == null){
            dir = 'MTY'
        }else if (category.equals('IMPRT')){
            dir='IN'
        }else if (category.equals('TRSHP') && transitState.equals('S20_INBOUND')){
            dir = 'IN'
        }else if (category.equals('TRSHP') && transitState.trim().length() > 0){
            dir = 'OUT'
        }else if (category.equals('THRGH') && !freightkindKey.equals('MTY')){
            dir = 'OUT'
        }else{
            dir = 'MTY'
        }
        return dir;
    }

//Method Retrieves CommodityCode-CMIS Relation value
    public  String commodityCode(String commodity, String group, String designatedTrucker, String lkpSlot)
    {
        def commodityCode = ''
        def map = new HashMap()
        map.put("PINEAPPLE CANNED", "CANNED P")
        map.put("PINEAPPLE FRESH", "FRESH PI")
        map.put("GOLDEN STATE","GOLDNSTA")
        map.put("MTY MILK CASES", "MLK CASE")
        try
        {
            commodityCode = map.get(commodity) != null ? map.get(commodity) : commodity;
            def truckerFlag = designatedTrucker.length() > 0

            if(group.equals("XFER-WO") && commodityCode.equals('SIT')){
                commodityCode = truckerFlag ? designatedTrucker+"   S" : "       S"
            }
            else if(group.equals("XFER-SI")){
                if(commodityCode.equals('SIT') && lkpSlot.startsWith('WOA')){
                    commodityCode = truckerFlag ? designatedTrucker+"   S" : "       S"
                }
            }else if(commodityCode.equals("SIT")){ //A3
            }
            else if(group.equals("YB")){
                commodityCode = 'YB'
            }
        }catch(Exception e){
            e.printStackTrace()
        }
        return commodityCode;
    }

}//Class Ends