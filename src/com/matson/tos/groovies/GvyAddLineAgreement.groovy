import com.navis.argo.business.reference.LineOperator
import com.navis.road.business.model.TruckingCompany
import com.navis.framework.portal.query.DomainQuery;
import com.navis.framework.portal.QueryUtils;
import com.navis.framework.portal.query.PredicateFactory;
import com.navis.argo.business.atoms.BizRoleEnum;
import com.navis.argo.ArgoRefField;
import com.navis.road.business.model.TruckingCompany
import com.navis.security.business.user.BaseUser
import com.navis.framework.business.atoms.LifeCycleStateEnum;
import com.navis.argo.business.reference.*;
import com.navis.framework.persistence.*;


public class  GvyAddLineAgreement
{

    public  void addLineAgrrementToTrckCmpy(){
        try
        {
            def truckList = getTruckingCmpy()
            int count = 0
            for(atruck in truckList) {
                addLineAgreement(atruck.getBzuId())
                count++
            }
            println("TruckList ::"+truckList.size()+"       TruckList Processed MED :"+count)
        }catch(Exception e){
            e.printStackTrace()
        }

    }


    public Object getTruckingCmpy()
    {
        try{
            DomainQuery dq = QueryUtils.createDomainQuery("TruckingCompany").addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_ROLE, BizRoleEnum.HAULIER)).addDqPredicate(PredicateFactory.eq(ArgoRefField.BZU_LIFE_CYCLE_STATE, LifeCycleStateEnum.ACTIVE));
            //return (TruckingCompany)HibernateApi.getInstance().getUniqueEntityByDomainQuery(dq);
            return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public void addLineAgreement(String trckCmpyId){
        try
        {
            //line Operator takes line String returns LineOperatorObject
            def lineOperatorObj = com.navis.argo.business.reference.LineOperator.findOrCreateLineOperator("MED")
            if(lineOperatorObj != null){
                // println("lineOperatorObj Not Null")
            }
            //IsHouseTrucker set to Boolean = false
            def isHouseTrucker = false
            com.navis.road.business.model.TruckingCompany  truckingCompany =  com.navis.road.business.model.TruckingCompany.findOrCreateTruckingCompany(trckCmpyId)
            def truckingCompanyLine = truckingCompany.addLineAgreement(lineOperatorObj, isHouseTrucker)
            java.text.DateFormat df = new java.text.SimpleDateFormat("yyyy-MM-dd");
            java.util.Date dt = df.parse("2050-12-31");
            truckingCompanyLine.setTrkclineExpirationDate(dt)
            truckingCompanyLine.setTrkclineStatus(com.navis.road.business.atoms.TrkcStatusEnum.OK)

        }catch(Exception e){
            e.printStackTrace()
        }
    }

}//Class Ends