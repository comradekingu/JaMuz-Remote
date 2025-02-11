package phramusca.com.jamuzremote;

/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */


import static phramusca.com.jamuzremote.ActivityMain.getAppDataPath;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.system.ErrnoException;
import android.system.OsConstants;
import android.util.Log;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.sql.CallableStatement;

public class ClientReception extends ProcessAbstract {

    private static final String TAG = ClientReception.class.getName();
    private final BufferedReader bufferedReader;
    private final InputStream inputStream;
    private final IListenerReception callback;
    private Context mContext;

    ClientReception(InputStream inputStream, IListenerReception callback, Context context) {
        super("Thread.Client.ClientReception");
        this.inputStream = inputStream;

        this.callback = callback;
        this.bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
        mContext = context;
    }

    @Override
    public void run() {
        try {
            //noinspection InfiniteLoopStatement
            while (true) {
                checkAbort();
                String msg = bufferedReader.readLine();
                if (msg == null) {
                    Log.d(TAG, "RECEIVED null"); //NON-NLS
                    callback.onDisconnected(mContext.getString(R.string.clientREceptionToastSocketClosed));
                } else if (msg.startsWith("JSON_")) { //NON-NLS
                    callback.onReceivedJson(msg.substring(5));
                } else if (msg.equals("SENDING_COVER")) { //NON-NLS
                    Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                    Log.d(TAG, "onReceivedBitmap"); //NON-NLS
                    Log.d(TAG, "onReceivedBitmap: calling callback"); //NON-NLS
                    callback.onReceivedBitmap(bitmap);
                } else if (msg.startsWith("SENDING_FILE")) { //NON-NLS
                    Track fileInfoReception;
                    try {
                        String json = msg.substring("SENDING_FILE".length()); //NON-NLS
                        fileInfoReception = new Track(new JSONObject(json), getAppDataPath(), false);
                        File destinationPath = new File(new File(fileInfoReception.getPath()).getParent());
                        destinationPath.mkdirs();
                        Log.i(TAG, "Start file reception: \n" + fileInfoReception); //NON-NLS
                        DataInputStream dis = new DataInputStream(new BufferedInputStream(inputStream));
                        double fileSize = fileInfoReception.getSize();
                        FileOutputStream fos = new FileOutputStream(fileInfoReception.getPath());
                        callback.onReceivingFile(fileInfoReception);
                        // TODO: Find best. Make a benchmark (and use it in notification progres bar)
                        //https://stackoverflow.com/questions/8748960/how-do-you-decide-what-byte-size-to-use-for-inputstream-read
                        byte[] buf = new byte[8192];
                        int bytesRead;
                        while (fileSize > 0 && (bytesRead = dis.read(buf, 0, (int) Math.min(buf.length, fileSize))) != -1) {
                            checkAbort();
                            fos.write(buf, 0, bytesRead);
                            fileSize -= bytesRead;
                        }
                        fos.close();
                        checkAbort();
                        callback.onReceivedFile(fileInfoReception);
                    } catch (JSONException e) {
                        Log.e(TAG, "onReceivedFile", e);
                    }
                }
            }
        } catch (InterruptedException ignored) {
        } catch (IOException ex) {
            boolean isENOSPC = false;
            if (ex.getCause() instanceof ErrnoException) {
                int errno = ((ErrnoException) ex.getCause()).errno;
                isENOSPC = errno == OsConstants.ENOSPC;
                //TODO: sync and merge: Manage errors like ENOENT (No such file or directory)") : SyncStatus{status=CONNECTED, nbRetries=0}
                //Less chance to happen since "SENDING_FILE" is not more received (reaplaced by API)
                //5-13 21:28:32.452 I/phramusca.com.jamuzremote.ClientSync:
                // onDisconnected("/storage/extSdCard/Android/data/org.phramusca.jamuz/files/Autres/DJ Little Tune/
                // New Remix Maquette 2007 /02 track2 .mp3:
                // open failed: ENOENT (No such file or directory)") : SyncStatus{status=CONNECTED, nbRetries=0}
                //OsConstants.ENOENT

                //NOTE the SPACE in the path !! "New Remix Maquette 2007 /02 track2 .mp3"
                //(it has been fixed by removing space in path only => "New Remix Maquette 2007/02 track2 .mp3" and in db)
            }
            if (isENOSPC) {
                //Ex: java.io.IOException: write failed: ENOSPC (No space left on device)
                callback.onDisconnected("ENOSPC");
            } else {
                // Other IOExceptions incl. SocketException
                callback.onDisconnected(ex.getMessage());
            }
        } finally {
            try {
                inputStream.close();
            } catch (IOException ignored) {
            }
        }
    }
}