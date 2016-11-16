import com.navis.framework.business.Roastery;
import com.navis.framework.portal.context.PortalApplicationContext;
import com.navis.framework.presentation.internationalization.HardCodedResourceKey;
import com.navis.framework.presentation.util.FrameworkUserActions;
import com.navis.framework.ulc.server.application.navigation.FrameworkNavigationNodes;
import com.navis.framework.util.internationalization.PropertyKeyFactory;
import com.navis.framework.variform.CarinaVariforms;
import com.navis.security.authorization.IMutablePrivilege;
import com.navis.security.authorization.PrivilegeFactory;
import com.navis.security.authorization.securable.ISecuredObjectManager;
import com.navis.security.authorization.securable.loader.SecuredActionElement;
import com.navis.security.authorization.securable.loader.SecuredNode;
import com.navis.security.business.privilege.IPrivilegeManagerMutator;
import com.navis.security.authorization.privilege.PrivilegeIdFactory;
import com.navis.security.authorization.IPrivilege;
/*
* Dynamically seeds a new privilege controlling the session factory screen.
* Normally this would be delivered as an xml configuration in the next release.
*/
class GroovySeedPrivilege {

    public String execute()
    {
        //STEP 1. add the privilege
        IPrivilegeManagerMutator privilegeManager = (IPrivilegeManagerMutator)Roastery.getBean("loaderBasedPrivilegeManager");
        final IMutablePrivilege priv = PrivilegeFactory.createPrivilege("DEBUG_SESSION_FACTORY", null, "2.0", null);
        priv.setNameKey(HardCodedResourceKey.valueOf(PropertyKeyFactory.valueOf("SESSION_FACTORY_PRIV_NAME"), "Debug - Session Factory"));
        priv.setDescriptionKey(HardCodedResourceKey.valueOf(PropertyKeyFactory.valueOf("SESSION_FACTORY_PRIV_DESC"), "Access to Session Factory Screen, get HQL timings. Temporary privilege loaded by groovy for the running of this instance"));
        privilegeManager.addPrivilege(priv);

        // STEP 2. Associate privilege to node and actions
        ISecuredObjectManager securedMananger = (ISecuredObjectManager)PortalApplicationContext.getBean(ISecuredObjectManager.BEAN_ID);
        SecuredNode securedNode = new SecuredNode(priv,FrameworkNavigationNodes.NODE_DIAGNOSTICS_SESSION_FACTORY.getName());
        if (!securedMananger.secureObject(securedNode)) {
            return "Error-Privilege was NOT seeded.";
        }
        securedMananger.secureObject(new SecuredActionElement(priv,FrameworkUserActions.ENABLE_STATISTICS.getName(), null));
        securedMananger.secureObject(new SecuredActionElement(priv,FrameworkUserActions.CLEAR_STATISTICS.getName(), null));
        securedMananger.secureObject(new SecuredActionElement(priv,FrameworkUserActions.DISABLE_STATISTICS.getName(), null));
        securedMananger.secureObject(new SecuredActionElement(priv,FrameworkUserActions.INFO.getName(),CarinaVariforms.TABLE_SESSION_FACTORY));
        SecuredActionElement element = new SecuredActionElement(priv,FrameworkUserActions.ENABLE_STATISTICS.getName(), null);
        IPrivilege foundPriv = privilegeManager.findPrivilege(PrivilegeIdFactory.valueOf("DEBUG_SESSION_FACTORY"));
        return "Created Privilege " + foundPriv;
    }
}