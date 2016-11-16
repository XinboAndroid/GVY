import com.navis.apex.business.model.GroovyInjectionBase;
import com.navis.framework.presentation.internationalization.IMessageTranslatorProvider;
import com.navis.framework.presentation.internationalization.MessageTranslator;
import com.navis.argo.business.atoms.CarrierVisitPhaseEnum;
import com.navis.framework.util.AtomizedEnum;
import com.navis.framework.portal.context.PortalApplicationContext;


class GvyInjTest extends GroovyInjectionBase {
    public String execute(Map inParameters) {

        def inLocale = Locale.ENGLISH;
        def inValue = CarrierVisitPhaseEnum.CLOSED;
        IMessageTranslatorProvider translatorProvider = (IMessageTranslatorProvider)PortalApplicationContext.getBean("messageTranslatorProvider");
        MessageTranslator translator = translatorProvider.getMessageTranslator(inLocale);
        com.navis.framework.util.internationalization.PropertyKey key = ((AtomizedEnum)inValue).getDescriptionPropertyKey();
        Object outValue;
        if(translator.isMessageAvailable(key))
        {
            outValue = translator.getMessage(key);
            log( "outValue=" + outValue);
        }
        log( "key=" + key);


    }
}