package gov.ca.cwds.service;

import gov.ca.cwds.PerryProperties;
import gov.ca.cwds.UniversalUserToken;
import gov.ca.cwds.data.reissue.TokenRepository;
import gov.ca.cwds.data.reissue.model.PerryTokenEntity;
import gov.ca.cwds.rest.api.domain.PerryException;
import gov.ca.cwds.security.jwt.JwtConfiguration;
import java.util.Date;
import java.util.function.Supplier;
import org.apache.commons.lang3.time.DateUtils;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.actuate.endpoint.HealthEndpoint;
import org.springframework.boot.actuate.endpoint.InfoEndpoint;
import org.springframework.boot.autoconfigure.flyway.FlywayAutoConfiguration;
import org.springframework.boot.autoconfigure.liquibase.LiquibaseAutoConfiguration;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.common.DefaultOAuth2AccessToken;
import org.springframework.security.oauth2.common.OAuth2AccessToken;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringRunner.class)
@DataJpaTest(
  excludeAutoConfiguration = {FlywayAutoConfiguration.class, LiquibaseAutoConfiguration.class}
)
@DirtiesContext
@ActiveProfiles("dev")
public class TokenServiceTest {

  @Autowired private TokenService tokenService;
  @Autowired private PerryProperties properties;
  @Autowired TokenRepository tokenRepository;

  @MockBean private HealthEndpoint healthEndpoint;

  @MockBean private InfoEndpoint infoEndpoint;

  private static final String ACCESS_TOKEN = "accessToken";
  private static final String USER_ID = "userId";
  private static final String PERRY_TOKEN = "perryToken";

  @Before
  public void before() {
    JwtConfiguration jwtConfiguration = new JwtConfiguration();
    jwtConfiguration.setTimeout(10);
    properties.setJwt(jwtConfiguration);
    tokenRepository.deleteAll();
  }

  @Test
  public void testLoginFlow() {
    String accessCode = issueAccessCode();
    assert accessCode != null;
    PerryTokenEntity perryTokenEntity = tokenRepository.findOne(PERRY_TOKEN);
    assert perryTokenEntity != null;
    assert perryTokenEntity.getSsoToken().equals(ACCESS_TOKEN);
    assert perryTokenEntity.getCreatedDate() != null;
    assert perryTokenEntity.getAccessCode().equals(accessCode);
    assert perryTokenEntity.getToken().equals(PERRY_TOKEN);
    String perryToken = tokenService.getPerryTokenByAccessCode(accessCode);
    assert perryToken != null;
    assert perryToken.equals(PERRY_TOKEN);
    perryTokenEntity = tokenRepository.findOne(PERRY_TOKEN);
    assert perryTokenEntity.getAccessCode() == null;
  }

  @Test
  public void testExpiredRecordsAreNotDeleted() {
    properties.setTokenRecordTimeout(1000);
    String accessCode = issueAccessCode();
    String accessCode2 = issueAccessCode(PERRY_TOKEN + "2");
    assert !accessCode.equals(accessCode2);
    assert tokenRepository.findAll().size() == 2;
  }

  @Test
  public void testDeleteToken() {
    issueAccessCode();
    String accessToken = tokenService.deleteToken(PERRY_TOKEN);
    assert accessToken.equals(ACCESS_TOKEN);
    assert tokenRepository.findAll().isEmpty();
  }

  @Test(expected = TransactionSystemException.class)
  public void testDeleteNotExistingToken() {
    tokenService.deleteToken(PERRY_TOKEN);
  }

  @Test
  public void testExpiredRecordsAreDeleted() {
    properties.setTokenRecordTimeout(0);
    String accessCode = issueAccessCode();
    String accessCode2 = issueAccessCode(PERRY_TOKEN + "2");
    assert !accessCode.equals(accessCode2);
    assert tokenRepository.findAll().size() == 1;
  }

  @Test
  @Transactional(value = "tokenTransactionManager")
  public void testPerryTokenIsRefreshed() {
    properties.setTokenRecordTimeout(1000);
    Date minLastUsedDate = new Date();
    String accessCode = issueAccessCode();
    final String perryToken = tokenService.getPerryTokenByAccessCode(accessCode);
    validateLastUsedDate(minLastUsedDate, () -> tokenRepository.getOne(perryToken));
    validateLastUsedDate(new Date(), () -> tokenService.getPerryToken(perryToken));
  }

  @Test(expected = PerryException.class)
  @Transactional(value = "tokenTransactionManager")
  public void testExpiredPerryToken() {
    properties.setTokenRecordTimeout(0);
    String accessCode = issueAccessCode();
    String perryToken = tokenService.getPerryTokenByAccessCode(accessCode);
    try {
      tokenService.getPerryToken(perryToken);
    } catch (PerryException e) {
      assert tokenRepository.findAll().isEmpty();
      throw e;
    }
  }

  private void validateLastUsedDate(Date minLastUsedDate, Supplier<PerryTokenEntity> perryTokenSupplier) {
    long minLastUsedDateTime = minLastUsedDate.getTime();
    PerryTokenEntity perryTokenEntity = perryTokenSupplier.get();
    long maxLastUsedDateTime = new Date().getTime();
    long lastUsedDate = perryTokenEntity.getLastUsedDate().getTime();
    assert lastUsedDate >= minLastUsedDateTime;
    assert lastUsedDate <= maxLastUsedDateTime;
  }

  private String issueAccessCode() {
    return issueAccessCode(PERRY_TOKEN);
  }

  private String issueAccessCode(String perryToken) {
    OAuth2AccessToken accessToken = new DefaultOAuth2AccessToken(ACCESS_TOKEN);
    UniversalUserToken universalUserToken = new UniversalUserToken();
    universalUserToken.setUserId(USER_ID);
    universalUserToken.setToken(perryToken);
    return tokenService.issueAccessCode(universalUserToken, accessToken.getValue(), "", "");
  }
}
