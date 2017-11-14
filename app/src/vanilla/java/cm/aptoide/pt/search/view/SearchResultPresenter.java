package cm.aptoide.pt.search.view;

import android.support.annotation.NonNull;
import android.util.Pair;
import cm.aptoide.pt.R;
import cm.aptoide.pt.crashreports.CrashReport;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.presenter.Presenter;
import cm.aptoide.pt.presenter.View;
import cm.aptoide.pt.search.SearchAnalytics;
import cm.aptoide.pt.search.SearchCursorAdapter;
import cm.aptoide.pt.search.SearchManager;
import cm.aptoide.pt.search.SearchNavigator;
import cm.aptoide.pt.search.SearchSuggestionManager;
import cm.aptoide.pt.search.model.SearchAdResult;
import cm.aptoide.pt.search.model.SearchAppResult;
import com.jakewharton.rxbinding.support.v7.widget.SearchViewQueryTextEvent;
import com.jakewharton.rxrelay.PublishRelay;
import java.util.List;
import rx.Observable;
import rx.Scheduler;

public class SearchResultPresenter implements Presenter {

  private static final int COMPLETION_THRESHOLD = 3;
  private static final String TAG = SearchResultPresenter.class.getName();
  private final SearchResultView view;
  private final SearchAnalytics analytics;
  private final SearchNavigator navigator;
  private final CrashReport crashReport;
  private final Scheduler viewScheduler;
  private final SearchManager searchManager;
  private final PublishRelay<SearchAdResult> onAdClickRelay;
  private final PublishRelay<SearchAppResult> onItemViewClickRelay;
  private final PublishRelay<Pair<SearchAppResult, android.view.View>> onOpenPopupMenuClickRelay;
  private final String defaultStoreName;
  private final String defaultThemeName;
  private final boolean isMultiStoreSearch;
  private final TrendingManager trendingManager;
  private final SearchCursorAdapter searchCursorAdapter;
  private final SearchSuggestionManager searchSuggestionManager;

  public SearchResultPresenter(SearchResultView view, SearchAnalytics analytics,
      SearchNavigator navigator, CrashReport crashReport, Scheduler viewScheduler,
      SearchManager searchManager, PublishRelay<SearchAdResult> onAdClickRelay,
      PublishRelay<SearchAppResult> onItemViewClickRelay,
      PublishRelay<Pair<SearchAppResult, android.view.View>> onOpenPopupMenuClickRelay,
      boolean isMultiStoreSearch, String defaultStoreName, String defaultThemeName, SearchCursorAdapter searchCursorAdapter,
      SearchSuggestionManager searchSuggestionManager, TrendingManager trendingManager) {
    this.view = view;
    this.analytics = analytics;
    this.navigator = navigator;
    this.crashReport = crashReport;
    this.viewScheduler = viewScheduler;
    this.searchManager = searchManager;
    this.onAdClickRelay = onAdClickRelay;
    this.onItemViewClickRelay = onItemViewClickRelay;
    this.onOpenPopupMenuClickRelay = onOpenPopupMenuClickRelay;
    this.isMultiStoreSearch = isMultiStoreSearch;
    this.defaultStoreName = defaultStoreName;
    this.defaultThemeName = defaultThemeName;
    this.searchCursorAdapter = searchCursorAdapter;
    this.searchSuggestionManager = searchSuggestionManager;
    this.trendingManager = trendingManager;

  }

  @Override public void present() {
    stopLoadingMoreOnDestroy();
    firstSearchDataLoad();
    firstAdsDataLoad();
    handleClickFollowedStoresSearchButton();
    handleClickEverywhereSearchButton();
    handleClickToOpenAppViewFromItem();
    handleClickToOpenAppViewFromAdd();
    handleClickToOpenPopupMenu();
    handleClickOnNoResultsImage();
    handleAllStoresListReachedBottom();
    handleFollowedStoresListReachedBottom();
    handleTitleBarClick();
    restoreSelectedTab();
    handleWidgetTrendingRequest();
    listenForSuggestions();
    handleQueryTextChanged();
  }

  private void restoreSelectedTab() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .first()
        .toSingle()
        .map(__ -> view.getViewModel())
        .subscribe(viewModel -> {
          if (viewModel.getAllStoresOffset() > 0 && viewModel.getFollowedStoresOffset() > 0) {
            if (viewModel.isAllStoresSelected()) {
              view.showAllStoresResult();
            } else {
              view.showFollowedStoresResult();
            }
          }
        }, e -> crashReport.log(e));
  }

  private void handleTitleBarClick() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .observeOn(viewScheduler)
        .flatMap(__ -> view.clickTitleBar())
        .doOnNext(__ -> view.focusInSearchBar())
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private void handleWidgetTrendingRequest() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .flatMap(__ -> view.onQueryTextChanged()
          .filter(data -> data == null || data.queryText().length()==0)
          .first()
        )
        .map(__ -> view.getViewModel())
        .filter(viewModel -> viewModel.getCurrentQuery()==null||viewModel.getCurrentQuery().isEmpty())
        .observeOn(viewScheduler)
        .doOnNext(__ -> view.hideLists())
        .flatMap(__ -> trendingManager.getTrendingSuggestions())
        .observeOn(viewScheduler)
        .doOnNext(__ -> view.focusInSearchBar())
        .doOnNext(data -> view.setTrending(data))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private void listenForSuggestions(){
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .flatMap(__ -> searchSuggestionManager.listenForSuggestions()
        .retry(3))
        .filter(data -> data != null && data.size() > 0)
        .observeOn(viewScheduler)
        .doOnNext(data -> searchCursorAdapter.setData(data))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private void handleQueryTextChanged(){
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .flatMap(__ -> view.onQueryTextChanged())
        .filter(data -> data!=null)
        .filter(data -> data.queryText().length()>0)
        .flatMap(data -> {
          final String query = data.queryText()
              .toString();
          if(query.length()<COMPLETION_THRESHOLD){
            return trendingManager.getTrendingSuggestions()
                .observeOn(viewScheduler)
                .doOnNext(trendingList -> view.setTrending(trendingList));
          }
          return handleQueryEvent(data);
        })
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {}, e -> crashReport.log(e));
  }

  private Observable<Void> handleQueryEvent(SearchViewQueryTextEvent event){
    return Observable.fromCallable(()->{
      final String query = event.queryText()
          .toString();

      if (event.isSubmitted()) {
        Logger.v(TAG,"Searching for: "+query);
        return null;
      }

      if (query.length() >= COMPLETION_THRESHOLD) {
        searchSuggestionManager.getSuggestionsFor(query);
      }
      return null;
    });

  }

  private void stopLoadingMoreOnDestroy() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.DESTROY))
        .first()
        .toSingle()
        .observeOn(viewScheduler)
        .subscribe(__ -> view.hideLoadingMore(), e -> crashReport.log(e));
  }

  private void handleAllStoresListReachedBottom() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .observeOn(viewScheduler)
        .flatMap(__ -> view.allStoresResultReachedBottom())
        .map(__ -> view.getViewModel())
        .filter(viewModel -> !viewModel.hasReachedBottomOfAllStores())
        .observeOn(viewScheduler)
        .doOnNext(__ -> view.showLoadingMore())
        .flatMap(viewModel -> loadDataForAllNonFollowedStores(viewModel.getCurrentQuery(),
            viewModel.isOnlyTrustedApps(), viewModel.getAllStoresOffset()).onErrorResumeNext(
            err -> {
              crashReport.log(err);
              return Observable.just(null);
            }))
        .observeOn(viewScheduler)
        .doOnNext(__ -> view.hideLoadingMore())
        .filter(data -> data != null)
        .doOnNext(data -> {
          final SearchResultView.Model viewModel = view.getViewModel();
          viewModel.incrementOffsetAndCheckIfReachedBottomOfFollowedStores(getItemCount(data));
        })
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private int getItemCount(List<SearchAppResult> data) {
    return data != null ? data.size() : 0;
  }

  private void handleFollowedStoresListReachedBottom() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .observeOn(viewScheduler)
        .flatMap(__ -> view.followedStoresResultReachedBottom())
        .map(__ -> view.getViewModel())
        .filter(viewModel -> !viewModel.hasReachedBottomOfFollowedStores())
        .observeOn(viewScheduler)
        .doOnNext(__ -> view.showLoadingMore())
        .flatMap(viewModel -> loadDataForAllFollowedStores(viewModel.getCurrentQuery(),
            viewModel.isOnlyTrustedApps(), viewModel.getFollowedStoresOffset()).onErrorResumeNext(
            err -> {
              crashReport.log(err);
              return Observable.just(null);
            }))
        .observeOn(viewScheduler)
        .doOnNext(__ -> view.hideLoadingMore())
        .filter(data -> data != null)
        .doOnNext(data -> {
          final SearchResultView.Model viewModel = view.getViewModel();
          viewModel.incrementOffsetAndCheckIfReachedBottomOfFollowedStores(getItemCount(data));
        })
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private void firstAdsDataLoad() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .map(__ -> view.getViewModel())
        .filter(viewModel -> !viewModel.hasLoadedAds())
        .flatMap(viewModel -> searchManager.getAdsForQuery(viewModel.getCurrentQuery())
            .onErrorReturn(err -> {
              crashReport.log(err);
              return null;
            })
            .observeOn(viewScheduler)
            .doOnNext(__ -> viewModel.setHasLoadedAds())
            .doOnNext(ad -> {
              if (ad == null) {
                view.setFollowedStoresAdsEmpty();
                view.setAllStoresAdsEmpty();
              } else {
                view.setAllStoresAdsResult(ad);
                view.setFollowedStoresAdsResult(ad);
              }
            }))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private void handleClickToOpenAppViewFromItem() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .observeOn(viewScheduler)
        .flatMap(__ -> onItemViewClickRelay)
        .doOnNext(data -> openAppView(data))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private void handleClickToOpenAppViewFromAdd() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .observeOn(viewScheduler)
        .flatMap(__ -> onAdClickRelay)
        .doOnNext(data -> {
          analytics.searchAppClick(view.getViewModel()
              .getCurrentQuery(), data.getPackageName());
          navigator.goToAppView(data);
        })
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private void handleClickToOpenPopupMenu() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .observeOn(viewScheduler)
        .flatMap(__ -> onOpenPopupMenuClickRelay)
        .flatMap(pair -> {
          final SearchAppResult data = pair.first;
          final boolean hasVersions = data.hasOtherVersions();
          final String appName = data.getAppName();
          final String appIcon = data.getIcon();
          final String packageName = data.getPackageName();
          final String storeName = data.getStoreName();

          return view.showPopup(hasVersions, pair.second)
              .doOnNext(optionId -> {
                if (optionId == R.id.versions) {
                  if (isMultiStoreSearch) {
                    navigator.goToOtherVersions(appName, appIcon, packageName);
                  } else {
                    navigator.goToOtherVersions(appName, appIcon, packageName, defaultStoreName);
                  }
                } else if (optionId == R.id.go_to_store) {
                  navigator.goToStoreFragment(storeName, defaultThemeName);
                }
              });
        })
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private void handleClickOnNoResultsImage() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .observeOn(viewScheduler)
        .flatMap(__ -> view.clickNoResultsSearchButton())
        .filter(query -> query.length() > 1)
        .doOnNext(query -> navigator.goToSearchFragment(query, view.getViewModel()
            .getStoreName()))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private void openAppView(SearchAppResult searchApp) {
    final String packageName = searchApp.getPackageName();
    final long appId = searchApp.getAppId();
    final String storeName = searchApp.getStoreName();
    analytics.searchAppClick(view.getViewModel()
        .getCurrentQuery(), packageName);
    navigator.goToAppView(appId, packageName, defaultThemeName, storeName);
  }

  private void handleClickFollowedStoresSearchButton() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .observeOn(viewScheduler)
        .flatMap(__ -> view.clickFollowedStoresSearchButton())
        .doOnNext(__ -> view.showFollowedStoresResult())
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private void handleClickEverywhereSearchButton() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .observeOn(viewScheduler)
        .flatMap(__ -> view.clickEverywhereSearchButton())
        .doOnNext(__ -> view.showAllStoresResult())
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }

  private Observable<List<SearchAppResult>> loadData(String query, String storeName,
      boolean onlyTrustedApps, int offset) {

    if (storeName != null && !storeName.trim()
        .equals("")) {
      return Observable.fromCallable(() -> {
        view.setViewWithStoreNameAsSingleTab(storeName);
        return null;
      })
          .flatMap(__ -> loadDataForSpecificStore(query, storeName, offset));
    }
    // search every store. followed and not followed
    return Observable.merge(loadDataForAllFollowedStores(query, onlyTrustedApps, offset),
        loadDataForAllNonFollowedStores(query, onlyTrustedApps, offset));
  }

  @NonNull private Observable<List<SearchAppResult>> loadDataForAllNonFollowedStores(String query,
      boolean onlyTrustedApps, int offset) {
    return searchManager.searchInNonFollowedStores(query, onlyTrustedApps, offset)
        .observeOn(viewScheduler)
        .doOnNext(view::addAllStoresResult)
        .doOnNext(data -> {
          final SearchResultView.Model viewModel = view.getViewModel();
          viewModel.incrementOffsetAndCheckIfReachedBottomOfAllStores(getItemCount(data));
        });
  }

  @NonNull private Observable<List<SearchAppResult>> loadDataForAllFollowedStores(String query,
      boolean onlyTrustedApps, int offset) {
    return searchManager.searchInFollowedStores(query, onlyTrustedApps, offset)
        .observeOn(viewScheduler)
        .doOnNext(view::addFollowedStoresResult)
        .doOnNext(data -> {
          final SearchResultView.Model viewModel = view.getViewModel();
          viewModel.incrementOffsetAndCheckIfReachedBottomOfFollowedStores(getItemCount(data));
        });
  }

  @NonNull
  private Observable<List<SearchAppResult>> loadDataForSpecificStore(String query, String storeName,
      int offset) {
    return searchManager.searchInStore(query, storeName, offset)
        .observeOn(viewScheduler)
        .doOnNext(view::addFollowedStoresResult)
        .doOnNext(data -> {
          final SearchResultView.Model viewModel = view.getViewModel();
          viewModel.setAllStoresSelected(false);
          viewModel.incrementOffsetAndCheckIfReachedBottomOfFollowedStores(getItemCount(data));
        });
  }

  private void firstSearchDataLoad() {
    view.getLifecycle()
        .filter(event -> event.equals(View.LifecycleEvent.CREATE))
        .map(__ -> view.getViewModel())
        .filter(viewModel -> viewModel.getAllStoresOffset() == 0
            && viewModel.getFollowedStoresOffset() == 0)
        .filter(viewModel -> viewModel.getCurrentQuery() != null && !viewModel.getCurrentQuery().isEmpty())
        .observeOn(viewScheduler)
        .doOnNext(__ -> view.showLoading())
        .doOnNext(viewModel -> analytics.search(viewModel.getCurrentQuery()))
        .flatMap(viewModel -> loadData(viewModel.getCurrentQuery(), viewModel.getStoreName(),
            viewModel.isOnlyTrustedApps(), 0).onErrorResumeNext(err -> {
          crashReport.log(err);
          return Observable.just(null);
        })
            .observeOn(viewScheduler)
            .doOnNext(__2 -> view.hideLoading())
            .doOnNext(data -> {
              if (data == null || getItemCount(data) == 0) {
                view.showNoResultsView();
                analytics.searchNoResults(viewModel.getCurrentQuery());
              } else {
                view.showResultsView();
                if (viewModel.isAllStoresSelected()) {
                  view.showAllStoresResult();
                } else {
                  view.showFollowedStoresResult();
                }
              }
            }))
        .compose(view.bindUntilEvent(View.LifecycleEvent.DESTROY))
        .subscribe(__ -> {
        }, e -> crashReport.log(e));
  }


}
