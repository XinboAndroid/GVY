public class GvyXmlMsgFormatter {

    public String doIt(String msgObj)
    {
        println("In Class GvyXmlMsgFormatter.doIt()")

        def gvyXmlMsg = createGroovyXml(msgObj)
        return gvyXmlMsg;
    }

    public String  createGroovyXml(String msgString)
    {
        def strBuff = new StringBuffer();
        strBuff.append( '<GroovyMsg');
        strBuff.append(' '+msgString);
        strBuff.append( '/>');
        return strBuff.toString();
    }


}