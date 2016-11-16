/*
 * Copyright (c) 2014 Navis LLC. All Rights Reserved.
 *
 */

package system

import com.navis.argo.ArgoEntity
import com.navis.argo.ArgoField
import com.navis.argo.ContextHelper
import com.navis.argo.XpsEntity
import com.navis.argo.XpsField
import com.navis.argo.business.api.ArgoUtils
import com.navis.argo.business.atoms.WiMoveKindEnum
import com.navis.argo.business.atoms.WiMoveStageEnum
import com.navis.argo.business.model.Yard
import com.navis.argo.business.xps.model.Che
import com.navis.argo.business.xps.model.CheZone
import com.navis.argo.business.xps.model.WorkShift
import com.navis.argo.business.xps.model.XpeCraneActivity
import com.navis.framework.metafields.MetafieldId
import com.navis.framework.metafields.MetafieldIdFactory
import com.navis.framework.persistence.HibernateApi
import com.navis.framework.portal.Ordering
import com.navis.framework.portal.QueryUtils
import com.navis.framework.portal.UserContext
import com.navis.framework.portal.query.DomainQuery
import com.navis.framework.portal.query.PredicateFactory
import com.navis.framework.query.common.api.QueryResult
import com.navis.inventory.MovesEntity
import com.navis.inventory.MovesField
import com.navis.inventory.business.atoms.WiSuspendStateEnum
import com.navis.inventory.business.moves.WorkInstruction
import com.navis.inventory.business.units.UnitFacilityVisit
import com.navis.inventory.external.inventory.AbstractUpdateWIEstimatedMoveTime
import com.navis.spatial.BinEntity
import com.navis.spatial.BinField
import com.navis.spatial.business.model.AbstractBin
import com.navis.spatial.business.model.BinContext
import com.navis.yard.YardEntity
import com.navis.yard.YardField
import org.apache.log4j.Level

/**
 * Created with IntelliJ IDEA.
 * User: kasinra
 * Date: 19/9/13
 * Time: 4:51 PM
 * To change this template use File | Settings | File Templates.
 */
public class UpdateWIEstimatedMoveTimeSample extends AbstractUpdateWIEstimatedMoveTime {

    public void updateWIEstimatedMoveTime() {

        log(Level.INFO, "Groovy : UpdateWIEstimatedMoveTimeSample Started");

        //Gets the current time. all date and time comparision will be on this time
        Date now = ArgoUtils.timeNow();

        //Gets all rail POWs
        QueryResult railPowsQr = findAllRailPows();

        //Returns from this execution if rail pows are null
        if (railPowsQr == null || railPowsQr.getTotalResultCount() == 0) {
            log(Level.INFO, "There are no rail pows found. so returning..");
            return;
        }

        //For each rail pow
        for (int i = 0; i < railPowsQr.getTotalResultCount(); i++) {

            //Gets the pow pkey amd double cycling from Pow.
            Long powPkey = (Long) railPowsQr.getValue(i, ArgoField.POINTOFWORK_PKEY);
            Boolean powDualCycling = (Boolean) railPowsQr.getValue(i, ArgoField.POINTOFWORK_DOUBLE_CYCLING);
            String powName = (String) railPowsQr.getValue(i, ArgoField.POINTOFWORK_NAME);

            log(Level.INFO, "Computing EMT for work instructions of POW, Pkey[" + powPkey + "] and PowName [" + powName + "]");
            //get workshift list for pow
            List<WorkShift> wss = findAllWorkShiftsForPow(powPkey);
            if (wss == null || wss.isEmpty()) {
                log(Level.INFO, "There are no work shifts, so continuing next pow pkey..");
                continue;
            }

            //find the current workshift among give
            WorkShift ws = findWorkShiftForCurrentTime(wss, now);
            if (ws == null) {
                log(Level.INFO, "There are no current or future scheduled work shift exist, so continuing next pow..");
                continue;
            }

            List<Serializable> wqPkeys = findAllWQsForWSs(wss);
            if (wqPkeys == null || wqPkeys.isEmpty()) {
                log(Level.INFO,
                        "There are no active work queues exist for powPkey[" + powPkey + "] and PowName [" + powName + "], so continuing next pow..");
                continue;
            }

            //get the yard entity as it is required for finding transferzones
            Serializable yardGkey = (Serializable) railPowsQr.getValue(i, ArgoField.POINTOFWORK_YARD);
            Yard yard = null;
            if (yardGkey == null) {
                yard = ContextHelper.getThreadYard();
            } else {
                yard = Yard.hydrate(yardGkey);
            }

            //find all che zones(only rail)
            List<CheZone> railRelatedCheZones = findRailCheZones(yard);
            Set<Long> nominatedChesForPow = new HashSet<Long>();

            //get current shift start and endTime
            Date shiftStartTime = ws.getWorkshiftStartTime();
            Date shiftEndTime = new Date(shiftStartTime.getTime() + ws.getWorkshiftDuration());
            //map which holds CheId as key and Wis estimated move time that is operated by the CheId
            Map<Long, Date> lastEmtsForChe = new HashMap<Long, Date>();

            //get all breaks for the current shift
            List<XpeCraneActivity> cas = findCraneActForWS(ws);

            setWincForWIsInWQs(wqPkeys, railRelatedCheZones, nominatedChesForPow, yard);

            for (Serializable wqPkey : wqPkeys) {

                List<WorkInstruction> wis = findAllWIsForWq(wqPkey);
                if (wis == null || wis.isEmpty()) {
                    log(Level.INFO, "There are no work instructions exist for work queue pkey[" + wqPkey + "], so continuing next pow pkey..");
                    continue;
                }

                List<WorkInstruction> ordWis = sortWIsGroupByWINCAndOrderByWiSequence(wis);
                List<Serializable> pairedWIGkeys = new ArrayList<Serializable>();
                for (WorkInstruction wi : ordWis) {
                    //second Part compute EMT for WIs those have WINC != null
                    //compute the move increment time for the work instruction.

                    //To skip the twin WI for which WINC and EMT are already set
                    if (pairedWIGkeys.contains(wi.getWiGkey())) {
                        continue;
                    }
                    int numOfWincs = nominatedChesForPow.size() == 0 ? 1 : nominatedChesForPow.size();
                    String wiLiftType = null;
                    Set<WorkInstruction> tandemSet = wi.getAllWorkInstructionsInTandemSet();
                    if (tandemSet != null && !tandemSet.isEmpty()) {
                        int tandemWisSize = tandemSet.size();
                        if (tandemWisSize == 2) {
                            wiLiftType = WI_TANDEM_LIFT;
                        } else if (tandemWisSize == 4) {
                            wiLiftType = WI_QUAD_LIFT;
                        }
                    }

                    if (wiLiftType == null && (wi.getWiTwinWith().getName().equals("PREV") || wi.getWiTwinWith().getName().equals("NEXT"))) {
                        wiLiftType = WI_TWIN_LIFT;
                    }

                    Long moveIncrTime = computeMoveIncrTime(powDualCycling, ws, wiLiftType, numOfWincs);

                    long timeToUpdate = 0L;
                    Che winc = wi.getWiNominatedChe();
                    Long wincCheId = winc == null ? null : winc.getCheId();

                    if (lastEmtsForChe.get(wincCheId) == null) {
                        //First WI for the WINC
                        //work shift start time is future time
                        if (shiftStartTime.after(now)) {

                            //emt = shift start time + move increment time
                            timeToUpdate = shiftStartTime.getTime() + moveIncrTime;
                        } else {
                            //work shift start time is not future time
                            //check if current time falls during shift break times
                            Date breakEndTime = getBreakETIfTimeForBreak(now, cas);
                            if (breakEndTime != null) {
                                // it is a break time
                                //emt is break end time + move increment time
                                timeToUpdate = breakEndTime.getTime() + moveIncrTime;
                            } else {
                                //current time does not fall during shift break time
                                //current time before shift end time
                                if (now.before(shiftEndTime)) {
                                    //emt is current time + move increment time
                                    timeToUpdate = now.getTime() + moveIncrTime;
                                } else {

                                    //current time is after shift end time
                                    //get the following shift
                                    WorkShift nextWS = ws.getWorkshiftNextShift();
                                    //if there is a following shift
                                    if (nextWS != null) {
                                        timeToUpdate = nextWS.getWorkshiftStartTime().getTime() + moveIncrTime;
                                    } else {
                                        // there is no following shift
                                        //next iteration will set the correct EMT for this work instructions
                                        timeToUpdate = now.getTime() + moveIncrTime;
                                    }
                                }
                            }
                        }
                    } else {
                        //If this work instruction is not the first one
                        Date lastEmtGivenForWINC = lastEmtsForChe.get(wincCheId);
                        Date lastEmtWithIncr = new Date(lastEmtGivenForWINC.getTime() + moveIncrTime);

                        //check if lastEmtWithIncr falls during shift break times
                        Date breakEndTime = getBreakETIfTimeForBreak(lastEmtWithIncr, cas);

                        if (breakEndTime != null) {
                            // it is a break time
                            //emt is break end time + move increment time
                            timeToUpdate = breakEndTime.getTime() + moveIncrTime;
                        } else {
                            //lastEmtWithIncr does not fall during shift break time
                            //check lastEmtWithIncr before shift end time
                            if (lastEmtWithIncr.before(shiftEndTime)) {
                                timeToUpdate = lastEmtWithIncr.getTime();
                            } else {
                                //current time is after shift end time
                                //get the following shift
                                WorkShift nextWS = ws.getWorkshiftNextShift();
                                if (nextWS != null) {
                                    timeToUpdate = nextWS.getWorkshiftStartTime().getTime() + moveIncrTime;
                                } else {
                                    // there is no following shift
                                    //next iteration will set the correct EMT for this work instructions
                                    timeToUpdate = lastEmtWithIncr.getTime();
                                }
                            }
                        }
                    }
                    Date emtToUpdate = new Date(timeToUpdate);
                    wi.setFieldValue(MovesField.WI_ESTIMATED_MOVE_TIME, emtToUpdate);
                    lastEmtsForChe.put(wincCheId, emtToUpdate);
                    //Twin WIs should have the same WINC and EMT
                    boolean updateTwinWithSameEmt = false;

                    if (WI_TWIN_LIFT.equals(wiLiftType)) {
                        WorkInstruction twinWi = wi.getTwinCompanion(false);
                        twinWi.setFieldValue(MovesField.WI_NOMINATED_CHE, winc.getCheGkey());
                        twinWi.setFieldValue(MovesField.WI_ESTIMATED_MOVE_TIME, emtToUpdate);
                        pairedWIGkeys.add(twinWi.getWiGkey());
                    } else if (WI_TANDEM_LIFT.equals(wiLiftType) || WI_QUAD_LIFT.equals(wiLiftType)) {
                        for (WorkInstruction tanWI : tandemSet) {
                            tanWI.setFieldValue(MovesField.WI_NOMINATED_CHE, winc.getCheGkey());
                            tanWI.setFieldValue(MovesField.WI_ESTIMATED_MOVE_TIME, emtToUpdate);
                            pairedWIGkeys.add(tanWI.getWiGkey());
                        }
                    }
                }
            }
        }
    }

    private void setWincForWIsInWQs(List<Serializable> inWqPkeys, List<CheZone> inCheZones, Set<Long> inNCsForPow, Yard inYard) {

        for (Serializable wqPkey : inWqPkeys) {

            //finds all the work instructions belongs to given work shifts
            List<WorkInstruction> wis = findAllWIsForWq(wqPkey);
            if (wis == null || wis.isEmpty()) {
                log(Level.INFO, "There are no work instructions exist for work queue pkey[" + wqPkey + "], so continuing next Work Queue..");
                continue;
            }

            //for each WI
            for (WorkInstruction wi : wis) {
                Che winc = null;
                //check and assign the che from railRelatedCheZones
                if (inCheZones != null && !inCheZones.isEmpty()) {

                    //find the maching che from list of che zones based on work instructions from or to position
                    Long cheId = findMatchingCheIdForWi(wi, inCheZones, inYard);
                    if (cheId != null) {
                        Che matchingChe = Che.findChe(cheId, inYard);
                        //if Che is not found for che id then just log a warning and  continue.
                        if (matchingChe == null) {
                            //very rare case .
                            log(Level.WARN, "Che is not found for the che id[" + cheId + "]");
                        } else {
                            winc = matchingChe;
                        }
                    }
                }

                //local variable winc is not null then update the work instructions with winc.
                if (winc != null) {
                    wi.setFieldValue(MovesField.WI_NOMINATED_CHE, winc.getCheGkey());
                    inNCsForPow.add(winc.getCheId());
                } else {
                    //If winc is not updated by this iteration then we don't need to calculate EMT
                    wi.setFieldValue(MovesField.WI_NOMINATED_CHE, null);
                    inNCsForPow.add(null);
                }
            } //first part is ended
        }
    }

    //this returns the break end time if given time is falls any break times
    private static Date getBreakETIfTimeForBreak(Date inCurTime, List<XpeCraneActivity> inShiftBreaks) {
        for (XpeCraneActivity sbreak : inShiftBreaks) {
            Date breakStartTime = sbreak.getCraneactivityAbsoluteStartTime();
            Date breakEndTime = new Date(breakStartTime.getTime() + sbreak.getCraneactivityDuration());
            if (breakStartTime != null && inCurTime.after(breakStartTime) && inCurTime.before(breakEndTime)) {
                return breakEndTime;
            }
        }
        return null;
    }

    //this method returns all creane activities for the current work shift
    private static List<XpeCraneActivity> findCraneActForWS(WorkShift inWs) {
        DomainQuery dq = QueryUtils.createDomainQuery(XpsEntity.XPE_CRANE_ACTIVITY)
                .addDqPredicate(PredicateFactory.eq(XpsField.CRANEACTIVITY_POSITIONING_OBJ, inWs.getWorkshiftPkey()));
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    //this methods returns the current work shift for current time
    private static WorkShift findWorkShiftForCurrentTime(List<WorkShift> inWss, Date inNow) {
        for (WorkShift ws : inWss) {
            Date wsStartTime = ws.getWorkshiftStartTime();
            if (wsStartTime.after(inNow)) {
                //this work shift will start in future
                return ws;
            } else {
                Long duration = ws.getWorkshiftDuration();
                Date wsEndTime = new Date(wsStartTime.getTime() + duration);
                if (wsEndTime.after(inNow)) {
                    return ws;
                }
            }
        }
        return null;
    }

    //This method compute the move increment time to update with WI.
    public static Long computeMoveIncrTime(Boolean powDualCycle, WorkShift inWs, final String inWiLiftType, int inWincs) {
        Long incrementTime = 0L;

        boolean isTwinLift = false;
        boolean isTandemLift = false;
        boolean isQuadLift = false;

        if (inWiLiftType != null && !inWiLiftType.isEmpty()) {
            if (WI_QUAD_LIFT.equals(inWiLiftType)) {
                isQuadLift = true;
            } else if (WI_TANDEM_LIFT.equals(inWiLiftType)) {
                isTandemLift = true;
            } else if (WI_TWIN_LIFT.equals(inWiLiftType)) {
                isTwinLift = true;
            }
        }

        //check if pow is dual cycle
        if (powDualCycle != null && powDualCycle) {
            long dualProduction = inWs.getWorkshiftDualProduction();
            if (isQuadLift) {
                incrementTime = ((4 * 3600) / dualProduction);
            } else if (isTandemLift || isTwinLift) {
                incrementTime = ((2 * 3600) / dualProduction);
            } else {
                incrementTime = (3600 / dualProduction);
            }
        } else {
            if (isQuadLift) {
                incrementTime = ((4 * 3600) / inWs.getWorkshiftQuadProduction());
            } else if (isTandemLift) {
                incrementTime = ((2 * 3600) / inWs.getWorkshiftTandemProduction());
            } else if (isTwinLift) {
                incrementTime = ((2 * 3600) / inWs.getWorkshiftTwinProduction());
            } else {
                incrementTime = (3600 / inWs.getWorkshiftProduction());
            }
        }
        return incrementTime * 10 * 1000 * inWincs;
    }

    //finds all rail pow entries
    private static QueryResult findAllRailPows() {
        DomainQuery dq = QueryUtils.createDomainQuery(ArgoEntity.POINT_OF_WORK)
                .addDqPredicate(PredicateFactory.eq(ArgoField.POINTOFWORK_IS_RAIL_POW, Boolean.TRUE))
                .addDqField(ArgoField.POINTOFWORK_YARD)
                .addDqField(ArgoField.POINTOFWORK_PKEY)
                .addDqField(ArgoField.POINTOFWORK_DOUBLE_CYCLING)
                .addDqField(ArgoField.POINTOFWORK_NAME);
        return HibernateApi.getInstance().findValuesByDomainQuery(dq);
    }

    //finds all work shifts for the given pow
    private static List<WorkShift> findAllWorkShiftsForPow(Serializable inPowPkey) {
        DomainQuery craneQuery = QueryUtils.createDomainQuery(ArgoEntity.WORK_SHIFT)
                .addDqPredicate(PredicateFactory.eq(ArgoField.WORKSHIFT_OWNER_POW_PKEY, inPowPkey))
                .addDqField(ArgoField.WORKSHIFT_PKEY)
                .addDqOrdering(Ordering.asc(ArgoField.WORKSHIFT_START_TIME));
        return HibernateApi.getInstance().findEntitiesByDomainQuery(craneQuery);
    }

    //this method returns all the WIs belongs to given WQ excludes if their suspendstate is suspend or bypass and movestage is put complete or complete
    //ascending order wi sequence
    private static List<Serializable> findAllWQsForWSs(List<WorkShift> inWss) {

        List<Long> wsPkeys = new ArrayList();
        for (WorkShift ws : inWss) {
            wsPkeys.add(ws.getWorkshiftGkey());
        }

        Ordering[] orderings = new Ordering[2];
        orderings[0] = Ordering.asc(WI_WQ_ORDER);
        orderings[1] = Ordering.asc(MovesField.WI_SEQUENCE);

        DomainQuery wqDq = QueryUtils.createDomainQuery(MovesEntity.WORK_QUEUE)
                .addDqField(MovesField.WQ_PKEY)
                .addDqPredicate(PredicateFactory.eq(MovesField.WQ_IS_BLUE, Boolean.TRUE))
                .addDqPredicate(PredicateFactory.in(MovesField.WQ_FIRST_RELATED_SHIFT, wsPkeys))
                .addDqOrdering(Ordering.asc(MovesField.WQ_ORDER));

        QueryResult qr = HibernateApi.getInstance().findValuesByDomainQuery(wqDq);
        return (List<Serializable>) getGivenFieldValueListFromQR(qr, MovesField.WQ_PKEY);
    }

    private List<WorkInstruction> sortWIsGroupByWINCAndOrderByWiSequence(List<WorkInstruction> inWis) {
        if (inWis == null || inWis.isEmpty()) {
            return inWis;
        }
        Collections.sort(inWis, new WorkInstructionComparator());
        return inWis;
    }

    //this method returns all the WIs belongs to given WQ excludes if their suspendstate is suspend or bypass and movestage is put complete or complete
    //ascending order wi sequence
    private static List<WorkInstruction> findAllWIsForWq(Serializable inWqPkey) {

        DomainQuery dq = QueryUtils.createDomainQuery(MovesEntity.WORK_INSTRUCTION)
                .addDqPredicate(PredicateFactory.eq(MovesField.WI_WQ_PKEY, inWqPkey))
                .addDqPredicate(PredicateFactory.in(MovesField.WI_MOVE_KIND, Arrays.asList(WiMoveKindEnum.RailLoad, WiMoveKindEnum.RailDisch)))
                .addDqPredicate(PredicateFactory.not(PredicateFactory.in(MovesField.WI_MOVE_STAGE, Arrays.asList(WiMoveStageEnum.PUT_COMPLETE,
                WiMoveStageEnum.COMPLETE))))
                .addDqPredicate(PredicateFactory.not(PredicateFactory.in(MovesField.WI_SUSPEND_STATE, Arrays.asList(WiSuspendStateEnum.SUSPEND,
                WiSuspendStateEnum.BYPASS,
                WiSuspendStateEnum.SYSTEM_BYPASS))))
                .addDqOrdering(Ordering.asc(MovesField.WI_SEQUENCE));
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq);
    }

    //This should find all CHEs assigned to given pow order by assigned date and .First assigned Che to pow should be first in list.
    private List<CheZone> findRailCheZones(Yard inYard) {
        if (inYard == null) {
            log(Level.WARN, "Could not find Yard so unable to find Che zones.");
            return null;
        }
        //find the binmodel for yard
        AbstractBin binModel = inYard.getYrdBinModel();
        //if binmodel is null then return null
        if (binModel == null) {
            return null;
        }

        DomainQuery dq = QueryUtils.createDomainQuery(BinEntity.ABSTRACT_BIN)
                .addDqPredicate(PredicateFactory.eq(ABN_BIN_TYPE_ID, "TRACK_PLAN"))
                .addDqField(BinField.ABN_GKEY);

        QueryResult qr = HibernateApi.getInstance().findValuesByDomainQuery(dq);
        List<Object> abnGkeys = getGivenFieldValueListFromQR(qr, BinField.ABN_GKEY);
        if (abnGkeys.isEmpty()) {
            return null;
        }

        DomainQuery tzAssocDq = QueryUtils.createDomainQuery(YardEntity.TRANSFER_ZONE_ASSOCIATION)
                .addDqPredicate(PredicateFactory.in(YardField.TZA_STOWAGE_BIN, abnGkeys))
                .addDqField(BLOCK_NAME);
        QueryResult railTzaQr = HibernateApi.getInstance().findValuesByDomainQuery(tzAssocDq);
        List<Object> abnNames = getGivenFieldValueListFromQR(railTzaQr, BLOCK_NAME);
        if (abnNames.isEmpty()) {
            return null;
        }
        DomainQuery dq1 = QueryUtils.createDomainQuery(ArgoEntity.CHE_ZONE)
                .addDqPredicate(PredicateFactory.in(ArgoField.CHEZONE_SEL_BLOCK, abnNames));
        return HibernateApi.getInstance().findEntitiesByDomainQuery(dq1);
    }

    //this method should find Che zone for the given WI's from or two position and return the Che Id of found Che zone
    private Long findMatchingCheIdForWi(WorkInstruction inWi, List<CheZone> inCheZones, Yard inYard) {
        String yardBlock = "";
        //get the WI's Ufv
        UnitFacilityVisit wiUfv = inWi.getWiUfv();
        //If Ufv is null return null
        if (wiUfv != null) {
            //gets the UFV's optimalRailTzSolt. Assumption optimal rail tz slot groovy is triggered and value is set
            String ufvOptimalRailTZSlot = inWi.getWiUfv().getUfvOptimalRailTZSlot();
            //If  ufvOptimalRailTZSlot == null then find the Che zone based on Wi's to or from position
            if (ufvOptimalRailTZSlot == null) {
                String posName = "";
                if (inWi.getWiMoveKind().equals(WiMoveKindEnum.RailDisch)) {
                    yardBlock = inWi.getWiToPosition().getBlockName();
                    posName = inWi.getWiToPosition().getPosName();
                } else {
                    yardBlock = inWi.getWiFromPosition().getBlockName();
                    posName = inWi.getWiFromPosition().getPosName();
                }
                if (yardBlock == null || yardBlock.isEmpty()) {
                    log(Level.DEBUG, "findMatchingCheIdForWi() yardBlock is null so returning null CHE Id");
                    return null;
                }
                int index = posName.lastIndexOf('-');
                String yardSlot = posName.substring(index + 1);
                for (CheZone chezone : inCheZones) {
                    if (chezone.getChezoneSelBlock().equals(yardBlock)) {
                        int wiTZSlot = Integer.parseInt(yardSlot);
                        Long chezoneOrdFirstColumn = chezone.getChezoneOrdFirstColumn();
                        Long chezoneOrdLastColumn = chezone.getChezoneOrdLastColumn();
                        //check if wi transfer zone slot falls with in che zone coverage.
                        //return the che if range matches
                        if ((wiTZSlot <= chezoneOrdFirstColumn && wiTZSlot >= chezoneOrdLastColumn) ||
                                (wiTZSlot <= chezoneOrdLastColumn && wiTZSlot >= chezoneOrdFirstColumn)) {
                            return chezone.getChezoneCheId();
                        }
                    }
                }
            }
            //If ufvOptimalRailTZSlot is not null then find the matching Che zone based on this field
            else if (inYard != null) {
                UserContext uc = ContextHelper.getThreadUserContext();
                AbstractBin yardBinModel = inYard.getYrdBinModel();
                if (yardBinModel != null) {
                    BinContext stowageContext = BinContext.findBinContext(Yard.CONTAINER_STOWAGE_BIN_CONTEXT);
                    AbstractBin abin = yardBinModel.findDescendantBinFromInternalSlotString(ufvOptimalRailTZSlot, stowageContext);
                    if (abin == null) {
                        log(Level.DEBUG,
                                "findMatchingCheIdForWi() unable to find AbstractBin for [" + ufvOptimalRailTZSlot + "] so returning null CHE Id");
                        return null;
                    }
                    yardBlock = abin.getAbnName();
                    for (CheZone chezone : inCheZones) {
                        if (chezone.getChezoneSelBlock().equals(yardBlock)) {
                            return chezone.getChezoneCheId();
                        }
                    }
                }
            }
        }
        return null;
    }

    private static List<Object> getGivenFieldValueListFromQR(QueryResult inWsQr, MetafieldId inField) {
        List<Object> wsPkeys = new ArrayList<Object>();
        if (inWsQr != null) {
            for (int i = 0; i < inWsQr.getTotalResultCount(); i++) {
                wsPkeys.add(inWsQr.getValue(i, inField));
            }
        }
        return wsPkeys;
    }

    private class WorkInstructionComparator implements Comparator {

        @Override
        public int compare(Object inObj01, Object inObj02) {

            if (inObj01 == null && inObj02 == null) {
                return 0;
            } else if (inObj01 == null) {
                return -1;
            } else if (inObj02 == null) {
                return 1;
            }
            if (!(inObj01 instanceof WorkInstruction) || !(inObj02 instanceof WorkInstruction)) {
                return -1;
            }

            WorkInstruction Wi1 = (WorkInstruction) inObj01;
            WorkInstruction Wi2 = (WorkInstruction) inObj02;

            Che che1 = Wi1.getWiNominatedChe();
            Che che2 = Wi1.getWiNominatedChe();
            Long wi1Seq = Wi1.getWiSequence();
            Long wi2Seq = Wi2.getWiSequence();

            if (che1 == null && che2 == null) {
                return wi1Seq.compareTo(wi2Seq);
            } else if (che1 != null && che2 != null) {
                if (che1.getCheGkey().equals(che2.getCheGkey())) {
                    return wi1Seq.compareTo(wi2Seq);
                } else {
                    return che1.getCheGkey().compareTo(che2.getCheGkey());
                }
            }
            if (che1 == null) {
                return -1;
            }
            return 1;
        }
    }

    public static MetafieldId BLOCK_NAME = MetafieldIdFactory.getCompoundMetafieldId(YardField.TZA_TRANSFER_ZONE_BIN, BinField.ABN_NAME);
    public static MetafieldId ABN_BIN_TYPE_ID = MetafieldIdFactory.getCompoundMetafieldId(BinField.ABN_BIN_TYPE, BinField.BTP_ID);
    public static MetafieldId WI_WQ_ORDER = MetafieldIdFactory.getCompoundMetafieldId(MovesField.WI_WORK_QUEUE, MovesField.WQ_ORDER);
    public static String WI_TWIN_LIFT = "TWIN";
    public static String WI_TANDEM_LIFT = "TANDEM";
    public static String WI_QUAD_LIFT = "QUAD";
}
