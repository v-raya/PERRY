import gov.ca.cwds.rest.api.domain.auth.GovernmentEntityType
import gov.ca.cwds.idm.service.cognito.util.CognitoPhoneConverter
import org.apache.commons.lang3.StringUtils

def attribute = {name -> cognitoUser.attributes?.find {it.name.equalsIgnoreCase(name)}?.value}

result.id = cognitoUser.username
result.enabled = cognitoUser.enabled
result.userCreateDate = cognitoUser.userCreateDate
result.userLastModifiedDate = cognitoUser.userLastModifiedDate
result.status = cognitoUser.userStatus
result.email = attribute("email")
result.racfid = attribute("custom:RACFID")
result.phoneNumber = CognitoPhoneConverter.fromCognitoFormat(attribute("phone_number"))
result.phoneExtensionNumber = attribute("custom:PhoneExtension")

if(StringUtils.isNotBlank(attribute("custom:Permission"))) {
    result.permissions = attribute("custom:Permission").split('\\s*:\\s*') as HashSet
}

if(StringUtils.isNotBlank(attribute("custom:Role"))) {
    result.roles = attribute("custom:Role").split('\\s*:\\s*') as HashSet
}

if(cwsUser) {

    def governmentEntityType = GovernmentEntityType.findBySysId(cwsUser.cwsOffice?.governmentEntityType)

    result.startDate = cwsUser.staffPerson?.startDate
    result.endDate = cwsUser.staffPerson?.endDate
    result.countyName = governmentEntityType?.description
    result.firstName = cwsUser.staffPerson?.firstName
    result.lastName = cwsUser.staffPerson?.lastName
    result.officeId = cwsUser.cwsOffice?.officeId
    result.officePhoneNumber = cwsUser.cwsOffice?.primaryPhoneNumber
    result.officePhoneExtensionNumber = cwsUser.cwsOffice?.primaryPhoneExtensionNumber
} else {
    result.countyName = attribute("custom:County")
    result.firstName = attribute("given_name")
    result.lastName = attribute("family_name")
    result.officeId = attribute("custom:Office")
}


