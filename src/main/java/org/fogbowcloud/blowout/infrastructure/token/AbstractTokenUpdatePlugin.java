package org.fogbowcloud.blowout.infrastructure.token;

import java.util.Properties;
import java.util.concurrent.TimeUnit;

import org.fogbowcloud.blowout.core.exception.BlowoutException;
import org.fogbowcloud.blowout.core.util.AppPropertiesConstants;
import org.fogbowcloud.manager.occi.model.Token;

public abstract class AbstractTokenUpdatePlugin {

	private static final int DEFAULT_UPDATE_TIME = 6;
	private static final TimeUnit DEFAULT_UPDATE_TIME_UNIT = TimeUnit.HOURS;
	
	private Properties properties;

	public AbstractTokenUpdatePlugin(Properties properties) {
		this.properties = properties;
	}

	public abstract Token generateToken();

	public int getUpdateTime() {
		try {
			return Integer.parseInt(
					this.properties.getProperty(AppPropertiesConstants.TOKEN_UPDATE_TIME));
		} catch (Exception e) {
			return DEFAULT_UPDATE_TIME;
		}
	}

	public TimeUnit getUpdateTimeUnits() {

		String timeUnit = this.properties
				.getProperty(AppPropertiesConstants.TOKEN_UPDATE_TIME_UNIT);

		if (UpdateTimeUnitsEnum.HOUR.getValue().equalsIgnoreCase(timeUnit)) {
			return TimeUnit.HOURS;
		} else if (UpdateTimeUnitsEnum.MINUTES.getValue().equalsIgnoreCase(timeUnit)) {
			return TimeUnit.MINUTES;
		} else if (UpdateTimeUnitsEnum.SECONDS.getValue().equalsIgnoreCase(timeUnit)) {
			return TimeUnit.SECONDS;
		} else if (UpdateTimeUnitsEnum.MILLISECONDS.getValue().equalsIgnoreCase(timeUnit)) {
			return TimeUnit.MILLISECONDS;
		}
		return DEFAULT_UPDATE_TIME_UNIT;

	}

	public abstract void validateProperties() throws BlowoutException;

	protected Properties getProperties() {
		return this.properties;
	}
}
