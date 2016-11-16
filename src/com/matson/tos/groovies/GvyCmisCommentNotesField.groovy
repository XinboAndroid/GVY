/*
* SrNo  Doer  Date    Change
* A1    GR  06/28/10  Arranged Hold Order Sequence
* A2    GR   12/13/11  Update HOLD FOR LNK
*
*/
import com.navis.framework.business.Roastery
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.api.IFlagType
import com.navis.argo.business.atoms.FlagPurposeEnum


public class GvyCmisCommentNotesField {

    public String doIt(Object gvyEventObj, String eventType, Object gvyTxtMsgFmt, Object unitObj)
    {
        println("In Class GvyCmisCommentNotesField.doIt()")
        def u = unitObj
        def CommentCargoStatusFields = ''
        try
        {
            def groupCode = u.getFieldValue("unitRouting.rtgGroup.grpId");

            def lkpSlot=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
            lkpSlot = lkpSlot != null ? lkpSlot : ''

            def _pmdDt =u.getFieldValue("unitActiveUfv.ufvFlexDate01")
            def strpmd = _pmdDt != null ? (''+_pmdDt) : ''
            def pmd =  strpmd.length() > 10 ? strpmd.substring(8,10) : strpmd

            def equiType=u.getFieldValue("unitPrimaryUe.ueEquipment.eqEquipType.eqtypId")
            equiType = equiType != null ? equiType : ''

            def _drayStatus=u.getFieldValue("unitDrayStatus")
            def drayStatus = _drayStatus!= null ? _drayStatus.getKey() : _drayStatus

            def reviewForStow =u.getFieldValue("unitActiveUfv.ufvFlexString01")


            //Cargo Status
            def cargoNotes=u.getFieldValue("unitRemark")
            cargoNotes = cargoNotes != null ? cargoNotes : ''
            cargoNotes = processCargoNotesOnEvent(gvyEventObj,eventType,cargoNotes,groupCode,lkpSlot,pmd,equiType,reviewForStow,drayStatus)

            String cargoNotesOverFlow = '';
            if(cargoNotes.length() > 65){
                int cargoNotesIndex = cargoNotes.substring(0,65).lastIndexOf(" ");
                cargoNotesOverFlow = cargoNotes.substring(cargoNotesIndex+1,cargoNotes.length());
                cargoNotes = cargoNotes.substring(0,cargoNotesIndex);
            }
            def cargoNotesAttr = gvyTxtMsgFmt.doIt('cargoNotes',cargoNotes.trim())

            //CRSSTATUS
            def crsStatus = null
            //Being Set as a HOLD
            /* if(eventType.equals("TAG_STRIP_ASSIGN") ||
                        (groupCode != null && groupCode.equals('TS') )) {
                    crsStatus = groupCode +' '+ getUnitActiveHolds(unitObj)
             }else{
                    crsStatus = getUnitActiveHolds(unitObj)
             }*/
            crsStatus = getUnitActiveHolds(unitObj)

            def holdsApp = ''
            if(crsStatus.length() > 10){
                int commaIndex = crsStatus.substring(0,11).lastIndexOf(' ');
                if (commaIndex < 0) {
                    commaIndex = crsStatus.substring(0,11).lastIndexOf('_');
                }
                holdsApp = crsStatus.substring(commaIndex+1,crsStatus.length());
                crsStatus = crsStatus.substring(0,commaIndex);
            }
            def crsStatusAttr = gvyTxtMsgFmt.doIt('crStatus',crsStatus)

            //Comments
            def commentValue = holdsApp.trim()+'_'+cargoNotesOverFlow.trim()
            def comments = processCommentOnEvent(commentValue,unitObj,eventType,groupCode, lkpSlot,drayStatus)
            comments = comments != null ? comments.trim() : ''
            def commentAttr =  gvyTxtMsgFmt.doIt('comments', comments)

            //Save unitRemark (cargoNotes + comments)
            saveUnitRemarks(unitObj,cargoNotes,comments,holdsApp)

            CommentCargoStatusFields = commentAttr+crsStatusAttr+cargoNotesAttr

        }catch(Exception e){
            e.printStackTrace()
        }

        return CommentCargoStatusFields

    }//doIt Ends

    public String processCommentOnEvent(String commentData, Object unit, String eventType, String groupCd, String lkpSlot,String drayStatus)
    {
        println("processCommentOnEvent method")
        def comment = commentData != null ? commentData : ''

        def groupCode = groupCd != null ? groupCd : ''
        def rfsNotes =  unit.getFieldValue("unitActiveUfv.ufvFlexString01")
        def commentFlag = comment.endsWith("_")


        //Checking on Group
        if(groupCode.equals('XFER-P2') && drayStatus.equals('OFFSITE') )
        {
            comment = commentFlag ? comment+'XFR BTW P53-P2' : comment+' XFR BTW P53-P2'

        }else if(groupCode.equals('XFER-WO') && drayStatus.equals('OFFSITE'))
        {
            comment = commentFlag ? comment+'XFR BTW P53-WO' : comment+' XFR BTW P53-WO'
        }
        else if(groupCode.equals('XFER-SI') && drayStatus.equals('OFFSITE'))
        {
            //Check if Unit in P2 or WO and then Update Comments
            if(lkpSlot.startsWith('WOA')){
                comment = commentFlag ? comment+'XFR BTW P53-WO' : comment+' XFR BTW P53-WO'
            }
            else if(lkpSlot.startsWith('P2')){
                comment = commentFlag ?comment+'XFR BTW P53-P2' : comment+' XFR BTW P53-P2'
            }
        }

        //Formatting the comment
        comment = formatCommentSize(comment);
        def appendFlag = comment.startsWith("_") || comment.endsWith("_");
        comment = appendFlag ? comment.replace("_", "") : comment

        return comment
    }
    private  String formatCommentSize(String _comment)
    {
        def commentValue = null;
        def comment = _comment;
        def  commentLen = comment.length();
        if(commentLen > 65 && comment.indexOf("_")!= -1)
        {
            def index = comment.indexOf("_");
            def stripCharLen = commentLen - 65;
            commentValue = comment.substring(0, index+1)+comment.substring((index+1)+stripCharLen);
        }
        else if(commentLen > 65 && comment.indexOf("_")== -1){
            def stripCharLen = commentLen - 65;
            commentValue = comment.substring(stripCharLen);
        }
        else{
            commentValue = comment;
        }
        return commentValue;
    }

    private String processCargoNotesOnEvent(Object gvyEventObj,String eventType,String cargoNotesInfo,String group, String lkpSlot, String pmd,String equiType,String reviewForStow,String drayStatus)
    {
        def groupCode = group != null ? group : ''
        def cargoNotes = cargoNotesInfo != null ? cargoNotesInfo : ''
        def eventnotes = gvyEventObj != null ? gvyEventObj.getEvntNote() : ''

        //Striping Event based Cargo Notes Information
        cargoNotes = stripCargoNotes(eventType,cargoNotes,groupCode,pmd)

        //Appending Information
        if((groupCode.equals('XFER-WO') ||
                (groupCode.equals('XFER-SI') && lkpSlot.startsWith('WOA')) ) && drayStatus.equals('OFFSITE'))
        {
            if(!cargoNotes.startsWith("WEST OAHU")){
                cargoNotes = cargoNotes != null ? 'WEST OAHU-'+cargoNotes : 'WEST OAHU'
            }
        }

        if (eventType.equals('TRANSFER_CANCEL'))
        {
            cargoNotes = cargoNotes != null ? 'PDISP CANCEL '+cargoNotes : 'PDISP CANCEL '
        }
        else if((pmd != null && pmd.length() > 0) && reviewForStow != null)
        {
            if(equiType.startsWith('R')){
                cargoNotes = cargoNotes != null ? cargoNotes+' UPG-'+pmd+' '+reviewForStow : 'UPG-'+pmd+' '+reviewForStow
            }
            else if(!equiType.startsWith('R')){
                cargoNotes = cargoNotes != null ?cargoNotes+' PMD-'+pmd+' '+reviewForStow : 'PMD-'+pmd+' '+reviewForStow
            }
        }
        else if((pmd != null && pmd.length() > 0) && equiType.startsWith('R'))
        {
            cargoNotes = cargoNotes != null ? cargoNotes+' UPG-'+pmd : 'UPG-'+pmd
        }
        else if((pmd != null && pmd.length() > 0) && !equiType.startsWith('R'))
        {
            cargoNotes = cargoNotes != null ?cargoNotes+' PMD-'+pmd : 'PMD-'+pmd
        }
        else if(reviewForStow != null)
        {
            cargoNotes = cargoNotes != null ?cargoNotes+' '+reviewForStow : reviewForStow
        }
/*     else if(eventType.equals('SHOP_HOLD'))
     {
       cargoNotes = cargoNotes != null ? (eventnotes!= null ? 'TO F&M-'+eventnotes+'.'+cargoNotes : 'TO F&M '+cargoNotes) : (eventnotes!= null ? 'TO F&M-'+eventnotes+'.' : 'TO F&M ')
     }
     else if(eventType.equals('CL_HOLD'))
     {
         cargoNotes = cargoNotes != null ? (eventnotes!= null ? eventnotes+' '+cargoNotes : cargoNotes) : (eventnotes!= null ? eventnotes : '')
     } */

        return cargoNotes.trim()
    }

    //Method Strips event specific information
    private String stripCargoNotes(String eventType,String cargoNotes, String groupCode, String pmd)
    {
        println("First stripCargoNotes Method")
        def stripCargoNotes = cargoNotes != null ? cargoNotes : ''

        if(groupCode.equals('XFER-WO') || groupCode.equals('XFER-SI') || groupCode.equals('XFER-P2') ||  eventType.equals('TRANSFER_CANCEL')){
            stripCargoNotes = stripCargoNotes.replace("WEST OAHU-", "")
            stripCargoNotes = stripCargoNotes.replace("WEST OAHU", "")
            stripCargoNotes = stripCargoNotes.replace("XFR BTW P53-P2","");
            stripCargoNotes = stripCargoNotes.replace("XFR BTW P53-WO","");
        }
/*     if(eventType.equals('SHOP_RELEASE'))
     {
       stripCargoNotes = stripCargoNotes.replace("TO F&M ", "")
       if(stripCargoNotes.indexOf("TO F&M-") != -1)
       {
            stripCargoNotes = stripCargoNotes.substring(stripCargoNotes.indexOf(".")+1)
       }
     } */
        else if(eventType.equals('PREMOUNT_REQUEST_CANCEL'))
        {
            if(stripCargoNotes.indexOf("UPG-") != -1)
                stripCargoNotes = stripCargoNotes.substring(0,stripCargoNotes.indexOf("UPG-"))

            if(stripCargoNotes.indexOf("PMD-") != -1)
                stripCargoNotes = stripCargoNotes.substring(0,stripCargoNotes.indexOf("PMD-"))
        }
        else if (groupCode.equals('XFER-P2') || groupCode.equals('XFER-WO') || groupCode.equals('XFER-SI'))
        {
            stripCargoNotes = stripCargoNotes.replace("PDISP CANCEL", "")
        }
        return stripCargoNotes.trim()
    }


    private void saveUnitRemarks(Object unit,String cargoNotes,String comments, String holdsApp)
    {
        //CARGO NOTES
        def cargoNotesValue = cargoNotes != null ? cargoNotes : ''
        def holdsOverFlow = holdsApp != null ? holdsApp : ''

        //COMMENT
        def commentOverFlow = comments != null ? comments : ''
        println('holdsOverFlow :::'+holdsOverFlow.length()+"      COMMENTS OVERFLOW :::::"+commentOverFlow.length())
        def commentIndex = commentOverFlow.indexOf("_")
        if(commentIndex != -1)
        {
            commentOverFlow = commentOverFlow.substring(commentIndex+1)
        }

        if((holdsOverFlow.length() > 0) && (holdsOverFlow.length() == commentOverFlow.length()))
        {
            commentOverFlow = ''
        }

        def unitRemark = cargoNotesValue.trim()+(commentOverFlow.trim().length() > 0 ? ' '+commentOverFlow : commentOverFlow.trim())
        println("unitRemark :::"+unitRemark)
        //unit.setUnitRemark(unitRemark)
    }

    //Method Get Active Holds for Unit
    public String getUnitActiveHolds(Object unitbase)
    {
        def map = new HashMap()
        map.put('DRAY CANNOT LTV','')
        map.put('HOLD FOR LNK','HLD')
        map.put('CG_INSP','CG')
        map.put('OUTGATE','RD')

        def strBuffer = new StringBuffer()
        ServicesManager sm = (ServicesManager)Roastery.getBean("servicesManager");
        def flagIds = sm.getActiveFlagIds(unitbase);
        if (flagIds != null) {
            for(holdId in flagIds)
            {
                def  iFlageType = sm.getFlagTypeById(holdId)
                def flagPurpose =  iFlageType.getPurpose().getKey()
                if(flagPurpose.equals('HOLD')) {
                    def appHoldId = map.get(holdId) != null ? map.get(holdId) : holdId
                    strBuffer.append(appHoldId+' ')
                }
            }
        }
        def fmtHoldId = strBuffer != null ? (''+strBuffer).trim() : strBuffer
        fmtHoldId = orderHoldsOnPriority(fmtHoldId)
        println('FMT HOLDS ::::'+fmtHoldId)
        return fmtHoldId
    }

    //A1- Method Orders the Holds
    public static String orderHoldsOnPriority(String holdsArr)
    {
        Map holdsMap = new LinkedHashMap();
        holdsMap.put("13","XT");
        holdsMap.put("12","TI"); holdsMap.put("11","PER"); holdsMap.put("10","ON");
        holdsMap.put("9","CG"); holdsMap.put("8","CC"); holdsMap.put("7","HP");
        holdsMap.put("6","AGN"); holdsMap.put("5","AG");  holdsMap.put("4","RM");
        holdsMap.put("3","GX"); holdsMap.put("2","INB"); holdsMap.put("1","CUS");

        Iterator it = holdsMap.keySet().iterator();
        String holdsLst = holdsArr;
        println(" holdsList-Actual :"+holdsLst);
        while (it.hasNext())
        {
            String holdkey = (String) it.next();
            String ahold = (String)holdsMap.get(holdkey);
            int holdIndex = holdsLst.indexOf(ahold);
            if(holdIndex != -1)
            {
                if("AG".equals(ahold) && holdsLst.contains('AGN')){
                    continue;
                }
                holdsLst = holdsLst.replace(ahold, "");
                holdsLst = ahold+" "+holdsLst.trim();
            }
        }//while ends
        holdsLst = holdsLst.replace("    ", " ");
        holdsLst = holdsLst.replace("   ", " ");
        holdsLst = holdsLst.replace("  ", " ");
        println(" holdsList-Ordered :::::"+holdsLst.trim());
        return holdsLst;
    }

}//Class Ends