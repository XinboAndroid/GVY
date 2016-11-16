import com.navis.apex.business.model.GroovyInjectionBase
import com.navis.argo.ContextHelper
import com.navis.argo.business.atoms.EquipRfrTypeEnum
import com.navis.argo.business.reference.Container
import com.navis.framework.metafields.Metafield
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.util.internationalization.ITranslationContext
import com.navis.framework.util.internationalization.PropertyKey
import com.navis.framework.util.internationalization.PropertyKeyFactory
import com.navis.framework.util.internationalization.TranslationUtils
import com.navis.framework.util.message.MessageLevel
import com.navis.framework.util.unit.TemperatureUnit
import com.navis.framework.util.unit.UnitUtils
import com.navis.road.business.model.TruckTransaction
import com.navis.road.business.util.RoadBizUtil
import com.navis.road.business.workflow.TransactionAndVisitHolder
import org.apache.commons.lang.StringUtils
import com.navis.road.business.atoms.TranSubTypeEnum
import com.navis.argo.business.api.GroovyApi
import com.navis.inventory.business.units.Unit;
import com.navis.framework.util.DateUtil;

import com.navis.services.business.event.Event;
import com.navis.services.business.rules.EventType;
import com.navis.services.business.api.EventManager;
import com.navis.services.business.event.EventFieldChange;
import com.navis.framework.portal.FieldChanges

import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.ArgoRefField;

import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;

import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.road.business.model.TruckingCompany;
import com.navis.road.business.model.TruckingCompanyLine;

import com.navis.framework.business.atoms.LifeCycleStateEnum;
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.road.RoadField;
import com.navis.argo.business.reference.LineOperator;
import com.navis.road.business.atoms.TrkcStatusEnum;
import com.navis.argo.UserArgoField;
import com.navis.argo.business.security.ArgoUser;
import com.navis.security.SecurityField;


/*
* Author : Raghu Iyer
* Date Written : 05/22/2014
* Description: This groovy is used to generate alert email for invalid trucker assignment
*/

public class MatGetFieldChange extends GroovyInjectionBase
{
    def inj = new GroovyInjectionBase();
    private final String  emailFrom = '1aktosdevteam@matson.com'
    String emailTo = "1aktosdevteam@matson.com";
    //private final String emailTo = "1aktosdevteam@matson.com";

    public void getEventChanges(Object event, String type)
    {
        try
        {
            println("Calling getEventChangesTest :: " + event.event.evntAppliedBy);
            def userId = event.event.evntAppliedBy;
            userId = userId != null ? userId.replace("user:",""):userId;
            println("UserId :: " + userId);
            def usrEmail = null;
            DomainQuery dq = QueryUtils.createDomainQuery("ArgoUser").addDqPredicate(PredicateFactory.eq(SecurityField.BUSER_UID, userId));
            println(dq);
            def user =  HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            if (user.size() > 0) {
                Iterator userIter = user.iterator();
                while ( userIter.hasNext()){
                    def usr = userIter.next();
                    usrEmail = usr.buserEMail;
                }
            }

            usrEmail = usrEmail != null ? usrEmail : userId+"@matson.com";

            if (type.equalsIgnoreCase("Trucker")){
                emailTo = emailTo + ";1aktosdevteam@matson.com";
            }else if (type.equalsIgnoreCase("YB Trucker")){
                emailTo = emailTo + ";1aktosdevteam@matson.com";
            }

            def gvyBaseClass = new GroovyInjectionBase()
            Set set = event.getEvent().getEvntFieldChanges();
            Iterator iter = set.iterator();
            EventFieldChange efc;
            while ( iter.hasNext()) {
                efc = (EventFieldChange)iter.next();
                println("get feild change Value :"+efc.getMetafieldId() + "-->"+ efc.getPrevVal() + "-->"+ efc.getNewVal())

                if ("rtgTruckingCompany".equalsIgnoreCase(efc.getMetafieldId()))
                {
                    def unit = event.entity;
                    def dischPort = unit.getFieldValue("unitRouting.rtgPOD1.pointId")
                    println("<<<<unit>>>>>"+unit.unitId);

                    if (type.equalsIgnoreCase("NIS Trucker")){
                        if (dischPort.equalsIgnoreCase("NAW")){
                            emailTo = emailTo + ";MNSNewvesNAW@matson.com";
                        }
                        /*if (dischPort.equalsIgnoreCase("KAH")){
                            emailTo = emailTo + ";KAH@matson.com";
                        }else if (dischPort.equalsIgnoreCase("HIL")){
                            emailTo = emailTo + ";HIL@matson.com";
                        }else if (dischPort.equalsIgnoreCase("KHI")){
                            emailTo = emailTo + ";KHI@matson.com";
                        }else if (dischPort.equalsIgnoreCase("NAW")){
                            emailTo = emailTo + ";MNSNewvesNAW@matson.com";
                        }*/
                    }

                    emailTo = emailTo + ";"+usrEmail;

                    String ctrNbr = unit.unitId;
                    String oldVal = efc.getPrevVal();
                    String newVal = efc.getNewVal();

                    println("Values changed to ::"+oldVal +"::::"+newVal);
                    checkValidTrucker(newVal,ctrNbr,type);
                }
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public void checkValidTrucker(String id, String unit, String type) {
        try {
            def inj = new GroovyInjectionBase();
            def emailSender = inj.getGroovyClassInstance("EmailSender");
            def status = null;

            LineOperator lineMat = LineOperator.findLineOperatorById("MAT");

            DomainQuery dq = QueryUtils.createDomainQuery("TruckingCompany");
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ROLE, BizRoleEnum.HAULIER));
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_LIFE_CYCLE_STATE, LifeCycleStateEnum.ACTIVE));
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_GKEY, id));

            def truckList =  HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            for(atruck in truckList) {
                TruckingCompanyLine truckingCompanyLineMat = TruckingCompanyLine.findTrukcingCompanyLineForLine(atruck,lineMat);
                println("Trucker Id ::"+atruck.bzuId);
                String trucker = atruck.bzuId;
                if (truckingCompanyLineMat != null){
                    for(atruckLineMat in truckingCompanyLineMat) {
                        status = atruckLineMat.getTrkclineStatus();
                        status = status != null ? status.getKey():null;
                        println(unit +" Trucker status for  "+ trucker +":: Is ::"+status);
                        if (status != "OK"){
                            println("Sending Email::"+emailTo);
                            emailSender.custSendEmail(emailFrom,emailTo," Invalid "+type+" : " + trucker + " Assigned for " + unit,unit +" assigned with trucker "+trucker+" which has Line agreement set to "+status+". Please verify and make the corrections if required.");
                        }
                    }
                }else {
                    emailSender.custSendEmail(emailFrom,emailTo," Invalid "+type+" : " + trucker + " Assigned for " + unit,unit +" assigned with trucker "+trucker+" which has no MAT Line agreement. Please verify and make the corrections if required.");
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
}