package gov.ca.cwds.service;

import gov.ca.cwds.UniversalUserToken;
import gov.ca.cwds.data.reissue.model.PerryTokenEntity;
import gov.ca.cwds.event.UserLoggedInEvent;
import gov.ca.cwds.service.sso.SsoService;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

/**
 * Created by TPT2 on 10/24/2017.
 */
@Service
public class LoginServiceImpl implements LoginService {

  private IdentityMappingService identityMappingService;
  private TokenService tokenService;
  private SsoService ssoService;
  private ApplicationEventPublisher eventPublisher;

  @Override
  public String issueAccessCode(String providerId) {
    SecurityContext securityContext = SecurityContextHolder.getContext();
    UniversalUserToken userToken = (UniversalUserToken) securityContext.getAuthentication().getPrincipal();
    String ssoToken = ssoService.getSsoToken();
    String identity = identityMappingService.map(userToken, providerId);
    String accessCode = tokenService.issueAccessCode(userToken, ssoToken, identity, ssoService.getSecurityContext());
    eventPublisher.publishEvent(new UserLoggedInEvent(userToken, LocalDateTime.now()));
    return accessCode;
  }

  @Override
  public String issueToken(String accessCode) {
    return tokenService.getPerryTokenByAccessCode(accessCode);
  }

  @Override
  public UniversalUserToken validate(String perryToken) {
    PerryTokenEntity perryTokenEntity = tokenService.getPerryToken(perryToken);
    ssoService.validate(perryTokenEntity);
    return UniversalUserToken.fromJson(perryTokenEntity.getJsonToken());
  }

  @Override
  public void invalidate(String perryToken) {
    String ssoToken = tokenService.deleteToken(perryToken);
    ssoService.invalidate(ssoToken);
  }

  @Autowired
  public void setIdentityMappingService(IdentityMappingService identityMappingService) {
    this.identityMappingService = identityMappingService;
  }

  @Autowired
  public void setTokenService(TokenService tokenService) {
    this.tokenService = tokenService;
  }

  @Autowired
  public void setSsoService(SsoService ssoService) {
    this.ssoService = ssoService;
  }

  @Autowired
  public void setEventPublisher(ApplicationEventPublisher eventPublisher) {
    this.eventPublisher = eventPublisher;
  }
}
