//Class Formats the message string by appending Attr & Attr value

public class GvyMsgFormatter {

    public String doIt(String attrName,Object attrValue )
    {
        try
        {
            def fmtValue = ''
            if(attrValue != null)
            {
                def attrFmtValue= replaceQuotes(attrValue)
                fmtValue = attrName+'=\''+attrFmtValue+'\' '
                return fmtValue;
            }
            else
            {
                return fmtValue;
            }
        }catch(Exception e){
            e.printStackTrace()
        }
    }

    public  String replaceQuotes(Object message)
    {
        def msg = message.toString();
        def replaceAmp = msg.replaceAll('&', '&amp;');
        replaceAmp = replaceAmp.replaceAll('\'', '&apos;');
        return replaceAmp;
    }

}
