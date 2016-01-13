package org.eclipse.leshan.standalone.servlet;

import com.google.gson.JsonObject;

public class Bills {
	private String vehicleId;
	private long startTime;
	private long endTime;
	private String spotId;
	private boolean finished = false;
	private double rate;
	
	public Bills(String vehicleId, String spotId, double rate){
		this.vehicleId = vehicleId;
		this.spotId = spotId;
		this.startTime = System.currentTimeMillis();
		this.rate = rate;
	}
	
	public void finish(){
		finished = true;
		this.endTime = System.currentTimeMillis();
	}

	public String getSpotId() {
		return spotId;
	}
	public JsonObject getJson(){
		JsonObject json = new JsonObject();
		json.addProperty("vehicleId", vehicleId);
		json.addProperty("spotId", spotId);
		json.addProperty("rate", rate);
		if(finished){
			json.addProperty("time", endTime-startTime);
		} else {
			json.addProperty("time", System.currentTimeMillis()-startTime);
		}
		
		return json;
	}


}
