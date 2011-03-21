package controllers;

import play.*;
import play.mvc.*;
import play.data.validation.*;

import java.util.*;

import jobs.SendingQueue;

import models.*;

public class Application extends Controller {

    public static void index() {
        render();
    }
    
    public static void enterDemo(@Required String user, @Required String demo) {        
        if(validation.hasErrors()) {
            flash.error("Please choose a nick name and the demonstration type…");
            index();
        }
        
        // Dispatch to the demonstration        
        if(demo.equals("refresh")) {
            Refresh.index(user);
        }
        if(demo.equals("longpolling")) {
            LongPolling.room(user);
        }
        if(demo.equals("websocket")) {
            WebSocket.room(user);
        }        
    }

	public static void getDb() {
		MsgDb first = MsgDb.find("name = ?", "我自己").first();
		renderJSON(first);
	}
}