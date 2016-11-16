package com.matson.tos;

import com.navis.argo.ArgoField;
import com.navis.argo.ContextHelper
import com.navis.argo.business.api.GroovyApi;
import com.navis.argo.business.atoms.DataSourceEnum
import com.navis.argo.business.atoms.FreightKindEnum;
import com.navis.argo.business.reference.Container;
import com.navis.argo.business.reference.EquipType
import com.navis.argo.business.reference.LineOperator;
import com.navis.framework.business.Roastery
import com.navis.framework.persistence.HibernateApi;
import com.navis.framework.portal.*
import com.navis.framework.util.BizViolation
import com.navis.road.RoadField
import com.navis.road.RoadPropertyKeys;
import com.navis.road.business.RoadFacade
import com.navis.road.business.api.RoadManager;
import com.navis.road.business.atoms.GateLaneClassEnum;
import com.navis.road.business.atoms.TranStatusEnum
import com.navis.road.business.atoms.TruckVisitStatusEnum;
import com.navis.road.business.model.Gate;
import com.navis.road.business.model.GateLane;
import com.navis.road.business.model.TruckVisitDetails;
import com.navis.road.business.model.TruckingCompany
import com.navis.road.business.util.RoadBizUtil

/**
 * Created by psethuraman on 10/31/2015.
 * api.getGroovyClassInstance("GvySubmitTransaction").doSubmitTransaction("TSTU1122160", "TSTBKG1");
 */

/*
class GroovyHello  {
    public String execute() {
        com.navis.argo.business.api.GroovyApi api = new com.navis.argo.business.api.GroovyApi();
        return api.getGroovyClassInstance("GvySubmitTransaction").doSubmitTransaction("TSTU1122160", "TSTBKG1");
        return "Hello World!"
    }
}
*/

public class MATGvySubmitMassTransaction {
    String gateId = "KDK_MASSGATE";
    String stageId = "outgate";
    String laneNbr = "1";
    //String ctrNbr;// = "TSTU1122160";
    //String bkgNbr = "TSTBKG12";
    String trkCo = "APL";
    String lineId = "APL";
    UserContext userContext = null;
    RoadFacade roadFacade = null;
    public String doSubmitTransaction(String inCtrNbr, String inBkgNbr, String inSlot, String inGateId ) throws Exception {
        try {
            GroovyApi groovyApi = new GroovyApi();
            if (inGateId != null) {
                gateId = inGateId;
            }
            roadFacade = (RoadFacade) Roastery.getBean(RoadFacade.BEAN_ID);
            userContext = ContextHelper.getThreadUserContext();
            LineOperator lineOperator = LineOperator.findLineOperatorById(lineId);
            if (inCtrNbr == null) {
                BizViolation.create(RoadPropertyKeys.GATE__CTR_REQUIRED, null, null);
            }
            TruckingCompany truckingCompany = TruckingCompany.findOrCreateTruckingCompany(trkCo);
            TruckVisitDetails tvd = this.createTruckVisit(gateId, stageId);
            FieldChanges fields = new FieldChanges();
            fields.setFieldChange(RoadField.TRAN_CTR_NBR, inCtrNbr);
            fields.setFieldChange(RoadField.TRAN_SUB_TYPE, "RE");
            fields.setFieldChange(RoadField.TRAN_CTR_FREIGHT_KIND, FreightKindEnum.MTY);
            fields.setFieldChange(RoadField.TRAN_EQO_NBR, inBkgNbr);
            fields.setFieldChange(RoadField.TRAN_CHS_NBR, "OWN");
            fields.setFieldChange(RoadField.TRAN_CHS_IS_OWNERS, Boolean.TRUE);

            fields.setFieldChange(RoadField.TRAN_TRKC_ID, truckingCompany.getBzuId());
            fields.setFieldChange(RoadField.TRAN_CTR_OWNER, lineOperator);
            fields.setFieldChange(RoadField.TRAN_STATUS, TranStatusEnum.COMPLETE);
            if (inSlot!= null && inSlot.length()>0) {
                fields.setFieldChange(RoadField.TRAN_FLEX_STRING03, inSlot);
            }
            fields.setFieldChange(RoadField.TRAN_TRUCK_VISIT, tvd.getPrimaryKey());
            fields.setFieldChange(RoadField.TRAN_CREATED, getDate());
            fields.setFieldChange(RoadField.TRAN_CREATOR, userContext.getUserId());
            BizRequest req = new BizRequest(userContext);
            CrudOperation crud = new CrudOperation((Object)null, 1, "TruckTransaction", fields, (Object[])null);
            req.addCrudOperation(crud);
            req.setParameter(RoadField.TRAN_STAGE_ID.getFieldId(), stageId);
            req.setParameter(RoadField.GATE_ID.getFieldId(), gateId);
            BizResponse response = new BizResponse();
            roadFacade.submitTransaction(req, response);
            groovyApi.sendEmail("gbabu@matson.com", "gbabu@matson.com", "Bulk Transaction ", "after submitting ingate" + response);
            if (response.getStatus()== "OK") {
                tvd.setTvdtlsStatus(TruckVisitStatusEnum.COMPLETE);
                HibernateApi.getInstance().saveOrUpdate(tvd);
                RoadBizUtil.commit();
            }
            return response.getStatus();
        } catch (Exception e) {
            return e;
        }

        return "ERR";

    }//doSubmitTransaction Ends

    private Gate findGate(String gateId) {
        Gate gate = Gate.findGateById(gateId);
        return gate;
    }

    private EquipType findOrcreateTranCtrType(String inCtrNbr) {
        EquipType equipType = Container.findEquipment(inCtrNbr).getEqEquipType();
        return equipType;
    }
    private GateLane findOrCreateGateLane(String inGateId) {
        GateLane gateLane = GateLane.findOrCreateLane(laneNbr, GateLaneClassEnum.INOUT, this.findGate(inGateId));
        return gateLane;
    }

    public static Date getDate() {
        Calendar cal = Calendar.getInstance();
        return cal.getTime();
    }

    private TruckVisitDetails createTruckVisit(String inGateId, String inNextStageId) throws Exception {
        TruckingCompany tc = TruckingCompany.findOrCreateTruckingCompany("APL");
        Gate gate = this.findGate(inGateId);
        GateLane gateLane = this.findOrCreateGateLane(inGateId);
        FieldChanges fields = new FieldChanges();
        fields.setFieldChange(RoadField.TVDTLS_TRUCK_LICENSE_NBR, "APLMASS");
        fields.setFieldChange(RoadField.TVDTLS_DRIVER_LICENSE_NBR, "APLMASS");
        fields.setFieldChange(RoadField.TVDTLS_ENTRY_LANE, gateLane.getPrimaryKey());
        fields.setFieldChange(RoadField.TVDTLS_EXIT_LANE, gateLane.getPrimaryKey());
        fields.setFieldChange(RoadField.TVDTLS_GATE, gate.getGateGkey());
        fields.setFieldChange(RoadField.TVDTLS_CREATED, getDate());
        fields.setFieldChange(RoadField.TVDTLS_CREATOR, userContext.getUserId());
        fields.setFieldChange(RoadField.TVDTLS_TRK_COMPANY, tc.getBzuGkey());
        fields.setFieldChange(ArgoField.CVD_DATA_SOURCE, DataSourceEnum.TESTING);
        BizRequest req = new BizRequest(userContext);
        CrudOperation crud = new CrudOperation((Object)null, 1, "TruckVisitDetails", fields, (Object[])null);
        req.addCrudOperation(crud);
        req.setParameter(RoadField.GATE_ID.getFieldId(), inGateId);
        req.setParameter(RoadField.TVDTLS_NEXT_STAGE_ID.getFieldId(), inNextStageId);
        BizResponse response = new BizResponse();
        roadFacade.createTruckVisit(req, response);
//        this.displayResponse(response);
        TruckVisitDetails tvd = (TruckVisitDetails) HibernateApi.getInstance().get(TruckVisitDetails.class, response.getCreatedPrimaryKey());
        return tvd;
    }
}
