package gov.ca.cwds.idm.service.cognito.util;

import static gov.ca.cwds.idm.service.cognito.attribute.CustomUserAttribute.PERMISSIONS;
import static gov.ca.cwds.idm.service.cognito.attribute.CustomUserAttribute.RACFID_CUSTOM;
import static gov.ca.cwds.idm.service.cognito.attribute.StandardUserAttribute.EMAIL;
import static gov.ca.cwds.idm.service.cognito.attribute.StandardUserAttribute.EMAIL_VERIFIED;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUtils.attribute;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUtils.buildEmailAttributes;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUtils.createPermissionsAttribute;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUtils.getAttribute;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUtils.getCustomDelimitedListAttributeValue;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUtils.getPermissions;
import static gov.ca.cwds.idm.service.cognito.util.CognitoUtils.getRACFId;
import static org.hamcrest.CoreMatchers.hasItem;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import com.amazonaws.services.cognitoidp.model.AttributeType;
import com.amazonaws.services.cognitoidp.model.UserType;
import gov.ca.cwds.idm.service.cognito.attribute.UserAttribute;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.Test;

public class CognitoUtilsTest {

  @Test
  public void testGetAttributeNoAttributes() {
    UserType cognitoUser = new UserType();
    Optional<AttributeType> attrOpt = getAttribute(cognitoUser, "someName");
    assertThat(attrOpt.isPresent(), is(false));
  }

  @Test
  public void testGetAttributeEmptyAttributes() {
    UserType cognitoUser = new UserType();
    cognitoUser.withAttributes();
    Optional<AttributeType> attrOpt = getAttribute(cognitoUser, "someName");
    assertThat(attrOpt.isPresent(), is(false));
  }

  @Test
  public void testGetAttributeOtherAttribute() {
    UserType cognitoUser = new UserType();

    AttributeType otherAttr = new AttributeType();
    otherAttr.setName("otherName");
    otherAttr.setValue("otherValue");

    cognitoUser.withAttributes(otherAttr);

    Optional<AttributeType> attrOpt = getAttribute(cognitoUser, "someName");
    assertThat(attrOpt.isPresent(), is(false));
  }

  @Test
  public void testGetAttributeValidAttribute() {
    UserType cognitoUser = new UserType();

    AttributeType validAttr = new AttributeType();
    validAttr.setName("someName");
    validAttr.setValue("someValue");

    AttributeType otherAttr = new AttributeType();
    otherAttr.setName("otherName");
    otherAttr.setValue("otherValue");

    cognitoUser.withAttributes(validAttr, otherAttr);

    Optional<AttributeType> attrOpt = getAttribute(cognitoUser, "someName");
    assertTrue(attrOpt.isPresent());
    AttributeType attr = attrOpt.get();
    assertThat(attr.getName(), is("someName"));
    assertThat(attr.getValue(), is("someValue"));

    Optional<AttributeType> attrOptUpCase = getAttribute(cognitoUser, "SOMENAME");
    assertTrue(attrOptUpCase.isPresent());
    AttributeType attrUpCase = attrOptUpCase.get();
    assertThat(attrUpCase.getName(), is("someName"));
    assertThat(attrUpCase.getValue(), is("someValue"));
  }

  @Test
  public void testGetNoPermissions() {

    UserType cognitoUser = new UserType();

    AttributeType otherAttr = new AttributeType();
    otherAttr.setName("otherName");
    otherAttr.setValue("otherValue");

    cognitoUser.withAttributes(otherAttr);

    assertThat(getPermissions(cognitoUser), empty());
  }

  @Test
  public void testGetNullPermissions() {

    UserType cognitoUser = new UserType();

    AttributeType permissionsAttr = new AttributeType();
    permissionsAttr.setName(PERMISSIONS.getName());
    permissionsAttr.setValue(null);

    cognitoUser.withAttributes(permissionsAttr);

    assertThat(getPermissions(cognitoUser), empty());
  }

  @Test
  public void testGetEmptyPermissions() {

    UserType cognitoUser = new UserType();

    AttributeType permissionsAttr = new AttributeType();
    permissionsAttr.setName(PERMISSIONS.getName());
    permissionsAttr.setValue("");

    cognitoUser.withAttributes(permissionsAttr);

    assertThat(getPermissions(cognitoUser), empty());
  }

  @Test
  public void testGetPermissions() {
    UserType cognitoUser = new UserType();

    AttributeType permissionsAttr = new AttributeType();
    permissionsAttr.setName(PERMISSIONS.getName());
    permissionsAttr.setValue("Snapshot-rollout:Hotline-rollout");

    cognitoUser.withAttributes(permissionsAttr);

    Set<String> permissions = getPermissions(cognitoUser);
    assertThat(permissions, hasSize(2));
    assertThat(permissions, hasItem("Snapshot-rollout"));
    assertThat(permissions, hasItem("Hotline-rollout"));
  }

  @Test
  public void testGetPermissionsAttributeValueNull() {
    assertThat(getCustomDelimitedListAttributeValue(null), is(""));
  }

  @Test
  public void testGetPermissionsAttributeValueEmpty() {
    Set<String> permissions = new HashSet<>();
    assertThat(getCustomDelimitedListAttributeValue(permissions), is(""));
  }

  @Test
  public void testGetPermissionsAttributeValue() {
    Set<String> permissions = new HashSet<>();
    permissions.add("one");
    permissions.add("two");
    assertThat(getCustomDelimitedListAttributeValue(permissions), is("one:two"));
  }

  @Test
  public void testCreatePermissionsAttribute() {
    Set<String> permissions = new HashSet<>();
    permissions.add("one");
    permissions.add("two");
    AttributeType attr = createPermissionsAttribute(permissions);
    assertThat(attr.getName(), is(PERMISSIONS.getName()));
    assertThat(attr.getValue(), is("one:two"));
  }


  @Test
  public void testAttribute() {
    AttributeType attr = attribute("attrName", "attrValue");
    assertThat(attr, is(notNullValue()));
    assertThat(attr.getName(), is("attrName"));
    assertThat(attr.getValue(), is("attrValue"));
  }

  @Test
  public void testGetRACFId() {
    UserType cognitoUser = new UserType();

    AttributeType attr = new AttributeType();
    attr.setName(RACFID_CUSTOM.getName());
    attr.setValue("YOLOD");
    cognitoUser.withAttributes(attr);

    assertThat(getRACFId(cognitoUser), is("YOLOD"));
  }

  @Test
  public void testGetRACFIdNoRACFIdAttr() {
    UserType cognitoUser = new UserType();
    assertThat(getRACFId(cognitoUser), is(nullValue()));
  }

  @Test
  public void testBuildEmailAttributesNullEmail() {
    Map<UserAttribute, AttributeType> attrMap = buildEmailAttributes(null);
    assertThat(attrMap, is(notNullValue()));
    assertThat(attrMap.size(), is(0));
  }

  @Test
  public void testBuildEmailAttributes() {
    final String NEW_EMAIL = "new@e.mail";
    Map<UserAttribute, AttributeType> attrMap = buildEmailAttributes(NEW_EMAIL);
    assertEmailAttributes(attrMap, NEW_EMAIL);
  }

  @Test
  public void testBuildEmailAttributesEmptyEmail() {
    Map<UserAttribute, AttributeType> attrMap = buildEmailAttributes("");
    assertEmailAttributes(attrMap, "");
  }

  private void assertEmailAttributes(Map<UserAttribute, AttributeType> attrMap, String email) {
    assertThat(attrMap, is(notNullValue()));
    assertThat(attrMap.size(), is(2));

    assertThat(attrMap.get(EMAIL), is(notNullValue()));
    AttributeType emailAttr = attrMap.get(EMAIL);
    assertThat(emailAttr.getName(), is(EMAIL.getName()));
    assertThat(emailAttr.getValue(), is(email));

    assertThat(attrMap.get(EMAIL_VERIFIED), is(notNullValue()));
    AttributeType emailVerifiedAttr = attrMap.get(EMAIL_VERIFIED);
    assertThat(emailVerifiedAttr.getName(), is(EMAIL_VERIFIED.getName()));
    assertThat(emailVerifiedAttr.getValue(), is("True"));
  }
}