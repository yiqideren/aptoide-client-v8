/*
 * Copyright (c) 2017.
 * Modified by Marcelo Benites on 15/02/2017.
 */

package cm.aptoide.pt.v8engine.view;

import rx.Observable;

/**
 * Created by marcelobenites on 15/02/17.
 */

public interface WebAuthorizationView extends View {

  void showLoading();

  void hideLoading();

  void showUrl(String url, String redirectUrl);

  Observable<Void> redirect();

  Observable<Void> urlLoad();

  void dismiss();

  void showErrorAndDismiss();
}
