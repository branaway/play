package play.mvc;

import java.io.File;
import java.io.InputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.Future;

import org.w3c.dom.Document;

import play.Invoker.Suspend;
import play.Logger;
import play.Play;
import play.classloading.ApplicationClasses.ApplicationClass;
import play.data.binding.Unbinder;
import play.data.validation.Validation;
import play.exceptions.NoRouteFoundException;
import play.exceptions.PlayException;
import play.exceptions.TemplateNotFoundException;
import play.exceptions.UnexpectedException;
import play.libs.F;
import play.libs.Time;
import play.mvc.Http.Request;
import play.mvc.Router.ActionDefinition;
import play.mvc.results.BadRequest;
import play.mvc.results.Error;
import play.mvc.results.Forbidden;
import play.mvc.results.NotFound;
import play.mvc.results.NotModified;
import play.mvc.results.Ok;
import play.mvc.results.Redirect;
import play.mvc.results.RedirectToStatic;
import play.mvc.results.RenderBinary;
import play.mvc.results.RenderHtml;
import play.mvc.results.RenderJson;
import play.mvc.results.RenderTemplate;
import play.mvc.results.RenderText;
import play.mvc.results.RenderXml;
import play.mvc.results.Unauthorized;
import play.templates.Template;
import play.templates.TemplateLoader;
import play.vfs.VirtualFile;
import sun.reflect.generics.reflectiveObjects.NotImplementedException;

import com.google.gson.JsonSerializer;
import com.thoughtworks.xstream.XStream;

/**
 * Application controller support: The controller receives input and initiates a response by making calls on model objects.
 *
 * This is the class that your controllers should extend.
 * bran: this Groovy based old system is deprecated in favor of JapidController 
 */
public class Controller implements ControllerSupport {

    /**
     * The current HTTP request: the message sent by the client to the server.
     *
     * Note: The ControllersEnhancer makes sure that an appropriate thread local version is applied.
     * ie : controller.request - controller.request.current()
     *
     */
    protected static Http.Request request = null;
    /**
     * The current HTTP response: The message sent back from the server after a request.
     *
     * Note: The ControllersEnhancer makes sure that an appropriate thread local version is applied.
     * ie : controller.response - controller.response.current()
     *
     */
    protected static Http.Response response = null;
    /**
     * The current HTTP session. The Play! session is not living on the server side but on the client side.
     * In fact, it is stored in a signed cookie. This session is therefore limited to 4kb.
     *
     * From Wikipedia:
     * 
     * Client-side sessions use cookies and cryptographic techniques to maintain state without storing as much data on the server. When presenting a dynamic web page, the server sends the current state data to the client (web browser) in the form of a cookie. The client saves the cookie in memory or on disk. With each successive request, the client sends the cookie back to the server, and the server uses the data to "remember" the state of the application for that specific client and generate an appropriate response.
     * This mechanism may work well in some contexts; however, data stored on the client is vulnerable to tampering by the user or by software that has access to the client computer. To use client-side sessions where confidentiality and integrity are required, the following must be guaranteed:
     * Confidentiality: Nothing apart from the server should be able to interpret session data.
     * Data integrity: Nothing apart from the server should manipulate session data (accidentally or maliciously).
     * Authenticity: Nothing apart from the server should be able to initiate valid sessions.
     * To accomplish this, the server needs to encrypt the session data before sending it to the client, and modification of such information by any other party should be prevented via cryptographic means.
     * Transmitting state back and forth with every request is only practical when the size of the cookie is small. In essence, client-side sessions trade server disk space for the extra bandwidth that each web request will require. Moreover, web browsers limit the number and size of cookies that may be stored by a web site. To improve efficiency and allow for more session data, the server may compress the data before creating the cookie, decompressing it later when the cookie is returned by the client.
     *
     * Note: The ControllersEnhancer makes sure that an appropriate thread local version is applied.
     * ie : controller.session - controller.session.current()
     */
    protected static Scope.Session session = null;
    /**
     * The current flash scope. The flash is a temporary storage mechanism that is a hash map
     * You can store values associated with keys and later retrieve them.
     * It has one special property: by default, values stored into the flash during the processing of a request
     * will be available during the processing of the immediately following request.
     * Once that second request has been processed, those values are removed automatically from the storage
     *
     * This scope is very useful to display messages after issuing a Redirect.
     *
     * Note: The ControllersEnhancer makes sure that an appropriate thread local version is applied.
     * ie : controller.flash - controller.flash.current()
     */
    protected static Scope.Flash flash = null;
    /**
     * The current HTTP params. This scope allows you to access the HTTP parameters supplied with the request.
     *
     * This is useful for example to know which submit button a user pressed on a form.
     *
     * Note: The ControllersEnhancer makes sure that an appropriate thread local version is applied.
     * ie : controller.params - controller.params.current()
     */
    protected static Scope.Params params = null;
    /**
     * The current renderArgs scope: This is a hash map that is accessible during the rendering phase. It means you can access
     * variables stored in this scope during the rendering phase (the template phase). 
     *
     * Note: The ControllersEnhancer makes sure that an appropriate thread local version is applied.
     * ie : controller.renderArgs - controller.renderArgs.current()
     */
    protected static Scope.RenderArgs renderArgs = null;
    /**
     * The current routeArgs scope: This is a hash map that is accessible during the reverse routing phase.
     * Any variable added to this scope will be used for reverse routing. Useful when you have a param that you want
     * to add to any route without add it expicitely to every action method.
     *
     * Note: The ControllersEnhancer makes sure that an appropriate thread local version is applied.
     * ie : controller.routeArgs - controller.routeArgs.current()
     */
    protected static Scope.RouteArgs routeArgs = null;
    /**
     * The current Validation object. It allows you to validate objects and to retrieve potential validations errors for those objects.
     *
     * Note: The ControllersEnhancer makes sure that an appropriate thread local version is applied.
     * ie : controller.validation - controller.validation.current()
     */
    protected static Validation validation = null;

    /**
     * Return a 200 OK text/plain response
     * @param text The response content
     */
    protected static void renderText(Object text) {
        throw new RenderText(text == null ? "" : text.toString());
    }

    /**
     * Return a 200 OK text/html response
     * @param html The response content
     */
    protected static void renderHtml(Object html) {
        throw new RenderHtml(html == null ? "" : html.toString());
    }

    /**
     * Return a 200 OK text/plain response
     * @param pattern The response content to be formatted (with String.format)
     * @param args Args for String.format
     */
    protected static void renderText(CharSequence pattern, Object... args) {
        throw new RenderText(pattern == null ? "" : String.format(pattern.toString(), args));
    }

    /**
     * Return a 200 OK text/xml response
     * @param xml The XML string
     */
    protected static void renderXml(String xml) {
        throw new RenderXml(xml);
    }

    /**
     * Return a 200 OK text/xml response
     * @param xml The DOM document object
     */
    protected static void renderXml(Document xml) {
        throw new RenderXml(xml);
    }

    /**
     * Return a 200 OK text/xml response. Use renderXml(Object, XStream) to customize the result.
     * @param o the object to serialize
     */
    protected static void renderXml(Object o) {
        throw new RenderXml(o);
    }

    /**
     * Return a 200 OK text/xml response
     * @param o the object to serialize
     * @param xstream the XStream object to use for serialization. See XStream's documentation
     *      for details about customizing the output.
     */
    protected static void renderXml(Object o, XStream xstream) {
        throw new RenderXml(o, xstream);
    }

    /**
     * Return a 200 OK application/binary response
     * @param is The stream to copy
     */
    protected static void renderBinary(InputStream is) {
        throw new RenderBinary(is, null, true);
    }

    /**
     * Return a 200 OK application/binary response. Content is streamed.
     *
     * @param is The stream to copy
     * @param length Stream's size in bytes.
     */
    protected static void renderBinary(InputStream is, long length) {
        throw new RenderBinary(is, null, length, true);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition attachment.
     *
     * @param is The stream to copy
     * @param name Name of file user is downloading.
     */
    protected static void renderBinary(InputStream is, String name) {
        throw new RenderBinary(is, name, false);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition attachment.
     *
     * @param is The stream to copy. Content is streamed.
     * @param name Name of file user is downloading.
     * @param length Stream's size in bytes.
     */
    protected static void renderBinary(InputStream is, String name, long length) {
        throw new RenderBinary(is, name, length, false);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition attachment.
     *
     * @param is The stream to copy
     * @param name Name of file user is downloading.
     * @param inline true to set the response Content-Disposition to inline
     */
    protected static void renderBinary(InputStream is, String name, boolean inline) {
        throw new RenderBinary(is, name, inline);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition attachment.
     *
     * @param is The stream to copy
     * @param name The attachment name
     * @param length Stream's size in bytes.
     * @param inline true to set the response Content-Disposition to inline
     */
    protected static void renderBinary(InputStream is, String name, long length, boolean inline) {
        throw new RenderBinary(is, name, length, inline);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition attachment
     * @param is The stream to copy
     * @param name The attachment name
     * @param contentType The content type of the attachment
     * @param inline true to set the response Content-Disposition to inline
     */
    protected static void renderBinary(InputStream is, String name, String contentType, boolean inline) {
        throw new RenderBinary(is, name, contentType, inline);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition attachment.
     *
     * @param is The stream to copy
     * @param name The attachment name
     * @param length Content's byte size.
     * @param contentType The content type of the attachment
     * @param inline true to set the response Content-Disposition to inline
     */
    protected static void renderBinary(InputStream is, String name, long length, String contentType, boolean inline) {
        throw new RenderBinary(is, name, length, contentType, inline);
    }

    /**
     * Return a 200 OK application/binary response
     * @param file The file to copy
     */
    protected static void renderBinary(File file) {
        throw new RenderBinary(file);
    }

    /**
     * Return a 200 OK application/binary response with content-disposition attachment
     * @param file The file to copy
     * @param name The attachment name
     */
    protected static void renderBinary(File file, String name) {
        throw new RenderBinary(file, name);
    }

    /**
     * Render a 200 OK application/json response
     * @param jsonString The JSON string
     */
    protected static void renderJSON(String jsonString) {
        throw new RenderJson(jsonString);
    }

    /**
     * Render a 200 OK application/json response
     * @param o The Java object to serialize
     */
    protected static void renderJSON(Object o) {
        throw new RenderJson(o);
    }

    /**
     * Render a 200 OK application/json response
     * @param o The Java object to serialize
     * @param type The Type informations for complex generic types
     */
    protected static void renderJSON(Object o, Type type) {
        throw new RenderJson(o, type);
    }

    /**
     * Render a 200 OK application/json response.
     * @param o The Java object to serialize
     * @param adapters A set of GSON serializers/deserializers/instance creator to use
     */
    protected static void renderJSON(Object o, JsonSerializer<?>... adapters) {
        throw new RenderJson(o, adapters);
    }

    /**
     * Send a 304 Not Modified response
     */
    protected static void notModified() {
        throw new NotModified();
    }

    /**
     * Send a 400 Bad request
     */
    protected static void badRequest() {
        throw new BadRequest();
    }

    /**
     * Send a 401 Unauthorized response
     * @param realm The realm name
     */
    protected static void unauthorized(String realm) {
        throw new Unauthorized(realm);
    }

    /**
     * Send a 401 Unauthorized response
     */
    protected static void unauthorized() {
        throw new Unauthorized("Unauthorized");
    }

    /**
     * Send a 404 Not Found response
     * @param what The Not Found resource name
     */
    protected static void notFound(String what) {
        throw new NotFound(what);
    }

    /**
     * Send a 200 OK response
     */
    protected static void ok() {
        throw new Ok();
    }

    /**
     * Send a todo response
     */
    protected static void todo() {
        notFound("This action has not been implemented Yet (" + request.action + ")");
    }

    /**
     * Send a 404 Not Found response if object is null
     * @param o The object to check
     */
    protected static void notFoundIfNull(Object o) {
        if (o == null) {
            notFound();
        }
    }

    /**
     * Send a 404 Not Found response if object is null
     * @param o The object to check
     * @param what The Not Found resource name
     */
    protected static void notFoundIfNull(Object o, String what) {
        if (o == null) {
            notFound(what);
        }
    }

    /**
     * Send a 404 Not Found reponse
     */
    protected static void notFound() {
        throw new NotFound("");
    }

    /**
     * Check that the token submitted from a form is valid.
     *
     * @see play.templates.FastTags._authenticityToken()
     */
    protected static void checkAuthenticity() {
        if(Scope.Params.current().get("authenticityToken") == null || !Scope.Params.current().get("authenticityToken").equals(Scope.Session.current().getAuthenticityToken())) {
            forbidden("Bad authenticity token");
        }
    }

    /**
     * Send a 403 Forbidden response
     * @param reason The reason
     */
    protected static void forbidden(String reason) {
        throw new Forbidden(reason);
    }

    /**
     * Send a 403 Forbidden response
     */
    protected static void forbidden() {
        throw new Forbidden("Access denied");
    }

    /**
     * Send a 5xx Error response
     * @param status The exact status code
     * @param reason The reason
     */
    protected static void error(int status, String reason) {
        throw new Error(status, reason);
    }

    /**
     * Send a 500 Error response
     * @param reason The reason
     */
    protected static void error(String reason) {
        throw new Error(reason);
    }

    /**
     * Send a 500 Error response
     * @param reason The reason
     */
    protected static void error(Exception reason) {
        Logger.error(reason, "error()");
        throw new Error(reason.toString());
    }

    /**
     * Send a 500 Error response
     */
    protected static void error() {
        throw new Error("Internal Error");
    }

    /**
     * Add a value to the flash scope
     * @param key The key
     * @param value The value
     */
    protected static void flash(String key, Object value) {
        Scope.Flash.current().put(key, value);
    }

//    /**
//     * Send a 302 redirect response.
//     * @param url The Location to redirect
//     */
//    protected static void redirect(String url) {
//        redirect(url, false);
//    }

    /**
     * Send a 302 redirect response.
     * @param file The Location to redirect
     */
    protected static void redirectToStatic(String file) {
        try {
            VirtualFile vf = Play.getVirtualFile(file);
            if (vf == null || !vf.exists()) {
                throw new NoRouteFoundException(file);
            }
            throw new RedirectToStatic(Router.reverse(Play.getVirtualFile(file)));
        } catch (NoRouteFoundException e) {
            StackTraceElement element = PlayException.getInterestingStrackTraceElement(e);
            if (element != null) {
                throw new NoRouteFoundException(file, Play.classes.getApplicationClass(element.getClassName()), element.getLineNumber());
            } else {
                throw e;
            }
        }
    }

    /**
     * Send a Redirect response.
     * @param url The Location to redirect
     * @param permanent true -> 301, false -> 302
     */
    protected static void redirect(String url, boolean permanent) {
        if (url.indexOf("/") == -1) { // fix Java !
            redirect(url, permanent, new Object[0]);
        }
        throw new Redirect(url, permanent);
    }

    /**
     * 302 Redirect to another action
     * @param action The fully qualified action name (ex: Application.index)
     * @param args Method arguments
     */
    public static void redirect(String action, Object... args) {
        redirect(action, false, args);
    }

    /**
     * Redirect to another action
     * @param action The fully qualified action name (ex: Application.index)
     * @param permanent true -> 301, false -> 302
     * @param args Method arguments
     */
    protected static void redirect(String action, boolean permanent, Object... args) {
        try {
            Map<String, Object> newArgs = new HashMap<String, Object>(args.length);
            Method actionMethod = (Method) ActionInvoker.getActionMethod(action)[1];
//            String[] names = (String[]) actionMethod.getDeclaringClass().getDeclaredField("$" + actionMethod.getName() + LVEnhancer.computeMethodHash(actionMethod.getParameterTypes())).get(null);
            String[] names = play.utils.Utils.getParamNames(actionMethod);
            for (int i = 0; i < names.length && i < args.length; i++) {
            	Unbinder.unBind(newArgs, args[i], names[i], actionMethod.getParameterAnnotations()[i]);
            }
            
            try {

                ActionDefinition actionDefinition = Router.reverse(action, newArgs);

                if (_currentReverse.get() != null) {
                    ActionDefinition currentActionDefinition = _currentReverse.get();
                    currentActionDefinition.action = actionDefinition.action;
                    currentActionDefinition.url = actionDefinition.url;
                    currentActionDefinition.method = actionDefinition.method;
                    currentActionDefinition.star = actionDefinition.star;
                    currentActionDefinition.args = actionDefinition.args;

                    _currentReverse.remove();
                } else {
                    throw new Redirect(actionDefinition.toString(), permanent);
                }
            } catch (NoRouteFoundException e) {
                StackTraceElement element = PlayException.getInterestingStrackTraceElement(e);
                if (element != null) {
                    throw new NoRouteFoundException(action, newArgs, Play.classes.getApplicationClass(element.getClassName()), element.getLineNumber());
                } else {
                    throw e;
                }
            }
        } catch (Exception e) {
            if (e instanceof Redirect) {
                throw (Redirect) e;
            }
            if (e instanceof PlayException) {
                throw (PlayException) e;
            }
            throw new UnexpectedException(e);
        }
    }

    protected static boolean templateExists(String templateName) {
        try {
            TemplateLoader.load(template(templateName));
            return true;
        } catch (TemplateNotFoundException ex) {
            return false;
        }
    }

    /**
     * Render a specific template.
     *
     * @param templateName The template name.
     * @param args The template data.
     */
    protected static void renderTemplate(String templateName, Map<String,Object> args) {
        // Template datas
        Scope.RenderArgs templateBinding = Scope.RenderArgs.current();
        templateBinding.data.putAll(args);
        templateBinding.put("session", Scope.Session.current());
        templateBinding.put("request", Http.Request.current());
        templateBinding.put("flash", Scope.Flash.current());
        templateBinding.put("params", Scope.Params.current());
        templateBinding.put("errors", Validation.errors());
        try {
            Template template = TemplateLoader.load(template(templateName));
            throw new RenderTemplate(template, templateBinding.data);
        } catch (TemplateNotFoundException ex) {
            if (ex.isSourceAvailable()) {
                throw ex;
            }
            StackTraceElement element = PlayException.getInterestingStrackTraceElement(ex);
            if (element != null) {
                ApplicationClass applicationClass = Play.classes.getApplicationClass(element.getClassName());
                if (applicationClass != null) {
                    throw new TemplateNotFoundException(templateName, applicationClass, element.getLineNumber());
                }
            }
            throw ex;
        }
    }

    /**
     * Render the template corresponding to the action's package-class-method name (@see <code>template()</code>).
     *
     * @param args The template data.
     */
    protected static void renderTemplate(Map<String,Object> args) {
        renderTemplate(template(), args);
    }


    /**
     * Work out the default template to load for the invoked action.
     * E.g. "controllers.Pages.index" returns "views/Pages/index.html".
     */
    protected static String template() {
        final Request theRequest = Request.current();
        final String format = theRequest.format;
        String templateName = theRequest.action.replace(".", "/") + "." + (format == null ? "html" : format);
        if (templateName.startsWith("@")) {
            templateName = templateName.substring(1);
            if (!templateName.contains(".")) {
                templateName = theRequest.controller + "." + templateName;
            }
            templateName = templateName.replace(".", "/") + "." + (format == null ? "html" : format);
        }
        return templateName;
    }

    /**
     * Work out the default template to load for the action.
     * E.g. "controllers.Pages.index" returns "views/Pages/index.html".
     */
    protected static String template(String templateName) {
        final Request theRequest = Request.current();
        final String format = theRequest.format;
        if (templateName.startsWith("@")) {
            templateName = templateName.substring(1);
            if (!templateName.contains(".")) {
                templateName = theRequest.controller + "." + templateName;
            }
            templateName = templateName.replace(".", "/") + "." + (format == null ? "html" : format);
        }
        return templateName;
    }

    /**
     * Retrieve annotation for the action method
     * @param clazz The annotation class
     * @return Annotation object or null if not found
     */
    protected static <T extends Annotation> T getActionAnnotation(Class<T> clazz) {
        Method m = (Method) ActionInvoker.getActionMethod(Http.Request.current().action)[1];
        if (m.isAnnotationPresent(clazz)) {
            return m.getAnnotation(clazz);
        }
        return null;
    }

    /**
     * Retrieve annotation for the controller class
     * @param clazz The annotation class
     * @return Annotation object or null if not found
     */
    protected static <T extends Annotation> T getControllerAnnotation(Class<T> clazz) {
        if (getControllerClass().isAnnotationPresent(clazz)) {
            return getControllerClass().getAnnotation(clazz);
        }
        return null;
    }

    /**
     * Retrieve annotation for the controller class
     * @param clazz The annotation class
     * @return Annotation object or null if not found
     */
    protected static <T extends Annotation> T getControllerInheritedAnnotation(Class<T> clazz) {
        Class<?> c = getControllerClass();
        while (!c.equals(Object.class)) {
            if (c.isAnnotationPresent(clazz)) {
                return c.getAnnotation(clazz);
            }
            c = c.getSuperclass();
        }
        return null;
    }

    /**
     * Retrieve the controller class
     * @return Annotation object or null if not found
     */
    protected static Class<? extends Controller> getControllerClass() {
        return Http.Request.current().controllerClass;
    }


    /**
     * Suspend the current request for a specified amount of time.
     *
     * <p><b>Important:</b> The method will not resume on the line after you call this. The method will
     * be called again as if there was a new HTTP request.
     *
     * @param timeout Period of time to wait, e.g. "1h" means 1 hour.
     */
    @Deprecated
    protected static void suspend(String timeout) {
        suspend(1000 * Time.parseDuration(timeout));
    }

    /**
     * Suspend the current request for a specified amount of time (in milliseconds).
     *
     * <p><b>Important:</b> The method will not resume on the line after you call this. The method will
     * be called again as if there was a new HTTP request.
     *
     * @param millis Number of milliseconds to wait until trying again.
     */
    @Deprecated
    protected static void suspend(int millis) {
        Request.current().isNew = false;
        throw new Suspend(millis);
    }

    /**
     * Suspend this request and wait for the task completion
     *
     * <p><b>Important:</b> The method will not resume on the line after you call this. The method will
     * be called again as if there was a new HTTP request.
     *
     * @param tasks
     */
    @Deprecated
    protected static void waitFor(Future<?> task) {
        Request.current().isNew = false;
        throw new Suspend(task);
    }

    protected static void await(String timeout, F.Action0 callback) {
        await(1000 * Time.parseDuration(timeout), callback);
    }
 
    protected static void await(int millis, F.Action0 callback) {
        Request.current().isNew = false;
        Request.current().args.put(ActionInvoker.A, callback);
        Request.current().args.put(ActionInvoker.CONTINUATIONS_STORE_RENDER_ARGS, Scope.RenderArgs.current());
        throw new Suspend(millis);
    }


   protected static <T> void await(Future<T> future, F.Action<T> callback) {
        Request.current().isNew = false;
        Request.current().args.put(ActionInvoker.F, future);
        Request.current().args.put(ActionInvoker.A, callback);
        Request.current().args.put(ActionInvoker.CONTINUATIONS_STORE_RENDER_ARGS, Scope.RenderArgs.current());
        throw new Suspend(future);
    }

    /**
     * Don't use this directly if you don't know why
     */
    public static ThreadLocal<ActionDefinition> _currentReverse = new ThreadLocal<ActionDefinition>();

    /**
     * @todo - this "Usage" example below doesn't make sense.
     *
     * Usage:
     *
     * <code>
     * ActionDefinition action = reverse(); {
     *     Application.anyAction(anyParam, "toto");
     * }
     * String url = action.url;
     * </code>
     */
    protected static ActionDefinition reverse() {
        ActionDefinition actionDefinition = new ActionDefinition();
        _currentReverse.set(actionDefinition);
        return actionDefinition;
    }

	/**
	 * @author Bing Ran (bing.ran@gmail.com)
	 * @param timeout
	 */
	public static void await(String timeout) {
		throw new NotImplementedException();
	}
	
	/**
	 * @author Bing Ran (bing.ran@gmail.com)
	 * @param timeout
	 */
	public static <T> T await(Future<T> future) {
		throw new NotImplementedException();
	}

	/**
	 * @author Bing Ran (bing.ran@gmail.com)
	 * @param millis
	 */
	public static void await(int millis) {
		throw new NotImplementedException();
	}
}
