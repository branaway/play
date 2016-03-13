package play.mvc.results;

import play.mvc.Http;

/**
 * 403 Forbidden
 */
public class Forbidden extends Error {
	private static final long serialVersionUID = 1L;
	public Forbidden(String reason) {
        super(Http.StatusCode.FORBIDDEN, reason);
    }
}
