package gov.ca.cwds.idm.service.cognito;

import static gov.ca.cwds.idm.service.cognito.CognitoUtils.COUNTY_ATTR_NAME;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.EMAIL_ATTR_NAME;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.EMAIL_DELIVERY;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.FIRST_NAME_ATTR_NAME;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.LAST_NAME_ATTR_NAME;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.OFFICE_ATTR_NAME;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.PHONE_NUMBER_ATTR_NAME;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.RACFID_ATTR_NAME;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.RACFID_ATTR_NAME_CUSTOM_1;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.RACFID_ATTR_NAME_CUSTOM_2;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.createPermissionsAttribute;
import static gov.ca.cwds.idm.service.cognito.CognitoUtils.createRolesAttribute;
import static gov.ca.cwds.service.messages.MessageCode.ERROR_CONNECT_TO_IDM;
import static gov.ca.cwds.service.messages.MessageCode.ERROR_GET_USER_FROM_IDM;
import static gov.ca.cwds.service.messages.MessageCode.ERROR_UPDATE_USER_IN_IDM;
import static gov.ca.cwds.service.messages.MessageCode.IDM_USER_VALIDATION_FAILED;
import static gov.ca.cwds.service.messages.MessageCode.UNABLE_CREATE_NEW_IDM_USER;
import static gov.ca.cwds.service.messages.MessageCode.USER_NOT_FOUND_BY_ID_IN_IDM;
import static gov.ca.cwds.service.messages.MessageCode.USER_WITH_EMAIL_EXISTS_IN_IDM;
import static gov.ca.cwds.util.Utils.toUpperCase;

import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProviderClientBuilder;
import com.amazonaws.services.cognitoidp.model.AdminCreateUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminCreateUserResult;
import com.amazonaws.services.cognitoidp.model.AdminDisableUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminEnableUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminGetUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.AdminUpdateUserAttributesRequest;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.DescribeUserPoolRequest;
import com.amazonaws.services.cognitoidp.model.InvalidParameterException;
import com.amazonaws.services.cognitoidp.model.ListUsersRequest;
import com.amazonaws.services.cognitoidp.model.ListUsersResult;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.services.cognitoidp.model.UserType;
import com.amazonaws.services.cognitoidp.model.UsernameExistsException;
import gov.ca.cwds.idm.service.cognito.dto.CognitoUserPage;
import gov.ca.cwds.idm.dto.User;
import gov.ca.cwds.idm.dto.UserUpdate;
import gov.ca.cwds.idm.service.cognito.dto.CognitoUsersSearchCriteria;
import gov.ca.cwds.rest.api.domain.PerryException;
import gov.ca.cwds.rest.api.domain.UserAlreadyExistsException;
import gov.ca.cwds.rest.api.domain.UserIdmValidationException;
import gov.ca.cwds.rest.api.domain.UserNotFoundPerryException;
import gov.ca.cwds.service.messages.MessagesService;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.PostConstruct;
import liquibase.util.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service(value = "cognitoServiceFacade")
@Profile("idm")
public class CognitoServiceFacade {
  private static final Logger LOGGER = LoggerFactory.getLogger(CognitoServiceFacade.class);

  @Autowired private CognitoProperties properties;

  @Autowired private MessagesService messages;

  private AWSCognitoIdentityProvider identityProvider;

  @PostConstruct
  public void init() {
    AWSCredentialsProvider credentialsProvider =
        new AWSStaticCredentialsProvider(
            new BasicAWSCredentials(properties.getIamAccessKeyId(), properties.getIamSecretKey()));
    identityProvider =
        AWSCognitoIdentityProviderClientBuilder.standard()
            .withCredentials(credentialsProvider)
            .withRegion(Regions.fromName(properties.getRegion()))
            .build();
  }

  public UserType getById(String id) {
    try {
      return getCognitoUserById(id);
    } catch (UserNotFoundException e) {
      throw new UserNotFoundPerryException(messages.get(USER_NOT_FOUND_BY_ID_IN_IDM, id), e);
    } catch (Exception e) {
      throw new PerryException(messages.get(ERROR_GET_USER_FROM_IDM), e);
    }
  }

  public void updateUser(String id, UserUpdate updateUserDto) {
    try {
      UserType existedCognitoUser = getCognitoUserById(id);
      updateUserAttributes(id, existedCognitoUser, updateUserDto);
      changeUserEnabledStatus(id, existedCognitoUser.getEnabled(), updateUserDto.getEnabled());
    } catch (UserNotFoundException e) {
      throw new UserNotFoundPerryException(messages.get(USER_NOT_FOUND_BY_ID_IN_IDM, id), e);
    } catch (Exception e) {
      throw new PerryException(messages.get(ERROR_UPDATE_USER_IN_IDM), e);
    }
  }

  public String createUser(User user) {

    AdminCreateUserRequest request = createAdminCreateUserRequest(user);

    try {
      AdminCreateUserResult result = identityProvider.adminCreateUser(request);
      return result.getUser().getUsername();

    } catch (UsernameExistsException e) {
      String causeMsg = messages.get(USER_WITH_EMAIL_EXISTS_IN_IDM, user.getEmail());
      String msg = messages.get(UNABLE_CREATE_NEW_IDM_USER, causeMsg);
      LOGGER.error(msg);
      throw new UserAlreadyExistsException(causeMsg, e);

    } catch (InvalidParameterException e) {
      String msg = messages.get(IDM_USER_VALIDATION_FAILED);
      LOGGER.error(msg, e);
      throw new UserIdmValidationException(msg, e);
    }
  }

  public String getCountyName(String userId) {
    try {
      return CognitoUtils.getCountyName(getCognitoUserById(userId));
    } catch (UserNotFoundException e) {
      throw new UserNotFoundPerryException(messages.get(USER_NOT_FOUND_BY_ID_IN_IDM, userId), e);
    }
  }

   public AdminCreateUserRequest createAdminCreateUserRequest(User user) {
    return
        new AdminCreateUserRequest()
            .withUsername(user.getEmail())
            .withUserPoolId(properties.getUserpool())
            .withDesiredDeliveryMediums(EMAIL_DELIVERY)
            .withUserAttributes(buildCreateUserAttributes(user));
  }

  public CognitoUserPage search(CognitoUsersSearchCriteria searchCriteria) {
    ListUsersRequest request = composeListUsersRequest(searchCriteria);
    try {
      ListUsersResult result = identityProvider.listUsers(request);
      return new CognitoUserPage(result.getUsers(), result.getPaginationToken());
    } catch (Exception e) {
      throw new PerryException(messages.get(ERROR_CONNECT_TO_IDM), e);
    }
  }

  public List<UserType> searchAllPages(CognitoUsersSearchCriteria searchCriteria) {
    List<UserType> result = new ArrayList<>();

    CognitoUserPage userPage = search(searchCriteria);
    addPage(result, userPage, searchCriteria);

    return result;
  }

  private void addPage(List<UserType> result, CognitoUserPage userPage,
      CognitoUsersSearchCriteria searchCriteria) {

    result.addAll(userPage.getUsers());
    String paginationToken = userPage.getPaginationToken();

    if (StringUtils.isNotEmpty(paginationToken)) {
      CognitoUsersSearchCriteria searchCriteria2 = new CognitoUsersSearchCriteria(searchCriteria);
      searchCriteria2.setPaginationToken(paginationToken);
      CognitoUserPage userPage2 = search(searchCriteria2);
      addPage(result, userPage2, searchCriteria);
    }
  }

  public void healthCheck() {
    identityProvider.describeUserPool(
        new DescribeUserPoolRequest().withUserPoolId(properties.getUserpool()));
  }

  private List<AttributeType> buildCreateUserAttributes(User user) {

    String racfid = toUpperCase(user.getRacfid());

    AttributesBuilder attributesBuilder =
        new AttributesBuilder()
            .addAttribute(EMAIL_ATTR_NAME, user.getEmail())
            .addAttribute(FIRST_NAME_ATTR_NAME, user.getFirstName())
            .addAttribute(LAST_NAME_ATTR_NAME, user.getLastName())
            .addAttribute(COUNTY_ATTR_NAME, user.getCountyName())
            .addAttribute(OFFICE_ATTR_NAME, user.getOffice())
            .addAttribute(PHONE_NUMBER_ATTR_NAME, user.getPhoneNumber())
            .addAttribute(RACFID_ATTR_NAME, racfid)
            .addAttribute(RACFID_ATTR_NAME_CUSTOM_1, racfid)
            .addAttribute(RACFID_ATTR_NAME_CUSTOM_2, racfid)
            .addAttribute(createPermissionsAttribute(user.getPermissions()))
            .addAttribute(createRolesAttribute(user.getRoles()));
    return attributesBuilder.build();
  }

  private UserType getCognitoUserById(String id) {
    AdminGetUserRequest request =
        new AdminGetUserRequest().withUsername(id).withUserPoolId(properties.getUserpool());
    AdminGetUserResult agur = identityProvider.adminGetUser(request);
    return new UserType()
        .withUsername(agur.getUsername())
        .withAttributes(agur.getUserAttributes())
        .withEnabled(agur.getEnabled())
        .withUserCreateDate(agur.getUserCreateDate())
        .withUserLastModifiedDate(agur.getUserLastModifiedDate())
        .withUserStatus(agur.getUserStatus());
  }

  private void updateUserAttributes(
      String id, UserType existedCognitoUser, UserUpdate updateUserDto) {

    List<AttributeType> updateAttributes = getUpdateAttributes(existedCognitoUser, updateUserDto);

    if (!updateAttributes.isEmpty()) {
      AdminUpdateUserAttributesRequest adminUpdateUserAttributesRequest =
          new AdminUpdateUserAttributesRequest()
              .withUsername(id)
              .withUserPoolId(properties.getUserpool())
              .withUserAttributes(updateAttributes);

      identityProvider.adminUpdateUserAttributes(adminUpdateUserAttributesRequest);
    }
  }

  private List<AttributeType> getUpdateAttributes(
      UserType existedCognitoUser, UserUpdate updateUserDto) {
    List<AttributeType> updateAttributes = new ArrayList<>();

    Set<String> existedUserPermissions = CognitoUtils.getPermissions(existedCognitoUser);
    Set<String> newUserPermissions = updateUserDto.getPermissions();

    if (newUserPermissions != null && !newUserPermissions.equals(existedUserPermissions)) {
      AttributeType permissionsAttr = createPermissionsAttribute(newUserPermissions);
      updateAttributes.add(permissionsAttr);
    }

    return updateAttributes;
  }

  private void changeUserEnabledStatus(String id, Boolean existedEnabled, Boolean newEnabled) {
    if (newEnabled != null && !newEnabled.equals(existedEnabled)) {
      if (newEnabled) {
        AdminEnableUserRequest adminEnableUserRequest =
            new AdminEnableUserRequest().withUsername(id).withUserPoolId(properties.getUserpool());
        identityProvider.adminEnableUser(adminEnableUserRequest);
      } else {
        AdminDisableUserRequest adminDisableUserRequest =
            new AdminDisableUserRequest().withUsername(id).withUserPoolId(properties.getUserpool());
        identityProvider.adminDisableUser(adminDisableUserRequest);
      }
    }
  }

  ListUsersRequest composeListUsersRequest(CognitoUsersSearchCriteria criteria) {
    ListUsersRequest request = new ListUsersRequest().withUserPoolId(properties.getUserpool());
    if (criteria.getPageSize() != null) {
      request = request.withLimit(criteria.getPageSize());
    }
    if (criteria.getPaginationToken() != null) {
      request = request.withPaginationToken(criteria.getPaginationToken());
    }
    if (criteria.getAttrName() != null) {
      request = request.withFilter(criteria.getAttrName() + " = \"" + criteria.getAttrValue() + "\"");
    }
    return request;
  }

  public CognitoProperties getProperties() {
    return properties;
  }

  public void setProperties(CognitoProperties properties) {
    this.properties = properties;
  }

  public AWSCognitoIdentityProvider getIdentityProvider() {
    return identityProvider;
  }

  public void setIdentityProvider(AWSCognitoIdentityProvider identityProvider) {
    this.identityProvider = identityProvider;
  }
  public void setMessagesService(MessagesService messages) {
    this.messages = messages;
  }
}
