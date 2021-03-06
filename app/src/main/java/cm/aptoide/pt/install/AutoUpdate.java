package cm.aptoide.pt.install;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.os.AsyncTask;
import android.os.Build;
import android.support.annotation.DrawableRes;
import cm.aptoide.analytics.AnalyticsManager;
import cm.aptoide.pt.AptoideApplication;
import cm.aptoide.pt.R;
import cm.aptoide.pt.actions.PermissionManager;
import cm.aptoide.pt.crashreports.CrashReport;
import cm.aptoide.pt.download.DownloadAnalytics;
import cm.aptoide.pt.download.DownloadFactory;
import cm.aptoide.pt.logger.Logger;
import cm.aptoide.pt.utils.AptoideUtils;
import cm.aptoide.pt.utils.design.ShowMessage;
import cm.aptoide.pt.view.ActivityView;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.ext.DefaultHandler2;
import rx.Observable;

public class AutoUpdate extends AsyncTask<Void, Void, AutoUpdate.AutoUpdateInfo> {

  private final String url;
  private final ActivityView activity;
  private final DownloadFactory downloadFactory;
  private final PermissionManager permissionManager;
  private final InstallManager installManager;
  private final Resources resources;
  private final int updateDialogIcon;
  private final boolean alwaysUpdate;
  private final String marketName;
  private final DownloadAnalytics downloadAnalytics;
  private ProgressDialog dialog;

  public AutoUpdate(ActivityView activity, DownloadFactory downloadFactory,
      PermissionManager permissionManager, InstallManager installManager, Resources resources,
      String autoUpdateUrl, @DrawableRes int updateDialogIcon, boolean alwaysUpdate,
      String marketName, DownloadAnalytics downloadAnalytics) {
    this.url = autoUpdateUrl;
    this.activity = activity;
    this.permissionManager = permissionManager;
    this.downloadFactory = downloadFactory;
    this.installManager = installManager;
    this.resources = resources;
    this.updateDialogIcon = updateDialogIcon;
    this.alwaysUpdate = alwaysUpdate;
    this.marketName = marketName;
    this.downloadAnalytics = downloadAnalytics;
  }

  @Override protected AutoUpdateInfo doInBackground(Void... params) {

    HttpURLConnection connection = null;

    try {
      SAXParser parser = SAXParserFactory.newInstance()
          .newSAXParser();
      AutoUpdateHandler autoUpdateHandler = new AutoUpdateHandler();

      Logger.getInstance()
          .d(this.getClass()
              .getName(), "Requesting auto-update from " + url);
      connection = (HttpURLConnection) new URL(url).openConnection();

      connection.setConnectTimeout(10000);
      connection.setReadTimeout(10000);

      parser.parse(connection.getInputStream(), autoUpdateHandler);

      AutoUpdateInfo autoUpdateInfo = autoUpdateHandler.getAutoUpdateInfo();

      if (autoUpdateInfo != null) {
        String packageName = activity.getPackageName();
        int vercode = autoUpdateInfo.vercode;
        int minsdk = autoUpdateInfo.minsdk;
        int minvercode = autoUpdateInfo.minAptoideVercode;
        try {
          int localVersionCode = activity.getPackageManager()
              .getPackageInfo(packageName, 0).versionCode;
          if (vercode > localVersionCode
              && localVersionCode > minvercode
              && Build.VERSION.SDK_INT >= minsdk || alwaysUpdate) {
            return autoUpdateInfo;
          }
        } catch (PackageManager.NameNotFoundException e) {
          CrashReport.getInstance()
              .log(e);
          e.printStackTrace();
        }
      }
    } catch (ParserConfigurationException e) {
      e.printStackTrace();
      CrashReport.getInstance()
          .log(e);
    } catch (SAXException e) {
      e.printStackTrace();
      CrashReport.getInstance()
          .log(e);
    } catch (MalformedURLException e) {
      e.printStackTrace();
      CrashReport.getInstance()
          .log(e);
    } catch (IOException e) {
      e.printStackTrace();
      CrashReport.getInstance()
          .log(e);
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
    return null;
  }

  @Override protected void onPostExecute(AutoUpdateInfo autoUpdateInfo) {
    super.onPostExecute(autoUpdateInfo);
    if (autoUpdateInfo != null) {
      requestUpdateSelf(autoUpdateInfo);
    }
  }

  private void requestUpdateSelf(final AutoUpdateInfo autoUpdateInfo) {

    AptoideApplication.setAutoUpdateWasCalled(true);

    AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(activity);
    final AlertDialog updateSelfDialog = dialogBuilder.create();
    updateSelfDialog.setTitle(activity.getText(R.string.update_self_title));
    updateSelfDialog.setIcon(updateDialogIcon);
    updateSelfDialog.setMessage(
        AptoideUtils.StringU.getFormattedString(R.string.update_self_msg, resources, marketName));
    updateSelfDialog.setCancelable(false);
    updateSelfDialog.setButton(DialogInterface.BUTTON_POSITIVE,
        activity.getString(android.R.string.yes), (arg0, arg1) -> {

          dialog = new ProgressDialog(activity);
          dialog.setMessage(activity.getString(R.string.retrieving_update));
          dialog.show();

          permissionManager.requestDownloadAccess(activity)
              .flatMap(
                  permissionGranted -> permissionManager.requestExternalStoragePermission(activity))
              .flatMap(success -> Observable.just(downloadFactory.create(autoUpdateInfo))
                  .flatMap(download -> installManager.install(download)
                      .toObservable()
                      .doOnSubscribe(() -> downloadAnalytics.downloadStartEvent(download,
                          AnalyticsManager.Action.CLICK,
                          DownloadAnalytics.AppContext.AUTO_UPDATE))))
              .first()
              .flatMap(downloadProgress -> installManager.getInstall(autoUpdateInfo.md5,
                  autoUpdateInfo.packageName, autoUpdateInfo.vercode))
              .skipWhile(installationProgress -> installationProgress.getState()
                  != Install.InstallationStatus.INSTALLING)
              .first(progress -> progress.getState() != Install.InstallationStatus.INSTALLING)
              .subscribe(install -> {
                // TODO: 12/07/2017 this code doesn't run
                if (install.isFailed()) {
                  ShowMessage.asSnack(activity, R.string.ws_error_SYS_1);
                }
                dismissDialog();
              }, throwable -> {
                CrashReport.getInstance()
                    .log(throwable);
                dismissDialog();
              });
        });
    updateSelfDialog.setButton(Dialog.BUTTON_NEGATIVE, activity.getString(android.R.string.no),
        (dialog, arg1) -> {
          dialog.dismiss();
        });
    if (activity.is_resumed()) {
      updateSelfDialog.show();
    }
  }

  private void dismissDialog() {
    if (this.dialog.isShowing()) {
      this.dialog.dismiss();
    }
  }

  public static class AutoUpdateInfo {

    public String md5;
    public int vercode;
    public String packageName;
    public int appId;
    public String path;
    public int minsdk = 0;
    public int minAptoideVercode = 0;
  }

  private class AutoUpdateHandler extends DefaultHandler2 {

    AutoUpdateInfo info = new AutoUpdateInfo();
    private StringBuilder sb = new StringBuilder();

    private AutoUpdateInfo getAutoUpdateInfo() {
      return info;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes)
        throws SAXException {
      super.startElement(uri, localName, qName, attributes);
      sb.setLength(0);
    }

    @Override public void endElement(String uri, String localName, String qName)
        throws SAXException {
      super.endElement(uri, localName, qName);

      if (localName.equals("versionCode")) {
        info.vercode = Integer.parseInt(sb.toString());
      } else if (localName.equals("uri")) {
        info.path = sb.toString();
      } else if (localName.equals("md5")) {
        info.md5 = sb.toString();
      } else if (localName.equals("minSdk")) {
        info.minsdk = Integer.parseInt(sb.toString());
      } else if (localName.equals("minAptVercode")) {
        info.minAptoideVercode = Integer.parseInt(sb.toString());
      }
      info.packageName = activity.getPackageName();
    }

    @Override public void characters(char[] ch, int start, int length) throws SAXException {
      super.characters(ch, start, length);
      sb.append(ch, start, length);
    }
  }
}
