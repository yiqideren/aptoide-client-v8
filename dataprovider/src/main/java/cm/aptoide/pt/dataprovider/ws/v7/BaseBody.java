/*
 * Copyright (c) 2016.
 * Modified by Neurophobic Animal on 12/05/2016.
 */

package cm.aptoide.pt.dataprovider.ws.v7;

import cm.aptoide.accountmanager.AptoideAccountManager;
import cm.aptoide.pt.dataprovider.util.AptoideUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

/**
 * Created by neuro on 20-04-2016.
 */
@Getter
@Setter
@Accessors(chain = true)
public class BaseBody {

	private String access_token = AptoideAccountManager.getAccessToken();
	private int aptoide_vercode = AptoideUtils.getVerCode();
	private String cdn = "pool";
}
