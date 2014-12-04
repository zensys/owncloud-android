/* ownCloud Android client application
 *   Copyright (C) 2012-2014 ownCloud Inc.
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License version 2,
 *   as published by the Free Software Foundation.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package com.owncloud.android.ui.dialog;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.FragmentManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.CookieSyncManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.widget.RelativeLayout;

import com.actionbarsherlock.app.SherlockDialogFragment;
import cz.zensys.owncloud.android.R;
import com.owncloud.android.authentication.SsoWebViewClient;
import com.owncloud.android.authentication.SsoWebViewClient.SsoWebViewClientListener;
import com.owncloud.android.lib.common.OwnCloudClient;
import com.owncloud.android.lib.common.utils.Log_OC;


/**
 * Dialog to show the WebView for SAML Authentication
 * 
 * @author Maria Asensio
 * @author David A. Velasco
 */
public class SamlWebViewDialog extends SherlockDialogFragment {

    public final String SAML_DIALOG_TAG = "SamlWebViewDialog";
    
    private final static String TAG =  SamlWebViewDialog.class.getSimpleName();

    private static final String ARG_INITIAL_URL = "INITIAL_URL";
    private static final String ARG_TARGET_URL = "TARGET_URL";
    
    private WebView mSsoWebView;
    private SsoWebViewClient mWebViewClient;
    
    private String mInitialUrl;
    private String mTargetUrl;
    
    private Handler mHandler;

    private SsoWebViewClientListener mSsoWebViewClientListener;

    /**
     * Public factory method to get dialog instances.
     * 
     * @param handler
     * @param Url           Url to open at WebView
     * @param targetURL     mBaseUrl + AccountUtils.getWebdavPath(mDiscoveredVersion, mCurrentAuthTokenType)
     * @return              New dialog instance, ready to show.
     */
    public static SamlWebViewDialog newInstance(String url, String targetUrl) {
        Log_OC.d(TAG, "New instance");
        SamlWebViewDialog fragment = new SamlWebViewDialog();
        Bundle args = new Bundle();
        args.putString(ARG_INITIAL_URL, url);
        args.putString(ARG_TARGET_URL, targetUrl);
        fragment.setArguments(args);
        return fragment;
    }
    
    
    public SamlWebViewDialog() {
        super();
        Log_OC.d(TAG, "constructor");
    }
    
    
    @Override
    public void onAttach(Activity activity) {
        Log_OC.d(TAG, "onAttach");
        super.onAttach(activity);
        try {
            mSsoWebViewClientListener = (SsoWebViewClientListener) activity;
            mHandler = new Handler();
            mWebViewClient = new SsoWebViewClient(activity, mHandler, mSsoWebViewClientListener);
            
       } catch (ClassCastException e) {
            throw new ClassCastException(activity.toString() + " must implement " + SsoWebViewClientListener.class.getSimpleName());
        }
    }

    
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        Log_OC.d(TAG, "onCreate, savedInstanceState is " + savedInstanceState);
        super.onCreate(savedInstanceState);
        
        setRetainInstance(true);
        
        CookieSyncManager.createInstance(getSherlockActivity().getApplicationContext());

        if (savedInstanceState == null) {
            mInitialUrl = getArguments().getString(ARG_INITIAL_URL);
            mTargetUrl = getArguments().getString(ARG_TARGET_URL);
        } else {
            mInitialUrl = savedInstanceState.getString(ARG_INITIAL_URL);
            mTargetUrl = savedInstanceState.getString(ARG_TARGET_URL);
        }
        
        setStyle(SherlockDialogFragment.STYLE_NO_TITLE, R.style.Theme_ownCloud_Dialog);
    }
    
    @SuppressWarnings("deprecation")
    @SuppressLint("SetJavaScriptEnabled")
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        Log_OC.d(TAG, "onCreateView, savedInsanceState is " + savedInstanceState);
        
        // Inflate layout of the dialog  
        RelativeLayout ssoRootView = (RelativeLayout) inflater.inflate(R.layout.sso_dialog, container, false);  // null parent view because it will go in the dialog layout
        
        if (mSsoWebView == null) {
            // initialize the WebView
            mSsoWebView = new SsoWebView(getSherlockActivity().getApplicationContext());
            mSsoWebView.setFocusable(true);
            mSsoWebView.setFocusableInTouchMode(true);
            mSsoWebView.setClickable(true);
            
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.removeAllCookie();
            mSsoWebView.loadUrl(mInitialUrl);
          
            WebSettings webSettings = mSsoWebView.getSettings();
            webSettings.setJavaScriptEnabled(true);
            webSettings.setBuiltInZoomControls(false);
            webSettings.setLoadWithOverviewMode(false);
            webSettings.setSavePassword(false);
            webSettings.setUserAgentString(OwnCloudClient.USER_AGENT);
            webSettings.setSaveFormData(false);
        }
        
        mWebViewClient.setTargetUrl(mTargetUrl);
        mSsoWebView.setWebViewClient(mWebViewClient);
        
        // add the webview into the layout
        RelativeLayout.LayoutParams layoutParams = new RelativeLayout.LayoutParams(
                RelativeLayout.LayoutParams.WRAP_CONTENT, 
                RelativeLayout.LayoutParams.WRAP_CONTENT
                );
        ssoRootView.addView(mSsoWebView, layoutParams);
        ssoRootView.requestLayout();
        
        return ssoRootView;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        Log_OC.d(TAG, "onSaveInstanceState being CALLED");
        super.onSaveInstanceState(outState);
        
        // save URLs
        outState.putString(ARG_INITIAL_URL, mInitialUrl);
        outState.putString(ARG_TARGET_URL, mTargetUrl);
    }

    @Override
    public void onDestroyView() {
        Log_OC.d(TAG, "onDestroyView");
        
        if ((ViewGroup)mSsoWebView.getParent() != null) {
            ((ViewGroup)mSsoWebView.getParent()).removeView(mSsoWebView);
        }
        
        mSsoWebView.setWebViewClient(null);
        
        // Work around bug: http://code.google.com/p/android/issues/detail?id=17423
        Dialog dialog = getDialog();
        if ((dialog != null)) {
            dialog.setOnDismissListener(null);
            //dialog.dismiss();
            //dialog.setDismissMessage(null);
        }
        
        super.onDestroyView();
    }
    
    @Override
    public void onDestroy() {
        Log_OC.d(TAG, "onDestroy");
        super.onDestroy();
    }

    @Override
    public void onDetach() {
        Log_OC.d(TAG, "onDetach");
        mSsoWebViewClientListener = null;
        mWebViewClient = null;
        super.onDetach();
    }
    
    @Override
    public void onCancel (DialogInterface dialog) {
        Log_OC.d(TAG, "onCancel");
        super.onCancel(dialog);
    }
    
    @Override
    public void onDismiss (DialogInterface dialog) {
        Log_OC.d(TAG, "onDismiss");
        super.onDismiss(dialog);
    }
    
    @Override
    public void onStart() {
        Log_OC.d(TAG, "onStart");
        super.onStart();
    }

    @Override
    public void onStop() {
        Log_OC.d(TAG, "onStop");
        super.onStop();
    }

    @Override
    public void onResume() {
        Log_OC.d(TAG, "onResume");
        super.onResume();
        mSsoWebView.onResume();
    }

    @Override
    public void onPause() {
        Log_OC.d(TAG, "onPause");
        mSsoWebView.onPause();
        super.onPause();
    }
    
    @Override
    public int show (FragmentTransaction transaction, String tag) {
        Log_OC.d(TAG, "show (transaction)");
        return super.show(transaction, tag);
    }

    @Override
    public void show (FragmentManager manager, String tag) {
        Log_OC.d(TAG, "show (manager)");
        super.show(manager, tag);
    }
    
}