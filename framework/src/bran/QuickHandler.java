package bran;

import static org.jboss.netty.handler.codec.http.HttpHeaders.isKeepAlive;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_LENGTH;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.CONTENT_TYPE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.COOKIE;
import static org.jboss.netty.handler.codec.http.HttpHeaders.Names.SET_COOKIE;
import static org.jboss.netty.handler.codec.http.HttpResponseStatus.OK;
import static org.jboss.netty.handler.codec.http.HttpVersion.HTTP_1_1;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.jboss.netty.buffer.ChannelBuffers;
import org.jboss.netty.channel.ChannelFuture;
import org.jboss.netty.channel.ChannelFutureListener;
import org.jboss.netty.channel.ChannelHandlerContext;
import org.jboss.netty.channel.ExceptionEvent;
import org.jboss.netty.channel.MessageEvent;
import org.jboss.netty.channel.SimpleChannelUpstreamHandler;
import org.jboss.netty.handler.codec.http.Cookie;
import org.jboss.netty.handler.codec.http.CookieDecoder;
import org.jboss.netty.handler.codec.http.CookieEncoder;
import org.jboss.netty.handler.codec.http.DefaultHttpResponse;
import org.jboss.netty.handler.codec.http.HttpRequest;
import org.jboss.netty.handler.codec.http.HttpResponse;
import org.jboss.netty.util.CharsetUtil;

import com.ning.http.client.AsyncCompletionHandler;
import com.ning.http.client.AsyncHandler;
import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.AsyncHttpClient.BoundRequestBuilder;
import com.ning.http.client.ListenableFuture;
import com.ning.http.client.Response;

public class QuickHandler extends SimpleChannelUpstreamHandler {
	private final class MyAsyncHandler extends AsyncCompletionHandler<Response> {
		Responder  run = null;
		public MyAsyncHandler(Responder run) {
			super();
			this.run = run;
		}

		@Override
		public void onThrowable(Throwable arg0) {
			arg0.printStackTrace();
		}

		@Override
		public Response onCompleted(Response response) throws Exception {
			run.respond(response);
			return null;
		}
	}

	private HttpRequest request;
	
	/** Buffer that stores the response content */
	private StringBuilder buf = new StringBuilder();

	ExecutorService executor = Executors.newCachedThreadPool();
	AsyncHttpClient client = new AsyncHttpClient();

	@Override
	public void messageReceived(ChannelHandlerContext ctx, final MessageEvent e) throws Exception {
		buf = new StringBuilder();
		request = (HttpRequest) e.getMessage();
		System.out.println("before submitted");
		BoundRequestBuilder prepareGet = client.prepareGet("http://bing.com/");
		System.out.println("before EXECUTE");
		AsyncHandler<Response> asyncHandler = new MyAsyncHandler(new Responder() {
			@Override
			public void respond(Response response) {
				try {
//					client.close();
//					buf.append(response.getResponseBody());
					buf.append(response.getHeaders().size());
					System.out.println("got response");
					writeResponse(e);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
		final ListenableFuture<Response> execute = prepareGet.execute(asyncHandler);
		System.out.println("request submitted");
		// Promise<play.libs.WS.HttpResponse> async =
		// WS.url("http://google.com/").getAsync();
		// async.onRedeem(new Action<Promise<play.libs.WS.HttpResponse>>() {
		// @Override
		// public void invoke(Promise<play.libs.WS.HttpResponse> result) {
		// try {
		// String string = result.get().getString();
		// buf.append(string);
		// writeResponse(e);
		// } catch (Exception e) {
		// e.printStackTrace();
		// }
		// }
		// });
		//
		// final String a = "hello";
		// final Promise<String> smartFuture = new Promise<String>();
		// executor.submit(new Callable<String>() {
		// @Override
		// public String call() throws Exception {
		// String result = a;
		// smartFuture.invoke(result);
		// return result;
		// }
		// });
		//
		//
		// buf.setLength(0);
		// buf.append("WELCOME TO THE WILD WILD WEB SERVER\r\n");
		// buf.append("===================================\r\n");
		//
		// buf.append("VERSION: " + request.getProtocolVersion() + "\r\n");
		// buf.append("HOSTNAME: " + " unknown" + "\r\n");
		// buf.append("REQUEST_URI: " + request.getUri() + "\r\n\r\n");
		//
		// for (Map.Entry<String, String> h : request.getHeaders()) {
		// buf.append("HEADER: " + h.getKey() + " = " + h.getValue() + "\r\n");
		// }
		// buf.append("\r\n");
		//
		// QueryStringDecoder queryStringDecoder = new
		// QueryStringDecoder(request.getUri());
		// Map<String, List<String>> params =
		// queryStringDecoder.getParameters();
		// if (!params.isEmpty()) {
		// for (Entry<String, List<String>> p : params.entrySet()) {
		// String key = p.getKey();
		// List<String> vals = p.getValue();
		// for (String val : vals) {
		// buf.append("PARAM: " + key + " = " + val + "\r\n");
		// }
		// }
		// buf.append("\r\n");
		// }
		//
		// ChannelBuffer content = request.getContent();
		// if (content.readable()) {
		// buf.append("CONTENT: " + content.toString(CharsetUtil.UTF_8) +
		// "\r\n");
		// }
		// writeResponse(e);
	}

	private void writeResponse(MessageEvent e) {
		// Decide whether to close the connection or not.
		boolean keepAlive = isKeepAlive(request);

		// Build the response object.
		HttpResponse response = new DefaultHttpResponse(HTTP_1_1, OK);
		response.setContent(ChannelBuffers.copiedBuffer(buf.toString(), CharsetUtil.UTF_8));
		response.setHeader(CONTENT_TYPE, "text/plain; charset=UTF-8");

		if (keepAlive) {
			// Add 'Content-Length' header only for a keep-alive connection.
			response.setHeader(CONTENT_LENGTH, response.getContent().readableBytes());
		}

		// Encode the cookie.
		String cookieString = request.getHeader(COOKIE);
		if (cookieString != null) {
			CookieDecoder cookieDecoder = new CookieDecoder();
			Set<Cookie> cookies = cookieDecoder.decode(cookieString);
			if (!cookies.isEmpty()) {
				// Reset the cookies if necessary.
				CookieEncoder cookieEncoder = new CookieEncoder(true);
				for (Cookie cookie : cookies) {
					cookieEncoder.addCookie(cookie);
				}
				response.addHeader(SET_COOKIE, cookieEncoder.encode());
			}
		}

		// Write the response.
		ChannelFuture future = e.getChannel().write(response);

		// Close the non-keep-alive connection after the write operation is
		// done.
		if (!keepAlive) {
			future.addListener(ChannelFutureListener.CLOSE);
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, ExceptionEvent e)
					throws Exception {
		e.getCause().printStackTrace();
		e.getChannel().close();
	}
}