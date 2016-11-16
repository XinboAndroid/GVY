/*
* SrNo       Name       Date             Comments
*  A1         GR           03/228/09      Updt ReplaceQuotes Method
*/
public class GvyCmisTxtMsgFormatter {

    public String doIt(Object attrName,Object attrValue )
    {
        def attrValueFmt = ''
        def fmtValue = ''
        try
        {
            //Converting value to String
            attrValue = replaceQuotes(''+attrValue)

            if(attrValue == null || attrValue.trim().length() == 0){
                attrValueFmt = 'null'
            }else{
                attrValueFmt = attrValue
            }

            fmtValue = attrName+'=\''+attrValueFmt+'\' '
        }catch(Exception e){
            e.printStackTrace()
        }

        return fmtValue;
    }

    public  String replaceQuotes(Object message)
    {
        def msg = message.toString();
        def replaceAmp = msg.replaceAll('&', '&amp;');
        replaceAmp = replaceAmp.replaceAll('\'', '&apos;');
        replaceAmp = replaceAmp.replaceAll("<", "&lt;")
        replaceAmp =  replaceAmp.replaceAll(">", "&gt;")
        replaceAmp = replaceAmp.replaceAll("\"", "&quot;")
        return replaceAmp;
    }

    public String  createGroovyXml(String msgString)
    {
        def strBuff = new StringBuffer();
        strBuff.append( '<GroovyMsg');
        strBuff.append(' '+msgString);
        strBuff.append( '/>');
        return strBuff.toString();
    }

}//Class Ends