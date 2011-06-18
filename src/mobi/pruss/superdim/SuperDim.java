package mobi.pruss.superdim;

import java.io.*;

import mobi.pruss.superdim.R;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface.*;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.view.Display;
import android.view.Menu;
import android.view.MenuItem;
import android.view.ContextMenu;  
import android.view.ContextMenu.ContextMenuInfo;  
import android.view.Surface;
import android.content.pm.ActivityInfo;

public class SuperDim extends Activity {
	private static final String backlightFile="/sys/class/leds/lcd-backlight/brightness";
	private static final String powerLEDFile="/sys/class/leds/power/brightness";
	private static final String cf3dNightmode="persist.cf3d.nightmode";
	private DataOutputStream rootCommands = null;
	private Process rootShell;
	private static final int BREAKPOINT_BAR = 3000;
	private static final int BREAKPOINT_BRIGHTNESS = 30;
	private static final int MAX_BAR = 10000;
	private static final int NIGHTMODE = 1;
	private static final int NIGHTMODE_DISABLED = 1000;
	private static final int NIGHTMODE_RED = 1001;
	private static final int NIGHTMODE_GREEN = 1002;
	private static final int NIGHTMODE_BLUE = 1003;
	private static final int NIGHTMODE_AMBER = 1004;
	private static final int NIGHTMODE_SALMON = 1005;
	private static final String defaultNightmode[] = { "disabled", "red", "green", "green", "disabled" };
	private static final int defaultBacklight[] = { 50, 10, 50, 255, 255 };
	private static final int defaultPowerLED[] = { 255, 255, 255, 255, 255 };
	private SeekBar barControl;
	private TextView currentValue;
	private Resources res;
	private boolean haveCF3D;
	public static final String CUSTOM_PREFIX = "custom_";
	
	private int toBrightness(int bar) {
		if (BREAKPOINT_BAR<=bar) {
			return (bar-BREAKPOINT_BAR)*(255-BREAKPOINT_BRIGHTNESS)/(MAX_BAR-BREAKPOINT_BAR)
				+BREAKPOINT_BRIGHTNESS;			
		}
		else {
			return 1 + bar*(BREAKPOINT_BRIGHTNESS-1)/BREAKPOINT_BAR;
		}
	}
	
	private int toBar(int brightness) {
		if (BREAKPOINT_BRIGHTNESS<=brightness) {
			return (brightness-BREAKPOINT_BRIGHTNESS)*(MAX_BAR-BREAKPOINT_BAR)/(255-BREAKPOINT_BRIGHTNESS)
				+ BREAKPOINT_BAR;
		}
		else {
			return (brightness-1)*BREAKPOINT_BAR / (BREAKPOINT_BRIGHTNESS-1);
		}
	}
	
	private boolean testRoot() {
		try {
			Process p = Runtime.getRuntime().exec("su");
			DataOutputStream out = new DataOutputStream(p.getOutputStream());
			out.close();
			if(p.waitFor() != 0) {
				return false;
			}
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}

	private boolean initRoot() {
		try {
			rootShell = Runtime.getRuntime().exec("su");
			rootCommands = new DataOutputStream(rootShell.getOutputStream());
			return true;
		}
		catch (Exception e) {
			return false;
		}
	}
	
	private void closeRoot() {
		if (rootCommands != null) {
			try {
				rootCommands.close();
			}
			catch (Exception e) {
			}
		}
	}
	
	private void setNightmode(String s) {
		try {
			rootCommands.writeBytes("setprop " + cf3dNightmode + " "+s+"\n");
			rootCommands.flush();			
		}
		catch (Exception e) {
			Log.e("Error","setting nightmode "+s);
		}
	}
	
	private void setBrightness(String file, boolean setSystem, int n) {
		if (n<0)
			n = 0;
		else if (n>255)
			n = 255;
		
		if (setSystem) {
			android.provider.Settings.System.putInt(getContentResolver(),
				     android.provider.Settings.System.SCREEN_BRIGHTNESS,
				     n);
		}
		
		try {
			rootCommands.writeBytes("echo "+n+" >\""+file+"\"\n");
			rootCommands.flush();
		}
		catch (Exception e) {
			Log.e("Error","setting "+n);
		} 
	}
	
	public void nightmodeOnClick(View v) {
		v.showContextMenu();
	}

	public void setValueOnClick(View v) {
		int newValue;
		switch(v.getId()) {
		case R.id.min:
			newValue = 1;
			break;
		case R.id.percent_25:
			newValue = 1 + 256 / 4;
			break;
		case R.id.percent_50:
			newValue = 1 + 256 / 2;
			break;
		case R.id.percent_75:
			newValue = 1 + 3 * 256 / 4;
			break;
		case R.id.percent_100:
			newValue = 255;
			break;
		default:
			return;
		}

		setBrightness(backlightFile, true, newValue);
        barControl.setProgress(toBar(newValue));
	}
		
	public void powerLEDOnClick(View v) {
		int b = getBrightness(powerLEDFile);
		
		if (b<0)
			return;
		
		setBrightness(powerLEDFile, false, b==0 ? 255 : 0);
	}
	
	private int getBrightness(String file) {
		try {
			FileInputStream stream = new FileInputStream(file);
			byte[] buf = new byte[12];
			String s;
			
			int numRead = stream.read(buf);
			
			stream.close();
			
			if(0 < numRead) {
				s = new String(buf, 0, numRead);
				
				return Integer.parseInt(s.trim());
			}
			else {
				return -1;
			}
		}
		catch (Exception e) {
			return -1;
		}
	}
	
	private String getNightmode() {
		try {
			Process p = Runtime.getRuntime().exec("getprop "+cf3dNightmode);
			DataInputStream stream = new DataInputStream(p.getInputStream());
			byte[] buf = new byte[12];
			String s;
			
			int numRead = stream.read(buf);
			if(p.waitFor() != 0) {
				return null;
			}			
			stream.close();
			
			if(0 < numRead) {
				s = new String(buf, 0, numRead);
				
				return s.trim();
			}
			else {
				return null;
			}
		}
		catch (Exception e) {
			return null;
		}
	}
	
	private void redraw() {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        res = getResources();
        
        alertDialog.setTitle("Changing night mode");
        alertDialog.setMessage("Please press OK.");
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, 
        		res.getText(R.string.ok), 
        	new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {} });
        alertDialog.show(); 
	}
	
	private void fatalError(int title, int msg) {
        AlertDialog alertDialog = new AlertDialog.Builder(this).create();
        res = getResources();
        
        Log.e("fatalError", (String) res.getText(title));

        alertDialog.setTitle(res.getText(title));
        alertDialog.setMessage(res.getText(msg));
        alertDialog.setButton(DialogInterface.BUTTON_POSITIVE, 
        		res.getText(R.string.ok), 
        	new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {finish();} });
        alertDialog.setOnCancelListener(new DialogInterface.OnCancelListener() {
            public void onCancel(DialogInterface dialog) {finish();} });
        alertDialog.show();		
	}
	
	private SharedPreferences getCustomPreferences(int n) {
		if (n < 0)
			return null;
		return getSharedPreferences(CUSTOM_PREFIX+n, 0); 
	}
	
	private int getCustomNumber(View v) {
		switch(v.getId()) {
		case R.id.custom0:
			return 0;
		case R.id.custom1:
			return 1;
		case R.id.custom2:
			return 2;
		case R.id.custom3:
			return 3;
		case R.id.custom4:
			return 3;
		default:
			return -1;
		}
	}
	
	public void customLoad(View v) {		
		int	n = getCustomNumber(v);
		if (n<0)
			return;
		SharedPreferences pref = getCustomPreferences(n);
		
		if (pref == null)
			return;
		
		int b = pref.getInt("backlight", defaultBacklight[n]);
		setBrightness( backlightFile, true, b);		
        barControl.setProgress(toBar(b));
		setBrightness( powerLEDFile, false, pref.getInt("powerLED", defaultPowerLED[n]));		
		
		if (haveCF3D) {
			String oldNM = getNightmode();
			String nm = pref.getString("nightmode", defaultNightmode[n]);
			if (! nm.equals(oldNM)) {
				setNightmode(nm);
				redraw();
			}
		}
	}
	
	private void customSave(View v) {
		int	n = getCustomNumber(v);
		if (n<0)
			return;
		SharedPreferences pref = getCustomPreferences(n);
		
		SharedPreferences.Editor ed = pref.edit();
		
		if (haveCF3D) {
			String nm = getNightmode();
			if ( nm != null)
				ed.putString("nightmode", nm);
		}
		
		ed.putInt("backlight", getBrightness(backlightFile));
		ed.putInt("powerLED", getBrightness(powerLEDFile));
		ed.commit();
		Toast.makeText(getApplicationContext(), "Saved!", Toast.LENGTH_SHORT).show();
	}
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    	Button  nightmodeButton;
        
        Log.v("SuperDim", "entering");
        String nm = getNightmode();
        haveCF3D = (nm != null);
        Log.v("cf3d",haveCF3D?nm:"(none)");

        setContentView(R.layout.main);
        
        if (getBrightness(backlightFile)<0) {
        	fatalError(R.string.incomp_device_title, R.string.incomp_device);
        	return;
        }
        
        if (!testRoot()) {
        	fatalError(R.string.need_root_title, R.string.need_root);
        	return;
        }
        
        initRoot();

        nightmodeButton = (Button)findViewById(R.id.nightmode);
        if (! haveCF3D) {
        	nightmodeButton.setVisibility(View.GONE);
        }
        else {
        	registerForContextMenu(nightmodeButton);
        }
        
        Button.OnLongClickListener customSaveListener = 
        	new Button.OnLongClickListener() {

				@Override
				public boolean onLongClick(View v) {
					
					customSave(v);
					return false;
				}
        };
        
        ((Button)findViewById(R.id.custom0)).setOnLongClickListener(customSaveListener);
        ((Button)findViewById(R.id.custom1)).setOnLongClickListener(customSaveListener);
        ((Button)findViewById(R.id.custom2)).setOnLongClickListener(customSaveListener);
        ((Button)findViewById(R.id.custom3)).setOnLongClickListener(customSaveListener);
        ((Button)findViewById(R.id.custom4)).setOnLongClickListener(customSaveListener);
        

        currentValue = (TextView)findViewById(R.id.current_value);
        barControl = (SeekBar)findViewById(R.id.brightness);

        SeekBar.OnSeekBarChangeListener seekbarListener = 
        	new SeekBar.OnSeekBarChangeListener() {
				
				@Override
				public void onStopTrackingTouch(SeekBar seekBar) {
					// TODO Auto-generated method stub
					
				}
				
				@Override
				public void onStartTrackingTouch(SeekBar seekBar) {
					// TODO Auto-generated method stub
					
				}
				
				@Override
				public void onProgressChanged(SeekBar seekBar, int progress,
						boolean fromUser) {
//	        		Log.v("Set", progress+" "+toBrightness(progress));
					currentValue.setText(""+toBrightness(progress)+"/255");
					setBrightness(backlightFile, true, toBrightness(progress));					
				}
			};
    
        barControl.setOnSeekBarChangeListener(seekbarListener);
        
        barControl.setProgress(toBar(getBrightness(backlightFile)));
    }
    
    @Override
    public void onRestart() {
    	super.onRestart();
    	initRoot();
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    	closeRoot();
    }
    
    @Override
    public boolean onContextItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    	case NIGHTMODE_DISABLED:
    		setNightmode("disabled");
    		return true;
    	case NIGHTMODE_RED:
    		setNightmode("red");
    		return true;
    	case NIGHTMODE_GREEN:
    		setNightmode("green");
    		return true;
    	case NIGHTMODE_BLUE:
    		setNightmode("blue");
    		return true;
    	case NIGHTMODE_AMBER:
    		setNightmode("amber");
    		return true;
    	case NIGHTMODE_SALMON:
    		setNightmode("salmon");
    		return true;
    	default:
    		return false;
    	}
    }
    
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenuInfo menuInfo) {
    	super.onCreateContextMenu(menu, v, menuInfo);
    	switch(v.getId()) {
    	case R.id.nightmode:
    		menu.add(NIGHTMODE, NIGHTMODE_DISABLED, Menu.NONE, R.string.nightmode_disabled);
    		menu.add(NIGHTMODE, NIGHTMODE_RED, Menu.NONE, R.string.nightmode_red);
    		menu.add(NIGHTMODE, NIGHTMODE_GREEN, Menu.NONE,R.string.nightmode_green);
    		menu.add(NIGHTMODE, NIGHTMODE_BLUE, Menu.NONE, R.string.nightmode_blue);
    		menu.add(NIGHTMODE, NIGHTMODE_AMBER, Menu.NONE, R.string.nightmode_amber);
    		menu.add(NIGHTMODE, NIGHTMODE_SALMON, Menu.NONE, R.string.nightmode_salmon);
    		break;
    	default:
    		break;
    	}
    }
}
