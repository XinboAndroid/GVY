/*
* Srno  Doer  Date       comment
* A1    KM    03/24/11   Review for Stow, set stowflag from Y to %.  This was causing units to have a stopper in GEMS.

*/

import com.navis.framework.business.Roastery
import com.navis.argo.business.api.ServicesManager
import com.navis.argo.business.api.IFlagType
import com.navis.argo.business.atoms.FlagPurposeEnum

public class GvyCmisPhysicalStatusDetail {

    public String doIt(String strMsgType, Object gvyTxtMsgFmt, Object unitObj)
    {
        println("In Class GvyCmisPhysicalStatusDetail.doIt()")
        def u = unitObj
        def phyStatusFields = ''
        try
        {
            def groupCode = u.getFieldValue("unitRouting.rtgGroup.grpId");
            def lkpSlot=u.getFieldValue("unitActiveUfv.ufvLastKnownPosition.posSlot")
            lkpSlot = lkpSlot != null ? lkpSlot : ''

            // LOCATION ROW
            def _lineOperator=u.getFieldValue("unitLineOperator.bzuId")
            def locationRowAttr =  gvyTxtMsgFmt.doIt('locationRow',_lineOperator)

            //CW Weight
            def weightKg=u.getFieldValue("unitGoodsAndCtrWtKg")
            def weightLB = weightKg != null ? Math.round(weightKg * 2.20462262) : ''
            def cwWeightAttr =  gvyTxtMsgFmt.doIt('cWeight',weightLB)

            //SEAL
            def _seal1=u.getFieldValue("unitSealNbr1")
            def sealAttr = gvyTxtMsgFmt.doIt('seal',_seal1)

            //STOW RESTRICTION CODE
            def stowId= u.getFieldValue("unitSpecialStow.stwId")
            def stowRestCode = stowId != null ? getStowCode(stowId) : ''
            def stowRestCodeAttr = gvyTxtMsgFmt.doIt('stowRestCode',stowRestCode)

            //STOW FLAG
            def stowFlag = ''
            if(strMsgType.equals('REVIEW_FOR_STOW')){
                stowFlag = '%'          //A1 changed from Y to %
            }else if (strMsgType.equals('UNIT_IN_GATE')){
                stowFlag = ''
            }else{
                stowFlag = '%'
            }
            def stowFlagAttr = gvyTxtMsgFmt.doIt('stowFlag',stowFlag)

            //ODF
            def odf = u.getFieldValue("unitIsOog");
            odf = odf == true ? 'Y' : ''
            def odfAttr = gvyTxtMsgFmt.doIt('odf',odf)

            phyStatusFields = locationRowAttr+cwWeightAttr+sealAttr+stowRestCodeAttr+stowFlagAttr+odfAttr
            //println('Phyysical Status Fields : '+phyStatusFields)

        }catch(Exception e){
            e.printStackTrace()
        }
        return phyStatusFields

    }

    //Method Retrieves StowCode - CMIS Relation value
    public String getStowCode(String stowId)
    {
        def map = new HashMap()
        map.put("INSP", "3")
        map.put("SHOP", "W")
        map.put("CL", "C")

        def stowCode = map.get(stowId) != null ? map.get(stowId) : stowId;
        return stowCode;
    }


}//Class Ends