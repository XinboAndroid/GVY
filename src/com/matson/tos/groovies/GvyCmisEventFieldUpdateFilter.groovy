/*
*  Srno Doer  Date         Change
*  A1   GR    04/30/2010   commented UNIT_PROPERTY_UPDATE for DAS as need to passing Vesvoy
*  A2   GR    04/30/2010   Added CLI_MAT event
*  A3   GR    05/21/2010   commented SIT Evnt for DAS as we need to passing Vesvoy
*  A4   GR    111/03/10    Added UNIT_SNX_UPDATE to vesvoy filter for NV direct post to Gems
*  A5   GR    10/30/11     TOS2.1 : ADDED UNIT_DISCH_COMEPLTED
*  A6   GR    01/03/12     TOS2.1 : YB CHANGE
*  A7   GR    03/01/12     NIS Event Change
*/

public class GvyCmisEventFieldUpdateFilter
{
    public boolean evntFilterOnPositionFldChng(Object eventType)
    {
        boolean evntFldChange = false;
        try
        {
            ArrayList evntArrList = new ArrayList();
            evntArrList.add("UNIT_SNX_UPDATE"); //A4
            evntArrList.add("NIS_CODING_COMPLETE_BARGE"); //A7
            evntArrList.add("NIS_DETENTION");
            evntArrList.add("NIS_TRUCKER_ASSIGN");
            //evntArrList.add("UNIT_PROPERTY_UPDATE");
            evntArrList.add("CLI_MAT");
            evntArrList.add("UNIT_DISMOUNT");
            evntArrList.add("UNIT_MOUNT");
            evntArrList.add("TRANSFER_TO_P2");
            evntArrList.add("TRANSFER_TO_SI");
            evntArrList.add("TRANSFER_CANCEL");
            evntArrList.add("TRANSFER_TO_WO");
            //evntArrList.add("SIT_ASSIGN");
            //evntArrList.add("SIT_UNASSIGN");
            evntArrList.add("YB_ASSIGN");
            evntArrList.add("YB_UNASSIGN");
            evntArrList.add("TAG_STRIP_ASSIGN");
            evntArrList.add("TAG_STRIP_UNASSIGN");
            evntArrList.add("COMMUNITY_SERVICE_ASSIGN");
            evntArrList.add("COMMUNITY_SERVICE_UNASSIGN");
            evntArrList.add("OVER_ROAD_ASSIGN");
            evntArrList.add("OVER_ROAD_UNASSIGN");
            evntArrList.add("OFF_LEASE_ASSIGN");
            evntArrList.add("OFF_LEASE_UNASSIGN");
            evntArrList.add("MDA_ASSIGN");
            evntArrList.add("MDA_UNASSIGN");
            evntArrList.add("RETURN_TO_CUSTOMER_ASSIGN");
            evntArrList.add("RETURN_TO_CUSTOMER_UNASSIGN");
            evntArrList.add("PASSPASS_ASSIGN (SHOW)");
            evntArrList.add("PASSPASS_UNASSIGN");
            evntArrList.add("PASSPASS_ASSIGN (OTR)");
            evntArrList.add("SHIPPER_REHANDLE");
            evntArrList.add("SHIPPER_REHANDLE_CANCEL");
            evntArrList.add("BDA");
            evntArrList.add("BDB");
            //Removed as it can pass Anyfield
            //evntArrList.add("LNK");
            evntArrList.add("ULK");

            for(evnt in evntArrList)
            {
                if(eventType.equals(evnt)){
                    //println("eventType :"+evnt+"  ArrayValue :"+evntArrList);
                    evntFldChange= true;
                }
            }
        }catch(Exception e){
            e.printStackTrace();
        }
        return evntFldChange;
    }

    //Method Returns the Event Value for DSC
    public String evntFilterOnDscFldChng(Object eventType)
    {
        def dsc = '%';
        try
        {
            ArrayList evntFldList = new ArrayList();
            evntFldList.add("TRANSFER_CANCEL");
            evntFldList.add("SIT_UNASSIGN");
            evntFldList.add("YB_UNASSIGN");
            evntFldList.add("TAG_STRIP_UNASSIGN");
            evntFldList.add("COMMUNITY_SERVICE_UNASSIGN");
            evntFldList.add("OVER_ROAD_UNASSIGN");
            evntFldList.add("OFF_LEASE_UNASSIGN");
            evntFldList.add("MDA_UNASSIGN");
            evntFldList.add("RETURN_TO_CUSTOMER_UNASSIGN");
            evntFldList.add("PASSPASS_UNASSIGN");
            evntFldList.add("SHIPPER_REHANDLE_CANCEL");
            evntFldList.add("UNIT_RECEIVE");
            evntFldList.add("UNIT_IN_GATE");
            evntFldList.add("UNIT_DISCH");
            evntFldList.add("UNIT_DISCH_COMPLETE"); //A5
            evntFldList.add("SIT_DIRECT_TO_YB"); //A5

            for(evnt in evntFldList)
            {
                if(evnt.equals(eventType))
                {
                    dsc="null"
                }
            }

        }catch(Exception e){
            e.printStackTrace();
        }
        return dsc;
    }

}