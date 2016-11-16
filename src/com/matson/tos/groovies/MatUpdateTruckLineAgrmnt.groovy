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


public class MatUpdateTruckLineAgrmnt extends GroovyInjectionBase{

    public String execute(Map inParameters) {
        try{

            ArrayList<String> truck = new ArrayList<String>();
            truck.add(0,"1SER");
            truck.add(1,"AIRN");
            truck.add(2,"ALAM");
            truck.add(3,"ALCA");
            truck.add(4,"ALOE");
            truck.add(5,"APT");
            truck.add(6,"ASAA");
            truck.add(7,"ASEZ");
            truck.add(8,"ATRG");
            truck.add(9,"BAAT");
            truck.add(10,"BET");
            truck.add(11,"BHL");
            truck.add(12,"BIAC");
            truck.add(13,"CBRE");
            truck.add(14,"CMSI");
            truck.add(15,"CSWD");
            truck.add(16,"EDS");
            truck.add(17,"ETAI");
            truck.add(18,"FDTD");
            truck.add(19,"FORD");
            truck.add(20,"FRIT");
            truck.add(21,"GEAI");
            truck.add(22,"GLSB");
            truck.add(23,"HAIK");
            truck.add(24,"HAZD");
            truck.add(25,"HH");
            truck.add(26,"HLAH");
            truck.add(27,"HON");
            truck.add(28,"HYBE");
            truck.add(29,"IMPB");
            truck.add(30,"JNKN");
            truck.add(31,"KAMT");
            truck.add(32,"KKDC");
            truck.add(33,"KLKA");
            truck.add(34,"MAAQ");
            truck.add(35,"MARA");
            truck.add(36,"MAUT");
            truck.add(37,"MKAM");
            truck.add(38,"MKDE");
            truck.add(39,"MLAO");
            truck.add(40,"MOAH");
            truck.add(41,"MTAG");
            truck.add(42,"OAHE");
            truck.add(43,"PAAA");
            truck.add(44,"PEAI");
            truck.add(45,"PEXR");
            truck.add(46,"PMBO");
            truck.add(47,"POCF");
            truck.add(48,"PRXA");
            truck.add(49,"PSTI");
            truck.add(50,"RCTT");
            truck.add(51,"RHAD");
            truck.add(52,"RHOM");
            truck.add(53,"RTKI");
            truck.add(54,"S0TJ");
            truck.add(55,"SANG");
            truck.add(56,"SIMU");
            truck.add(57,"THIM");
            truck.add(58,"TJT");
            truck.add(59,"TRTK");
            truck.add(60,"TSAC");
            truck.add(61,"TTAB");
            truck.add(62,"TWAD");
            truck.add(63,"UTAW");
            truck.add(64,"WACH");
            truck.add(65,"WWMD");


            //LineOperator lineMat = LineOperator.findLineOperatorById("MAT");
            //LineOperator lineApl = LineOperator.findLineOperatorById("APL");
            LineOperator lineMed = LineOperator.findLineOperatorById("MED");
            for (String trucker: truck)
            {

                DomainQuery dq = QueryUtils.createDomainQuery("TruckingCompany");
                dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ROLE, BizRoleEnum.HAULIER));
                dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_LIFE_CYCLE_STATE, LifeCycleStateEnum.ACTIVE));
                dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_PER_UNIT_GUARANTEE_LIMIT,"1"));
                dq.addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ID, trucker));
                //LOGGER.warn(dq);
                def truckList =  HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
                LOGGER.warn("Updating ::"+ trucker + "::"+truck.indexOf(trucker) + " For Line HON : TuckerList = "+(truckList != null ? truckList.size() : "0"))
                for(atruck in truckList) {
                    /*TruckingCompanyLine truckingCompanyLineMat = TruckingCompanyLine.findTrukcingCompanyLineForLine(atruck,lineMat);
                        for(atruckLineMat in truckingCompanyLineMat) {
                      atruckLineMat.setTrkclineStatus(TrkcStatusEnum.RCVONLY)
                  }
                  TruckingCompanyLine truckingCompanyLineApl = TruckingCompanyLine.findTrukcingCompanyLineForLine(atruck,lineApl);
                        for(atruckLineApl in truckingCompanyLineApl) {
                      atruckLineApl.setTrkclineStatus(TrkcStatusEnum.RCVONLY)
                  }*/
                    TruckingCompanyLine truckingCompanyLineMed = TruckingCompanyLine.findTrukcingCompanyLineForLine(atruck,lineMed);
                    for(atruckLineMed in truckingCompanyLineMed) {
                        atruckLineMed.setTrkclineStatus(TrkcStatusEnum.RCVONLY)
                    }
                }
                //break;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }
    private static final Logger LOGGER = Logger.getLogger(MatUpdateTruckLineAgrmnt.class);
}