import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.road.business.model.TruckingCompany;
import com.navis.road.business.model.TruckingCompanyLine;
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.framework.persistence.HibernateApi;
import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.ArgoRefField;
import com.navis.security.business.user.BaseUser
import com.navis.framework.business.atoms.LifeCycleStateEnum;
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;
import com.navis.road.RoadField;
import com.navis.argo.business.reference.LineOperator;
import com.navis.road.business.atoms.TrkcStatusEnum;
import org.apache.log4j.Logger


public class MatValidateTrucker extends GroovyInjectionBase{
    private final String  emailFrom = '1aktosdevteam@matson.com'
    private final String emailTo = "1aktosdevteam@matson.com";
    //public String execute(Map inParameters) {
    public String execute(String unit, String desTrkr, String rlsToParty, String nisTrkr, String ybTrkr) {

        String trucker = "ABFS";
        //String unit = "MATU123456";
        println("Calling MatValidateTrucker for Trucker ::"+desTrkr+"::"+rlsToParty+"::"+nisTrkr+"::"+ybTrkr);
        if (desTrkr != null){
            checkValidTrucker(unit, desTrkr, "Trucker");
        }

        if (rlsToParty != null){
            checkValidTrucker(unit, rlsToParty, "Release to Party")
        }

        if (nisTrkr != null){
            checkValidTrucker(unit, nisTrkr, "NIS Trucker")
        }

        if (ybTrkr != null){
            checkValidTrucker(unit, ybTrkr, "YB Trucker")
        }

/*
		try {
			def inj = new GroovyInjectionBase();
			def emailSender = inj.getGroovyClassInstance("EmailSender");

			LineOperator lineMat = LineOperator.findLineOperatorById("MAT");

			DomainQuery dq = QueryUtils.createDomainQuery("TruckingCompany");
				dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ROLE, BizRoleEnum.HAULIER));
				dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_LIFE_CYCLE_STATE, LifeCycleStateEnum.ACTIVE));
				dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ID, trucker));

			def truckList =  HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    		for(atruck in truckList) {
		  		TruckingCompanyLine truckingCompanyLineMat = TruckingCompanyLine.findTrukcingCompanyLineForLine(atruck,lineMat);
				for(atruckLineMat in truckingCompanyLineMat) {
					status = atruckLineMat.getTrkclineStatus();
					status = status != null ? status.getKey():null;
					println(unit +" Trucker status for  "+ trucker +":: Is ::"+status);
					if (status != "OK"){
						println("Sending Email");
						emailSender.custSendEmail(emailFrom,emailTo," Invalid Trucker : " + trucker + " Assigned for " + unit,unit +" assigned with trucker "+trucker+" which has Line agreement set to "+status+". Please verify and make the corrections if required.");
					}
				}
			}
		}catch(Exception e){
			e.printStackTrace();
		}*/
    }
    public void checkValidTrucker(String unit, String trucker, String type) {
        try {
            def inj = new GroovyInjectionBase();
            def emailSender = inj.getGroovyClassInstance("EmailSender");
            def status = null;

            LineOperator lineMat = LineOperator.findLineOperatorById("MAT");

            DomainQuery dq = QueryUtils.createDomainQuery("TruckingCompany");
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ROLE, BizRoleEnum.HAULIER));
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_LIFE_CYCLE_STATE, LifeCycleStateEnum.ACTIVE));
            dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ID, trucker));

            def truckList =  HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            for(atruck in truckList) {
                TruckingCompanyLine truckingCompanyLineMat = TruckingCompanyLine.findTrukcingCompanyLineForLine(atruck,lineMat);
                for(atruckLineMat in truckingCompanyLineMat) {
                    status = atruckLineMat.getTrkclineStatus();
                    status = status != null ? status.getKey():null;
                    println(unit +" Trucker status for  "+ trucker +":: Is ::"+status);
                    if (status != "OK"){
                        println("Sending Email");
                        emailSender.custSendEmail(emailFrom,emailTo," Invalid "+type+" : " + trucker + " Assigned for " + unit,unit +" assigned with trucker "+trucker+" which has Line agreement set to "+status+". Please verify and make the corrections if required.");
                    }
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    private static final Logger LOGGER = Logger.getLogger(MatValidateTrucker.class);
}