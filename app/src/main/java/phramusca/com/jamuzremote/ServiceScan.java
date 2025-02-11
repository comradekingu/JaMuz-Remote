package phramusca.com.jamuzremote;

import android.content.Intent;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by raph on 10/06/17.
 */
public class ServiceScan extends ServiceBase {

    private static final String TAG = ServiceScan.class.getName();
    private Notification notificationScan;
    private int nbFiles = 0;
    private int nbFilesTotal = 0;
    private ProcessAbstract scanLibrary;
    private ProcessAbstract processBrowseFS;
    private ProcessAbstract processBrowseFScount;
    private String userPath;
    private PowerManager.WakeLock wakeLock;

    @Override
    public void onCreate() {
        notificationScan = new Notification(this, NotificationId.SCAN, getString(R.string.scanTitle));
        super.onCreate();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
        if (powerManager != null) {
            wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "jamuzremote:MyPowerWakelockTag"); //NON-NLS
            wakeLock.acquire(24 * 60 * 60 * 1000); //24 hours, enough to scan a lot, if not all !
        }

        userPath = intent.getStringExtra("userPath");
        scanLibraryInThread();
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        stop();
        wakeLock.release();
        super.onDestroy();
    }

    private void scanLibraryInThread() {
        new Thread() {
            public void run() {
                runOnUiThread(() -> helperNotification.notifyBar(notificationScan, getString(R.string.serviceScanNotifyCleaningDatabase)));
                if (HelperLibrary.musicLibrary != null) {
                    //Delete tracks from database that are from another folder than those 2
                    HelperLibrary.musicLibrary.deleteTrack(getAppDataPath, userPath);
                    //Scan user folder and cleanup library
                    File folder = new File(userPath);
                    scanFolder(folder);
                    waitScanFolder();
                    RepoAlbums.reset();

                    //Scan complete, warn user
                    final String msg = getString(R.string.serviceScanNotifyDatabaseUpdated);
                    runOnUiThread(() -> {
                        helperToast.toastLong(msg);
                        helperNotification.notifyBar(notificationScan, msg, 5000);
                    });
                    stopSelf();
                }
            }
        }.start();
    }

    private void waitScanFolder() {
        try {
            if (scanLibrary != null) {
                scanLibrary.join();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "ActivityMain onDestroy: UNEXPECTED InterruptedException", e); //NON-NLS
        }
    }

    private void scanFolder(final File path) {
        scanLibrary = new ProcessAbstract("Thread.ActivityMain.scanLibrayInThread") { //NON-NLS
            public void run() {
                try {
                    if (!path.getAbsolutePath().equals("/")) { //NON-NLS
                        checkAbort();
                        nbFiles = 0;
                        nbFilesTotal = 0;
                        checkAbort();
                        //Scan android filesystem for files
                        processBrowseFS = new ProcessAbstract("Thread.ActivityMain.browseFS") { //NON-NLS
                            public void run() {
                                try {
                                    browseFS(path);
                                } catch (IllegalStateException | InterruptedException e) {
                                    Log.w(TAG, "Thread.ActivityMain.browseFS InterruptedException"); //NON-NLS
                                    scanLibrary.abort();
                                }
                            }
                        };
                        processBrowseFS.start();
                        //Get total number of files
                        processBrowseFScount = new ProcessAbstract("Thread.ActivityMain.browseFScount") { //NON-NLS
                            public void run() {
                                try {
                                    browseFScount(path);
                                } catch (InterruptedException e) {
                                    Log.w(TAG, "Thread.ActivityMain.browseFScount InterruptedException"); //NON-NLS
                                    scanLibrary.abort();
                                }
                            }
                        };
                        processBrowseFScount.start();
                        checkAbort();
                        processBrowseFS.join();
                        processBrowseFScount.join();
                    }

                    //Scan deleted files
                    //This will remove from db files not in filesystem
                    checkAbort();
                    List<Track> tracks =
                            new Playlist("ScanFolder", false) //NON-NLS
                                    .getTracks(new ArrayList<Track.Status>() {
                                        {
                                            add(Track.Status.LOCAL);
                                        }
                                    });
                    nbFilesTotal = tracks.size();
                    nbFiles = 0;
                    for (Track track : tracks) {
                        checkAbort();
                        File file = new File(track.getPath());
                        if (!file.exists()) {
                            Log.d(TAG, "Remove track from db: " + track); //NON-NLS
                            track.delete();
                        }
                        notifyScan(getString(R.string.serviceScanNotifyScanningDeleted), 200);
                    }
                } catch (InterruptedException e) {
                    Log.w(TAG, "Thread.ActivityMain.scanLibrayInThread InterruptedException"); //NON-NLS
                }
            }

            private void browseFS(File path) throws InterruptedException {
                checkAbort();
                if (path.isDirectory()) {
                    File[] files = path.listFiles();
                    if (files != null) {
                        if (files.length > 0) {
                            for (File file : files) {
                                checkAbort();
                                if (file.isDirectory()) {
                                    browseFS(file);
                                } else {
                                    String absolutePath = file.getAbsolutePath();
                                    //getAppDataPath is managed in ServiceSync
                                    if (!absolutePath.startsWith(getAppDataPath.getAbsolutePath())) {
                                        //Scanning extra local folder
                                        List<String> audioExtensions = new ArrayList<>();
                                        audioExtensions.add("mp3"); //NON-NLS
                                        audioExtensions.add("flac"); //NON-NLS
                                        /*audioFiles.add("ogg");*/
                                        String ext = absolutePath.substring(absolutePath.lastIndexOf(".") + 1); //NON-NLS
                                        if (audioExtensions.contains(ext)) {
                                            HelperLibrary.musicLibrary.insertOrUpdateTrack(absolutePath);
                                        }
                                    }
                                    notifyScan(getString(R.string.scanNotifyScanning), 13);
                                }
                            }
                        } else {
                            Log.i(TAG, "Deleting empty folder " + path.getAbsolutePath()); //NON-NLS
                            //noinspection ResultOfMethodCallIgnored
                            path.delete();
                        }
                    }
                }
            }

            private void browseFScount(File path) throws InterruptedException {
                checkAbort();
                if (path.isDirectory()) {
                    File[] files = path.listFiles();
                    if (files != null) {
                        if (files.length > 0) {
                            for (File file : files) {
                                checkAbort();
                                if (file.isDirectory()) {
                                    browseFScount(file);
                                } else {
                                    nbFilesTotal++;
                                }
                            }
                        }
                    }
                }
            }
        };
        scanLibrary.start();
    }

    private void notifyScan(final String action, int every) {
        nbFiles++;
        helperNotification.notifyBar(notificationScan, action, every, nbFiles, nbFilesTotal);
    }

    private void stop() {
        //Abort and wait scanLibrayInThread is aborted
        //So it does not crash if scanLib not completed
        if (processBrowseFS != null) {
            processBrowseFS.abort();
        }
        if (processBrowseFScount != null) {
            processBrowseFScount.abort();
        }
        if (scanLibrary != null) {
            scanLibrary.abort();
        }
        try {
            if (processBrowseFS != null) {
                processBrowseFS.join();
            }
            if (processBrowseFScount != null) {
                processBrowseFScount.join();
            }
            if (scanLibrary != null) {
                scanLibrary.join();
            }
        } catch (InterruptedException e) {
            Log.e(TAG, "ActivityMain onDestroy: UNEXPECTED InterruptedException", e); //NON-NLS
        }
    }
}