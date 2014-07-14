package com.pubnub.api;

import java.util.Iterator;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class PubnubSyncedObject extends JSONObject {
	private String lastUpdate = "0";
	private boolean state = false;
	private Callback callback;
	private String objectId;
	private boolean stale = true;

	private PubnubCore pubnub;

	PubnubCore getPubnub() {
		return pubnub;
	}

	void setPubnub(PubnubCore pubnub) {
		this.pubnub = pubnub;
	}

	private PubnubSyncedObject o = this;

	public PubnubSyncedObject(String objectId) {
		this.objectId = objectId;
	}

	public Callback getCallback() {
		return callback;
	}

	private String getStringFromJSONObject(JSONObject o, String key) {
		try {
			return o.getString(key);
		} catch (JSONException e) {
			return null;
		}
	}

	private JSONObject getJSONObjectFromJSONObject(JSONObject o, String key) {
		try {
			return o.getJSONObject(key);
		} catch (JSONException e) {
			return null;
		}
	}

	private Object getObjectFromJSONObject(JSONObject o, String key) {
		try {
			return o.get(key);
		} catch (JSONException e) {
			return null;
		}
	}

	private void applyUpdate(JSONObject o, JSONObject update) {
		try {
			String location = update.getString("location");
			String[] path = PubnubUtil.splitString(location, ".");
			String last = path[path.length - 1];
			JSONObject x = o;
			for (int i = 1; i < path.length - 1; i++) {
				String key = path[i];
				
				if (getJSONObjectFromJSONObject(x, key) == null) {
					x.put(key, new JSONObject());
				}
				x = x.getJSONObject(key);
			}
			System.out.println(x);
			if (update.getString("action").equals("update")) {
				x.put(last, update.get("value"));
			} else if (update.getString("action").equals("delete")) {
				x.remove(last);
			}
			o.put("last_update", update.getLong("timetoken"));

		} catch (JSONException e) {
			e.printStackTrace();
		}

	}

	private void applyUpdates(JSONObject o, JSONObject updates,
			Callback callback) {

		Iterator keys = updates.keys();
		try {
			while (keys.hasNext()) {
				String key = (String) keys.next();
				String action = "update";
				JSONArray updatesArray = updates.getJSONArray(key);
				for (int i = 0; i < updatesArray.length(); i++) {
					applyUpdate(o, updatesArray.getJSONObject(i));
					action = updatesArray.getJSONObject(i).getString("action");
				}
				callback.successCallback("", action);
				updates.remove(key);
			}
		} catch (JSONException e) {
			e.printStackTrace();
		}
	}

	public void setCallback(final Callback callback) {
		this.callback = callback;

		try {
			pubnub.subscribe("pn_ds_" + objectId, new Callback() {
				public void successCallback(String channel, Object response) {
					JSONObject update = (JSONObject) response;
					try {
						System.out.println(update.toString(2));
					} catch (JSONException e) {
						e.printStackTrace();
					}
					applyUpdate(o, update);
					try {
						JSONObject callbackData = new JSONObject();
						callbackData.put("location", update.getString("location"));
						callbackData.put("action", update.getString("action"));
						callback.successCallback("", callbackData);
					} catch (JSONException jse) {

					}
				}

				public void errorCallback(String channel, PubnubError error) {

				}
			});
		} catch (PubnubException e) {
			e.printStackTrace();
		}
	}

	public String getLastUpdate() {
		return lastUpdate;
	}

	public boolean isState() {
		return state;
	}

	public static JSONObject deepMerge(JSONObject target, JSONObject source) throws JSONException {
		Iterator keys = source.keys();
		while(keys.hasNext()) {
			String key = (String) keys.next();
            Object value = source.get(key);
            if (!target.has(key)) {
                target.put(key, value);
            } else {
                if (value instanceof JSONObject) {
                    JSONObject valueJson = (JSONObject)value;
                    deepMerge(valueJson, target.getJSONObject(key));
                } else {
                    target.put(key, value);
                }
            }
		}
	    return target;
	}
	
	public void sync(final Callback callback) {
		pubnub.read(objectId, "", new Callback(){
			public void successCallback(String channel, Object response) {
				try {
					deepMerge(o, (JSONObject)response);
				} catch (JSONException e) {
					callback.errorCallback("", e.toString());
				}
				o.setStale(false);
				try {
					o.put("last_update", System.nanoTime() / 100);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				callback.successCallback("", "updated");
			}
			public void errorCallback(String channel, PubnubError response) {
				o.setStale(true);
				callback.errorCallback(channel, response);
			}
		});
	}

	public boolean isStale() {
		return stale;
	}

	void setStale(boolean stale) {
		this.stale = stale;
	}
}