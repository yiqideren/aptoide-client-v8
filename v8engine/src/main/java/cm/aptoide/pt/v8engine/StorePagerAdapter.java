/*
 * Copyright (c) 2016.
 * Modified by SithEngineer on 02/08/2016.
 */

package cm.aptoide.pt.v8engine;

import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import cm.aptoide.pt.dataprovider.ws.v7.store.StoreContext;
import cm.aptoide.pt.model.v7.Event;
import cm.aptoide.pt.model.v7.store.GetHome;
import cm.aptoide.pt.model.v7.store.GetStoreTabs;
import cm.aptoide.pt.v8engine.util.Translator;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;

/**
 * Created by neuro on 28-04-2016.
 */
public class StorePagerAdapter extends FragmentStatePagerAdapter {

  private final List<GetStoreTabs.Tab> tabs;
  private final StoreContext storeContext;
  private final EnumMap<Event.Name, Integer> availableEventsMap = new EnumMap<>(Event.Name.class);
  private String storeTheme;
  private long storeId;

  public StorePagerAdapter(FragmentManager fm, GetHome getHome, StoreContext storeContext) {
    super(fm);
    storeId = getHome.getNodes().getMeta().getData().getStore().getId();
    tabs = getHome.getNodes().getTabs().getList();
    this.storeContext = storeContext;
    translateTabs(tabs);
    if (storeId != 15) {
      storeTheme = getHome.getNodes().getMeta().getData().getStore().getAppearance().getTheme();
    }
    validateGetStore();

    fillAvailableEventsMap(getHome);
  }

  private void translateTabs(List<GetStoreTabs.Tab> tabs) {
    for (GetStoreTabs.Tab t : tabs)
      t.setLabel(Translator.translate(t.getLabel()));
  }

  private void validateGetStore() {
    Iterator<GetStoreTabs.Tab> iterator = tabs.iterator();
    while (iterator.hasNext()) {
      GetStoreTabs.Tab next = iterator.next();

      if (next.getEvent().getName() == null || next.getEvent().getType() == null) {
        iterator.remove();
      }
    }
  }

  private void fillAvailableEventsMap(GetHome getHome) {
    List<GetStoreTabs.Tab> list = getHome.getNodes().getTabs().getList();
    for (int i = 0; i < list.size(); i++) {
      Event event = list.get(i).getEvent();

      if (!containsEventName(event.getName())) {
        availableEventsMap.put(event.getName(), i);
      }
    }
  }

  public boolean containsEventName(Event.Name name) {
    return availableEventsMap.containsKey(name);
  }

  @Override public Fragment getItem(int position) {

    GetStoreTabs.Tab tab = tabs.get(position);
    Event event = tab.getEvent();

    switch (event.getType()) {
      case API:
        return caseAPI(tab);
      case CLIENT:
        return caseClient(event, tab);
      case v3:
        return caseV3(event);
      default:
        // Safe to throw exception as the tab should be filtered prior to getting here.
        throw new RuntimeException("Fragment type not implemented!");
    }
  }

  private Fragment caseAPI(GetStoreTabs.Tab tab) {
    Event event = tab.getEvent();
    switch (event.getName()) {
      case getUserTimeline:
        return V8Engine.getFragmentProvider()
            .newAppsTimelineFragment(event.getAction(), storeTheme);
      default:
        return V8Engine.getFragmentProvider()
            .newStoreTabGridRecyclerFragment(event, storeTheme, tab.getTag(), storeContext);
    }
  }

  private Fragment caseClient(Event event, GetStoreTabs.Tab tab) {
    switch (event.getName()) {
      case myUpdates:
        return V8Engine.getFragmentProvider().newUpdatesFragment();
      case myDownloads:
        return V8Engine.getFragmentProvider().newDownloadsFragment();
      case myStores:
        return V8Engine.getFragmentProvider()
            .newSubscribedStoresFragment(event, tab.getLabel(), storeTheme, tab.getTag());
      default:
        // Safe to throw exception as the tab should be filtered prior to getting here.
        throw new RuntimeException("Fragment type not implemented!");
    }
  }

  private Fragment caseV3(Event event) {
    switch (event.getName()) {
      case getReviews:
        return V8Engine.getFragmentProvider().newLatestReviewsFragment(storeId);
      default:
        // Safe to throw exception as the tab should be filtered prior to getting here.
        throw new RuntimeException("Fragment type not implemented!");
    }
  }

  public Event.Name getEventName(int position) {
    return tabs.get(position).getEvent().getName();
  }

  /**
   * Returns the position of an Event, given a name.
   *
   * @param name name of the Event {@link Event.Name}
   * @return returns a positive integer 0...X if there is an Event with requested name, else returns
   * -1.
   */
  public Integer getEventNamePosition(Event.Name name) {
    final Integer integer = availableEventsMap.get(name);
    if (integer == null) {
      return -1;
    }
    return integer;
  }

  @Override public int getCount() {
    return tabs.size();
  }

  @Override public CharSequence getPageTitle(int position) {
    return tabs.get(position).getLabel();
  }
}
