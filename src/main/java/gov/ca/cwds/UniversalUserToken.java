package gov.ca.cwds;

import gov.ca.cwds.rest.api.domain.PerryException;
import gov.ca.cwds.rest.api.domain.auth.UserAuthorization;
import gov.ca.cwds.util.UniversalUserTokenDeserializer;
import java.io.IOException;
import java.io.Serializable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import org.codehaus.jackson.annotate.JsonProperty;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.annotate.JsonDeserialize;

/**
 * Created by dmitry.rudenko on 7/28/2017.
 */
@JsonDeserialize(using = UniversalUserTokenDeserializer.class)
public class UniversalUserToken implements RolesHolder, Serializable {

  @JsonProperty("user")
  private String userId;
  private Set<String> roles = new LinkedHashSet<>();
  private Set<String> permissions = new LinkedHashSet<>();
  private String token;
  private Map<String, Object> parameters = new HashMap<>();
  private UserAuthorization authorization;

  public Object getParameter(String parameterName) {
    return parameters.get(parameterName);
  }

  public Object setParameter(String parameterName, Object parameter) {
    return parameters.put(parameterName, parameter);
  }

  public Map<String, Object> getParameters() {
    return parameters;
  }

  public void setParameters(Map<String, Object> parameters) {
    this.parameters = parameters;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  @Override
  public Set<String> getRoles() {
    return roles;
  }

  public void setRoles(Set<String> roles) {
    this.roles = roles;
  }

  public String toString() {
    return userId;
  }

  public UserAuthorization getAuthorization() {
    return authorization;
  }

  public void setAuthorization(UserAuthorization authorization) {
    this.authorization = authorization;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public Set<String> getPermissions() {
    return permissions;
  }

  public void setPermissions(Set<String> permissions) {
    this.permissions = permissions;
  }

  public static UniversalUserToken fromJson(String json)  {
    try {
      return new ObjectMapper().readValue(json, UniversalUserToken.class);
    } catch (IOException e) {
      throw new PerryException(e.getMessage(), e);
    }
  }
}
