package android_serialport_api;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.security.InvalidParameterException;

import android.fpi.MtGpio;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;

public class SerialPortManager {

	private static SerialPortManager mSerialPortManager = new SerialPortManager();
	
	//UART 3G
	private static final String UARTPATH3G = "/dev/ttyMT3";
	private static final int BAUDRATE = 460800;
	//UART 4G
	private static final String UARTPATH4G = "/dev/ttyHSL1";
	//SPI
	private static final String SPIPATH = "/dev/spidev0.0";
	private static final int Speed =  2000 * 1000;
	private static final int Mode=1;
	//Mode
	private int mDeviceType=0; 
		
	private SerialPort mSerialPort = null;
	private boolean isOpen;	
	private boolean firstOpen = false;

	private OutputStream mOutputStream;
	private InputStream mInputStream;
	private byte[] mBuffer = new byte[128 * 1024];
	private int mCurrentSize = 0;
	private Looper looper;
	private HandlerThread ht;
	private ReadThread mReadThread;
	
	private boolean bCancel=false;
	private AsyncFingerprint asyncFP=null;
	
	
	public AsyncFingerprint getNewAsyncFingerprint() {
		if (!isOpen) {
			try {
				openSerialPort();
				isOpen = true;
			} catch (InvalidParameterException e) {
				e.printStackTrace();
			} catch (SecurityException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			Cancel(false);
			asyncFP= new AsyncFingerprint(looper);
			asyncFP.Cancel(false);
			Log.i("xpb", "Open Serial");
			return asyncFP;
		}
		return asyncFP;
		//return new AsyncFingerprint(looper);		
	}

	public SerialPortManager() {
	}

	public static SerialPortManager getInstance() {
		return mSerialPortManager;
	}

	public boolean isOpen() {
		return isOpen;
	}
	
	public boolean isFirstOpen() {
		return firstOpen;
	}

	public void setFirstOpen(boolean firstOpen) {
		this.firstOpen = firstOpen;
	}

	private void createWorkThread() {
		ht = new HandlerThread("workerThread");
		ht.start();
		looper = ht.getLooper();
	}

	public void openSerialPort() throws SecurityException, IOException,
			InvalidParameterException {
		if (mSerialPort == null) {
			/* Open the serial port */
			mSerialPort = new SerialPort();			
			if(mSerialPort.getmodel().equals("b82")||mSerialPort.getmodel().equals("FP07")){
				//setUpGpio();				
				mSerialPort.OpenDevice(new File(SPIPATH), Speed, Mode,SerialPort.DEVTYPE_SPI);
				mDeviceType=SerialPort.USETYPE_SPI;
				Log.i("xpb", "SPI Mode");
				setUpGpio();
				SystemClock.sleep(500);
			}else{
				if(mSerialPort.getmodel().equals("M9PLUS")){
					mDeviceType=SerialPort.USETYPE_UART4G;
					setUpGpio();
					SystemClock.sleep(500);
					mSerialPort.OpenDevice(new File(UARTPATH4G), BAUDRATE, 0,SerialPort.DEVTYPE_UART);
					Log.i("xpb", "UART Mode 4G");
				}else{
					mDeviceType=SerialPort.USETYPE_UART3G;
					setUpGpio();
					SystemClock.sleep(100);
					mSerialPort.OpenDevice(new File(UARTPATH3G), BAUDRATE, 0,SerialPort.DEVTYPE_UART);
					Log.i("xpb", "UART Mode 3G");
				}
			}
			mOutputStream = mSerialPort.getOutputStream();
			mInputStream = mSerialPort.getInputStream();			
			
			if((mDeviceType==SerialPort.USETYPE_UART3G)||(mDeviceType==SerialPort.USETYPE_UART4G)){
				mReadThread = new ReadThread();
				mReadThread.start();
			}
			isOpen = true;
			createWorkThread();
			firstOpen = true;
		}
	}
	
	public void PowerControl(boolean sw){
		if(sw){
			try {
				setUpGpio();
			} catch (IOException e) {
			}
		}else{
			try {
				setDownGpio();
			} catch (IOException e) {
			}
		}
	}

	public void closeSerialPort() {
		Cancel(true);			
		asyncFP.Cancel(true);
		if (ht != null) {
			ht.quit();
		}
		ht = null;
		if((mDeviceType==SerialPort.USETYPE_UART3G)||(mDeviceType==SerialPort.USETYPE_UART4G)){
			SystemClock.sleep(200);
		}else{
			SystemClock.sleep(1000);
		}
		
		if (mReadThread != null)
			mReadThread.interrupt();
		mReadThread = null;
		try {
			setDownGpio();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
		if (mSerialPort != null) {
			try {
				mOutputStream.close();
				mInputStream.close();
			} catch (IOException e) {
				e.printStackTrace();
			} 
			mSerialPort.close();
			mSerialPort = null;
		}
		isOpen = false;
		mCurrentSize = 0;
		
		Log.i("xpb", "Close Serial");
	}
	
	private boolean checkCmdTag(byte[] data){
		for(int i=0;i<data.length-4;){
			if(((byte)data[i]==(byte)(0xEF))&&
					((byte)data[i+1]==(byte)(0x01))&&
					((byte)data[i+2]==(byte)(0xFF))&&
					((byte)data[i+3]==(byte)(0xFF))
					){
				return true;
			}
		}
		return false;
	}
	
	protected /*synchronized*/ int read(byte buffer[],int size,int waittime) {
		if(bCancel){
			Log.i("xpb", "Cancel=" + String.valueOf(bCancel));	  
			return 0;
		}
		
		if((mDeviceType==SerialPort.USETYPE_UART3G)||(mDeviceType==SerialPort.USETYPE_UART4G)){
			int time = 4000;
			int sleepTime = 50;
			int length = time / sleepTime;
			boolean shutDown = false;
			int[] readDataLength = new int[3];
			for (int i = 0; i < length; i++) {
				if (mCurrentSize == 0) {
					SystemClock.sleep(sleepTime);
					continue;
				} else {
					break;
				}
			}

			if (mCurrentSize > 0) {
				while (!shutDown) {
					SystemClock.sleep(sleepTime);
					readDataLength[0] = readDataLength[1];
					readDataLength[1] = readDataLength[2];
					readDataLength[2] = mCurrentSize;
					Log.i("whw", "read2    mCurrentSize=" + mCurrentSize);
					if (readDataLength[0] == readDataLength[1]
							&& readDataLength[1] == readDataLength[2]) {
						shutDown = true;
					}
				}
				if (mCurrentSize <= buffer.length) {
					System.arraycopy(mBuffer, 0, buffer, 0, mCurrentSize);
				}
			}
			return mCurrentSize;
		}else
		{
			mCurrentSize=0;
			SystemClock.sleep(waittime);

			byte[] revbuf = new byte[150];
			int n=(size/139+1);
			for(int t=0;t<n;t++){
				if(bCancel){
					Log.i("xpb", "Cancel=" + String.valueOf(bCancel));
					return 0;
				}
				try {
					SystemClock.sleep(2);
					mInputStream.read(revbuf);
					if(checkCmdTag(revbuf)){
						System.arraycopy(revbuf, 0, mBuffer, mCurrentSize,revbuf.length);
						mCurrentSize += revbuf.length;
					}
				} catch (IOException e) {
				}
			}
			//Log.i("xpb", "revsize=" + String.valueOf(mCurrentSize));	
			//Log.i("xpb", "revdata=" + DataUtils.toHexString(mBuffer,0,mCurrentSize));
			int ret=0;
			for(int i=0;i<mCurrentSize-4;){
				if(bCancel){
					Log.i("xpb", "Cancel=" + String.valueOf(bCancel));
					return 0;
				}
				if(((byte)mBuffer[i]==(byte)(0xEF))&&
						((byte)mBuffer[i+1]==(byte)(0x01))&&
						((byte)mBuffer[i+2]==(byte)(0xFF))&&
						((byte)mBuffer[i+3]==(byte)(0xFF))
						){
					int pkgsize=(int)(mBuffer[i+8])+((int)(mBuffer[i+7]<<8)&0xFF00)+9;
					if(pkgsize==-117)
						pkgsize=139;
					System.arraycopy(mBuffer, i, buffer, ret,pkgsize);
					ret=ret+pkgsize;
					//Log.i("xpb", "pkgsize=" + String.valueOf(pkgsize));
					//Log.i("xpb", "pkgdata=" + DataUtils.toHexString(mBuffer,i,pkgsize));
					i=ret;
				}else{
					i++;
				}				
			}
		   	//Log.i("xpb", "size=" + String.valueOf(ret));	   	
		   	return ret;
		}		
	}
	
	protected /*synchronized*/ void write(byte[] data) throws IOException {
		if((mDeviceType==SerialPort.USETYPE_UART3G)||(mDeviceType==SerialPort.USETYPE_UART4G)){
			mCurrentSize=0;
			mOutputStream.write(data);
		}else{
			if(bCancel){
				Log.i("xpb", "Cancel=" + String.valueOf(bCancel));
				return;
			}
			byte[] tmp=new byte[150];
			System.arraycopy(data,0, tmp, 0, data.length);
			mOutputStream.write(tmp);
		}
	}

	private void setUpGpio() throws IOException {
		switch(mDeviceType){
		case SerialPort.USETYPE_UART3G:
			MtGpio mt=new MtGpio();
			mt.FPPowerSwitch(true);
			return;
		case SerialPort.USETYPE_UART4G:
			IoControl(true);
			return;
		case SerialPort.USETYPE_SPI:
			mSerialPort.PowerSwitch(true);
			return;
		}		
	}

	private void setDownGpio() throws IOException {
		switch(mDeviceType){
		case SerialPort.USETYPE_UART3G:
			MtGpio mt=new MtGpio();
			mt.FPPowerSwitch(false);
			return;
		case SerialPort.USETYPE_UART4G:
			IoControl(false);
			return;
		case SerialPort.USETYPE_SPI:
			mSerialPort.PowerSwitch(false);
			return;
		}		
	}

	private class ReadThread extends Thread {

		@Override
		public void run() {
			while (!isInterrupted()) {
				int length = 0;
				try {
					byte[] buffer = new byte[100];
					if (mInputStream == null)
						return;
					length = mInputStream.read(buffer);
					if (length > 0) {
						System.arraycopy(buffer, 0, mBuffer, mCurrentSize,
								length);
						mCurrentSize += length;
					}
					Log.i("whw", "mCurrentSize=" + mCurrentSize + "  length="
							+ length);
				} catch (IOException e) {
					e.printStackTrace();
					return;
				}
			}
		}
	}

	public void Cancel(boolean sw) {
		bCancel=sw;
	}
	
	public boolean WriteIoFile(String strValue,String Path){
		File file;
		FileOutputStream outstream;
		try{
			file = new File(Path);
			outstream = new FileOutputStream(file);
			outstream.write(strValue.getBytes());
			outstream.close();
		} catch(FileNotFoundException e){
			return false;
		} catch(IOException e){
			return false;
		}
		return true;
	}
	
	public void IoControl(boolean bOpen){
		//String GPIO_DIR ="/sys/devices/soc.0/scan_se955.69/";
		String GPIO_DIR ="/sys/devices/soc.0/scan_se955.71/";
		String[] GPIO_FILE={"start_scan",
							"power_status"};
		if(bOpen){
			WriteIoFile("on",GPIO_DIR+GPIO_FILE[0]);
			WriteIoFile("on",GPIO_DIR+GPIO_FILE[1]);
		}else{
			WriteIoFile("off",GPIO_DIR+GPIO_FILE[0]);
			WriteIoFile("off",GPIO_DIR+GPIO_FILE[1]);
		}
	}

}
