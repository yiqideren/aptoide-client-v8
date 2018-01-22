package cm.aptoide.pt.billing.view;

import android.app.Activity;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.ColorInt;
import android.support.customtabs.CustomTabsIntent;
import android.widget.Toast;
import cm.aptoide.pt.BuildConfig;
import cm.aptoide.pt.billing.payment.PaymentMethod;
import cm.aptoide.pt.billing.purchase.Purchase;
import cm.aptoide.pt.billing.view.card.CreditCardAuthorizationFragment;
import cm.aptoide.pt.billing.view.login.PaymentLoginFragment;
import cm.aptoide.pt.billing.view.payment.PaymentFragment;
import cm.aptoide.pt.billing.view.payment.PaymentMethodsFragment;
import cm.aptoide.pt.navigator.ActivityNavigator;
import cm.aptoide.pt.navigator.CustomTabsNavigator;
import cm.aptoide.pt.navigator.FragmentNavigator;
import cm.aptoide.pt.navigator.Result;
import com.paypal.android.sdk.payments.PayPalConfiguration;
import com.paypal.android.sdk.payments.PayPalFuturePaymentActivity;
import com.paypal.android.sdk.payments.PayPalPayment;
import com.paypal.android.sdk.payments.PayPalService;
import com.paypal.android.sdk.payments.PaymentConfirmation;
import java.math.BigDecimal;
import rx.Observable;

public class BillingNavigator {

  private static final int CUSTOMER_AUTHORIZATION_REQUEST_CODE = 2001;
  private final Context context;
  private final PurchaseBundleMapper bundleMapper;
  private final ActivityNavigator activityNavigator;
  private final FragmentNavigator fragmentNavigator;
  private final String marketName;
  private final CustomTabsNavigator customTabsNavigator;
  private final int customTabsToolbarColor;

  public BillingNavigator(Context context, PurchaseBundleMapper bundleMapper, ActivityNavigator activityNavigator,
      FragmentNavigator fragmentNavigator, String marketName,
      CustomTabsNavigator customTabsNavigator, @ColorInt int customTabsToolbarColor) {
    this.context = context;
    this.bundleMapper = bundleMapper;
    this.activityNavigator = activityNavigator;
    this.fragmentNavigator = fragmentNavigator;
    this.marketName = marketName;
    this.customTabsNavigator = customTabsNavigator;
    this.customTabsToolbarColor = customTabsToolbarColor;
  }

  public void navigateToCustomerAuthenticationView(String merchantName) {
    fragmentNavigator.navigateToWithoutBackSave(
        PaymentLoginFragment.create(getBillingBundle(merchantName, null)), true);
  }

  public void navigateToAuthorizationView(String merchantName, PaymentMethod service) {

    final Bundle bundle = getBillingBundle(merchantName, service.getType());

    switch (service.getType()) {
      case PaymentMethod.PAYPAL:
        fragmentNavigator.navigateToWithoutBackSave(PaymentFragment.create(bundle), true);
        break;
      case PaymentMethod.CREDIT_CARD:
        fragmentNavigator.navigateTo(CreditCardAuthorizationFragment.create(bundle), true);
        break;
      default:
        throw new IllegalArgumentException(service.getType()
            + " does not require authorization. Can not navigate to authorization view.");
    }
  }

  public void popView() {
    fragmentNavigator.popBackStack();
  }

  public void navigateToPayPalForResult(int requestCode, String currency, String description,
      double amount) {

    final Bundle bundle = new Bundle();
    bundle.putParcelable(PayPalService.EXTRA_PAYPAL_CONFIGURATION,
        new PayPalConfiguration().environment(BuildConfig.PAYPAL_ENVIRONMENT)
            .clientId(BuildConfig.PAYPAL_KEY)
            .merchantName(marketName));
    bundle.putParcelable(com.paypal.android.sdk.payments.PaymentActivity.EXTRA_PAYMENT,
        new PayPalPayment(new BigDecimal(amount), currency, description,
            PayPalPayment.PAYMENT_INTENT_SALE));

    activityNavigator.navigateForResult(com.paypal.android.sdk.payments.PaymentActivity.class,
        requestCode, bundle);
  }

  public Observable<PayPalResult> payPalResults(int requestCode) {
    return activityNavigator.results(requestCode)
        .map(result -> map(result));
  }

  public void popViewWithResult(Purchase purchase) {
    activityNavigator.navigateBackWithResult(Activity.RESULT_OK, bundleMapper.map(purchase));
  }

  public void popViewWithResult(Throwable throwable) {
    activityNavigator.navigateBackWithResult(Activity.RESULT_CANCELED, bundleMapper.map(throwable));
  }

  public void popViewWithResult() {
    activityNavigator.navigateBackWithResult(Activity.RESULT_CANCELED,
        bundleMapper.mapCancellation());
  }

  private Bundle getBillingBundle(String merchantName, String serviceName) {
    final Bundle bundle = new Bundle();
    bundle.putString(BillingActivity.EXTRA_MERCHANT_PACKAGE_NAME, merchantName);
    bundle.putString(BillingActivity.EXTRA_SERVICE_NAME, serviceName);
    return bundle;
  }

  private PayPalResult map(Result result) {
    switch (result.getResultCode()) {
      case Activity.RESULT_OK:
        final PaymentConfirmation confirmation = result.getData()
            .getParcelableExtra(
                com.paypal.android.sdk.payments.PaymentActivity.EXTRA_RESULT_CONFIRMATION);
        if (confirmation != null && confirmation.getProofOfPayment() != null) {
          return new BillingNavigator.PayPalResult(BillingNavigator.PayPalResult.SUCCESS,
              confirmation.getProofOfPayment()
                  .getPaymentId());
        } else {
          return new BillingNavigator.PayPalResult(BillingNavigator.PayPalResult.ERROR, null);
        }
      case Activity.RESULT_CANCELED:
        return new BillingNavigator.PayPalResult(BillingNavigator.PayPalResult.CANCELLED, null);
      case PayPalFuturePaymentActivity.RESULT_EXTRAS_INVALID:
      default:
        return new BillingNavigator.PayPalResult(BillingNavigator.PayPalResult.ERROR, null);
    }
  }

  public void navigateToUriForResult(String redirectUrl) {
    customTabsNavigator.navigateToCustomTabs(
        new CustomTabsIntent.Builder().setToolbarColor(customTabsToolbarColor)
            .build(), Uri.parse(redirectUrl));
  }

  public Observable<Uri> uriResults() {
    return customTabsNavigator.customTabResults();
  }

  public void navigateToPaymentView(String merchantName) {
    fragmentNavigator.navigateToWithoutBackSave(
        PaymentFragment.create(getBillingBundle(merchantName, null)), true);
  }

  public void navigateToPaymentMethodsView(String merchantName) {
    fragmentNavigator.navigateToWithoutBackSave(
        PaymentMethodsFragment.create(getBillingBundle(merchantName, null)), true);
  }

  public void navigateToManageAuthorizationsView() {
    Toast.makeText(context, "Not implemented!", Toast.LENGTH_SHORT).show();
  }

  public static class PayPalResult {

    public static final int SUCCESS = 0;
    public static final int ERROR = 1;
    public static final int CANCELLED = 2;

    private final int status;
    private final String paymentConfirmationId;

    public PayPalResult(int status, String paymentConfirmationId) {
      this.status = status;
      this.paymentConfirmationId = paymentConfirmationId;
    }

    public int getStatus() {
      return status;
    }

    public String getPaymentConfirmationId() {
      return paymentConfirmationId;
    }
  }
}