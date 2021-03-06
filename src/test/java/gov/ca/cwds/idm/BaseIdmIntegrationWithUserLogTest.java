package gov.ca.cwds.idm;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import gov.ca.cwds.idm.dto.UserIdAndOperation;
import gov.ca.cwds.idm.persistence.ns.OperationType;
import gov.ca.cwds.idm.service.UserLogService;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;

public abstract class BaseIdmIntegrationWithUserLogTest extends BaseIdmIntegrationTest {

  @Autowired
  protected UserLogService userLogService;

  protected final void assertLastUserLog(LocalDateTime startTime, String expectedUserName, OperationType expectedOperation) {
    List<UserIdAndOperation> UserIdAndOperations =
        userLogService.getUserIdAndOperations(startTime);
    assertTrue(UserIdAndOperations.size() > 0);
    UserIdAndOperation lastUserIdAndOperation = UserIdAndOperations.get(UserIdAndOperations.size() - 1);
    assertThat(lastUserIdAndOperation, is(new UserIdAndOperation(expectedUserName, expectedOperation)));
  }
}
