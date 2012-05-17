package com.findafountain;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.GestureDetector.OnDoubleTapListener;
import android.view.GestureDetector.OnGestureListener;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.widget.Toast;

import com.google.android.maps.GeoPoint;
import com.google.android.maps.MapActivity;
import com.google.android.maps.MapView;
import com.google.android.maps.MyLocationOverlay;
import com.google.android.maps.Overlay;

/**
 * The main map activity of this application.
 * @author Joel
 */
public class MainActivity extends MapActivity implements OnDoubleTapListener, OnGestureListener
{
	private static String TAG = "MainActivity";
	
	private MyMapView mapView;
	
	//Keeps track of the user's location
	private MyLocationOverlay myLocationOverlay;
	//Handles polling for changes in the zoom level
	private ZoomHandler zoomHandler;
	//Handles the polling for panning/dragging changes
	private PanHandler panHandler;
	
	//Whether or not the UI is drawing the fountains via the async task
	//This will help prevent another signaling for drawing
	private boolean isDrawingFountains;
	
	//The current drawable to be used for overlay items.
	//Changes bassed on the desired fountain status to be drawn
	private Drawable currentDrawable;
	
	//A customized list of drinkable/blue map overlays
	//We have only a single overlay that gets added to as the user pans.
	//In turn, we can keep already drawn overlays.
	private CustomItemizedOverlay currentItemizedOverlay;
	
	//TODO: Create the DBAdapter instance in the Application subclass.
	public DBAdapter dbAdapter;

	//Used to determine the bounds of the viewing rectangle for lazy-loading
	private LongLat topLeft, topRight, botLeft, botRight;
	
	//The activity's action bar.
	//Note: This needs to be accessible within all subclasses.
	private MyActionBar actionBar;
	
	private static final int INITIAL_ZOOM_LEVEL = 17;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.mainactivity);
        	
        //Init the class' objects
        Initialize();
         
        //Simulate a clicking of the actionbar refresh button
	    actionBar.new RefreshAction().performAction(mapView);        
    }
    
	@Override
	protected void onPause()
	{
    	super.onPause();
    	//When the application is paused, turn off the location tracking
		myLocationOverlay.disableMyLocation();
		myLocationOverlay.disableCompass();
		//Stop polling
		panHandler.removeCallbacks();
		zoomHandler.removeCallbacks();
	}

	@Override
	protected void onResume()
	{
		super.onResume();		
		//When the application comes into the foreground, turn location tracking on
		myLocationOverlay.enableMyLocation();
		myLocationOverlay.enableCompass();
		//Register the handlers
		zoomHandler.postDelayed();
		panHandler.postDelayed();
		
		//Draw the fountains after all objects are initialized.
		//This draws markers in viewing range while the async task polls the server.
		DrawFountainLocations();
	}
		
	@Override
	protected boolean isRouteDisplayed()
	{ return false; }
		
	/**
	 * Sets up the mapView, mapController, and other objects.
	 */
    private void Initialize()
    {    
        //Create an instance of our custom map view and set attributes
        mapView = (MyMapView) findViewById(R.id.mymapview);
        mapView.setBuiltInZoomControls(true);			
		mapView.getController().setZoom(INITIAL_ZOOM_LEVEL);
		
		//Draw the user's current location
		CreateMyLocation();
		
		//Set the drawing flag
		isDrawingFountains = false;
		
		//Create the handler that maintains the changing zoom level overlay renderings
		zoomHandler = new ZoomHandler(mapView, new Handler(){
			public void handleMessage(Message msg){
				super.handleMessage(msg);
				switch (msg.what){
					//If we zoomed out, then redraw the fountains. Zooming in just means we're seeing what we've already drawn
					case ZoomHandler.Actions.ZOOM_OUT:
						DrawFountainLocations();		
						Log.d(TAG, "zoomMessageHandler: Zoom Out Triggered Redraw!");
						break;
					case ZoomHandler.Actions.ZOOM_IN:
						DrawFountainLocations();		
						Log.d(TAG, "zoomMessageHandler: Zoom In Triggered Redraw!");
						break;
				}
			}
		});
		
		panHandler = new PanHandler(mapView, new Handler(){
			public void handleMessage(Message msg) {
				super.handleMessage(msg);
				switch (msg.what) {
					//The pan finished
					case 1:
						DrawFountainLocations();
						Log.d(TAG, "panMessageHandler: PanHandling Complete!");
						break;
				}
			}
		});

		//Handles all incoming messages from the action bar
		Handler actionbarMessageHandler = new Handler(){
			public void handleMessage(Message msg){
				super.handleMessage(msg);
				switch (msg.what) {
					case MyActionBar.Actions.REFRESH:
						if(msg.arg1 == -1){
							Toast.makeText(MainActivity.this, "Sorry, try refreshing later.", Toast.LENGTH_LONG).show();
							Log.d(TAG, "actionbarMessageHandler: Refresh error handled!");
						} else {
							DrawFountainLocations();
							Log.d(TAG, "actionbarMessageHandler: Refresh Message Handled!");
						}
						break;
					case MyActionBar.Actions.ADD:
						break;
					case MyActionBar.Actions.MY_LOCATION:
						//Animate to the user's last known location
						GeoPoint myLoc = myLocationOverlay.getMyLocation();
						//Make sure the location was found.
						//If the GPS signal isn't found, then the app bombs without this check
						if(myLoc != null){
							mapView.getController().animateTo(myLoc);
							mapView.getController().setZoom(INITIAL_ZOOM_LEVEL);
							Log.d(TAG, "actionbarMessageHandler: MyLocation message handled!");
						} else if(myLoc == null){
							Toast.makeText(MainActivity.this, "Your location is currently unavailable!", Toast.LENGTH_SHORT).show();
							Log.e(TAG, "actionbarMessageHandler: My location is null. Problem finding location.");
						}
						break;
				}
			}
		};
		
		//Initialize the database adapter for all database operations
		dbAdapter = new DBAdapter(this);
		if(dbAdapter == null){
			Log.e(TAG, "Initialize: dbAdapter not created!");
			return;
		}
		Log.d(TAG, "Initialize: dbAdapter Created.");
		
		//Create the action bar
		actionBar = (MyActionBar) findViewById(R.id.myactionbar);
		actionBar.Initialize(dbAdapter, actionbarMessageHandler);
		Log.d(TAG, "Initialize: Actionbar Created.");
					
		Handler itemizedOverlayHandler = new Handler(){
			public void handleMessage(Message msg){
				super.handleMessage(msg);
				switch (msg.what) {
					case CustomItemizedOverlay.ZOOM_TOO_FAR:
						Toast.makeText(MainActivity.this, "You zoomed out too far!", Toast.LENGTH_SHORT).show();
						//When you zoom out too far, no fountains are shown
						mapView.getController().setZoom(INITIAL_ZOOM_LEVEL);
			        	currentItemizedOverlay.resetZoomMsgFlag();
						break;
				}
			}
		};
		//Default drawable is drinkable. Don't delete as Drawing uses this too.
		currentDrawable = (Drawable) this.getResources().getDrawable(R.drawable.drinkable);
		Log.d(TAG, "Initialize: Current Drawable Created.");
		currentItemizedOverlay = new CustomItemizedOverlay(currentDrawable, mapView, itemizedOverlayHandler);		
		Log.d(TAG, "Initialize: CustomItemizedOverlay Created.");
		
		//Init the viewing bounds
		topLeft = new LongLat();
		topRight = new LongLat();
		botLeft = new LongLat();
		botRight = new LongLat();
		
		Log.d(TAG, "Initialize: Initialization Complete!");
    }//end Initialize()
    
    /**
     * Sets up the MyLocationOverlay object that maintains the user's
     * current location and renders both a marker and approximation radius.
     * 
     * Note: If the location is unavailable, then a toast is shown
     */
    private void CreateMyLocation()
    {
		myLocationOverlay = new MyLocationOverlay(this, mapView);
        mapView.getOverlays().add(myLocationOverlay);
        myLocationOverlay.enableCompass();
        myLocationOverlay.enableMyLocation();
        myLocationOverlay.runOnFirstFix(new Runnable(){
        	//Purpose: Handles what occurs when the location overlay object	is first created!
            public void run(){
            	//Grab the user's best known location and move the map to it.
            	GeoPoint myLoc = myLocationOverlay.getMyLocation();
            	if(myLoc != null) mapView.getController().animateTo(myLoc);
            	else { 							
            		Toast.makeText(MainActivity.this, "Your location is currently unavailable!", Toast.LENGTH_SHORT).show();
            		Log.e(TAG, "CreateMyLocation: My location is null. Problem finding location.");
            	}
            }
        });        
        Log.d(TAG, "CreateMyLocation: MyLocationOverlay Created!");
    }
        
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mapmenu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()){
            case R.id.menu_refresh:
            	//Trigger action bar refresh press
            	actionBar.new RefreshAction().performAction(mapView);
                return true; 
            case R.id.menu_about:
            	//Create about balloon with author info
            	AboutBalloonOverlayView aboutBalloon = AboutBalloonOverlayView.getInstance(this, 0);
            	//Make the balloon visible since it might have been closed
            	aboutBalloon.setVisibility(View.VISIBLE);
            	//Set layout parameters including where to show the balloon
                MapView.LayoutParams params = new MapView.LayoutParams(
    					LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, myLocationOverlay.getMyLocation(),
    					MapView.LayoutParams.BOTTOM_CENTER);
                aboutBalloon.setLayoutParams(params);
    			params.mode = MapView.LayoutParams.MODE_MAP;
    			//Remove the balloon from the map view to avoid duplicates
    			mapView.removeView(aboutBalloon);
    			mapView.addView(aboutBalloon, params);
    			//Zoom to the location of the balloon
                actionBar.new MyLocationAction().performAction(mapView);
            	return true;
        } 
        return false;
    }

    
    /**
     * Lazy-loads fountain locations within an enlarged viewing range, creating markers,
     * and adding them to a custom fountain itemized (list of markers) overlay.
     * 
     * Notes: 
     * 	We impose a cache of 75 overlay items before clearing and redrawing
     * 		the cache. This allows us to prevent redrawing existing fountains; however,
     * 		since we can't smoothly show hundreds of overlays, we limit the number
     * 		of reused overlay items to 75.
     */
    public void DrawFountainLocations()
	{
	  	//If either or the mapview's dimensions is zero, don't draw anything. 
		int mapHeight = mapView.getHeight(), mapWidth = mapView.getWidth();
    	if(mapHeight == 0 && mapWidth == 0){
    		Log.e(TAG, "DrawFountainLocations: MapView Dimensions are zeros!");
    		return;
    	}
			
	  	//If we're already drawing, then leave! Restricts other simultaneous draw calls.
	  	if(isDrawingFountains){
	  		Log.d(TAG, "DrawFountainLocations: Exiting to prevent redraw!");
  			return;
  		} else {
  			isDrawingFountains = true;
  			Log.d(TAG, "DrawFountainLocations: Drawing Initiated!");
  		}
	  	
	  	try{
			List<Overlay> mapOverlays = mapView.getOverlays();
			//Remove the current itemized overlay from the mapview since we'll repopulate it.
			mapOverlays.remove(currentItemizedOverlay);
			
			//Scales the viewing rectangle to pull more fountains and avoid overlay popping.
			//Pixel amounts for padding the edges of the viewing rectangle.
			int sw = mapWidth / 2;	//Scale width
			int sh = mapHeight / 2;	//Scale height 
			
			topLeft.initFromGeoPoint(mapView.getProjection().fromPixels(-sw, -sh));
			topRight.initFromGeoPoint(mapView.getProjection().fromPixels(mapWidth + sw, -sh));
			botLeft.initFromGeoPoint(mapView.getProjection().fromPixels(-sw, mapHeight + sh));
			botRight.initFromGeoPoint(mapView.getProjection().fromPixels(mapWidth + sw, mapHeight + sh));
			
			ArrayList<Fountain> fountains = dbAdapter.SelectFountainsInRange(topLeft, topRight, 
																			botRight, botLeft, 
																			75); //Limit
			int numFountains = fountains.size();
			Log.d(TAG, "DrawFountainLocations: " + numFountains + " fountains in range.");
			//During the fresh install, there is no data in the database, yielding 0 results
			if(numFountains == 0){
				isDrawingFountains = false;
				Log.d(TAG, "DrawFountainLocations: Drawing Terminated! No Fountains Exist for Drawing");
				return;		
			}			
			//Create fountain overlays for each fountain in range
			//ArrayList<FountainOverlayItem> overlayItems = new ArrayList<FountainOverlayItem>();
			LinkedList<FountainOverlayItem> overlayItems = new LinkedList<FountainOverlayItem>();
			
			for(int i = 0; i < numFountains; i++) 
				overlayItems.add(new FountainOverlayItem(fountains.get(i), currentDrawable));
	    		
			currentItemizedOverlay.addOverlays(overlayItems);    	
			//Add the newly populated overlay items
	    	mapOverlays.add(currentItemizedOverlay);
	    	//Force the redrawing of the mapview to avoid artifacts
	    	mapView.postInvalidate();	   
	    	Log.d(TAG, "DrawFountainLocations: Map View Redrawn!");
	    	Log.d(TAG, "DrawFountainLocations: Itemized Overlay contains " + currentItemizedOverlay.size() + " fountains!");
	  	} catch(Exception e){
	  		Log.e(TAG, "DrawFountainLocations: " + e);
	  	}
	  	
	  	//Reset the flag to allow for another draw call
    	isDrawingFountains = false;
    	Log.d(TAG, "DrawFountainLocations: Drawing Terminated.");
  	}
  
    /**
     * On a user's double tap, trigger a zoom in action
     */
	@Override
	public boolean onDoubleTap(MotionEvent e){
		mapView.getController().zoomIn();
		DrawFountainLocations();		
		Log.d(TAG, "onDoubleTap: doubleTap Zoom triggered Redraw!");
		
		return true;
	}

	@Override
	public boolean onDoubleTapEvent(MotionEvent e){
		return false;
	}

	@Override
	public boolean onSingleTapConfirmed(MotionEvent e){
		return false;
	}
	
	//OnGestureListener Methods
	@Override
	public boolean onDown(MotionEvent e) {
		return false;
	}

	@Override
	public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
		return false;
	}

	@Override
	public void onLongPress(MotionEvent e) {
	}

	@Override
	public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
		return false;
	}

	@Override
	public void onShowPress(MotionEvent e) {
	}

	@Override
	public boolean onSingleTapUp(MotionEvent e) {
		return false;
	}
}