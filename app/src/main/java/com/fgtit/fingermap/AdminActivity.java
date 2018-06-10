package com.fgtit.fingermap;


import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.EditText;

import com.fgtit.R;
import com.fgtit.app.ActivityList;

public class AdminActivity extends Activity {
	
	private EditText editText;
	
	@SuppressLint("NewApi")
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_admin);

		this.getActionBar().setDisplayHomeAsUpEnabled(true);
		
		editText=(EditText)findViewById(R.id.editText1);
		editText.setText(ActivityList.getInstance().PassWord);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.admin, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		switch(id){
		case android.R.id.home:
			this.finish();
			overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
			return true;
		case R.id.action_save:{
			ActivityList.getInstance().PassWord=editText.getText().toString();
			ActivityList.getInstance().SetConfigByVal("PassWord", editText.getText().toString());
			this.finish();
			overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
			}
			break;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override  
	public boolean onKeyDown(int keyCode, KeyEvent event) {  
	    if(keyCode == KeyEvent.KEYCODE_BACK && event.getAction() == KeyEvent.ACTION_DOWN){
	    	this.finish();
			overridePendingTransition(R.anim.in_from_left, R.anim.out_to_right);
	    	return true;  
	    }
	    return super.onKeyDown(keyCode, event);  
	}
	
}
