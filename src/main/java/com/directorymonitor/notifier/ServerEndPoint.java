package com.directorymonitor.notifier;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

import com.directorymonitor.model.NotificationModel;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@ServerEndpoint(value = "/ServerEndPoint")
public class ServerEndPoint {

	public static Set<Session> users = new HashSet<Session>();
	
	@OnOpen
	public void handleOpen(Session userSession) {
		System.out.println("INFO: Adding User: " + userSession + " to the queue at the server.");
		users.add(userSession);
		System.out.println("INFO: After Adding number of active users = " + users.size());
	}

	@OnClose
	public void handleClose(Session userSession) {
	
		System.out.println("INFO: Removing User: " + userSession + " from the queue at the server.");
		users.remove(userSession);
		System.out.println("INFO: After removing number of active users = " + users.size());
	}

	@OnMessage
	public void handleMessage(String message, Session userSession) {
		try {
			System.out.println("INFO: Notifying all the active users. JSON_Message: " + message);
			for (Session eachUser : users) {
				System.out.println("INFO: Notification sent from " + userSession + " to "+ eachUser);
				eachUser.getBasicRemote().sendText(message);
			}
		} catch (IOException e) {
			System.out.println("ERROR: Server cannot send notifications.");
		}
	}
	
	public synchronized static void notifyAllUsers(NotificationModel notification) {
		try {
			String message = convertToJson(notification);
			if(message != null) {
				for (Session eachUser : users) {
					eachUser.getBasicRemote().sendText(message);
				}
			}
		} catch (IOException e) {
			System.out.println("ERROR: Server cannot send notifications.");
		}
	}

	private static String convertToJson(NotificationModel notification) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			return mapper.writeValueAsString(notification);
		} catch (JsonProcessingException e) {
			System.out.println("ERROR: Notification message JSON Parsing Failed.");
		}
		return null;
	}
}
