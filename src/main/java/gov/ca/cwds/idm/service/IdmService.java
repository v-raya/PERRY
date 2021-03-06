package gov.ca.cwds.idm.service;

import gov.ca.cwds.idm.dto.RegistrationResubmitResponse;
import gov.ca.cwds.idm.dto.User;
import gov.ca.cwds.idm.dto.UserAndOperation;
import gov.ca.cwds.idm.dto.UserUpdate;
import gov.ca.cwds.idm.dto.UserVerificationResult;
import gov.ca.cwds.idm.dto.UsersPage;
import gov.ca.cwds.idm.dto.UsersSearchCriteria;
import java.time.LocalDateTime;
import java.util.List;

public interface IdmService {

  User findUser(String id);

  void updateUser(String id, UserUpdate updateUserDto);

  UserVerificationResult verifyIfUserCanBeCreated(String racfId, String email);

  UsersPage getUserPage(String paginationToken);

  String createUser(User user);

  List<User> searchUsers(UsersSearchCriteria usersSearchCriteria);

  List<UserAndOperation> getFailedOperations(LocalDateTime lastJobTime);

  RegistrationResubmitResponse resendInvitationMessage(String id);
}
