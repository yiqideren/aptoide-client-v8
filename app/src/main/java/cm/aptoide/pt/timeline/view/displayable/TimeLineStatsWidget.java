package cm.aptoide.pt.timeline.view.displayable;

import android.support.annotation.UiThread;
import android.view.View;
import android.widget.Button;
import cm.aptoide.pt.R;
import cm.aptoide.pt.crashreports.CrashReport;
import cm.aptoide.pt.navigator.FragmentNavigator;
import cm.aptoide.pt.view.BaseActivity;
import cm.aptoide.pt.view.recycler.widget.Widget;
import com.jakewharton.rxbinding.view.RxView;
import javax.inject.Inject;
import rx.Observable;

/**
 * Created by trinkes on 15/12/2016.
 */

public class TimeLineStatsWidget extends Widget<TimeLineStatsDisplayable> {

  @Inject FragmentNavigator fragmentNavigator;
  private Button followers;
  private Button following;
  private Button followFriends;
  private View rightSeparator;

  public TimeLineStatsWidget(View itemView) {
    super(itemView);
    ((BaseActivity) getContext()).getActivityComponent()
        .inject(this);
  }

  @UiThread @Override protected void assignViews(View itemView) {
    followers = (Button) itemView.findViewById(R.id.followers);
    following = (Button) itemView.findViewById(R.id.following);
    followFriends = (Button) itemView.findViewById(R.id.follow_friends_button);
    rightSeparator = itemView.findViewById(R.id.rightSeparator);
  }

  @UiThread @Override public void bindView(TimeLineStatsDisplayable displayable) {
    followers.setText(displayable.getFollowersText(getContext()));
    following.setText(displayable.getFollowingText(getContext()));

    Observable<Void> followersClick = RxView.clicks(followers)
        .doOnNext(__ -> displayable.followersClick(fragmentNavigator));

    Observable<Void> followingClick = RxView.clicks(following)
        .doOnNext(__ -> displayable.followingClick(fragmentNavigator));

    Observable<Void> followFriendsClick = RxView.clicks(followFriends)
        .doOnNext(__ -> displayable.followFriendsClick(fragmentNavigator));

    compositeSubscription.add(Observable.merge(followersClick, followingClick, followFriendsClick)
        .doOnError((throwable) -> CrashReport.getInstance()
            .log(throwable))
        .subscribe());

    if (!displayable.isShouldShowAddFriends()) {
      rightSeparator.setVisibility(View.GONE);
      followFriends.setVisibility(View.GONE);
    }
  }
}
