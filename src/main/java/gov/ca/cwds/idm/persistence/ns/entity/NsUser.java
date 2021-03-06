package gov.ca.cwds.idm.persistence.ns.entity;

import java.time.LocalDateTime;
import java.util.Objects;
import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;

@Entity
@Table(name = "user")
public class NsUser {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  private Long id;

  @NotNull
  @Size(min = 1, max = 128)
  @Column(name = "username")
  private String username;

  @Column(name = "last_login_time")
  private LocalDateTime lastLoginTime;

  @Column(name = "last_registration_resubmit_time")
  private LocalDateTime lastRegistrationResubmitTime;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUsername() {
    return username;
  }

  public void setUsername(String username) {
    this.username = username;
  }

  public LocalDateTime getLastLoginTime() {
    return lastLoginTime;
  }

  public void setLastLoginTime(LocalDateTime lastLoginTime) {
    this.lastLoginTime = lastLoginTime;
  }

  public LocalDateTime getLastRegistrationResubmitTime() {
    return lastRegistrationResubmitTime;
  }

  public void setLastRegistrationResubmitTime(LocalDateTime lastRegistrationResubmitTime) {
    this.lastRegistrationResubmitTime = lastRegistrationResubmitTime;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof NsUser)) {
      return false;
    }
    NsUser that = (NsUser) o;
    return Objects.equals(getUsername(), that.getUsername());
  }

  @Override
  public int hashCode() {
    return Objects.hash(getUsername());
  }
}
