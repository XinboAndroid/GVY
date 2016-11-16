public class GvyCmisListAction
{

    LinkedHashSet linkedSet = new LinkedHashSet();

    public void setActionList(String action)
    {
        linkedSet.add(action);
    }

    public LinkedHashSet  getActionList()
    {
        return linkedSet;
    }

}
