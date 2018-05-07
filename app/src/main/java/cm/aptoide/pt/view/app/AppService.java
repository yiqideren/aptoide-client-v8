package cm.aptoide.pt.view.app;

import android.content.SharedPreferences;
import android.support.annotation.NonNull;
import cm.aptoide.pt.dataprovider.exception.NoNetworkConnectionException;
import cm.aptoide.pt.dataprovider.interfaces.TokenInvalidator;
import cm.aptoide.pt.dataprovider.model.v7.GetApp;
import cm.aptoide.pt.dataprovider.model.v7.GetAppMeta;
import cm.aptoide.pt.dataprovider.model.v7.ListApps;
import cm.aptoide.pt.dataprovider.model.v7.listapp.App;
import cm.aptoide.pt.dataprovider.ws.BodyInterceptor;
import cm.aptoide.pt.dataprovider.ws.v7.BaseBody;
import cm.aptoide.pt.dataprovider.ws.v7.GetAppRequest;
import cm.aptoide.pt.dataprovider.ws.v7.ListAppsRequest;
import cm.aptoide.pt.store.StoreCredentialsProvider;
import java.util.ArrayList;
import java.util.List;
import okhttp3.OkHttpClient;
import retrofit2.Converter;
import rx.Observable;
import rx.Single;
import rx.exceptions.OnErrorNotImplementedException;

/**
 * Created by trinkes on 18/10/2017.
 */

public class AppService {
  private final StoreCredentialsProvider storeCredentialsProvider;
  private final BodyInterceptor<BaseBody> bodyInterceptor;
  private final OkHttpClient httpClient;
  private final Converter.Factory converterFactory;
  private final TokenInvalidator tokenInvalidator;
  private final SharedPreferences sharedPreferences;
  private boolean loading;

  public AppService(StoreCredentialsProvider storeCredentialsProvider,
      BodyInterceptor<BaseBody> bodyInterceptor, OkHttpClient httpClient,
      Converter.Factory converterFactory, TokenInvalidator tokenInvalidator,
      SharedPreferences sharedPreferences) {
    this.storeCredentialsProvider = storeCredentialsProvider;
    this.bodyInterceptor = bodyInterceptor;
    this.httpClient = httpClient;
    this.converterFactory = converterFactory;
    this.tokenInvalidator = tokenInvalidator;
    this.sharedPreferences = sharedPreferences;
  }

  private Single<AppsList> loadApps(long storeId, boolean bypassCache, int offset, int limit,
      boolean bypassServerCache) {
    if (loading) {
      return Single.just(new AppsList(true));
    }
    ListAppsRequest.Body body =
        new ListAppsRequest.Body(storeCredentialsProvider.get(storeId), limit, sharedPreferences);
    body.setOffset(offset);
    body.setStoreId(storeId);
    return new ListAppsRequest(body, bodyInterceptor, httpClient, converterFactory,
        tokenInvalidator, sharedPreferences).observe(bypassCache, bypassServerCache)
        .doOnSubscribe(() -> loading = true)
        .doOnUnsubscribe(() -> loading = false)
        .doOnTerminate(() -> loading = false)
        .flatMap(listApps -> mapListApps(listApps))
        .toSingle()
        .onErrorReturn(throwable -> createErrorAppsList(throwable));
  }

  private Observable<AppsList> mapListApps(ListApps listApps) {
    if (listApps.isOk()) {
      List<Application> list = new ArrayList<>();
      for (App app : listApps.getDataList()
          .getList()) {
        list.add(new Application(app.getName(), app.getIcon(), app.getStats()
            .getRating()
            .getAvg(), app.getStats()
            .getDownloads(), app.getPackageName(), app.getId(), ""));
      }
      return Observable.just(new AppsList(list, false, listApps.getDataList()
          .getNext()));
    } else {
      return Observable.error(new IllegalStateException("Could not obtain timeline from server."));
    }
  }

  public Single<AppsList> loadFreshApps(long storeId, int limit) {
    return loadApps(storeId, true, 0, limit, false);
  }

  public Single<AppsList> loadApps(long storeId, int offset, int limit) {
    return loadApps(storeId, false, offset, limit, false);
  }

  @NonNull private AppsList createErrorAppsList(Throwable throwable) {
    if (throwable instanceof NoNetworkConnectionException) {
      return new AppsList(AppsList.Error.NETWORK);
    } else {
      return new AppsList(AppsList.Error.GENERIC);
    }
  }

  public Single<DetailedApp> loadDetailedApp(long appId, String packageName) {
    return GetAppRequest.of(packageName, bodyInterceptor, appId, httpClient, converterFactory,
        tokenInvalidator, sharedPreferences)
        .observe(true, false)
        .doOnSubscribe(() -> loading = true)
        .doOnUnsubscribe(() -> loading = false)
        .doOnTerminate(() -> loading = false)
        .flatMap(getApp -> mapAppToDetailedApp(getApp))
        .toSingle()
        .onErrorReturn(throwable -> {
          throw new OnErrorNotImplementedException(throwable);
        });
  }

  private Observable<DetailedApp> mapAppToDetailedApp(GetApp getApp) {
    GetAppMeta.App app = getApp.getNodes()
        .getMeta()
        .getData();
    DetailedApp detailedApp =
        new DetailedApp(app.getId(), app.getName(), app.getPackageName(), app.getSize(),
            app.getIcon(), app.getGraphic(), app.getAdded(), app.getModified(), app.getDeveloper(),
            app.getStore(), app.getMedia(), app.getStats(), app.getObb(), app.getPay());
    return Observable.just(detailedApp);
  }
}
