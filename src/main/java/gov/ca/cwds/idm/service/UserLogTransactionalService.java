package gov.ca.cwds.idm.service;

import static gov.ca.cwds.config.TokenServiceConfiguration.TOKEN_TRANSACTION_MANAGER;

import gov.ca.cwds.idm.dto.UserIdAndOperation;
import gov.ca.cwds.idm.persistence.ns.entity.UserLog;
import gov.ca.cwds.idm.persistence.ns.repository.UserLogRepository;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Profile("idm")
@Service
@Transactional(value = TOKEN_TRANSACTION_MANAGER)
public class UserLogTransactionalService {

  @Autowired
  private UserLogRepository userLogRepository;

  public UserLog save(UserLog userLog) {
    return userLogRepository.save(userLog);
  }

  @Transactional(value = TOKEN_TRANSACTION_MANAGER, readOnly = true)
  public List<UserIdAndOperation> getUserIdAndOperationTypes(LocalDateTime lastDate) {
    return userLogRepository.getUserIdAndOperationTypes(lastDate);
  }

  public int deleteLogsBeforeDate(LocalDateTime lastDate) {
    return userLogRepository.deleteLogsBeforeDate(lastDate);
  }
}
