/*
 * Copyright (c) 2016.
 * Modified by Marcelo Benites on 05/12/2016.
 */

package cm.aptoide.pt.dataprovider.ws.v3;

import cm.aptoide.pt.dataprovider.ws.BodyInterceptor;
import cm.aptoide.pt.model.v3.BaseV3Response;
import okhttp3.OkHttpClient;
import retrofit2.Converter;
import rx.Observable;

public class CreatePaymentAuthorizationRequest extends V3<BaseV3Response> {

  private final boolean hasAuthorizationCode;

  private CreatePaymentAuthorizationRequest(BaseBody baseBody,
      BodyInterceptor<BaseBody> bodyInterceptor, OkHttpClient httpClient,
      Converter.Factory converterFactory, boolean hasAuthorizationCode) {
    super(baseBody, httpClient, converterFactory, bodyInterceptor);
    this.hasAuthorizationCode = hasAuthorizationCode;
  }

  public static CreatePaymentAuthorizationRequest of(int paymentId,
      BodyInterceptor<BaseBody> bodyInterceptor, OkHttpClient httpClient,
      Converter.Factory converterFactory) {
    BaseBody args = new BaseBody();
    args.put("payType", String.valueOf(paymentId));
    return new CreatePaymentAuthorizationRequest(args, bodyInterceptor, httpClient,
        converterFactory, false);
  }

  public static CreatePaymentAuthorizationRequest of(int paymentId, String authorizationCode,
      BodyInterceptor<BaseBody> bodyInterceptor, OkHttpClient httpClient,
      Converter.Factory converterFactory) {
    BaseBody args = new BaseBody();
    args.put("payType", String.valueOf(paymentId));
    args.put("authToken", authorizationCode);
    args.put("reqType", "rest");
    return new CreatePaymentAuthorizationRequest(args, bodyInterceptor, httpClient,
        converterFactory, true);
  }

  @Override protected Observable<BaseV3Response> loadDataFromNetwork(Interfaces interfaces,
      boolean bypassCache) {
    if (hasAuthorizationCode) {
      return interfaces.createPaymentAuthorizationWithCode(map);
    }
    return interfaces.createPaymentAuthorization(map);
  }
}
