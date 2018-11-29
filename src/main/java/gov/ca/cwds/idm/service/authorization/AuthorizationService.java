package gov.ca.cwds.idm.service.authorization;

import com.amazonaws.services.cognitoidp.model.UserType;
import gov.ca.cwds.idm.dto.User;

public interface AuthorizationService {

  void checkCanViewUser(User user);

  boolean canCreateUser(User user);

  void checkCanUpdateUser(UserType existingUser);

  void checkCanUpdateUser(String userId);

  boolean canUpdateUser(String userId);

  void checkCanResendInvitationMessage(String id);

  void checkCanEditRoles(UserType cognitoUser);

  boolean canEditRoles(User user);
}
