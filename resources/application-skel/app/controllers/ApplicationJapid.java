package controllers;

import play.*;
import play.mvc.*;

import java.util.*;

import models.*;


public class ApplicationJapid extends cn.bran.play.JapidController {

    // the views are in the "japidroot" directory
	// access url: /ApplicationJapid/index
    public static void index() {
    	render("Bing", 33);
    }
    

}