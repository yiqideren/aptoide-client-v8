package cm.aptoide.pt.v8engine.view.recycler.displayable.implementations.grid;

import android.content.Context;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import cm.aptoide.pt.model.v7.Type;
import cm.aptoide.pt.model.v7.listapp.App;
import cm.aptoide.pt.model.v7.timeline.StoreLatestApps;
import cm.aptoide.pt.v8engine.R;
import cm.aptoide.pt.v8engine.view.recycler.displayable.Displayable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;

/**
 * Created by marcelobenites on 6/17/16.
 */
@AllArgsConstructor
public class StoreLatestAppsDisplayable extends Displayable {

	@Getter private String storeName;
	@Getter private String avatarUrl;
	@Getter private List<LatestApp> latestApps;

	private DateCalculator dateCalculator;
	private Date date;

	public static StoreLatestAppsDisplayable from(StoreLatestApps storeLatestApps, DateCalculator dateCalculator) {
		final List<LatestApp> latestApps = new ArrayList<>();
		for (App app : storeLatestApps.getApps()) {
			latestApps.add(new LatestApp(app.getId(), app.getIcon()));
		}
		return new StoreLatestAppsDisplayable(storeLatestApps.getStore().getName(), storeLatestApps.getStore().getAvatar(), latestApps, dateCalculator,
				storeLatestApps.getLatestUpdate());
	}

	public StoreLatestAppsDisplayable() {
	}

	public String getTimeSinceLastUpdate(Context context) {
		return dateCalculator.getTimeSinceDate(context, date);
	}

	@Override
	public Type getType() {
		return Type.SOCIAL_TIMELINE;
	}

	@Override
	public int getViewLayout() {
		return R.layout.displayable_social_timeline_store_latest_apps;
	}

	@EqualsAndHashCode
	@AllArgsConstructor
	public static class LatestApp {

		@Getter private final long appId;
		@Getter private final String iconUrl;
	}
}
