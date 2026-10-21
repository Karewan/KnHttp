package ovh.karewan.knhttp.common;

/*
 * Priority of the asynchronous requests.
 *
 * While less requests than the limit (KnHttp.setMaxConcurrentRequests(), 64 by default) are running,
 * a new request starts at once whatever its priority: the priority only orders the requests waiting
 * when the limit is reached.
 */
@SuppressWarnings("unused")
public enum Priority {
    /*
      NOTE: DO NOT CHANGE ORDERING OF THOSE CONSTANTS UNDER ANY CIRCUMSTANCES.
      Doing so will make ordering incorrect.
     */

	/**
	 * Lowest priority level. Used for prefetches of data.
	 */
	LOW,

	/**
	 * Default priority level.
	 */
	MEDIUM,

	/**
	 * Used for data that are currently visible on screen.
	 */
	HIGH,

	/**
	 * Data required instantly: always started at once, ignores (and doesn't count in) the concurrent requests limit.
	 */
	IMMEDIATE
}
