package gov.ca.cwds.security.module;

import gov.ca.cwds.security.annotations.Authorize;
import gov.ca.cwds.security.configuration.SecurityConfiguration;
import gov.ca.cwds.security.permission.AbacPermission;
import java.lang.reflect.Parameter;
import java.util.Collection;
import javax.inject.Inject;
import javax.script.ScriptContext;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.SimpleScriptContext;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.apache.shiro.SecurityUtils;

/**
 * Created by dmitry.rudenko on 9/25/2017.
 */
public class AbacMethodInterceptor implements MethodInterceptor {

  private ScriptEngine scriptEngine;

  @Inject
  private volatile SecurityConfiguration securityConfiguration;

  public AbacMethodInterceptor() {
    scriptEngine = new ScriptEngineManager().getEngineByName("groovy");
  }

  @Override
  public Object invoke(MethodInvocation methodInvocation) throws Throwable {
    inject();
    if (!isEnabled()) {
      return methodInvocation.proceed();
    }

    checkParametersPermissions(methodInvocation);
    Object result = methodInvocation.proceed();
    return checkResultPermissions(result, methodInvocation);
  }

  private Object checkResultPermissions(Object result, MethodInvocation methodInvocation)
      throws ScriptException {
    Authorize authorize = methodInvocation.getMethod().getAnnotation(Authorize.class);
    if (authorize != null && result != null) {
      checkPermissions(authorize, result);
    }
    return result;
  }

  private void checkParametersPermissions(MethodInvocation methodInvocation)
      throws ScriptException {
    Parameter[] parameters = methodInvocation.getMethod().getParameters();
    Object[] args = methodInvocation.getArguments();
    for (int i = 0; i < parameters.length; i++) {
      Authorize authorize = parameters[i].getAnnotation(Authorize.class);
      if (authorize != null && args[i] != null) {
        checkPermissions(authorize, args[i]);
      }
    }
  }

  private void checkPermissions(Authorize authorize, Object arg) throws ScriptException {
    for (String permission : authorize.value()) {
      checkPermission(permission, arg);
    }
  }

  @SuppressWarnings("unchecked")
  private void checkPermission(String permission, Object arg) throws ScriptException {
    AbacPermission abacPermission = new AbacPermission(permission);
    String selector = abacPermission.getSecuredObject().toString();
    int dotIndex = selector.indexOf('.');
    String identifier = dotIndex == -1 ? selector : selector.substring(0, dotIndex);
    ScriptContext scriptContext = new SimpleScriptContext();
    scriptContext.setAttribute(identifier, arg, ScriptContext.ENGINE_SCOPE);

    if (arg instanceof Collection) {
      applyPermissionToCollection(abacPermission, scriptContext, identifier);
    } else {
      applyPermissionToScalar(abacPermission, scriptContext);
    }
  }

  private void applyPermissionToCollection(AbacPermission abacPermission, ScriptContext scriptContext, String identifier)
      throws ScriptException {
    String selector = abacPermission.getSecuredObject().toString();
    selector = "it" + selector.substring(identifier.length());
    final String collectSecuredObjectsScripts = String.format("(%s.collect{%s}).toSet()", identifier, selector);
    @SuppressWarnings("unchecked")
    Collection<Object> securedObjects = (Collection<Object>) scriptEngine.eval(collectSecuredObjectsScripts, scriptContext);
    final int sizeBefore = securedObjects.size();

    abacPermission.setSecuredObject(securedObjects);
    SecurityUtils.getSubject().checkPermission(abacPermission);

    if (sizeBefore != securedObjects.size()) {
      scriptContext.setAttribute("securedObjects", securedObjects, ScriptContext.ENGINE_SCOPE);
      final String filterArgsScripts = String.format("%s.removeIf{!securedObjects.contains(%s)}", identifier, selector);
      scriptEngine.eval(filterArgsScripts, scriptContext);
    }
  }

  private void applyPermissionToScalar(AbacPermission abacPermission, ScriptContext scriptContext)
      throws ScriptException {
    String selector = abacPermission.getSecuredObject().toString();
    @SuppressWarnings("unchecked")
    Collection<Object> securedObjects = (Collection<Object>) scriptEngine
        .eval("[" + selector + "].flatten()", scriptContext);
    for (Object securedObject : securedObjects) {
      abacPermission.setSecuredObject(securedObject);
      SecurityUtils.getSubject().checkPermission(abacPermission);
    }
  }

  private void inject() {
    if (securityConfiguration == null) {
      synchronized (this) {
        if (securityConfiguration == null) {
          initSafely();
        }
      }
    }
  }

  private void initSafely() {
    try {
      SecurityModule.injector().injectMembers(this);
    } catch (Exception e) {
      securityConfiguration = new SecurityConfiguration();
    }
  }

  private boolean isEnabled() {
    final Boolean authorization = securityConfiguration.getAuthorizationEnabled();
    return authorization == null || authorization;
  }
}
