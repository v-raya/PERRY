package gov.ca.cwds.idm.service.exception;

import gov.ca.cwds.idm.exception.IdmException;
import gov.ca.cwds.idm.exception.UserAlreadyExistsException;
import gov.ca.cwds.idm.exception.UserNotFoundException;
import gov.ca.cwds.idm.exception.UserValidationException;
import gov.ca.cwds.service.messages.MessageCode;
import gov.ca.cwds.service.messages.MessagesService;
import gov.ca.cwds.service.messages.MessagesService.Messages;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

@Service
@Profile("idm")
public class ExceptionFactory {

  private static final Logger LOGGER = LoggerFactory.getLogger(ExceptionFactory.class);

  private MessagesService messagesService;

  interface IdmExceptionCreator<T extends IdmException> {
    T create(String techMsg, String userMsg, MessageCode messageCode);
  }

  interface IdmExceptionWithCauseCreator<T extends IdmException> {
    T create(String techMsg, String userMsg, MessageCode messageCode, Throwable cause);
  }

  public IdmException createIdmException(MessageCode messageCode, Throwable cause, String... args) {
    return createExceptionWithCause(IdmException::new, cause, messageCode, args);
  }

  public UserNotFoundException createUserNotFoundException(MessageCode messageCode, Throwable cause,
      String... args) {
    return createExceptionWithCause(UserNotFoundException::new, cause, messageCode, args);
  }

  public UserAlreadyExistsException createUserAlreadyExistsException(MessageCode messageCode,
      Throwable cause, String... args) {
    return createExceptionWithCause(UserAlreadyExistsException::new, cause, messageCode, args);
  }

  public UserValidationException createValidationException(MessageCode messageCode,
      String... args) {
    return createException(UserValidationException::new, messageCode, args);
  }

  public UserValidationException createValidationException(MessageCode messageCode, Throwable cause,
      String... args) {
    return createExceptionWithCause(UserValidationException::new, cause, messageCode, args);
  }

  private <T extends IdmException> T createException(IdmExceptionCreator<T> creator,
      MessageCode messageCode, String... args) {
    Messages messages = messagesService.getMessages(messageCode, args);
    LOGGER.error(messages.getTechMsg());
    return creator.create(messages.getTechMsg(), messages.getUserMsg(), messageCode);
  }

  private <T extends IdmException> T createExceptionWithCause(IdmExceptionWithCauseCreator<T> creator,
      Throwable cause, MessageCode messageCode, String... args) {
    Messages messages = messagesService.getMessages(messageCode, args);
    LOGGER.error(messages.getTechMsg());
    return creator.create(messages.getTechMsg(), messages.getUserMsg(), messageCode, cause);
  }

  @Autowired
  public void setMessagesService(MessagesService messagesService) {
    this.messagesService = messagesService;
  }
}