package gov.ca.cwds.config;

import gov.ca.cwds.service.OauthLogoutHandler;
import gov.ca.cwds.web.PerryLogoutSuccessHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.oauth2.client.EnableOAuth2Sso;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

/**
 * Created by dmitry.rudenko on 5/23/2017.
 */
@Profile("oauth2")
@EnableOAuth2Sso
@Configuration
public class OAuthConfiguration extends WebSecurityConfigurerAdapter {

  @Autowired
  private LoginServiceValidatorFilter loginServiceValidatorFilter;
  @Autowired
  private OauthLogoutHandler tokenRevocationLogoutHandler;

  @Autowired
  private PerryLogoutSuccessHandler logoutSuccessHandler;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    // /authn/validate should be for backend only!
    http.authorizeRequests()
        .antMatchers("/authn/login").authenticated()
        .antMatchers("/**").permitAll()
        .and()
        .logout()
        .logoutUrl("/authn/logout").permitAll()
        .addLogoutHandler(tokenRevocationLogoutHandler)
        .logoutSuccessHandler(logoutSuccessHandler)
        .and()
        .addFilterBefore(loginServiceValidatorFilter,
            AbstractPreAuthenticatedProcessingFilter.class)
        .csrf().disable();

    super.configure(http);
  }
}
