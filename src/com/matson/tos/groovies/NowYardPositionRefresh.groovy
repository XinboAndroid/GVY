import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.framework.persistence.*;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.ArgoReportsField;
import com.navis.framework.business.Roastery;
import com.navis.inventory.business.api.UnitField;

import com.navis.argo.business.atoms.LocTypeEnum;
import com.navis.inventory.business.atoms.UnitVisitStateEnum;
import com.navis.argo.business.model.LocPosition;
import nowsol_chastrack.*;
/*
* Class Passes Now Unit Slot updates for
* 08/16/11 2.1 Updated Email Method
*/
/*<?xml version='1.0' encoding='UTF-8'?><argo:snx xmlns:argo='http://www.navs.com/argo' xmlns:xsi='http://www.w3.org/2001/XMLSchema-instance' xsi:schemaLocation='http://www.navis.com/argo snx.xsd'><groovy class-location='database' class-name='NowYardRowPositionRefresh'><parameters><parameter id='recorder' value='now' /><parameter id='position' value='A15' /><parameter id='note' value='Test' /></parameters></groovy></argo:snx>*/

public class NowYardRowPositionRefresh extends GroovyInjectionBase
{
    def emailSender = null;
    //private static final String emailTo = '1aktosdevteam@matson.com' ;
    def emailTo = '1aktosdevteam@matson.com';

    public void execute(Map inParameters){

        println("Calling NowYardRowPositionRefresh");
        def nowRefreshSnx = null;
        def position = (String) inParameters.get( "position");
        if(position == null || position.trim().length() == 0){
            throw new Exception("Now Postion Recieved is Blank");
        }
        def pos = position
        position = position.replace('.','')
        println("Calling NowYardRowPositionRefresh position :; " + position );
        def chasTrackingBld = getGroovyClassInstance("NowChassisTrackingBuilder")
        try{

            String row = '%'+position+'%'
            LocPosition inPosition = new LocPosition(LocTypeEnum.YARD, 'SI', 300863, null, null)
            List ufvList = findAllUfvByPosition(inPosition,row)
            ufvList = getActiveUnits(ufvList)

            if(ufvList == null || ufvList.size() == 0 ){
                nowRefreshSnx = staticSnx(pos,"empty");
            }else{
                StringBuffer strBuff = new StringBuffer();
                for(aUfv in ufvList){
                    def xml = chasTrackingBld.xmlBuilder(aUfv.getUfvUnit(), null,'refresh:'+pos)
                    strBuff.append(xml);
                }
                nowRefreshSnx =  chasTrackingBld.setSnx(strBuff.toString())
            }

            //chasTrackingBld.postNowMsg(nowRefreshSnx)

        }catch(Exception e){
            e.printStackTrace();
            emailSender = emailSender != null ? emailSender : getGroovyClassInstance("EmailSender")
            emailSender.custSendEmail(emailTo, "N4ToNow : REFRESH ERROR ON ROW : "+position, e.getMessage());
        }
    }


    public List findAllUfvByPosition(LocPosition inPosition, String row) {
        DomainQuery dq = QueryUtils.createDomainQuery("UnitFacilityVisit").addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_TYPE, inPosition.getPosLocType())).addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_GKEY, inPosition.getPosLocGkey())).addDqPredicate(PredicateFactory.eq(UnitField.UFV_POS_LOC_ID, inPosition.getPosLocId())).addDqPredicate(PredicateFactory.like(UnitField.UFV_POS_NAME, row));
        List ufvList = Roastery.getHibernateApi().findEntitiesByDomainQuery(dq);
        return ufvList;
    }

    public List getActiveUnits(List ufvList){
        List arrList = new ArrayList();
        for(aUfv in ufvList){
            if(UnitVisitStateEnum.ACTIVE.equals(aUfv.getUfvVisitState())){
                arrList.add(aUfv)
            }
        }
        return arrList
    }

    public String staticSnx(String position, String note){

        String msg = "<unit transit-state='YARD' unique-key='"+note+"' snx-update-note='refresh:"+position+"' id='"+note+"'>"+
                "<equipment eqid='"+note+"' role='PRIMARY' height-mm='2591' type='C40' class='CHS' />"+
                "<position slot='"+position+"' location='SI' loc-type='YARD' /></unit>";
        def chasTrackingBld = getGroovyClassInstance("NowChassisTrackingBuilder")
        String emptyRow = chasTrackingBld.setSnx( msg)
        return emptyRow;
    }

}//Class Ends