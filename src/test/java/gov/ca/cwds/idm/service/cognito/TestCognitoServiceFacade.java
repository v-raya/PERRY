package gov.ca.cwds.idm.service.cognito;

import static gov.ca.cwds.Constants.ABSENT_USER_ID;
import static gov.ca.cwds.Constants.ERROR_USER_ID;
import static gov.ca.cwds.Constants.ES_ERROR_CREATE_USER_EMAIL;
import static gov.ca.cwds.Constants.NEW_USER_ES_FAIL_ID;
import static gov.ca.cwds.Constants.NEW_USER_SUCCESS_ID;
import static gov.ca.cwds.Constants.SOME_PAGINATION_TOKEN;
import static gov.ca.cwds.Constants.USERPOOL;
import static gov.ca.cwds.Constants.USER_NO_RACFID_ID;
import static gov.ca.cwds.Constants.USER_WITH_INACTIVE_STATUS_COGNITO;
import static gov.ca.cwds.Constants.USER_WITH_NO_PHONE_EXTENSION;
import static gov.ca.cwds.Constants.USER_WITH_RACFID_AND_DB_DATA_ID;
import static gov.ca.cwds.Constants.USER_WITH_RACFID_ID;
import static gov.ca.cwds.idm.service.cognito.CustomUserAttribute.COUNTY;
import static gov.ca.cwds.idm.service.cognito.CustomUserAttribute.PERMISSIONS;
import static gov.ca.cwds.idm.service.cognito.CustomUserAttribute.RACFID_CUSTOM;
import static gov.ca.cwds.idm.service.cognito.CustomUserAttribute.RACFID_CUSTOM_2;
import static gov.ca.cwds.idm.service.cognito.CustomUserAttribute.ROLES;
import static gov.ca.cwds.idm.service.cognito.StandardUserAttribute.EMAIL;
import static gov.ca.cwds.idm.service.cognito.StandardUserAttribute.FIRST_NAME;
import static gov.ca.cwds.idm.service.cognito.StandardUserAttribute.LAST_NAME;
import static gov.ca.cwds.idm.service.cognito.StandardUserAttribute.RACFID_STANDARD;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUsersSearchCriteriaUtil.DEFAULT_PAGESIZE;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUsersSearchCriteriaUtil.composeToGetFirstPageByAttribute;
import static gov.ca.cwds.idm.util.TestUtils.attr;
import static gov.ca.cwds.idm.util.TestUtils.date;
import static io.dropwizard.testing.FixtureHelpers.fixture;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.amazonaws.services.cognitoidp.AWSCognitoIdentityProvider;
import com.amazonaws.services.cognitoidp.model.AdminGetUserRequest;
import com.amazonaws.services.cognitoidp.model.AdminGetUserResult;
import com.amazonaws.services.cognitoidp.model.AdminListDevicesRequest;
import com.amazonaws.services.cognitoidp.model.AdminListDevicesResult;
import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.InternalErrorException;
import com.amazonaws.services.cognitoidp.model.ListUsersRequest;
import com.amazonaws.services.cognitoidp.model.ListUsersResult;
import com.amazonaws.services.cognitoidp.model.UserNotFoundException;
import com.amazonaws.services.cognitoidp.model.UserType;
import gov.ca.cwds.idm.WithMockCustomUser;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;
import javax.annotation.PostConstruct;
import liquibase.util.StringUtils;

public class TestCognitoServiceFacade extends CognitoServiceFacadeImpl {

    private AWSCognitoIdentityProvider cognito;

    @PostConstruct
    @Override
    public void init() {
      cognito = mock(AWSCognitoIdentityProvider.class);

      CognitoProperties properties = new CognitoProperties();
      properties.setIamAccessKeyId("iamAccessKeyId");
      properties.setIamSecretKey("iamSecretKey");
      properties.setUserpool(USERPOOL);
      properties.setRegion("us-east-2");

      setProperties(properties);
      setIdentityProvider(cognito);

      TestUser userWithoutRacfid =
          testUser(
              USER_NO_RACFID_ID,
              Boolean.TRUE,
              "FORCE_CHANGE_PASSWORD",
              date(2018, 5, 4),
              date(2018, 5, 30),
              "donzano@gmail.com",
              "Don",
              "Manzano",
              WithMockCustomUser.COUNTY,
              "RFA-rollout:Snapshot-rollout:",
              "CWS-worker:County-admin",
              null);

      TestUser userWithRacfid =
          testUser(
              USER_WITH_RACFID_ID,
              Boolean.TRUE,
              "CONFIRMED",
              date(2018, 5, 4),
              date(2018, 5, 29),
              "julio@gmail.com",
              "Julio",
              "Iglecias",
              WithMockCustomUser.COUNTY,
              "Hotline-rollout",
              "CWS-worker:County-admin",
              "YOLOD");

      TestUser userWithRacfidAndDbData =
          testUser(
              USER_WITH_RACFID_AND_DB_DATA_ID,
              Boolean.TRUE,
              "CONFIRMED",
              date(2018, 5, 3),
              date(2018, 5, 31),
              "garcia@gmail.com",
              "Garcia",
              "Gonzales",
              WithMockCustomUser.COUNTY,
              "test",
              null,
              "SMITHBO");

      TestUser userWithNoPhoneExtension =
          testUser(
              USER_WITH_NO_PHONE_EXTENSION,
              Boolean.TRUE,
              "CONFIRMED",
              date(2018, 5, 3),
              date(2018, 5, 31),
              "gabriel@gmail.com",
              "Gabriel",
              "Huanito",
              WithMockCustomUser.COUNTY,
              "test",
              null,
              "SMITHB2");

      TestUser userWithEnableStatusInactiveInCognito =
          testUser(
              USER_WITH_INACTIVE_STATUS_COGNITO,
              Boolean.FALSE,
              "CONFIRMED",
              date(2018, 5, 3),
              date(2018, 5, 31),
              "smith3rd@gmail.com",
              "Smith",
              "Third",
              WithMockCustomUser.COUNTY,
              "test",
              null,
              "SMITHB3");

      TestUser newSuccessUser =
          testUser(
              NEW_USER_SUCCESS_ID,
              Boolean.TRUE,
              "FORCE_CHANGE_PASSWORD",
              date(2018, 5, 4),
              date(2018, 5, 30),
              "gonzales@gmail.com",
              "Garcia",
              "Gonzales",
              WithMockCustomUser.COUNTY,
              null,
              null,
              null);

      TestUser doraFailUser =
          testUser(
              NEW_USER_ES_FAIL_ID,
              Boolean.TRUE,
              "FORCE_CHANGE_PASSWORD",
              date(2018, 5, 4),
              date(2018, 5, 30),
              ES_ERROR_CREATE_USER_EMAIL,
              "Garcia",
              "Gonzales",
              WithMockCustomUser.COUNTY,
              null,
              null,
              null);

      setUpGetAbsentUserRequestAndResult();

      setUpGetErrorUserRequestAndResult();

      setListUsersRequestAndResult("", userWithoutRacfid, userWithRacfid, userWithRacfidAndDbData);

      setListUsersRequestAndResult(SOME_PAGINATION_TOKEN, userWithoutRacfid);

      setSearchUsersByEmailRequestAndResult("julio@gmail.com", "test@test.com", userWithRacfid);

      setSearchByRacfidRequestAndResult(userWithRacfid);

      setSearchByRacfidRequestAndResult(userWithRacfidAndDbData);

      setSearchByRacfidRequestAndResult(userWithNoPhoneExtension);

      setSearchByRacfidRequestAndResult(userWithEnableStatusInactiveInCognito);

      mockAdminListDevices();
    }

  private void mockAdminListDevices() {
    AdminListDevicesResult result = null;
    try {
      result = CognitoObjectMapperHolder.OBJECT_MAPPER
          .readValue(fixture("fixtures/idm/devices/two_devices.json"),
              AdminListDevicesResult.class);
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
    when(cognito.adminListDevices(any(AdminListDevicesRequest.class))).thenReturn(result);
  }

  private void setListUsersRequestAndResult(String paginationToken, TestUser... testUsers) {
      ListUsersRequest request =
          new ListUsersRequest().withUserPoolId(USERPOOL).withLimit(DEFAULT_PAGESIZE);

      if (StringUtils.isNotEmpty(paginationToken)) {
        request.withPaginationToken(paginationToken);
      }

      List<UserType> userTypes =
          Arrays.stream(testUsers)
              .map(TestCognitoServiceFacade::userType)
              .collect(Collectors.toList());

      ListUsersResult result = new ListUsersResult().withUsers(userTypes);

      when(cognito.listUsers(request)).thenReturn(result);
    }

    private TestUser testUser(
        String id,
        Boolean enabled,
        String status,
        Date userCreateDate,
        Date lastModifiedDate,
        String email,
        String firstName,
        String lastName,
        String county,
        String permissions,
        String roles,
        String racfId) {

      TestUser testUser =
          new TestUser(
              id,
              enabled,
              status,
              userCreateDate,
              lastModifiedDate,
              email,
              firstName,
              lastName,
              county,
              permissions,
              roles,
              racfId);

      setUpGetUserRequestAndResult(testUser);

      return testUser;
    }

    private static Collection<AttributeType> attrs(TestUser testUser) {
      Collection<AttributeType> attrs = new ArrayList<>();

      if (testUser.getEmail() != null) {
        attrs.add(attr(EMAIL.getName(), testUser.getEmail()));
      }
      if (testUser.getFirstName() != null) {
        attrs.add(attr(FIRST_NAME.getName(), testUser.getFirstName()));
      }
      if (testUser.getLastName() != null) {
        attrs.add(attr(LAST_NAME.getName(), testUser.getLastName()));
      }
      if (testUser.getCounty() != null) {
        attrs.add(attr(COUNTY.getName(), testUser.getCounty()));
      }
      if (testUser.getPermissions() != null) {
        attrs.add(attr(PERMISSIONS.getName(), testUser.getPermissions()));
      }
      if (testUser.getRoles() != null) {
        attrs.add(attr(ROLES.getName(), testUser.getRoles()));
      }
      if (testUser.getRacfId() != null) {
        attrs.add(attr(RACFID_CUSTOM.getName(), testUser.getRacfId()));
        attrs.add(attr(RACFID_STANDARD.getName(), testUser.getRacfId()));
        attrs.add(attr(RACFID_CUSTOM_2.getName(), testUser.getRacfId()));
      }
      return attrs;
    }

    private static UserType userType(TestUser testUser) {
      UserType userType =
          new UserType()
              .withUsername(testUser.getId())
              .withEnabled(testUser.getEnabled())
              .withUserCreateDate(testUser.getUserCreateDate())
              .withUserLastModifiedDate(testUser.getLastModifiedDate())
              .withUserStatus(testUser.getStatus());

      userType.withAttributes(attrs(testUser));
      return userType;
    }

    private void setSearchUsersByEmailRequestAndResult(
        String email_correct, String email_wrong, TestUser... testUsers) {
      ListUsersRequest request_correct =
          new ListUsersRequest()
              .withUserPoolId(USERPOOL)
              .withLimit(DEFAULT_PAGESIZE)
              .withFilter("email = \"" + email_correct + "\"");

      ListUsersRequest request_wrong =
          new ListUsersRequest()
              .withUserPoolId(USERPOOL)
              .withLimit(DEFAULT_PAGESIZE)
              .withFilter("email = \"" + email_wrong + "\"");

      List<UserType> userTypes =
          Arrays.stream(testUsers)
              .map(TestCognitoServiceFacade::userType)
              .collect(Collectors.toList());

      ListUsersResult result = new ListUsersResult().withUsers(userTypes);
      ListUsersResult result_empty = new ListUsersResult();

      when(cognito.listUsers(request_correct)).thenReturn(result);
      when(cognito.listUsers(request_wrong)).thenReturn(result_empty);
    }

    private void setUpGetUserRequestAndResult(TestUser testUser) {

      AdminGetUserRequest getUserRequest =
          new AdminGetUserRequest().withUsername(testUser.getId()).withUserPoolId(USERPOOL);

      AdminGetUserResult getUserResult = new AdminGetUserResult();
      getUserResult.setUsername(testUser.getId());
      getUserResult.setEnabled(testUser.getEnabled());
      getUserResult.setUserStatus(testUser.getStatus());
      getUserResult.setUserCreateDate(testUser.getUserCreateDate());
      getUserResult.setUserLastModifiedDate(testUser.getLastModifiedDate());

      getUserResult.withUserAttributes(attrs(testUser));

      when(cognito.adminGetUser(getUserRequest)).thenReturn(getUserResult);
    }

    private void setUpGetAbsentUserRequestAndResult() {

      AdminGetUserRequest getUserRequest =
          new AdminGetUserRequest().withUsername(ABSENT_USER_ID).withUserPoolId(USERPOOL);

      when(cognito.adminGetUser(getUserRequest))
          .thenThrow(new UserNotFoundException("user not found"));
    }

    private void setUpGetErrorUserRequestAndResult() {

      AdminGetUserRequest getUserRequest =
          new AdminGetUserRequest().withUsername(ERROR_USER_ID).withUserPoolId(USERPOOL);

      when(cognito.adminGetUser(getUserRequest))
          .thenThrow(new InternalErrorException("internal error"));
    }

    ListUsersRequest setSearchByRacfidRequestAndResult(TestUser testUser){

      ListUsersRequest request =
          composeListUsersRequest(composeToGetFirstPageByAttribute(RACFID_STANDARD, testUser.getRacfId()));

      ListUsersResult result = new ListUsersResult().withUsers(userType(testUser));

      when(cognito.listUsers(request)).thenReturn(result);

      return request;
    }

  static class TestUser {

    private String id;
    private Boolean enabled;
    private String status;
    private Date userCreateDate;
    private Date lastModifiedDate;
    private String email;
    private String firstName;
    private String lastName;
    private String county;
    private String permissions;
    private String roles;
    private String racfId;

    TestUser(
        String id,
        Boolean enabled,
        String status,
        Date userCreateDate,
        Date lastModifiedDate,
        String email,
        String firstName,
        String lastName,
        String county,
        String permissions,
        String roles,
        String racfId) {
      this.id = id;
      this.enabled = enabled;
      this.status = status;
      this.userCreateDate = userCreateDate;
      this.lastModifiedDate = lastModifiedDate;
      this.email = email;
      this.firstName = firstName;
      this.lastName = lastName;
      this.county = county;
      this.permissions = permissions;
      this.roles = roles;
      this.racfId = racfId;
    }

    public String getId() {
      return id;
    }

    public Boolean getEnabled() {
      return enabled;
    }

    public String getStatus() {
      return status;
    }

    public Date getUserCreateDate() {
      return userCreateDate;
    }

    public Date getLastModifiedDate() {
      return lastModifiedDate;
    }

    public String getEmail() {
      return email;
    }

    public String getFirstName() {
      return firstName;
    }

    public String getLastName() {
      return lastName;
    }

    public String getCounty() {
      return county;
    }

    public String getPermissions() {
      return permissions;
    }

    public String getRoles() {
      return roles;
    }

    public String getRacfId() {
      return racfId;
    }
  }
  }


