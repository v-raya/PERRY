package gov.ca.cwds.config.api.common;

import gov.ca.cwds.config.LoggingRequestIdFilter;
import gov.ca.cwds.config.LoggingUserIdFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.context.request.async.WebAsyncManagerIntegrationFilter;

public abstract class BaseApiConfiguration extends WebSecurityConfigurerAdapter {
  @Autowired
  private SpApiAuthenticationErrorHandler errorHandler;
  @Autowired
  private SpApiAuthenticationProvider authProvider;
  @Autowired
  private SpApiAccessDeniedHandler apiAccessDeniedHandler;
  @Autowired
  private LoggingRequestIdFilter loggingRequestIdFilter;
  @Autowired
  private LoggingUserIdFilter loggingUserIdFilter;

  @Override
  protected void configure(HttpSecurity http) throws Exception {
    http.sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
        .and().addFilterBefore(getFilter(), UsernamePasswordAuthenticationFilter.class)
        .addFilterBefore(loggingRequestIdFilter, WebAsyncManagerIntegrationFilter.class)
        .addFilterAfter(loggingUserIdFilter, UsernamePasswordAuthenticationFilter.class)
        .exceptionHandling().authenticationEntryPoint(errorHandler).accessDeniedHandler(apiAccessDeniedHandler)
            .and().csrf().disable();
  }

  @Override
  protected void configure(AuthenticationManagerBuilder auth) {
    auth.authenticationProvider(authProvider);
  }

  @Bean
  public SpApiSecurityFilter getFilter() throws Exception{
    return new SpApiSecurityFilter(authenticationManager());
  }
}
