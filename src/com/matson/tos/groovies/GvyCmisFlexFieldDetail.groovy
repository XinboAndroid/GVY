import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.services.business.event.Event

public class GvyCmisFlexFieldDetail {

    public String doIt(Object gvyTxtMsgFmt, Object unitObj, Object event, String eventType, Object isUnitObj,Object gvyBaseClass)
    {
        //  gvyBaseClass.log("In Class GvyCmisFlexFieldDetail.doIt()")
        def u = unitObj
        def flexFieldAttr = ''
        try
        {
            //HSF7
            def hsf7 = u.getFieldValue("unitFlexString05")
            def hsf7Attr = gvyTxtMsgFmt.doIt('hsf7',hsf7)

            //PMD
            def pmd = null;
            def chasType =u.getFieldValue("unitActiveUfv.ufvFlexString02")
            if(chasType != null){
                pmd = chasType;
            }else{
                def _pmdDt =u.getFieldValue("unitActiveUfv.ufvFlexDate01")
                def strpmd = _pmdDt != null ? (''+_pmdDt) : ''
                pmd =  strpmd.length() > 10 ? strpmd.substring(8,10) : strpmd
            }
            def pmdAttr = gvyTxtMsgFmt.doIt('pmd',pmd)

            //LOCATION RUN
            def  locationRun = '%'
            def locationRunAttr =  gvyTxtMsgFmt.doIt('locationRun',locationRun)

            def drayStatus=u.getFieldValue("unitDrayStatus")
            drayStatus = drayStatus!= null ? drayStatus.getKey() : null
            //MISC2 - A1(flex fielsds-cargo status field Change code)
            def misc2 = ''
            def gvyEditFlag = gvyBaseClass.getGroovyClassInstance("GvyCmisProcessEditFlag");
            misc2 =gvyEditFlag.processEditFlag(event, eventType, unitObj, gvyBaseClass )
            unitObj.setUnitFlexString11(misc2)
            def misc2Attr = gvyTxtMsgFmt.doIt('misc2',misc2)

            //MISC3
            def gvyEventUtil = gvyBaseClass.getGroovyClassInstance("GvyEventUtil");
            def misc3 = getMisc3(u, gvyEventUtil)
            def misc3Attr = gvyTxtMsgFmt.doIt('misc3',misc3)

            flexFieldAttr =  hsf7Attr+pmdAttr+locationRunAttr+misc2Attr+misc3Attr
            // println('flexFieldAttr : '+flexFieldAttr)
        }catch(Exception e){
            e.printStackTrace()
        }
        return flexFieldAttr
    }

    public String getMisc3(Object u , Object gvyEventUtil )
    {
        def misc3 = ''
        try
        {
            def ufvFlexDate2=u.getFieldValue("unitActiveUfv.ufvFlexDate02")
            def flex2Fmt = ufvFlexDate2 != null ? gvyEventUtil.convertToJulianDate(ufvFlexDate2) : ''
            def ufvFlexDate3=u.getFieldValue("unitActiveUfv.ufvFlexDate03")
            def flex3Fmt = ufvFlexDate3 != null ? gvyEventUtil.convertToJulianDate(ufvFlexDate3) : ''
            println('flex2Fmt:::'+flex2Fmt+"   flex3Fmt::::"+flex3Fmt)
            misc3 = flex2Fmt + flex3Fmt

        }catch(Exception e){
            e.printStackTrace()
        }
        return misc3
    }


}//Class Ends