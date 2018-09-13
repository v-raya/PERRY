package gov.ca.cwds.idm.service;

import static gov.ca.cwds.idm.persistence.model.OperationType.CREATE;
import static gov.ca.cwds.idm.persistence.model.OperationType.UPDATE;
import static gov.ca.cwds.idm.service.ExecutionStatus.FAIL;
import static gov.ca.cwds.idm.service.ExecutionStatus.SUCCESS;
import static gov.ca.cwds.idm.service.ExecutionStatus.WAS_NOT_EXECUTED;
import static gov.ca.cwds.idm.service.cognito.StandardUserAttribute.EMAIL;
import static gov.ca.cwds.idm.service.cognito.StandardUserAttribute.RACFID_STANDARD;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUsersSearchCriteriaUtil.composeToGetFirstPageByEmail;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUsersSearchCriteriaUtil.composeToGetFirstPageByRacfId;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUtils.getRACFId;
import static gov.ca.cwds.service.messages.MessageCode.ACTICE_USER_WITH_RAFCID_EXISTS_IN_IDM;
import static gov.ca.cwds.service.messages.MessageCode.DUPLICATE_USERID_FOR_RACFID_IN_CWSCMS;
import static gov.ca.cwds.service.messages.MessageCode.ERROR_UPDATE_USER_ENABLED_STATUS;
import static gov.ca.cwds.service.messages.MessageCode.IDM_MAPPING_SCRIPT_ERROR;
import static gov.ca.cwds.service.messages.MessageCode.NOT_AUTHORIZED_TO_ADD_USER_FOR_OTHER_COUNTY;
import static gov.ca.cwds.service.messages.MessageCode.NO_USER_WITH_RACFID_IN_CWSCMS;
import static gov.ca.cwds.service.messages.MessageCode.UNABLE_CREATE_IDM_USER_IN_ES;
import static gov.ca.cwds.service.messages.MessageCode.UNABLE_TO_PURGE_PROCESSED_USER_LOGS;
import static gov.ca.cwds.service.messages.MessageCode.UNABLE_UPDATE_IDM_USER_IN_ES;
import static gov.ca.cwds.service.messages.MessageCode.USER_CREATE_SAVE_TO_SEARCH_AND_DB_LOG_ERRORS;
import static gov.ca.cwds.service.messages.MessageCode.USER_CREATE_SAVE_TO_SEARCH_ERROR;
import static gov.ca.cwds.service.messages.MessageCode.USER_NOTHING_UPDATED;
import static gov.ca.cwds.service.messages.MessageCode.USER_PARTIAL_UPDATE;
import static gov.ca.cwds.service.messages.MessageCode.USER_PARTIAL_UPDATE_AND_SAVE_TO_SEARCH_AND_DB_LOG_ERRORS;
import static gov.ca.cwds.service.messages.MessageCode.USER_PARTIAL_UPDATE_AND_SAVE_TO_SEARCH_ERRORS;
import static gov.ca.cwds.service.messages.MessageCode.USER_UPDATE_SAVE_TO_SEARCH_AND_DB_LOG_ERRORS;
import static gov.ca.cwds.service.messages.MessageCode.USER_UPDATE_SAVE_TO_SEARCH_ERROR;
import static gov.ca.cwds.service.messages.MessageCode.USER_WITH_EMAIL_EXISTS_IN_IDM;
import static gov.ca.cwds.util.Utils.toLowerCase;
import static gov.ca.cwds.util.Utils.toUpperCase;
import static java.util.stream.Collectors.toSet;

import com.amazonaws.services.cognitoidp.model.UserType;
import gov.ca.cwds.PerryProperties;
import gov.ca.cwds.data.persistence.auth.CwsOffice;
import gov.ca.cwds.data.persistence.auth.StaffPerson;
import gov.ca.cwds.idm.dto.User;
import gov.ca.cwds.idm.dto.UserAndOperation;
import gov.ca.cwds.idm.dto.UserEnableStatusRequest;
import gov.ca.cwds.idm.dto.UserIdAndOperation;
import gov.ca.cwds.idm.dto.UserUpdate;
import gov.ca.cwds.idm.dto.UserVerificationResult;
import gov.ca.cwds.idm.dto.UsersPage;
import gov.ca.cwds.idm.dto.UsersSearchCriteria;
import gov.ca.cwds.idm.persistence.model.OperationType;
import gov.ca.cwds.idm.persistence.model.UserLog;
import gov.ca.cwds.idm.service.cognito.CognitoServiceFacade;
import gov.ca.cwds.idm.service.cognito.StandardUserAttribute;
import gov.ca.cwds.idm.service.cognito.dto.CognitoUserPage;
import gov.ca.cwds.idm.service.cognito.dto.CognitoUsersSearchCriteria;
import gov.ca.cwds.idm.service.cognito.util.CognitoUsersSearchCriteriaUtil;
import gov.ca.cwds.idm.service.execution.OptionalExecution;
import gov.ca.cwds.idm.service.execution.PutInSearchExecution;
import gov.ca.cwds.rest.api.domain.PartialSuccessException;
import gov.ca.cwds.rest.api.domain.PerryException;
import gov.ca.cwds.rest.api.domain.auth.GovernmentEntityType;
import gov.ca.cwds.service.CwsUserInfoService;
import gov.ca.cwds.service.dto.CwsUserInfo;
import gov.ca.cwds.service.messages.MessageCode;
import gov.ca.cwds.service.messages.MessagesService;
import gov.ca.cwds.service.scripts.IdmMappingScript;
import gov.ca.cwds.util.CurrentAuthenticatedUserUtil;
import gov.ca.cwds.util.Utils;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.script.ScriptException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

@Service
@Profile("idm")
@SuppressWarnings({"fb-contrib:EXS_EXCEPTION_SOFTENING_NO_CONSTRAINTS"})
public class IdmServiceImpl implements IdmService {

  private static final Logger LOGGER = LoggerFactory.getLogger(IdmServiceImpl.class);

  @Autowired private CognitoServiceFacade cognitoServiceFacade;

  @Autowired private CwsUserInfoService cwsUserInfoService;

  @Autowired private PerryProperties configuration;

  @Autowired private MessagesService messages;

  @Autowired private UserLogService userLogService;

  @Autowired private SearchService searchService;

  @Override
  public User findUser(String id) {
    UserType cognitoUser = cognitoServiceFacade.getCognitoUserById(id);
    return enrichCognitoUser(cognitoUser);
  }

  @Override
  @PreAuthorize("@cognitoServiceFacade.getCountyName(#userId) == principal.getParameter('county_name')")
  public void updateUser(String userId, UserUpdate updateUserDto) {

    UserType existedCognitoUser = cognitoServiceFacade.getCognitoUserById(userId);

    ExecutionStatus updateAttributesStatus =
        updateUserAttributes(userId, updateUserDto, existedCognitoUser);

    OptionalExecution<UserEnableStatusRequest, Boolean> updateUserEnabledExecution =
        executeUpdateEnableStatusOptionally(userId, updateUserDto, existedCognitoUser);

    if (updateAttributesStatus == WAS_NOT_EXECUTED && updateUserEnabledExecution.getExecutionStatus() == FAIL) {
      throw (RuntimeException)updateUserEnabledExecution.getException();
    }

    PutInSearchExecution<String> doraExecution = null;
    if(updateAttributesStatus == SUCCESS || updateUserEnabledExecution.getExecutionStatus() == SUCCESS) {
      doraExecution = updateUserInSearch(userId);
    } else {
      LOGGER.info(messages.get(USER_NOTHING_UPDATED, userId));
    }

    handleUpdatePartialSuccess(
        userId,
        updateAttributesStatus,
        updateUserEnabledExecution,
        doraExecution);
  }

  private ExecutionStatus updateUserAttributes(String userId, UserUpdate updateUserDto, UserType existedCognitoUser) {
    ExecutionStatus updateAttributesStatus = WAS_NOT_EXECUTED;

    if(cognitoServiceFacade.updateUserAttributes(userId, existedCognitoUser, updateUserDto)) {
      updateAttributesStatus = SUCCESS;
    }
    return updateAttributesStatus;
  }

  private void handleUpdatePartialSuccess(
      String userId,
      ExecutionStatus updateAttributesStatus,
      OptionalExecution<UserEnableStatusRequest, Boolean> updateUserEnabledExecution,
      PutInSearchExecution<String> doraExecution) {

    ExecutionStatus updateEnableStatus = updateUserEnabledExecution.getExecutionStatus();
    Exception updateEnableException = updateUserEnabledExecution.getException();

    ExecutionStatus doraStatus = WAS_NOT_EXECUTED;
    Exception doraException = null;
    ExecutionStatus logDbStatus = WAS_NOT_EXECUTED;
    Exception logDbException = null;

    if(doraExecution != null) {
      doraStatus = doraExecution.getExecutionStatus();
      doraException = doraExecution.getException();

      OptionalExecution<String, UserLog> userLogExecution = doraExecution.getUserLogExecution();
      if(userLogExecution != null) {
        logDbStatus = userLogExecution.getExecutionStatus();
        logDbException = userLogExecution.getException();
      }
    }

    if (updateAttributesStatus == SUCCESS && updateEnableStatus == FAIL) {//partial Cognito update
      handleUpdatePartialSuccessWithCognitoFail(
          userId, updateEnableException, doraStatus, doraException, logDbStatus, logDbException);
    } else {//no Cognito partial update
      handleUpdatePartialSuccessNoCognitoFail(
          userId, doraStatus, doraException, logDbStatus, logDbException);
    }
  }

  private void handleUpdatePartialSuccessWithCognitoFail(
      String userId,
      Exception updateEnableException,
      ExecutionStatus doraStatus,
      Exception doraException,
      ExecutionStatus logDbStatus,
      Exception logDbException) {

    if (doraStatus == SUCCESS) {
      throwPartialSuccessException(userId, USER_PARTIAL_UPDATE, updateEnableException);

    } else if (doraStatus == FAIL && logDbStatus == SUCCESS) {
      throwPartialSuccessException(
          userId,
          USER_PARTIAL_UPDATE_AND_SAVE_TO_SEARCH_ERRORS,
          updateEnableException,
          doraException);

    } else if (doraStatus == FAIL && logDbStatus == FAIL) {
      throwPartialSuccessException(
          userId,
          USER_PARTIAL_UPDATE_AND_SAVE_TO_SEARCH_AND_DB_LOG_ERRORS,
          updateEnableException,
          doraException,
          logDbException);
    }
  }

  private void handleUpdatePartialSuccessNoCognitoFail(
      String userId,
      ExecutionStatus doraStatus,
      Exception doraException,
      ExecutionStatus logDbStatus,
      Exception logDbException) {

    if (doraStatus == FAIL && logDbStatus == SUCCESS) {
      throwPartialSuccessException(userId, USER_UPDATE_SAVE_TO_SEARCH_ERROR, doraException);

    } else if (doraStatus == FAIL && logDbStatus == FAIL) {
      throwPartialSuccessException(
          userId, USER_UPDATE_SAVE_TO_SEARCH_AND_DB_LOG_ERRORS, doraException, logDbException);
    }
  }

  private OptionalExecution<UserEnableStatusRequest, Boolean> executeUpdateEnableStatusOptionally(
      String userId, UserUpdate updateUserDto, UserType existedCognitoUser) {

    OptionalExecution<UserEnableStatusRequest, Boolean> updateUserEnabledExecution = new OptionalExecution<UserEnableStatusRequest, Boolean>(
        new UserEnableStatusRequest(
            userId, existedCognitoUser.getEnabled(), updateUserDto.getEnabled())) {
      @Override
      protected Boolean tryMethod(UserEnableStatusRequest userEnableStatusRequest) {
        return cognitoServiceFacade.changeUserEnabledStatus(userEnableStatusRequest);
      }
      @Override
      protected void catchMethod(Exception e) {
        LOGGER.error(messages.get(ERROR_UPDATE_USER_ENABLED_STATUS, userId), e);
      }
    };

    if (updateUserEnabledExecution.getExecutionStatus() == SUCCESS && !updateUserEnabledExecution.getResult()) {
      updateUserEnabledExecution.setExecutionStatus(WAS_NOT_EXECUTED);
    }
    return updateUserEnabledExecution;
  }

  @Override
  public String createUser(User user) {
    UserType userType = cognitoServiceFacade.createUser(user);
    String userId = userType.getUsername();
    PutInSearchExecution doraExecution = createUserInSearch(userType);
    handleCreatePartialSuccess(userId, doraExecution);
    return userId;
  }

  private void handleCreatePartialSuccess(String userId, PutInSearchExecution doraExecution) {
    if (doraExecution.getExecutionStatus() == FAIL) {
      OptionalExecution dbLogExecution = doraExecution.getUserLogExecution();

      if (dbLogExecution.getExecutionStatus() == SUCCESS) {
        MessageCode errorCode = USER_CREATE_SAVE_TO_SEARCH_ERROR;
        String msg = messages.get(errorCode, userId);
        throw new PartialSuccessException(
            userId, msg, errorCode, doraExecution.getException());
      } else {//logging in db failed
        MessageCode errorCode = USER_CREATE_SAVE_TO_SEARCH_AND_DB_LOG_ERRORS;
            String msg = messages.get(errorCode, userId);
        throw new PartialSuccessException(
            userId,
            msg,
            errorCode,
            doraExecution.getException(),
            dbLogExecution.getException());
      }
    }
  }

  @Override
  public UsersPage getUserPage(String paginationToken) {
    CognitoUserPage userPage = cognitoServiceFacade.searchPage(CognitoUsersSearchCriteriaUtil.composeToGetPage(paginationToken));
    List<User> users = enrichCognitoUsersByCws(userPage.getUsers());
    return new UsersPage(users, userPage.getPaginationToken());
  }

  @Override
  public List<User> searchUsers(UsersSearchCriteria criteria) {
    StandardUserAttribute searchAttr = criteria.getSearchAttr();
    Set<String> values = transformSearchValues(criteria.getValues(), searchAttr);

    List<UserType> cognitoUsers = new ArrayList<>();

    for(String value : values) {
      CognitoUsersSearchCriteria cognitoSearchCriteria =
          CognitoUsersSearchCriteriaUtil.composeToGetFirstPageByAttribute(searchAttr, value);
      cognitoUsers.addAll(cognitoServiceFacade.searchAllPages(cognitoSearchCriteria));
    }
    return enrichCognitoUsersByCws(cognitoUsers);
  }

  @Override
  public List<UserAndOperation> getFailedOperations(LocalDateTime lastJobTime) {

    deleteProcessedLogs(lastJobTime);

    List<UserIdAndOperation> dbList = userLogService.getUserIdAndOperations(lastJobTime);

    return filterIdAndOperationList(dbList)
        .stream()
        .map(e -> new UserAndOperation(findUser(e.getId()), e.getOperation()))
        .collect(Collectors.toList());
  }

  private void deleteProcessedLogs(LocalDateTime lastJobTime) {
    int deletedCount = 0;
    try {
      deletedCount = userLogService.deleteProcessedLogs(lastJobTime);
    } catch (Exception e) {
      LOGGER.error(messages.get(UNABLE_TO_PURGE_PROCESSED_USER_LOGS, lastJobTime), e);
    }
    if (deletedCount > 0) {
      LOGGER.info("{} processed user log records are deleted", deletedCount);
    }
  }

  private void throwPartialSuccessException(
      String userId, MessageCode errorCode, Exception... causes) {
    String msg = messages.get(errorCode, userId);
    PartialSuccessException e = new PartialSuccessException(userId, msg, errorCode, causes);
    LOGGER.error(msg, e);
    throw e;
  }

  private static List<UserIdAndOperation> filterIdAndOperationList(List<UserIdAndOperation> inputList) {
    Map<String, OperationType> idAndOperationMap = new HashMap<>();

    for (UserIdAndOperation userIdAndOperation : inputList) {
      String userId = userIdAndOperation.getId();
      OperationType operation = userIdAndOperation.getOperation();
      OperationType existedOperation = idAndOperationMap.get(userId);

      if (existedOperation == null || (existedOperation == CREATE && operation == UPDATE)) {
        idAndOperationMap.put(userId, operation);
      }
    }

    return idAndOperationMap.entrySet()
        .stream()
        .map(e -> new UserIdAndOperation(e.getKey(), e.getValue()))
        .collect(Collectors.toList());
  }

  @Override
  public UserVerificationResult verifyUser(String racfId, String email) {
    email = toLowerCase(email);

    CwsUserInfo cwsUser = getCwsUserByRacfId(racfId);
    if (cwsUser == null) {
      return composeNegativeResultWithMessage(NO_USER_WITH_RACFID_IN_CWSCMS, racfId);
    }
    Collection<UserType> cognitoUsers =
        cognitoServiceFacade.searchPage(composeToGetFirstPageByEmail(email)).getUsers();
    if (!CollectionUtils.isEmpty(cognitoUsers)) {
      return composeNegativeResultWithMessage(USER_WITH_EMAIL_EXISTS_IN_IDM, email);
    }

    User user = composeUser(cwsUser, email);
    if (!Objects.equals(CurrentAuthenticatedUserUtil.getCurrentUserCountyName(), user.getCountyName())) {
      return composeNegativeResultWithMessage(NOT_AUTHORIZED_TO_ADD_USER_FOR_OTHER_COUNTY);
    }

    Collection<UserType> cognitoUsersByRacfId =
        cognitoServiceFacade.searchPage(composeToGetFirstPageByRacfId(toUpperCase(racfId)))
            .getUsers();
    if (!CollectionUtils.isEmpty(cognitoUsersByRacfId)
        && isActiveUserPresent(cognitoUsersByRacfId)) {
      return composeNegativeResultWithMessage(ACTICE_USER_WITH_RAFCID_EXISTS_IN_IDM, racfId);
    }

    return UserVerificationResult.Builder.anUserVerificationResult()
        .withUser(user)
        .withVerificationPassed().build();
  }

  private static boolean isActiveUserPresent(Collection<UserType> cognitoUsers) {
    return cognitoUsers
        .stream()
        .anyMatch(userType -> Objects.equals(userType.getEnabled(), Boolean.TRUE));
  }

  static Set<String> transformSearchValues(Set<String> values, StandardUserAttribute searchAttr) {
    if(searchAttr == RACFID_STANDARD) {
      values = applyFunctionToValues(values, Utils::toUpperCase);
    } else if (searchAttr == EMAIL) {
      values = applyFunctionToValues(values, Utils::toLowerCase);
    }
    return values;
  }

  private static Set<String> applyFunctionToValues(Set<String> values, Function<String, String> function) {
    return values.stream().map(function).collect(toSet());
  }

  private List<User> enrichCognitoUsersByCws(Collection<UserType> cognitoUsers) {
    Map<String, String> userNameToRacfId = new HashMap<>(cognitoUsers.size());
    for (UserType user : cognitoUsers) {
      userNameToRacfId.put(user.getUsername(), getRACFId(user));
    }
    Map<String, CwsUserInfo> idToCmsUser = cwsUserInfoService.findUsers(userNameToRacfId.values())
        .stream().collect(
            Collectors.toMap(CwsUserInfo::getRacfId, e -> e, (user1, user2) -> {
              LOGGER.warn(messages.get(DUPLICATE_USERID_FOR_RACFID_IN_CWSCMS, user1.getRacfId()));
              return user1;
            }));
    IdmMappingScript mapping = configuration.getIdentityManager().getIdmMapping();
    return cognitoUsers
        .stream()
        .map( e -> { try {
          return mapping.map(e, idToCmsUser.get(userNameToRacfId.get(e.getUsername())));
        } catch (ScriptException ex) {
          LOGGER.error(messages.get(IDM_MAPPING_SCRIPT_ERROR));
          throw new PerryException(ex.getMessage(), ex);
        }}).collect(Collectors.toList());
  }

  private PutInSearchExecution<String> updateUserInSearch(String id) {
    return new PutInSearchExecution<String>(id){
      @Override
      protected ResponseEntity<String> tryMethod(String id) {
        User updatedUser = findUser(id);
        return searchService.updateUser(updatedUser);
      }
      @Override
      protected void catchMethod(Exception e) {
        String msg = messages.get(UNABLE_UPDATE_IDM_USER_IN_ES, id);
        LOGGER.error(msg, e);
        setUserLogExecution(userLogService.logUpdate(id));
      }
    };
  }

  private PutInSearchExecution createUserInSearch(UserType userType) {
    return new PutInSearchExecution<UserType>(userType){
      @Override
      protected ResponseEntity<String> tryMethod(UserType userType) {
        User user = enrichCognitoUser(userType);
        return searchService.createUser(user);
      }
      @Override
      protected void catchMethod(Exception e) {
        String msg = messages.get(UNABLE_CREATE_IDM_USER_IN_ES, userType.getUsername());
        LOGGER.error(msg, e);
        setUserLogExecution(userLogService.logCreate(userType.getUsername()));
      }
    };
  }

  private UserVerificationResult composeNegativeResultWithMessage(
      MessageCode errorCode, Object... params) {
    String message = messages.get(errorCode, params);
    LOGGER.info(message);
    return UserVerificationResult.Builder.anUserVerificationResult()
        .withVerificationFailed(errorCode.getValue(), message)
        .build();
  }

  private User composeUser(CwsUserInfo cwsUser, String email) {
    User user = new User();
    user.setEmail(email);
    user.setRacfid(cwsUser.getRacfId());
    enrichDataFromCwsOffice(cwsUser.getCwsOffice(), user);
    enrichDataFromStaffPerson(cwsUser.getStaffPerson(), user);
    return user;
  }

  private void enrichDataFromStaffPerson(StaffPerson staffPerson, final User user) {
    if (staffPerson != null) {
      user.setFirstName(staffPerson.getFirstName());
      user.setLastName(staffPerson.getLastName());
      user.setEndDate(staffPerson.getEndDate());
      user.setStartDate(staffPerson.getStartDate());
    }
  }

  private void enrichDataFromCwsOffice(CwsOffice office, final User user) {
    if (office != null) {
      user.setOffice(office.getCwsOfficeName());
      Optional.ofNullable(office.getPrimaryPhoneNumber())
          .ifPresent(e -> user.setPhoneNumber(e.toString()));
      Optional.ofNullable(office.getPrimaryPhoneExtensionNumber())
          .ifPresent(user::setPhoneExtensionNumber);
      Optional.ofNullable(office.getGovernmentEntityType())
          .ifPresent(
              x -> user.setCountyName((GovernmentEntityType.findBySysId(x)).getDescription()));
    }
  }

  private User enrichCognitoUser(UserType cognitoUser) {
    String racfId = getRACFId(cognitoUser);
    CwsUserInfo cwsUser = getCwsUserByRacfId(racfId);
    try {
      return configuration.getIdentityManager().getIdmMapping().map(cognitoUser, cwsUser);
    } catch (ScriptException e) {
      LOGGER.error(messages.get(IDM_MAPPING_SCRIPT_ERROR));
      throw new PerryException(e.getMessage(), e);
    }
  }

  private CwsUserInfo getCwsUserByRacfId(String racfId) {
    CwsUserInfo cwsUser = null;
    if (racfId != null) {
      List<CwsUserInfo> users =
          cwsUserInfoService.findUsers(Collections.singletonList(toUpperCase(racfId)));
      if (!CollectionUtils.isEmpty(users)) {
        cwsUser = users.get(0);
      }
    }
    return cwsUser;
  }

  public void setSearchService(SearchService searchService) {
    this.searchService = searchService;
  }

  public void setCognitoServiceFacade(
      CognitoServiceFacade cognitoServiceFacade) {
    this.cognitoServiceFacade = cognitoServiceFacade;
  }

  public void setCwsUserInfoService(CwsUserInfoService cwsUserInfoService) {
    this.cwsUserInfoService = cwsUserInfoService;
  }

  public void setUserLogService(UserLogService userLogService) {
    this.userLogService = userLogService;
  }

  public void setMessages(MessagesService messages) {
    this.messages = messages;
  }
}
