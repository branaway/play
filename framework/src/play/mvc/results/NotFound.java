package play.mvc.results;

import play.mvc.Http;

/**
 * 404 not found
 */
public class NotFound extends Error {
	private static final long serialVersionUID = 1L;

	/**
     * @param why a description of the problem
     */
    public NotFound(String why) {
        super(Http.StatusCode.NOT_FOUND, why);
    }
}
