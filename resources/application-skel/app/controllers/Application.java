package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;

// see the ApplicationJapid for Japid system
public class Application extends Controller {

	// using the classic Groovy views in the "app/view" directory
    public static void index() {
        render();
    }
    
}