import gov.ca.cwds.rest.api.domain.auth.GovernmentEntityType

def authorization = user.authorization

//RACFID USER
if (authorization) {
    def privileges = []
    authorization.authorityPrivilege.findAll {
        it.authPrivilegeCode == "P" && it.endDate == null
    } each {
        privileges.push it.authPrivilegeTypeDesc
    }

    def supervisorAuthorities = ["S", "A", "T", "B"]

    def supervisor = authorization.unitAuthority != null && authorization.unitAuthority.size() > 0 && authorization.unitAuthority.every { a ->
        supervisorAuthorities.any {
            it == a.unitAuthorityCode
        }
    }

    def authorityCodes = []
    authorization.unitAuthority.each {
        authorityCodes.push it.unitAuthorityCode
    }

    def governmentEntityType = GovernmentEntityType.findBySysId(authorization.cwsOffice?.governmentEntityType)

    def token=
            [user           : authorization.userId,
            first_name     : authorization.staffPerson?.firstName,
            last_name      : authorization.staffPerson?.lastName,
            email          : user.parameters["email"],
            roles          : user.roles + [supervisor ? "Supervisor" : "SocialWorker"],
            staffId        : authorization.staffPerson?.id,
            county_name    : governmentEntityType.description,
            county_code    : governmentEntityType.countyCd,
            county_cws_code: governmentEntityType.sysId,
            privileges     : privileges + user.permissions,
            authorityCodes : authorityCodes]

    if (user.roles?.contains("CWS-admin")) {
        token.userName = user.parameters["userName"]
    }

    return token
}
//NON-RACFID USER
else {
    def countyName = user.parameters["custom:county"]
    def cwsCounty = countyName ? GovernmentEntityType.findByDescription(countyName) : null

    def token = [user           : user.userId,
                 roles          : user.roles,
                 first_name     : user.parameters["given_name"],
                 last_name      : user.parameters["family_name"],
                 email          : user.parameters["email"],
                 county_code    : cwsCounty?.countyCd,
                 county_cws_code: cwsCounty?.sysId,
                 county_name    : countyName,
                 privileges     : user.permissions]

    //NON-RACFID CALS USER
    if (user.roles?.contains("CALS-external-worker")) {
        token.privileges += ["CWS Case Management System", "Resource Management"]
    }

    if (user.roles?.contains("CWS-admin")) {
        token.userName = user.parameters["userName"]
    }

    return token
}