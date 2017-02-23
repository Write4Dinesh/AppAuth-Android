/*
 * Copyright 2015 The AppAuth for Android Authors. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package net.openid.appauthdemo;

import android.annotation.TargetApi;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.ColorRes;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.customtabs.CustomTabsIntent;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import net.openid.appauth.AppAuthConfiguration;
import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ClientSecretBasic;
import net.openid.appauth.RegistrationRequest;
import net.openid.appauth.RegistrationResponse;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.browser.BrowserBlacklist;
import net.openid.appauth.browser.Browsers;
import net.openid.appauth.browser.VersionRange;
import net.openid.appauth.browser.VersionedBrowserMatcher;

import java.util.Arrays;
import java.util.List;

/**
 * Demonstrates the usage of the AppAuth library to connect to a set of pre-configured
 * OAuth2 providers.
 * <p>
 * <p><em>NOTE</em>: From a clean checkout of this project, no IDPs are automatically configured.
 * Edit {@code res/values/idp_configs.xml} to specify the required configuration properties to
 * enable the IDPs you wish to test. If you wish to add additional IDPs for testing, please see
 * {@link IdentityProvider}.
 */
public class MainActivity extends AppCompatActivity {
    private static final String TAG = "DineshAppAuth";

    private AuthorizationService mAuthService;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.open_id_iv).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                ProgressDialog mProgressDialog = new ProgressDialog(MainActivity.this);
                mProgressDialog.setCancelable(true);
                mProgressDialog.show();
            }
        });
        mAuthService = new AuthorizationService(this);
        initAuthorizationService();
        List<IdentityProvider> IDPList = IdentityProvider.getEnabledProviders(this);
        findViewById(R.id.sign_in_container).setVisibility(
                IDPList.isEmpty() ? View.GONE : View.VISIBLE);
        findViewById(R.id.no_idps_configured).setVisibility(
                IDPList.isEmpty() ? View.VISIBLE : View.GONE);
        getAllIDPs(IDPList);

    }

    private FrameLayout createSignInButtonForIDP(IdentityProvider IDP) {

        FrameLayout IDPSignInButton = new FrameLayout(this);
        IDPSignInButton.setBackgroundResource(IDP.buttonImageRes);
        IDPSignInButton.setContentDescription(getResources().getString(IDP.buttonContentDescriptionRes));
        IDPSignInButton.setLayoutParams(new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        TextView IDPNameTv = new TextView(this);
        IDPNameTv.setText(IDP.name);
        IDPNameTv.setTextColor(getColorCompat(IDP.buttonTextColorRes));
        IDPNameTv.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER));
        IDPSignInButton.addView(IDPNameTv);
        ViewGroup idpButtonContainer = (ViewGroup) findViewById(R.id.idp_button_container);
        idpButtonContainer.addView(IDPSignInButton);
        return IDPSignInButton;
    }

    private void getAllIDPs(List<IdentityProvider> IDPList) {
        for (final IdentityProvider IDP : IDPList) {
            createSignInButtonForIDP(IDP).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Log.d(TAG, "initiating auth for " + IDP.name);
                    //Listener that listens for AuthorizationServiceConfiguration
                    OnRetrieveAuthServiceConfigListener onAuthServiceConfigRetrieved = new OnRetrieveAuthServiceConfigListener(IDP);
                    IDP.retrieveConfig(MainActivity.this, onAuthServiceConfigRetrieved);
                }
            });
        }
    }

    private class OnRetrieveAuthServiceConfigListener implements AuthorizationServiceConfiguration.RetrieveConfigurationCallback {
        private IdentityProvider mCurrentIDP;

        public OnRetrieveAuthServiceConfigListener(IdentityProvider currentIDP) {
            mCurrentIDP = currentIDP;
        }

        @Override
        public void onFetchConfigurationCompleted(@Nullable AuthorizationServiceConfiguration authServiceConfig, @Nullable AuthorizationException noAuthConfigException) {
            if (noAuthConfigException != null) {
                Log.w(TAG, "Failed to retrieve configuration for " + mCurrentIDP.name, noAuthConfigException);
            } else {
                Log.d(TAG, "configuration retrieved for " + mCurrentIDP.name + ", proceeding");
                if (mCurrentIDP.getClientId() == null) {
                    Log.d(TAG, "Client_id not available for this IDP. please register one");
                    // Do dynamic client registration if no client_id
                    makeRegistrationRequest(authServiceConfig, mCurrentIDP);
                } else {
                    makeAuthorizationRequest(authServiceConfig, mCurrentIDP, new AuthState());
                }
            }
        }
    }

    //Blacklist samsnug custom tab browser
    private BrowserBlacklist blockListBrowser() {
        VersionRange versionRange = VersionRange.atMost("4.0.20-56");//this or below
        //VersionRange versionRange = VersionRange.ANY_VERSION;//block all the versions
        BrowserBlacklist blacklist = new BrowserBlacklist(
                new VersionedBrowserMatcher(Browsers.SBrowser.PACKAGE_NAME, Browsers.SBrowser.SIGNATURE_SET, true, // custom tab
                        versionRange));
        return blacklist;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mAuthService != null) {
            mAuthService.dispose();
        }
    }

    private PendingIntent createCancelPendingIntent() {
        Intent intent = new Intent(this, CancelActivity.class);

        return PendingIntent.getActivity(this, 420, intent, 0);
    }

    private void initAuthorizationService() {
        AppAuthConfiguration.Builder builder = new AppAuthConfiguration.Builder();
        builder.setBrowserMatcher(blockListBrowser());
        AppAuthConfiguration appAuthConfiguration = builder.build();
        mAuthService.dispose();
        mAuthService = null;
        mAuthService = new AuthorizationService(this, appAuthConfiguration);
    }

    private void makeAuthorizationRequest(@NonNull AuthorizationServiceConfiguration authServiceConfig, @NonNull IdentityProvider IDP, @NonNull AuthState authState) {
        String loginHint = ((EditText) findViewById(R.id.login_hint_value)).getText().toString().trim();
        if (loginHint.isEmpty()) {
            loginHint = null;
        }
        // Create Authorization Request
        Log.d(TAG, "Creating Authorization Request...");
        AuthorizationRequest.Builder authRequestBuilder = new AuthorizationRequest.Builder(authServiceConfig, IDP.getClientId(), ResponseTypeValues.CODE, IDP.getRedirectUri());
        authRequestBuilder.setScope(IDP.getScope());
        authRequestBuilder.setLoginHint(loginHint);
        AuthorizationRequest authRequest = authRequestBuilder.build();
        Log.d(TAG, "Making auth request to " + authServiceConfig.authorizationEndpoint);

        PendingIntent postAuthPendingIntent = TokenActivity.createPostAuthorizationIntent(this, authRequest, authServiceConfig.discoveryDoc, authState);
        CustomTabsIntent.Builder customTabsIntentBuilder = mAuthService.createCustomTabsIntentBuilder().setToolbarColor(getColorCompat(R.color.colorAccent));
        CustomTabsIntent customTabsIntent = customTabsIntentBuilder.build();
        //TODO: user different overloaded method to pass Cancel pending intent as well
        mAuthService.performAuthorizationRequest(authRequest, postAuthPendingIntent, createCancelPendingIntent(), customTabsIntent);
    }

    //The client id is not available. make registration request
    private void makeRegistrationRequest(
            @NonNull AuthorizationServiceConfiguration serviceConfig,
            @NonNull final IdentityProvider idp) {

        final RegistrationRequest registrationRequest = new RegistrationRequest.Builder(
                serviceConfig,
                Arrays.asList(idp.getRedirectUri()))
                .setTokenEndpointAuthenticationMethod(ClientSecretBasic.NAME)
                .build();

        Log.d(TAG, "Making registration request to " + serviceConfig.registrationEndpoint);
        mAuthService.performRegistrationRequest(
                registrationRequest,
                new AuthorizationService.RegistrationResponseCallback() {
                    @Override
                    public void onRegistrationRequestCompleted(
                            @Nullable RegistrationResponse registrationResponse,
                            @Nullable AuthorizationException ex) {
                        Log.d(TAG, "Registration request complete");
                        if (registrationResponse != null) {
                            idp.setClientId(registrationResponse.clientId);
                            Log.d(TAG, "Registration request complete successfully");
                            // Continue with the authentication
                            makeAuthorizationRequest(registrationResponse.request.configuration, idp,
                                    new AuthState((registrationResponse)));
                        }
                    }
                });
    }


    @TargetApi(Build.VERSION_CODES.M)
    @SuppressWarnings("deprecation")
    private int getColorCompat(@ColorRes int color) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return getColor(color);
        } else {
            return getResources().getColor(color);
        }
    }
}
