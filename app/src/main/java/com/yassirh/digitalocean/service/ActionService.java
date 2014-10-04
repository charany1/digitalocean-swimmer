package com.yassirh.digitalocean.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.support.v4.app.NotificationCompat;

import com.loopj.android.http.AsyncHttpResponseHandler;
import com.loopj.android.http.SyncHttpClient;
import com.yassirh.digitalocean.R;
import com.yassirh.digitalocean.data.DatabaseHelper;
import com.yassirh.digitalocean.data.DropletDao;
import com.yassirh.digitalocean.model.Account;
import com.yassirh.digitalocean.model.Action;
import com.yassirh.digitalocean.utils.ApiHelper;

import org.apache.http.Header;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
import java.util.HashSet;
import java.util.Set;

public class ActionService {

	private Context context;
	private Thread t;

	public ActionService(Context context) {
		this.context = context;
	}

	public void trackActions(){
		final Account currentAccount = ApiHelper.getCurrentAccount(context);
		if(currentAccount == null || t != null){
			return;
		}
		final String url = String.format("%s/actions", ApiHelper.API_URL); 
		t = new Thread(new Runnable() {
			NotificationManager notifyManager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
			NotificationCompat.Builder builder;
			Set<Integer> shownNotifications = new HashSet<Integer>();
			@Override
			public void run() {
				for (;;) {
					try {
                        SyncHttpClient client = new SyncHttpClient();
						client.addHeader("Authorization", String.format("Bearer %s", currentAccount.getToken()));
						client.get(url, new AsyncHttpResponseHandler() {

                            @Override
                            public void onSuccess(int statusCode, Header[] headers, byte[] responseBody) {
                                try {
                                    DropletDao dropletDao = new DropletDao(DatabaseHelper.getInstance(context));
                                    JSONObject jsonObject = new JSONObject(new String(responseBody));
                                    JSONArray actionsJSONArray = jsonObject.getJSONArray("actions");
                                    for(int i = 0; i < actionsJSONArray.length(); i++){
                                        JSONObject actionJSONObject = actionsJSONArray.getJSONObject(i);
                                        Action action = jsonObjectToAction(actionJSONObject);
                                        if(action.getStatus().equals("in-progress")){
                                            if(!shownNotifications.contains((int)action.getId())){
                                                builder = new NotificationCompat.Builder(context);
                                                if(action.getResourceType().equals("droplet")){
                                                    builder.setContentTitle(dropletDao.findById(action.getResourceId()).getName())
                                                            .setContentText(action.getType() + " - in progress")
                                                            .setSmallIcon(R.drawable.ic_launcher);
                                                    builder.setProgress(0, 0, true);
                                                }

                                                builder.setContentIntent(PendingIntent.getActivity(context,0,new Intent(),PendingIntent.FLAG_UPDATE_CURRENT));
                                                notifyManager.notify((int)action.getId(), builder.build());
                                                shownNotifications.add((int)action.getId());
                                            }
                                        }
                                        else{
                                            if(shownNotifications.contains((int)action.getId())){
                                                new DropletService(context).getAllDropletsFromAPI(false);
                                                shownNotifications.remove((int)action.getId());
                                            }
                                            notifyManager.cancel((int)action.getId());
                                        }
                                    }
                                } catch (JSONException e) {
                                    e.printStackTrace();
                                }
                            }

                            @Override
                            public void onFailure(int statusCode, Header[] headers, byte[] responseBody, Throwable error) {
                                if(statusCode == 401){
                                    ApiHelper.showAccessDenied();
                                }
                            }
                        });
						Thread.sleep(5000);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}
				}
			}
		});
		t.start();
	}
	
	

	private Action jsonObjectToAction(JSONObject actionJSONObject) {
		Action action = new Action();
		try {
			action.setId(actionJSONObject.getLong("id"));
			action.setStatus(actionJSONObject.getString("status"));
			action.setType(actionJSONObject.getString("type"));
			action.setResourceId(actionJSONObject.getLong("resource_id"));
			action.setResourceType(actionJSONObject.getString("resource_type"));
			action.setRegion(actionJSONObject.getString("region"));
			if(!actionJSONObject.getString("started_at").equals("null")){
				action.setStartedAt(ApiHelper.iso8601Format.parse(actionJSONObject.getString("started_at").replace("Z", "")));
			}
			if(!actionJSONObject.getString("completed_at").equals("null")){
				action.setCompletedAt(ApiHelper.iso8601Format.parse(actionJSONObject.getString("completed_at").replace("Z", "")));
			}
		} catch (ParseException e) {
			e.printStackTrace();
		} catch (JSONException e) {
			e.printStackTrace();
		}
		return action;
	}
}
