/*
 * Copyright (c) 2012 Navis LLC. All Rights Reserved.
 *
 */

package extension.system
import com.navis.argo.ArgoEntity
import com.navis.argo.ArgoField
import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.argo.business.atoms.WiMoveStageEnum
import com.navis.argo.business.xps.model.WorkAssignment
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.metafields.entity.EntityId
import com.navis.framework.metafields.entity.EntityIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.JoinType
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.query.common.api.QueryResult
import com.navis.inventory.InventoryEntity
import com.navis.inventory.InventoryField
import com.navis.inventory.MovesEntity
import com.navis.inventory.MovesField
import com.navis.inventory.business.atoms.WiEcStateEnum
import com.navis.inventory.business.atoms.WiSuspendStateEnum
import com.navis.inventory.business.moves.WorkInstruction
import com.navis.inventory.business.moves.WorkQueue
import com.navis.inventory.external.inventory.AbstractJobListValidator
import com.navis.inventory.web.IYardInventoryConstants
/**
 * Created with IntelliJ IDEA.
 * User: azharad
 * Date: 7/2/12
 * Time: 5:09 PM
 * To change this template use File | Settings | File Templates.
 */

public class JobListValidationHandlerSample extends AbstractJobListValidator {

    /*
      In this example, we will extract WorkInstruction but want to do all the built-in filter
    */

    public void filterJobList(final Map inJobListData) {

        logMsg("Groovy: filterJobList started!");
        Map cheHolder = (HashMap) inJobListData.get("CHE_HOLDER");
        List<WorkInstruction> jobListData = (LinkedList<WorkInstruction>) inJobListData.get("JOB_LIST");
        computeChesJobList(cheHolder, jobListData);

        logMsg("Groovy: filterJobList Done!");
    }

    private void computeChesJobList(Map inCheMap, List<WorkInstruction> inJobList) {

        List<WorkInstruction> allFetchableWI = findExecutableWIs(inCheMap);

        // Further refine the Work Instrunction with the remaining criteria...
        for (WorkInstruction workInstruction : allFetchableWI) {
            if (!workInstruction.isMarkedForFetch()) {
                logMsg("...rejecting work instruction not marked for fetch %s" + workInstruction.getWiEcStateFetch());
                continue;
            }

            final WiMoveStageEnum moveStage = workInstruction.getWiMoveStage();
            if (moveStage == null) {
                logMsg("...rejecting move stage not defined" + workInstruction.toString());
                continue;
            }

            // restrict to WIs that are in appropriate move stage
            if (isWrongMoveStage(moveStage, workInstruction.getWiMoveKind())) {
                logMsg("...rejecting move stage %s is not appropriate" + moveStage);
                continue;
            }

            //restrict WIs that has non active Work Queues
            final Serializable yardGkey = workInstruction.getWiUyv().getUyvYard().getYrdGkey();
            WorkQueue workQueue = WorkQueue.findByPkey(yardGkey, workInstruction.getWiWqPkey());
            if (workQueue == null || !workQueue.getWqIsBlue()) {
                logMsg("...rejecting work instruction has no work queue or work queue is not active");
                continue;
            }

            // restrict to WIs that are not part of a currently being executed WA (rejected and completed WIs should be included)
            Long cheGkey = (Long) inCheMap.get(ArgoField.CHE_GKEY);
            Long cheId = (Long) inCheMap.get(ArgoField.CHE_ID);
            if (isNotWiExecutingWA(yardGkey, workInstruction,
                    workInstruction.getWiCheWorkAssignmentPkey(), cheGkey)) {
                // Remove this WI since it is part of an executing WA... {
                logMsg("...rejecting work assignment not associated and executed by che " + cheId);
                continue;
            }

            // restrict to first non-completed WI for a unit (container/tbdUnit)
            if (!isWiFirstForUnit(workInstruction)) {
                logMsg("...Must be first non-completed WI for the target Unit");
                continue;
            }

            // any containers that this CHE has set aside should be on the job list - JPS 08.30.01
            if (workInstruction.getWiIsBeingRehandled() &&
                    workInstruction.getWiIntendedCheIndex().equals((Long) inCheMap.get(ArgoField.CHE_ID))) {
                logMsg("...added che has set it aside and it is being rehandled");
            }
            inJobList.add(workInstruction);
        }
    }

    private static boolean isWrongMoveStage(final WiMoveStageEnum inMoveStageEnum,
                                            final WiMoveKindEnum inMoveKindEnum) {
        if (inMoveKindEnum.equals(WiMoveKindEnum.RailDisch)) {
            return !(inMoveStageEnum.equals(WiMoveStageEnum.PLANNED));
        } else {
            return (inMoveStageEnum.equals(WiMoveStageEnum.NONE) ||
                    inMoveStageEnum.equals(WiMoveStageEnum.PUT_UNDERWAY) ||
                    inMoveStageEnum.equals(WiMoveStageEnum.PUT_COMPLETE) ||
                    inMoveStageEnum.equals(WiMoveStageEnum.COMPLETE));
        }
    }

    private List<WorkInstruction> findExecutableWIs(Map inCheMap) {
        // This query needs to be refined according to the requirement
        List<WorkInstruction> wiMovesByPow = new ArrayList();

        DomainQuery dq = QueryUtils.createDomainQuery(MovesEntity.WORK_INSTRUCTION)
                .addDqPredicate(PredicateFactory.ne(MovesField.WI_EC_STATE_FETCH, WiEcStateEnum.NONE))
                .addDqPredicate(PredicateFactory.ne(MovesField.WI_MOVE_STAGE, WiMoveStageEnum.COMPLETE))
                .addDqPredicate(PredicateFactory.not(PredicateFactory.in(MovesField.WI_SUSPEND_STATE,
                [WiSuspendStateEnum.SUSPEND, WiSuspendStateEnum.BYPASS])))
                .addDqPredicate(PredicateFactory.in(MovesField.WI_MOVE_KIND, [
                WiMoveKindEnum.RailLoad,
                WiMoveKindEnum.RailDisch
        ]));
        try {
            List<WorkInstruction> preWIs = HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
            for (WorkInstruction wi : preWIs) {
                dq = QueryUtils.createDomainQuery(MovesEntity.WORK_QUEUE)
                        .addDqPredicate(PredicateFactory.eq(MovesField.WQ_PKEY, wi.getWiWqPkey()))
                        .addDqField(MovesField.WQ_FIRST_RELATED_SHIFT_PKEY);
                QueryResult qr = HibernateApi.getInstance().findValuesByDomainQuery(dq);
                if (qr.getTotalResultCount() > 0) {
                    DomainQuery craneQuery = QueryUtils.createDomainQuery(ArgoEntity.WORK_SHIFT)
                            .addDqPredicate(
                            PredicateFactory.eq(ArgoField.WORKSHIFT_PKEY, qr.getValue(0, MovesField.WQ_FIRST_RELATED_SHIFT_PKEY)))
                            .addDqField(ArgoField.WORKSHIFT_OWNER_POW_PKEY);
                    QueryResult qrPow = HibernateApi.getInstance().findValuesByDomainQuery(craneQuery);
                    if (qrPow.getTotalResultCount() > 0) {
                        Long wiPowPkey = (Long) qrPow.getValue(0, ArgoField.WORKSHIFT_OWNER_POW_PKEY);
                        Long comparePowPkey = 0L;
                        if ((inCheMap.get(ArgoField.CHE_POW_PKEY) != null) &&
                                ((Long) inCheMap.get(ArgoField.CHE_POW_PKEY) != 0)) {
                            comparePowPkey = (Long) inCheMap.get(ArgoField.CHE_POW_PKEY);
                            if (comparePowPkey.equals(wiPowPkey)) {
                                wiMovesByPow.add(wi);
                            }
                        } else {
                            DomainQuery poolQuery = QueryUtils.createDomainQuery(ArgoEntity.POINT_OF_WORK)
                                    .addDqPredicate(
                                    PredicateFactory.eq(ArgoField.POINTOFWORK_OWNER_POOL_REFERENCE,
                                            (Long) inCheMap.get(ArgoField.CHE_POOL_PKEY)))
                                    .addDqField(ArgoField.POINTOFWORK_PKEY);
                            QueryResult poolPow = HibernateApi.getInstance().findValuesByDomainQuery(poolQuery);
                            if (poolPow.getTotalResultCount() > 0) {
                                for (int i = 0; i < poolPow.getTotalResultCount(); i++) {
                                    comparePowPkey = (Long) poolPow.getValue(i, ArgoField.POINTOFWORK_PKEY);

                                    if (comparePowPkey.equals(wiPowPkey)) {
                                        wiMovesByPow.add(wi);
                                        break;
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } catch (Exception ex) {
            logMsg("Fetching Work Instruction exception: " + ex.getMessage());
        }
        finally {
            logMsg("WorkInstruction: finding all executable WIs took : ");
        }

        return wiMovesByPow;
    }

    private Boolean isWiFirstForUnit(WorkInstruction inWorkInstruction) {
        MetafieldId uyvUfv = MetafieldIdFactory.getCompoundMetafieldId(MovesField.WI_UYV, InventoryField.UYV_UFV);
        String ufvAlias = new String("uyvufv");
        EntityId ufvEntityId = EntityIdFactory.valueOf(InventoryEntity.UNIT_FACILITY_VISIT, ufvAlias);
        MetafieldId entityAwareUnitGkey = MetafieldIdFactory.getEntityAwareMetafieldId(ufvEntityId, InventoryField.UFV_UNIT);
        Long wiUnitGKey = inWorkInstruction.getWiUyv().getUyvUfv().getUfvUnit().getUnitGkey();
        if (wiUnitGKey == null) {
            return false;
        }
        DomainQuery queryFirstUnitWI = QueryUtils.createDomainQuery(MovesEntity.WORK_INSTRUCTION)
                .addDqJoin(PredicateFactory.createJoin(JoinType.INNER_JOIN, uyvUfv, ufvAlias))
                .addDqPredicate(PredicateFactory.ne(MovesField.WI_MOVE_STAGE, WiMoveStageEnum.COMPLETE))
                .addDqPredicate(PredicateFactory.eq(entityAwareUnitGkey, wiUnitGKey))
                .addDqPredicate(PredicateFactory.lt(MovesField.WI_MOVE_NUMBER, inWorkInstruction.getWiMoveNumber()));
        try {
            // If any query results at all, then inWiGkey is not the smallest move number for non-completed WI for this unit...
            return HibernateApi.getInstance().findPrimaryKeysByDomainQuery(queryFirstUnitWI).length == 0;
        } finally {
            LOGGER.debug("WorkInstruction: checking if WI is Unit's first WI took : ");
        }
    }

    private Boolean isNotWiExecutingWA(Serializable inYardGkey, WorkInstruction inWorkInstruction,
                                       Long inWAPkey, Long inCheGkey) {
        Boolean isWiExecutingWA = false;
        Long waPkey = inWorkInstruction == null ? null : inWorkInstruction.getWiItvWorkAssignmentPkey();
        if (waPkey != null) {
            WorkAssignment theWA = WorkAssignment.findByPkey(inYardGkey, inWAPkey);
            isWiExecutingWA = theWA != null &&
                    (theWA.getWorkassignmentStatus() == 2 || theWA.getWorkassignmentStatus() == 4 ||
                            (theWA.getWorkassignmentChe() != null &&
                                    theWA.getWorkassignmentChe().getCheGkey() != inCheGkey));
        }
        return isWiExecutingWA;
    }

/*
  In this example, we want to do built-in sort first and then additional sort based on Yard position.
*/

    public void sortJobList(final Map inJobListData) {
        logMsg("Groovy: sortJobList started!");

        //do built-in validations
        super.sortJobList(inJobListData);
        List<WorkInstruction> workInstructions = (LinkedList<WorkInstruction>) inJobListData.get("JOB_LIST");

        Collections.sort(workInstructions, new Comparator<WorkInstruction>() {
            public int compare(WorkInstruction inWorkInstructionA, WorkInstruction inWorkInstructionB) {
                final String fromLocSlotA = inWorkInstructionA.getWiPosition().getPosName();
                final String fromLocSlotB = inWorkInstructionB.getWiPosition().getPosName();
                if (fromLocSlotA == null) {
                    return fromLocSlotB == null ? IYardInventoryConstants.A_EQUALS_B : IYardInventoryConstants.A_GREATER_THAN_B;
                }
                return fromLocSlotA.compareTo(fromLocSlotB);
            }
        });

        logMsg("Groovy: sortJobList Done!");
    }

    private void logMsg(String inMsg) {
        log(inMsg);
        System.out.println(inMsg);
    }
}