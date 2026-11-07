<#-- Trang khách hàng duyệt consent của Open API. Dữ liệu: vobsConsent (io.vobs.keycloak.ConsentPage). -->
<#import "template.ftl" as layout>
<#import "buttons.ftl" as buttons>
<@layout.registrationLayout displayMessage=true; section>
    <#if section = "header">
        ${msg("vobsConsentTitle")}
    <#elseif section = "form">
        <div id="vobs-consent" class="content-area">
            <p>${msg("vobsConsentIntro", vobsConsent.clientName)}</p>
            <ul id="vobs-consent-scopes" class="${properties.kcListClass!}">
                <#list vobsConsent.scopes as scope>
                    <li data-scope="${scope}">${msg("vobsScope." + scope)}</li>
                </#list>
            </ul>
            <p>${msg("vobsConsentExpires", vobsConsent.expiresOn)}</p>
            <form id="vobs-consent-form" class="${properties.kcFormClass!}" action="${url.loginAction}" method="post">
                <#if vobsConsent.accounts?has_content>
                    <fieldset class="${properties.kcFormGroupClass!}">
                        <legend class="${properties.kcFormLabelClass!}">${msg("vobsConsentAccounts")}</legend>
                        <#list vobsConsent.accounts as account>
                            <div class="${properties.kcCheckboxClass!}">
                                <label for="account-${account?index}" class="${properties.kcCheckboxClass!}">
                                    <input class="${properties.kcCheckboxInputClass!}" type="checkbox"
                                           id="account-${account?index}" name="accountId" value="${account.id}">
                                    <span class="${properties.kcCheckboxLabelClass!}">${account.label} (${account.id})</span>
                                </label>
                            </div>
                        </#list>
                    </fieldset>
                <#elseif vobsConsent.accountsRequired>
                    <p>${msg("vobsConsentNoAccounts")}</p>
                </#if>
                <@buttons.actionGroup horizontal=true>
                    <@buttons.button id="vobs-approve" name="approve" label="vobsConsentApprove"/>
                    <@buttons.button id="vobs-reject" name="reject" label="vobsConsentReject" type="secondary"/>
                </@buttons.actionGroup>
            </form>
        </div>
    </#if>
</@layout.registrationLayout>
