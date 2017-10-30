package gov.ca.cwds.service.reissue;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * Created by TPT2 on 10/30/2017.
 */
@Service
@Profile("dev")
public class ReissueLoginServiceDev implements ReissueLoginService {
  @Override
  public String issueAccessCode(String providerId) {
    return null;
  }

  @Override
  public String validate(String perryToken) {
    return null;
  }

  @Override
  public void invalidate(String perryToken) {

  }

  @Override
  public String issueToken(String accessCode) {
    return null;
  }
}
